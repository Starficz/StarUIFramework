# StarUIFramework changelog

## 1.0.0-alpha.2

Second alpha: the API may still change before 1.0.0.

Breaking:

- Requires LazyLib 3.1 (its Kotlin 2.4 runtime). Compile with Kotlin 2.3 or newer, `-api-version` 2.4 at most.
- CustomPanel is the escape hatch again: it neither places nor sizes its children, and its size is exact or fill
  (fill by default); wrap no longer compiles. Anchored children in a panel that wraps around them: the new
  AnchorPanel.
- Dialog and showDialog take the content's `size: Size` instead of a width and height.

Added:

- AnchorPanel: places children by Modifier.anchor, sizes them and wraps around them. inject builds one: fill
  follows the vanilla panel, no anchor goes top left.
- Dialogs size to their content by default (wrap), follow it as it changes, and never come closer than 50 px to
  the screen edge. They are opaque by default (vanilla's dialog background), with vanilla's odd window heights.
  Modifier.fitVanillaDialog() sizes vanilla's own dialogs to StarUI content.
- findAll / find on any panel, vanilla's included (holo and dialog content, panel-drawn buttons): kind, text, key,
  screen position, whether it is shown, click point. dumpLayout walks the same tree and lists holo content.
- StarUITesting: instant animations and a window host for scripted tests.
- TooltipMakerPanel: a last resort for TooltipMakerAPI content StarUI has no component for, laid out and updated
  like the rest.
- SkillIcon: vanilla's framed skill icon on its own (learned, elite, thin border, or a person's level live).
- Modifier.hoverState(state): hover into a UIState<Boolean>.
- The KDoc is inside jars/StarUIFramework.jar, next to the classes: no separate sources jar to attach.
- The starui.dump system property logs every StarUI panel on screen as dumpLayout text.

Fixed:

- A fill or wrap injection no longer stays 1x1 at the vanilla panel's bottom left.
- No layout warnings for a vanilla panel that has no size yet.
- An injection without an anchor starts at the panel's top left once; placing it yourself afterwards sticks instead
  of snapping back on every layout.

Agent skills (starsector-starui, starsector-hotreload 2.1.0): RC6, terser docs, a gallery of 68 vanilla parts,
console, campaign, find, click, wheel and dump commands for the hot game.

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
- Licences: the library is LGPL-3.0-only, the samples and showcase are CC0 1.0 (full texts in licenses/).
