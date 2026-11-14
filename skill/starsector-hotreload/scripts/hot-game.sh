#!/usr/bin/env bash
# Runs one hot-reload game in its slot until the game exits. Started by hot.sh start (detached, in a slot); not
# meant to be called by hand.
#   hot-game.sh PROJECT_DIR WORK_DIR [--play] [--with ID]... [--jar JAR]... [--dep FOLDER [--dep-jar JAR]...]...
#               [--debug PORT] [--no-auto] [--settings|--no-settings]
#
# The game runs on the JetBrains Runtime bundled with this skill, with -XX:+AllowEnhancedClassRedefinition and the
# HotReload agent, so a rebuilt jar of the mod (or of a --dep) is swapped into the running game.
# Mods: --play: the user's enabled list with the mod under test swapped in; otherwise only the mod
# under test, its dependencies, --with mods and a helper that keeps the game running behind other windows.
# --dep FOLDER: a mod project to run (and hot reload) instead of the installed mod with the same id; --dep-jar JAR
# gives it a freshly built jar, as --jar does for the mod under test.
# Display: --play: the user's launcher choices, window in front. Otherwise the test window (1600x900, cursor guarded,
# driven with gamectl) at normal priority, opened behind your windows but free to bring forward; --front opens it in front.
set -uo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
# hot.sh runs a copy of this script from the work folder (bash reads a script while it runs it, so an edit to the
# skill would break a running session); HOT_SKILL_DIR then names the skill
SKILL=${HOT_SKILL_DIR:-${HERE%/scripts}}
LIVE="$SKILL/scripts"
. "$LIVE/lib.sh"
hot_root || exit 2

(( $# >= 2 )) || { sed -n '2,17p' "$0" >&2; exit 2; }
PROJECT=$(cd -- "$1" 2>/dev/null && pwd) || { echo "NO_PROJECT: no such folder: $1" >&2; exit 2; }
WORK=$(mkdir -p -- "$2" && cd -- "$2" && pwd) || { echo "NO_WORK: cannot create $2" >&2; exit 2; }
shift 2
play=0 front=0 auto=1 settings= debug= with=() jars=() deps=() depjars=()
while (( $# )); do
    case $1 in
        --play) play=1; shift ;;
        --front) front=1; shift ;;
        --no-auto) auto=0; shift ;;
        --settings) settings=1; shift ;;
        --no-settings) settings=0; shift ;;
        --debug) debug=${2:-}; shift 2 || shift ;;
        --resolution) export HOT_RESOLUTION=${2:-}; shift 2 || shift ;;
        --with) with+=("${2:-}"); shift 2 || shift ;;
        --jar) jars+=("${2:-}"); shift 2 || shift ;;
        --dep) deps+=("${2:-}"); depjars+=(""); shift 2 || shift ;;
        --dep-jar)
            (( ${#deps[@]} )) || { echo "--dep-jar needs a --dep before it" >&2; exit 2; }
            depjars[${#deps[@]}-1]+="${2:-}"$'\n'; shift 2 || shift ;;
        *) echo "Unknown option $1" >&2; exit 2 ;;
    esac
done
[[ -z $settings ]] && settings=$play
[[ -z $debug || $debug =~ ^[0-9]+$ ]] || { echo "--debug takes a port number" >&2; exit 2; }

hot_cfg game_root; hot_posix_path "$REPLY"; GAME=${REPLY%/}
[[ -n $GAME && -d $GAME/starsector-core ]] || { echo "NO_SETUP: no Windows game folder is stored. Run once: bash \"$LIVE/hot.sh\" setup \"<Starsector folder>\"" >&2; exit 2; }
hot_jbr || { echo "NO_JBR: no JetBrains Runtime on this machine. Run once: bash \"$SKILL/scripts/hot.sh\" setup" >&2; exit 2; }
JBR=$REPLY
if bash "$LIVE/game-proc.sh" alive "$WORK" 2>/dev/null; then
    echo "BUSY: a game from $WORK is still open (PID $(cat "$WORK/game.pid"))" >&2; exit 1
fi

mod_id() {
    hot_mod_info "$1" || return 1
    [[ $REPLY =~ \"id\"[[:space:]]*:[[:space:]]*\"([^\"]+)\" ]] || return 1
    REPLY=${BASH_REMATCH[1]}
}
mod_id "$PROJECT" || { echo "NO_PROJECT: no mod_info.json with an id in $PROJECT" >&2; exit 2; }
MOD_ID=$REPLY

# the jars a project loads, as mod_info.json lists them
jar_entries() {
    local list
    ENTRIES=()
    hot_mod_info "$1"
    [[ $REPLY =~ \"jars\"[[:space:]]*:[[:space:]]*\[([^]]*)\] ]] || return 0
    list=${BASH_REMATCH[1]}
    while [[ $list =~ \"([^\"]+)\" ]]; do ENTRIES+=("${BASH_REMATCH[1]}"); list=${list#*\"*\"}; done
}

# stage a project (stage-mod.sh: copies of its jars, so the game never locks the build) and add its jars to the
# watch list: each staged jar with the jar it came from (a fresh build replaces the entry with its file name, else
# the first one, as stage-mod.sh does)
HOT="$WORK/hot"
WATCH=()
stage() {
    local project=$1 staged=$2 e j src
    shift 2
    bash "$LIVE/stage-mod.sh" "$project" "$staged" "$@" >> "$WORK/stage.log" 2>&1 || return 1
    jar_entries "$project"
    local sources=()
    for e in "${ENTRIES[@]}"; do sources+=("$project/$e"); done
    for j in "$@"; do
        local hit=0 i
        for i in "${!ENTRIES[@]}"; do
            [[ ${ENTRIES[i]##*/} == "${j##*/}" ]] && { sources[i]=$j; hit=1; }
        done
        (( hit )) || { (( ${#sources[@]} )) && sources[0]=$j; }
    done
    for i in "${!ENTRIES[@]}"; do
        src=${sources[i]}
        [[ $src == /* || $src =~ ^[A-Za-z]: ]] || src="$PWD/$src"
        WATCH+=("$(cygpath -w -- "$src")"$'\t'"$(cygpath -w -- "$staged/${ENTRIES[i]}")")
    done
}
: > "$WORK/stage.log"
STAGED="$WORK/staged/${PROJECT##*/}"
stage "$PROJECT" "$STAGED" "${jars[@]}" ||
    { echo "STAGE_FAILED: read $WORK/stage.log (last lines below)" >&2; tail -n 20 "$WORK/stage.log" >&2; exit 1; }
declare -A override=([${MOD_ID,,}]="$STAGED")
for i in "${!deps[@]}"; do
    dep=$(cd -- "${deps[i]}" 2>/dev/null && pwd) || { echo "NO_PROJECT: no such --dep folder: ${deps[i]}" >&2; exit 2; }
    mod_id "$dep" || { echo "NO_PROJECT: no mod_info.json with an id in $dep" >&2; exit 2; }
    dep_id=$REPLY
    djars=()
    while IFS= read -r j; do [[ -n $j ]] && djars+=("$j"); done <<< "${depjars[i]}"
    stage "$dep" "$WORK/staged/${dep##*/}" "${djars[@]}" ||
        { echo "STAGE_FAILED: --dep $dep, read $WORK/stage.log" >&2; tail -n 20 "$WORK/stage.log" >&2; exit 1; }
    override[${dep_id,,}]="$WORK/staged/${dep##*/}"
    echo "Hot dependency: $dep_id from $dep"
done

# which mods, by id
ids=()
if (( play )); then
    list="$GAME/mods/enabled_mods.json"
    if [[ -r $list ]]; then
        while read -r id; do [[ -n $id ]] && ids+=("$id"); done < <(tr -d '\r\n' < "$list" | grep -o '"enabledMods"[^]]*' | grep -o '"[^"]*"' | sed '1d; s/"//g')
        echo "Your enabled mods: ${#ids[@]} in $list"
    else
        echo "NOTE: no $list: only the mod under test and its dependencies"
    fi
fi
ids+=("$MOD_ID" "${with[@]}")
mods=() seen=' ' skipped=()
add_id() {
    local id=$1 low=${1,,} dir
    [[ $seen == *" $low "* ]] && return 0
    seen+="$low "
    if [[ -n ${override[$low]:-} ]]; then mods+=("${override[$low]}"); return 0; fi
    if dir=$(bash "$LIVE/find-mod.sh" "$GAME" "$id" 2>/dev/null); then mods+=("$dir"); else skipped+=("$id"); fi
}
for id in "${ids[@]}"; do add_id "$id"; done
i=0
while (( i < ${#mods[@]} )); do
    hot_mod_info "${mods[i]}"; i=$(( i + 1 ))
    [[ $REPLY =~ \"dependencies\"[[:space:]]*:[[:space:]]*\[([^]]*)\] ]] || continue
    d=${BASH_REMATCH[1]}
    while [[ $d =~ \"id\"[[:space:]]*:[[:space:]]*\"([^\"]+)\" ]]; do
        dep=${BASH_REMATCH[1]}; d=${d#*"${BASH_REMATCH[0]}"}
        [[ $seen == *" ${dep,,} "* ]] || { add_id "$dep"; echo "Added dependency $dep"; }
    done
done
if (( ${#skipped[@]} )); then
    (( play )) || { echo "MISSING_MOD: not installed in $GAME/mods: ${skipped[*]}" >&2; exit 1; }
    echo "NOTE: enabled but not installed, left out: ${skipped[*]}"
fi
if (( ! play )); then
    # keeps the main loop running behind other windows, so gamectl input and captures work
    rm -rf -- "$WORK/hot_helper"
    cp -r -- "$SKILL/templates/hot-helper" "$WORK/hot_helper"
    mods+=("$WORK/hot_helper")
fi
bash "$LIVE/make-modset.sh" "$WORK" "${mods[@]}" > "$WORK/modset.log" 2>&1 ||
    { echo "MODSET_FAILED: read $WORK/modset.log" >&2; tail -n 20 "$WORK/modset.log" >&2; exit 1; }

mkdir -p "$WORK/saves"
if (( settings )) && [[ -d $GAME/saves/common ]]; then
    rm -rf -- "$WORK/saves/common"
    cp -r -- "$GAME/saves/common" "$WORK/saves/common" && echo "Copied your settings from $GAME/saves/common"
fi

# launch-game.sh runs GAME/jre/bin/java.exe: give it a game folder whose jre is the JetBrains Runtime. Both entries
# are junctions; nothing in the install changes.
SHIM="$WORK/game"
mkdir -p "$SHIM"
junction() {
    [[ -e $1 ]] && MSYS_NO_PATHCONV=1 cmd.exe /c rmdir "$(cygpath -w -- "$1")" >/dev/null 2>&1
    MSYS_NO_PATHCONV=1 cmd.exe /c mklink /J "$(cygpath -w -- "$1")" "$(cygpath -w -- "$2")" >/dev/null
}
junction "$SHIM/starsector-core" "$GAME/starsector-core" && junction "$SHIM/jre" "$JBR" ||
    { echo "SHIM_FAILED: could not link $SHIM" >&2; exit 1; }

# the agent, built once per source change with the runtime's own JDK
TOOLS=$(bash "$LIVE/build-tools.sh" 2>> "$WORK/stage.log" | tail -n 1)
AGENT="$TOOLS/HotReloadAgent.jar"
[[ -f $AGENT ]] || { echo "AGENT_FAILED: read $WORK/stage.log" >&2; exit 1; }

# the control folder the agent reads
rm -rf -- "$HOT"
mkdir -p "$HOT"
printf '%s\n' "${WATCH[@]}" > "$HOT/watch.txt"
(( auto )) || : > "$HOT/no-auto"

args=(-XX:+AllowEnhancedClassRedefinition "-javaagent:$(cygpath -w -- "$AGENT")=$(cygpath -w -- "$HOT")")
[[ -n $debug ]] && args+=("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:$debug")
[[ -n ${HOT_JAVA_ARGS:-} ]] && args+=("$HOT_JAVA_ARGS")
export HOT_JAVA_ARGS=$(printf '%s\n' "${args[@]}")
if (( play )); then
    heap=$(grep -a -o -E -- '-Xmx[0-9]+[mMgG]' "$GAME/vmparams" 2>/dev/null | head -n 1)
    [[ -n $heap ]] && export HOT_HEAP=${heap#-Xmx}
    export HOT_HIDE=0 HOT_CURSOR_GUARD=0 HOT_USER_DISPLAY=1
else
    # A hot game is one the user looks at and clicks too. It opens behind the user's windows: the game takes focus
    # once, when its window is created about a second after launch, so the push-back runs only for the first
    # HOT_HIDE_SECONDS (20). After that a click brings it to the front and it stays there. --front: never pushed back.
    # Normal priority either way; the helper keeps it running behind other windows, so gamectl works in both.
    if (( front )); then export HOT_HIDE=0; else export HOT_HIDE=1 HOT_HIDE_SECONDS=${HOT_HIDE_SECONDS:-20}; fi
fi

echo "HOT_GAME: ${#mods[@]} mods, ${#WATCH[@]} jar(s) hot, $( (( play )) && echo 'play window' || { (( front )) && echo 'test window, in front' || echo 'test window, opened behind'; }), work $WORK"
bash "$LIVE/launch-game.sh" "$SHIM" "$WORK" > "$WORK/launch.log" 2>&1
status=$?
if grep -q 'ERROR com.fs.starfarer.combat.CombatMain' "$WORK/logs/starsector.log" 2>/dev/null; then
    echo "HOT_ENDED: the game crashed (exit $status). Read the ERROR lines in $WORK/logs/starsector.log"
else
    echo "HOT_ENDED: the game closed (exit $status)"
fi
