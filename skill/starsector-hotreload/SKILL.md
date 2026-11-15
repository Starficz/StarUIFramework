---
name: starsector-hotreload
description: Live-test a Starsector mod in a game that stays open, isolated from the user's own mods and saves. The game runs on the JetBrains Runtime with enhanced class redefinition and a reload agent, so every rebuilt jar of the mod (or of a library mod developed with it) is swapped into the running game in under a second, with no restart. Includes background input and window captures that never touch the user's mouse, keyboard or window focus (hot.sh ctl, shot), finding, clicking and scrolling UI by its text (hot.sh find, click, wheel; vanilla's and StarUI's), Console Commands for test state (hot.sh console), getting into a campaign (newgame, continue, campaign), and notes for driving it during UI work. Use for UI and gameplay iteration: change code, rebuild the jar, reopen the screen, capture, look. Also when the user wants to see a mod's UI in the real game, try a change without restarting, or play a build with their own mods. Windows, Git Bash.
---

# Starsector hot reload

One game that stays open; rebuild the jar and it reloads. Version 2.0.0, used on Starsector 0.98.5a-RC4 and RC6.

- **Isolated**: its own mods folder (the mod, its dependencies, added mods), saves, logs and captures in a work
  folder; the user's install, mods, saves and settings are never changed.
- **JetBrains Runtime 17** with `-XX:+AllowEnhancedClassRedefinition`: reloads may add, remove and change methods,
  fields, lambdas and supertypes.
- A test-only agent watches the built jars and swaps changed classes in.
- **Background driving**: input and captures go to the game window only; the user's mouse, keyboard and focus are
  never touched.
- **The game outlives the command**: open until the user closes it or `hot.sh stop`.

For UI work, read [references/driving.md](references/driving.md) before the first click.

## Rules

1. **Ask for the Starsector folder**; never search the disk. `setup` stores it per machine.
2. **Never stop Java by name or path** (the user may run a game or IDE): `hot.sh stop` stops only its own PID.
3. **Never print, copy or edit licence data** (registry; the hot game serves an in-memory copy, deleted on close).
4. **Never point the game at the user's real mods or saves**; never overwrite a jar the user builds: build into your
   own output and pass `--jar`.
5. **Open every capture you report on.**
6. **Say what you saw and what you did not**; never "tested" for compiled-only code.

## Setup, once per machine

```bash
HOT=<this skill folder>/scripts/hot.sh
bash "$HOT" setup "<Starsector folder>"     # the folder holding starsector-core, mods and jre
```

Ends `HOT_SETUP OK`, or `FAIL` lines saying what to do. Checks: Git Bash (Git for Windows: `curl`, `unzip`,
`sha512sum`, `perl`); the game folder; the game has run once on this account (licence in the registry, else the user
starts it once and enters the key); the work folder resolves junctions (Claude desktop redirects `%LOCALAPPDATA%`;
else set `HOT_HOME`); JetBrains Runtime 17.0.14 (JBRSDK b1367.22, downloaded once from JetBrains, SHA-512 checked,
~125 MB: tell the user before the first setup); the tools, built from `tools/` (agent, prefs helper, cursor guard,
`gamectl.exe`). Everything lives in `%LOCALAPPDATA%\starsector-hotreload` (or `HOT_HOME`), never in the skill folder.

## Start

```bash
bash "$HOT" start "<mod project folder>"
```

Returns `READY:` at the title screen (~35 s with Fast Rendering); the game keeps running. Run it in the foreground.

| Option | Effect |
|---|---|
| (none) | Test window: the mod, its dependencies and `--with` mods, 1600x900. **Opens and stays behind** (first 20 s guarded, `HOT_HIDE_SECONDS`); background input only. For your own test loops. |
| `--front` | Same, opened in front: a live review the user watches. |
| `--play` | For the user: their mod list, settings, resolution and fullscreen, in front. |
| `--jar JAR` | Run and watch this build instead of `mod_info.json`'s jars (an IntelliJ build into `jars/` reloads by itself without it). |
| `--dep FOLDER` | Also run a mod project (a library you change too) instead of its installed copy, watched; `--dep-jar JAR` gives it a fresh build. |
| `--with ID` | Add an installed mod by id. |
| `--resolution WxH` | Another window size (check 1280x768); `newgame` / `continue` click 1600x900 positions. |
| `--debug PORT` | JDWP on `127.0.0.1:PORT` (IntelliJ Remote JVM Debug). |
| `--no-auto` | Reload only on `hot.sh reload`. |
| `--settings` / `--no-settings` | Copy the user's `saves/common` settings or not (default: only `--play`). |

Self-tests start with no option and use only background commands (`ctl` refuses others with `FOREGROUND_INPUT`);
live reviews `--front` or `--play`. End self-tests with `hot.sh stop` once captures are read; leave it open only when
handing it over (say so and how to stop it).

Dependencies must be installed in the game's `mods` (`MISSING_MOD:` names them). The work folder is per project
(`hot.sh dir`: `logs/`, `captures/`, saves kept). A second `start` says `ALREADY_OPEN`.

## The loop

1. Edit; rebuild the watched jar with the project's build.
2. `bash "$HOT" reload` prints what changed (usually already reloaded: `UNCHANGED: ... reloaded at HH:MM:SS`).
3. **Make the UI build again**: StarUI panels rebuild themselves; other UI needs its screen reopened or a dev hook on
   the reload counter ([driving.md](references/driving.md#seeing-a-ui-change)).
4. `bash "$HOT" shot NAME`, open the PNG.

| Command | Does |
|---|---|
| `hot.sh find TEXT [--kind K] [--contains]` | Every on-screen component (vanilla's and StarUI's) whose text or StarUI key is TEXT: kind, size, position, `shown` / `hidden`, click pixel, path. Reaches holo and dialog content and panel-drawn buttons. Needs StarUI loaded (`--with staruiframework`) |
| `hot.sh click TEXT [--kind K] [--contains] [--right]` | `find`, then a real background click on the one shown match; `NOT_FOUND`, `NOT_SHOWN` or `AMBIGUOUS` instead of guessing |
| `hot.sh wheel TEXT NOTCHES [--kind K] [--contains]` | `find`, then the wheel over it (> 0 down, < 0 up) |
| `hot.sh ctl CMD ARGS` | Background input by coordinates: `bmove X Y`, `bclick X Y`, `brclick X Y`, `bwheel X Y N`, `bkey NAME [N]`, `btype TEXT`, `bcombo MOD... KEY` (chords: [driving.md](references/driving.md#input)) |
| `hot.sh shot [NAME]` | Capture to `captures/NAME.png`, print the path |
| `hot.sh wait [PATTERN] [S]` | Wait for a log pattern, or quiet |
| `hot.sh newgame` | New small-sector game from the title (mercenary, no tutorial); `IN_CAMPAIGN` once saved |
| `hot.sh continue` | Load the newest save, paused; `NO_SAVE` at once without one |
| `hot.sh campaign` | `continue` if there is a save, else `newgame` |
| `hot.sh console "COMMAND"` | A Console Commands command in campaign or battle, output printed (`addcredits 50000`, `addstorypoints 5`, `runcode ...`). Needs Console Commands and `--with lw_console`; any text works (file channel) |
| `hot.sh dump` | Every StarUI panel on screen as `dumpLayout` text (StarUI 1.0.0-alpha+) |
| `hot.sh status` / `log [N]` / `dir` / `stop` | Setup then game state; reload log; work folder; close |

Without a project folder, commands act on the one open game; with several, name the folder first.

| Reload line (`reload`, `hot/reload.log`, game log `HotReload`) | Meaning |
|---|---|
| `RELOADED (generation N): X.jar: R redefined, A new, D removed, 0 failed, T ms` | Applied; lists classes |
| `RELOAD_PARTIAL` / `RELOAD_FAILED` | Classes refused (`FAILED:` with the JVM's reason); they run the old version; exit 1 |
| `UNCHANGED` | Already this build |
| `WAITING` | No class of the jar loaded yet; reloads once they are |
| `WARN ...` | Real limits, below |

## What a reload cannot do

Code is replaced, not state; static and field initializers never rerun:

- **New static fields** (top-level `val`, enum constant, `object` property, companion constant) stay 0/null
  (`WARN ...: new static field(s)`): restart.
- **Changed static initializers** keep old values; `const val` is inlined at uses, so those update.
- **New instance fields** are 0/null in existing objects: recreate (reopen the screen) or restart.
- **Removed classes** stay until restart. Resources (data, sprites, `mod_info.json`, `settings.json`) need a restart.
- PatchLib patches stay applied and changed handlers reload; a changed patch **target** needs a restart.

Restart: `hot.sh stop`, `hot.sh start`, `continue`. The agent raises the system property
`starsector.hotreload.generation` on every changing reload; StarUI rebuilds on it, and other mods may read it in an
`advance` (keep that out of release builds or harmless without the property).

## Problems

- `NO_SETUP`, `NO_JBR`, `NO_TOOLS`: `hot.sh setup`.
- `MISSING_MOD`: a dependency isn't installed; ask the user.
- `START_FAILED`, `STAGE_FAILED`, `AGENT_FAILED`, `TOOLS_FAILED`: read the named log (`session.log`, `launch.log`,
  `stage.log`, `logs/starsector.log`).
- `HOTRELOAD on: ... enhanced redefinition OFF`: not on the JBR; only method bodies reload.
- The window must stay **behind, not minimized** (minimized can't be captured).
- Rendering looks wrong: try without Fast Rendering, `HOT_FAST_RENDER=0 bash "$HOT" start ...`.
- Sessions run a copy of `hot-game.sh` and per-version tools: editing this skill never breaks a running game.

## Layout

| Path | Purpose |
|---|---|
| `scripts/hot.sh` | Entry point: `setup`, `start`, `reload`, `ctl`, `shot`, `wait`, `newgame`, `continue`, `campaign`, `console`, `dump`, `find`, `click`, `wheel`, `status`, `log`, `dir`, `stop` |
| `scripts/hot-game.sh` | The session: stage mods, build the mod set, launch, wait for exit |
| `scripts/launch-game.sh` | Classpath, Fast Rendering, prefs from memory, cursor guard |
| `scripts/stage-mod.sh`, `make-modset.sh`, `find-mod.sh` | Jar copies (builds never locked), the isolated mods folder, mods by id |
| `scripts/game-proc.sh`, `wait-log.sh` | The game's own PID; log waiting |
| `scripts/build-tools.sh`, `fetch-jbr.sh`, `lib.sh` | Tools and runtime; helpers |
| `scripts/new-game.sh` | `newgame`'s clicks |
| `tools/HotReloadAgent.java` | Reload agent |
| `tools/SmokePrefs.java` | Registry preferences from memory |
| `tools/CursorGuard.java` | Stops a background game moving the real cursor |
| `tools/gamectl.cs` | Background input and captures |
| `templates/hot-helper` | Data-only mod (`idleWhileWindowNotVisible: false`) so the game runs behind other windows |

Credits: launcher, prefs helper, cursor guard, `gamectl` and staging scripts adapted from starsector-native-smoke 2.0.2
([NOTICE.md](NOTICE.md)).

## Why not HotswapAgent

JBR 17's `-XX:HotswapAgent` flag ships without the jar, and HotswapAgent's value is framework plugins and class
folders; Starsector mods are jars in the game's mod class loader. The bundled agent loads not-yet-loaded changed
classes before redefining (else the loader later serves the staged old version) and adds new classes to that loader.
