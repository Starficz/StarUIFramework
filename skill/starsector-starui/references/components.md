# Components

Behaviour of every component, size, overflow mode and modifier. Parameter lists here are a guide: take the exact
signature from `api/org/starficz/starui/` before calling.

Contents: [Containers](#containers) · [Alignment](#alignment) · [Content and controls](#content-and-controls) ·
[Tables](#tables) · [Vanilla element ports](#vanilla-element-ports) · [What a call returns](#what-a-call-returns) ·
[Sizes](#sizes) · [Overflow, scrolling and long lists](#overflow-scrolling-and-long-lists) · [Spacing](#spacing) ·
[Modifiers](#modifiers) · [Borders and frames](#borders-and-frames) · [Anchors](#anchors) · [Theme](#theme) ·
[Layers, dialogs and toasts](#layers-dialogs-and-toasts) · [The vanilla toolkit](#the-vanilla-toolkit)

Names follow Jetpack Compose where Compose has one (Left and Right instead of Start and End: Starsector has no
right-to-left layouts), and vanilla's where the component is a vanilla object (`SectionHeading`, `AreaCheckbox`,
`ShipDisplay`). Argument order: what the component shows or edits (`text`, `bind`, `items`), then `size`,
`modifier`, the optional settings, and last a click handler or builder.

## Containers

| Call | Places children | Default size |
|---|---|---|
| `Column(size, modifier, grow, horizontalAlignment, spacing, overflow) { }` | top to bottom, `spacing` apart (`grow = VGrow.Upwards` reverses) | wrap |
| `Row(size, modifier, grow, verticalAlignment, spacing, overflow) { }` | left to right (`HGrow.Leftwards` reverses) | wrap |
| `Box(size, modifier, overflow) { }` | on top of each other; place children with `Modifier.align(Alignment.X)` | wrap |
| `AnchorPanel(size, modifier, overflow) { }` | where their `Modifier.anchor(...)` puts them (top left without one); sizes them, wraps around them | wrap |
| `CustomPanel(size, modifier, overflow) { }` | where their `Modifier.anchor(...)` puts them; StarUI neither places nor sizes them (the escape hatch) | fill; exact or fill only |
| `LazyColumn(items, size, modifier, itemHeight, key, spacing) { item -> }` / `LazyRow(..., itemWidth, ...)` | one row per item; only those near the view exist; scrolls | exact or fill only |
| `Section(title, size, modifier, contentPadding, spacing, backgroundColor, borderColor) { }` | vanilla-style block: a `SectionHeading` on top (none for `title = null`), the content in a padded column below, inside a thin dark border | fill width, wrap height |
| `Holo(size, modifier, open, background, contentKey, foldDuration, animateFirstOpen, contentInset) { }` | vanilla's own holo panel (the framed window of its dialogs and title menu) around a Box; `open` folds it with vanilla's static; a `contentKey` change swaps the content through vanilla's static transition | exact or fill only |
| `Theme(theme) { }` | not a panel: the calls inside take their defaults from `theme` | |

Column and Row start at the left and top, like Compose. Space inside a container's edges is `Modifier.padding`;
`spacing` is the gap between its children. `FlexSpacer(weight)` in a Column or Row takes a share of the leftover
space (pins what follows it to the far end).

## Alignment

- Box children: `Modifier.align(Alignment.TopLeft)`, also `TopCenter`, `TopRight`, `CenterLeft`, `Center`,
  `CenterRight`, `BottomLeft`, `BottomCenter`, `BottomRight`.
- Column: `horizontalAlignment = Alignment.Left`, `CenterHorizontally` or `Right`.
- Row: `verticalAlignment = Alignment.Top`, `CenterVertically` or `Bottom`.
- Text inside its own box: `textAlign = TextAlign.Left`, `Center` or `Right` (Text, Button, SectionHeading).

## Content and controls

- `Text(text, size, modifier, font, color, textAlign, highlightedText, overflow)`: a vanilla label. With the default
  wrap size it is as wide as its longest line; with an exact or fill width it wraps to that width and grows in
  height. `overflow = TextOverflow.Ellipsis` keeps it to one line instead: text wider than its room (its exact or
  fill width, or what its Row has left) is cut and ends in `...`, with the full text in a tooltip unless the `Text`
  has a `Modifier.tooltip` of its own (a parent's tooltip, a tile's say, shows over it). `label.text` then reads the
  shortened text. Names wrap to two lines before they are cut ([design-rules.md](design-rules.md#wording)).
  `highlightedText = listOf("1200" to VanillaColors.highlight)` colours parts of it. Vanilla's label does the
  matching: each string is found in order, the search for one starting after the last match, at word boundaries, so
  "1" colours the word 1 in "Costs 1 point", not the 1 in "10". List the pieces in the order they appear.
- `Image(spritePath, size, modifier, ...)`: a sprite. `ImageSize.native(scale, maxWidth, maxHeight)` and
  `ImageSize.aspectWrap()` keep its aspect.
- `ShipDisplay(size, modifier, fleetMember, variant, ..., exactScale)`: vanilla's ship preview. Vanilla fits the ship
  and then shrinks it to 95%, 3% above centre; `exactScale = 1f` draws one sprite pixel per UI unit, centred, on
  whole pixels.
- `Button(text, size, modifier, contentPadding, color, backgroundColor, font: ButtonFont, textAlign, cutStyle, shortcut) { onClick }`.
  `ButtonFont` holds the fonts a vanilla button can be made with; any other is a compile error. Colours, font,
  alignment, cut and shortcut are fixed when the button is made (a change makes a new button); the label is not.
  There is no `enabled` argument: set `button.isEnabled` on the returned `ButtonAPI`, from state read in the builder.
  A `Modifier.tooltip` on a button also shows while it is disabled (where vanilla says why it is blocked).
- `TextField(bind: UIState<String>, size, modifier, contentPadding, font, placeholder)`.
- `Checkbox(text, bind, size, modifier, ..., buttonGroup)` and `AreaCheckbox(text, bind, ...)`: a tick box with a
  label, and vanilla's area toggle (the selectable row or tab look). A `buttonGroup` needs `bind` on every member
  and is one of two kinds:
  - `RadioGroup()`: exactly one selected. A click selects only that button, a second click keeps it; a member's
    state set `true` from code turns the others off. For two to five exclusive modes: one `UIState<Boolean>` per
    mode (`val methods = List(3) { mutableStateOf(it == 0) }`), the chosen one is the index that is `true`.
  - `FilterGroup()`: vanilla's filter bar. A click selects only that button, Shift or Ctrl + click toggles just
    that one, and a click on the only selected button selects them all. Wrong for modes and tabs.

  Tabs are buttons, not toggles ([recipes.md](recipes.md#tabs)).
- `Slider(bind: UIState<Float>, size, modifier, valueRange = 0f..100f, text, barColor, widgetColor, configure =, step)`
  and `ProgressBar(progress, size, modifier, valueRange, text, barColor, configure =, animate)`: vanilla's slider
  widget (the same drawing as vanilla's bars). One value; `min` is a floor, `max` an overflow mark. `step` is a step
  size. `configure` takes any `SliderAPI` setter and runs on every build. A bar's `text` is its whole label
  (`configure = { setShowLabelOnly(false) }` adds vanilla's own "value / max" after it); a slider's `text` is
  followed by its value.
- `SectionHeading(text, size, modifier, color, backgroundColor, textAlign)`: vanilla's own heading bar
  (`addSectionHeading`), returned as its `LabelAPI`. Fills the width by default; its text updates in place.
- `Spacer(size, modifier)` draws nothing (give it `Modifier.background(color)` to show it); `Divider(modifier,
  thickness, color)` is a line; `FlexSpacer(weight)` fills leftover space.
- Controls have a `contentPadding` (from the theme) between their edge and their content; `Modifier.padding` adds to
  it. `contentPadding = PaddingValues.Zero` makes a control as small as its content.

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

- Text cells are in the table's `baseColor` (the faction's base colour, cyan for the player) unless `cell(text,
  color = ...)` gives one: a name column that should read as plain text needs `color = VanillaColors.text`.
- `column(title) { text }` for text, `{ cell(text, color = ...) }` for styled text, `{ custom { ... } }` for StarUI
  components in a cell (laid over the cell, reactive like any builder). A custom cell's content has a wrap or exact
  height, placed with `Modifier.align` (`CenterLeft`, `Center`): the table measures it to size the rows, and a child
  that fills the height throws there (a fatal error in game).
- Columns with `width` are exact; the rest share the leftover by `weight`. A column's `tooltip` shows on its header.
- Rows are all `itemHeight` tall (theme: 20), or as tall as the tallest custom cell needs. Vanilla's rows hold one
  line: a second line (a ship's class under its name) goes into `rowTooltip` or its own column.
- Sorting is optional: `.sortBy { }` on a column makes a header click sort by it, a second click reverses it, with
  vanilla's sort arrow. The sort lives in the table, or in a `sort: UIState<TableSort?>` you pass to set, read or keep
  it. A table without sortable columns keeps the items' order.

## Vanilla element ports

`StatGrid { entry(label, value) }`, `StatModGrid(stat)`, `LabelledValue(label, value)`, `RelationshipBar(value)`,
`ImportanceIndicator(importance)`, `CommodityIcons { icons(id, count) }`, `CostDisplay { cost(id, quantity) }`,
`ShipList(ships)`, `FleetSummary(ships)`, `CodexEntries(title, ids)`, `CargoList`, `PlanetInfo`, `SkillPanel`,
`SkillIcon(skillId, size, learned, elite, thinBorder)` / `SkillIcon(skillId, person)`,
`EventProgressBar`, `SurveyRequirements`, `SectorMap`, `PlaythroughData`.

These are vanilla's own TooltipMakerAPI pieces, built on a temporary tooltip at the width they get and held in a
StarUI panel. `SkillIcon` is the engine's icon itself, remade on a size change; the `person` overload reads the
level live. They are made again when that width changes or the call's arguments change. Those that read live game
objects (`StatModGrid`, `ShipList`, `FleetSummary`, `SkillPanel`, a faction or person `RelationshipBar`,
`CostDisplay(withAvailable = true)`, the planet, cargo, event and map ones) are made again on every call. `CostDisplay`,
the faction or person `RelationshipBar` and the campaign ones need a campaign.

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

The vanilla object itself where the API has a type for it: `Text` a `LabelAPI`, `Button` a `ButtonAPI`, containers
their `CustomPanelAPI` (a scrolling one too; `.scrollPanel` gives its `ScrollPanelAPI`). `Image`, `ShipDisplay`,
`Slider`/`ProgressBar`, `.sectorMap` and `Holo` return `BoxedUIImage`, `BoxedUIShipPreview`, `BoxedUISlider`,
`BoxedSectorMap` and `BoxedUIHolo`: small wrappers for vanilla objects whose API type is not a component or that have
none. Pass them to vanilla panel methods freely (StarUI unwraps them); `vanillaComponent` is the vanilla object.

## Sizes

A component's size is its `size` argument, the only way to size it (there are no sizing modifiers):
`Size.wrapContent()`, `fillMaxWidth()`, `fillMaxWidth(height = h)`, `fillMaxHeight()`, `fillMaxHeight(width = w)`,
`fillMaxSize()`, `exact(w, h)`, and `mixed(Axis.WrapContent, Axis.FillMax(2f))` for any other pair
(`Axis.FillMax(weight)` shares leftover space by weight).

Three types, each a superset of the one before, so the compiler rules out sizes that mean nothing:

- `BoundedSize`: exact or fill on both axes. Components with nothing to wrap take it: `Spacer`, `ShipDisplay`,
  `LazyColumn`, `LazyRow`, `Slider`, `ProgressBar`, `Holo`. `Size.wrapContent()` there is a compile error.
- `Size`: adds wrap. Everything else.
- `ImageSize`: adds `native` and `aspectWrap`. `Image` only.

Layout rules, each of which throws a clear exception when broken:

- a fill child on a Column's or Row's main axis needs that container to have a known size on that axis (not wrap);
- a fill child along a scrolling axis gets what is left of the visible size (0 once the content overflows);
- `Modifier.anchor` is only for children of a `CustomPanel` or an `AnchorPanel`.

## Overflow, scrolling and long lists

What a container does with children that do not fit is its `overflow` argument, per axis:
`Overflow(horizontal, vertical)` of `AxisOverflow.Visible` (spill out, the default), `Clip` or `Scroll`. Shorthands:
`Overflow.Visible`, `Overflow.Clip`, `Overflow.ScrollVertical` (a list), `Overflow.ScrollHorizontal`, `Overflow.Scroll`.

```kotlin
Column(Size.fillMaxSize(), overflow = Overflow.ScrollVertical) { for (s in ships) key(s.id) { ShipRow(s) } }
Box(Size.exact(400f, 300f), overflow = Overflow.Scroll) { Image(map) }
```

- **A scroll axis clips both axes**: `Overflow(Visible, Scroll)` throws.
- **Only an exact or fill axis overflows**: a wrapping axis grows with its children.
- On a scroll axis the children get the visible size as their room. A fill child along a Column or Row takes what is
  left (a fill spacer pins a footer to the bottom of a short list; it is 0 once the list overflows).
- On a clip axis anything bigger is cut, and clicks on the cut part reach nothing.
- `container.scrollPanel` gives the `ScrollPanelAPI` (`scrollToY`, `ensureVisible`, offsets). **The scroll position
  survives rebuilds**; a list that gets shorter is scrolled back into range. Scrollbar side, shadows and colours come
  from `StarUITheme.scroll`.
- The mouse wheel goes to the deepest scroll panel under the cursor that can still move, then outward. Shift+wheel
  scrolls horizontally when both axes scroll. Right-drag pans.
- Children outside a clipping or scrolling container's visible part are not drawn and get no input.
- **`LazyColumn` / `LazyRow`** for hundreds or thousands of items or heavy rows: only rows within about a screen of
  the view exist, each `itemHeight` tall. `key = { it.id }` keeps a row's components while it stays near the view,
  and keeps the view in place when items above it are added or removed. A row that scrolls far away is thrown away
  with its native state (a text field's focus): keep what must survive in a `UIState`.

## Spacing

- `Modifier.margin(...)`: outside the component, between it and its neighbours or its container's edge.
- `Modifier.padding(...)`: inside it, between a container's edge and its children, or around a leaf's content.

Shapes: `(all)`, `(horizontal, vertical)`, or sides by name (`left =`, `top =`, `right =`, `bottom =`; `horizontal =`
and `vertical =` set two). Four bare numbers are not accepted. `Modifier.hitBoxPadding(...)` (extra area that takes
mouse input) takes the same shapes. `PaddingValues` holds padding as a value (`PaddingValues.all(4f)`,
`PaddingValues.symmetric(horizontal = 10f)`, `PaddingValues.Zero`).

## Modifiers

`.padding`, `.margin`, `.align(Alignment)` (in a Box), `.anchor(AnchorData)` (in a CustomPanel or AnchorPanel), `.background(color)`,
`.border(...)`, `.tooltip(text)` / `.tooltip { builder }`, `.onClick { }`, `.onClickRelease`, `.onClickOutside`,
`.onHover`, `.onHoverEnter`, `.onHoverExit`, `.hoverState(state)`, `.onMouseHeld`, `.onKeyDown` / `.onKeyUp` / `.onKeyHeld`,
`.hitBoxPadding(...)`, `.onAdvance { amount -> }`, `.onRender { alpha -> }`, `.onRenderBelow { alpha -> }`,
`.onLayout { }` (after every layout pass), `.onFirstLayout { }`, `.onPositionChanged { position -> }`,
`.fitVanillaDialog()` (sizes the vanilla dialog it sits in to it: [setup.md](setup.md#panels-vanilla-hands-you)),
`.plugin { }` (the component's `StarUIPanelPlugin`).

- `a then b` joins two modifiers: a component of your own adds its look to the caller's `modifier`
  (`Modifier.border() then modifier`).
- Modifiers are not order-sensitive (unlike Compose).
- Modifiers are re-applied each time the call runs: `Modifier.background(if (selected.value) a else b)` follows the
  state when the builder making the component reads it. Reads inside render callbacks never subscribe.
- `.hoverState(hovered)`: a `UIState<Boolean>` true while hovered; read it in the builder for a hover look.
- They work on every component, vanilla ones included, because they attach to the native object.
- Input handlers receive the event (`{ event -> }`) and may ignore it.
- The render and lifecycle handlers run on the component's `StarUIPanelPlugin`, whose `x`, `y`, `width`, `height`,
  `left`, `top`, `right`, `bottom`, `centerX`, `centerY` are its current size and edges:
  `.onLayout { label.text = "${width.toInt()} wide" }`. Render callbacks draw in screen coordinates, y up.
- Inside `.plugin { }`: `consumeEvents = true` (input stops here), `customData`, and the same handlers
  (`onRender { }`, `onAdvance { }`, `onClick { }`, ...).
- `.tooltip(text, width = 300f, location = ABOVE)` is one paragraph; `.tooltip { addPara(...) }` builds it with
  vanilla's `TooltipMakerAPI` when it shows, so it reads live values.
- On a vanilla `ButtonAPI` use `Button(onClick = ...)` or `button.onClick { }` from `vanilla`; a button's previous
  listener is kept.

## Borders and frames

- `.border()` is a 1 px line in the player faction's dark colour, what vanilla's campaign panels draw;
  `.border(color, width)` changes it.
- `.border(VanillaFrame.TitleMenu)` draws `ui_border1`, the sprite frame of vanilla's dialogs and title menu, bare and
  untinted. Vanilla never shows it bare: for its look (scanline background, tint, noise, the fold animation) use
  `Holo`.
- **A border takes space**, like CSS: its depth is added to the padding on every side, so `padding` is the gap inside
  the border. A wrapping axis grows by it; an exact or fill axis keeps its size and the content gets less room.
  Children's `align` / `margin` count from inside it: `Modifier.border().padding(5f)` puts content 6 px from the
  outer edge.

## Anchors

Inside a `CustomPanel` or an `AnchorPanel` only:

```kotlin
Anchor.inside.topLeft.ofParent(xSpacing, ySpacing)       // also topCenter, center, bottomRight, ...
Anchor.below.sibling(ref).matchingLeftEdge(5f)           // above / below / leftOf / rightOf
Anchor.rightOf.previousComponent.matchingTopEdge(5f)
Anchor.my.bottomRight.toTopLeft.ofParent()               // my corner onto the reference's corner
```

For `inside` anchors the spacing moves inward from the named edges (for `center`, +x is right and +y is up). A
top-level node that wraps its content re-applies its anchor after every layout, so a bottom-right panel stays pinned
while it grows.

The two anchored containers differ in who sizes what:

- `AnchorPanel` (like Android's `RelativeLayout`) sizes its children and itself: wrapping, it is as large as its
  anchored children reach (one anchored to its centre or far edge needs its own size plus its offset); a child that
  fills an axis takes the panel's size on it; a StarUI child without an anchor sits at the top left. `inject` builds
  one.
- `CustomPanel` leaves its children alone: each sizes itself against the panel and places itself by its anchor, and
  vanilla components added by hand stay as they were put. Its own size never comes from its children, so it is exact
  or fill (a wrap size does not compile); straight in a vanilla panel, it fills that panel and follows its resizes.

## Theme

`StarUITheme` holds the defaults every component takes its optional arguments from, one class per component
(`text`, `textField`, `button`, `areaCheckbox`, `checkbox`, `slider`, `progressBar`, `scroll`, `divider`,
`sectionHeading`, `section`, `shipDisplay`, `image`, `table`). An argument passed to a call always wins.

```kotlin
val compact = StarUITheme.current.copy(button = StarUITheme.current.button.copy(contentPadding = PaddingValues.Zero))
Theme(compact) { /* every Button in here uses it */ }
```

Colours left `null` in a theme fall back to vanilla's live colours. Set a panel's repeated choices (button height,
section padding, table row height) once in a theme instead of on every call.

## Layers, dialogs and toasts

From `org.starficz.starui.window`. What each is for, with examples: [setup.md](setup.md#a-layer-over-a-screen).
Each comes in two forms: one called in a builder, shown for as long as the builder calls it (closed when a rebuild
leaves it out or its UI goes away), and one called from a handler or a script, open until closed. Each form throws
where the other belongs.

| In a builder | From a handler or script | What |
|---|---|---|
| `Layer(onDismissRequest, modal, dim, pause, dismissOnEscape) { }` | `showLayer(modal, dim, pause, closeOnEscape, onClose) { layer -> }` returns `StarUILayer` (`close()`, `isOpen`, `panel`) | a full-screen `Box` over the screen; modal: dims (theme `layer.dim`), takes all input, pauses, Escape dismisses |
| `Dialog(onDismissRequest, size, confirmText, cancelText, onConfirm, onCancel, static, contentKey, codexEntryId, background, pause) { dialog -> }` | `showDialog(size, ...) { dialog -> }` (closes itself on confirm or cancel) | vanilla's custom dialog window on a modal layer, as large as its content by default (`size` wraps; exact or fill per axis); `dialog.confirm()`, `dialog.cancel()`, `isOpen`; `static = DialogStatic.Vanilla` / `CancelOnly` / `None` |
| | `showToast(text, duration, alignment, color)`, `showToast(duration, alignment) { }` returns `StarUIToast` (`close()`, `isOpen`) | a notice that slides in and goes away by itself; at most three per place, the rest counted by "+N more" |

## The vanilla toolkit

`org.starficz.starui.vanilla` has Kotlin access to vanilla UI for code that works on vanilla components directly:
readable methods vanilla's base component and panel have that the API interfaces leave out, plus small conveniences.

- Edges and centre: `left`, `right`, `top`, `bottom`, `centerX`, `centerY`; `setSize`, `setLocation`,
  `setAbsoluteLocation`, `xAlignOffset` / `yAlignOffset`, `applyAnchor`.
- State and animation: `isEnabled`, `fader`, `mouseoverHighlightFader`, `topAncestor`; the slide animation
  (`setSlideData`, `slideIn` / `slideOut`, `forceSlideIn` / `forceSlideOut`, `isSliding`, `isSlidIn`, `isSlidOut`,
  `isSlidingIn`).
- Tooltips on any component: `addTooltip`, `setTooltipOffsetFromCenter`, `setTooltipPositionRelativeToAnchor`,
  `setMouseOverPad`.
- Panels: `children` (a copy), `getChildrenNonCopy()` (vanilla's live list), `lastComponent`, `findChildWithMethod`,
  `allChildrenWithMethod`.
- `ButtonAPI.onClick`, `LabelAPI.autoSize`, `ScrollPanelAPI` extras (`renderShadows`, `shadowColor`,
  `addToOverlay`), `measureTextSize(text, font)`, `playSound`, `Color.withAlpha(alpha)`.
- `VanillaColors`: `base`, `dark`, `bright` (the player faction's UI colours), `text`, `gray`, `highlight`, `positive`,
  `negative`, `story`, `storyDark`, `storyBright`, `panel`, each read live.
- `Font` (every game font; `Font.X.path` for vanilla calls that take a font file) and `ButtonFont`.

StarUI's components need none of the toolkit; it is for working with vanilla panels you reach yourself.
