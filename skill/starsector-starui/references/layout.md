# Layout: hosts, sizes, archetypes, contract

Where a panel lives, how big it may be, its overall shape, and the contract every panel is built against. Attaching:
[setup.md](setup.md#where-ui-can-go). "(engine)": measured on 0.98a-RC8.

Contents: [Hosts](#hosts) · [Screen sizes](#screen-sizes) · [Archetypes](#archetypes) ·
[Layout contract](#layout-contract) · [View state](#view-state) · [Coordinates](#coordinates)

## Hosts

The smallest host that holds the feature; say which and why.

| Host | Use it when | Cost |
|---|---|---|
| **A line or section in an existing tooltip** | A few values about a thing that already has a tooltip | None; often right |
| **Interaction dialog with a side panel** | A place or person to visit, a few actions, flavour matters; text and options left, your panel right | Lowest; feels most like the game |
| **Custom dialog** | A management panel: lists, a detail column, several actions | The default for list plus actions |
| **Intel panel** | Checked from anywhere, or reports over time | Bound to the intel layout |
| **A vanilla screen** | Belongs on refit, a core tab, combat, the title screen | `StarUIPlugin` for whole screens; sub-panels need `inject` (can break on updates) |
| **A layer over a screen** | Its own window over any screen, opened from a button there | `Layer` / `showLayer` ([setup.md](setup.md#a-layer-over-a-screen)): modal, dimmed, pausing, Escape dismisses, stacking |

### Stock interaction dialog with a side panel

Prose and numbered options left, a visual panel right: `dialog.visualPanel.showStarUIPanel(w, h) { }` (board:
`example_sidepanel`). Prefer it over a custom dialog when options do the navigation (Leave, Talk to, Back) and the
panel needs few buttons; the feature has a voice (secretary, dockmaster, a lore line per visit); the content is cards
or one summary.

It is one column of about 600 px: a list plus detail, a table with more than four value columns, or rows over ~560 px
(14 skill icons, a ship list) need a custom dialog opened from a dialog option.

- Keep the height to what fits at 1280 x 768.
- Vanilla draws nothing behind it (the map shows through); `showStarUIPanel` draws black (`background`).
- Don't rebuild the left side's text and options in your panel.
- Story-point options use the story colour; disabled options are gray with the reason in their tooltip (rules or the
  dialog plugin, not panel code).
- Dialog text scrolls away: persistent state belongs in the panel.
- Same parts and rules as any panel (title line with `?`, cards or rows, actions on their cards).

### Dialog window

`Dialog(onDismissRequest, size, confirmText, cancelText) { dialog -> }` in a builder, `showDialog(size, ...)` from a
handler: vanilla's custom dialog on a modal layer, over any screen and other dialogs ([setup.md](setup.md#a-dialog)).
`size` is the content's: wrap (default), exact or fill; never within 50 px of the screen edge (larger shrinks).

- (engine) Window `(w + 20) x (h + 55)` inside a 7 px frame; content 10 px in; buttons 150 x 25, `TL_BR`, Orbitron 20,
  bottom right, Cancel in the corner, Confirm left of it.
- Host buttons: "Return" when it only closes, "Confirm" plus "Cancel" when it commits as a whole.
- Dialogs stack: a confirmation is a `Dialog` in the panel's builder. (engine) Vanilla's `showCustomDialog` allows one
  per interaction dialog and `showConfirmDialog` does nothing while a dialog is open; `Dialog` has neither limit.
- Vanilla pickers (cargo, fleet member) opened from dialog content open above it; untested with `Dialog`.

### Intel panel and tooltips

`IntelInfoPlugin.createLargeDescription(panel, w, h)` gives a `CustomPanelAPI`: build StarUI into it. The small list
entry and tooltips are `TooltipMakerAPI` content: vanilla's calls. One flowing column; design rules hold.

## Screen sizes

- Design at **1600 x 900**, check at **1280 x 768**; every action button on screen at both.
- Large custom dialogs: 1000 to 1200 wide, 640 to 720 high at 1600 x 900.
- Fix non-shrinking widths first (full icon rows, number columns, buttons), then split the rest (14 icons of 32 px in
  one row: a 500 px detail column, whatever that leaves the list).
- Width shrinks in the flexible column (the name), never number columns or buttons; height in scroll areas, never
  pinned blocks.
- Fixed parts don't fit at 1280 x 768: too much pinned; move it into the scroll area or a tab.
- Mock up one small-screen board for large panels.

## Archetypes

By the vanilla screen with the same kind of content ([gallery.md](gallery.md)):

| The content is | Archetype | Vanilla |
|---|---|---|
| Entries compared by value | Table, detail or summary beside or below | industry picker, custom production, colony list |
| Entries to pick from, with pictures or controls | List left, selection's detail right | intel screen, officer picker |
| Things known by face, one or two values | Picture tiles, detail in a tooltip or beside | colony screen |
| One thing with several settings | Editor: stat rows with `-` / `+`, action banks | refit |
| A report or event | One flowing column: headings, paragraphs, a bar, a small table, one button | intel event |
| A fixed sequence of choices | Numbered dialog options with a side panel for state | any dialog |

Blocks 10 apart under centred section headings, lines 3 apart; commit row bottom right `TL_BR`; action banks `BL_TR`.

## Layout contract

Every panel is built to this; reviewers check it line by line.

1. **Sizes from the panel, not text**: `Size.exact` for what can't shrink (number columns, buttons, icon rows), fill
   (`fillMaxWidth()`, `Axis.FillMax(weight)`) for the flexible part, so nothing moves when text changes. Wrapped
   text (exact or fill width) is measured; what follows goes below. Anchors (`AnchorPanel`, or `CustomPanel` as the
   escape hatch) only where the design places things freely.
2. **No overlap or cut text with worst-case data** (40-char names, 9-digit credits, 300-day counters, 14 skills,
   30 colonies, 200 entries, empty): one-line text `overflow = TextOverflow.Ellipsis`; wrapped text has an exact or
   fill width; number columns fit the widest value (measured); icon rows two rows, then `+N`.
3. **Lists scroll and keep their offset**, ending on a whole block, never a heading (StarUI keeps offsets).
4. **Actions with what they change, and visible**; the commit row pinned outside the scroll area; every action visible
   at both screen sizes.
5. **States keep the layout**: blocked buttons grey with a red reason (reserved line or tooltip); empty lists keep
   their heading and a gray line; hidden optional blocks don't move pinned ones.
6. **Vanilla's spacing**: 3, 5, 10 ([design-rules.md](design-rules.md#spacing-and-alignment)).
7. **Look**: black interiors; fills only on selected row, buttons, heading bars, table headers; colours and fonts via
   accessors; buttons faction base on dark, story colours for story points; vanilla's cuts ([buttons.md](buttons.md)).
8. **Tooltip rule**: explanations on the control; panel rules behind `?`
   ([design-rules.md](design-rules.md#text-on-the-panel-and-the-tooltip-rule)).
9. **Confirmations are the game's popup**: no inline pair, no custom modal.
10. **Every view reachable from code** through its `UIState`s (tab, selected entry, sub view), so tests and debug
    commands can show each state.

## View state

See [reactive.md](reactive.md).

- One `UIState` per thing deciding what shows (tab, selected id, sub view), in an object outside the UI; nothing else
  decides.
- Handlers change them; reading builders rerun next frame, reusing objects (scroll, focus, hover survive).
- Changing game data is mirrored from `onAdvance` only when it changed (a day passed, a bar moved a pixel).
- Each block is a small component function (`fun UIPanelAPI.TeamRow(team: Team)`) building only its part.

## Coordinates

- StarUI layout, anchors, `dumpLayout()`: parent's top left, y down.
- Rendering (`onRender`, `onRenderBelow`): screen coordinates, **y up**: plugin `x`, `y` (bottom), `width`, `height`.
- `getMouseX()` / `getMouseY()` and `PositionAPI`: the same virtual coordinates, y up.
- `glScissor` needs real pixels: multiply by `getScreenScaleMult()`.
