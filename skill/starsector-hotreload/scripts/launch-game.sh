#!/usr/bin/env bash
# Starts Starsector directly (no launcher window) against an isolated mods/saves/logs folder. Windows, Git Bash.
# Runs in the foreground until the game exits. While the game runs, its PID is in WORK_DIR/game.pid; stop it with
# game-proc.sh stop WORK_DIR, never by process name. Called by hot-game.sh.
#
# Usage: launch-game.sh GAME_ROOT WORK_DIR
#   GAME_ROOT  a Starsector folder (holding starsector-core and jre; hot-game.sh passes one whose jre is the JBR)
#   WORK_DIR   folder holding mods/ (with enabled_mods.json); saves/, screenshots/ and logs/ are made in it
#
# Optional environment:
#   HOT_RESOLUTION      windowed size (default 1600x900)
#   HOT_HEAP            heap size for -Xms/-Xmx (default 3g)
#   HOT_FAST_RENDER     auto (default): use Fast Rendering (fr.jar) when it is installed; 1: require it; 0: never
#   HOT_HIDE            1 (default): push the game window behind other windows while it starts
#   HOT_HIDE_SECONDS    how long to keep pushing it back (default 20)
#   HOT_JAVA_ARGS       extra JVM arguments, one per line
#   HOT_PRIORITY        process priority (default Normal)
#   HOT_USER_DISPLAY    1: take resolution, fullscreen and sound from the user's own launcher settings
#   HOT_FULLSCREEN, HOT_SOUND  1 to turn them on (default off)
#   HOT_CURSOR_GUARD    1 (default): the game never moves the real cursor; 0 for a game the user plays
# Adapted from starsector-native-smoke 2.0.2's launch-game.sh (see NOTICE.md).
set -euo pipefail
(( $# == 2 )) || { sed -n '5,8p' "$0" >&2; exit 2; }
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
. "$HERE/lib.sh"
GAME=$(cd -- "$1" && pwd)
WORK=$(mkdir -p -- "$2" && cd -- "$2" && pwd)
RES=${HOT_RESOLUTION:-1600x900}
HEAP=${HOT_HEAP:-3g}
FAST=${HOT_FAST_RENDER:-auto}
winpath() { cygpath -w -- "$1"; }

CORE="$GAME/starsector-core"
JAVA="$GAME/jre/bin/java.exe"
[[ -d $CORE ]] || { printf 'Not a Starsector folder (no starsector-core): %s\n' "$GAME" >&2; exit 1; }
[[ -x $JAVA || -r $JAVA ]] || { printf 'Missing Java: %s\n' "$JAVA" >&2; exit 1; }
printf 'Java: %s (%s)\n' "$("$JAVA" -version 2>&1 | head -n 1)" "$JAVA"
[[ -r $WORK/mods/enabled_mods.json ]] || { printf 'Missing %s/mods/enabled_mods.json\n' "$WORK" >&2; exit 1; }
mkdir -p "$WORK/saves" "$WORK/screenshots" "$WORK/logs"

# The game's own jars, in the order the vanilla launcher uses (0.98a and 0.98.5a).
JARS=(janino.jar commons-compiler.jar commons-compiler-jdk.jar starfarer.api.jar starfarer_obf.jar
    jogg-0.0.7.jar jorbis-0.0.15.jar json.jar lwjgl.jar jinput.jar log4j-1.2.9.jar lwjgl_util.jar
    fs.sound_obf.jar fs.common_obf.jar xstream-1.4.10.jar txw2-3.0.2.jar jaxb-api-2.4.0-b180830.0359.jar
    webp-imageio-0.1.6.jar)
for jar in "${JARS[@]}"; do
    [[ -r $CORE/$jar ]] || { printf 'Missing game jar: %s/%s\n' "$CORE" "$jar" >&2; exit 1; }
done

# Fast Rendering (https://github.com/Halke1986/starsector-render): fr.jar goes first on the classpath. 1.0 loads as
# an agent from fr.jar (flags from fr.vmparams), 0.8.x from fr.agent.jar, older versions replaced the class loader.
FR_ARGS=()
if [[ $FAST != 0 && -r $CORE/fr.jar ]]; then
    JARS=(fr.jar "${JARS[@]}")
    fr_flags=()
    if [[ -r $CORE/fr.vmparams ]]; then
        while read -r flag; do fr_flags+=("$flag"); done < <(grep -v '^[[:space:]]*#' "$CORE/fr.vmparams" | tr -s ' \t\r' '\n' |
            grep -E '^(-javaagent:|--enable-preview$|-Dcom\.genir\.)' | grep -v -E 'PatchLibAgent' || true)
    fi
    if printf '%s\n' "${fr_flags[@]}" | grep -q '^-javaagent:fr\.jar$'; then
        FR_ARGS=("${fr_flags[@]}")
    elif [[ -r $CORE/fr.agent.jar ]]; then
        FR_ARGS=(-javaagent:fr.agent.jar -Dcom.genir.renderer.watchdog=0)
    else
        FR_ARGS=(-Djava.system.class.loader=com.genir.renderer.loaders.AppClassLoader)
    fi
    # Fast Rendering's own launcher also loads PatchLib's agent when it is installed.
    [[ -r $CORE/PatchLibAgent.jar ]] && FR_ARGS=(-javaagent:PatchLibAgent.jar "${FR_ARGS[@]}")
    printf 'Fast Rendering: on (%s)\n' "${FR_ARGS[*]}"
elif [[ $FAST == 1 ]]; then
    printf 'HOT_FAST_RENDER=1 but %s/fr.jar is missing\n' "$CORE" >&2; exit 1
else
    printf 'Fast Rendering: off\n'
fi
CP=$(IFS=';'; printf '%s' "${JARS[*]}")

TOOLS=$(bash "$HERE/build-tools.sh" | tail -n 1) || { printf 'TOOLS_FAILED: run hot.sh setup\n' >&2; exit 1; }

# Java prefs are the registry on Windows; they hold the licence and the launcher's settings. A plain run writes its
# save path to the "continue" key there, which repoints the Continue button of the user's own game. Serve a copy from
# memory instead (SmokePrefs), with Continue pointing at this work folder's newest save. Never print the copy.
PREFS_DIR="$WORK/prefs"
mkdir -p "$PREFS_DIR"
"$JAVA" -cp "$(winpath "$TOOLS/prefs-classes")" SmokePrefs export "$(winpath "$PREFS_DIR/prefs.xml")"
save=$(ls -dt "$WORK"/saves/save_* 2>/dev/null | head -n 1 || true)
cont=
[[ -n $save ]] && cont=$(winpath "$save")
PREFS_ARGS=(-Djava.util.prefs.PreferencesFactory=SmokePrefs -Dsmoke.prefs.xml="$(winpath "$PREFS_DIR/prefs.xml")"
    "-Dsmoke.prefs.set=continue=$cont|canSkipTutorial=true")
CP="$(winpath "$TOOLS/prefs-classes");$CP"

FS=$([[ ${HOT_FULLSCREEN:-0} == 1 ]] && echo true || echo false)
SOUND=$([[ ${HOT_SOUND:-0} == 1 ]] && echo true || echo false)
if [[ ${HOT_USER_DISPLAY:-0} == 1 ]]; then
    # the launcher's own choices: only these three entries are read from the copy (it holds the licence)
    pref() { perl -ne 'print $1 if /<entry key="'"$1"'" value="([^"]*)"/' "$PREFS_DIR/prefs.xml"; }
    v=$(pref resolution || true); v=${v//\//}
    [[ $v =~ ^[0-9]+x[0-9]+$ ]] && RES=$v
    v=$(pref fullscreen || true); [[ $v == true || $v == false ]] && FS=$v
    v=$(pref sound || true); [[ $v == true || $v == false ]] && SOUND=$v
fi
printf 'Display: %s, fullscreen %s, sound %s\n' "$RES" "$FS" "$SOUND"

# the game centres the real cursor through LWJGL at battle start, even behind other windows; the agent drops that move
GUARD_ARGS=()
[[ ${HOT_CURSOR_GUARD:-1} == 1 ]] && GUARD_ARGS=(-javaagent:"$(winpath "$TOOLS/CursorGuard.jar")" --add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED)

extra=()
while IFS= read -r arg; do [[ -n $arg ]] && extra+=("$arg"); done <<< "${HOT_JAVA_ARGS:-}"
cd -- "$CORE"
"$JAVA" -noverify -Xms"$HEAP" -Xmx"$HEAP" -Xss4m \
    --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
    --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.lang.ref=ALL-UNNAMED \
    --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.desktop/java.awt.font=ALL-UNNAMED \
    --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED \
    --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED --add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
    "${FR_ARGS[@]}" "${PREFS_ARGS[@]}" "${GUARD_ARGS[@]}" "${extra[@]}" \
    -Dfile.encoding=UTF-8 -Djava.util.Arrays.useLegacyMergeSort=true -Djava.library.path='native\windows' \
    -Dcom.fs.starfarer.settings.paths.saves="$(winpath "$WORK/saves")" \
    -Dcom.fs.starfarer.settings.paths.screenshots="$(winpath "$WORK/screenshots")" \
    -Dcom.fs.starfarer.settings.paths.mods="$(winpath "$WORK/mods")" \
    -Dcom.fs.starfarer.settings.paths.logs="$(winpath "$WORK/logs")" \
    -DlaunchDirect=true -DstartRes="$RES" -DstartFS="$FS" -DstartSound="$SOUND" \
    -classpath "$CP" com.fs.starfarer.StarfarerLauncher &
job=$!

# $! is a shell process (Git Bash runs java.exe as its child): find the java.exe whose command line names WORK_DIR.
# game-proc.sh stops only this process, never another Java the user runs.
pid=
filter="\$_.CommandLine -like '*$(winpath "$WORK")\\*'"
for _ in 1 2 3 4 5 6 7 8 9 10; do
    pid=$(powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $filter } | Select-Object -First 1 -ExpandProperty ProcessId" 2>/dev/null | tr -dc '0-9')
    [[ -n $pid ]] && break
    sleep 1
done
if [[ -n $pid ]]; then
    printf '%s\n' "$pid" > "$WORK/game.pid"
    printf 'Game PID %s, written to %s/game.pid\n' "$pid" "$WORK"
    if [[ ${HOT_HIDE:-1} == 1 ]]; then
        GAMECTL_PID=$pid "$TOOLS/gamectl.exe" hide "${HOT_HIDE_SECONDS:-20}" >> "$WORK/hide.log" 2>&1 &
    fi
    prio=${HOT_PRIORITY:-Normal}
    [[ $prio != Normal ]] && powershell.exe -NoProfile -Command "(Get-Process -Id $pid).PriorityClass = '$prio'" >/dev/null 2>&1
else
    printf 'WARNING: game PID not found; game-proc.sh cannot stop this run\n' >&2
fi
wait "$job" || status=$?
rm -f -- "$WORK/game.pid"
# the copied prefs hold the licence
rm -rf -- "$PREFS_DIR"
exit "${status:-0}"
