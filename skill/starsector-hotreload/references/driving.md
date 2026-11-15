# Driving a hot game for UI work

Reach a screen, act on it and capture it without touching the user's mouse, keyboard or focus. Coordinates are
client pixels of the **1600x900 test window** (measured on 0.98.5a-RC4, checked on RC6, no mods changing these
screens); measure anything else on a capture.

Contents: [The loop for a panel](#the-loop-for-a-panel) · [Seeing a UI change](#seeing-a-ui-change) ·
[Reaching a screen](#reaching-a-screen) · [Making test state](#making-test-state) ·
[Input](#input) · [Captures](#captures) · [Finding where to click](#finding-where-to-click) ·
[Waiting](#waiting) · [Checking a layout](#checking-a-layout) · [Limits](#limits)

## The loop for a panel

```bash
HOT=<skill>/scripts/hot.sh
bash "$HOT" start "<mod folder>"               # once; READY at the title screen
bash "$HOT" continue || bash "$HOT" newgame    # into the campaign (newgame the first time)
bash "$HOT" click Refit --kind Button          # open the screen the panel lives on (or ctl bkey R)
bash "$HOT" find "Apply" --contains            # is it there, shown, where: read before clicking
bash "$HOT" ctl bmove 5 5                      # park the cursor, so no tooltip covers the shot
bash "$HOT" shot refit-before                  # open the PNG it prints
# edit, rebuild the jar: the game reloads it by itself (hot.sh reload prints what changed)
bash "$HOT" ctl bkey ESCAPE; bash "$HOT" ctl bkey R    # reopen: built UI stays as built until it is made again
bash "$HOT" ctl bmove 5 5; bash "$HOT" shot refit-after   # StarUI rebuilds by itself: skip the reopen
```

Show before and after as pairs. Keep the game open between rounds; `hot.sh stop` when done unless handing it over.

## Seeing a UI change

A reload swaps code; UI on screen shows it when its building code runs again:

| UI | After a reload |
|---|---|
| **StarUI** (1.0.0-alpha+) | Rebuilt by itself within a fraction of a second, state, scroll and focus kept (log: `StarUI: hot reload N, rebuilding M UI scopes`) |
| Other UI | Old code until rebuilt: a state change reruns its container; reopening a tab rebuilds panels made there; campaign UI, title and combat only when vanilla remakes them (load, restart) |

The counter is the system property `starsector.hotreload.generation` (`"0"` before mods load, +1 per changing
reload; absent in normal games; readable by mods).

1. **StarUI**: nothing to do.
2. **Other UI**: a dev-only rebuild hook in an `advance` while it is open (does nothing without the agent):

   ```kotlin
   private var seenGeneration: String? = System.getProperty("starsector.hotreload.generation")

   override fun advance(amount: Float) {
       val g = System.getProperty("starsector.hotreload.generation")
       if (g != seenGeneration) { seenGeneration = g; rebuildPanel() }   // your panel's own rebuild
   }
   ```

   Or reopen the screen (`ctl bkey ESCAPE`, its hotkey).
3. **Capture after every reload** (`ctl bmove 5 5`, `shot NAME`).
4. **Restart** after a `WARN` (new static fields, changed static initializers): a new `val` in an existing `object` or
   file (a new `UIState`) stays `null`. New `object`s or classes are fine. New sprites and data need a restart.
5. Size changes: read `panel.dumpLayout()` (debug key, `logs/starsector.log`) or `hot.sh dump`.
6. StarUI itself next to a mod: `start ... --dep "<StarUILib folder>" --dep-jar <fresh build>`.

## Reaching a screen

| Screen | How |
|---|---|
| Title screen | Where `start` ends. Continue (1233, 283), Missions (1235, 362), New Game (1222, 396), Codex (1248, 463), Settings (1237, 497) |
| Campaign | `hot.sh campaign` (`continue` if a save exists, else `newgame`, ~1 min); both end paused; `continue` alone says `NO_SAVE` at once |
| Core tabs | Tab bar at y 885: Character (75), Fleet (202), Refit (330), Crew/Cargo (458), Map (586), Intel (714), Command (842); hotkeys C, F, R, I, Tab, E, D; `bkey ESCAPE` closes |
| Pause | `bkey SPACE` toggles; keep the campaign paused while capturing |
| Refit simulator | Refit, dismiss the notice the first time (Dismiss, 1075 511), Run simulation (682, 825), deploy, `bkey SPACE` |
| Officer level-up | Fleet tab, an empty captain portrait (825, 300), the officer's "Level up!"; give the pending level with `hot.sh console` |
| A market | `hot.sh console "goto jangala"`, click the planet, unpause briefly; options by number keys (`bkey 3` trade) |

## Making test state

Game state (credits, story points, an officer to level, a fleet) comes from Console Commands, never temporary project
code:

```bash
bash "$HOT" start "<project>" --jar "<build>" --with lw_console   # once: Console Commands in the test game
bash "$HOT" campaign                                              # a campaign: the newest save, else a new game
bash "$HOT" console "addcredits 50000"
bash "$HOT" console "addstorypoints 5"
bash "$HOT" console 'runcode Console.showMessage("" + Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy().size());'
```

- Prints the output, then `CONSOLE_DONE`. `help [command]`; `runcode` takes any Java statement (starsector-knowledge
  has a runcode guide). `spawnfleet pirates 30`, `goto <entity>` set up encounters.
- Runs in campaign or battle (the title's background battle answers "campaign-only"). `NOT_RUN`: get into the campaign.
- `NO_CONSOLE`: not installed, or not started `--with lw_console`. (The agent passes the request on; the helper mod runs
  it on the game thread, `tools/HotHelper*.java`.)
- Setup needed every time (a fixed officer, a test fleet): a `--dep` test-only mod with a `ModPlugin`, outside the project.

## Input

**Click by name** (no coordinates; moved buttons are still hit):

```bash
bash "$HOT" find "Bounties" --contains            # every match: kind, size, position, shown or hidden, click point
bash "$HOT" click "Bounties (11)"                 # the one shown match, or NOT_FOUND / NOT_SHOWN / AMBIGUOUS
bash "$HOT" click "Confirm" --contains --kind Button   # vanilla adds the shortcut to the text: "Confirm [G]"
bash "$HOT" wheel "Personal Bounty - Ra Han" 5    # turn the wheel 5 notches down over that row's list
```

- TEXT matches a label's, button's or field's text, or a StarUI key, exactly; `--contains`: case-insensitive part.
  `--kind`: StarUI names (`Button`, `Text`, `Column`) or vanilla's `Button`, `Label`, `TextField`, `Panel`,
  `ScrollPanel`. Panel-drawn buttons (intel rows, filter chips) are found by their labels; clicking one clicks the
  button.
- Searches like StarUI's `findAll`: panels, scroll panels, StarUI containers, holo and dialog content (vanilla's title
  menu and dialogs too), panel-drawn buttons' content.
- `hidden`: scrolled away, clipped, off screen or transparent; not what is drawn over it. The click is real input
  (a modal over it takes it); capture to check.
- Needs StarUI loaded (`--with staruilib` for other mods); `NO_ANSWER`: no StarUI panel rendered yet.
- Listed positions: screen units, top left, y down; `click=(x, y)`: capture pixels.

`ctl` posts window messages to the game window, by coordinates:

| Command | Does |
|---|---|
| `ctl bmove X Y` | Move the game's mouse (hover) |
| `ctl bclick X Y` / `ctl brclick X Y` | Left / right click |
| `ctl bwheel X Y N [MOD]...` | Wheel N notches over X, Y (> 0 down) |
| `ctl bkey NAME [N]` | Key press: `ESCAPE`, `ENTER`, `SPACE`, `TAB`, `BACK`, `F1`-`F12`, `A`-`Z`, `0`-`9`, `UP`, ... |
| `ctl bcombo MOD... KEY` | Key with modifiers (`bcombo CTRL SHIFT U`): active window only |
| `ctl btype TEXT` | Lowercase letters, digits, space, `-`, `.`, `/` |

- **Move before clicking**: the first click sometimes only updates hover; `bmove` near it, wait half a second, click.
- **No modifiers in the background**: the game clears Ctrl, Shift and Alt every frame its window is inactive
  (`Display.isActive()`), and LWJGL drops a Shift the keyboard doesn't hold. Shift/Ctrl+click, chords like
  Ctrl + Shift + U, and capitals are out of reach; use a console command or modifier-free debug key, or ask the user
  in a `--front` game. A Shift held at the PC can capitalise typed letters.
- **No drags**; the plain wheel works.
- Number keys pick interaction dialog options (`bkey 2`).
- A clicked fleet member is picked up (drag): click its slot again to put it back.

## Captures

- `hot.sh shot NAME` writes `captures/NAME.png` (client area) and prints the path; open and look.
- **Park the cursor first** (`ctl bmove 5 5`); with a modal open, park on an empty spot inside it (outside may not reach
  the tooltip's owner). Check shots for leftover tooltips. Tooltip shots: `bmove X Y`, wait a second, `shot`.
- `hot.sh dump`: every StarUI panel as text (positions, texts) without a screenshot.
- Behind other windows, never minimized (black capture).
- 1 px alignment on a 2x crop: rectangle plus 2 px, nearest-neighbour
  (`img.crop((x, y, x + w, y + h)).resize((w * 2, h * 2), Image.NEAREST)`), vanilla's crop beside yours.
- Captures stay in the work folder; say which ones you reported on.

## Finding where to click

- By name: `hot.sh find` / `click` ([Input](#input)).
- No text (icon, empty slot): measure from a labelled neighbour or a keyed row, or on a capture (capture = client pixels).
- In-game code: StarUI's `findAll { }` (`isShown`, `visibleCenterX`, `path`).

## Waiting

- `hot.sh wait PATTERN [SECONDS]`: a log line written after the call; no pattern: the log goes quiet (never while the
  campaign runs: pause).
- After a click opening a screen, half a second to a second; capture to confirm.
- Stuck: capture first (a dialog, crash box or title screen says more than the log).
- `CRASHED:` or `ERROR` in `logs/starsector.log`: read them first.

## Checking a layout

Capture every state the design names (worst case, empty, selected, blocked, a tooltip) and check: no overlaps or cut
text; shared edges, design spacing, text centred to the pixel; the second size (`hot.sh stop`, `start --resolution
1280x768`; click positions above are 1600x900 only). Unreachable states: set from code (a debug key filling worst-case
entries).

## Limits

- One target at a time: no drags, no modifiers behind other windows.
- `newgame` / `continue` click fixed 1600x900 positions with no mods changing title or new-game screens; otherwise
  measure and use `ctl`.
- F12 screenshots don't save from background presses: use `shot`.
- Fast Rendering now and then hung a game right after a load in background runs (last frame frozen, no CPU): restart.
