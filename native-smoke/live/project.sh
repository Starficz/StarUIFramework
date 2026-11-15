# Settings of this mod project for the starsector-native-smoke skill (smoke.sh checks, run-live-checks.sh --project).
# The runner sources this file in bash: plain assignments and the two functions, nothing else.

# slot owner, shown in smoke.sh status: letters, digits, '.', '_' and '-'
OWNER=staruilib

# ids (mod_info.json "id") of mods to enable besides this one. Dependencies named in mod_info.json files are added
# by the runner, so list only extra mods the checks want, such as lw_console
DEPS=()

# the mod's own Java package, so the profile report covers and warns about its code
PROFILE_PKG=org.starficz

# the check mod: a copy of the skill's templates/live-check-mod with this project's groups
CHECK_DIR="$PROJECT/native-smoke/live/check-mod"

# How the mod under test gets built and staged. Without stage_mod the runner links the project's folders and copies
# its built jars (it stops with STALE_JAR when a jar is older than the sources).
# With a compile gate, build with it and stage its jar, so the game runs what the gate checked. The function runs in
# the runner's shell: PROJECT is this project, STAGE the folder to fill, LIVE_SCRIPTS the skill's scripts/live.
# StarUI has no compile gate (it is built from IntelliJ): build-jar.ps1 compiles src/ plus the Java-friendly helpers in
# native-smoke/live/kotlin/ into a test-only jar next to the stage, never into the checkout.
stage_mod() {
    local jar="${STAGE%/}-build/StarUILib.jar"
    mkdir -p "${STAGE%/}-build" || return 1
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$PROJECT/native-smoke/live/build-jar.ps1")"         -OutJar "$(cygpath -w "$jar")" || return 1
    bash "$LIVE_SCRIPTS/stage-mod.sh" "$PROJECT" "$STAGE" "$jar"
}

# extra JVM arguments for the game, one per line. LAUNCH is base while the base save is made, checks for the run
# itself; GROUP holds the groups (an older check mod that reads its own property: -Dmymod.check=$GROUP on checks only)
# java_args() {
#     printf '%s\n' "-Dmymod.switch=${SWITCHES:-on}"
# }
