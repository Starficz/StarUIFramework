# Components

Behaviour of components, sizes, overflow and modifiers. Parameter lists are a guide: take exact signatures from
`api/org/starficz/starui/`.

Contents: [Containers](#containers) · [Alignment](#alignment) · [Content and controls](#content-and-controls) ·
[Tables](#tables) · [Vanilla element ports](#vanilla-element-ports) · [What a call returns](#what-a-call-returns) ·
[Sizes](#sizes) · [Overflow, scrolling and long lists](#overflow-scrolling-and-long-lists) · [Spacing](#spacing) ·
[Modifiers](#modifiers) · [Borders and frames](#borders-and-frames) · [Anchors](#anchors) · [Theme](#theme) ·
[Layers, dialogs and toasts](#layers-dialogs-and-toasts) · [The vanilla toolkit](#the-vanilla-toolkit)

Names follow Compose (Left/Right, not Start/End), or vanilla's for vanilla objects (`SectionHeading`, `AreaCheckbox`,
`ShipDisplay`). Argument order: what it shows or edits (`text`, `bind`, `items`), `size`, `modifier`, options, then
the click handler or builder.

## Containers

| Call | Places children | Default size |
|---|---|---|
| `Column(size, modifier, grow, horizontalAlignment, spacing, overflow) { }` | top to bottom, `spacing` apart (`VGrow.Upwards` reverses) | wrap |
| `Row(size, modifier, grow, verticalAlignment, spacing, overflow) { }` | left to right (`HGrow.Leftwards` reverses) | wrap |
| `Box(size, modifier, overflow) { }` | stacked; `Modifier.align(Alignment.X)` places | wrap |
| `AnchorPanel(size, modifier, overflow) { }` | by `Modifier.anchor` (top left without); sizes them, wraps around them | wrap |
| `CustomPanel(size, modifier, overflow) { }` | by `Modifier.anchor`; neither places nor sizes them (escape hatch) | fill; exact or fill only |
| `LazyColumn(items, size, modifier, itemHeight, key, spacing) { item -> }` / `LazyRow(..., itemWidth, ...)` | one row per item, only near the view; scrolls | exact or fill only |
| `Section(title, size, modifier, contentPadding, spacing, backgroundColor, borderColor) { }` | `SectionHeading` on top (none if `title = null`), padded column below, thin dark border | fill width, wrap height |
| `Holo(size, modifier, open, background, contentKey, foldDuration, animateFirstOpen, contentInset) { }` | vanilla's holo window around a Box; `open` folds it; a `contentKey` change swaps content through vanilla's static | exact or fill only |
| `Theme(theme) { }` | not a panel: calls inside default to `theme` | |

Column and Row start top left. `Modifier.padding` is space inside the edges, `spacing` the gap between children.
`FlexSpacer(weight)` takes a share of the leftover (pins what follows to the far end).

## Alignment

- Box children: `Modifier.align(Alignment.TopLeft)`, `TopCenter`, `TopRight`, `CenterLeft`, `Center`, `CenterRight`,
  `BottomLeft`, `BottomCenter`, `BottomRight`.
- Column: `horizontalAlignment = Alignment.Left` / `CenterHorizontally` / `Right`.
- Row: `verticalAlignment = Alignment.Top` / `CenterVertically` / `Bottom`.
- Text in its own box: `textAlign = TextAlign.Left` / `Center` / `Right` (Text, Button, SectionHeading).

## Content and controls

- `Text(text, size, modifier, font, color, textAlign, highlightedText, overflow)`: a vanilla label. Wrap: as wide as
  its longest line; exact or fill width: wraps and grows in height. `overflow = TextOverflow.Ellipsis`: one line, cut
  with `...` and the full text in a tooltip (unless it has its own `Modifier.tooltip`); `label.text` then reads the
  cut text. Names wrap to two lines before being cut ([wording](design-rules.md#wording)).
  `highlightedText = listOf("1200" to VanillaColors.highlight)`: vanilla's matching, in order, whole words ("1"
  never lights the 1 of "10"); list pieces in text order.
- `Image(spritePath, size, modifier, ...)`: `ImageSize.native(scale, maxWidth, maxHeight)` / `ImageSize.aspectWrap()`
  keep the aspect.
- `ShipDisplay(size, modifier, fleetMember, variant, ..., exactScale)`: vanilla's ship preview (fits, then 95%, 3%
  above centre); `exactScale = 1f`: one sprite pixel per UI unit, centred, pixel-sharp.
- `Button(text, size, modifier, contentPadding, color, backgroundColor, font: ButtonFont, textAlign, cutStyle, shortcut) { onClick }`.
  `ButtonFont` only (other fonts don't compile). Colours, font, alignment, cut, shortcut are fixed at creation (a
  change makes a new button); the label is not. No `enabled` argument: set `button.isEnabled` from state read in the
  builder. A `Modifier.tooltip` shows while disabled too (where vanilla says why).
- `TextField(bind: UIState<String>, size, modifier, contentPadding, font, placeholder)`.
- `Checkbox(text, bind, size, modifier, ..., buttonGroup)`, `AreaCheckbox(text, bind, ...)` (vanilla's area toggle).
  A `buttonGroup` needs `bind` on every member:
  - `RadioGroup()`: exactly one selected; a second click keeps it; setting one `true` from code turns the others off.
    Modes: `val modes = List(3) { mutableStateOf(it == 0) }`.
  - `FilterGroup()`: vanilla's filter bar: click selects only it, Shift/Ctrl+click toggles it, clicking the only
    selected one selects all. Not for modes or tabs.

  Tabs are buttons, not toggles ([recipes.md](recipes.md#tabs)).
- `Slider(bind: UIState<Float>, size, modifier, valueRange = 0f..100f, text, barColor, widgetColor, configure =, step)`,
  `ProgressBar(progress, size, modifier, valueRange, text, barColor, configure =, animate)`: vanilla's slider widget
  (same drawing as its bars). `min` is a floor, `max` an overflow mark; `step` is a step size; `configure` takes
  `SliderAPI` setters, every build. A bar's `text` is its whole label (`setShowLabelOnly(false)` adds "value / max");
  a slider's is followed by its value.
- `SectionHeading(text, size, modifier, color, backgroundColor, textAlign)`: vanilla's heading bar, returned as its
  `LabelAPI`; fills the width; text updates in place.
- `Spacer` draws nothing (`Modifier.background` shows it); `Divider(modifier, thickness, color)`; `FlexSpacer(weight)`.
- Controls' `contentPadding` (theme) sits between edge and content; `Modifier.padding` adds to it;
  `PaddingValues.Zero` shrinks a control to its content.

## Tables

`Table(items, size, modifier, ...) { column(...) }` is vanilla's table:

```kotlin
Table(ships, Size.fillMaxWidth(), rowTooltip = { ship -> addPara(ship.description, 0f) }, onRowClick = { selected.value = it }) {
    column("Ship") { it.name }.sortBy { it.name }
    column("Cost", width = 80f, align = TextAlign.Right) { cell("${it.cost}", color = VanillaColors.highlight) }
        .sortBy(descendingFirst = true) { it.cost }
    column("", width = 70f) { custom { Button("Sell", Size.exact(60f, 20f), Modifier.align(Alignment.Center)) { sell(it) } } }
}
```

- Text cells are in `baseColor` (faction base, cyan for the player); plain text needs `cell(text, color = VanillaColors.text)`.
- `{ text }`, `{ cell(text, color) }`, `{ custom { StarUI } }` (laid over the cell, reactive). Custom content has wrap
  or exact height, placed with `Modifier.align`: a child filling the height throws (fatal in game).
- Columns with `width` are exact; the rest share the leftover by `weight`. A column's `tooltip` shows on its header.
- Rows are `itemHeight` (theme 20), or the tallest custom cell. One line per row: a second line goes to `rowTooltip`
  or its own column.
- `.sortBy { }` makes a header sortable (second click reverses, vanilla's arrow). The sort lives in the table or in a
  `sort: UIState<TableSort?>` you pass. Without sortable columns, items keep their order.

## Vanilla element ports

`StatGrid { entry(label, value) }`, `StatModGrid(stat)`, `LabelledValue(label, value)`, `RelationshipBar(value)`,
`ImportanceIndicator(importance)`, `CommodityIcons { icons(id, count) }`, `CostDisplay { cost(id, quantity) }`,
`ShipList(ships)`, `FleetSummary(ships)`, `CodexEntries(title, ids)`, `CargoList`, `PlanetInfo`, `SkillPanel`,
`SkillIcon(skillId, size, learned, elite, thinBorder)` / `SkillIcon(skillId, person)`, `EventProgressBar`,
`SurveyRequirements`, `SectorMap`, `PlaythroughData`.

Vanilla's own pieces, built on a temporary tooltip at the width they get and held in a StarUI panel; made again on a
width or argument change. `SkillIcon` is the engine's icon itself, remade on a size change; its `person` overload
reads the level live. Made again on every call (live game objects): `StatModGrid`, `ShipList`, `FleetSummary`,
`SkillPanel`, faction or person `RelationshipBar`, `CostDisplay(withAvailable = true)`, planet, cargo, event, map.
Need a campaign: `CostDisplay`, faction or person `RelationshipBar`, the campaign ones.

### TooltipMakerPanel: last resort

Only for vanilla content with no StarUI component and no simple StarUI build (a mod's helper that fills a tooltip).
Highlights, headings, tables, buttons, stat blocks: `Text(highlightedText = )`, `SectionHeading`, `Table`, `Button`,
`StatGrid`.

`TooltipMakerPanel(size, modifier, inputs, onButtonClick) { width -> addPara(...) }`: the block fills a fresh tooltip
`width` wide; the panel is as tall as the content. Wrap takes the room's width (300 unbounded).

- Made again (block re-run, content state lost) on a width change, a change of a `UIState` the block read (only the
  content), or a run of the builder around it unless `inputs` are equal. No side effects in the block.
- `addButton` clicks call `onButtonClick(data)`; `onClick { }` works on returned buttons.
- StarUI calls inside the block throw. Content is inset 5 px, as in vanilla tooltips.

## What a call returns

The vanilla object where the API has a type: `Text` a `LabelAPI`, `Button` a `ButtonAPI`, containers a
`CustomPanelAPI` (scrolling ones too; `.scrollPanel` gives the `ScrollPanelAPI`). `Image`, `ShipDisplay`,
`Slider`/`ProgressBar`, `.sectorMap`, `Holo` return `BoxedUIImage`, `BoxedUIShipPreview`, `BoxedUISlider`,
`BoxedSectorMap`, `BoxedUIHolo`: wrappers for vanilla objects with no component type. Pass them to vanilla methods
freely; `vanillaComponent` is the object.

## Sizes

The `size` argument is the only way to size (no sizing modifiers): `Size.wrapContent()`, `fillMaxWidth()`,
`fillMaxWidth(height = h)`, `fillMaxHeight()`, `fillMaxHeight(width = w)`, `fillMaxSize()`, `exact(w, h)`,
`mixed(Axis.WrapContent, Axis.FillMax(2f))` (`FillMax(weight)` shares leftover by weight).

- `BoundedSize`: exact or fill only: `Spacer`, `ShipDisplay`, `LazyColumn`, `LazyRow`, `Slider`, `ProgressBar`,
  `Holo`, `SkillIcon`. Wrap there does not compile.
- `Size`: adds wrap. Everything else.
- `ImageSize`: adds `native`, `aspectWrap`. `Image` only.

Throws when broken:
- a fill child on a Column's or Row's main axis needs a known (not wrap) size there;
- a fill child along a scrolling axis gets what is left of the visible size (0 once it overflows);
- `Modifier.anchor` only in `CustomPanel` / `AnchorPanel`.

## Overflow, scrolling and long lists

`overflow = Overflow(horizontal, vertical)` of `AxisOverflow.Visible` (default), `Clip`, `Scroll`. Shorthands:
`Overflow.Visible`, `Clip`, `ScrollVertical` (a list), `ScrollHorizontal`, `Scroll`.

```kotlin
Column(Size.fillMaxSize(), overflow = Overflow.ScrollVertical) { for (s in ships) key(s.id) { ShipRow(s) } }
Box(Size.exact(400f, 300f), overflow = Overflow.Scroll) { Image(map) }
```

- **A scroll axis clips both axes**: `Overflow(Visible, Scroll)` throws.
- **Only exact or fill axes overflow**; wrap grows.
- Scroll axis: children get the visible size as room; a fill child takes what is left (a fill spacer pins a footer
  under a short list; 0 once it overflows).
- Clip axis: the excess is cut and takes no clicks.
- `container.scrollPanel`: `scrollToY`, `ensureVisible`, offsets. **Scroll position survives rebuilds**; a shorter
  list is scrolled back into range. Scrollbar side, shadows, colours: `StarUITheme.scroll`.
- Wheel: the deepest scroll panel under the cursor that can still move, then outward; Shift+wheel horizontal when both
  scroll. Right-drag pans.
- Children outside the visible part are not drawn and get no input.
- **`LazyColumn` / `LazyRow`** for hundreds of items or heavy rows: rows exist only within about a screen of the view.
  `key = { it.id }` keeps a row's components while near the view and the view in place when items above change. A
  row scrolled far away loses its native state (focus): keep what must survive in a `UIState`.

## Spacing

- `Modifier.margin(...)`: outside, between it and neighbours or the container's edge.
- `Modifier.padding(...)`: inside, between a container's edge and children, or around a leaf's content.

Shapes: `(all)`, `(horizontal, vertical)`, or named sides (`left =`, `top =`, `right =`, `bottom =`; `horizontal =`,
`vertical =`). Four bare numbers don't compile. `Modifier.hitBoxPadding(...)` (extra input area) takes the same.
`PaddingValues.all(4f)`, `.symmetric(horizontal = 10f)`, `.Zero`.

## Modifiers

`.padding`, `.margin`, `.align` (Box), `.anchor` (CustomPanel / AnchorPanel), `.background(color)`, `.border(...)`,
`.tooltip(text)` / `.tooltip { }`, `.onClick`, `.onClickRelease`, `.onClickOutside`, `.onHover`, `.onHoverEnter`,
`.onHoverExit`, `.hoverState(state)`, `.onMouseHeld`, `.onKeyDown` / `.onKeyUp` / `.onKeyHeld`, `.hitBoxPadding`,
`.onAdvance { amount -> }`, `.onRender { alpha -> }`, `.onRenderBelow { alpha -> }`, `.onLayout { }` (every layout),
`.onFirstLayout { }`, `.onPositionChanged { }`, `.fitVanillaDialog()` ([setup.md](setup.md#panels-vanilla-hands-you)),
`.plugin { }` (the `StarUIPanelPlugin`).

- `a then b` joins modifiers (`Modifier.border() then modifier` in a component of your own). Order does not matter.
- Re-applied each time the call runs: `Modifier.background(if (selected.value) a else b)` follows the state when the
  builder making the component reads it. Reads inside render callbacks never subscribe.
- `.hoverState(hovered)`: a `UIState<Boolean>` true while hovered; read it in the builder for a hover look.
- They work on every component, vanilla ones too. Input handlers get the event (`{ event -> }`).
- Render and lifecycle handlers run on the plugin: `x`, `y`, `width`, `height`, `left`, `top`, `right`, `bottom`,
  `centerX`, `centerY` are current (`.onLayout { label.text = "${width.toInt()} wide" }`). Render callbacks draw in
  screen coordinates, y up.
- `.plugin { }`: `consumeEvents = true` (input stops here), `customData`, the same handlers.
- `.tooltip(text, width = 300f, location = ABOVE)`: one paragraph; `.tooltip { addPara(...) }` builds with
  `TooltipMakerAPI` when shown (live values).
- Vanilla `ButtonAPI`: `Button(onClick = )` or `button.onClick { }` (`vanilla`); the previous listener is kept.

## Borders and frames

- `.border()`: 1 px line in the player faction's dark colour (vanilla's campaign panels); `.border(color, width)`.
- `.border(VanillaFrame.TitleMenu)`: `ui_border1`, vanilla's dialog and title-menu sprite frame, bare and untinted.
  For vanilla's look (scanlines, tint, noise, fold) use `Holo`.
- **A border takes space** (CSS-like): its depth is added to padding on every side. Wrap grows; exact or fill gives
  the content less room. Children's `align` / `margin` count from inside it: `Modifier.border().padding(5f)` puts
  content 6 px from the outer edge.

## Anchors

In a `CustomPanel` or `AnchorPanel` only:

```kotlin
Anchor.inside.topLeft.ofParent(xSpacing, ySpacing)       // also topCenter, center, bottomRight, ...
Anchor.below.sibling(ref).matchingLeftEdge(5f)           // above / below / leftOf / rightOf
Anchor.rightOf.previousComponent.matchingTopEdge(5f)
Anchor.my.bottomRight.toTopLeft.ofParent()               // my corner onto the reference's corner
```

`inside` spacing moves inward from the named edges (`center`: +x right, +y up). A wrapping top-level node re-applies
its anchor after every layout, so a bottom-right panel stays pinned while it grows.

- `AnchorPanel` (Android's `RelativeLayout`) sizes children and itself: wrapping, it reaches its anchored children
  (one anchored to its centre or far edge needs its own size plus offset); a fill child takes the panel's size; a
  child without anchor sits top left. `inject` builds one.
- `CustomPanel` leaves children alone: each sizes itself against the panel and places itself; vanilla components
  added by hand stay put. Exact or fill only; directly in a vanilla panel it fills and follows it.

## Theme

`StarUITheme` holds every component's defaults, one class each (`text`, `textField`, `button`, `areaCheckbox`,
`checkbox`, `slider`, `progressBar`, `scroll`, `divider`, `sectionHeading`, `section`, `shipDisplay`, `image`,
`table`). Arguments passed to a call win.

```kotlin
val compact = StarUITheme.current.copy(button = StarUITheme.current.button.copy(contentPadding = PaddingValues.Zero))
Theme(compact) { /* every Button in here uses it */ }
```

`null` colours fall back to vanilla's live colours. Set a panel's repeated choices (button height, section padding,
row height) once in a theme.

## Layers, dialogs and toasts

`org.starficz.starui.window`; when to use which: [setup.md](setup.md#a-layer-over-a-screen). Two forms each: in a
builder (shown while the builder calls it) or from a handler/script (open until closed). Each throws where the
other belongs.

| In a builder | From a handler or script | What |
|---|---|---|
| `Layer(onDismissRequest, modal, dim, pause, dismissOnEscape) { }` | `showLayer(modal, dim, pause, closeOnEscape, onClose) { layer -> }` → `StarUILayer` (`close()`, `isOpen`, `panel`) | full-screen `Box` over the screen; modal: dims (theme `layer.dim`), takes all input, pauses, Escape dismisses |
| `Dialog(onDismissRequest, size, confirmText, cancelText, onConfirm, onCancel, static, contentKey, codexEntryId, background, pause) { dialog -> }` | `showDialog(size, ...) { dialog -> }` (closes on confirm or cancel) | vanilla's custom dialog on a modal layer, content-sized by default (wrap; exact or fill per axis); `confirm()`, `cancel()`, `isOpen`; `static = DialogStatic.Vanilla` / `CancelOnly` / `None` |
| | `showToast(text, duration, alignment, color)`, `showToast(duration, alignment) { }` → `StarUIToast` (`close()`, `isOpen`) | a notice that slides in and leaves; three per place, the rest as "+N more" |

## The vanilla toolkit

`org.starficz.starui.vanilla`: readable vanilla methods the API leaves out, for code on vanilla panels you reach
yourself (StarUI's components need none).

- Edges and centre: `left`, `right`, `top`, `bottom`, `centerX`, `centerY`; `setSize`, `setLocation`,
  `setAbsoluteLocation`, `xAlignOffset` / `yAlignOffset`, `applyAnchor`.
- State and animation: `isEnabled`, `fader`, `mouseoverHighlightFader`, `topAncestor`; slides (`setSlideData`,
  `slideIn` / `slideOut`, `forceSlideIn` / `forceSlideOut`, `isSliding`, `isSlidIn`, `isSlidOut`, `isSlidingIn`).
- Tooltips: `addTooltip`, `setTooltipOffsetFromCenter`, `setTooltipPositionRelativeToAnchor`, `setMouseOverPad`.
- Panels: `children` (copy), `getChildrenNonCopy()` (live), `lastComponent`, `findChildWithMethod`,
  `allChildrenWithMethod`.
- `ButtonAPI.onClick`, `LabelAPI.autoSize`, `ScrollPanelAPI` extras (`renderShadows`, `shadowColor`, `addToOverlay`),
  `measureTextSize(text, font)`, `playSound`, `Color.withAlpha(alpha)`.
- `VanillaColors`: `base`, `dark`, `bright` (player faction UI), `text`, `gray`, `highlight`, `positive`, `negative`,
  `story`, `storyDark`, `storyBright`, `panel`, read live.
- `Font` (every game font; `Font.X.path` for vanilla calls) and `ButtonFont`.
