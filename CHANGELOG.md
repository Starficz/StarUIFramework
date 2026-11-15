# StarUIFramework changelog

## 1.0.0-alpha

First release, an alpha: the API may still change before 1.0.0.

- Kotlin DSL that builds the game's own UI objects (labels, buttons, panels) directly, with layout, input and
  rendering added through PatchLib.
- Containers: Column, Row, Box, CustomPanel with anchors, Section, Holo, Theme. Sizes per axis (exact, fill,
  wrap), spacing, padding and margins, Spacer, FlexSpacer, Divider.
- Overflow per axis on every container: visible, clip or scroll, with the mouse wheel going to the innermost
  scrolling container. LazyColumn and LazyRow for long lists.
- Content and controls: Text, Image, ShipDisplay, Button, Checkbox, AreaCheckbox, radio groups, TextField, Slider,
  ProgressBar, Table (optionally sortable), tooltips.
- Vanilla parts as components: StatGrid, StatModGrid, LabelledValue, CostDisplay, FleetSummary, ShipList,
  CargoList, CommodityIcons, PlanetInfo, SectorMap, SkillPanel, RelationshipBar, ImportanceIndicator,
  EventProgressBar, SurveyRequirements, PlaythroughData, CodexEntries.
- Reactive state: UIState / mutableStateOf. A builder that reads a state rebuilds when it changes; key() keeps
  list items' UI when they move.
- Windows on any screen: showLayer, showDialog (Holo windows that stack) and showToast.
- Screens through StarUIPlugin: title screen, campaign, combat, and the refit, fleet, cargo, character, intel,
  map and outposts tabs. inject() adds UI to any other vanilla panel from a PatchLib hook.
- KDoc on the whole public API, shipped as jars/StarUIFramework-sources.jar for your IDE.
- A showcase in game: Ctrl + Shift + U on the title screen, in the campaign or in combat opens a page for every
  feature, each demo next to its code.
- Built for Starsector 0.98.5a-RC6. Requires LazyLib and PatchLib.
