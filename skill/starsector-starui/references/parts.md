# Parts list

The elements vanilla builds its panels from: when vanilla uses each, its size and look in vanilla, and how to
build it in StarUI. Numbers come from vanilla's code ([vanilla-styles.md](vanilla-styles.md)); "(engine)" marks
numbers measured on screen on 0.98a-RC8. Where vanilla has its own component, StarUI wraps it: use that, not a
copy.

A "Mockup" line names the helper in `scripts/mockup/kit.py`.

Contents: [Title line](#title-line) · [Tabs](#tabs) · [Section heading](#section-heading) · [Table](#table) ·
[Selectable list](#selectable-list) · [Picture tiles](#picture-tiles) · [Label and value](#label-and-value) ·
[Bars](#bars) · [Costs and resources](#costs-and-resources) · [Requirements](#requirements) ·
[Icons, portraits, ships, skills](#icons-portraits-ships-skills) · [Buttons and the bottom row](#buttons-and-the-bottom-row) ·
[Tooltip](#tooltip) · [Confirmation popup](#confirmation-popup) · [Scroll list](#scroll-list) ·
[Empty state and reason lines](#empty-state-and-reason-lines) · [Not vanilla](#not-vanilla) ·
[Choosing between parts](#choosing-between-parts)

## Title line

- **Vanilla:** a dialog or panel opens with its name: a section heading across the top (pickers), or the name in
  Orbitron with a `?` help icon at the right end (refit, intel).
- **Build:** `SectionHeading(title)`; the `?` icon an
  `Image(Global.getSettings().getSpriteName("fleetScreen", "icon_more_info"))` at the right end with
  `Modifier.tooltip(width = 350f) { ... }` holding the panel's rules and rates.
- **Mockup:** `title(w, s, sub, help)`.

## Tabs

- **Vanilla:** the core tabs, intel's Intel / Planets / Factions, the command tab's Colonies / Income, all
  `CutStyle.TOP`, side by side, the count in the label ("Intel (1)"), hotkeys in yellow.
- **Look:** the selected tab is filled and its label bright; the others base label on the dark fill.
- **Build:** `Button(label, Size.exact(w, 20f), cutStyle = CutStyle.TOP)` per tab, the colours from one
  `UIState<Int>` ([recipe](recipes.md#tabs)). Not `AreaCheckbox`es in a `FilterGroup`: a click on the selected tab
  would select every tab.
- **Rule:** a tab that has nothing to show for the current selection is not drawn.
- **Mockup:** `tabs(x, y, w, labels, selected)`.

## Section heading

- **Vanilla:** `addSectionHeading`: a dark bar the full width of its column, the text **centred** in the faction's
  base colour, 10 px (`opad`) above it and 10 px from it to its content. 278 of 284 vanilla headings are centred.
  (engine) 18 px high: one line plus 3.
- **Build:** `SectionHeading(text)`; the column of blocks has `spacing = 10f`. For another faction's panel:
  `SectionHeading(text, color = faction.baseUIColor, backgroundColor = faction.darkUIColor)`.
- **Rule:** a heading is never the last thing in a scroll area and never alone: an empty group keeps its heading
  and one gray line.
- **Check:** vanilla's `addSectionHeading` draws 10 px wider than asked. StarUI sizes the heading's label itself;
  check the bar's right end on a 2x capture.
- **Mockup:** `heading(x, y, w, s, 'center', note)`.

## Table

- **Vanilla:** `beginTable`: a header strip in the faction's colours, **20 px rows** (every vanilla table),
  fixed widths for value columns (70 to 150 px) and one name column taking the rest. Numbers right aligned in
  highlight. The selected row is the only filled row; a sortable header shows vanilla's arrow.
- **Build:** `Table(items) { column(...) }` (vanilla's table, rows 20 by default); `.sortBy { }` where the player
  sorts; `onRowClick`, `rowTooltip`.
- **Mockup:** `thead(x, y, w, cols)`, rows with `row(...)`.

## Selectable list

- **Vanilla:** a list the player picks one entry from: the officer and administrator pickers, the colony list, the
  intel list. Row height by what a row holds ([design-rules.md](design-rules.md#density)): 30 to 44 for icon and
  name, 54 to 68 for a picture and two text lines, 120 for an officer with skills and buttons. Rows 3 to 5 px
  apart. Selected: filled with the dark colour; nothing else changes.
- **Columns:** fixed x positions shared by all rows; identity left (icon, name, second line in gray), numbers
  right aligned at the right end in highlight. One value per cell.
- **Build:** many rows of values: a `Table`. Rows with pictures or controls: a container that takes the click,
  `Box(Size.fillMaxWidth(height = rowH), Modifier.background(if (selected) VanillaColors.dark else Color.BLACK).onClick { selectedId.value = id }.tooltip(...)) { Row(Size.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, spacing = 5f) { ... } }`,
  in a `LazyColumn(items, itemHeight = rowH, spacing = 3f, key = { it.id })` for long lists.
- **Trap:** the tooltip of a row goes on the row's container, not on the labels in it.
- **Mockup:** `row(x, y, w, h, inner, selected)`.

## Picture tiles

- **Vanilla:** the colony screen's industries: a picture per tile with a heading strip over it, in a grid; the
  detail of the selected one in a tooltip or a panel beside it.
- **Build:** a `Column(spacing = 10f)` of `Row(spacing = 10f)`s of tiles: `Column { SectionHeading(name); Image(picture, ...) }`
  with `Modifier.onClick`. Vanilla's `PlanetInfo`, `ShipList` and `SkillPanel` cover their own tiles.
- **Mockup:** `tile(...)`.

## Label and value

- **Vanilla:** stat blocks with the label left and the value right in highlight, values on one right edge (refit's
  stats, the colony summary); label over value pairs in a header (the colony screen).
- **Build:** vanilla's own: `StatGrid { entry(label, value) }`, `LabelledValue(label, value)`, `StatModGrid(stat)`.
  By hand: `Row(Size.fillMaxWidth()) { Text(label); FlexSpacer(); Text(value, color = VanillaColors.highlight) }`
  in a `Column(spacing = 3f)`.
- **Mockup:** `grid(x, y, w, rows)`.

## Bars

- **Vanilla:** the slider widget used as a bar: black track, a soft fill brightest in the middle, end lines; the
  numbers centred on it ("4,200 / 5,000"). Colour by what it measures, from the settings colours:

| Key (`Global.getSettings().getColor`) | Default | Use |
|---|---|---|
| `progressBarStandardColor` | 0,121,216 | Any progress with no cargo meaning |
| `progressBarCargoColor` | 203,203,144 | Cargo space |
| `progressBarFuelColor` | 255,125,25 | Fuel |
| `progressBarCrewColor` | 0,225,160 | Crew |
| `progressBarWarningColor` | 255,210,0 | Near a limit |
| `progressBarDangerColor` | 255,50,0 | Over a limit, failing |
| `progressBarOverflowColor` | 255,0,0 alpha 200 | The part above capacity |
| `progressBarFleetPointsColor` | 0,181,216 | Fleet points, deployment |

- **Build:** `ProgressBar(value, Size.fillMaxWidth(height = 20f), valueRange = 0f..max, text = "4,200 / 5,000", barColor = Global.getSettings().getColor("progressBarStandardColor"))`.
  The `text` is the whole label (vanilla's own "value / max" after it only with `configure = { setShowLabelOnly(false) }`);
  with `text = null` it shows nothing. A thin bar under a state word in a row: `Size.exact(70f, 4f)`, no text.
  Event progress (stages, markers): `EventProgressBar`. A faction relationship: `RelationshipBar`. Never a bar of
  your own.
- **Mockup:** `bar(x, y, w, h, frac, color, label)`.

## Costs and resources

- **Vanilla:** commodity icons with amounts (`CostDisplay`, `CommodityIcons`): the icon, the amount in highlight,
  red when short. A single price is text with the amount highlighted ("Costs %s credits").
- **Build:** `CostDisplay { cost(id, quantity) }` (campaign only), `CommodityIcons { icons(id, count) }`, or
  `Text("Costs 40 supplies", highlightedText = listOf("40" to VanillaColors.highlight))`.
- **Mockup:** `costrow(...)`, `stack(...)`.

## Requirements

- **Vanilla:** the survey panel's requirement list (`SurveyRequirements`): what is needed, met in the normal
  colour, unmet in negative red.
- **Build:** `SurveyRequirements(...)` for surveys; by hand a `Column(spacing = 3f)` of `Text`s coloured
  `VanillaColors.text` or `VanillaColors.negative`.

## Icons, portraits, ships, skills

- **Vanilla:** game icons in place of words: commodities, credits, ships, skills, factions, submarkets. Ships in a
  ship display, portraits square, skills in their aptitude frame (the skill panel).
- **Build:** `Image(sprite, ImageSize...)`; `ShipDisplay(Size.exact(s, s), fleetMember = ...)` (vanilla shrinks
  the ship to 95%; `exactScale` for one sprite pixel per UI pixel); `ShipList(ships)`, `FleetSummary(ships)`;
  `SkillPanel(person)` for a person's skills; `CodexEntries` for codex links.
- **Rule:** an icon row is at most two rows, then `+N` with the rest in its tooltip.
- **Skills:** the API's only framed skill icons are vanilla's skill panel, `SkillPanel(person)`: an icon in its
  aptitude frame with the skill's name beside it, two per line, about 43 px a line (five skills take 130 px), each
  with vanilla's tooltip. `oneColumn = true` stacks them. There is no API call for a compact row of framed icons
  without names (the mockup kit's `skill` helper draws one): use `SkillPanel`, and say so when a board shows a
  compact row. A person for it: `Global.getFactory().createPerson()` and `stats.setSkillLevel(id, 1f)` (2 for elite).
  It is big: plan its room before choosing it (a strip of learned skills next to other content will not fit), and
  a user who asked for a compact strip will reject it on sight. For a compact strip, draw the frame yourself
  ([design-rules.md](design-rules.md): skills keep the vanilla frame).
- **A skill's effects have a scope each, not the skill:** a `.skill` file lists every effect with its own `type`, and
  vanilla reuses one script for several scopes. Point Defense lists `PointDefense$Level1` once as `SHIP` and once
  as `SHIP_FIGHTERS` (the skill's `scope` / `scope2`: the piloted ship and its fighters). A list of what a skill
  does groups its effects by type and shows a shared script once, with every scope it applies to.
- **Storages:** a submarket's icon is its `icon` column in `data/campaign/submarkets.csv` (`graphics/factions/storage.png`
  for storage); the settings have no getter for it.
- **Trap:** weapon icons have no single sprite (turret and barrel are separate files).
- **Mockup:** `portrait`, `ship`, `iconrow`, `skill`.

## Buttons and the bottom row

- **Vanilla:** [buttons.md](buttons.md): every button the faction's base label on its dark fill; story-point
  actions in the story colours; the commit and cancel row bottom right in `TL_BR`; banks of actions on the thing on
  screen in `BL_TR` (refit).
- **Build:** the column is `Column(Size.fillMaxSize(), spacing = 10f) { Column(Size.fillMaxSize(), overflow = Overflow.ScrollVertical) { body }; Row(Size.fillMaxWidth(), spacing = 5f) { FlexSpacer(); Button("Cancel", cutStyle = CutStyle.TL_BR) { ... }; Button("Confirm", cutStyle = CutStyle.TL_BR) { ... } } }`:
  the scroll area takes what the pinned row leaves.

## Tooltip

- **Vanilla:** 350 px wide for a short tooltip, 450 for one with a table or several sections; a title in
  highlight when it names a thing, paragraphs 10 px apart, numbers in highlight. To the left of a control in a right
  hand column so it does not cover the next one.
- **Build:** `Modifier.tooltip(width = 350f, location = TooltipMakerAPI.TooltipLocation.BELOW) { addPara(...) }`
  (StarUI's default is 300 wide, above). The builder runs when the tooltip shows, so it reads live values.
- **Mockup:** `tooltip(x, y, w, h, inner)`.

## Confirmation popup

- **Vanilla:** before an action that destroys something or cannot be undone: the game's popup, the question in
  Insignia 21 with the name in highlight, Confirm and Cancel bottom right. (engine) content 500 x 130, window
  520 x 185.
- **Build:** a `Dialog` ([setup.md](setup.md#a-dialog)). **Mockup:** `popup(text_html)`.

## Scroll list

- **Vanilla:** a scroll panel with its bar at the right edge; the list ends on a whole row.
- **Build:** `Column(size, overflow = Overflow.ScrollVertical)` for tens of rows, `LazyColumn` for hundreds. Give
  it an exact height of a whole number of rows (`n * rowHeight + (n - 1) * spacing`). StarUI keeps the scroll offset
  over rebuilds. Check on a capture that rows leave room for the scrollbar.
- **Mockup:** `scrollbar(x, y, h, frac, off)`.

## Empty state and reason lines

- **Empty state:** one gray line under the kept heading ("No contacts."). `Text(line, color = VanillaColors.gray)`.
- **Reason line:** why an action cannot be taken, in negative red, next to or under the control, its height reserved
  so nothing jumps: [recipes.md](recipes.md#guarded-button). Vanilla often puts it in the disabled button's tooltip
  instead (a `Modifier.tooltip` on a button shows while it is disabled); do both when the reason is not obvious.

## Not vanilla

These come from approved mod panels; vanilla has no such part. Use them only when the user asks for them, and build
them from vanilla pieces with vanilla's spacing and colours:

- a **name bar** (a text field holding the entry's name, with a favourite star): vanilla renames in a dialog;
- a **tagline** (one centred flavour line under a name);
- **markers** on a picture (an outlined icon over its corner): `Image(icon, withOutline = true)` in a `Box`;
- an **action strip** (a bordered row holding a thing and its buttons);
- **pips** for a level, a **change row** (`old -> new`, [recipes.md](recipes.md#change-row)), a **cost button**
  ([recipes.md](recipes.md#cost-button)).

## Choosing between parts

| You have | Use |
|---|---|
| Entries compared by their values | A `Table` |
| Entries to pick one from, with pictures or controls | A selectable list |
| Things known by face, one or two values each | Picture tiles |
| Two to five exclusive modes | Area toggles in one `RadioGroup` |
| Filters the player combines | Area toggles in one `FilterGroup` (vanilla's filter bar) |
| An on/off option | `Checkbox` |
| A small number to adjust | `-` value `+` with small `ALL` buttons, or a `Slider` |
| Progress toward an end | `ProgressBar` with numbers on it |
| Properties of the selected thing | `StatGrid` / label and value rows |
| A cost in resources | `CostDisplay` or highlighted text |
| Something that needs "are you sure" | The game's confirmation popup |
| A rule or rate | The `?` help tooltip |
| Many small things | An icon row with `+N` |
