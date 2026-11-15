# Buttons

Which button, colour, cut, place, label and state; where mod UI breaks the game's look most. "(engine)": measured
on 0.98a-RC8. Vanilla's banks and cuts: `vanilla_refit.png`, `vanilla_industry_picker.png` ([gallery.md](gallery.md)).

Contents: [Kinds](#kinds) · [Colour](#colour) · [Cut corners](#cut-corners) · [Sizes](#sizes) ·
[Placement](#placement) · [Labels](#labels) · [States](#states) · [Confirm](#confirm) · [Tabs](#tabs) ·
[Toggles, checkboxes, steppers](#toggles-checkboxes-steppers) · [Icons and second text on a button](#icons-and-second-text-on-a-button) ·
[Tooltips](#tooltips) · [Custom drawn buttons](#custom-drawn-buttons)

## Kinds

| Kind | StarUI | Use |
|---|---|---|
| Action | `Button(text, size) { onClick }` (defaults: faction base on dark) | Does something now |
| Tab | `Button(label, cutStyle = CutStyle.TOP)` ([recipe](recipes.md#tabs)) | Switches the view |
| Area toggle | `AreaCheckbox(text, bind, buttonGroup = modes)`, one `RadioGroup` | One of a set of modes |
| Filter bar | `AreaCheckbox(text, bind, buttonGroup = filters)`, one `FilterGroup` | Show some kinds, or all |
| Checkbox | `Checkbox(text, bind)` | On/off option |
| Stepper | `-` and `+` `Button`s (`CutStyle.ALL`) around a `Text`, in a Row | A small number |

## Colour

**Every vanilla button**: owning faction's base label on its dark fill (`VanillaColors.base` on `.dark`, the `Button`
defaults), Dismiss, Scuttle, Abort, Abandon included ([vanilla-styles.md](vanilla-styles.md#buttons)).

- **No coloured fills** (green confirm, red remove, amber cancel); no colour by state, only enabled or disabled.
- **Story points are the exception**: `Button(text, color = VanillaColors.story, backgroundColor = VanillaColors.storyDark)`
  ("Develop contact" past the limit, "Escalate crisis"); the cost is story-coloured text.
- **Another faction's panel** (market, contact) uses that faction's base and dark.
- **Cost and risk live in text**: highlight cost on or beside the label, a red blocked reason, the game's confirm
  popup for the irreversible.
- **Interaction dialogs**: story-point options via `setOptionColor`.
- **Hotkeys** in `buttonShortcut` yellow (255,210,0): `Button(shortcut = Keyboard.KEY_X)`.
- The selected one of a set is a checked area toggle (bright label, dark fill), not a colour.

## Cut corners

45° cuts say how a button relates to its neighbours ([vanilla-styles.md](vanilla-styles.md#cut-styles)):

| `CutStyle` | Vanilla uses it for | Examples |
|---|---|---|
| `TL_BR` | A dialog's or panel's commit/cancel row; menus | Cancel, Save, Confirm, Reset, Finish, Load Game |
| `ALL` | A lone button; small `-` / `+`. `addButton`'s default | Confirm, Abort, Assign, View blueprints, Take all |
| `TOP` | Tabs | Character, Fleet, Refit, Officers, Colonies, Income |
| `BL_TR` | A bank of actions on the thing on screen | refit's Strip, Undo, Autofit..., Weapon groups...; survey actions |
| `BOTTOM` | A button hanging under a block | Show on map, Lay in course, Expand all |
| `C2_MENU`, `NONE` | Combat command menu; never in a panel | |

One cut and one height per line. `Button` defaults to `TL_BR`: pass `ALL` for a lone button, `BL_TR` at a strip's
right end. Give exact sizes (`Size.exact(w, 20f)`): wrap gives one line uneven widths.

## Sizes

| Button | Height | Width |
|---|---|---|
| In intel, a tooltip or dialog content (`addButton`) | 20 | the column's, or 120 |
| In a bank or the bottom row | 20 to 25, one per line | equal in a bank, for the longest label |
| Tab | as the screen's other tabs | equal |
| Stepper | 20 | 20 |
| Host Confirm / Cancel (engine) | 25 | 150, Orbitron 20 |

- Fonts: Orbitron 12 condensed for banks (default), Orbitron 20 for a dialog's commit row, Victor 10 for tiny ones.
- The label fits in `w - h / 2` (cuts eat corners): shorten the word before shrinking the font.

## Placement

**A button sits next to what it changes**; no action blocks.

| Action | Place |
|---|---|
| Rename | No button: the name is a text field (as refit's ship name). |
| Favourite, pin, lock | A toggle (star) at the name bar's right end. |
| Change what is assigned (ship, target, person) | Right end of the assigned thing's strip: "Change...", "Move...", "Unassign". |
| Answer a request, pay for something shown | Right end of its strip; a cost button when priced. |
| Cancel a running order | Next to what runs (Recall). |
| Per-entry actions in a card list | Card's right end, bottom aligned, `TL_BR` bank. |
| Rare actions, navigation (Family tree, Service log) | Bottom row, left. |
| Remove or destroy (Dismiss, Scrap) | Bottom row, via the game's confirm popup. |
| An editor's or wizard's one commit (Save, Build) | Bottom row right; Cancel left of it. |
| A rule or rate | Not a button: the `?` icon. |

### A panel overloaded with options

Signs: more than three buttons in a bank, labels that need their subject ("Rename pet", "Change ship"), a column of
mostly buttons, "too many buttons", "cluttered".

**Features in place first**, before tabs, a second view or smaller buttons:

1. Write beside each action the thing it changes.
2. Put it on the part that shows that thing; the label drops the subject ("Change ship" → "Change..." on the strip;
   "Rename pet" → the name field).
3. An unshown subject gets a strip, with its actions.
4. Controls where they exist: text field, star, toggle or checkbox, stepper.
5. Show a strip only while its state holds; hide what never applies.
6. Whole-entry, rare and destructive actions go to the bottom row.
7. Only then: tabs, or a second view behind a bottom-row button.

Never: a "More..." menu, a second button column, an actions-only tab, shrunken buttons. Not in place: an editor's one
commit (bottom row) and multi-selection actions (a bank on the list heading). Any "are you sure" opens the game's
popup ([Confirm](#confirm)); no inline Confirm/Cancel, no "Really?".

- **Bottom row**: pinned below the column, outside the scroll area, buttons at the right end, `TL_BR`.
- **Order**: frequent or less committing left, committing right (Cancel, then Confirm); removal at the other end.
- Buttons never move on state change: grey in place; never-applicable ones are not drawn.
- Every needed button on screen at 1600 x 900 and 1280 x 768 (not only inside a scroll area).
- Keep buttons out of the tooltip area of the control above (open that tooltip left).

## Labels

- One or two words, verb first, the game's vocabulary: Train, Store, Remove, Accelerate, Use Relics.
- Capitalise like the neighbouring game text; consistent within a panel.
- No full stop, no "Click to", no object obvious from the place ("Remove").
- A cost rides on the button: "Send [supplies] 40".
- `...` only when the click opens a picker or dialog first ("Donate...").

## States

| State | Look | Rule |
|---|---|---|
| Enabled | Fill as above | |
| Hover | Engine glow | Text drawn over a button face does not glow; prefer the button's own label |
| Disabled, with reason | Grey fill and label, one red reason line | Always; a grey button without reason is a bug. In a strip, the strip's second line; under a lone button, 3 px (`pad`) below |
| Disabled, short on stock | Grey, amount in red | Tooltip shows cost against stock |
| Disabled, obvious | Grey | Only when the reason is on screen (stepper at 0) |
| Not applicable | Not drawn | Can never apply to this entry |

Always reserve the reason's space (a strip's second line; 3 + 20 px under a lone button) so nothing jumps
([recipe](recipes.md#guarded-button)). No `enabled` argument: set `isEnabled` from state read in the builder.

## Confirm

**Every confirmation is the game's popup**: a small centred window, question, Confirm and Cancel. Never custom, never
inline.

- For destructive or irreversible actions (Dismiss, Scrap, Abort with a lost fee); not for one-click undoable ones.
- Text: the question with the name in highlight ("Dismiss Ilse Varga?"), at most one consequence line. Insignia 21,
  no title.
- (engine) Confirm left, Cancel right, 150 x 25, bottom right; 520 x 185 window for 500 x 130 content.
- The opening button is normal, no `...`.
- StarUI: a `Dialog`, content 500 x 130, `confirmText`, `cancelText = "Cancel"`, question in `Text(font = Font.INSIGNIA_21)`
  ([setup.md](setup.md#a-dialog)); stacks over any panel or dialog. (engine) `CampaignUIAPI.showConfirmDialog` works
  only with no dialog open.
- Mockups: its own cut-out on the states board (`k.popup`).

## Tabs

- `CutStyle.TOP`, one height.
- Selected: bright label, opaque dark fill. Others: dark fill at 60%, base label.
- Never-applicable tabs are hidden, not greyed.
- The selected tab is view state; it survives rebuilds.

## Toggles, checkboxes, steppers

- **Area toggle bank** (2 to 5 exclusive modes): equal widths, 22 px high, 5 apart; checked: dark fill, bright label;
  others black with 1 px border; an optional 4 px mode-colour strip at the left; tooltips say what each mode does.
  `AreaCheckbox`es in a `RadioGroup`, each `Size.fillMaxWidth(height = 22f)` in a Row. Not `FilterGroup` (filters only).
- **Checkbox**: independent on/off, label right, 20 px line.
- **Stepper**: `-`, value (highlight above 0, gray at 0), `+`; buttons grey at the limits.
- No dropdowns in the game: toggle bank or picker dialog. Text fields for names only; numbers get steppers.

## Icons and second text on a button

**The cost button is the standard price**: icon left of the label, amount in highlight after it ("Send [supplies] 40",
"Gene scan [relic] 10"); no cost row, no sentence.

- One icon, one number; two resources or a change to see first: cost rows beside the button.
- Size it for the largest amount.
- Short: grey button, red amount, cost against stock in the tooltip.
- The engine's button holds one text: icon and labels are laid over it in a `Box` ([recipe](recipes.md#cost-button));
  the overlay does not glow on hover (accepted).

## Tooltips

- Every button whose label and place don't say it all: what it does, costs, lore.
- They show on disabled buttons (StarUI sets `setShowTooltipWhileInactive`); hover a grey one on a capture.
- The blocked reason is also on the panel (red line).
- A button opening the confirm popup says in its tooltip what is lost.

## Custom drawn buttons

For picture tiles (submarket, faction, slot) or restyling inside a vanilla screen; lessons from rebuilding the
submarket buttons.

- **Tile from icon**: 4 + 64 + 4 = 72; icon centred on whole pixels (1 to 2 px offsets get reported).
- **Vanilla's frame cuts corners by 3 px, but its fill and disabled overlay are square**: a fill behind a cut frame
  must be cut too (octagon).
- **Selected as rings, not fill**: three 1 px rings following the cuts, outside in: black, owner's base colour at
  full saturation and value (grays under 3% saturation keep brightness), white tinted 10% toward it. Never yellow or
  bright for selected.
- **Ring corners**: shrink the cut by `sqrt(2) - 1` per ring inward.
- **Guard quads** `w > 0 && h > 0` (zero sizes draw a stray line).
- **Draw in `Modifier.onRender { alpha -> }`** (screen coordinates, y up: plugin `x`, `y`, `width`, `height`); raw GL
  in `glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT)` / `glPopAttrib()`; multiply alphas by
  `alpha`; clicks via `Modifier.onClick` on the same component.
- **Behaviour changes to vanilla controls become settings** (names On hover / On click / Never; highlight Off /
  Bright band / Animated); read once per build, never per frame.
- Hover and unfold motion: [motion.md](motion.md).
