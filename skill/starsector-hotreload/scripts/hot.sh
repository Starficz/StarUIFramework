#!/usr/bin/env bash
# One entry point for the starsector-hotreload skill. Run it from Git Bash on Windows.
#   hot.sh setup [GAME_FOLDER]      once per machine: stores the game folder, checks the licence prefs exist, downloads
#                                   the JetBrains Runtime, builds the tools
#   hot.sh start PROJECT_DIR [--play|--front] [--with ID]... [--jar JAR]... [--dep FOLDER [--dep-jar JAR]...]...
#                [--debug PORT] [--no-auto] [--settings|--no-settings] [--resolution WxH] [--wait S]
#                                   start a game that stays open, with the mod's jars hot; returns at the title screen
#   hot.sh reload [PROJECT_DIR] [--timeout S]   swap the current build in now and print what changed
#   hot.sh ctl [PROJECT_DIR] CMD [ARG...]       background input to the game: bmove X Y, bclick X Y, brclick X Y,
#                                   bkey NAME [N], btype TEXT (gamectl; coordinates are capture pixels). Only these:
#                                   gamectl's foreground commands (click, move, key...) are refused (FOREGROUND_INPUT)
#   hot.sh shot [PROJECT_DIR] [NAME]            capture the game window to WORK/captures/NAME.png, print the path
#   hot.sh wait [PROJECT_DIR] [PATTERN] [S]     wait until the game log matches PATTERN, or goes quiet without one
#   hot.sh newgame [PROJECT_DIR]                from the title screen, start a new game (1600x900 test window)
#   hot.sh continue [PROJECT_DIR]               from the title screen, load the newest save (1600x900 test window);
#                                   NO_SAVE at once when the work folder has none
#   hot.sh campaign [PROJECT_DIR]               continue when there is a save, else newgame
#   hot.sh dump [PROJECT_DIR] [--timeout S]     print every StarUI panel on screen (StarUI's dumpLayout text, dialog
#                                   content included): StarUI writes it to the game log on request, this prints it
#   hot.sh console [PROJECT_DIR] "COMMAND" [--timeout S]  run a Console Commands command in the campaign or a battle
#                                   (needs Console Commands installed and the game started --with lw_console); prints
#                                   its output. Any text works: it goes through a file, not typed keys
#   hot.sh status [PROJECT_DIR]     the machine setup first (NO_SETUP, NO_JBR), then is the game open, its PID, the
#                                   last reload
#   hot.sh log [PROJECT_DIR] [N]    the last N lines of the reload log (default 40)
#   hot.sh dir [PROJECT_DIR]        print the work folder (game log in logs/, captures/, saves/, game.pid)
#   hot.sh stop [PROJECT_DIR]       close the game
# PROJECT_DIR may be left out when exactly one hot game is open.
# start: --play is a game for the user (their mod list, settings and display, window in front); without it the test
# window (only this mod, its dependencies and --with mods; 1600x900, driven with ctl), opened behind your windows
# and kept there by background input: for your own test loops. --front opens it in front: for a live review the user
# watches.
# --jar JAR: run this freshly built jar instead of the project's own, and watch it. --dep FOLDER: also run and watch
# that mod project instead of its installed copy. --resolution WxH: another test window size (newgame and continue
# click 1600x900 positions). --debug PORT: JDWP on 127.0.0.1:PORT (IntelliJ: Remote JVM Debug).
# --no-auto: reload only on hot.sh reload (default: a watched jar that changes is reloaded by itself).
# Every command prints one last line that says what happened; lines in capitals with a colon name the problem.
set -uo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
SKILL=${HERE%/scripts}
. "$HERE/lib.sh"
hot_root || exit 2
usage() { sed -n '2,37p' "$0" >&2; exit 2; }
(( HOT_WIN )) || { echo "NOT_WINDOWS: this skill is written for Git Bash on Windows" >&2; exit 2; }
hot_real_dir "$HOT_ROOT_DIR/work"; WORK_ROOT=$REPLY
mkdir -p "$WORK_ROOT"

# OWNER and WORK for a project: the folder is fixed per project (by its mod id)
project_work() {
    local project info
    project=$(cd -- "$1" 2>/dev/null && pwd) || { echo "NO_PROJECT: no such folder: $1" >&2; exit 2; }
    hot_mod_info "$project" || { echo "NO_PROJECT: no mod_info.json in $project. Pass the mod's root folder" >&2; exit 2; }
    info=$REPLY
    OWNER=${project##*/}
    [[ $info =~ \"id\"[[:space:]]*:[[:space:]]*\"([^\"]+)\" ]] && OWNER=${BASH_REMATCH[1]}
    OWNER=${OWNER,,}; OWNER=${OWNER//[^a-z0-9._-]/-}
    PROJECT=$project
    WORK="$WORK_ROOT/$OWNER"
}
alive() { bash "$HERE/game-proc.sh" alive "$1" 2>/dev/null; }

# the session for commands other than start: the named project, else the one open hot game. PICKED: 1 when the first
# argument named the project (the caller then drops it)
pick() {
    PICKED=0
    if [[ -n ${1:-} && -d $1 && -r $1/mod_info.json ]]; then project_work "$1"; PICKED=1; return; fi
    local open=() w
    for w in "$WORK_ROOT"/*/; do w=${w%/}; [[ -d $w/hot ]] && alive "$w" && open+=("$w"); done
    if (( ${#open[@]} == 1 )); then WORK=${open[0]}; OWNER=${WORK##*/}; return; fi
    if (( ${#open[@]} == 0 )); then echo "NO_GAME: no hot game is open. Start one: bash \"$0\" start PROJECT_DIR" >&2; exit 1; fi
    echo "WHICH: ${#open[@]} hot games are open (${open[*]}); name the project folder" >&2; exit 2
}
need_game() { alive "$WORK" || { echo "NO_GAME: the hot game in $WORK is not open" >&2; exit 1; }; }
gamectl() {
    local tools
    tools=$(bash "$HERE/build-tools.sh" 2>/dev/null | tail -n 1)
    [[ -x $tools/gamectl.exe ]] || { echo "NO_TOOLS: run: bash \"$0\" setup" >&2; exit 1; }
    GAMECTL_PID=$(cat "$WORK/game.pid") "$tools/gamectl.exe" "$@"
}

# a junction made in DIR resolves for Windows programs (not true for a path the Claude desktop app redirects)
links_ok() {
    local t="$1/.hot-link-test-$$" w seen
    mkdir -p "$t/target" && : > "$t/target/seen" || return 1
    w=$(cygpath -w -- "$t")
    MSYS_NO_PATHCONV=1 cmd.exe /c mklink /J "$w\\link" "$w\\target" >/dev/null 2>&1
    seen=$(MSYS_NO_PATHCONV=1 cmd.exe /c dir /b "$w\\link\\" 2>/dev/null)
    MSYS_NO_PATHCONV=1 cmd.exe /c rmdir "$w\\link" >/dev/null 2>&1
    rm -rf -- "$t"
    [[ $seen == *seen* ]]
}

cmd_setup() {
    local fail=0 game=${1:-} tools
    hot_version
    echo "starsector-hotreload $REPLY, machine folder $HOT_ROOT_DIR"
    echo "ok   Git Bash on Windows"
    [[ -n $game ]] || { hot_cfg game_root; game=$REPLY; }
    if [[ -z $game ]]; then
        echo "FAIL: no game folder given or stored"
        echo "  fix: ask the user for their Starsector folder (the one holding starsector-core), then: bash \"$0\" setup \"<folder>\". Never search the disk for it"
        fail=1
    else
        hot_posix_path "$game"; game=${REPLY%/}
        [[ ${game##*/} == starsector-core ]] && game=${game%/*}
        if [[ -r $game/starsector-core/starfarer.api.jar ]]; then
            hot_cfg_set game_root "$game"
            echo "ok   game folder $game"
        else
            echo "FAIL: $game is not a Starsector folder (no starsector-core/starfarer.api.jar in it)"
            echo "  fix: ask the user for the folder that holds starsector-core, mods and jre"
            fail=1
        fi
    fi
    # the game reads its licence and settings from the registry; a test game serves a copy of them from memory.
    # Never print, copy or edit them.
    if MSYS_NO_PATHCONV=1 reg.exe query 'HKCU\Software\JavaSoft\Prefs\com\fs\starfarer' >/dev/null 2>&1; then
        echo "ok   the game has run on this Windows account (its preferences exist)"
    else
        echo "FAIL: the game has never run on this Windows account"
        echo "  fix: the user starts the game once through its own launcher and enters the licence key, then run setup again"
        fail=1
    fi
    if links_ok "$WORK_ROOT"; then
        echo "ok   work folder $WORK_ROOT"
    else
        echo "FAIL: a junction in $WORK_ROOT does not resolve for the game"
        echo "  fix: set HOT_HOME to a folder outside %LOCALAPPDATA% for every hot.sh call"
        fail=1
    fi
    if hot_jbr; then
        echo "ok   JetBrains Runtime $REPLY"
    else
        echo "JetBrains Runtime 17 (enhanced class redefinition) goes to $HOT_ROOT_DIR/jbr-17: downloading it from JetBrains once"
        bash "$HERE/fetch-jbr.sh" || { echo "FAIL: the JetBrains Runtime download did not finish (above)"; fail=1; }
    fi
    if hot_jbr; then
        if tools=$(bash "$HERE/build-tools.sh" | tail -n 1); then echo "ok   tools in $tools"
        else echo "FAIL: a tool did not build (TOOLS_FAILED lines above)"; fail=1; fi
    fi
    (( fail )) && { echo "HOT_SETUP_FAILED: fix the FAIL lines above and run setup again"; exit 1; }
    echo "HOT_SETUP OK"
}

cmd_start() {
    (( $# >= 1 )) || usage
    project_work "$1"; shift
    hot_jbr || { echo "NO_JBR: this machine has no runtime for hot games yet. Run once: bash \"$0\" setup" >&2; exit 2; }
    hot_cfg game_root; [[ -n $REPLY ]] || { echo "NO_SETUP: run once: bash \"$0\" setup \"<Starsector folder>\"" >&2; exit 2; }
    local wait=420 args=() a
    while (( $# )); do
        case $1 in
            --wait) wait=${2:-420}; shift 2 || shift ;;
            --jar|--dep-jar|--dep)
                # absolute paths: the session runs from another folder
                a=$(cd -- "$(dirname -- "${2:-.}")" 2>/dev/null && pwd)/${2##*/}
                [[ $1 == --dep ]] && a=$(cd -- "${2:-.}" 2>/dev/null && pwd)
                [[ -e $a ]] || { echo "NO_FILE: $1 ${2:-}: not found" >&2; exit 2; }
                args+=("$1" "$a"); shift 2 ;;
            *) args+=("$1"); shift ;;
        esac
    done
    if alive "$WORK"; then
        echo "ALREADY_OPEN: a hot game of $OWNER is open (PID $(cat "$WORK/game.pid")). Rebuild the jar to reload it, or: bash \"$0\" stop \"$PROJECT\""
        exit 0
    fi
    mkdir -p "$WORK"
    rm -f -- "$WORK/session.log" "$WORK/game.pid"
    # the session must outlive this shell (the agent's tool call ends, the game stays): its own hidden process. It
    # runs a copy of hot-game.sh, since bash reads a script while it runs it and an edit to the skill would break it.
    local script="$WORK/session.sh" q=
    cp -- "$HERE/hot-game.sh" "$WORK/hot-game.run.sh"
    for a in "${args[@]}"; do q+=" $(printf '%q' "$a")"; done
    cat > "$script" <<EOF
#!/usr/bin/env bash
export HOT_SKILL_DIR=$(printf '%q' "$SKILL") HOT_HOME=$(printf '%q' "$HOT_ROOT_DIR")
exec >> $(printf '%q' "$WORK/session.log") 2>&1
exec bash $(printf '%q' "$WORK/hot-game.run.sh") $(printf '%q' "$PROJECT") $(printf '%q' "$WORK")$q
EOF
    # the last game's log: a crash it logged would read as this start crashing until the new game replaces the file
    [[ -f $WORK/logs/starsector.log ]] && mv -f "$WORK/logs/starsector.log" "$WORK/logs/starsector.prev.log"
    local bash_exe
    bash_exe=$(cygpath -w -- "$(command -v bash)")
    powershell.exe -NoProfile -Command "Start-Process -WindowStyle Hidden -FilePath '$bash_exe' -ArgumentList '\"$(cygpath -w -- "$script")\"'" ||
        { echo "START_FAILED: could not start the session process" >&2; exit 1; }
    echo "Starting a hot game of $OWNER, work folder $WORK (session log $WORK/session.log)"

    # wait for the game process, then the agent, then the title screen
    local t0=$SECONDS
    until [[ -s $WORK/game.pid ]]; do
        if grep -q -E '^(HOT_ENDED|NO_[A-Z_]+:|[A-Z_]+_FAILED:|MISSING_MOD:|BUSY:|Unknown option)' "$WORK/session.log" 2>/dev/null; then
            tail -n 20 "$WORK/session.log" >&2
            echo "START_FAILED: the session stopped before the game started (above; launch log $WORK/launch.log)" >&2; exit 1
        fi
        (( SECONDS - t0 > wait )) && { echo "TIMEOUT: no game after ${wait}s; read $WORK/session.log" >&2; exit 1; }
        sleep 1
    done
    grep -E '^(Your enabled mods|Added dependency|Hot dependency|NOTE|HOT_GAME)' "$WORK/session.log"
    until grep -q '^HOTRELOAD' "$WORK/hot/reload.log" 2>/dev/null || grep -q '^HOTRELOAD' "$WORK/launch.log" 2>/dev/null; do
        alive "$WORK" || { echo "START_FAILED: the game exited while starting; read $WORK/launch.log and $WORK/logs/starsector.log" >&2; exit 1; }
        (( SECONDS - t0 > wait )) && { echo "TIMEOUT: the agent did not report; read $WORK/launch.log" >&2; exit 1; }
        sleep 1
    done
    grep -h -E '^(Java|Display|Fast Rendering|WARNING)' "$WORK/launch.log"
    grep -h '^HOTRELOAD' "$WORK/hot/reload.log" "$WORK/launch.log" 2>/dev/null | head -n 1
    # the title screen: the log has gone quiet after the mods loaded
    local left=$(( wait - (SECONDS - t0) )) res
    (( left < 30 )) && left=30
    res=$(bash "$HERE/wait-log.sh" "$WORK/logs/starsector.log" "" "$left" 0); local st=$?
    case $st in
        0) echo "READY: hot game of $OWNER at the title screen, PID $(cat "$WORK/game.pid" 2>/dev/null), work $WORK"
           echo "Rebuild a watched jar and it reloads by itself; bash \"$0\" reload prints what changed." ;;
        3) echo "CRASHED: the game logged a fatal error; read $WORK/logs/starsector.log"; exit 1 ;;
        1) echo "START_FAILED: the game exited while loading; read $WORK/logs/starsector.log"; exit 1 ;;
        *) echo "SLOW: the game is still loading after ${wait}s ($res); it keeps going, PID $(cat "$WORK/game.pid" 2>/dev/null)" ;;
    esac
}

cmd_reload() {
    local project= timeout=60
    while (( $# )); do
        case $1 in
            --timeout) timeout=${2:-60}; shift 2 || shift ;;
            *) project=$1; shift ;;
        esac
    done
    pick "$project"; need_game
    local id="r$(date +%s)-$$" hot="$WORK/hot"
    printf '%s' "$id" > "$hot/request.tmp" && mv -f -- "$hot/request.tmp" "$hot/request"
    local t0=$SECONDS
    until [[ -s $hot/result-$id.txt ]]; do
        alive "$WORK" || { echo "NO_GAME: the game exited before the reload finished" >&2; exit 1; }
        (( SECONDS - t0 > timeout )) && { echo "TIMEOUT: no answer from the agent after ${timeout}s (is the game frozen? read $WORK/launch.log)" >&2; exit 1; }
        sleep 0.3
    done
    local result
    result=$(cat -- "$hot/result-$id.txt")
    rm -f -- "$hot/result-$id.txt"
    printf '%s\n' "$result"
    grep -q -E '^(RELOAD_FAILED|RELOAD_PARTIAL)' <<< "$result" && exit 1
    return 0
}

cmd_ctl() {
    pick "${1:-}"; (( PICKED )) && shift
    (( $# )) || usage
    # gamectl's commands without the b prefix focus the game window and move the real mouse: the user's, while they
    # work. A self-test never needs them, so ctl does not pass them on.
    case $1 in
        b*|info) ;;
        *) echo "FOREGROUND_INPUT: ctl $1 would bring the game to the front and move the user's mouse; use the background command (bmove, bclick, brclick, bkey, bcombo, btype; captures: hot.sh shot)" >&2
           exit 2 ;;
    esac
    need_game
    gamectl "$@"
}

cmd_shot() {
    pick "${1:-}"; (( PICKED )) && shift
    need_game
    local name=${1:-shot-$(date +%H%M%S)}
    name=${name%.png}
    mkdir -p "$WORK/captures"
    gamectl bshot "$(cygpath -w -- "$WORK/captures/$name.png")" >/dev/null || { echo "SHOT_FAILED: gamectl could not capture the window (minimized?)" >&2; exit 1; }
    echo "$WORK/captures/$name.png"
}

cmd_wait() {
    pick "${1:-}"; (( PICKED )) && shift
    need_game
    bash "$HERE/wait-log.sh" "$WORK/logs/starsector.log" "${1:-}" "${2:-120}"
}

cmd_newgame() {
    pick "${1:-}"
    need_game
    local tools
    tools=$(bash "$HERE/build-tools.sh" 2>/dev/null | tail -n 1)
    : > "$WORK/logs/starsector.log"
    GAMECTL="$tools/gamectl.exe" GAMECTL_PID=$(cat "$WORK/game.pid") bash "$HERE/new-game.sh" || exit 1
    local res
    res=$(bash "$HERE/wait-log.sh" "$WORK/logs/starsector.log" "Saving stage 8" 300 0) ||
        { echo "NEWGAME_FAILED: $res. Capture the window (hot.sh shot) and look: the clicks are for the 1600x900 test window" >&2; exit 1; }
    echo "IN_CAMPAIGN: a new game was generated and saved ($res); later starts can load it with Continue"
}

cmd_continue() {
    pick "${1:-}"
    need_game
    local res
    # no save: the Continue button does nothing, and waiting for a load would take the whole timeout
    if ! ls -d "$WORK"/saves/save_* >/dev/null 2>&1; then
        echo "NO_SAVE: this work folder has no save yet. hot.sh newgame makes one (hot.sh campaign does whichever applies)" >&2
        exit 1
    fi
    : > "$WORK/logs/starsector.log"
    # the title screen's Continue button in the 1600x900 test window; move first so the click is not only a hover
    gamectl bmove 1225 287 >/dev/null; sleep 0.5; gamectl bclick 1233 283 >/dev/null
    res=$(bash "$HERE/wait-log.sh" "$WORK/logs/starsector.log" "Loading stage 39" 300 0) ||
        { echo "CONTINUE_FAILED: $res. Capture the window (hot.sh shot): is there a save (hot.sh newgame makes one)?" >&2; exit 1; }
    sleep 3
    echo "IN_CAMPAIGN: the newest save is loaded, paused"
}

cmd_campaign() {
    local arg=${1:-}
    pick "$arg"
    need_game
    if ls -d "$WORK"/saves/save_* >/dev/null 2>&1; then cmd_continue "$arg"; else cmd_newgame "$arg"; fi
}

cmd_status() {
    # the setup first: without it no game can start, and "no game" alone hides why
    hot_cfg game_root
    [[ -n $REPLY ]] || { echo "NO_SETUP: this machine is not set up. Run once: bash \"$0\" setup \"<Starsector folder>\""; return 1; }
    hot_jbr || { echo "NO_JBR: the JetBrains Runtime is missing. Run once: bash \"$0\" setup"; return 1; }
    pick "${1:-}"
    if alive "$WORK"; then echo "OPEN: hot game of $OWNER, PID $(cat "$WORK/game.pid"), work $WORK"; else echo "CLOSED: no game in $WORK"; fi
    [[ -r $WORK/hot/last.txt ]] && { echo "Last reload:"; sed 's/^/  /' "$WORK/hot/last.txt"; }
    return 0
}

cmd_dump() {
    local project= timeout=15
    while (( $# )); do
        case $1 in
            --timeout) timeout=${2:-15}; shift 2 || shift ;;
            *) project=$1; shift ;;
        esac
    done
    pick "$project"; need_game
    local id="d$(date +%s)-$$" hot="$WORK/hot" log="$WORK/logs/starsector.log"
    # the agent sets the system property starui.dump to the id; StarUI's next render logs the dump with it
    printf '%s' "$id" > "$hot/dump.tmp" && mv -f -- "$hot/dump.tmp" "$hot/dump"
    local t0=$SECONDS
    until grep -q -F "StarUI dump $id end" "$log" 2>/dev/null; do
        alive "$WORK" || { echo "NO_GAME: the game exited before the dump" >&2; exit 1; }
        if (( SECONDS - t0 > timeout )); then
            [[ -e $hot/dump ]] && { echo "TIMEOUT: the agent did not take the request in ${timeout}s (read $WORK/launch.log)" >&2; exit 1; }
            echo "NO_DUMP: no StarUI panel rendered in ${timeout}s: no StarUI UI is on screen, or the StarUI jar is older than the dump (1.0.0-alpha after its first release)" >&2
            exit 1
        fi
        sleep 0.3
    done
    # from the line that starts the dump to its end line, without the log4j prefix of the first one
    awk -v id="$id" 'index($0, "StarUI dump " id ":") { on = 1; sub(/^.*StarUI dump /, "StarUI dump ") }
        on { print } index($0, "StarUI dump " id " end") { exit }' "$log"
}

cmd_console() {
    local project= timeout=30 command=
    while (( $# )); do
        case $1 in
            --timeout) timeout=${2:-30}; shift 2 || shift ;;
            *) if [[ -z $project && -z $command && -d $1 && -r $1/mod_info.json ]]; then project=$1; else command=${command:+$command }$1; fi; shift ;;
        esac
    done
    [[ -n $command ]] || { echo "Usage: hot.sh console [PROJECT_DIR] \"COMMAND\"" >&2; exit 2; }
    pick "$project"; need_game
    [[ -f $WORK/hot_helper/jars/hot-helper.jar ]] ||
        { echo "NO_CONSOLE: the helper has no console runner: install Console Commands (lw_console) in the game's mods folder, then restart the hot game" >&2; exit 1; }
    grep -q -i '"lw_console"' "$WORK/mods/enabled_mods.json" 2>/dev/null ||
        { echo "NO_CONSOLE: Console Commands is not in this game: restart it with --with lw_console" >&2; exit 1; }
    local id="c$(date +%s)-$$" hot="$WORK/hot" log="$WORK/logs/starsector.log"
    printf '%s\n%s' "$id" "$command" > "$hot/console.tmp" && mv -f -- "$hot/console.tmp" "$hot/console"
    local t0=$SECONDS
    until grep -q -F "HOTCONSOLE $id end" "$log" 2>/dev/null; do
        alive "$WORK" || { echo "NO_GAME: the game exited before the command ran" >&2; exit 1; }
        if (( SECONDS - t0 > timeout )); then
            [[ -e $hot/console ]] && { echo "TIMEOUT: the agent did not take the command in ${timeout}s (read $WORK/launch.log)" >&2; exit 1; }
            echo "NOT_RUN: nothing ran it in ${timeout}s: commands run in the campaign or a battle, not on the title screen (hot.sh campaign gets there)" >&2
            exit 1
        fi
        sleep 0.3
    done
    # the console's own output between the markers, without log4j's prefix (time, thread, level, logger)
    awk -v id="$id" 'index($0, "HOTCONSOLE " id " start") { on = 1; next }
        index($0, "HOTCONSOLE " id " end") { exit }
        on { line = $0; if (match(line, / - /)) line = substr(line, RSTART + 3); print line }' "$log"
    echo "CONSOLE_DONE: $command"
}

cmd_log() {
    local n=40 project=
    for a in "$@"; do [[ $a =~ ^[0-9]+$ ]] && n=$a || project=$a; done
    pick "$project"
    tail -n "$n" "$WORK/hot/reload.log" 2>/dev/null || echo "NO_LOG: $WORK/hot/reload.log"
}

cmd_stop() {
    pick "${1:-}"
    alive "$WORK" || { echo "CLOSED: no game in $WORK"; return 0; }
    bash "$HERE/game-proc.sh" stop "$WORK" && echo "STOPPED: the hot game of $OWNER"
}

cmd=${1:-}
shift || true
case $cmd in
    setup) cmd_setup "$@" ;;
    start) cmd_start "$@" ;;
    reload) cmd_reload "$@" ;;
    ctl) cmd_ctl "$@" ;;
    shot) cmd_shot "$@" ;;
    wait) cmd_wait "$@" ;;
    newgame) cmd_newgame "$@" ;;
    continue) cmd_continue "$@" ;;
    campaign) cmd_campaign "$@" ;;
    dump) cmd_dump "$@" ;;
    console) cmd_console "$@" ;;
    status) cmd_status "$@" ;;
    log) cmd_log "$@" ;;
    dir) pick "${1:-}"; echo "$WORK" ;;
    stop) cmd_stop "$@" ;;
    *) usage ;;
esac
