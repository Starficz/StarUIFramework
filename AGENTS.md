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
  **2.1.x**, so compile with `-api-version 2.1`.
- Imports live in `org.starficz.starui.compose` (sizes), `.compose.components` (Column, Text, ...),
  `.compose.modifier` (Modifier and its extensions), `.compose.runtime` (UIState, StarUIManager, ...) and
  `org.starficz.starui.core` (Anchor, Font, vanilla helpers).

## Getting UI on screen

Every component function is an extension on `UIPanelAPI`, so you can call it on any panel:

```kotlin
somePanel.Column(LayoutSize.wrap(), Modifier.anchor(Anchor.inside.topLeft.ofParent(20f, 20f))) {
    Text("Hello")
    Button("Close") { /* ... */ }
}
```

To attach to vanilla screens, use one of these:

- **`StarUIPlugin`**: implement it, override `addPanelToCampaignUI`, `addPanelToRefitTab`,
  `addPanelToCombatScreen`, etc. (see `compose/runtime/StarUIPlugin.kt`), then call
  `StarUIManager.registerPlugin(it)` in `onApplicationLoad`. Each builder gets a full-size `CustomPanelAPI` over that
  screen and is re-injected whenever vanilla rebuilds the screen.
- **`DynamicInjection`**: for a specific vanilla sub-panel. `findTarget()` returns the panel (or null) and
  `UIPanelAPI.injectUI()` adds your components. Register with `StarUIManager.registerDynamicInjection(it)`. It
  re-runs when your injected element disappears. Example: Combat AI Tweaks' `ui/RefitButton.kt`.

## Containers

| Call | Places children | Default size |
|---|---|---|
| `Column(size, modifier, grow, horizontalAlignment, yPadding, xPadding, spacing) { }` | top to bottom (`grow = VGrow.Upwards` reverses) | fill width, wrap height |
| `Row(size, modifier, grow, verticalAlignment, xPadding, yPadding, spacing) { }` | left to right (`HGrow.Leftwards` reverses) | wrap width, fill height |
| `Box(size, modifier) { }` | stacked on top of each other; position children with `Modifier.align(Alignment.X)` | wrap |
| `CustomPanel(size, modifier) { }` | nowhere: you position children with `Modifier.anchor(...)` | wrap |
| `FramedPanel(...) { }` | a `CustomPanel` with background and border | wrap |
| `ScrollPanel(size, modifier, align, yPadding, xPadding, spacing, ...) { }` | top to bottom, scrollable; **needs a bounded size** | fill |
| `Theme(theme) { }` | not a panel: overrides `StarUITheme` defaults for the calls inside | |

Column and Row default to the start (left / top) like Compose. `VerticalStackLayout` and `HorizontalStackLayout` are
deprecated aliases that centre by default.

## Content and controls

`Text(text, size, modifier, font, color, textAlign, highlightedText)`, `Image(spritePath, size, ...)`,
`ShipDisplay(size, modifier, fleetMember, variant, ...)`, `Button(text, size, modifier, ..., font, cutStyle,
shortcut) { onClick }`, `TextField(bind: UIState<String>, size, modifier, font, hint)`,
`Checkbox(text, ..., bind, buttonGroup)`, `AreaCheckbox(text, ..., bind, buttonGroup)`,
`Slider(bind: UIState<Float>, rangeMin, rangeMax, size, modifier, text, step) { configure }`,
`ProgressBar(progress, rangeMin, rangeMax, size, modifier, text, animate) { configure }`, `Spacer(size, modifier,
color)`, `Divider()`, `FlexSpacer(weight)`.

`Slider` and `ProgressBar` are the vanilla slider (`SettingsAPI.createSlider`) and return its `SliderAPI`; it holds one
value (its `min` is a floor, `max` an overflow mark). `configure` takes any `SliderAPI` setter (colours, display mode)
and runs on every build. Use `slider.asComponent` (from `core`) for the `UIComponentAPI` view.

Each call returns the vanilla object itself where the game API covers it: `Text` returns a `LabelAPI`, `ScrollPanel`
a `ScrollPanelAPI` (its builder's components go into the scrolling content container), `Button` a `ButtonAPI`.
`Image` and `ShipDisplay` return `BoxedUIImage` / `BoxedUIShipPreview`, small wrappers for components that have no API;
pass them to vanilla panel methods freely (StarUI unwraps them).

`Button` supports only the fonts vanilla buttons have (`ORBITRON_12_CONDENSED` default, `ORBITRON_20(_BOLD)`,
`ORBITRON_24(_BOLD)`, `VICTOR_10/14`). Fonts are the `Font` enum in `core/Font.kt`.

## Sizes

Each component takes a typed size: `Size` (content), `LayoutSize` (Column/Row/Box), `PanelSize` (panels, Spacer,
ScrollPanel), `ImageSize`. They all have the same factories: `wrap(padding)`, `fill(weight)`, `exact(w, h)`,
`fillX(...)`, `fillY(...)`, `mixed(Axis.Wrap(), Axis.Fill())`.

Layout rules, each of which throws a clear exception when broken:
- a `fill` child on a Column's or Row's main axis needs that container to have a known size on that axis (not `wrap`);
- nothing can `fill` inside a ScrollPanel's scrolling direction;
- `Modifier.anchor` is not allowed inside a Column or Row (they place their children themselves).

## Modifiers

`Modifier.width(px)`, `.height(px)`, `.size(w, h)`, `.fillMaxWidth()`, `.fillMaxHeight()`, `.fillMaxSize()`,
`.wrapContentWidth()`, `.padding(...)` (space inside), `.margin(left, top, right, bottom)` (space outside; StarUI
has margin, Compose does not), `.align(Alignment)` (in a Box), `.anchor(AnchorData)` (in a CustomPanel),
`.background(color)`, `.border(color, thickness)`, `.tooltip(text)` / `.tooltip { builder }`, `.onClick { }`,
`.onClickRelease`, `.onClickOutside`, `.onHover`, `.onHoverEnter`, `.onHoverExit`, `.onMouseHeld`,
`.onKeyDown` / `.onKeyUp` / `.onKeyHeld`, `.hitBoxPadding(...)`, `.onAdvance { amount -> }`,
`.onRender { alpha -> }`, `.onRenderBelow { alpha -> }`, `.onLayout { }` (after every layout pass),
`.onFirstLayout { }` (once), `.onPositionChanged { }`, `.plugin { }` (raw `StarUIPanelPlugin` access).

Modifiers work on every component, vanilla ones included, because they attach to the native object.
On a vanilla `ButtonAPI`, use `Button(onClick = ...)` or `button.onClick { }` from `core`. A button's previous
listener is kept.

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
    Checkbox("Details", bind = showDetails)
    if (showDetails.value) Text("...")
}
```

1. **Plain `if`, `for` and `when` just work.** Each container builder (Column, Row, Box, CustomPanel, ScrollPanel)
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
   label's font or colour, a text field's font or hint) make a new object when they change. `ShipDisplay` is always
   recreated.
7. `state.peek()` reads without subscribing. Reads outside builders (handlers, `onAdvance`, render callbacks) never
   subscribe.
8. Existing bindings still work: `TextField(bind = ...)`, `Checkbox(bind = ...)`, `Slider(bind = ...)`, `ButtonGroup`
   (radio groups) and `state.onChange { }` inside a builder (cleaned up automatically when the builder re-runs or its
   UI is removed). A bound control writes its state the moment its native value changes, whoever changed it: the
   player, or any code calling `setText` / `setChecked` / `setProgress` on the vanilla object.
9. UI that is removed or hidden never rebuilds. It catches up once when it is shown again.
10. Layout updates by itself: adding or removing children, changing a `Text`'s string, or a rebuild all re-lay out
    the tree on the next frame. Call `component.requestLayout()` only after changing something StarUI cannot see,
    for example a native component's size set by hand.
11. `plugin.dispose()` (or `StarUIPanelPlugin.getAttached(component)?.dispose()`) tears down StarUI's handlers,
    bindings and scopes under a component you are throwing away. Children removed by a vanilla `clearChildren()`
    are disposed automatically. `removeComponent` alone does not dispose, so moving a component (remove, then add
    elsewhere) keeps it working.

## Where StarUI differs from Jetpack Compose

- Calls create real, persistent Starsector objects and return them (`val b = Button(...)`), so you can keep and use
  the reference. The objects survive rebuilds when they match (rule 6).
- No `remember`, no `LaunchedEffect`: keep state in normal fields or objects (`UIState` anywhere), and use
  `Modifier.onAdvance` for per-frame work.
- Sizes are typed (`Size`, `LayoutSize`, `PanelSize`) instead of `Modifier.size` only; `margin` exists.
- Absolute positioning is anchors in a `CustomPanel`, not `offset`.
- Coordinates in raw Starsector APIs are bottom-left based; StarUI layouts and `dumpLayout()` use top-left, y down.

## Seeing what you built (no screenshots needed)

- `component.dumpLayout()` returns the tree below a component as text: kind, text, size, position relative to it,
  sizing, padding/margin, attached handlers and scope state. Log it, or assert on it in tests.
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
  compose/components/   Containers.kt (Column, Row, Box, CustomPanel, ScrollPanel, key, reuse helpers),
                        Content.kt (Text, Image, ShipDisplay), Controls.kt (Button, TextField, checkboxes), Spacers.kt
  compose/layout/       LayoutEngine.kt, StackLayoutEngine.kt (Column/Row), BoxLayoutEngine.kt
  compose/modifier/     Modifier.kt, LayoutModifiers.kt, EventModifiers.kt, VisualModifiers.kt
  compose/runtime/      StarUIPanelPlugin.kt (per-component state, handlers, layout, scopes),
                        Scope.kt (reactive scopes, rebuild timing, reuse cursor), UIState.kt, StarUIContext.kt
                        (build-time stacks: plugin, theme, scope, key), StarUIManager.kt (injection), LayoutDump.kt
  core/                 vanilla helpers, BoxedUIImage / BoxedUIShipPreview (no API for those), Anchor, Font,
                        ReflectionUtils
  internal/             ModPlugin, PatchLib hooks (UIComponentLifecyclePatch.kt)
native-smoke/           in-game test suite (test-only, never shipped)
docs/                   design notes: reactive-scopes-plan.md
```

## How it works, in one paragraph per piece

- **Zero wrapper.** Leaves (labels, buttons) get a `StarUIPanelPlugin` stored in a weak map. CustomPanels use it
  as their plugin, wrapping any foreign plugin as `delegate`. PatchLib hooks on the vanilla base component call the
  plugin's render, advance and input methods.
- **Layout.** Containers have a `LayoutEngine`. The top-level StarUI node runs measure and layout lazily in
  `renderBelow` when `requestLayout()` has marked it. Stack children are anchored on their previous sibling, so
  removing one re-pins the rest (`pinChildrenToPanel`).
- **Scopes.** `attachScope` keeps each container's builder. `UIState` reads subscribe the current scope, and writes
  mark it dirty. A dirty scope is rebuilt from an ancestor's `renderBelow` or `advance` (`Scope.flush`), never
  during input or from scripts. A rebuild hands the old children to a `ReuseCursor`, and each component function
  asks `childSlot(kind, props)` for its match.
- **Registrations made during a build on a plugin that outlives it** (the container's own plugin, an older plugin)
  are undone before the next build (`Scope.addCleanup`). Handlers on reused children are cleared by
  `resetForReuse()`.

## Rules

- **Test in the real game.** `native-smoke/run-test-suite.ps1` compiles `src/` and `native-smoke/kotlin/` into a
  test-only jar and runs every group in an isolated game process (about 45 s; `-Filter name,name` runs only some).
  It ends with `SUITE SUMMARY checks=N failures=0`. Look at the PNGs too. It cannot run under Fast Rendering.
  Write the test first and see it fail when practical. Tests are Kotlin in the default package, registered in
  `native-smoke/kotlin/TestFramework.kt`.
- **No obfuscated names** in code, PatchLib matchers, casts or imports. Find engine classes and members by API
  interface, readable name or shape. Prefer the API whenever it has the method (0.98.5a added many UI ones:
  `getParent`, `getChildrenCopy2`, `clearChildren`, `relativeTo2`, most of `ScrollPanelAPI`, `SliderAPI`).
  Look engine code up in a decompile of the current game's `starfarer_obf.jar` (the patchlib skill bundles
  Vineflower); a decompile from an older game version will mislead you.
- **Never use `java.lang.reflect` directly**: the game's script class loader refuses it at runtime
  (`SecurityException`). Go through `ReflectionUtils` (`invoke`, `getMethodsMatching`, `ReflectedMethod`).
- A reflective `invoke("name")` throws "Ambiguous method call" when the API declares the same method with a narrower
  return type (the class then has a bridge method too). Call the API method instead.
- **Keep public signatures stable**: Combat AI Tweaks, Scy Nation and Refit Filters ship compiled against StarUI.
  Add and deprecate; don't remove or change.
- Source files are CRLF. Keep each file's existing line endings.
- `jars/StarUIFramework.jar` is committed and built from IntelliJ; the test runner never touches it.
