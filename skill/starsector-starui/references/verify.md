# Verify

A panel is done when it was seen in the game in every state and passed the review checklist. Code that compiles
proves nothing about alignment.

Contents: [The layout as text](#the-layout-as-text) · [Finding and driving](#finding-and-driving) ·
[In the game](#in-the-game) · [States to see](#states-to-see) ·
[Asking for screenshots](#asking-for-screenshots) · [Review checklist](#review-checklist) ·
[2x pixel comparison](#2x-pixel-comparison) · [Reporting](#reporting)

## The layout as text

`component.dumpLayout()` returns the tree below a component, one line per component: kind, text, size, position
relative to the component's top left (y down), how StarUI sizes it, attached handlers and the reactive scope.

```
Column 150x80 at (40, 40) size=(wrap, wrap) [onRenderBelow] scope(builds=2, reads=1)
  Text "state B" 52x15 at (0, 0) size=(wrap, wrap)
  Spacer 100x20 at (0, 20) size=(exact 100, exact 20) [onRenderBelow]
```

Sizes and positions are the current ones, so dump after a frame has rendered (from `Modifier.onFirstLayout { }`, a
debug key handler, or after a click), and log it (`Global.getLogger(MyClass::class.java).info(panel.dumpLayout())`).
A Holo's content, and so a `Dialog`'s, is listed under its holo, though it is not one of the holo's children.

Without code of your own: **`hot.sh dump`** (starsector-hotreload) prints every StarUI panel on screen, dialogs and
layers included. It sets the system property `starui.dump` to a new id; StarUI's next frame writes the dump to the
game log between `StarUI dump <id>: N StarUI panels on screen` and `StarUI dump <id> end`. A test running inside the
game (a native smoke probe) can do the same with `System.setProperty("starui.dump", "<new id>")` and read the log, or
call `dumpLayout()` on a panel it holds.

Read it for:

- components 0 wide or 0 tall, or at the same position as a sibling;
- a child whose right or bottom edge passes its container's (cut off or spilling);
- gaps that are not the spacing scale (positions are exact numbers);
- a `Text` wider than its column with worst-case data;
- `scope(builds=N)` climbing every frame: something rebuilds all the time.

The dump is cheap evidence, not the result: the look still needs a capture.

## Finding and driving

One way to find things and one way to press them, for vanilla UI and StarUI alike: find the component by its text or
key, then send real input at the part of it that is shown.

- **In a hot game** (no code): `hot.sh find TEXT` lists every component on screen with that text or key, shown or
  hidden, with the pixel to click; `hot.sh click TEXT` clicks the one shown match, `hot.sh wheel TEXT N` scrolls over
  it (starsector-hotreload's `references/driving.md`). Holo and dialog content, vanilla's title menu and dialogs, and
  vanilla's panel-drawn buttons (intel rows, filter chips) are all reached.
- **In code inside the game** (a test, a debug key): `findAll { }` and `find { }` on any panel give the same results
  as `FoundComponent`s: `kind`, `text`, `key`, `left`/`top`/`width`/`height` on the screen (y down), `isShown`,
  `visibleCenterX`/`visibleCenterY` (where a click lands), `path`, `parent`, and `findAll` again inside one:

  ```kotlin
  val screen = Global.getSettings().screenPanel
  val row = screen.find { it.key == ship.id }                    // a keyed row
  val learn = row?.find { it.kind == "Button" && it.text == "Learn" }
  check(learn != null && learn.isShown) { "Learn is not on screen" }
  ```

  `isShown` covers clipping and scrolling containers, the screen's edge and opacity (a closed holo, a faded layer);
  not what is drawn over the component. Press it with real input at `visibleCenterX`, `visibleCenterY`, so a window
  over it takes the click as it would the player's.
- **Windows in a scripted test**: `StarUITesting.instantAnimations = true` makes layers, dialogs and toasts open and
  close at once (no fades, folds or slides to wait out); `StarUITesting.windowHost = panel` opens them on a panel of
  the test's own (before any screen exists, or to keep them inside it; windows then never pause the game).
  `StarUITesting.reset()` when the test ends. Not for release code.

## In the game

The `starsector-hotreload` skill ships with StarUI: a game that stays open, swaps every rebuilt jar in, and is driven
and captured in the background. Read its `SKILL.md` and `references/driving.md`.

```bash
HOT=<starsector-hotreload>/scripts/hot.sh
bash "$HOT" setup "<Starsector folder>"    # once per machine
bash "$HOT" start "<mod project folder>"   # StarUIFramework, LazyLib and PatchLib come in as dependencies
bash "$HOT" continue || bash "$HOT" newgame
bash "$HOT" ctl bkey R; bash "$HOT" ctl bmove 5 5; bash "$HOT" shot refit
```

The loop: change the code, rebuild the mod's jar, capture, look at the PNG. In a hot game StarUI rebuilds every
live UI scope after each reload by itself (the game log says `StarUI: hot reload N, rebuilding M UI scopes`), so the
panel on screen shows the new code with its state, scroll positions and focus kept. A new `UIState` in an existing
`object` needs a restart (the reload prints a `WARN`). Code outside any builder (a `showDialog` call, a
plugin's `init`) ran once: close and reopen the dialog after changing it. A layout
exception stops the game with a "Fatal" box: `hot.sh stop`, fix, `hot.sh start`, `hot.sh continue`. To change StarUI
itself next to the mod:
`start ... --dep "<StarUIFramework folder>" --dep-jar <fresh build>`.

Your own checks run behind the user's windows: start with no option and drive with `hot.sh click`, `wheel` and
`ctl`'s background commands (`bclick`, `bmove`, `bwheel`, `bkey`, `btype`); they never focus the game or move the
user's mouse. Chords (Ctrl, Shift, Alt) do not reach a game behind other windows: the game drops modifiers while its
window is inactive. `hot.sh stop`
once the captures are read. Start with `--front` only for a live review the user watches, and leave the game open
only when you hand it to them.

## States to see

Set them from code (a debug key that sets the `UIState`s and fills a list with worst-case entries), then capture
each:

1. **Worst-case data**: the longest name, the largest numbers, a full list that scrolls, and the empty list.
2. **Every state**: one entry, selected, a blocked action with its red reason, a cost button short on stock, in
   progress, done, the confirmation popup.
3. **One tooltip open** (hover on purpose: `ctl bmove X Y`, wait a second, `shot`).
4. **1600 x 900, and once at 1280 x 768** (`hot.sh start ... --resolution 1280x768`).

Park the cursor (`ctl bmove 5 5`) before every other capture. Look at every capture against the approved board and
the checklist below, then show the user the states they care about, before and after as pairs.

## Asking for screenshots

Without a game you can run, the user is your eyes. Ask once, precisely, so one round is enough. Do not ask "does it
look right?".

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

- Say how to reach each state (a console command, a debug key, a save) and add a debug way in if there is none.
- For a change request, first ask for (or capture) the current state, so before and after can be compared.
- PNG, not a phone photo and not a JPG from a chat app, when 1 px matters.
- When the screenshots bring a complaint and no fix ("this looks lost"), do not patch the code: go back to boards
  and run an experiment round ([experiments.md](experiments.md)).

## Review checklist

Run it on captures, state by state. A reviewer subagent can run it too; give it the approved boards.

**Never acceptable**

- [ ] No text overlaps other text, an icon or a border, in any state.
- [ ] No text, icon or box is cut off, in any state. Shortened text ends in `...` and has a tooltip.
- [ ] Nothing is misaligned: shared left edges, shared right edges, equal gaps, equal row heights, text centred
      in its box to the pixel, icons centred on their tiles.

**Layout**

- [ ] Every action button is visible at 1600 x 900 and at 1280 x 768.
- [ ] Each action sits on the strip, bar or card of the thing it changes. No block of stacked buttons.
- [ ] The bottom row is pinned behind its rule; the list and the card scroll without it.
- [ ] The scroll area ends on a whole row, never on a heading; the offset is kept after a click.
- [ ] Nothing jumps when a button gets blocked or enabled, or when an optional strip appears.
- [ ] Vanilla's spacing: 3 between lines that belong together, 10 between blocks and around section headings.
- [ ] The first column of every grid shows its left border; text is 10 px from a framed block's border.
- [ ] Empty lists keep their heading and show one gray line.
- [ ] Worst case data fits: longest name, largest numbers, most icons (`+N`), longest list.

**Look**

- [ ] Black interiors, 1 px borders, no tinted tiles; fill only on the selected row, buttons, headings, table
      headers.
- [ ] No rounded corners, shadows or gradients except bars.
- [ ] Headings are vanilla's `SectionHeading`, centred.
- [ ] Bars are vanilla's (`ProgressBar`), in the right settings colour.
- [ ] Fonts: Insignia 15 for reading, Orbitron 12 for headings, tabs and buttons.
- [ ] Status markers are outlined icons with no box behind them, one per picture; wants use the quest (!).
- [ ] Skill icons are vanilla's (`SkillIcon`, `SkillPanel`): the aptitude frame, the elite frame with its stars when elite, never a
  frame drawn by hand.

**Buttons and colour**

- [ ] Every button is the faction's base label on its dark fill; only a story-point action takes the story
      colours.
- [ ] Costs are in highlight (red when short), next to or on the button.
- [ ] Cuts as vanilla: `TL_BR` for the commit and cancel row, `ALL` for a button alone, `TOP` for tabs, `BL_TR`
      for a bank of actions on the thing on screen, `BOTTOM` for a button under a block. One cut per line.
- [ ] Every grey button has its red reason line; the reason is specific.
- [ ] Tabs and actions that cannot apply are hidden, not shown empty.
- [ ] Highlight yellow only on numbers that matter; one highlight colour per line.

**Text**

- [ ] No sentence on the panel explains a feature, option or rule. Those are tooltips; panel rules are behind the
      `?` icon.
- [ ] Every remaining line of text is state, data, a red reason, a consequence needed before a click, or the one
      tagline under the name.
- [ ] Every text matches the approved text list word for word.
- [ ] Labels are short and use the game's words.
- [ ] Icons are used where the game has one; costs show icon, amount and stock.
- [ ] Tooltips show on disabled buttons too and do not cover the next button.

**Behaviour**

- [ ] Selection, tab and scroll position survive every rebuild and a round trip through a picker.
- [ ] Destroying actions open the game's confirmation popup; Cancel changes nothing; after Confirm the panel below
      shows the change.
- [ ] Motion, if any, is short, time based and can be turned off.

## 2x pixel comparison

For a widget that copies a vanilla one (bar, heading, button, frame), "looks about right" is not a result.

1. Get the vanilla widget and yours into captures at the same size and state. Best: both in one capture.
2. Crop both to the widget's full rectangle plus 2 px around.
3. Scale both by 2 or 4 with nearest neighbour (no smoothing) and put them on one strip, vanilla left
   (Pillow: `img.crop(box).resize((w * 2, h * 2), Image.NEAREST)`).
4. Compare the whole rectangle: every line, the end caps, the corners. A per pixel difference image helps
   (`ImageChops.difference(a, b)`).
5. Fix from the vanilla draw code, not by nudging colours. Repeat until the difference is noise.
6. Show the strip to the user.


## Reporting

Lead with the result. Then, in a few lines: what was seen in game (states, sizes, the captures), what was only dumped
or compiled, what was **not** seen (a state you could not reach, the small screen, a tooltip, other mods, old
saves), and open decisions with your recommendation. Never write "tested" for code that only compiled.
