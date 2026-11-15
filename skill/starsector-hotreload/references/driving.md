# Driving a hot game for UI work

How to reach a screen, act on it and capture it without touching the user's mouse, keyboard or focus. Coordinates
below are client pixels of the **1600x900 test window**, measured on 0.98.5a-RC4 with no mods that change these
screens. Measure anything else on a capture.

Contents: [The loop for a panel](#the-loop-for-a-panel) · [Seeing a UI change](#seeing-a-ui-change) ·
[Reaching a screen](#reaching-a-screen) ·
[Input](#input) · [Captures](#captures) · [Finding where to click](#finding-where-to-click) ·
[Waiting](#waiting) · [Checking a layout](#checking-a-layout) · [Limits](#limits)

## The loop for a panel

```bash
HOT=<skill>/scripts/hot.sh
bash "$HOT" start "<mod folder>"               # once; READY at the title screen
bash "$HOT" continue || bash "$HOT" newgame    # into the campaign (newgame the first time)
bash "$HOT" ctl bkey R                         # open the screen the panel lives on (here: refit)
bash "$HOT" ctl bmove 5 5                      # park the cursor, so no tooltip covers the shot
bash "$HOT" shot refit-before                  # open the PNG it prints
# edit, rebuild the jar: the game reloads it by itself (hot.sh reload prints what changed)
bash "$HOT" ctl bkey ESCAPE; bash "$HOT" ctl bkey R    # reopen: built UI stays as built until it is made again
bash "$HOT" ctl bmove 5 5; bash "$HOT" shot refit-after   # StarUI rebuilds by itself: skip the reopen
```

Show the user before and after captures as pairs. Keep the game open between your rounds; `hot.sh stop` when the
self-test is done, unless you hand the game to the user to look at.

## Seeing a UI change

A reload swaps the code; it does not by itself rebuild UI that is already on screen. A panel shows the new code the
next time the code that builds it runs. Tested on 0.98.5a-RC4 (Kotlin 2.2):

| UI | After a reload |
|---|---|
| **StarUI** (1.0.0-alpha and later) | Rebuilt by itself: StarUI sees the agent's counter and rebuilds every live UI scope within a fraction of a second, keeping state, scroll positions and focus. The game log says `StarUI: hot reload N, rebuilding M UI scopes`. Nothing to do |
| Any other UI | Old code until its building code runs again: a state change re-runs the container that reads it; vanilla building the screen again (close and reopen a tab) rebuilds a panel made on that screen; the campaign UI, the title screen and combat only when vanilla makes them again (load the save, restart) |

The counter is the system property `starsector.hotreload.generation`: the agent sets it to `"0"` before any mod loads
and raises it by one on every reload that changed code. Mods may read it (`System.getProperty` passes the mod
classloader's gate); in a normal game it is absent.

For a panel you are working on:

1. **StarUI**: nothing. Rebuild the jar and capture.
2. **Other UI**: rebuild the panel when the counter changes, in an `advance` that runs while it is open. Dev only;
   take it out before a release (without the agent the property is absent and this does nothing):

   ```kotlin
   private var seenGeneration: String? = System.getProperty("starsector.hotreload.generation")

   override fun advance(amount: Float) {
       val g = System.getProperty("starsector.hotreload.generation")
       if (g != seenGeneration) { seenGeneration = g; rebuildPanel() }   // your panel's own rebuild
   }
   ```

   Without it, close and reopen the screen (`ctl bkey ESCAPE`, then its hotkey).
3. **Capture after every reload**: `ctl bmove 5 5`, then `shot NAME`; show before and after as pairs.
4. **Restart** (`hot.sh stop`, `hot.sh start`, `hot.sh continue`) when the reload prints `WARN` for new static
   fields or a changed static initializer: a new `val` in an existing `object` or file (a new `UIState` there) stays
   `null` until then, so the panel throws or shows nothing. A new `object` or class is fine without a restart. New
   sprites and data files need a restart too.
5. **Read the layout as text** after a reload when the change is about sizes: log `panel.dumpLayout()` from a debug
   key and read it in the work folder's `logs/starsector.log` ([Finding where to click](#finding-where-to-click)).
6. **Developing StarUI itself** next to a mod: `start ... --dep "<StarUIFramework folder>" --dep-jar <fresh build>`;
   its classes reload the same way.

## Reaching a screen

| Screen | How |
|---|---|
| Title screen | Where `start` ends. Continue (1233, 283), Missions (1235, 362), New Game (1222, 396), Codex (1248, 463), Settings (1237, 497) |
| Campaign | `hot.sh continue` (the newest save of this work folder) or `hot.sh newgame` (about a minute; makes the save `continue` loads later). Both end paused |
| Core tabs | From the campaign, the tab bar at y 885: Character (75), Fleet (202), Refit (330), Crew/Cargo (458), Map (586), Intel (714), Command (842). `bkey R` opens refit. `bkey ESCAPE` closes a tab |
| Pause | `bkey SPACE` toggles it. Keep the campaign paused while capturing: it logs and moves while running |

A panel a mod builds when vanilla builds a screen shows new code the next time vanilla builds it
([Seeing a UI change](#seeing-a-ui-change)).

## Input

`ctl` posts window messages to the game window only:

| Command | Does |
|---|---|
| `ctl bmove X Y` | Move the game's mouse (hover) |
| `ctl bclick X Y` / `ctl brclick X Y` | Left / right click |
| `ctl bkey NAME [N]` | A key press: `ESCAPE`, `ENTER`, `SPACE`, `TAB`, `BACK`, `F1`-`F12`, `A`-`Z`, `0`-`9`, `UP`, ... |
| `ctl btype TEXT` | Type lowercase letters, digits, space, `-`, `.`, `/` |

- **Move before you click.** The first click on a control sometimes only updates hover. `bmove` a few pixels away,
  wait half a second, then `bclick`.
- **No modifier keys.** Ctrl, Shift and Alt cannot be held: Shift+click and Ctrl+click are out of reach, and typed
  text has no capitals or Shift characters. If the person at the PC holds Shift while you type, letters can come out
  as capitals.
- **No drags and no mouse wheel** in the background. Test scrolling with code (set a scroll position, read it back)
  or ask the user to scroll.
- Number keys pick options in an interaction dialog (`bkey 2`).

## Captures

- `hot.sh shot NAME` writes `captures/NAME.png` (the client area, 1600x900) and prints its path. Open it with the
  image tool and look; a command that ran proves nothing.
- **Park the cursor first** (`ctl bmove 5 5`) or a tooltip may cover the shot. For a tooltip shot, hover on purpose:
  `ctl bmove X Y`, wait a second, `shot`.
- The window must be behind other windows, not minimized. If a capture is black, ask the user not to minimize it.
- Check 1 px alignment on a 2x crop, not by eye on the full frame: crop the panel's rectangle plus 2 px and scale it
  by 2 with nearest-neighbour (no smoothing), for example with Pillow
  (`img.crop((x, y, x + w, y + h)).resize((w * 2, h * 2), Image.NEAREST)`). Put a vanilla widget's crop next to
  yours when the panel copies one.
- Captures stay in the work folder. Tell the user which ones you reported on.

## Finding where to click

- Measure on a capture: capture pixels are client pixels.
- A StarUI panel can tell you exactly: `panel.dumpLayout()` lists every component's size and position relative to the
  panel's top left, y down. Dumped from a `StarUIPlugin` screen panel (full screen, at 0, 0) at UI scale 100%, the
  positions are capture pixels: click at `x + width / 2, y + height / 2`. Log the dump
  (`Global.getLogger(MyClass::class.java).info(panel.dumpLayout())`, for example from a debug key handler or
  `Modifier.onFirstLayout { }`) and read it in the work folder's `logs/starsector.log`.

## Waiting

- `hot.sh wait PATTERN [SECONDS]` waits for a log line written after the call; `hot.sh wait` with no pattern waits
  for the log to go quiet (a load finished). Quiet never comes while the campaign runs unpaused: pause first.
- After a click that opens a screen, half a second to a second is enough; capture to confirm.
- If something looks stuck, capture first: a dialog, a crash box or the title screen explains more than the log.
- `CRASHED:` or `ERROR` lines in `logs/starsector.log` mean the game hit an exception; read them before anything else.

## Checking a layout

For each panel, capture every state the design names (worst-case data, empty, selected, blocked, a tooltip open)
and check:

- no text overlaps text, an icon or a border; nothing is cut off;
- shared edges line up, gaps match the spacing the design asks for, text is centred in its box to the pixel;
- the panel at the second size: `hot.sh stop`, then `start --resolution 1280x768` (click positions above are for
  1600x900 only).

Set states from code where clicks cannot reach them (a debug key that fills a list with worst-case entries).

## Limits

- One click target at a time: no drag, wheel or modifier keys (above).
- `newgame` and `continue` click fixed positions of the 1600x900 window with no mods that change the title or new
  game screens. With such mods, measure on a capture and use `ctl`.
- The game's F12 screenshot key does not save a file from a background key press. Use `shot`.
- Fast Rendering, when installed, now and then hung a game right after a load in background runs: the window keeps
  its last frame and the process uses no CPU. Stop and start again.
