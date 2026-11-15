# Reactive UI

Contents: [The model](#the-model) · [The rules](#the-rules) · [Where state lives](#where-state-lives) ·
[Lifetime and disposal](#lifetime-and-disposal) · [Where StarUI differs from Compose](#where-starui-differs-from-compose)

## The model

State lives in `UIState<T>`. **A builder that reads it follows it:**

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

Builders: a container's trailing block (Column, Row, Box, CustomPanel, Section, lazy row, table `custom` cell), a
`StarUIPlugin` screen builder, an `inject` builder. Each records the states it reads and runs again on the next frame
when one changes; children are matched to the new calls.

## The rules

1. **Plain `if` / `for` / `when` work.** Nested containers are separate scopes: put a read in the smallest container
   that shows it.
2. **Handlers change state, never build UI.** Adding a component from a handler throws. Handler:
   `showDetails.value = true`; builder: `if (showDetails.value) Text(...)`.
3. **Updates appear next frame**, never mid-input. Several writes in a frame: one rebuild.
4. **A builder never writes a state it reads** (a loop, stopped after 8 passes; the log names the builder). Derive
   values as plain `val`s.
5. **`key(id) { }` on list items** that can move. Unkeyed calls match by position (a row's field could keep another
   row's focus).
6. **Rebuilds reuse native objects**: text updates in place; focus, caret, scroll, hover survive. Creation-time
   arguments (button font, colours, cut; label font or colour; field font or placeholder) make a new object when
   changed. `ShipDisplay` is always new.
7. **Reads outside builders never subscribe** (handlers, `onAdvance`, render callbacks). `state.peek()` reads without
   subscribing. `var count by mutableStateOf(0)` delegates (a delegated read in a builder subscribes).
8. **Bindings** (`TextField`, `Checkbox`, `Slider`, button groups) write their state when the native value changes,
   by the player or by `setText` / `setChecked` / `setProgress`. `state.onChange { }` in a builder is cleaned up with
   it (throws outside one). Outside UI: `observeForever { }` / `removeObserver` (held strongly).
9. **Hidden or removed UI never rebuilds**; it catches up once when shown.
10. **Layout updates itself** on child changes, text changes and rebuilds. `component.requestLayout()` only after a
    change StarUI cannot see (a native size set by hand).

## Where state lives

- Outside the UI, created once: a plugin field or an object (`object MyToolState { val tab = UIState(0) }`). A state
  made inside a builder is remade (and forgets) every run; make it in an outer builder that does not read it.
- One state per thing the player selects or changes (tab, selected id, open sub view, each field): setting them from
  a test or debug command then shows any state for a capture.
- Scroll position needs no state (kept over rebuilds); a lazy list's far-off rows lose theirs.
- Game data (fleet, credits) is not a `UIState`: write a state you own from `onAdvance` when it differs
  (`if (credits != shown.value) shown.value = credits`), or rebuild on the events that change it.

## Hot reload

In a `starsector-hotreload` game, every live scope rebuilds after each reload (outermost first), keeping objects,
state, scroll and focus. Containers that read no state stay non-reactive. Normal games run none of this.

## Lifetime and disposal

- Thrown-away UI needs nothing: StarUI's state lives on the vanilla objects and is collected with them.
- `plugin.dispose()` (or `StarUIPanelPlugin.getAttached(c)?.dispose()`) turns StarUI off under a component you keep.
  Children removed by a rebuild or `clearChildren()` are disposed automatically.
- A `Layer` / `Dialog` in a builder closes when a rebuild leaves it out or the scope is disposed. To outlive its
  opener, use `showLayer` / `showDialog` from a handler.
- `removeComponent` does not dispose (moving keeps it working), except: a label must be added elsewhere before its
  old panel is thrown away, or it loses its modifiers.

## Where StarUI differs from Compose

- Calls create real, persistent objects and return them (`val b = Button(...)`); they survive matching rebuilds.
- No `remember` / `LaunchedEffect`: state in fields or objects; per-frame work in `Modifier.onAdvance`.
- Typed `size` argument, no sizing modifiers; `margin` exists; modifier order does not matter.
- Absolute placement: anchors in `CustomPanel` / `AnchorPanel`, not `offset`.
- Clipping and scrolling: `overflow` (a scrolling container is a different native object, chosen at creation).
- Raw API coordinates (render callbacks, `PositionAPI`) are bottom-left, y up; StarUI layouts and `dumpLayout()` are
  top-left, y down.
