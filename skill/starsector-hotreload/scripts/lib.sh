#!/usr/bin/env bash
# Sourced by this skill's scripts: the machine folder, its settings, paths and the built tools.
#   . "$HERE/lib.sh"
#   hot_root          HOT_ROOT_DIR = the machine folder (%LOCALAPPDATA%\starsector-hotreload, or HOT_HOME): config,
#                     the JetBrains Runtime, the built tools, the work folders. HOT_WIN = 1 in Git Bash on Windows
#   hot_cfg KEY       REPLY = a setting from HOT_ROOT_DIR/config, empty when unset (hot.sh setup writes them)
#   hot_cfg_set K V   stores a setting
#   hot_posix_path P  REPLY = C:\x or C:/x as /c/x
#   hot_mod_info DIR  REPLY = DIR/mod_info.json without its '#' comment lines (loose JSON, read with patterns)
#   hot_real_dir DIR  REPLY = DIR as it is stored on disk: the Claude desktop app (MSIX) redirects %LOCALAPPDATA% to
#                     its package folder, and a junction to the redirected path does not resolve for Java or cmd
#   hot_jbr           REPLY = the JetBrains Runtime folder (as stored on disk); fails when it is not installed
#   hot_tools         REPLY = the folder for the tools built from this copy of the skill (one per source version,
#                     since a running game keeps its tools locked)
#   hot_version       REPLY = this skill's version
# Every function is safe under set -eu. Parts adapted from starsector-native-smoke 2.0.2 (see NOTICE.md).
HOT_LIB=${BASH_SOURCE[0]%/*}
[[ $HOT_LIB == /* ]] || HOT_LIB="$PWD/$HOT_LIB"
HOT_LIB=${HOT_LIB//\/.\//\/}; HOT_LIB=${HOT_LIB%/.}
HOT_SKILL=${HOT_LIB%/scripts}

hot_posix_path() {
    local p=${1//\\//}
    if (( ${HOT_WIN:-0} )) && [[ $p =~ ^([A-Za-z]):(/.*)?$ ]]; then
        if [[ -d /${BASH_REMATCH[1],,} ]]; then p="/${BASH_REMATCH[1],,}${BASH_REMATCH[2]}"; else p=$(cygpath -u -- "$p"); fi
    fi
    REPLY=$p
}

hot_root() {
    [[ -n ${HOT_ROOT_DIR:-} ]] && return 0
    HOT_WIN=0
    if command -v tasklist.exe >/dev/null 2>&1 && [[ -r /proc/$$/winpid ]]; then HOT_WIN=1; fi
    if [[ -n ${HOT_HOME:-} ]]; then
        hot_posix_path "$HOT_HOME"
    elif (( HOT_WIN )) && [[ -n ${LOCALAPPDATA:-} ]]; then
        hot_posix_path "$LOCALAPPDATA/starsector-hotreload"
    else
        REPLY="/tmp/starsector-hotreload-${USER:-$(id -un)}"
    fi
    HOT_ROOT_DIR=$REPLY
    [[ -d $HOT_ROOT_DIR ]] || mkdir -p "$HOT_ROOT_DIR" || { echo "NO_MACHINE_FOLDER: cannot create $HOT_ROOT_DIR" >&2; return 2; }
    return 0
}

hot_cfg() {
    local k v
    REPLY=
    hot_root || return 0
    [[ -r $HOT_ROOT_DIR/config ]] || return 0
    while IFS='=' read -r k v || [[ -n $k ]]; do
        [[ $k == "$1" ]] && REPLY=${v%$'\r'}
    done < "$HOT_ROOT_DIR/config"
    return 0
}

hot_cfg_set() {
    hot_root || return 1
    local f="$HOT_ROOT_DIR/config"
    { [[ -r $f ]] && grep -v "^$1=" "$f"; printf '%s=%s\n' "$1" "$2"; } > "$f.tmp" && mv -f -- "$f.tmp" "$f"
}

hot_mod_info() {
    local line
    REPLY=
    [[ -r $1/mod_info.json ]] || return 1
    while IFS= read -r line || [[ -n $line ]]; do
        [[ $line =~ ^[[:space:]]*# ]] || REPLY+=$line$'\n'
    done < "$1/mod_info.json"
    return 0
}

hot_real_dir() {
    local dir=${1%/} base rel mark hit
    REPLY=$dir
    (( ${HOT_WIN:-0} )) && [[ -n ${LOCALAPPDATA:-} && -d $dir ]] || return 0
    hot_posix_path "$LOCALAPPDATA"; base=${REPLY%/}; REPLY=$dir
    [[ ${dir,,} == "${base,,}"/* && ${dir,,} != "${base,,}"/packages/* ]] || return 0
    rel=${dir:${#base}}
    mark=".hot-where-$$-$RANDOM"
    : > "$dir/$mark" 2>/dev/null || return 0
    for hit in "$base"/Packages/*/LocalCache/Local"$rel/$mark"; do
        [[ -e $hit ]] && { REPLY=${hit%/*}; break; }
    done
    rm -f -- "$dir/$mark"
    return 0
}

hot_jbr() {
    hot_root || return 1
    [[ -x $HOT_ROOT_DIR/jbr-17/bin/java.exe ]] || return 1
    hot_real_dir "$HOT_ROOT_DIR/jbr-17"
}

hot_tools() {
    local sum
    hot_root || return 1
    sum=$(cat "$HOT_SKILL"/tools/* | cksum) || return 1
    REPLY="$HOT_ROOT_DIR/tools/${sum%% *}"
    [[ -d $REPLY ]] || mkdir -p "$REPLY" || return 1
}

hot_version() {
    REPLY=unknown
    [[ -r $HOT_SKILL/VERSION ]] && { read -r REPLY < "$HOT_SKILL/VERSION" || true; }
    return 0
}
