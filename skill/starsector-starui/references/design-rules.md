# Design rules

What the game's UI looks like and what a mod panel must do to match. Rules follow vanilla's own code; counts in
[vanilla-styles.md](vanilla-styles.md).

Contents: [The look](#the-look) · [Colour roles](#colour-roles) · [Typography](#typography) ·
[Spacing and alignment](#spacing-and-alignment) · [Panel structure](#panel-structure) · [Density](#density) ·
[Text on the panel and the tooltip rule](#text-on-the-panel-and-the-tooltip-rule) · [Wording](#wording) ·
[Icons](#icons) · [Showing numbers](#showing-numbers) · [What vanilla never does](#what-vanilla-never-does)

## The look

A flat, dark instrument panel:

- **Black background**; depth from 1 px lines and a few fills, not shaded tiles.
- **One hue**: frames, headings, buttons are shades of the player faction's UI colour (pale cyan). Other hues are
  signals: yellow numbers that matter, green / orange red good / bad, light green story points.
- **Straight lines**: square or 45° cut corners; no rounding, shadows or gradients (except bars and glows).
- **Two fonts**: Orbitron for headings, tabs, button labels; Insignia for everything read.
- **Few fills**: heading bars, buttons, the selected row, table header strips, bars. The rest is outline or text.
- **Icons carry meaning**: commodities, ships, skills, portraits, crests.

All at once: `vanilla_colony_screen.jpg`, `vanilla_industry_picker.png`, `vanilla_custom_production.png`
([gallery.md](gallery.md)).

## Colour roles

Read colours live through `VanillaColors`; RGB values are vanilla defaults, for mockups only.

| Role | StarUI | Default | Use for | Not for |
|---|---|---|---|---|
| Base UI | `VanillaColors.base` | 170,222,255 | Button and tab labels, heading text, frame lines | Body text |
| Dark UI | `VanillaColors.dark` | 31,94,112 a175 | Button fill, heading bars, selected row, row and card borders | Content backgrounds |
| Bright UI | `VanillaColors.bright` | 203,245,255 | Selected tab or toggle label, hover | Large areas |
| Text | `VanillaColors.text` (`color = null`) | 220,220,220 | Everything read | Headings |
| Gray | `VanillaColors.gray` | 175,175,175 a180 | Second lines, units, notes, empty lines, disabled, old values | What must not be missed |
| Highlight | `VanillaColors.highlight` | 255,210,0 | Numbers that matter: costs, counts, days, new values | Sentences, labels |
| Positive | `VanillaColors.positive` | 155,255,0 | A gain, a met requirement, "Ready" | Decoration |
| Negative | `VanillaColors.negative` | 255,100,0 | Blocked reason, shortfall, loss | Warnings that block nothing |
| Story | `VanillaColors.story` (`storyDark`, `storyBright`) | 163,239,128 | What costs a story point, only | Other "special" actions |
| Bar colours | `getColor("progressBarStandardColor")` and other `progressBar*Color`, as `ProgressBar.barColor` | [parts.md](parts.md#bars) | Bars | Text |
| Bar frame | `getColor("widgetBorderColorBright")` | 100,220,255 | Bar widget lines | |

No raw colours: only these, settings colours and faction UI colours.

- **One meaning per colour**, everywhere.
- **Faction panels use that faction's base colour** for accents (not yellow, not the player's cyan). Base, not bright:
  unset bright is base mixed 35% to pale cyan and turns gray factions cyan.
- **Buttons are never coloured by what they do**: faction base on its dark, destructive ones too; only story-point
  actions take story colours. Cost and risk are said around it (highlight cost, red reason) and in the game's
  confirm popup ([buttons.md](buttons.md)).
- **Disabled**: gray text on gray fill; never desaturate the rest.
- **At most one highlight colour per line** besides text.
- **State words**: positive ready, base running, gray idle, negative failing. Not repeated on every row when the
  group heading says it.
- **Category words** (personality, type, rank) stay text or gray unless the game colours them or the user asks. A
  mod's fixed category code is used the same in every panel and explained in the `?` tooltip; propose one in notes,
  never invent it.
- **Story point costs**: text in story colour ("1 story point"); the spending control is story-coloured
  (`setOptionColor`, story button: [buttons.md](buttons.md#colour)).

## Typography

| Font | StarUI | Use |
|---|---|---|
| Insignia 15 | `Font.INSIGNIA_15`; `font = null` | Body, rows, values, tooltips (default) |
| Insignia 21 | `Font.INSIGNIA_21` | One prominent value per panel at most |
| Orbitron 12 | `Font.ORBITRON_12`; `ButtonFont.ORBITRON_12_CONDENSED` (default) | Section headings, tabs, button labels, table headers |
| Orbitron 20 | `Font.ORBITRON_20_AA`; `ButtonFont.ORBITRON_20` | The game's Confirm and Cancel, a screen title |
| Victor 10 | `Font.VICTOR_10` | Tiny tags on an icon; rare |

- Insignia 15 and Orbitron 12, plus one larger at most.
- Emphasis is colour (highlight on the number), never bold, underline, caps or size.
- Headings are vanilla's section heading (base on dark bar, centred), never large plain text.
- Insignia 15 line height is 17 px (engine); lines 18 to 20 px apart.
- Measure with `measureTextSize(text, font)`, never by characters (`W` is 3x `i`).

## Spacing and alignment

- **Whole pixels** for every position and size; half pixels blur.
- **Shared edges**: a column's elements share left and right edges; value columns right-align to one x. Almost
  aligned means align.
- **Two steps**, as vanilla declares (`pad = 3`, `opad = 10`, sometimes `small = 5`):

  | Gap | px |
  |---|---|
  | Lines of one entry, a label and its value | 3 |
  | Rows of a short list, parts of one block | 5 |
  | Between blocks; above a section heading; heading to content | 10 |
  | Table rows | 0: 20 px rows touching |

- **Section headings: 10 above and below**; the first sits at the top.
- **Whitespace groups, boxes do not**: a heading or 10 px gap before more lines or boxes.
- **Text in a box centred to the pixel**: `y + floor((h - textHeight) / 2)` (the engine rounds down).
- **Icons centred to the pixel**; tiles sized from the icon (64 + 4 + 4 = 72, not 70).
- **Text never touches a border**: 10 px (`opad`), 5 at least.
- **Same thing, same size**: one row height per list, one button height (and width when it fits) per bank, one
  portrait size per list.
- **Numbers right, text left, headings centred.**

Users notice 1 to 2 px: check on a 2x capture, not in code.

## Panel structure

Top to bottom:

1. **Title line** at y 0: section heading with the name, `?` help icon at its right end.
2. **Tabs and counters** (optional): tabs left, relevant stock right, then a 1 px rule.
3. **Body**: an [archetype](layout.md#archetypes), usually a list left and the selection's detail right.
4. **Bottom row**: pinned outside the scroll area, buttons at the right end (Cancel, Confirm): rare, destructive and
   whole-entry actions, an editor's or wizard's one commit.
5. The host's own buttons below.

**Actions sit with what they change**, not in a button block: name is a text field, favourite a star on the name bar,
the assigned ship a strip with Change and Recall at its end, a request a strip with its answer button. Example boards:
`scripts/mockup/example_board.py`.

Column order follows the player's decision: what is it, its state, what can I do, cost, do it. Wizards show the next
step when the current is done. Conditional blocks (a request, a running order) draw only while they apply; blocks
below sit in the scroll area so nothing pinned moves.

**Lists and tiles are equal choices.** Rows when comparing values down a column or more than two values per entry;
tiles when entries have a face, one or two values, and a detail card beside the grid. Don't tile a table for looks.
Archetype from content ([layout.md](layout.md#archetypes)).

## Density

Use vanilla's row height for the same kind of content; one height per list.

| Vanilla list | Row (engine, RC8) | Holds |
|---|---|---|
| Tooltip tables, `Table` rows | 20 | one line of values |
| Blueprint and custom production lists | 30 | icon, name, a few values |
| Hullmod picker | 36 | 32 px icon, name, cost |
| Industry picker | 44 | picture, name, several values |
| Planet list | 54 | picture, two lines |
| Colony list, comm directory | 68 | picture, two text lines, icons |
| Officer and administrator pickers | 120 | portrait, name, status sentence, bar, skill icons, buttons |

Rows 3 to 5 px apart, or touching in a table.

Too dense, in order: (1) explanations into tooltips (fixes most); (2) group with a heading or 10 px gap; (3) remove
values the player does not decide on, one value per cell ("what does the player do with it?"); (4) icons for words;
(5) the next taller row height.

## Text on the panel and the tooltip rule

**A sentence explaining a feature, option or rule is the hover tooltip of the control it explains**, not panel text.

Panel text only if the player needs it now and could not infer it; "only the first time" or "they can guess" goes
to a tooltip.

| Stays on the panel | Goes into a tooltip |
|---|---|
| State: "In training", "Idle", "Returning" | What a mode or option does |
| Data: names, counts, days left, costs | How a rate is calculated |
| The red reason a button is blocked | Lore and flavour (on the action button) |
| A consequence before a click: "Aborting loses the fee" | Panel rules (`?` on the title line) |
| An empty list's one gray line | Affected things, exceptions, edge cases |
| A tagline under the selected thing's name | Other lore |
| | The full text of a shortened label |

Where: an option on its toggle; a value on its row or cell; an action on its button (lore too); a tile marker on the
marker (state and what ends it); panel rules and rates on the `?` (always there). Tooltips work on disabled buttons
([buttons.md](buttons.md#tooltips)).

Content: title in highlight when it names a thing, short paragraphs 10 px apart, numbers in highlight, three at
most. Width 350, or 450 with a table or several sections.

## Wording

- **Short standard labels**, verb first: Accelerate, Use Relics, Train, Store, Remove, Recall, Cancel, Confirm.
- **The game's words** for the same thing ("Storage", "Supplies", "per month", "Hazard rating"); check its strings.
- **No explaining the obvious** ("Click to select", search orders).
- **No sentences on buttons**, no trailing full stop, no exclamation marks, no ALL CAPS.
- **Numbers with units** as the game writes them: `1,200c` with the credits icon, `30 days`, `x1.5`, `+10%`.
- **Names wrap to two lines before being cut**, then `...` and the full name in a tooltip.
- **Specific reasons**: "Needs 40 more Supplies", not "Requirements not met".

## Icons

- The game's icon whenever there is one: commodities, credits, ships, skills, portraits, crests, submarkets (storage
  as its submarket icon).
- Icon plus number replaces the word: `[supplies icon] 120`.
- Aspect kept, whole pixels, integer sizes; ships contain-fit a square cell, portraits fill it.
- An icon row: two rows at most, then `+N`; never pushes the layout.
- **A state on a picture is a marker**: the icon with an outline of its own shape over the corner, never a box
  behind; one per picture, most urgent first.
- **Wants and requests** use the quest icon (`getSpriteName("intel", "important")`), not a colour.
- **Skills keep the vanilla frame** (aptitude cut-corner frame, elite stars;
  [parts.md](parts.md#icons-portraits-ships-skills)): `SkillIcon` (alone) or `SkillPanel` (with names); never a frame
  drawn by hand.
- A mockup may tint a game icon as a placeholder for missing art; note that the mod needs its own.
- Unknown sprite paths give a 0 x 0 sprite ([traps.md](traps.md)).

## Showing numbers

- **Change**: old struck through in gray, `->`, new in highlight: ~~48~~ `->` 24 ([recipes.md](recipes.md#change-row)).
- **One-resource cost rides on its button**: `[icon] Send 40`; stock in the header counter and tooltip; short: grey
  button, red amount.
- **Several costs, or one compared first**: cost rows `[icon] 40 (205)` (amount highlight, stock gray), red when short.
- **A table's key number** (grade, level) is the first column, left of the name; unknown: gray `?`, row gray.
- **Progress**: a bar with numbers on it (`4,200 / 5,000`), or a thin bar under a row's state text.
- **A level of few steps**: pips.
- **A requirement**: tick or cross plus text, red while unmet.
- **Grids**: label left, value right, values share a right edge.

## What vanilla never does

Each marks "mod UI" at a glance; not in mockups either:

- Tinted tiles behind rows, cards or cells.
- Rounded corners, drop shadows, bevels; gradients on buttons or headings.
- Centred body text in a wide column (a tagline is the exception).
- Decorative coloured body text; icons from another style (flat web, emoji).
- A scroll bar inside a scroll bar.
- A home-made confirmation (inline Confirm/Cancel, custom modal): use the game's popup ([buttons.md](buttons.md#confirm)).
- Stacked buttons under the content.
- Buttons filled green, red, amber, or any non-faction colour.
- A black box behind a status icon.
- Text explaining the UI ("Use the buttons below to...").
- Layout that moves when a value changes.
