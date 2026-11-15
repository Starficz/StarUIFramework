# AGENTS.md - StarUIFramework

StarUI is a Kotlin UI library for Starsector mods (mod id `staruiframework`, package `org.starficz.starui`). It has
a Compose-style DSL, but every call creates a **real Starsector UI object** (a label, a button, a `CustomPanelAPI`)
directly in the panel you call it on. There is no virtual tree and no wrapper node. Layout, input, rendering and
reactive updates are added to the native objects through PatchLib hooks.

Part 1 is for building UI with StarUI. Part 2 is for working on StarUI itself.

---

# Part 1: building UI with StarUI

## Setup

- `mod_info.json` dependencies: `staruiframework`, `lw_lazylib` (runtime Kotlin stdlib) and `patchlib`.
- Compile against `jars/StarUIFramework.jar`, the game's API jars and LazyLib. The runtime Kotlin stdlib is LazyLib's
  (LazyLib 3.1: **2.4.21**), so compile with Kotlin 2.3 or newer and `-api-version` 2.4 at most. Attach
  `jars/StarUIFramework-sources.jar` to the library for the KDoc in the IDE.
- Imports live in five packages: `org.starficz.starui` (`Size`, `Axis`, `Alignment`, `TextAlign`, `TextOverflow`,
  `VGrow`/`HGrow`, `ContentScale`, `Overflow`, `Anchor`, `UIState`, `mutableStateOf`, `StarUITheme`, `StarUIManager`,
  `StarUIPlugin`, `inject`, `InjectPlacement`, `showStarUIPanel`, `requestLayout`, `dumpLayout`), `.components`
  (Column, Text, ..., `FilterGroup`, `RadioGroup`, `key`), `.window` (windows over a screen: `Layer`, `showLayer`,
  `StarUILayer`, `Dialog`, `showDialog`, `StarUIDialog`, `DialogStatic`, `showToast`, `StarUIToast`), `.modifier` (Modifier and its extensions,
  `PaddingValues`) and `.vanilla` (Font, ButtonFont, VanillaColors, HoloBackground, the `Boxed*` types, and the
  vanilla toolkit: see below). `.runtime` holds `StarUIPanelPlugin` (the receiver of `Modifier.plugin { }`);
  everything else in it, and in `.layout`, is internal.
- Names follow Jetpack Compose where Compose has one (with Left/Right instead of Start/End: Starsector has no
  right-to-left layouts), and vanilla's where the component is a vanilla object (`SectionHeading`,
  `AreaCheckbox`, `ShipDisplay`).

## Getting UI on screen

Every component function is an extension on `UIPanelAPI`, so you can call it on any panel:

```kotlin
somePanel.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(20f, 20f))) {
    Text("Hello")
    Button("Close") { /* ... */ }
}
```

To attach to vanilla screens, use one of these:

- **`StarUIPlugin`**: implement it, override `addPanelToCampaignUI`, `addPanelToRefitTab`,
  `addPanelToCombatScreen`, etc. (see `StarUIPlugin.kt`), then call
  `StarUIManager.registerPlugin(it)` in `onApplicationLoad`. Each builder gets a full-size `CustomPanelAPI` over that
  screen, added at the moment vanilla builds it (the campaign core UI when it is made, a core tab when it opens,
  the combat UI when a battle sets it up, the warroom (command map) when it is first shown, the title screen, the
  mission screen's refit when it is shown) and back
  after vanilla clears and refills it. Nothing polls. A screen built before a plugin registered gets its panel the
  next time vanilla builds it, so register in `onApplicationLoad`. The warroom hands input to its children
  before its map, so a control in `addPanelToCombatWarroom` takes the clicks on it before the map sees them. `StarUIManager.coreUI`,
  `currentCoreUITabID`, `currentCoreUITabPanel` and the `combat*` panels read the screen that is showing now.
- **`panel.inject(key, size, modifier, placement) { }`**: for a specific vanilla sub-panel. It adds one StarUI
  `CustomPanel` (its own reactive scope) to the panel and builds your components in it. Call it at the moment
  vanilla builds the panel: a PatchLib `@After` on the method or constructor that builds it, with the hook's
  `self` (or a panel reached from it) as the receiver. Your mod then compiles against `PatchLibAPI.jar` too.

  ```kotlin
  // 0.98.5a-RC4: the refit's hullmod list (refitPanel.getModDisplay().getMods()); never clears its children
  @Patch(target = ClassMatch(typeName = "com.fs.starfarer.coreui.refit.ModWidget"))
  object RefitButtonPatch {
      @JvmStatic
      @After(target = MethodMatch(methodType = MethodType.CONSTRUCTOR))
      fun afterCreate(context: AfterContext) {
          val widget = context.self as? UIPanelAPI ?: return
          widget.inject("mymod:refit-button") { Button("AI Config") { /* ... */ } }
      }
  }
  ```

  - One injection per `key` per panel (prefix keys with your mod id). While it is live, another call returns it
    and does nothing, so a hook that fires twice, a build method that runs again, or your own every-frame script
    (the fallback where no hook point exists) can all just call it. `panel.injection(key)` finds it.
  - When the panel clears its children (how vanilla rebuilds a panel in place), the injection comes back at the
    panel's next render, above what vanilla put back, with the builder run again. So hooking the panel's first
    build is enough. `removeInjection(key)`, `dispose()` on the container, or removing it yourself ends it for good.
  - Since the builder can run again, read what it needs from the panel inside it (`this.parent`, getters,
    siblings) instead of capturing objects a vanilla rebuild replaces.
  - `placement = InjectPlacement.Bottom` puts it under the panel's own children. Not for StarUI's own reactive
    containers (it throws): put the components in their builder.
  - Example: Combat AI Tweaks' `ui/RefitButton.kt`.
- **Layers** (`window/Layers.kt`): a full-screen StarUI panel on top of whatever is showing (title, campaign with its
  dialogs and core tabs, combat), on `SettingsAPI.getScreenPanel()`; layers stack in open order and tooltips from
  inside show above. `Layer(onDismissRequest, modal, dim, pause, dismissOnEscape) { }` in a builder is shown while
  the builder calls it (Compose's `Dialog`): a rebuild that leaves it out or the caller's disposal closes it, Escape
  calls `onDismissRequest`, a `Layer` in a layer closes with it. `showLayer(modal, dim, pause, closeOnEscape,
  onClose) { layer -> }` from handlers and scripts stays until `layer.close()` or Escape. Modal (default): dims
  (`StarUITheme.layer.dim`, 0.6), takes every key and click, pauses the campaign or battle (the earlier pause state comes back
  with the last pausing layer). Non-modal: keys pass, only clicks over its content are taken. The receiver is a
  full-screen `Box` (`Modifier.align`). The campaign's core rebuild takes layers off the screen panel; they go back
  on its next render (`Layers.afterCoreUICreated` from `CoreUIScreenPatch`, the render hook). Plan:
  `docs/overlay-plan.md`.
- **Dialogs** (`window/Dialogs.kt`): `Dialog(onDismissRequest, width, height, confirmText, cancelText, onConfirm,
  onCancel, static, contentKey, codexEntryId, background, pause) { dialog -> }` in a builder (a `Layer` underneath,
  same lifetime rules) and `showDialog(width, height, ...)` from handlers: vanilla's custom dialog window, measure for
  measure (Holo of (w + 34) x (h + 69), content 10 px in, 150 x 25 Orbitron 20 TL_BR buttons bottom right, the
  confirm one with the G shortcut, dim 0.85, fade 0.3 in / 0.2 out, fold 0.15 / 0.1; opaque `HoloBackground.Dialog`
  by default; never closer than 50 px to the screen's edge: a larger size shrinks, `Float.MAX_VALUE` fills). Enter,
  Space, G and the confirm button call `onConfirm`; Escape and the cancel button `onCancel`; F2
  `showCodex(codexEntryId)`; a click outside flickers the window. `DialogStatic` picks the static bursts (open, confirm, cancel). The builder's
  `StarUIDialog` has `confirm()`, `cancel()`, `isOpen`. `dialog.visualPanel.showStarUIPanel(w, h) { }` (`SidePanel.kt`) fills an
  interaction dialog's side panel, on black by default (vanilla draws nothing behind it).
- **Toasts** (`window/Toasts.kt`): `showToast(text, duration, alignment, color)` and `showToast(duration, alignment)
  { }` from handlers and scripts (throws in a builder). One non-modal layer per screen (`ToastHost`, on
  `Layers.Stack.toasts`) holds a full-screen stage whose toast panels `ToastHost.tick` places by hand every frame,
  as Windows' notifications move: newest nearest the edge, the rest gliding to their slots (exponential, rate 12/s),
  vanilla's slide (`setSlideData`, `slideIn` / `slideOut`, 0.25 s, which also fades) from the side the place is on,
  at most 3 per place (`TOAST_MAX_SHOWN`), the rest waiting, counted by a "+N more" `MoreChip` nearest the edge
  (placed by the same `StackSlot` code; a toast that starts to leave already frees its slot in the count, so the chip
  goes as it starts sliding out); hover stops the time. A toast is parked off-screen until
  placed (a component that is not drawn is never laid out). `layer.hitTest` makes the layer take the mouse over
  toasts only. `Layers.show` raises the toasts' layer above any layer opened later. Default bottom right, in the
  campaign 220 px up (above the minimap); top centre 40 px down (under "Game paused"). Looks beyond the plain panel
  are recipes (recipes.md: `IntelToast`, `AccentToast`, `HeadingToast`).
- Box children: `Modifier.align(Alignment.TopLeft)`, also `TopCenter`, `TopRight`, `CenterLeft`, `Center`,
  `CenterRight`, `BottomLeft`, `BottomCenter`, `BottomRight` (`Alignment.entries` lists them).
- Column: `horizontalAlignment = Alignment.Left`, `CenterHorizontally` or `Right`.
- Row: `verticalAlignment = Alignment.Top`, `CenterVertically` or `Bottom`.
- Text inside its own box: `textAlign = TextAlign.Left`, `Center` or `Right` (Text, Button, SectionHeading).

## Content and controls

Argument order: what the component shows or edits (`text`, `bind`), then `size`, `modifier`, the optional settings,
and last a click handler or builder where there is one.

`Text(text, size, modifier, font, color, textAlign, highlightedText, overflow)`, `Image(spritePath, size, ...)`,
`ShipDisplay(size, modifier, fleetMember, variant, ..., exactScale)`,
`Button(text, size, modifier, contentPadding, color, backgroundColor, font: ButtonFont, textAlign, cutStyle, shortcut) { onClick }`,
`TextField(bind: UIState<String>, size, modifier, contentPadding, font, placeholder)`,
`Checkbox(text, bind, size, modifier, ..., buttonGroup)`, `AreaCheckbox(text, bind, size, modifier, ..., buttonGroup)`,
`Slider(bind: UIState<Float>, size, modifier, valueRange = 0f..100f, text, barColor, widgetColor, configure =, step)`,
`ProgressBar(progress, size, modifier, valueRange, text, barColor, configure =, animate)`,
`SectionHeading(text, size, modifier, color, backgroundColor, textAlign)`, `Table(items, size, modifier, ...) { column(...) }`,
`StatGrid { entry(label, value) }`, `StatModGrid(stat)`, `LabelledValue(label, value)`, `RelationshipBar(value)`,
`ImportanceIndicator(importance)`, `CommodityIcons { icons(id, count) }`, `CostDisplay { cost(id, quantity) }`,
`ShipList(ships)`, `FleetSummary(ships)`, `CodexEntries(title, ids)`, `Spacer(size, modifier)`,
`Divider(modifier, thickness, color)`, `FlexSpacer(weight)`.

- Controls have a `contentPadding` (space between their edge and their content, from the theme); `Modifier.padding`
  adds to it. `contentPadding = PaddingValues.Zero` makes a control as small as its content.
- `Button` takes a `ButtonFont`: the seven fonts a vanilla button can be made with. Any other is a compile error.
- `Text(..., overflow = TextOverflow.Ellipsis)` stays on one line: text wider than its room (exact or fill width,
  or what its container has left) is cut and ends in "...", with the full text in a tooltip unless the Text has a
  `Modifier.tooltip`. The label holds the shortened text; a `setText` from code becomes the new full text.
- A `buttonGroup` needs `bind`: a group works on its buttons' states (it throws otherwise). A `RadioGroup` keeps
  exactly one selected: a click selects only that button and a second click keeps it; a member's state set `true`
  from code turns the others off. A `FilterGroup` is vanilla's filter bar: a click selects only that button; Shift
  or Ctrl + click toggles just that one; clicking the only selected button selects them all.
- A `Modifier.tooltip` on a button also shows while the button is disabled.
- For vanilla calls that take a font file (`setParaFont`, `addTextField`), `Font.X.path` gives it.
- `Spacer` draws nothing; give it `Modifier.background(color)` to show it.
- `SectionHeading` is vanilla's own (`TooltipMakerAPI.addSectionHeading`) and returns its `LabelAPI`, which draws its
  own bar; it fills the width by default and its text updates in place.
- `Table` is vanilla's table: `column("Ship") { it.name }` for text, `column("Cost", width = 80f) { cell("$cost",
  color = ...) }` for styled text, `column("", width = 70f) { custom { Button(...) } }` for StarUI components in a cell
  (laid over the cell, reactive like any builder). Columns with `width` are exact; the rest share the leftover by
  `weight`. `rowTooltip`, `onRowClick` and a column's `tooltip` (on its header) work as in vanilla. Rows are all
  `itemHeight` tall, or as tall as the tallest custom cell's content needs (a button in a 20 px row would spill).
  Sorting is optional: `column(...) { ... }.sortBy(descendingFirst = false) { it.cost }` makes a column sortable (a
  header click sorts by it, a second click reverses), with vanilla's sort arrow in `sortIconColor`. StarUI sorts the
  items itself: vanilla's own sort needs a delegate interface that is obfuscated, and `beginTable` makes every column
  unsortable. The sort lives in the table, or in a `sort: UIState<TableSort?>` you pass (set, read or keep it).
- The other TooltipMakerAPI ports (`StatGrid`, `RelationshipBar`, `ShipList`, ...) build vanilla's own component on a
  temporary tooltip at the width they get and hold it in a StarUI panel; they are made again when that width
  changes or a call gives them different arguments (a builder running again for something else keeps them). Those
  that read live game objects (`StatModGrid`, `ShipList`, `FleetSummary`, `SkillPanel`, the faction or person
  `RelationshipBar`, `CostDisplay(withAvailable = true)`, the planet, cargo, event and map ones) are made again on
  every call, since those objects change without any argument changing. `CostDisplay` and the faction or person
  `RelationshipBar` need a campaign.
- Vanilla's `bringComponentToTop` also raises the panel in its parent, and so on up: inside a Column or Row it
  reorders them. To move a child above its siblings, remove it and add it again.
- For vanilla's colours use `VanillaColors` (from `vanilla`): the player faction's `base`, `dark` and `bright`, `text`,
  `gray`, `highlight`, `positive`, `negative`, the story-point colours and `panel`, each read live. The showcase
  (`staruisamples/showcase/`) is styled with them.
- `Slider` and `ProgressBar` are the vanilla slider (`SettingsAPI.createSlider`) and return a `BoxedUISlider` (its
  `SliderAPI` and the component it is); it holds one value (its `min` is a floor, `max` an overflow mark). `step` is
  a step size (Compose's `steps` is a count). `configure` is a named argument taking any `SliderAPI` setter; it runs
  on every build. A `ProgressBar`'s `text` is its whole label (`setShowLabelOnly(true)`; vanilla's would add
  "value / max" after it). For a raw `SliderAPI` from elsewhere, `asComponent` (from `vanilla`) gives the component view.
- **The vanilla toolkit** (`vanilla/UIExtensions.kt`, `VanillaHelpers.kt`, `VanillaWrappers.kt`): Kotlin access to
  vanilla UI for code that works on vanilla components directly, public and kept working. Thin calls to the readable
  methods vanilla's base component and panel have and the API interfaces leave out, plus small conveniences:
  edges and centre (`left`, `right`, `top`, `bottom`, `centerX`, `centerY`), `setSize`, `setLocation`,
  `xAlignOffset` / `yAlignOffset`, `setAbsoluteLocation`, `applyAnchor`, `fader`, `mouseoverHighlightFader`,
  `topAncestor`, `isEnabled`, the slide animation (`setSlideData`, `slideIn` / `slideOut`, `forceSlide*`,
  `isSliding`, `isSlidIn`, `isSlidOut`, `isSlidingIn`), tooltips (`addTooltip`, `setTooltipOffsetFromCenter`,
  `setTooltipPositionRelativeToAnchor`), `setMouseOverPad`; on panels `children` (a copy), `getChildrenNonCopy()`
  (vanilla's live list), `lastComponent`, `findChildWithMethod`, `allChildrenWithMethod`; `ButtonAPI.onClick`,
  `LabelAPI.autoSize`, the `ScrollPanelAPI` extras (`renderShadows`, `shadowColor`, `addToOverlay`), `measureTextSize`,
  `playSound`, `Color.withAlpha`. StarUI's components need none of them. Every one is called in the `toolkit-*`
  suite groups, so a game update that renames a vanilla method fails there first. Add a check there with each new
  helper. TooltipMakerAPI content is not part of it: StarUI wraps the pieces it supports as components
  (`VanillaElements.kt`) and does not offer a general TooltipMakerAPI escape hatch.

Each call returns the vanilla object itself where the game API covers it: `Text` returns a `LabelAPI`, `Button` a
`ButtonAPI`; containers return their `CustomPanelAPI` (a scrolling one too: `.scrollPanel`, from `components`, gives
its scroll panel).
`Image`, `ShipDisplay`, `Slider` / `ProgressBar`, `.sectorMap` and `Holo` give `BoxedUIImage`, `BoxedUIShipPreview`,
`BoxedUISlider`, `BoxedSectorMap` and `BoxedUIHolo`: small wrappers making one type of a vanilla object whose API interface is not
a component type (`SliderAPI`, `SectorMapAPI` extend nothing) or that has none (images, ship previews), the way
`BoxedUILabel` did before 0.98.5a made `LabelAPI` a component. Pass them to vanilla panel methods freely (StarUI
unwraps them); every box's `vanillaComponent` is the vanilla object itself (an obfuscated engine class), and reflective calls on
a box (`invoke`) reach it already. Where the API interface already is a component (`LabelAPI`, `ScrollPanelAPI`), it is returned as is,
and the methods it leaves out are extensions on it (`ScrollPanelAPI.addToOverlay`, `LabelAPI.autoSize`).

Vanilla ship displays fit the ship and then always shrink it to 95%, and sit it 3% above centre. For an exact scale,
pass `exactScale = 1f` (one sprite pixel per UI unit; `1f / screenScaleMult` for one per screen pixel), or set
`shipDisplay.exactScale`: the ship then draws at that scale, centred, with its edges on whole pixels
(`ShipDisplayExactScalePatch`). Vanilla's `getScale()` still reports its own 95% value.

## Sizes

A component's size is its `size` argument, the only way to size it (there are no sizing modifiers). Factories, in
Compose's words: `Size.wrapContent()`, `fillMaxWidth()`, `fillMaxWidth(height = h)`, `fillMaxHeight()`,
`fillMaxHeight(width = w)`, `fillMaxSize()`, `exact(w, h)`, and `mixed(Axis.WrapContent, Axis.FillMax(2f))` for any other pair
(`Axis.FillMax(weight)` shares leftover space by weight).

Three types, each a superset of the one before, so the compiler rules out sizes that mean nothing:
- `BoundedSize`: exact or fill on both axes. Components with no content to wrap take it: `Spacer`, `ShipDisplay`,
  `LazyColumn`, `LazyRow`, `Slider`, `ProgressBar`. `Size.wrapContent()` there is a compile error.
- `Size`: adds wrap. Everything else.
- `ImageSize`: adds `ImageSize.native(scale, maxWidth, maxHeight)` and `ImageSize.aspectWrap()`. `Image` only.

Layout rules, each of which throws a clear exception when broken:
- a fill child on a Column's or Row's main axis needs that container to have a known size on that axis (not wrap);
- a fill child along a scrolling axis gets what is left of the visible size (0 once the content overflows);
- `Modifier.anchor` is only for children of a `CustomPanel`: a Column, Row, Box or lazy list places its children
  itself, so an anchor there throws.

## Overflow: clipping, scrolling and long lists

What a container does with children that do not fit is its `overflow` argument, per axis, like CSS's `overflow-x` /
`overflow-y`: `Overflow(horizontal, vertical)` of `AxisOverflow.Visible` (spill out, the default), `Clip` (cut at the
edge) or `Scroll`. Shorthands: `Overflow.Visible`, `Overflow.Clip`, `Overflow.ScrollVertical` (a list: Clip, Scroll),
`Overflow.ScrollHorizontal`, `Overflow.Scroll` (a 2D viewport).

```kotlin
Column(Size.fillMaxSize(), overflow = Overflow.ScrollVertical) { for (s in ships) key(s.id) { ShipRow(s) } }
Row(Size.fillMaxWidth(height = 120f), overflow = Overflow.ScrollHorizontal) { ... }
Box(Size.exact(400f, 300f), overflow = Overflow.Scroll) { Image(map) }
Box(Size.exact(200f, 60f), overflow = Overflow.Clip) { ... }
```

- **A scroll axis clips both axes** (vanilla's scroll panel cuts on both): `Overflow(Visible, Scroll)` throws.
- **On a scroll axis** the children get the visible size as their room, and the content is that size or bigger. A
  fill child along a Column or Row takes what is left (a fill spacer pins a footer to the bottom of a short list; it
  is 0 once the list overflows); across a Column or Row, and in a Box, a fill child spans the whole content.
- **On a clip axis** the children are bounded by the container; anything bigger is cut, and clicks on the cut-off
  part reach nothing. Clip on both axes is vanilla's own clipping (it nests); one axis is a StarUI scissor.
- **Only an exact or fill axis overflows**: a wrapping axis grows with its children.
- A scrolling container is a vanilla scroll panel holding the StarUI container; the call still returns the container
  (its size is the content's). `container.scrollPanel` (from `components`) gives the `ScrollPanelAPI` (`scrollToY`,
  `ensureVisible`, offsets); null on other panels. The scroll position survives rebuilds; a list that gets shorter is
  scrolled back into range. Scroll panel looks (left scrollbar, shadows, colours, wheel on or off) come from
  `StarUITheme.scroll`.
- **The mouse wheel** goes to the deepest scroll panel under the cursor that can still move, and to the next one out
  at its end (game-wide, vanilla's panels too). The plain wheel scrolls vertically, or horizontally when only the
  width overflows; Shift+wheel scrolls horizontally when both do. Horizontal wheel scrolling eases like vanilla's
  vertical one. Right-drag pans the deepest panel under the cursor with something to pan.
- **Cost**: children outside a clipping or scrolling container's visible part are not drawn and get no input; they
  still advance (onAdvance, vanilla animations). A changed row lays out only itself and its ancestors. Measured
  (`bench-long-lists`, run with `-Filter bench`: benchmarks are left out of normal runs): 1000 rows of title, labels and a wrapped paragraph idle at ~5 ms per frame over drawing the
  visible text, ~100x cheaper than vanilla's own scroll panel holding the same text.
- **`LazyColumn` / `LazyRow`** for hundreds or thousands of items, or heavy rows: only the rows within about a screen
  of the view exist, each `itemHeight` tall (`itemWidth` wide), and rows just off screen do not advance. Each row's
  builder is its own scope. With `key = { it.id }` a row keeps its components while it stays near the view, and
  removing or inserting items above the view keeps what you see in place. A row that scrolls far away is thrown
  away with its native state (a text field's focus, an inner scroll position), as in Compose: keep what must survive
  in a `UIState`.

## Spacing

Two kinds, each a modifier with the same three shapes:
- `Modifier.margin(...)`: outside the component, between it and its neighbours or its container's edge (StarUI has
  margin, Compose does not);
- `Modifier.padding(...)`: inside it, between a container's edge and its children, or around a leaf's content.

Shapes: `(all)`, `(horizontal, vertical)`, or sides by name only: `(left = , top = , right = , bottom = )`, where
`horizontal =` / `vertical =` set two sides at once. Four bare numbers are not accepted, so a side is never guessed.
`Modifier.hitBoxPadding` (extra area that takes mouse input) takes the same shapes. `PaddingValues` holds padding as a
value (`PaddingValues.all(4f)`, `PaddingValues.symmetric(horizontal = 10f)`, `PaddingValues.Zero`).

## Modifiers

`.padding`, `.margin`, `.align(Alignment)` (in a Box), `.anchor(AnchorData)` (in a CustomPanel),
`.background(color)`, `.border(color, width)` / `.border(VanillaFrame)`, `.tooltip(text)` / `.tooltip { builder }`, `.onClick { }`,
`.onClickRelease`, `.onClickOutside`, `.onHover`, `.onHoverEnter`, `.onHoverExit`, `.onMouseHeld`,
`.onKeyDown` / `.onKeyUp` / `.onKeyHeld`, `.hitBoxPadding(...)`, `.onAdvance { amount -> }` (on the plugin, like `onLayout`),
`.onRender { alpha -> }`, `.onRenderBelow { alpha -> }`, `.onLayout { }` (after every layout pass),
`.onFirstLayout { }` (once), `.onPositionChanged { position -> }`, `.plugin { }` (raw `StarUIPanelPlugin` access).
`a then b` joins two modifiers (a component of your own that adds its look to the caller's `modifier`).
The render and lifecycle ones run on the component's `StarUIPanelPlugin`, so its members `x`, `y`, `width`, `height`,
`left`, `top`, `right`, `bottom`, `centerX`, `centerY` are its current size and edges (no import needed):
`.onLayout { label.text = "${width.toInt()} wide" }`. Inside `.plugin { }` the same handlers are the plugin's
`onRender { }`, `onRenderBelow { }`, `onAdvance { }`, `onLayout { }`, `onClick { }` and so on.

Borders: `.border()` is a 1 px line in the player faction's dark colour, what vanilla's campaign panels draw;
`.border(color, width)` changes it. `.border(VanillaFrame.TitleMenu)` draws `ui_border1`, the sprite frame of
vanilla's dialogs and title menu, bare, inside the component and under its content, untinted as vanilla draws it
(the game's other frame sets are only loaded by dead or tutorial code; `ui_border4` lives on only as a ship
display's old border style, `ShipDisplay(showBorder = true, newBorderStyle = false)`). Vanilla never shows it bare: for its look
(a dark scanline background over the frame's inner edge, a tint, noise, the fold animation) use `Holo`. Like a CSS border, either one takes its space: its
depth (the line's width, or `VanillaFrame.depth`: 8 for TitleMenu, 4 otherwise) is added to the padding on every
side, so `padding` is the gap inside the border. A wrapping axis grows by it; an exact or fill one keeps its size
and the content gets less room. (Compose's border takes no space; StarUI's modifiers are not order-sensitive, so
Compose's `.border().padding()` trick is not available.)

Modifiers work on every component, vanilla ones included, because they attach to the native object. The input
handlers receive the event (`{ event -> }`); a handler that does not need it just ignores it (`{ clicks++ }`).
On a vanilla `ButtonAPI`, use `Button(onClick = ...)` or `button.onClick { }` from `vanilla`; both take `() -> Unit`. A
button's previous listener is kept.

## Anchors (inside a `CustomPanel`)

```kotlin
Anchor.inside.topLeft.ofParent(xSpacing, ySpacing)       // also topCenter, center, bottomRight, ...
Anchor.below.sibling(ref).matchingLeftEdge(5f)           // above / below / leftOf / rightOf
Anchor.rightOf.previousComponent.matchingTopEdge(5f)
Anchor.my.bottomRight.toTopLeft.ofParent()               // my corner onto the reference's corner
```

For `inside` anchors the spacing moves inward from the named edges (for `center`, +x is right and +y is up). A top-level node that wraps its content re-applies its anchor
after every layout, so a bottom-right panel stays pinned while it grows.

## Reactive UI: the rules

State lives in `UIState<T>`. **Read it inside a builder and the builder follows it:**

```kotlin
val ships = UIState(listOf<Ship>())
val showDetails = UIState(false)

Column {
    Text("Fleet (${ships.value.size})")
    for (ship in ships.value) key(ship.id) {
        Row { Text(ship.name); Button("Remove") { ships.value = ships.value - ship } }
    }
    if (ships.value.isEmpty()) Text("No ships")
    Checkbox("Details", showDetails)
    if (showDetails.value) Text("...")
}
```

1. **Plain `if`, `for` and `when` just work.** Each container builder (Column, Row, Box, CustomPanel, lazy rows)
   records the `UIState`s it reads. When one changes, that builder runs again on the next frame. Nested containers
   are separate: changing a state that only an inner Row reads re-runs only that Row.
2. **Event handlers change state; they never build UI.** Adding a component to a reactive container from a click
   handler throws an error that tells you this. Write `showDetails.value = true` in the handler and put
   `if (showDetails.value) Text(...)` in the builder.
3. **Updates appear on the next frame, never in the middle of input handling.** Several writes in one frame cause
   one rebuild.
4. **A builder must not write a state it reads.** That is a loop: it is stopped after 8 passes, and the error log
   names the builder.
5. **Wrap list items in `key(id) { ... }`** when items can be inserted, removed or reordered. Without keys, calls
   are matched by position, so a row's text field could keep the focus of the row that used to be there.
6. **Re-running a builder reuses the native objects.** A `Text` call updates the existing label's string, and
   focus, caret, scroll position and hover survive. Arguments fixed at creation (a button's font or colours, a
   label's font or colour, a text field's font or placeholder) make a new object when they change. `ShipDisplay` is always
   recreated.
7. `state.peek()` reads without subscribing. Reads outside builders (handlers, `onAdvance`, render callbacks) never
   subscribe. A state is also a delegate: `var count by mutableStateOf(0)` reads and writes `value`.
8. Existing bindings still work: `TextField(bind = ...)`, `Checkbox(bind = ...)`, `Slider(bind = ...)`, `FilterGroup`
   and `RadioGroup`, and `state.onChange { }` inside a builder (cleaned up automatically when the builder re-runs or its
   UI is removed; outside a builder it throws). For a listener outside any UI, `state.observeForever { }` and
   `removeObserver`: the state holds it strongly. A bound control writes its state the moment its native value
   changes, whoever changed it: the player, or any code calling `setText` / `setChecked` / `setProgress` on the
   vanilla object.
9. UI that is removed or hidden never rebuilds. It catches up once when it is shown again.
10. Layout updates by itself: adding or removing children, changing a `Text`'s string, or a rebuild all re-lay out
    the tree on the next frame. Call `component.requestLayout()` only after changing something StarUI cannot see,
    for example a native component's size set by hand.
11. UI you throw away needs nothing: StarUI's state lives on the vanilla objects themselves, so a panel nobody
    holds any more (a screen vanilla rebuilt, a closed dialog) is collected with everything StarUI attached to it.
    `plugin.dispose()` (or `StarUIPanelPlugin.getAttached(component)?.dispose()`) switches StarUI off under a
    component right away (handlers, bindings, `UIState` listeners and scopes), for UI you keep but no longer want
    reacting; children removed by a rebuild or a vanilla `clearChildren()` are disposed automatically.
    `removeComponent` alone does not dispose, so moving a component (remove, then add elsewhere) keeps it working.
    One exception: a label taken out of a panel must be added elsewhere before that panel is thrown away, or it
    comes back without its modifiers (labels keep StarUI's state in their parent).

## Where StarUI differs from Jetpack Compose

- Calls create real, persistent Starsector objects and return them (`val b = Button(...)`), so you can keep and use
  the reference. The objects survive rebuilds when they match (rule 6).
- No `remember`, no `LaunchedEffect`: keep state in normal fields or objects (`UIState` anywhere), and use
  `Modifier.onAdvance` for per-frame work.
- Sizes are a typed `size` argument (`Size`, `BoundedSize`, `ImageSize`, with Compose's factory names) instead of sizing modifiers, so a size that means nothing for a component is a compile error; `margin` exists.
- Absolute positioning is anchors in a `CustomPanel`, not `offset`.
- Clipping and scrolling are the container's `overflow` argument (CSS-style, per axis), not `clipToBounds` /
  `verticalScroll` modifiers: a scrolling container is a different native object, chosen when it is made.
- Coordinates in raw Starsector APIs are bottom-left based; StarUI layouts and `dumpLayout()` use top-left, y down.

## Seeing what you built (no screenshots needed)

- **Hot reload**: in a game started by the starsector-hotreload skill (`skill/starsector-hotreload`), StarUI
  rebuilds every live scope after each reload (`runtime/HotReloadRefresh.kt`), so UI on screen shows changed code
  without reopening anything. Nothing runs without the agent's `starsector.hotreload.generation` property.
- `component.dumpLayout()` returns the tree below a component as text: kind, text, size, position relative to it,
  sizing, padding/margin, attached handlers and scope state (a Holo's or Dialog's content under its holo). Log it, or
  assert on it in tests.
- **Dump on request**: setting the system property `starui.dump` to a new value makes StarUI's next frame log every
  StarUI panel on screen as `dumpLayout` text (`runtime/DebugDump.kt`), between `StarUI dump <id>: ...` and
  `StarUI dump <id> end`. `hot.sh dump` sets it through the hot-reload agent and prints the block; a test inside the
  game sets it itself. Without the property it costs one clock read per frame.
- **Preview** a piece of UI in the real game renderer, without playing. From `StarUIFramework/`:
  ```powershell
  powershell -NoProfile -ExecutionPolicy Bypass -File native-smoke\run-test-suite.ps1 -Preview example -OutDir <new empty dir>
  ```
  This starts an isolated game process (about 45 s; your normal mods and saves are untouched). It writes
  `<dir>\example.png` and `<dir>\example.layout.txt` and prints the layout text. Previews are registered with
  `StarUITestSuite.preview("name") { root.Column { ... } }` in `native-smoke/kotlin/Previews.kt` (default package).
  Use `-PreviewSource my\Previews.kt` (it must define `fun registerPreviews()`) to use your own file, and
  `-ExtraSources dir,file.kt` to compile your own UI code with it (code that needs only StarUI, the game API and
  LazyLib). Needs JDK 17 at `C:\Program Files\Java\jdk-17` and IntelliJ's bundled kotlinc; see the parameters at
  the top of `run-test-suite.ps1`.

---

# Part 2: working on StarUI itself

## Layout of the source

```
src/org/starficz/starui/
  (root)                the everyday types: Sizing.kt (Size, Axis), Alignment.kt, Overflow.kt, Anchor.kt, UIState.kt,
                        StarUITheme.kt, StarUIManager.kt (screen roots), StarUIPlugin.kt, Injection.kt
                        (inject), SidePanel.kt (showStarUIPanel),
                        RequestLayout.kt, LayoutDump.kt,
                        Dsl.kt
  window/               windows over a screen: Layers.kt (Layer, showLayer, the layer stack), Dialogs.kt (Dialog,
                        showDialog), Toasts.kt (showToast); later movable windows
  components/           Containers.kt (Column, Row, Box, CustomPanel, overflow and scroll hosts, key, reuse helpers),
                        LazyLists.kt (LazyColumn, LazyRow), Content.kt (Text, Image, ShipDisplay), Controls.kt
                        (Button, TextField, checkboxes, sliders), ButtonGroup.kt (FilterGroup, RadioGroup), Spacers.kt, Sections.kt, Tables.kt,
                        Holo.kt, VanillaElements.kt (the TooltipMakerAPI ports)
  layout/               LayoutEngine.kt, StackLayoutEngine.kt (Column/Row), BoxLayoutEngine.kt, ViewportLayoutEngine.kt
                        (scrolling containers), VanillaElementEngine.kt (tooltip-built vanilla ports), NodeSize.kt
  modifier/             Modifier.kt, LayoutModifiers.kt, EventModifiers.kt, VisualModifiers.kt
  runtime/              StarUIPanelPlugin.kt (handlers, layout, scopes), UINode.kt (each component's record: size
                        rule, padding, margin, alignment, anchor, measure, layout, reuse record, plugin; NodeStore:
                        where nodes live), Scope.kt (reactive scopes, rebuild timing, reuse cursor), StarUIContext.kt
                        (build-time stacks: plugin, theme, scope, key), ClipStack.kt
  vanilla/              the vanilla toolkit (UIExtensions.kt, VanillaHelpers.kt), Boxed* (image, ship preview, slider, sector map, holo), Font, VanillaColors,
                        ReflectionUtils (internal), VanillaClasses.kt (the engine classes StarUI constructs, found at
                        load), VanillaFactory.kt (how components make vanilla elements: SettingsAPI factories where
                        they exist, else one kept scratch tooltip; text measuring; the vanilla ports' temporary
                        tooltips)
  patches/              PatchLib hooks, one per file: UIComponentLifecyclePatch (render, advance, input, culling),
                        UIPanelContainerPatch (child changes, box unwrapping), ClipInputPatch, LabelTextPatch,
                        NativeValuePatches (text field, checkbox, slider values), ButtonListenerChainPatch,
                        ScrollWheelPatch (nested and horizontal wheel), ShipDisplayExactScalePatch,
                        the StarUIPlugin screens: CoreUIScreenPatch, CombatScreenPatch, TitleScreenPatch,
                        MissionRefitPatch (the core tabs use the API's CoreUITabListener instead)
  internal/             ModPlugin (registers the tab listener on game load)
native-smoke/           in-game test suite (test-only, never shipped)
skill/                  the agent skills that ship with StarUI: starsector-starui (design guide, StarUI parts,
                        recipes, traps, verify) and starsector-hotreload (standalone hot-reload game, input and
                        captures); build_skill.py copies the public API sources and samples into starsector-starui and
                        packs both into dist/; vanilla-crawl/ regenerates references/vanilla-styles.md's counts
docs/                   design notes: reactive-scopes-plan.md, scrolling-plan.md (overflow, wheel, long-list numbers)
```

## How it works, in one paragraph per piece

- **Zero wrapper.** CustomPanels use a `StarUIPanelPlugin` as their plugin, wrapping any foreign plugin as
  `delegate`. A leaf (label, button) gets one only when it needs handlers. PatchLib hooks on the vanilla base
  component call the plugin's render, advance and input methods.
- **Nodes, and their lifetime.** Every component StarUI knows about has a `UINode`: its size rule, padding (a
  container's own, a leaf's around its content), margin, alignment, anchor, measure and layout, reuse record and
  plugin. Engines read their children's nodes; calls and modifiers write the component's own. A node lives exactly
  as long as its component, with nothing in StarUI holding either, so UI that vanilla throws away (a rebuilt tab, a
  closed dialog) is collected without being disposed (`NodeStore`): a CustomPanel's plugin holds its node; any other
  component's node sits in vanilla's per-component object store (`addListener` / `getListeners` on the engine's base
  UI component, readable names); labels have none, so a label's node sits in its parent's store and moves with the
  label (the `add` hook); a label with no parent yet is held strongly until it is added or disposed. Two lookup
  indexes, weak on both sides: every node, and (for the lifecycle hooks, which look up every vanilla component) only
  the leaves that have a plugin. The one loss: a label taken out of a panel and
  added elsewhere only after that panel was thrown away comes back without its node (and handlers). Tests:
  `leaks-*`.
- **Layout.** Containers have a `LayoutEngine`. The top-level StarUI node runs measure and layout lazily in
  `renderBelow` when `requestLayout()` has marked it; the request marks the path up from the changed node, so
  measures of unchanged nodes are reused and they are not laid out again (`StarUIPanelPlugin.measure`,
  `commitChildLayout`). Stack children are placed relative to the container at their running offset (chaining them
  on siblings made vanilla recompute every later one: quadratic). A scrolling container with an exact or fill size
  is a layout boundary.
- **Overflow.** A scrolling container is a vanilla scroll panel whose plugin has a `ViewportLayoutEngine`; the
  StarUI container sits in its content container. While a clipping or scrolling container's children render, its
  visible rectangle is on `ClipStack`, and the render hook skips StarUI children entirely outside it (they also skip
  input; `pausesOffscreenAdvance`, set by lazy lists, skips their advance). `ScrollWheelPatch` routes the wheel.
- **Scopes.** `attachScope` keeps each container's builder. `UIState` reads subscribe the current scope, and writes
  mark it dirty. A dirty scope is rebuilt from an ancestor's `renderBelow` or `advance` (`Scope.flush`), never
  during input or from scripts. A rebuild hands the old children to a `ReuseCursor`, and each component function
  asks `childSlot(kind, props)` for its match.
- **Component functions.** A leaf call gets its `childSlot`, makes or reuses its native object, adds it, and ends in
  `finishLeaf` (starting size, sizing, measure and layout on its node, the reuse record, modifiers). A container
  call goes through `containerParts` and `register`. A reused component's node is reset (`resetForBuild`) when the
  rebuild hands it out, and its call records it anew.
- **Holo.** `Holo` makes vanilla's own holo panel (found at load by its readable methods, `VanillaClasses`) and
  hands it a StarUI Box with `setNext`: the holo draws, advances and feeds input to that content itself, so the
  content is not a child in the UI tree. It is a layout root of its own (its exact size is set from the holo's) and
  its scope flushes itself; the holo's plugin owns it (`ownedOutsideTree`) and disposes it with itself.
- **Screens.** `StarUIManager` injects one screen panel per vanilla screen (`inject` with key
  `staruiframework:screen`, or `-below` under the combat ship info) holding a panel per registered plugin with a
  builder for it; none when no plugin has one. The moment comes from the API's `CoreUITabListener` for core tabs
  (guarded: vanilla can announce a tab it put off, with the old tab still showing) and from four PatchLib hooks
  for the rest. Its getters compute from `AppDriver`'s current state on every read; nothing is cached per frame.
- **Injection.** `inject` keeps a `Records` object (key to size, modifier, placement, builder, the container
  weakly) in the host panel's vanilla store (`NodeStore.findInStore` / `putInStore`), so it goes with the panel.
  The container's plugin carries `injectionKey`. `UIPanelContainerPatch.onChildrenCleared` marks a cleared live
  container's entry and its host pending; the render `@Before` in `UIComponentLifecyclePatch` (one `hasPending`
  check per component) builds those entries again on the host's next render, before its children draw. The build
  runs with outer key and read tracking hidden, so a call from inside another builder does not disturb it.
- **Registrations made during a build on a plugin that outlives it** (the container's own plugin, an older plugin)
  are undone before the next build (`Scope.addCleanup`). Handlers on reused children are cleared by
  `resetForReuse()`.

## Rules

- **Test in the real game.** `native-smoke/run-test-suite.ps1` compiles `src/` and `native-smoke/kotlin/` into a
  test-only jar and runs every group in an isolated game process (about 45 s; `-Filter name,name` runs only some).
  It ends with `SUITE SUMMARY checks=N failures=0`. Look at the PNGs too. It cannot run under Fast Rendering.
  With starsector-native-smoke 2.0 every game holds a slot: from Git Bash, run it as
  `bash "<skill>/scripts/live/smoke-slot.sh" run staruiframework.tests -- powershell.exe -NoProfile -ExecutionPolicy
  Bypass -File "<Windows path to run-test-suite.ps1>" -OutDir "<Windows path, new folder>"` (`NO_SLOT` otherwise).
  Write the test first and see it fail when practical. Tests are Kotlin in the default package, registered in
  `native-smoke/kotlin/TestFramework.kt`.
- **No obfuscated names** in code, PatchLib matchers, casts or imports. Find engine classes and members by API
  interface, readable name or shape. Prefer the API whenever it has the method (0.98.5a added many UI ones:
  `getParent`, `getChildrenCopy2`, `clearChildren`, `relativeTo2`, most of `ScrollPanelAPI`, `SliderAPI`).
  Look engine code up in the `starsector-knowledge` skill (`api/`, then `obf/`, the decompiled engine of the
  game version it names); do not assume a decompile of your own, and one from an older game version will mislead
  you.
- **Never use `java.lang.reflect` directly**: the game's script class loader refuses it at runtime
  (`SecurityException`). Go through `ReflectionUtils` (`invoke`, `getMethodsMatching`, `ReflectedMethod`).
- A reflective `invoke("name")` throws "Ambiguous method call" when the API declares the same method with a narrower
  return type (the class then has a bridge method too). Call the API method instead.
- **Public signatures: break them freely until the first public release.** Nothing is released yet, so a better
  API wins over compatibility: rename, remove and change return types without shims or deprecated copies. Combat AI
  Tweaks, Scy Nation and Refit Filters are compiled against StarUI; rebuild them (or note what they need) after a
  change that affects them. After the first release: add and deprecate, don't remove or change.
- **The agent skill ships with StarUI.** A change to public behaviour or names also updates `skill/starsector-starui/`
  (`references/` name components, parameters and behaviour), and a new version updates its `SKILL.md` Version
  section. `python skill/build_skill.py` refuses to build when SKILL.md names another version than `mod_info.json`.
  The recipes in `references/recipes.md` are compiled and run by the `skill-recipes` suite group
  (`native-smoke/kotlin/SkillRecipes.kt`): change both together.
- Keep each file's existing line endings (most source files are LF, some CRLF).
- Imports: five or more names from one package become a wildcard (`org.starficz.starui.components.*`); never for
  `java.*` (`java.util.*` and `java.awt.*` would shadow Kotlin's `List`) or object members. Sorted in IntelliJ's
  order: others, then `java`, `javax`, `kotlin`.
- `jars/StarUIFramework.jar` is committed and built from IntelliJ; the test runner never touches it. After rebuilding
  it, run `python build_sources_jar.py` so `jars/StarUIFramework-sources.jar` (the KDoc for users' IDEs, not in
  `mod_info.json`) matches.
