# Parts list

Vanilla's panel elements: when vanilla uses each, its look, how to build it. Numbers from vanilla's code
([vanilla-styles.md](vanilla-styles.md)); "(engine)" measured on 0.98a-RC8. Where vanilla has a component, StarUI
wraps it: use that, not a copy. "Mockup" names the `scripts/mockup/kit.py` helper.

Contents: [Title line](#title-line) · [Tabs](#tabs) · [Section heading](#section-heading) · [Table](#table) ·
[Selectable list](#selectable-list) · [Picture tiles](#picture-tiles) · [Label and value](#label-and-value) ·
[Bars](#bars) · [Costs and resources](#costs-and-resources) · [Requirements](#requirements) ·
[Icons, portraits, ships, skills](#icons-portraits-ships-skills) · [Buttons and the bottom row](#buttons-and-the-bottom-row) ·
[Tooltip](#tooltip) · [Confirmation popup](#confirmation-popup) · [Scroll list](#scroll-list) ·
[Empty state and reason lines](#empty-state-and-reason-lines) · [Not vanilla](#not-vanilla) ·
[Choosing between parts](#choosing-between-parts)

## Title line

- **Vanilla:** a section heading across the top (pickers), or the name in Orbitron with a `?` at the right (refit,
  intel).
- **Build:** `SectionHeading(title)`; `?` = `Image(Global.getSettings().getSpriteName("fleetScreen", "icon_more_info"))`
  at the right with `Modifier.tooltip(width = 350f) { ... }` holding rules and rates.
- **Mockup:** `title(w, s, sub, help)`.

## Tabs

- **Vanilla:** core tabs, intel's Intel / Planets / Factions, command's Colonies / Income: `CutStyle.TOP`, counts in
  the label ("Intel (1)"), yellow hotkeys. Selected: filled, bright label; others base on dark.
- **Build:** `Button(label, Size.exact(w, 20f), cutStyle = CutStyle.TOP)` per tab on one `UIState<Int>`
  ([recipe](recipes.md#tabs)). Not a `FilterGroup` (a click on the selected tab selects all).
- **Rule:** a tab with nothing to show for the selection is not drawn.
- **Mockup:** `tabs(x, y, w, labels, selected)`.

## Section heading

- **Vanilla:** `addSectionHeading`: a full-width dark bar, text **centred** in base colour (278 of 284), 10 px above
  and 10 to its content. (engine) 18 px high.
- **Build:** `SectionHeading(text)` in a column with `spacing = 10f`; another faction:
  `SectionHeading(text, color = faction.baseUIColor, backgroundColor = faction.darkUIColor)`.
- **Rule:** never last in a scroll area, never alone: an empty group keeps heading and one gray line.
- **Check:** vanilla draws 10 px wider than asked; check the bar's right end at 2x.
- **Mockup:** `heading(x, y, w, s, 'center', note)`.

## Table

- **Vanilla:** `beginTable`: header strip in faction colours, **20 px rows**, value columns 70 to 150 px, one name
  column taking the rest; numbers right in highlight; only the selected row filled; sortable headers show an arrow.
- **Build:** `Table(items) { column(...) }`, `.sortBy { }`, `onRowClick`, `rowTooltip`.
- **Mockup:** `thead(x, y, w, cols)`, `row(...)`.

## Selectable list

- **Vanilla:** officer, administrator, colony and intel lists. Row height by content
  ([density](design-rules.md#density)): 30 to 44 icon and name, 54 to 68 picture and two lines, 120 officer with
  skills and buttons; 3 to 5 apart. Selected: dark fill, nothing else changes.
- **Columns:** fixed x shared by all rows; identity left (icon, name, gray second line), numbers right in highlight;
  one value per cell.
- **Build:** many values: `Table`. Pictures or controls:
  `Box(Size.fillMaxWidth(height = rowH), Modifier.background(if (selected) VanillaColors.dark else Color.BLACK).onClick { selectedId.value = id }.tooltip(...)) { Row(Size.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, spacing = 5f) { ... } }`,
  in `LazyColumn(items, itemHeight = rowH, spacing = 3f, key = { it.id })` for long lists.
- **Trap:** the row's tooltip goes on its container, not its labels.
- **Mockup:** `row(x, y, w, h, inner, selected)`.

## Picture tiles

- **Vanilla:** colony industries: picture tiles with a heading strip, in a grid; detail in a tooltip or side panel.
- **Build:** `Column(spacing = 10f)` of `Row(spacing = 10f)`s of `Column { SectionHeading(name); Image(...) }` with
  `Modifier.onClick`. `PlanetInfo`, `ShipList`, `SkillPanel` bring their own tiles.
- **Mockup:** `tile(...)`.

## Label and value

- **Vanilla:** stat blocks, label left, value right in highlight on one right edge (refit stats, colony summary);
  label-over-value pairs in headers (colony screen).
- **Build:** `StatGrid { entry(label, value) }`, `LabelledValue(label, value)`, `StatModGrid(stat)`; by hand
  `Row(Size.fillMaxWidth()) { Text(label); FlexSpacer(); Text(value, color = VanillaColors.highlight) }` in a
  `Column(spacing = 3f)`.
- **Mockup:** `grid(x, y, w, rows)`.

## Bars

- **Vanilla:** the slider widget as a bar: black track, soft fill brightest mid, end lines, numbers centred
  ("4,200 / 5,000"). Colour by meaning:

| Key (`Global.getSettings().getColor`) | Default | Use |
|---|---|---|
| `progressBarStandardColor` | 0,121,216 | Progress with no cargo meaning |
| `progressBarCargoColor` | 203,203,144 | Cargo space |
| `progressBarFuelColor` | 255,125,25 | Fuel |
| `progressBarCrewColor` | 0,225,160 | Crew |
| `progressBarWarningColor` | 255,210,0 | Near a limit |
| `progressBarDangerColor` | 255,50,0 | Over a limit, failing |
| `progressBarOverflowColor` | 255,0,0 a200 | Above capacity |
| `progressBarFleetPointsColor` | 0,181,216 | Fleet points, deployment |

- **Build:** `ProgressBar(value, Size.fillMaxWidth(height = 20f), valueRange = 0f..max, text = "4,200 / 5,000", barColor = Global.getSettings().getColor("progressBarStandardColor"))`.
  `text` is the whole label (`configure = { setShowLabelOnly(false) }` adds "value / max"; `null` shows nothing).
  Thin bar under a row's state: `Size.exact(70f, 4f)`, no text. Events: `EventProgressBar`; relations:
  `RelationshipBar`. Never a bar of your own.
- **Mockup:** `bar(x, y, w, h, frac, color, label)`.

## Costs and resources

- **Vanilla:** commodity icons with amounts (`CostDisplay`, `CommodityIcons`), amount in highlight, red when short;
  a single price as text ("Costs %s credits").
- **Build:** `CostDisplay { cost(id, quantity) }` (campaign), `CommodityIcons { icons(id, count) }`, or
  `Text("Costs 40 supplies", highlightedText = listOf("40" to VanillaColors.highlight))`.
- **Mockup:** `costrow(...)`, `stack(...)`.

## Requirements

- **Vanilla:** `SurveyRequirements`: met in normal colour, unmet in negative.
- **Build:** `SurveyRequirements(...)`; by hand a `Column(spacing = 3f)` of `Text`s in `text` or `negative`.

## Icons, portraits, ships, skills

- **Vanilla:** icons for words (commodities, credits, ships, skills, factions, submarkets); ships in a ship display,
  square portraits, skills in their aptitude frame.
- **Build:** `Image(sprite, ImageSize...)`; `ShipDisplay(Size.exact(s, s), fleetMember = ...)` (`exactScale` for
  1:1); `ShipList`, `FleetSummary`; `CodexEntries` for codex links.
- **Rule:** icon rows are at most two rows, then `+N` with the rest in its tooltip.
- **Skills:**
  - `SkillIcon(skillId, size, learned, elite, thinBorder)` / `SkillIcon(skillId, person)` (level read live):
    vanilla's framed icon alone, 40 x 40 default. For strips and cells of your own. No tooltip: add
    `Modifier.tooltip { }`. Mockup kit: `skill`.
  - `SkillPanel(person)`: the whole panel with names, two per line, ~43 px a line (five skills 130 px). Big: a user
    who wanted a strip rejects it. Test person: `Global.getFactory().createPerson()`, `stats.setSkillLevel(id, 1f)`
    (2 elite).
  - Scope is per effect, not per skill: Point Defense lists `PointDefense$Level1` as `SHIP` and as
    `SHIP_FIGHTERS`. Group a skill's effects by type; show a shared script once.
- **Storages:** a submarket's icon is its `icon` column in `data/campaign/submarkets.csv`
  (`graphics/factions/storage.png`); no settings getter.
- **Trap:** weapons have no single sprite (turret and barrel files).
- **Mockup:** `portrait`, `ship`, `iconrow`, `skill`.

## Buttons and the bottom row

- **Vanilla:** [buttons.md](buttons.md): faction base on dark; story colours only for story points; commit row
  bottom right `TL_BR`; action banks on the thing on screen `BL_TR`.
- **Build:** `Column(Size.fillMaxSize(), spacing = 10f) { Column(Size.fillMaxSize(), overflow = Overflow.ScrollVertical) { body }; Row(Size.fillMaxWidth(), spacing = 5f) { FlexSpacer(); Button("Cancel", cutStyle = CutStyle.TL_BR) { ... }; Button("Confirm", cutStyle = CutStyle.TL_BR) { ... } } }`
  (the scroll area takes what the pinned row leaves).

## Tooltip

- **Vanilla:** 350 wide, 450 with a table or sections; title in highlight when naming a thing; paragraphs 10 apart;
  numbers in highlight; opens left of controls in a right column.
- **Build:** `Modifier.tooltip(width = 350f, location = TooltipMakerAPI.TooltipLocation.BELOW) { addPara(...) }`
  (default 300, above); built when shown, so live.
- **Mockup:** `tooltip(x, y, w, h, inner)`.

## Confirmation popup

- **Vanilla:** before destructive or irreversible actions: question in Insignia 21, name in highlight, Confirm and
  Cancel bottom right. (engine) content 500 x 130, window 520 x 185.
- **Build:** a `Dialog` ([setup.md](setup.md#a-dialog)). **Mockup:** `popup(text_html)`.

## Scroll list

- **Vanilla:** a scroll panel, bar at the right, ending on a whole row.
- **Build:** `Column(size, overflow = Overflow.ScrollVertical)` for tens of rows, `LazyColumn` for hundreds; exact
  height of whole rows (`n * rowHeight + (n - 1) * spacing`); offset kept over rebuilds; leave room for the bar.
- **Mockup:** `scrollbar(x, y, h, frac, off)`.

## Empty state and reason lines

- **Empty:** one gray line under the kept heading ("No contacts."): `Text(line, color = VanillaColors.gray)`.
- **Reason:** why an action is blocked, in negative, by the control, its height reserved
  ([recipe](recipes.md#guarded-button)). Vanilla often uses the disabled button's tooltip; do both when not obvious.

## Not vanilla

From approved mod panels; only when the user asks, built from vanilla pieces, spacing and colours:

- a **name bar** (text field with the name and a favourite star; vanilla renames in a dialog);
- a **tagline** (one centred flavour line under a name);
- **markers** on a picture: `Image(icon, withOutline = true)` in a `Box`;
- an **action strip** (bordered row: a thing and its buttons);
- **pips** for a level, a **change row** ([recipe](recipes.md#change-row)), a **cost button**
  ([recipe](recipes.md#cost-button)).

## Choosing between parts

| You have | Use |
|---|---|
| Entries compared by value | `Table` |
| Entries to pick from, with pictures or controls | Selectable list |
| Things known by face, one or two values | Picture tiles |
| Two to five exclusive modes | Area toggles, one `RadioGroup` |
| Filters the player combines | Area toggles, one `FilterGroup` |
| On/off option | `Checkbox` |
| A small number | `-` value `+` with `ALL` buttons, or `Slider` |
| Progress | `ProgressBar` with numbers |
| Properties of the selection | `StatGrid` / label and value rows |
| A cost in resources | `CostDisplay` or highlighted text |
| "Are you sure" | The game's confirm popup |
| A rule or rate | The `?` tooltip |
| Many small things | Icon row with `+N` |
