#!/usr/bin/env bash
# Builds this skill's tools once per machine and per version of their sources into the machine folder
# (tools/<sum>), and prints that folder on its last line. Parts that are built already are skipped, so every launch
# may call this. The Java parts are built with the JetBrains Runtime's own JDK (hot.sh setup installs it); gamectl
# with the C# compiler that ships with Windows (.NET Framework 4).
#   HotReloadAgent.jar   the reload agent
#   prefs-classes/       SmokePrefs: serves the game's registry preferences (licence, settings) from memory, so a test
#                        game never writes them
#   CursorGuard.jar      stops the game moving the real cursor while it runs behind other windows
#   gamectl.exe          background input and captures for the game window
#   hot-helper.jar       the test helper mod's code: runs hot.sh console commands through Console Commands. Built
#                        only when Console Commands (lw_console) is in the game's mods folder; without it the
#                        helper mod stays data-only
# Exit 1 names the part that failed. Adapted from starsector-native-smoke 2.0.2 (see NOTICE.md).
set -uo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
. "$HERE/lib.sh"
hot_root || exit 1
hot_jbr || { echo "NO_JBR: run: bash \"$HERE/hot.sh\" setup" >&2; exit 2; }
BIN="$REPLY/bin"
hot_tools || exit 1
TOOLS=$REPLY
SRC="$HOT_SKILL/tools"
w() { cygpath -w -- "$1"; }
failed=0

if [[ ! -f $TOOLS/HotReloadAgent.jar ]]; then
    tmp="$TOOLS/agent.$$"; rm -rf -- "$tmp"; mkdir -p "$tmp/classes"
    if "$BIN/javac.exe" --release 17 -Xlint:all,-rawtypes -d "$(w "$tmp/classes")" "$(w "$SRC/HotReloadAgent.java")" >&2 &&
        printf 'Premain-Class: HotReloadAgent\nAgent-Class: HotReloadAgent\nCan-Redefine-Classes: true\nCan-Retransform-Classes: true\n' > "$tmp/manifest.txt" &&
        "$BIN/jar.exe" --create --file "$(w "$tmp/a.jar")" --manifest "$(w "$tmp/manifest.txt")" -C "$(w "$tmp/classes")" . >&2; then
        mv -n -- "$tmp/a.jar" "$TOOLS/HotReloadAgent.jar"; echo "Built $TOOLS/HotReloadAgent.jar" >&2
    else
        echo 'TOOLS_FAILED agent: javac or jar reported errors' >&2; failed=1
    fi
    rm -rf -- "$tmp"
fi

if [[ ! -f $TOOLS/prefs-classes/SmokePrefs.class ]]; then
    tmp="$TOOLS/prefs-classes.$$"; rm -rf -- "$tmp"; mkdir -p "$tmp"
    if "$BIN/javac.exe" --release 17 -nowarn -d "$(w "$tmp")" "$(w "$SRC/SmokePrefs.java")" >&2; then
        mv -T -- "$tmp" "$TOOLS/prefs-classes" 2>/dev/null || rm -rf -- "$tmp"
    else
        rm -rf -- "$tmp"; echo 'TOOLS_FAILED prefs: javac reported errors' >&2; failed=1
    fi
fi

if [[ ! -f $TOOLS/CursorGuard.jar ]]; then
    tmp="$TOOLS/guard.$$"; rm -rf -- "$tmp"; mkdir -p "$tmp/classes"
    if "$BIN/javac.exe" -nowarn -Xlint:-options -source 17 -target 17 --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
            -d "$(w "$tmp/classes")" "$(w "$SRC/CursorGuard.java")" >&2 &&
        printf 'Premain-Class: CursorGuard\n' > "$tmp/manifest.txt" &&
        "$BIN/jar.exe" cfm "$(w "$tmp/CursorGuard.jar")" "$(w "$tmp/manifest.txt")" -C "$(w "$tmp/classes")" . >&2; then
        mv -n -- "$tmp/CursorGuard.jar" "$TOOLS/CursorGuard.jar"
    else
        echo 'TOOLS_FAILED guard: javac or jar reported errors' >&2; failed=1
    fi
    rm -rf -- "$tmp"
fi

# the helper's code compiles against the game's API and Console Commands' jar, found by its mod id
if [[ ! -f $TOOLS/hot-helper.jar ]]; then
    hot_cfg game_root; hot_posix_path "$REPLY"; game=${REPLY%/}
    console_jar=
    for info in "$game"/mods/*/mod_info.json; do
        if grep -q -E '"id"[[:space:]]*:[[:space:]]*"lw_console"' "$info" 2>/dev/null; then
            [[ -f ${info%/mod_info.json}/jars/lw_Console.jar ]] && console_jar=${info%/mod_info.json}/jars/lw_Console.jar
            break
        fi
    done
    if [[ -n $console_jar && -f $game/starsector-core/starfarer.api.jar ]]; then
        tmp="$TOOLS/helper.$$"; rm -rf -- "$tmp"; mkdir -p "$tmp/classes"
        cp="$(w "$game/starsector-core/starfarer.api.jar");$(w "$game/starsector-core/log4j-1.2.9.jar");$(w "$console_jar")"
        if "$BIN/javac.exe" --release 17 -nowarn -cp "$cp" -d "$(w "$tmp/classes")" \
                "$(w "$SRC/HotHelperModPlugin.java")" "$(w "$SRC/HotHelperCombat.java")" "$(w "$SRC/HotHelperConsole.java")" >&2 &&
            "$BIN/jar.exe" --create --file "$(w "$tmp/h.jar")" -C "$(w "$tmp/classes")" . >&2; then
            mv -n -- "$tmp/h.jar" "$TOOLS/hot-helper.jar"; echo "Built $TOOLS/hot-helper.jar" >&2
        else
            # optional: the game still starts, hot.sh console then answers NO_CONSOLE
            echo 'WARN helper: javac or jar reported errors; hot.sh console will not work' >&2
        fi
        rm -rf -- "$tmp"
    fi
fi

if [[ ! -x $TOOLS/gamectl.exe ]]; then
    csc=/c/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe
    if [[ -x $csc ]]; then
        tmp="gamectl.$$.exe"
        if (cd -- "$TOOLS" && "$csc" -nologo -optimize -r:System.Drawing.dll "-out:$tmp" "$(w "$SRC/gamectl.cs")" >&2); then
            mv -n -- "$TOOLS/$tmp" "$TOOLS/gamectl.exe"; rm -f -- "$TOOLS/$tmp"; echo "Built $TOOLS/gamectl.exe" >&2
        else
            rm -f -- "$TOOLS/$tmp"; echo 'TOOLS_FAILED gamectl: csc.exe reported errors' >&2; failed=1
        fi
    else
        echo "TOOLS_FAILED gamectl: $csc not found (.NET Framework 4 ships with Windows)" >&2; failed=1
    fi
fi
printf '%s\n' "$TOOLS"
exit $failed
