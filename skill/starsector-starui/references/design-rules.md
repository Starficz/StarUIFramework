# Design rules

Read this file in full before designing. It says what the game's UI looks like and what a mod panel must do to
match it. Every rule follows what vanilla's own code does; the counts behind them are in
[vanilla-styles.md](vanilla-styles.md).

Contents: [The look](#the-look) · [Colour roles](#colour-roles) · [Typography](#typography) ·
[Spacing and alignment](#spacing-and-alignment) · [Panel structure](#panel-structure) · [Density](#density) ·
[Text on the panel and the tooltip rule](#text-on-the-panel-and-the-tooltip-rule) · [Wording](#wording) ·
[Icons](#icons) · [Showing numbers](#showing-numbers) · [What vanilla never does](#what-vanilla-never-does)

## The look

Starsector UI is a flat, dark instrument panel:

- **Black background.** Content sits directly on black. Depth comes from 1 px lines and from the few filled
  elements, not from shaded tiles.
- **One hue.** Every frame, heading and button is a shade of the player faction's UI colour (pale cyan by
  default). Other hues are signals: yellow for numbers that matter, green and orange red for good and bad, light
  green for story points.
- **Straight lines.** Corners are square or cut at 45 degrees. Nothing is rounded, nothing has a drop shadow,
  nothing has a gradient except bars and glows.
- **Two fonts.** Orbitron (wide, technical) for headings, tabs and button labels. Insignia (narrow, readable) for
  everything the player reads.
- **Few filled areas.** Heading bars, buttons, the selected row, table header strips, bars. Everything else is
  outline or text.
- **Icons carry meaning.** Commodities, ships, skills, portraits and faction crests do the work that text would
  do in a web UI.

Look at `vanilla_colony_screen.jpg`, `vanilla_industry_picker.png` and `vanilla_custom_production.png` in
[gallery.md](gallery.md) to see all of this at once.

## Colour roles

Always read colours live through `VanillaColors` (from `org.starficz.starui.vanilla`), which reads the game's
accessors. The RGB values are the vanilla defaults and are only for mockups; mods and the player's faction change
them.

| Role | StarUI | Default | Use for | Not for |
|---|---|---|---|---|
| Base UI | `VanillaColors.base` | 170,222,255 | Button and tab labels, heading text, frame lines | Body text |
| Dark UI | `VanillaColors.dark` | 31,94,112 alpha 175 | Fill of buttons, heading bars, the selected row, row and card borders | Backgrounds of content |
| Bright UI | `VanillaColors.bright` | 203,245,255 | Selected tab or toggle label, hover | Large areas |
| Text | `VanillaColors.text` (a `Text` with `color = null`) | 220,220,220 | Everything the player reads | Headings |
| Gray | `VanillaColors.gray` | 175,175,175 alpha 180 | Second line of a row, units, notes, empty state lines, disabled text, old values | Anything the player must not miss |
| Highlight | `VanillaColors.highlight` | 255,210,0 | Numbers that matter: costs, counts, days, the new value | Whole sentences, labels |
| Positive | `VanillaColors.positive` | 155,255,0 | A gain, a met requirement, "Ready" | Decoration |
| Negative | `VanillaColors.negative` | 255,100,0 | The reason a button is blocked, a shortfall, a loss | Warnings that block nothing |
| Story | `VanillaColors.story` (also `storyDark`, `storyBright`) | 163,239,128 | Anything that costs a story point, and nothing else | Other "special" actions |
| Bar colours | `Global.getSettings().getColor("progressBarStandardColor")` and the other `progressBar*Color` keys, as a `ProgressBar`'s `barColor` | see [parts.md](parts.md#bars) | Bars | Text |
| Bar frame | `getColor("widgetBorderColorBright")` | 100,220,255 | Lines of the bar widget | |

A mod defines no raw colours of its own: every colour comes from these accessors, the settings colours or a
faction's UI colours.

Rules:

- **One meaning per colour, everywhere.** If yellow is "number that matters" it is never also "selected".
- **Faction colours stay faction colours.** A panel that belongs to a faction or a market uses that faction's
  base UI colour for its accents, not yellow and not the player's cyan. Use the base colour, not the bright one:
  an unset bright colour is the base colour mixed 35% toward pale cyan, which turns gray factions cyan.
- **Buttons are never coloured by what they do.** Every vanilla button, a destructive one included, is the
  faction's base colour on its dark colour. The one exception is an action that spends a story point: its button
  takes the story colours. What a click costs or risks is said in the text around it (the cost in highlight, a red
  reason) and in the game's confirmation popup. Details in [buttons.md](buttons.md).
- **Do not dim by lowering saturation.** Disabled is gray text on a gray fill; everything else keeps its colour.
- **At most one highlight colour per line** of text, besides the text colour.
- **State words** use the role colours: positive for ready or good, the base colour for a normal running
  state, gray for idle or none, negative for failing or blocked. Do not repeat the same coloured state word on
  every row of a group whose heading already says it.
- **Category words** (a personality, a type, a rank) stay in the text or gray colour unless the game itself
  colours them or the user asks for a colour code. A mod may define a small fixed colour code for one category
  (the reference Academy panel colours personalities); then it is used the same way in every panel and
  explained in the `?` tooltip. Do not invent one on your own for a mockup: propose it in the notes.
- **A story point cost** is text in the story colour ("1 story point"). The control that spends it is
  story-coloured: an interaction dialog option with `setOptionColor`, a button with the story colours
  ([buttons.md](buttons.md#colour)).

## Typography

| Font | StarUI | Use |
|---|---|---|
| Insignia 15 | `Font.INSIGNIA_15`; a `Text` with `font = null` | All body text, row text, values, tooltips. The default. |
| Insignia 21 | `Font.INSIGNIA_21` | One prominent value per panel at most |
| Orbitron 12 | `Font.ORBITRON_12`; buttons `ButtonFont.ORBITRON_12_CONDENSED` (the default) | Section headings, tabs, button labels, table headers |
| Orbitron 20 | `Font.ORBITRON_20_AA`; buttons `ButtonFont.ORBITRON_20` | The game's own Confirm and Cancel buttons, a screen title |
| Victor 10 | `Font.VICTOR_10` | Tiny tags on an icon. Rare. |

- A panel uses Insignia 15 and Orbitron 12, plus one larger font at most. More sizes look like a web page.
- Emphasis is colour (highlight yellow on the number), never bold, underline, caps or size.
- Headings are vanilla's section heading: the faction's base colour on its dark bar, centred. Do not write a
  heading as plain large text.
- Insignia 15 has a line height of 17 px (engine); lay out text lines 18 to 20 px apart.
- Measure text with the game (`measureTextSize(text, font)` from `vanilla`), never by character count. The fonts are
  proportional: `W` is three times as wide as `i`.

## Spacing and alignment

- **Everything is on whole pixels.** Positions and sizes are integers. A half pixel blurs text and lines.
- **Shared edges.** Elements of a column share one left edge and one right edge. A value column is right
  aligned to one x. If two things almost line up, make them line up.
- **Vanilla's spacing is two steps**, declared at the top of almost every vanilla UI method: `pad = 3` and
  `opad = 10`, with `small = 5` now and then:

  | Gap | px |
  |---|---|
  | Lines that belong together: the lines of one entry, a label and its value | 3 |
  | Small gaps: rows of a short list, parts of one block | 5 |
  | Between blocks; above a section heading; from a heading to its content | 10 |
  | Rows of a table | none: vanilla's table rows are 20 px and touch |

- **A section heading has 10 above and 10 below**, as vanilla places it (`addSectionHeading(..., opad)` and the
  next call at `opad`). The first heading of a column sits at the top.
- **Whitespace groups, boxes do not.** When a panel reads as one wall of rows, add a heading or a 10 px gap
  before adding lines or boxes.
- **Text in a box is centred to the pixel**: `y + floor((h - textHeight) / 2)`. The engine rounds down; do the
  same or your text sits 1 px lower than the game's.
- **Icons on a tile are centred to the pixel**, and the tile is sized from the icon: a 64 px icon with 4 px on
  each side makes a 72 px tile, not 70.
- **Text never touches a border**: 10 px (`opad`) from the border of a framed block, 5 at least.
- **Same thing, same size.** All rows of a list have one height. All buttons of a bank have one height and, when
  they fit, one width. All portraits of a list have one size.
- **Align numbers right, text left; headings centred**, as vanilla's section headings are.

The user notices offsets of 1 to 2 px. Check alignment on a 2x screenshot, not by reading the code.

## Panel structure

From top to bottom, a panel has:

1. **Title line** at y 0: a section heading with the panel name, and the `?` help icon at its right end.
2. **Tab bar and counters** (optional): tabs left, the player's relevant stock right (credits, a commodity),
   then a 1 px rule.
3. **Body**: one of the archetypes in [layout.md](layout.md#archetypes). Usually a list on the left and the
   detail of the selected entry on the right.
4. **Bottom row** of a column or dialog: pinned, outside the scroll area, buttons at the right end as vanilla
   puts them (Cancel, Confirm). It holds the rare, the destructive and the whole entry actions, and the one
   commit of an editor or wizard.
5. The host's own buttons (a custom dialog's Return, or Confirm and Cancel) below the content.

**Actions sit with the thing they change**, not in a block of buttons: the name is a text field, the favourite
is a star on the name bar, the assigned ship is a strip with Change and Recall at its right end, a request is a
strip with the button that answers it. The player finds an action by looking at what they want to change.
The example boards of the mockup generator (`scripts/mockup/example_board.py`) show it for a list, for tiles and
for a form.

Order inside a column follows the order the player decides in: what is it, what state is it in, what can I do,
what does it cost, do it. A wizard shows the next step only when the current one is done. A block that applies
only sometimes (a request, a running order) is drawn only while it applies; the blocks below it are in the
scroll area, so nothing pinned moves.

**Lists and tiles are equal choices.** Rows when the player compares values down a column or the entries have
more than two values each. Tiles when each entry has a face (a person, a pet, a ship), carries one or two
values, and the detail lives on a card next to the grid. Most management panels stay lists or tables; do not
turn a table into tiles because tiles look newer. Pick the archetype from the content
([layout.md](layout.md#archetypes)): six shapes are in use and each has a reference image.

## Density

Density is how much each screen holds. Take vanilla's row heights for the same kind of content, and keep one
height for every row of a list.

| Vanilla list | Row (engine, measured on RC8) | Holds |
|---|---|---|
| Tooltip tables, `Table` rows | 20 | one line of values |
| Blueprint and custom production lists | 30 | icon, name, a few values |
| Hullmod picker | 36 | 32 px icon, name, cost |
| Industry picker | 44 | picture, name, several values |
| Planet list | 54 | picture, two lines |
| Colony list, comm directory | 68 | picture, two text lines, icons |
| Officer and administrator pickers | 120 | portrait, name, a status sentence, a bar, skill icons, buttons |

Pick the closest one by what a row holds. Rows are 3 to 5 px apart, or touching in a table.

When the user says a panel is too dense, in this order:

1. Move explaining text into tooltips (next section). This alone fixes most cases.
2. Group with the spacing: a heading or a 10 px gap between blocks.
3. Remove values the player does not decide on. Keep one value per cell. For every indicator ask what the
   player does with it; "nothing" removes it.
4. Replace words with icons.
5. Then take the next taller vanilla row height.

## Text on the panel and the tooltip rule

**A sentence that explains a feature, an option or a rule does not sit on the panel. It is the hover tooltip of
the control it explains.**

Plain text on the panel is fine only when it gives information that is immediately relevant and that the player
cannot infer from the elements around it. The test for every line of text: *does the player need this right
now, to decide or to understand what they see, and would they not know it otherwise?* If the answer is "only the
first time" or "they can guess it", it goes into a tooltip.

| Stays on the panel | Goes into a tooltip |
|---|---|
| State: "In training", "Idle", "Returning" | What a mode or option does |
| Data: names, counts, days left, costs | How a rate is calculated |
| The red reason a button is blocked | Lore and flavour (tooltip of the action button) |
| A consequence to know before a click: "Aborting loses the fee" | Rules of the whole panel (`?` help icon on the title line) |
| The one grey line of an empty list | Which things are affected, exceptions, edge cases |
| The tagline: one flavour line under the name of the selected thing | Every other piece of lore |
| | The full text of a shortened label |

Where the tooltip goes:

- An option or mode: on the toggle or button that selects it.
- A value: on the value's row or cell.
- An action: on the action button. Its tooltip may also hold the lore line.
- A marker on a tile: on the marker. It says what the state is and what ends it.
- The panel's rules and rates: on the `?` help icon at the right end of the title line. One place, always the
  same, so the player learns to look there.

Tooltips must also work on disabled buttons (the player hovers a grey button to learn why). See
[buttons.md](buttons.md#tooltips).

Tooltip content: title in highlight colour when it names a thing, then short paragraphs 10 px apart, numbers in
highlight. Width 350 px, 450 for one with a table or several sections (vanilla's two widths). A tooltip is not a
manual: three short paragraphs at most.

## Wording

- **Short labels in standard software words**: Accelerate, Use Relics, Train, Store, Remove, Recall, Cancel,
  Confirm. One or two words. Verb first for actions.
- **Reuse the game's own words** for the same thing: "Storage", "Supplies", "per month", "Hazard rating". Look
  the term up in the game's strings before inventing one.
- **Do not explain what the player assumes.** No "Click to select". No "Items are taken from storage first".
  No description of the order storages are searched in.
- **No sentences on buttons**, no trailing full stop on labels, no exclamation marks, no ALL CAPS.
- **Numbers with their unit**, the way the game writes them: `1,200c` with the credits icon or sign, `30 days`,
  `x1.5`, `+10%`.
- **Names wrap to two lines before they are shortened.** Only then `...` plus the full name in a tooltip.
- **Reason lines are specific**: "Needs 40 more Supplies", not "Requirements not met".

## Icons

- Use the game's icon whenever the thing has one: commodity icons, the credits icon, ship sprites, skill icons,
  portraits, faction crests, submarket icons (show a storage as its submarket icon, not as text).
- An icon plus a number replaces the word: `[supplies icon] 120` not "Supplies: 120".
- Icons keep their aspect ratio, sit on whole pixels and are drawn at an integer size. Ships are fitted into a
  square cell (contain), portraits fill it.
- An icon row is at most two rows high, then a `+N` cell. It never pushes the layout.
- **A state on a picture is a marker**: the icon itself with a black outline of its own shape, hanging over the
  picture's corner. Never a black box or cell behind it. One marker per picture, the most urgent state first.
- **A want, a request, anything that waits for the player** uses the game's quest (!) icon
  (`getSpriteName("intel", "important")`), not a colour.
- **Skills keep the vanilla frame**: the aptitude coloured cut corner frame and the elite stars
  ([parts.md](parts.md#icons-portraits-ships-skills)). A skill icon in a plain box or a yellow frame reads as mod UI.
- When the mod has no art for a state yet, a mockup may tint a game icon as a placeholder. Say so in the notes:
  the mod needs its own icon before release.
- Check that the sprite exists and is loaded: an unknown path gives a 0 x 0 sprite and an empty hole
  ([traps.md](traps.md)).

## Showing numbers

- **A change** is shown as old value struck through in gray, `->`, new value in highlight:
  ~~48~~ `->` 24 ([recipes.md](recipes.md#change-row)).
- **A cost in one resource rides on its button**: `[icon] Send 40`. The stock is in the header counter and in
  the button's tooltip. Short on stock: the button is grey and the amount red.
- **Several costs, or a cost the player compares first**, are cost rows: icon, amount in highlight, stock in
  gray brackets, `[icon] 40 (205)`. The amount turns red when the stock is short.
- **A table's key number** (a grade, a level) is the first column, left of the name, not a pip row at the far
  end. A value that is not known yet is a gray `?` in the same cell, with the row in gray.
- **Progress** is a bar with the numbers on it (`4,200 / 5,000`) or a thin bar under the state text in a row.
- **A level of a few steps** is a row of pips, not a number.
- **A requirement** is a tick or cross plus the text, red while unmet.
- **Label left, value right** in a grid. Values share one right edge.

## What vanilla never does

Each of these marks a panel as "mod UI" at a glance. Do not do them, in mockups either.

- Light blue or gray tinted tiles behind rows, cards or cells.
- Rounded corners, drop shadows, bevels.
- Gradients on buttons or headings.
- Centred body text in a wide column. The tagline under a name is the one exception.
- Coloured body text for decoration.
- Icons from another style (flat web icons, emoji).
- A scroll bar inside a scroll bar.
- A home made confirmation: a Confirm and Cancel pair that appears in place of a button, or a custom modal.
  Every confirmation is the game's popup ([buttons.md](buttons.md#confirm)).
- A block of stacked buttons under the content. Actions sit with the thing they change.
- A button filled green, red or amber, or in any colour other than the faction's.
- A black box behind a status icon on a picture.
- Text that explains the UI ("Use the buttons below to...").
- Layout that changes when a value changes (a button that moves because a label got longer).
