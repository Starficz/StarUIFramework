---
name: starsector-hotreload
description: Live-test a Starsector mod in a game that stays open, isolated from the user's own mods and saves. The game runs on the JetBrains Runtime with enhanced class redefinition and a reload agent, so every rebuilt jar of the mod (or of a library mod developed with it) is swapped into the running game in under a second, with no restart. Includes background input and window captures that never touch the user's mouse, keyboard or window focus (hot.sh ctl, shot), getting into a campaign (newgame, continue), and notes for driving it during UI work. Use for UI and gameplay iteration: change code, rebuild the jar, reopen the screen, capture, look. Also when the user wants to see a mod's UI in the real game, try a change without restarting, or play a build with their own mods. Windows, Git Bash.
---

# Starsector hot reload

Start one game, leave it running, and iterate: edit, rebuild the jar, the game picks it up. Version 2.0.0, used on
Starsector 0.98.5a-RC4.

- The game runs **isolated**: its own mods folder (only the mod under test, its dependencies and the mods you add),
  saves, logs and screenshots in a work folder. The user's own install, mods, saves and settings are never changed.
- The JVM is the **JetBrains Runtime 17** started with `-XX:+AllowEnhancedClassRedefinition`, so a reload may add,
  remove and change methods, fields, lambdas and supertypes, not just method bodies.
- A test-only Java agent watches the mod's built jars and swaps changed classes in.
- You drive the game in the **background**: clicks, keys and captures go to the game window only. The user keeps
  working; their mouse, keyboard and focus are never touched.
- **The game outlives the command that started it.** It stays open until the user closes it or you run
  `hot.sh stop`: across your edit and reload rounds, and for the user when you hand it to them.

For UI work, read [references/driving.md](references/driving.md) before the first click.

## Rules

1. **Ask the user for the Starsector folder.** Never search the disk for one. `setup` stores it once per machine.
2. **Never stop a Java process by name or path.** The user may run their own game or an IDE. Stop the hot game with
   `hot.sh stop` (it stops only the PID it started, after checking its command line).
3. **Never print, copy or edit licence data.** The game's licence and settings live in the Windows registry; a hot
   game serves a copy from memory and deletes it when it closes. Never read or show that copy.
4. **Never point the game at the user's real mods or saves**, and never use a build command that overwrites a jar
   the user builds themselves: build into your own output and pass it with `--jar`.
5. **Open every capture you report on.** A command that ran is not a screen that looks right.
6. **Say what you saw and what you did not.** Never write "tested" for code that only compiled.

## Setup, once per machine

```bash
HOT=<this skill folder>/scripts/hot.sh
bash "$HOT" setup "<Starsector folder>"     # the folder holding starsector-core, mods and jre
```

It ends with `HOT_SETUP OK`, or with `FAIL` lines that each say what to do. It checks:

1. **Git Bash on Windows** (Git for Windows: `curl`, `unzip`, `sha512sum`, `perl` come with it).
2. **The game folder**, stored in the machine folder for every later call.
3. **The game has run once on this Windows account**, so its licence is in the registry. If not, the user starts
   the game once through its own launcher and enters the key.
4. **The work folder resolves junctions** (the Claude desktop app redirects `%LOCALAPPDATA%`; setup handles it, or
   tells you to set `HOT_HOME` to another folder).
5. **The JetBrains Runtime 17.0.14** (JBRSDK b1367.22): downloaded once from JetBrains
   (`cache-redirector.jetbrains.com`), SHA-512 checked and trimmed to about 125 MB. Tell the user before the first
   setup that it downloads a Java runtime from JetBrains.
6. **The tools**, built from `tools/` with that runtime's JDK and Windows' own C# compiler: the reload agent, the
   licence prefs helper, the cursor guard and `gamectl.exe` (input and captures).

Everything lives in the machine folder `%LOCALAPPDATA%\starsector-hotreload` (or `HOT_HOME`): config, runtime, tools,
work folders. Nothing is written into the skill folder.

## Start

```bash
bash "$HOT" start "<mod project folder>"
```

It returns with `READY:` once the game is at the title screen (about 35 s with Fast Rendering installed), and the
game keeps running. Run it in the foreground; it detaches the game itself.

| Option | Effect |
|---|---|
| (none) | Test window: the mod, its dependencies and `--with` mods only, 1600x900, normal priority. It **opens behind** the user's windows (the first 20 s are guarded, `HOT_HIDE_SECONDS`) and stays behind: `ctl` sends only background input. The user can click it to the front themselves. For your own test loops. |
| `--front` | The same window, opened in front. For a live review the user watches. |
| `--play` | A game for the user: their enabled mod list and settings, their resolution and fullscreen choice, in front. |
| `--jar JAR` | Run this build instead of the project's own jar, and watch it. Without it the project's `mod_info.json` jars are run and watched (an IntelliJ build into `jars/` then reloads by itself). |
| `--dep FOLDER` | Also run a mod project (a library you change alongside, such as a UI framework) instead of its installed copy, and watch its jars. `--dep-jar JAR` after it gives that dep a fresh build. |
| `--with ID` | Add an installed mod by `mod_info` id. |
| `--resolution WxH` | Another test window size (check a layout at 1280x768). `newgame` and `continue` click 1600x900 positions. |
| `--debug PORT` | JDWP on `127.0.0.1:PORT` (IntelliJ: Remote JVM Debug). |
| `--no-auto` | Reload only on `hot.sh reload`. |
| `--settings` / `--no-settings` | Copy (or not) the user's `saves/common` settings. Default: copied in `--play` only. |

**Behind or in front.** A self-test (you drive the game and read the captures; the user works on) starts with no
option and stays behind: drive it only with `ctl`'s background commands, which never focus the window or touch the
user's mouse or keyboard (`ctl` refuses the others with `FOREGROUND_INPUT`). A live review (the user watches or
plays now) starts with `--front`, or `--play` for the user's own mods and display.

**Close what you will not show.** End a self-test with `hot.sh stop` once its captures are read. Leave the game open
only when you hand it to the user to look at or play: say so, and how to stop it.

The mod's dependencies must be installed in the game's `mods` folder (`MISSING_MOD:` names them). The work folder is
fixed per project (`hot.sh dir`): game log in `logs/`, captures in `captures/`, saves kept between starts. A second
`start` while the game is open says `ALREADY_OPEN`.

## The loop

1. Edit the source.
2. Rebuild the watched jar with the project's own build.
3. `bash "$HOT" reload` prints what changed. The agent has usually reloaded by itself already (a watched jar that
   changed and then stayed unchanged for 250 ms); `reload` then says `UNCHANGED: ... it was reloaded at HH:MM:SS`.
4. **Make the UI build again.** Redefined code runs on the next call; UI already on screen stays as built until
   the code that builds it runs again. StarUI panels rebuild by themselves after every reload; other UI needs its
   screen reopened, or a dev hook on the reload counter ([driving.md](references/driving.md#seeing-a-ui-change)).
5. `bash "$HOT" shot NAME` and open the PNG it prints.

| Command | Does |
|---|---|
| `hot.sh ctl CMD ARGS` | Background input, and only that: `bmove X Y`, `bclick X Y`, `brclick X Y`, `bkey NAME [N]`, `btype TEXT` |
| `hot.sh shot [NAME]` | Capture the game window to `captures/NAME.png` and print the path |
| `hot.sh wait [PATTERN] [S]` | Wait until the game log matches a pattern, or goes quiet |
| `hot.sh newgame` | From the title screen: a new small-sector game (mercenary start, no tutorial); ends `IN_CAMPAIGN` once saved |
| `hot.sh continue` | From the title screen: load the newest save of this work folder, paused; `NO_SAVE` at once when it has none |
| `hot.sh campaign` | `continue` when the work folder has a save, else `newgame`: the one command to get into a campaign |
| `hot.sh console "COMMAND"` | Run a Console Commands command in the campaign or a battle and print its output: test state without touching the project (`addcredits 50000`, `addstorypoints 5`, `runcode ...` for anything else). Needs Console Commands installed and the game started `--with lw_console`. The text goes through a file, so capitals, brackets and quotes all work |
| `hot.sh dump` | Print every StarUI panel on screen as `dumpLayout` text (dialog content included): StarUI writes it to the game log on request (StarUI 1.0.0-alpha after its first release) |
| `hot.sh status` / `log [N]` / `dir` / `stop` | Setup state (`NO_SETUP`, `NO_JBR`) then game state, reload log, work folder, close the game |

Commands without a project folder act on the one open hot game; with several open, name the project folder first.

Reload result lines (`reload`, also in `hot/reload.log` and the game log as `HotReload`):

| Line | Meaning |
|---|---|
| `RELOADED (generation N): X.jar: R redefined, A new, D removed, 0 failed, T ms` | Applied. Lists the classes. |
| `RELOAD_PARTIAL` / `RELOAD_FAILED` | Some or all classes were refused; `FAILED:` names each with the JVM's reason. The game runs the old version of those. Exit 1. |
| `UNCHANGED` | The game already runs this build. |
| `WAITING` | None of the jar's classes is loaded yet; it reloads by itself once they are. |
| `WARN ...` | See below. These are real limits, not noise. |

## What a reload cannot do

Class redefinition replaces code, not state. Static initializers and field initializers never run again:

- **New static fields** (a new top-level `val`, a new enum constant, a new `object` property, a new companion
  constant) stay 0/null. `WARN ...: new static field(s)`. Restart for these.
- **A changed static initializer** (a changed top-level `val` initializer, changed enum constructor arguments) keeps
  the old values. `const val` is inlined at its uses, so changing one works in the classes that use it.
- **New instance fields** are 0/null in objects that already exist; objects created after the reload are fine.
  Rebuild the object (reopen the screen) or restart.
- **Removed classes** stay in the game until it restarts.
- Resources (data files, sprites, `mod_info.json`, `settings.json`) are not reloaded; restart for them.
- PatchLib patches stay applied on reloaded classes and a changed patch handler reloads, but a changed patch
  **target** needs a restart (PatchLib patches a class when it loads).

Restart with `hot.sh stop` and `hot.sh start` (saves stay in the work folder; `continue` loads the newest).

The agent raises the system property `starsector.hotreload.generation` by one on every reload that changed something.
StarUI watches it and rebuilds its UI; other mods may read it (`System.getProperty` passes the mod classloader's gate)
in an `advance` to rebuild open UI. Keep such code out of release builds or make it harmless when the property is
absent.

## Problems

- `NO_SETUP`, `NO_JBR`, `NO_TOOLS`: run `hot.sh setup`.
- `MISSING_MOD`: a dependency is not installed in the game's `mods` folder; ask the user to install it.
- `START_FAILED`, `STAGE_FAILED`, `AGENT_FAILED`, `TOOLS_FAILED`: the line names the log to read (`session.log`,
  `launch.log`, `stage.log`, `logs/starsector.log` in the work folder).
- `HOTRELOAD on: ... enhanced redefinition OFF`: the game did not start on the JetBrains Runtime; only method bodies
  reload.
- The game window must stay **behind, not minimized**: a minimized window cannot be captured.
- A capture that looks wrong around rendering: Fast Rendering (`fr.jar` in `starsector-core`) is used when installed;
  check with `HOT_FAST_RENDER=0 bash "$HOT" start ...`.
- Each session runs a copy of `hot-game.sh` and the tools are built per source version, so editing this skill never
  breaks a running game.

## Layout

| Path | Purpose |
|---|---|
| `scripts/hot.sh` | Entry point: `setup`, `start`, `reload`, `ctl`, `shot`, `wait`, `newgame`, `continue`, `campaign`, `console`, `dump`, `status`, `log`, `dir`, `stop` |
| `scripts/hot-game.sh` | The session `start` runs: stages the mods, builds the mod set, launches, waits for the game to exit |
| `scripts/launch-game.sh` | Starts the game against the work folder: classpath, Fast Rendering, prefs from memory, cursor guard |
| `scripts/stage-mod.sh`, `make-modset.sh`, `find-mod.sh` | Copies of the mod's jars (the game never locks a build), the isolated mods folder, installed mods by id |
| `scripts/game-proc.sh`, `wait-log.sh` | The game's own PID only; waiting on the game log |
| `scripts/build-tools.sh`, `fetch-jbr.sh`, `lib.sh` | Tools and runtime in the machine folder; shared helpers |
| `scripts/new-game.sh` | The click sequence `newgame` runs |
| `tools/HotReloadAgent.java` | The reload agent |
| `tools/SmokePrefs.java` | Serves the game's registry preferences from memory |
| `tools/CursorGuard.java` | Stops the game moving the real cursor from a background window |
| `tools/gamectl.cs` | Background input and captures for the game window |
| `templates/hot-helper` | Data-only helper mod (`idleWhileWindowNotVisible: false`) added to test games so the game keeps running behind other windows |

Credits: the launcher, prefs helper, cursor guard, `gamectl` and staging scripts are adapted from
starsector-native-smoke 2.0.2 ([NOTICE.md](NOTICE.md)).

## Why not HotswapAgent

JBR 17 has a `-XX:HotswapAgent` flag but does not bundle the HotswapAgent jar. HotswapAgent's value is framework
plugins and reloading from class folders on the classpath; Starsector mods are jars in the game's own mod class
loader, which it does not watch. The bundled agent loads changed classes the game has not loaded yet before
redefining them (else the class loader would later supply the old version from the staged jar), and adds new classes
to the mod class loader.
