# Layout: hosts, sizes, archetypes, contract

Where a panel lives, how big it may be, which overall shape it takes, and the contract every panel is built
against. How StarUI attaches to each host: [setup.md](setup.md#where-ui-can-go). "(engine)" marks numbers measured
on 0.98a-RC8.

Contents: [Hosts](#hosts) · [Screen sizes](#screen-sizes) · [Archetypes](#archetypes) ·
[Layout contract](#layout-contract) · [Rebuild pattern and view state](#rebuild-pattern-and-view-state) ·
[Coordinates](#coordinates)

## Hosts

Pick the smallest host that holds the feature. Say which one you picked and why.

| Host | Use it when | Cost |
|---|---|---|
| **A line or section in an existing tooltip** | The feature only shows a few values about a thing that already has a tooltip | None. Often the right answer. |
| **Stock interaction dialog with a side panel** | The player visits a place or person; a few actions; flavour text matters. The dialog's text and numbered options stay on the left, your panel sits on the right. | Lowest of the real panels. Feels most like the game. |
| **Custom dialog** | A management panel: lists, a detail column, several actions | The default for anything with a list plus actions |
| **Intel panel** | Something the player checks from anywhere, or that reports over time | Bound to the intel screen's layout |
| **A vanilla screen** | The feature belongs on a vanilla screen (refit, a core tab, combat, the title screen) | `StarUIPlugin` covers whole screens with no patch; a sub-panel needs `inject` from a PatchLib hook, which can break on game updates |
| **A layer over a screen** | A window of its own over the current screen (any screen: title, campaign, a dialog, a battle), opened from a button there | `Layer` in a builder, `showLayer` elsewhere ([setup.md](setup.md#a-layer-over-a-screen)): modal, dimmed, pausing, Escape to dismiss; layers stack |

### Stock interaction dialog with a side panel

The default interaction dialog is a full UI on its own: prose on the left, numbered options below it, and a
visual panel on the right that normally shows a picture. `dialog.visualPanel.showStarUIPanel(w, h) { }` puts a
StarUI panel there (vanilla's `showCustomPanel` underneath). The generator's example `example_sidepanel` board shows the result.

Use it before reaching for a custom dialog when:

- the options list can do the navigation (Leave, Talk to, Back) and the panel only needs a few buttons of its
  own;
- the feature has a voice: a secretary, a dockmaster, a line of lore per visit;
- the content is a list of cards or one summary, not a list plus a detail column.

Tiebreak with the custom dialog: the side panel is one column of about 600 px. If the feature needs a list
plus a detail column, a table with more than four value columns, or any row wider than about 560 px (14 skill
icons, a ship list), it does not fit: use a custom dialog and open it from a dialog option, so the place and
its voice stay in the dialog and the management happens in the panel.

Notes:

- About 600 px wide. Keep the height to what fits at 1280 x 768.
- Vanilla draws nothing behind a custom panel there: the campaign map and its outlines would show through.
  `showStarUIPanel` gives it a black background (its `background` parameter).
- The left side has no chrome: plain text and options. Do not rebuild it inside your panel.
- Options that cost a story point use the story colour; disabled options are gray with the reason in the
  option's tooltip. That is the dialog's own behaviour (rules or the dialog plugin), not panel code.
- Text printed to the dialog scrolls away. State that must stay visible belongs in the panel.
- The panel follows the same parts and rules as any other: title line with `?`, cards or rows, each action
  on the card it belongs to.

### Dialog window

`Dialog(onDismissRequest, size, confirmText, cancelText) { dialog -> }` in a builder, `showDialog(size, ...)` from
a click handler: vanilla's custom dialog window on a modal layer, over any screen and over other dialogs
([setup.md](setup.md#a-dialog)). `size` is the content's: wrap by default (the window fits the content), exact, or
fill (as large as the screen allows); it never comes closer than 50 px to the screen's edge.

- (engine) The window is `(w + 20) x (h + 55)` inside a 7 px frame; your content is `w x h` 10 px in; the
  buttons are 150 x 25, `TL_BR`, Orbitron 20, bottom right, Cancel in the corner and Confirm left of it.
- Size it from the screen: `w = min(designWidth, screenWidth - 100)`, same for height. Use
  `Global.getSettings().getScreenWidth()` and `getScreenHeight()`; they are in virtual pixels and already
  account for the player's UI scale.
- Name the host button for what it does: "Return" when the dialog only closes, "Confirm" plus "Cancel" when
  the dialog commits as a whole.
- Dialogs stack: the confirmation over a panel is a `Dialog` in the panel's builder. (engine) Vanilla's own
  `showCustomDialog` allows one per interaction dialog and `showConfirmDialog` does nothing while a dialog is open;
  neither limit applies to `Dialog`.
- Vanilla pickers (cargo, fleet member) opened from a dialog's content are vanilla dialogs on the same screen
  panel, so they open above it; not run yet with StarUI's `Dialog`.

### Intel panel and tooltips

An intel's large description gets a `CustomPanelAPI` (`IntelInfoPlugin.createLargeDescription(panel, w, h)`):
StarUI builds into it. Its small list entry and tooltips are `TooltipMakerAPI` content; keep those to vanilla's
own calls. The design rules hold; the layout is a single flowing column.

## Screen sizes

- Design at **1600 x 900**. Check at **1280 x 768**. Every action button must be on screen at both.
- A typical large custom dialog: 1000 to 1200 wide, 640 to 720 high at 1600 x 900.
- Fix the widths of things that cannot shrink first (an icon row that must show a full set, number columns,
  buttons), then split what is left: a detail column that must show 14 icons of 32 px in one row is 500 px wide,
  whatever that leaves for the list.
- Width shrinks first in the list's flexible column (the name), never in number columns or buttons.
- Height shrinks in the scroll areas. Pinned blocks keep their height.
- If the fixed parts alone do not fit at 1280 x 768, the panel has too much pinned content: move something
  into the scroll area or behind a tab.
- Show one board at the small size in the mockups when the panel is large.

## Archetypes

Pick one from what the content is, by the vanilla screen that holds the same kind of content
([gallery.md](gallery.md)).

| The content is | Archetype | Vanilla |
|---|---|---|
| Entries the player compares by their values | Table, with the detail or a summary beside or below it | industry picker, custom production, colony list |
| Entries to pick one from, each with a picture or controls | List and detail: a list left, the selected entry's detail right | intel screen, officer picker |
| Things known by face, one or two values each | Picture tiles, the detail in a tooltip or beside them | colony screen |
| One thing with several settings | Editor: stat rows with `-` / `+`, banks of actions | refit |
| A report or an event | One flowing column: headings, paragraphs, a bar, a small table, one button | intel event |
| A fixed sequence of choices | The interaction dialog's numbered options, with a side panel for state | any dialog |

- Fix the widths that cannot shrink first (number columns, buttons, an icon row that must show a full set), then
  give the rest to the flexible column.
- Space blocks 10 apart with a section heading over each group (centred), lines 3 apart, as vanilla does.
- The commit and cancel row sits at the bottom right in `TL_BR`; a bank of actions on the thing on screen in
  `BL_TR`.

## Layout contract

Every panel is built to this contract. A reviewer checks a panel against it line by line.

1. **Sizes from the panel, not from text.** Fixed widths (`Size.exact`) for what cannot shrink (number columns,
   buttons, icon rows), a fill (`Size.fillMaxWidth()`, `Axis.FillMax(weight)`) for the flexible part, so no
   element moves when a text changes. Wrapped text (a `Text` with an exact or fill width) is measured by StarUI and
   what follows is placed below it. Anchors (an `AnchorPanel`, or a `CustomPanel` as the escape hatch) only where
   the design places things freely.
2. **No overlap and no cut text for worst case data**: 40 character names, 9 digit credits, 300 day counters,
   14 skills, 30 colonies, 200 list entries, and the empty list.
   - One line text is cut to its box with `...`, the full text in a tooltip: `Text(..., overflow = TextOverflow.Ellipsis)`.
   - Wrapped text has an exact or fill width; StarUI places what follows below its measured height.
   - A number column is as wide as the widest value it can hold, measured with the game.
   - An icon row is two rows at most, then `+N`.
3. **Lists scroll and keep their offset.** A scroll column ends on a whole block and never on a heading.
   The scroll offset survives every rebuild (StarUI keeps it).
4. **Actions sit with what they change and are visible.** Each action is next to the thing it changes; the
   commit and cancel row is pinned outside the scroll area. Every action button is visible at 1600 x 900 and at
   1280 x 768.
5. **States keep the layout.** A blocked button is grey with its reason in red (on a reserved line, or in its
   tooltip). An empty list keeps its heading and one gray line. A hidden optional block does not move pinned
   blocks.
6. **Vanilla's spacing.** 3 between lines that belong together, 5 for small gaps, 10 between blocks and around a
   section heading ([design-rules.md](design-rules.md#spacing-and-alignment)).
7. **Look.** Black interiors; fill only for the selected row, buttons, heading bars and table header strips.
   Fonts and colours through accessors, never raw RGB. Every button the faction's base on dark, story-point actions
   in the story colours; cuts as vanilla uses them ([buttons.md](buttons.md)).
8. **Tooltip rule.** Explaining text is a tooltip on the control it explains; panel rules sit behind the `?`
   icon ([design-rules.md](design-rules.md#text-on-the-panel-and-the-tooltip-rule)).
9. **Confirmations are the game's popup.** No Confirm and Cancel pair in place, no custom modal.
10. **Every view is reachable from code** through its `UIState`s (selected tab, selected entry, open sub view),
   so a test or a debug command can show each state for a screenshot.

## View state

StarUI replaces the manual rebuild: see [reactive.md](reactive.md).

- One `UIState` per thing that decides what is shown: selected tab, selected entry id, open sub view. Keep them in
  an object outside the UI. Nothing else decides what is shown.
- Click handlers change those states; the builders that read them run again on the next frame, reusing the native
  objects. Scroll offsets, focus and hover survive.
- Game data that changes over time is mirrored into a state from `onAdvance` only when it changed (a day passed, a
  bar moved by a pixel), not every frame.
- Each block is a small component function (`fun UIPanelAPI.TeamRow(team: Team)`) that builds only its own part.
  Blocks do not know about each other.

## Coordinates

- StarUI layout, anchors and `dumpLayout()` measure from the parent's top left, y down.
- Rendering (`Modifier.onRender`, `onRenderBelow`) uses screen coordinates with **y up**: the plugin's `x`, `y`
  (the bottom edge), `width`, `height`.
- `SettingsAPI.getMouseX()` / `getMouseY()` and `PositionAPI` share the same virtual coordinates, y up.
- `glScissor` needs real pixels: multiply by `getScreenScaleMult()`.
