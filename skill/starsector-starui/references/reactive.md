# Reactive UI

Contents: [The model](#the-model) · [The rules](#the-rules) · [Where state lives](#where-state-lives) ·
[Lifetime and disposal](#lifetime-and-disposal) · [Where StarUI differs from Compose](#where-starui-differs-from-compose)

## The model

State lives in `UIState<T>`. **Read it inside a builder and the builder follows it:**

```kotlin
val ships = UIState(listOf<Ship>())
val showDetails = UIState(false)

Column {
    Text("Fleet (${ships.value.size})")
    for (ship in ships.value) key(ship.id) {
        Row { Text(ship.name); Button("Remove") { ships.value = ships.value - ship } }
    }
    if (ships.value.isEmpty()) Text("No ships", color = VanillaColors.gray)
    Checkbox("Details", showDetails)
    if (showDetails.value) Text("...")
}
```

A builder is the trailing block of a container (Column, Row, Box, CustomPanel, Section, a lazy row, a table's
`custom` cell), a `StarUIPlugin` screen builder, or an `inject` builder. Each records the `UIState`s it reads; when
one changes, that builder runs again on the next frame and its children are matched to the new calls.

## The rules

1. **Plain `if`, `for` and `when` just work.** Nested containers are separate scopes: a state that only an inner Row
   reads re-runs only that Row. Put a read in the smallest container that shows it.
2. **Event handlers change state; they never build UI.** Adding a component to a reactive container from a click
   handler throws an error that says so. Write `showDetails.value = true` in the handler and
   `if (showDetails.value) Text(...)` in the builder.
3. **Updates appear on the next frame**, never in the middle of input handling. Several writes in one frame cause one
   rebuild.
4. **A builder must not write a state it reads.** That is a loop: it is stopped after 8 passes, and the error log
   names the builder. Derive values in the builder as plain `val`s instead of storing them back.
5. **Wrap list items in `key(id) { ... }`** when items can be inserted, removed or reordered. Without keys calls are
   matched by position, so a row's text field could keep the focus of the row that used to be there.
6. **Re-running a builder reuses the native objects.** A `Text` call updates the existing label's string; focus,
   caret, scroll position and hover survive. Arguments fixed at creation (a button's font, colours or cut, a label's
   font or colour, a text field's font or placeholder) make a new object when they change. `ShipDisplay` is always
   made again.
7. **Reads outside builders never subscribe**: handlers, `onAdvance`, render callbacks. `state.peek()` reads without
   subscribing inside a builder. A state is also a delegate: `var count by mutableStateOf(0)` reads and writes
   `value` (a delegated local read in a builder subscribes like `.value`).
8. **Bindings.** `TextField(bind = ...)`, `Checkbox(bind = ...)`, `Slider(bind = ...)` and the button groups write their
   state the moment the native value changes, whoever changed it: the player, or any code calling `setText`,
   `setChecked` or `setProgress` on the vanilla object. `state.onChange { }` inside a builder is cleaned up when the
   builder re-runs or its UI is removed (outside a builder it throws). For a listener outside any UI,
   `state.observeForever { }` and `removeObserver`; the state holds it strongly.
9. **Hidden or removed UI never rebuilds.** It catches up once when it is shown again.
10. **Layout updates by itself.** Adding or removing children, changing a `Text`'s string, or a rebuild re-lay out the
    tree on the next frame. Call `component.requestLayout()` only after changing something StarUI cannot see, such as
    a native component's size set by hand.

## Where state lives

- Keep a panel's state outside the UI: a field on your plugin or an object (`object MyToolState { val tab =
  UIState(0) }`), created once. A `UIState` made inside a builder is made again every time that builder re-runs, so
  it forgets its value. Create it in an outer builder that does not read it, or outside the UI.
- One state per thing the player selects or changes: the selected tab, the selected entry's id, an open sub view,
  each editable field. The design skill's "every view reachable from code" rule then holds for free: setting the
  states from a test or a debug command shows any state for a capture.
- The scroll position needs no state: a scrolling container keeps it over rebuilds. A lazy list's rows far off screen
  do lose theirs.
- Game data (the player's fleet, credits) is not a `UIState`. Read it in the builder and rebuild when it changes:
  write a state you own from `onAdvance` when the value you show differs (`if (credits != shown.value) shown.value =
  credits`), or rebuild on the events that change it.

## Hot reload

In a game started by the `starsector-hotreload` skill, StarUI rebuilds every live scope after each reload, outermost
first, so UI on screen runs the new code at once. It keeps native objects, state, scroll positions and focus like any
rebuild. Containers that read no state stay non-reactive. In a normal game none of this runs.

## Lifetime and disposal

- UI you throw away needs nothing: StarUI's state lives on the vanilla objects, so a panel nobody holds any more (a
  screen vanilla rebuilt, a closed dialog) is collected with everything StarUI attached to it.
- `plugin.dispose()` (or `StarUIPanelPlugin.getAttached(component)?.dispose()`) switches StarUI off under a component
  right away (handlers, bindings, `UIState` listeners and scopes), for UI you keep but no longer want reacting.
  Children removed by a rebuild or a vanilla `clearChildren()` are disposed automatically.
- A `Layer` or `Dialog` called in a builder belongs to that builder's scope: it closes when a rebuild leaves the
  call out or the scope is disposed (its screen closed). One that must outlive the panel that opened it is a
  `showLayer` / `showDialog` from a handler, closed with `close()` or its buttons.
- `removeComponent` alone does not dispose, so moving a component (remove, then add elsewhere) keeps it working. One
  exception: a label taken out of a panel must be added elsewhere before that panel is thrown away, or it comes back
  without its modifiers.

## Where StarUI differs from Compose

- Calls create real, persistent Starsector objects and return them (`val b = Button(...)`), so you can keep and use
  the reference. They survive rebuilds when they match.
- No `remember`, no `LaunchedEffect`: keep state in normal fields or objects, and use `Modifier.onAdvance` for
  per-frame work.
- Sizes are a typed `size` argument instead of sizing modifiers; `margin` exists; modifiers are not
  order-sensitive.
- Absolute positioning is anchors in a `CustomPanel` or `AnchorPanel`, not `offset`.
- Clipping and scrolling are the container's `overflow` argument, not `clipToBounds` / `verticalScroll`: a scrolling
  container is a different native object, chosen when it is made.
- Coordinates in raw Starsector APIs (render callbacks, `PositionAPI`) are bottom-left based, y up; StarUI layouts and
  `dumpLayout()` use top-left, y down.
