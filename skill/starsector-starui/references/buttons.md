# Buttons

Which button, in which colour and shape, where, with which label and in which state. "(engine)" marks numbers
measured on 0.98a-RC8. Buttons are where mod UI
most often breaks the game's look. `vanilla_refit.png` and `vanilla_industry_picker.png` in
[gallery.md](gallery.md) show vanilla's banks, cuts and placement.

Contents: [Kinds](#kinds) · [Colour](#colour) · [Cut corners](#cut-corners) · [Sizes](#sizes) ·
[Placement](#placement) · [Labels](#labels) · [States](#states) · [Confirm](#confirm) · [Tabs](#tabs) ·
[Toggles, checkboxes, steppers](#toggles-checkboxes-steppers) · [Icons and second text on a button](#icons-and-second-text-on-a-button) ·
[Tooltips](#tooltips) · [Custom drawn buttons](#custom-drawn-buttons)

## Kinds

| Kind | StarUI | Use |
|---|---|---|
| Action button | `Button(text, size) { onClick }` (faction base label on dark fill, the defaults) | Does something now |
| Tab | `Button(label, cutStyle = CutStyle.TOP)` per tab ([recipe](recipes.md#tabs)) | Switches the view |
| Area toggle | `AreaCheckbox(text, bind, buttonGroup = modes)` per mode, one `RadioGroup` | One of a set of modes |
| Filter bar | `AreaCheckbox(text, bind, buttonGroup = filters)` per filter, one `FilterGroup` | Show some kinds, or all |
| Checkbox | `Checkbox(text, bind)` | An on/off option |
| Stepper | Two small `Button`s (`-`, `+`, `CutStyle.ALL`) with a `Text` value between, in a Row | A small number |

## Colour

**Every vanilla button has the same colours**: the owning faction's base UI colour for the label and its dark UI
colour for the fill (`VanillaColors.base` on `VanillaColors.dark` for the player's own panels, the `Button`
defaults). Vanilla does this for every button whose colours its code sets, Dismiss, Scuttle, Abort and Abandon
included ([vanilla-styles.md](vanilla-styles.md#buttons)).

- **No coloured fills.** Not green for confirm, not red for remove, not amber for cancel. A button never changes
  colour by state; it is enabled or disabled.
- **The one exception: story points.** An action that spends a story point takes the story colours:
  `Button(text, color = VanillaColors.story, backgroundColor = VanillaColors.storyDark)`, as vanilla's "Develop
  contact" above the contact limit and "Escalate crisis" are. The cost itself is story-coloured text.
- **A panel that belongs to another faction** (a market, a contact) uses that faction's base and dark colours for
  its buttons, as vanilla's intel and comms panels do.
- **What the click costs or risks is in the text**: the cost in highlight (on the button's label or beside it),
  a red reason line when it is blocked, and the game's confirmation popup for what cannot be undone.
- **In an interaction dialog** the option that spends a story point is story-coloured (`setOptionColor`).
- **Hotkeys** are shown in `buttonShortcut` yellow (255,210,0), vanilla's most used engine colour: StarUI's
  `Button(shortcut = Keyboard.KEY_X)` draws it.
- The selected one of a set is not a colour: it is a checked area toggle (bright label, dark fill).

## Cut corners

Vanilla buttons have 45 degree cuts. Which corners are cut says how the button relates to its neighbours.
Vanilla's engine uses them like this ([vanilla-styles.md](vanilla-styles.md#cut-styles)):

| `CutStyle` | Vanilla uses it for | Examples |
|---|---|---|
| `TL_BR` | The commit and cancel row of a dialog or panel; menus | Cancel, Save, Confirm, Reset, Finish, Load Game |
| `ALL` | A button that stands alone; small `-` / `+` buttons. The default of `TooltipMakerAPI.addButton` | Confirm, Abort, Assign, View blueprints, Take all |
| `TOP` | Tabs | Character, Fleet, Refit, Officers, Colonies, Income |
| `BL_TR` | A bank of actions on the thing on screen | refit's Strip, Undo, Autofit..., Weapon groups...; the survey panel's actions |
| `BOTTOM` | A button hanging under a block | Show on map, Lay in course, Expand all |
| `C2_MENU`, `NONE` | The combat command menu; never in a panel | |

All buttons of one line have the same cut and the same height.

StarUI's `Button` defaults to `TL_BR` (the commit row). Pass `CutStyle.ALL` for a button that stands alone, and
`BL_TR` for the buttons at the right end of a strip (a bank of actions on the thing it shows). Give buttons an exact
size (`Size.exact(w, 20f)`): the default wraps the label, so buttons of one line come out in different widths.

## Sizes

| Button | Height | Width |
|---|---|---|
| Button in intel, a tooltip or a dialog's content (vanilla's `addButton`) | 20 | the column's width, or 120 |
| Button in a bank or the bottom row | 20 to 25, one height per line | equal inside one bank, sized for the longest label |
| Tab | as the screen's other tabs | equal |
| Stepper button | 20 | 20 |
| Host Confirm / Cancel (engine) | 25 | 150, Orbitron 20 |

- Vanilla's fonts for buttons: Orbitron 12 condensed for banks of small buttons (the `Button` default), Orbitron 20
  for a dialog's commit row, Victor 10 for tiny ones.
- The label must fit with half the button height to spare (`w - h / 2`), because the cuts eat the corners. Fit
  the label and shorten the word before shrinking the font.

## Placement

**A button sits next to the thing it changes.** There is no action block: a stack of buttons under the content
makes the player map each button back to its subject.

| Action | Place |
|---|---|
| Rename | No button: the name is a text field, as refit's ship name is. |
| Mark as favourite, pin, lock | A toggle on the name bar's right end (a star). |
| Change what is assigned (a ship, a target, a person) | Right end of the strip that shows the assigned thing: "Change...", "Move...", "Unassign". |
| Answer a request, pay for something that is shown | Right end of the strip that shows the request, as a cost button when it has a price. |
| Cancel a running order | Next to the thing that runs (Recall). |
| Act on each entry of a card list | Right end of the card, bottom aligned, in a `TL_BR` bank. |
| Rare actions, navigation to another view (Family tree, Service log) | Bottom row, left. |
| Remove or destroy the entry (Dismiss, Scrap) | Bottom row, opening the game's confirmation popup. |
| The one commit of an editor or wizard (Save, Build) | Bottom row, right end; Cancel left of it. |
| A rule or a rate | Not a button: the `?` help icon. |

### A panel overloaded with options

Signs: more than three buttons in one bank or stack; a label that needs its subject to make sense ("Rename
pet", "Change ship"); a column that is mostly buttons; the user says "too many buttons", "cluttered",
"overloaded".

**Features in place is the first move**, before tabs, a second view or smaller buttons. It fixed the pet
screen, and it is the best first move for most overloaded panels:

1. Write next to every action the thing it changes: the name, the ship, the request, the entry, the panel.
2. Put each action on the part that shows that thing (table above). The label loses the subject: "Rename
   pet" becomes the name field, "Change ship" becomes "Change..." on the ship strip.
3. A subject that is not shown yet gets a strip, and its actions with it.
4. Turn buttons into controls where one exists: rename is a text field, favourite a star, an option a
   toggle or checkbox, an amount a stepper.
5. Show a strip only while its state holds (a request strip exists while a request is open). Hide what can
   never apply to this entry.
6. What is left acts on the entry as a whole: rare and destructive actions go to the bottom row.
7. Only if the card is still too full: tabs, or a second view behind a bottom row button.

Never: a "More..." menu, a second button column, a tab that holds only actions, buttons made smaller to fit.

Where in place is not the answer: the one commit of an editor or wizard (it belongs to the whole form, bottom
row), and actions on several selected entries (a bank on the list's heading line).

In place is about where the button sits, never about the confirmation. Any "are you sure" action, wherever
its button is, opens the game's confirmation popup ([Confirm](#confirm)). No Confirm and Cancel pair on the
strip, no button that turns into "Really?".

- **The bottom row** is pinned at the bottom of its column, outside the scroll area, its buttons at the right end
  as vanilla's dialogs put them, in a `TL_BR` row.
- **Order in a strip or bank, left to right:** the more frequent or less committing action left, the
  committing one right (vanilla: Cancel, then Confirm).
- A removing action stands apart from what starts or confirms: the other end of the row.
- A button never moves when state changes. If it cannot be used it greys out in place. A button that can
  never apply to this entry is not drawn (Gene scan on a pet that is fully read).
- Every button is on screen at 1600 x 900 and at 1280 x 768: strips in a scroll area must not be the only
  way to reach an action the player needs every visit.
- Do not put a button where a tooltip of the control above it will cover it (open that tooltip to the left).

## Labels

- One or two words, verb first, in the game's vocabulary: Train, Store, Remove, Accelerate, Use Relics.
- Capitalise the way the neighbouring game text does. Do not mix "Lay course" and "Lay Course" in one panel.
- No full stop, no "Click to", no object when it is obvious from the place ("Remove", not "Remove officer from
  training").
- A cost rides on the button as icon plus number ("Send [supplies] 40"), see below.
- `...` after a label only when the click opens a picker or dialog and does nothing yet ("Donate...").

## States

| State | Look | Rule |
|---|---|---|
| Enabled | Fill as above | |
| Hover | The engine's glow | Text you draw over a button face does not take the glow; prefer the button's own label |
| Disabled, with a reason | Grey fill, grey label, one red reason line | Always. A grey button without a reason is a bug. In a strip the reason takes the strip's second line (in place of the gray one); under a button that stands alone it is a line 3 px (`pad`) below. |
| Disabled, short on stock | Grey fill, the amount on the button in red | The tooltip shows the cost against the stock |
| Disabled, obvious | Grey | Only when the reason is on screen already (stepper at 0) |
| Not applicable | Not drawn | A tab or action that cannot apply to this entry at all |

Reserve the reason's place always: a strip keeps its second line, a button that stands alone keeps 3 + 20 px
under it, also while it is enabled. Otherwise the layout jumps when the state flips. Recipe:
[recipes.md](recipes.md#guarded-button). StarUI's `Button` has no `enabled` argument: set `isEnabled` on the
returned `ButtonAPI` from state read in the builder.

## Confirm

**Every confirmation is the game's own confirmation popup**: a small centred window with the question, Confirm
and Cancel. Never a custom one, and never a Confirm and Cancel pair that replaces the button in place.

- Use it for an action that destroys something or cannot be undone (Dismiss, Scrap, Abort with a lost fee).
  Do not confirm what can be undone with one click.
- Text: the question with the thing's name in highlight ("Dismiss Ilse Varga?"), then at most one line with
  the consequence. Insignia 21. No title.
- (engine) Confirm is left, Cancel right, both 150 x 25 at the bottom right; the window is 520 x 185 for the
  default 500 x 130 content.
- The button that opens it is a normal button and takes no `...`.
- In StarUI it is a `Dialog` with that window: content 500 x 130, `confirmText` and `cancelText = "Cancel"`, the
  question in a `Text` with `font = Font.INSIGNIA_21` ([setup.md](setup.md#a-dialog)). It stacks over any panel or
  dialog. (engine) Vanilla's `CampaignUIAPI.showConfirmDialog` does the same with no dialog open, and nothing
  while one is.
- In a mockup the popup is its own cut out on the states board (`k.popup`).

## Tabs

- `CutStyle.TOP`, one height for all of them.
- Selected: bright label on the opaque dark fill. Not selected: dark fill at 60% alpha, base label.
- Disabled tabs are hidden, not greyed, when they can never apply to the current selection.
- The selected tab is part of the view state and survives a rebuild.

## Toggles, checkboxes, steppers

- **Area toggle bank** for two to five exclusive modes: equal widths, 22 px high, 5 px apart. The checked one
  has the dark fill and a bright label; the others are black with a 1 px border. A 4 px strip at the left edge
  can carry the mode's colour. Each toggle's tooltip says what the mode does. Build it as `AreaCheckbox`es in a
  `RadioGroup`, in a Row with `Size.fillMaxWidth(height = 22f)` each. Not a `FilterGroup`: that is vanilla's filter
  bar (a click on the only selected button selects them all, Shift or Ctrl + click adds one), right for filters and
  wrong for modes.
- **Checkbox** for an independent on/off option: the game's small checkbox with its label to the right, 20 px
  line.
- **Stepper** for a small number: `-`, value (highlight when above 0, gray at 0), `+`. Buttons grey out at the
  limits.
- The game has no dropdown. Use a toggle bank or a picker dialog. A text field exists
  (`TextField(bind = name)`) and is for names only; numbers get a stepper.

## Icons and second text on a button

**The cost button is the standard way to show a price**: an icon left of the label and the amount in highlight
after it ("Send [supplies] 40", "Gene scan [relic] 10"). The player reads the price where they click, and the
panel needs no cost row and no sentence.

- One icon and one number. Two resources, or a change the player should see first, use cost rows next to the
  button instead.
- Size it for the largest amount.
- Short on stock: grey button, red amount, the cost against the stock in the tooltip.
- The engine's button takes one text, so the face is a button with an icon and labels laid over it in a `Box`
  ([recipes.md](recipes.md#cost-button)). The overlay does not brighten on hover; that is accepted.

## Tooltips

- Every button whose effect is not fully said by its label and place has a tooltip: what it does, what it
  costs, the lore line if there is one.
- Tooltips must show on disabled buttons too, so the player can hover a grey button for help. A `Modifier.tooltip`
  on a button does (StarUI sets vanilla's `setShowTooltipWhileInactive`). Check it on a capture: hover the grey
  button.
- The blocked reason is on the panel (red line), not only in the tooltip.
- A button that opens the confirmation popup says in its tooltip what is lost.

## Custom drawn buttons

Needed when a button is a picture tile (a submarket, a faction, a slot) or is restyled inside a vanilla screen.
These lessons come from rebuilding the game's submarket buttons.

- **Size the tile from the icon**: 4 + 64 + 4 = 72 px for a 64 px icon. Centre the icon on whole pixels. A 1 to
  2 px offset is visible and gets reported.
- **The vanilla button frame cuts its corners by 3 px, but its background fill and its disabled overlay are
  drawn square.** A fill you draw behind a cut frame must be cut the same way (an octagon), or its corners
  stick out.
- **Selected state as rings, not as a fill**: three 1 px rings following the cut corners, from the outside
  in: black, the owner's base UI colour, white tinted 10% toward that colour. Take the owner's base UI colour
  at full saturation and value so dull faction colours still read; a gray (saturation under 3%) keeps its
  brightness instead. Do not use yellow for "selected" and do not use the bright UI colour.
- **Ring corners:** shrink the corner cut by `sqrt(2) - 1` px per ring step inward, so the diagonals stay as
  thick as the sides.
- **Never draw a quad with a negative or zero size.** GL still draws it and leaves a stray 1 px line. Guard
  every quad with `w > 0 && h > 0`.
- **Draw in `Modifier.onRender { alpha -> }`** (screen coordinates, y up: the plugin's `x`, `y`, `width`,
  `height`). Wrap raw GL in `glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT)` and
  `glPopAttrib()`, and multiply every alpha by `alpha`. Take the click with `Modifier.onClick` on the same
  component.
- **Offer the behaviour as settings** when it changes how a vanilla control reacts: for example names shown
  On hover / On click / Never, highlight Off / Bright band / Animated. Some players want no hover motion at
  all. Read the settings once per build and cache them; do not read them per frame.
- Hover and unfold motion: [motion.md](motion.md).
