# Verify

Done means seen in game in every state and through the review checklist. Compiling proves nothing about layout.

Contents: [The layout as text](#the-layout-as-text) · [Finding and driving](#finding-and-driving) ·
[Scripted checks](#scripted-checks) · [In the game](#in-the-game) · [States to see](#states-to-see) ·
[Asking for screenshots](#asking-for-screenshots) · [Review checklist](#review-checklist) ·
[2x pixel comparison](#2x-pixel-comparison) · [Reporting](#reporting)

## The layout as text

`component.dumpLayout()`: one line per component below it: kind, text, size, position from its top left (y down),
sizing, handlers, scope.

```
Column 150x80 at (40, 40) size=(wrap, wrap) [onRenderBelow] scope(builds=2, reads=1)
  Text "state B" 52x15 at (0, 0) size=(wrap, wrap)
  Spacer 100x20 at (0, 20) size=(exact 100, exact 20) [onRenderBelow]
```

Dump after a frame (`Modifier.onFirstLayout { }`, a debug key, after a click) and log it. Holo and `Dialog` content is
listed under its holo. Without code: **`hot.sh dump`** prints every StarUI panel on screen (it sets the property
`starui.dump`; StarUI logs between `StarUI dump <id>: ...` and `StarUI dump <id> end`). In-game tests can set the
property themselves.

Look for: 0-sized components or siblings at one position; children past their container's edge; gaps off the
spacing scale; worst-case `Text` wider than its column; `scope(builds=N)` climbing every frame. The dump is evidence,
not the result: the look needs a capture.

## Finding and driving

Find by text or key, press with real input at the shown part; vanilla UI and StarUI alike.

- **Hot game, no code**: `hot.sh find TEXT` lists matches (shown or hidden) with click pixels; `hot.sh click TEXT`
  clicks the one shown match; `hot.sh wheel TEXT N` scrolls over it (starsector-hotreload `references/driving.md`).
  Reaches holo and dialog content, vanilla's title menu and dialogs, panel-drawn buttons (intel rows, filter chips).
- **In-game code** (test, debug key): `findAll { }` / `find { }` on any panel return `FoundComponent`s: `kind`,
  `text`, `key`, screen `left`/`top`/`width`/`height` (y down), `isShown`, `visibleCenterX`/`Y` (click point),
  `path`, `parent`, nested `findAll`:

  ```kotlin
  val screen = Global.getSettings().screenPanel
  val row = screen.find { it.key == ship.id }                    // a keyed row
  val learn = row?.find { it.kind == "Button" && it.text == "Learn" }
  check(learn != null && learn.isShown) { "Learn is not on screen" }
  ```

  `isShown` covers clipping and scrolling ancestors, the screen edge and opacity (closed holo, faded layer), not what
  is drawn over it. Press with real input at the visible centre, so an overlying window takes it as it would.
- **Scripted window tests**: `StarUITesting.instantAnimations = true` (no fades, folds, slides);
  `StarUITesting.windowHost = panel` (windows open on the test's panel and never pause); `StarUITesting.reset()` at
  the end. Not for release code.

## Scripted checks

For pass or fail lines instead of driving by hand (a popup only one screen has): `starsector-native-smoke`, if
installed (its own `SKILL.md`).

- Open the screen first (a tab, a dialog), then build or find the panel: UI tied to a screen closes or never builds
  while that screen is not current. `StarUIManager.currentCoreUITabPanel` reaches the open tab's panel.
- Test code runs in the game's script loader, which refuses `java.lang.reflect`: call the mod's own entry points
  and StarUI, never reflection. Don't swallow exceptions in checks; a `SecurityException` in a `catch` looks like a
  missing component.

## In the game

`starsector-hotreload` is its own skill folder, installed next to this one: read its `SKILL.md` and
`references/driving.md`.

```bash
HOT=<starsector-hotreload>/scripts/hot.sh
bash "$HOT" setup "<Starsector folder>"    # once per machine
bash "$HOT" start "<mod project folder>"   # StarUIFramework, LazyLib and PatchLib come in as dependencies
bash "$HOT" continue || bash "$HOT" newgame
bash "$HOT" ctl bkey R; bash "$HOT" ctl bmove 5 5; bash "$HOT" shot refit
```

- Loop: change code, rebuild the jar, capture, look. StarUI rebuilds every live scope after a reload (log:
  `StarUI: hot reload N, rebuilding M UI scopes`), keeping state, scroll and focus.
- Restart for a new `UIState` in an existing `object` (reload prints `WARN`). Code outside builders (`showDialog`, a
  plugin's `init`) ran once: reopen it.
- A layout exception shows a "Fatal" box: `hot.sh stop`, fix, `start`, `continue`.
- StarUI changes next to the mod: `start ... --dep "<StarUIFramework folder>" --dep-jar <fresh build>`.
- Your checks run behind the user's windows: drive with `hot.sh click` / `wheel` and `ctl`'s `bclick`, `bmove`,
  `bwheel`, `bkey`, `btype` (never focus or mouse). Chords (Ctrl, Shift, Alt) cannot reach an inactive window.
  `hot.sh stop` after reading captures; `--front` only for live reviews; leave it open only when handing it over.

## States to see

Set them from code (a debug key setting the `UIState`s and filling worst-case data), capture each:

1. **Worst case**: longest name, largest numbers, a full scrolling list, the empty list.
2. **Every state**: one entry, selected, blocked with red reason, cost short, in progress, done, confirm popup.
3. **One tooltip open** (`ctl bmove X Y`, wait a second, `shot`).
4. **1600 x 900, once at 1280 x 768** (`--resolution 1280x768`).

Park the cursor (`ctl bmove 5 5`) before other captures. Check each against the approved board and the checklist;
show the user the relevant states as before and after pairs.

## Asking for screenshots

Without a runnable game, ask once and precisely (never "does it look right?"):

```
To check the panel I need screenshots of these states, please. Full game window, not cropped or resized,
UI scale 100%, at 1600 x 900 (one extra at 1280 x 768 if you can):

1. <Panel> open with <the entry that has the longest name> selected
2. The list scrolled to the bottom
3. <Tab 2>
4. <The blocked case: how to get there>, so the grey button and the red line show
5. After clicking <Dismiss> (the confirmation popup showing)
6. Mouse resting on <control> until the tooltip shows
7. The empty case: <how to get there>

Please keep the mouse off the panel for 1 to 5.
```

- Say how to reach each state (console command, debug key, save); add a debug way if none.
- For a change, first get the current state for before and after.
- PNG, not phone photos or chat JPGs, when 1 px matters.
- A complaint without a fix from screenshots: back to boards, run a round ([experiments.md](experiments.md)).

## Review checklist

Per state, on captures (a reviewer subagent with the approved boards can run it).

**Never acceptable**

- [ ] No text overlaps text, icons or borders, in any state.
- [ ] Nothing cut off; shortened text ends in `...` with a tooltip.
- [ ] Nothing misaligned: shared edges, equal gaps and row heights, text centred to the pixel, icons centred.

**Layout**

- [ ] Every action button visible at 1600 x 900 and 1280 x 768.
- [ ] Each action on its subject's strip, bar or card; no stacked buttons.
- [ ] Bottom row pinned behind its rule; list and card scroll without it.
- [ ] Scroll areas end on a whole row, never a heading; offset kept after clicks.
- [ ] Nothing jumps when a button blocks or an optional strip appears.
- [ ] Spacing 3 within groups, 10 between blocks and around headings.
- [ ] Every grid's first column shows its left border; text 10 px from framed borders.
- [ ] Empty lists keep their heading and one gray line.
- [ ] Worst-case data fits (names, numbers, `+N` icons, list length).

**Look**

- [ ] Black interiors, 1 px borders, no tinted tiles; fills only on selected row, buttons, headings, table headers.
- [ ] No rounded corners, shadows or gradients (bars excepted).
- [ ] Headings are `SectionHeading`, centred.
- [ ] Bars are `ProgressBar` in the right settings colour.
- [ ] Fonts: Insignia 15 for reading, Orbitron 12 for headings, tabs, buttons.
- [ ] Status markers: outlined icons, no box, one per picture; wants use the quest (!).
- [ ] Skill icons are vanilla's (`SkillIcon`, `SkillPanel`): aptitude frame, elite frame with stars; never hand-drawn.

**Buttons and colour**

- [ ] Every button faction base on dark; only story-point actions in story colours.
- [ ] Costs in highlight (red when short), on or beside the button.
- [ ] Cuts: `TL_BR` commit row, `ALL` lone, `TOP` tabs, `BL_TR` action bank, `BOTTOM` under a block; one per line.
- [ ] Every grey button has a specific red reason.
- [ ] Inapplicable tabs and actions hidden.
- [ ] Highlight only on numbers that matter; one per line.

**Text**

- [ ] No explaining sentences on the panel (tooltips; rules behind `?`).
- [ ] Remaining text: state, data, red reasons, a consequence before a click, the one tagline.
- [ ] Text matches the approved list word for word.
- [ ] Short labels in the game's words.
- [ ] Icons where the game has them; costs show icon, amount, stock.
- [ ] Tooltips show on disabled buttons and don't cover the next button.

**Behaviour**

- [ ] Selection, tab and scroll survive rebuilds and a picker round trip.
- [ ] Destroying actions open the confirm popup; Cancel changes nothing; Confirm shows the change.
- [ ] Motion is short, time based, can be turned off.

## 2x pixel comparison

For a copy of a vanilla widget (bar, heading, button, frame), "about right" is not a result.

1. Capture vanilla's and yours at the same size and state (best in one capture).
2. Crop each to its rectangle plus 2 px.
3. Scale x2 or x4 nearest-neighbour onto one strip, vanilla left
   (`img.crop(box).resize((w * 2, h * 2), Image.NEAREST)`).
4. Compare everything (lines, caps, corners); `ImageChops.difference(a, b)` helps.
5. Fix from vanilla's draw code, not by nudging colours, until the difference is noise.
6. Show the strip.

## Reporting

Result first. Then: what was seen in game (states, sizes, captures), what was only dumped or compiled, what was
**not** seen (unreached states, small screen, tooltips, other mods, old saves), open decisions with your
recommendation. Never "tested" for compiled-only code.
