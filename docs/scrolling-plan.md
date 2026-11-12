# Overflow plan: clipping and scrolling as one per-axis setting, wheel on both axes, long lists

Status: steps 1-5 done (2026-11-12); steps 6-7 to do. Found on the way: a per-pass measure
cache was needed (nesting multiplied measures) and VanillaElementEngine re-laid out every frame; both fixed in 3079e0b.
Clip on both axes uses vanilla's own `setClipping` (a nesting scissor); one-axis clip is a StarUI GL scissor. Work in the order of the steps below; each step ends with the
in-game suite passing (`native-smoke/run-test-suite.ps1`, under `smoke-slot.sh run`) and the PNGs looked at.

## The design

### One setting: `overflow`, per axis

What a container does with children that do not fit is one setting, like CSS's `overflow-x` / `overflow-y`, not a
clip modifier plus a separate scroll flag. Every container takes it: `Row`, `Column`, `Box`, `CustomPanel`.

```kotlin
enum class AxisOverflow { Visible, Clip, Scroll }

class Overflow(val horizontal: AxisOverflow, val vertical: AxisOverflow) {
    companion object {
        val Visible = Overflow(AxisOverflow.Visible, AxisOverflow.Visible)    // the default
        val Clip = Overflow(AxisOverflow.Clip, AxisOverflow.Clip)
        val ScrollVertical = Overflow(AxisOverflow.Clip, AxisOverflow.Scroll)  // a list: too-wide content is cut
        val ScrollHorizontal = Overflow(AxisOverflow.Scroll, AxisOverflow.Clip)
        val Scroll = Overflow(AxisOverflow.Scroll, AxisOverflow.Scroll)        // the 2D viewport
    }
}

Column(Size.fillMaxSize(), overflow = Overflow.ScrollVertical) { ... }
Row(Size.fillMaxWidth(height = 120f), overflow = Overflow.ScrollHorizontal) { ... }
Box(Size.exact(400f, 300f), overflow = Overflow.Scroll) { Image(map) }
Box(Size.exact(200f, 60f), overflow = Overflow.Clip) { ... }
Box(Size.exact(200f, 60f), overflow = Overflow(horizontal = AxisOverflow.Clip, vertical = AxisOverflow.Visible)) { ... }
```

| Axes | Native object | Behaviour |
|---|---|---|
| both `Visible` (default) | plain panel | children spill out, as today and as CSS's default |
| `Visible` / `Clip` only | plain panel with a StarUI scissor | each `Clip` axis is cut at the edge (one axis or both); input on the hidden part is ignored |
| any `Scroll` | vanilla scroll panel | `Scroll` axes scroll, the other axis is clipped |

Rules, each documented and each a clear exception where it is an error:

- **A scroll axis clips both axes.** A vanilla scroll panel scissors to its own rectangle on both axes, so the axis
  next to a `Scroll` axis is `Clip` or `Scroll`, never `Visible`. `Overflow(Visible, Scroll)` throws
  ("a scroll axis clips both axes: use Clip or Scroll for the other"). CSS forbids the same pair and silently turns
  the `visible` into `auto`; StarUI says so instead, as it does for other meaningless combinations.
- **On a `Clip` axis** the content is bounded by the container, as today: something wider is cut.
- **On a `Scroll` axis** the room offered to the content is the viewport's size (fill, wrapping text and alignment
  have something to work with) and the content's size is max(viewport, what it measures). Its bar appears only when
  the content is bigger, like CSS's `auto`.
- **Fill on a `Scroll` axis means "what is left of the viewport"**: a fill spacer can push a footer to the bottom of a
  short list; it is 0 when the content already overflows. This replaces the error "Cannot use Dimension.FillMax
  inside an unconstrained infinite axis".
- **A parameter, not a modifier**: the scroll cases need a different native object, chosen before the panel exists,
  and modifiers run after. All five cases stay in one place.
- **Wrapping containers never overflow** on a wrapping axis (they grow): `overflow` only matters on an exact or fill
  axis. Say so in the docs; no error.

### `ScrollPanel` is removed

`Box(overflow = Overflow.Scroll)` is the 2D viewport and `Column(overflow = Overflow.ScrollVertical)` is the list. No
deprecation (the user is the only user). Only the samples use it today: `ShowcaseUIPlugin.kt` (page body, task list,
the two "ScrollPanel options" cards) and `ExampleUIPlugin.kt:213`. Refit Filters, Combat AI Tweaks and Scy Nation have
none. Its options (shadows, colours, scrollbars, left scrollbar, mouse wheel) move to `StarUITheme.scrollPanel`
(rename to `StarUITheme.scroll`), and anything else goes through the accessor below.

**Return types do not change.** A scrolling Column is a vanilla scroll panel holding the StarUI Column; the call
returns the Column's `CustomPanelAPI`. A `core` accessor `val CustomPanelAPI.scrollPanel: ScrollPanelAPI?` gives the
scroll API (`scrollToY`, `ensureVisible`, offsets) for a scrolling container, null otherwise.

### The wheel: innermost panel first, both axes

A PatchLib `@Before` on vanilla's `processScrollInputOnly` (game-wide, every scroll panel):

- **Innermost first.** A wheel event goes to the deepest scroll panel under the cursor that can still move in that
  direction, scrolled through the API (`setYOffset` / `setXOffset`), and the event is consumed. Vanilla does the
  opposite: a panel handles the wheel before passing input to its children, so the outer panel takes it whenever it
  can scroll and a nested one never scrolls.
- **Chaining.** "Can still move" means not already at its end in that direction: at the end of an inner list the
  wheel goes on to the next panel out, as in browsers.
- **Axis.** Plain wheel scrolls y when that panel's content overflows vertically, otherwise x when it overflows
  horizontally. Shift+wheel scrolls x when it overflows on both axes.
- **Vanilla unchanged where nothing is nested and nothing overflows sideways**: the patch leaves the event to vanilla
  (same step, Shift = 5x faster vertical).
- Panels with the wheel off (`setUseMWheel(false)`) are skipped, as vanilla does.

Chosen over a StarUI-only wheel (turning vanilla's off and handling it in StarUI's `@After` input hook) because it keeps
vanilla's wheel feel and fixes nesting for every panel, StarUI's or not.

### Not part of this

- Text overflow (`maxLines`, ellipsis): a `Text` setting like Compose's `TextOverflow`, later.

### Performance is part of it

Long, text-heavy lists must stay smooth (the user: LunaLib's UI has this problem, StarUI must not). Scrolling
containers cull off-screen children (step 1), a text-heavy 1000- and 3000-row benchmark with budgets is part of the
suite (step 5), and lazy lists (`LazyColumn` / `LazyRow`, step 6) follow if the numbers call for them.

## What vanilla does (0.98.5a decompile, `C:\Users\starf\starsector-reference-0.98.5a`)

The scroll panel is `obf/com/fs/starfarer/ui/g.java` (`class g extends z implements ScrollPanelAPI`). Line numbers are
from that decompile; names are obfuscated unless they are API or readable.

- **Already 2D.** Horizontal widget and rail rectangles (`class.do()`, `oÖO000()`, ~894-928), a 4 px bar along the
  bottom drawn whenever content width > panel width, in the same render method as the vertical bar (`Öo0000`,
  ~716-846). Clicking the horizontal rail jumps, dragging its widget scrolls (`processScrollInputOnly`, ~216-430).
  Right-drag pans the content on both axes (button field `ÔØÔo00 = g.o.Ò00000` = RMB).
- **Wheel is vertical only**, in `processScrollInputOnly(events, boolean)` (~123-160): step -100, capped at 33% of the
  panel height, times 5 with Shift, clamped to the content height; then `oØÔo00.setOffset(x, y)` and a position
  recompute; the event is consumed. Only when `setUseMWheel` is on and the panel overflows vertically (or is already
  scrolled).
- **Outer panel first.** `processInputImpl` (444-460) calls `processScrollInputOnly(events, false)` before
  `super.processInputImpl` (the children). Hence the innermost-first routing above.
- **Callers of `processScrollInputOnly`**: the panel's own `processInputImpl` (line 447), the trade screen's cargo grids
  (`campaign/ui/trade/F.java:254-255`, passing `true`) and a core-UI list (`coreui/publicsuper.java:146`). Hooking
  that one method covers every wheel path.
- **API setters keep vanilla consistent**: `setXOffset` / `setYOffset` write the internal offset fields and
  `forceOffset` the content (433-441); `setContentSize(w, h)` sizes the content container (119); `clampOffset` (942)
  zeroes an axis that does not overflow. `getContentContainer()` (100) is the content panel (obfuscated type, a
  UIPanelAPI, not a CustomPanelAPI). The x offset is <= 0 (content moves left).
- **Scissor**: vanilla's helper (`com.fs.starfarer.util.K.o00000(x, y, w, h)`, used by ship displays and scroll panels)
  sets a rectangle; check whether it intersects with an enclosing one before relying on it for nested `Clip`. StarUI's
  own scissor must intersect.
- **Gotcha found earlier**: vanilla `bringComponentToTop` also raises the panel in its parent, all the way up
  (`ui/z.java` `bringToTop`). Never use it to reorder inside StarUI; remove and re-add instead.

## What StarUI does now (to change)

- `compose/components/Containers.kt`, `ScrollPanel(...)`: makes a vanilla scroll panel through a temporary tooltip,
  attaches a `StarUIPanelPlugin` to its content container with `myNodeSize = NodeSize(FillMax(1f), WrapContent)` and a
  vertical `StackLayoutEngine`; another plugin on the outer panel. FillMax width is why content can never be wider
  than the panel.
- `compose/layout/StackLayoutEngine.kt` throws for FillMax on an unbounded axis (~line 120).
- StarUI's input hook is an `@After` on `processInput` (`internal/UIComponentLifecyclePatch.kt` ~104-121), so vanilla
  consumes wheel events before StarUI sees them: the wheel needs its own `@Before`.

## Steps

### 1. `Overflow` and the scrolling containers

- `compose/Overflow.kt`: `AxisOverflow`, `Overflow` (with the shorthands, the `Visible`-next-to-`Scroll` check in the
  constructor, `toString`).
- `Row`, `Column`, `Box`, `CustomPanel` take `overflow: Overflow = Overflow.Visible`. With any `Scroll` axis: create the
  vanilla scroll panel (factor the creation code out of today's `ScrollPanel`), put the StarUI container panel inside
  its content container, size the outer panel by the call's `size`, return the inner panel. Reused across rebuilds
  (scroll position kept), like `ScrollPanel` now (`slot.reused()`, `applyReuseBounds`); a different `overflow` makes a
  new one.
- Layout: a viewport step between the outer scroll panel and the inner container. Per `Scroll` axis: measure the inner
  container with room = viewport size, content size = max(viewport, measured), `setContentSize`, lay the inner
  container out at that size, then `clampOffset()` so a shrunk list does not stay scrolled past its end. Per `Clip`
  axis: bounded by the viewport. Change `StackLayoutEngine` (and `BoxLayoutEngine`) so a fill child on a scrolled axis
  gets the viewport's leftover instead of the unbounded-axis error.
- **Render culling (required, not an optimisation to do later).** Vanilla's content container only renders its
  *direct* children that intersect the viewport (inner class `oo` of `ui/g.java`, its `renderImpl`: children whose
  position does not intersect the scroll panel are skipped with `notifyDidNotRender`). Today's `ScrollPanel` puts the
  items directly in the content container, so off-screen items are not drawn. In the new design the content
  container holds one child, the StarUI Column, which always intersects: nothing would be culled and a scrolling
  Column would be slower than today's ScrollPanel. So a StarUI container with any non-`Visible` axis skips rendering
  (renderBelow, render) of children whose rectangle does not intersect its visible rectangle, and so do containers
  nested in it (they know the clip/viewport rectangle from the stack in step 2). Vanilla's culling skips rendering
  only (advance and input still run for every child); check how far StarUI can skip per-frame work for off-screen
  children without breaking hover-exit and `onAdvance` semantics (likely: render culled, advance kept).
- `core`: `val CustomPanelAPI.scrollPanel: ScrollPanelAPI?`; `dumpLayout` shows `overflow=ScrollVertical` etc.
- Remove `ScrollPanel`; theme `scrollPanel` -> `scroll` (drop `horizontalAlignment`). Migrate the samples.
- Tests (extend `AnchorAndScrollTests.kt` or a new `OverflowTests.kt`):
  - `Column(overflow = ScrollVertical)` with tall content: content height = sum, width = viewport, vertical bar only,
    scroll position kept across a rebuild, an over-wide child is cut (content width stays the viewport's);
  - `Row(overflow = ScrollHorizontal)` of cards wider than the viewport: content width = sum, x offset set and clamped;
  - a short `Column(overflow = ScrollVertical)` with a fill spacer footer: the footer reaches the viewport's bottom;
    with a long list the spacer is 0;
  - `Box(overflow = Scroll)` with a 1500 x 1200 child in 400 x 300: both bars, both offsets clamp; a small child with
    `Modifier.align(Alignment.Center)` sits centred in the viewport;
  - nested: a scrolling Row inside a scrolling Column;
  - `Overflow(Visible, Scroll)` throws with the message;
  - `Overflow.Visible` behaves exactly as now (the existing suite covers it).

### 2. `Clip` without scrolling

- A StarUI scissor around a plain panel's children: on in the panel plugin's `renderBelow` before the children, off in
  `render` after them (check the order of the plugin calls against the children's render). Per axis: an unclipped axis
  uses the screen's extent. **Nested clips intersect**: keep a stack of clip rectangles (StarUI-wide), push the
  intersection, restore the previous rectangle on pop; if vanilla's own scissor (scroll panels, ship displays) is
  active and its helper does not intersect, handle that (read the helper first).
- Input: mouse events outside the visible rectangle do not reach the clipped children (filter them in the panel
  plugin's input path before children process input, or mark them so StarUI handlers ignore them; vanilla buttons
  need the event withheld, check how).
- Tests: a 200 x 60 `Box(overflow = Clip)` with a 300 x 120 child: pixels outside the box are background; a click on
  the hidden part of a button inside does nothing; a one-axis clip cuts only that axis; a clip inside a clip shows
  only the intersection.

### 3. Wheel patch

Read the patchlib skill's `SKILL.md`, `references/guidelines.md`, `references/library-notes.md` and
`references/obfuscated-code.md` again before writing it; state the skill version (1.0.0) against the install (1.1.0).

- `internal/ScrollWheelPatch.kt`: `@Patch(target = ClassMatch(subtype = ScrollPanelAPI::class))`,
  `@Before(target = MethodMatch(methodName = "processScrollInputOnly", parameterCount = 2, ...))`. A build-labelled
  comment with the obfuscated class (`com.fs.starfarer.ui.g` on 0.98.5a-RC4). `processScrollInputOnly` is a readable
  non-API name: an inferred target, the first to re-check after a game update.
- Handler, per unconsumed mouse scroll event inside `self` (coordinate check against `self.position`, never
  `containsEvent` on an obfuscated type; iterate the obfuscated list argument as `List<InputEventAPI>`):
  1. find the scroll panels under the cursor from `self` down (children containing the point that are
     `ScrollPanelAPI`, through their content containers), deepest first;
  2. for each, the axis this event would scroll it on (plain: y if it overflows vertically, else x if horizontally;
     Shift: x if it overflows on both) and whether it can still move that way (offset not at its end);
  3. the first that can: if it is `self` and the axis is y and nothing nested could take it, leave the event to
     vanilla (unchanged behaviour); otherwise scroll it through `setYOffset` / `setXOffset` with vanilla's step
     (-100, capped at 33% of the panel's size on that axis, times 5 with Shift on y), clamped, and consume;
  4. none can: leave the event alone.
  Skip panels with the wheel off (no API getter for `setUseMWheel`: find the readable field by shape, or note that
  StarUI only turns it off through the theme and track it there).
- Keep it cheap (it runs every frame for every scroll panel): bail out at once when there is no scroll event.
- Tests: real scroll events (check `StarUITestSupport` for a wheel event; add one if missing): plain wheel on a
  horizontal-only Row moves x; Shift+wheel on a `Scroll` Box moves x, plain wheel moves y; a list nested in a
  scrolling page scrolls the inner list first and the page once the list is at its end; a vertical-only vanilla-style
  panel with Shift keeps the 5x vertical step. Check the `Installed a hook patch site` log line.

### 4. Showcase

- The page body becomes `Column(Size.fillMaxSize(), overflow = Overflow.ScrollVertical)`; the task list a scrolling
  Column inside it (exercises innermost-first).
- Replace the two "ScrollPanel options" cards with an "Overflow" card: a clipped box next to a visible one, a
  horizontal card strip (`ScrollHorizontal`), a 2D map-like grid (`Box(overflow = Scroll)`), a short scrolling
  Column with a fill footer, and the theme's scroll options (left scrollbar, no shadows, widget colour) on one of them.
- Check in the test game (`scratchpad/live-showcase`, staged jar in `scratchpad/stage-pl`), then offer a play-mode boot
  (`smoke.sh play ... --jar` with the showcase-registering build from `scratchpad/play-showcase`).

### 5. Performance: long, text-heavy lists

The user's requirement: long lists full of text must stay smooth. LunaLib's UI has this problem (reported by the user)
and StarUI must not. Measure, don't assume; text is the case to stress, not empty rows.

- **The test list**: 1000 rows (and a 3000-row run), each row a `Row` with a `Section`-like background, a title
  `Text` (Orbitron), two or three plain `Text` labels, one wrapped multi-line paragraph (`Size.fillMaxWidth()`, 3-5
  lines), one `highlightedText` label, and a small `Button`. Plus a variant with a `StatGrid` per row. That is
  thousands of vanilla labels, most of them off-screen.
- **Measure, in the suite's perf style** (`InteropAndPerfTests.kt` `perf-*` groups: warm up, median of repeated
  frames, report ms; and the JFR profile the suite records), for each of:
  1. today's `ScrollPanel` (items direct children of the content container: vanilla culls them);
  2. the new `Column(overflow = ScrollVertical)` without StarUI culling (expected to be the worst);
  3. the same with StarUI culling;
  4. (later) `LazyColumn`.
- **Numbers to record per case**: first build (ms), a full relayout (ms), a rebuild of one row's state (ms; must not
  relayout or re-measure every other row's text: check that text measurement is cached per label and only redone
  when its text or width changes), steady per-frame cost while idle (ms/frame), per-frame cost while scrolling (ms),
  and changing one label's text in a visible row (ms).
- **Budgets** (fail the group, like the existing perf checks): idle per-frame cost of the 1000-row list under
  1 ms over an empty screen once culling is in; a one-row state change under 2 ms; scrolling frames under 2 ms.
  Adjust the numbers once the first measurements are in, and write the chosen budgets into the test.
- Look at the JFR top frames for text: label measurement (`measureTextSize`, `computeTextHeight`, vanilla's
  `autoSize`), StarUI's layout passes and per-frame plugin dispatch. Anything that scales with the total row count
  rather than the visible rows is a bug to fix in this step.
- Record the results (table of the cases above) in this plan, then decide step 6.

#### Step 5 results (2026-11-12, `perf-long-lists` in `LongListPerfTests.kt`)

Rows: Orbitron title, three labels, a 3-5 line paragraph, a highlighted label, a per-row UIState label, a button.
700 x 700 scrolling Column. Milliseconds; "idle" and "scrolling" are per frame over an empty screen.

| Case | first build | relayout | one row's state | label setText | idle | scrolling |
|---|---|---|---|---|---|---|
| vanilla: only the ~6 visible rows, no list (the floor) | | | | | 3.1 | |
| vanilla's own scroll panel, 1000 rows as paragraphs | 2298 | | | | 535 | 552 |
| first measurement: 1000 rows, render culling only | 1219 | 539 | 557 | 556 | 17.0 | 19.4 |
| 1000 rows, no culling | 2900 | | | | 860 | 875 |
| 1000 rows, culled, advance kept (now) | 515 | 6.0 | 6.0 | 5.5 | 7.4 | 9.7 |
| 1000 rows, culled, off-screen advance paused | 565 | 7.5 | 7.9 | 7.2 | 4.7 | 9.7 |
| 3000 rows, culled, advance kept | 1546 | 33 | 34 | 32 | 17.5 | 33 |
| 300 rows + a StatGrid each, culled | 223 | 4.6 | 4.0 | 4.1 | 5.3 | 8.6 |

What it took (each found in the JFR profile):
- render culling; plus off-screen StarUI panels skip input (`ClipStack.cullPerFrameWork`, as vanilla's scroll panel
  does). Advance keeps running for plain containers (the user's call: onAdvance and vanilla animations of off-screen
  rows must not pause there); a container can pause it (`pausesOffscreenAdvance`), which lazy lists will do. With
  advance kept, 1000 rows idle at ~8 ms (floor ~3.7), 3000 at ~17 ms; paused, 1000 rows idle at ~4.5 ms;
- Column/Row children placed relative to the container, not chained on the previous sibling (chaining made vanilla
  recompute every later sibling on each move: quadratic);
- `setSize` skips an unchanged size; a placement is not redone when unchanged;
- each `Text` keeps its own measurement and wrapped heights (the shared 1024-entry LRU missed every time with 7000
  strings; vanilla's `autoSizeToWidth` is the costliest call in a layout);
- measure results reused across passes for nodes nothing changed in, and their layout skipped (incremental layout);
- a scrolling container with an exact/fill size is a layout boundary;
- the advance hook skips the plugin lookup for labels in StarUI containers none of whose leaves has onAdvance;
- debug log strings only built when debugging; a highlighted `Text` with `%` no longer crashes.

Still growing with the row count: scrolling (vanilla recomputes every position in the content on each offset change)
and a change anywhere (StarUI walks every sibling, each now cached). At 3000 rows that is ~20-40 ms. Hence step 6.
Budgets in the test are relative to the floor (idle within 6 ms of it at 1000 rows, 16 ms at 3000, 3 ms with
off-screen advance paused; scrolling within 12 ms; one change under 15 ms; at least 20x cheaper than vanilla's own
scroll panel). Lazy lists (step 6): pause off-screen advance, and only rows near the view exist.

### 6. Lazy lists (after step 5, if its numbers call for it)

`LazyColumn` / `LazyRow`, Compose's names: only the rows near the viewport exist.

```kotlin
LazyColumn(ships.value, Size.fillMaxSize(), key = { it.id }, itemHeight = 40f) { ship ->
    Row { ShipDisplay(...); Text(ship.name); Button("Sell") { ... } }
}
```

- A vanilla scroll panel (step 1's scrolling container) with `setContentSize` set to the whole list (row count x
  item height + spacing), so the scrollbar, drag and wheel are vanilla's and the wheel patch's.
- A scope whose builder emits only the rows in the visible range plus a small buffer (a screen above and below), each
  placed at its position in the whole list. The range is checked in `advance` (cheap: offset / item height) and the
  scope rebuilds only when it changes; `key` reuse keeps rows that stay visible, creates the ones scrolling in,
  disposes the ones leaving.
- First version: fixed `itemHeight` (exact scrollbar, exact positions). Variable heights later: measure rows as they
  are made, estimate the rest from the average, correct the content size as measurements arrive (Compose does the
  same; the bar moves a little).
- Documented consequences: a row's native state (text field focus, inner scroll position, hover) is lost when it
  scrolls out, as in Compose; anything that must survive goes in `UIState`. Rows with heavy content (`ShipDisplay`
  builds a ship) may stutter on fast scrolls: measure with the step 5 list plus a ShipDisplay per row.
- Tests: the step 5 lists as `LazyColumn` (and beat its budgets by a wide margin); scrolling to the end and back keeps
  keyed rows stable; a state change in a visible row rebuilds only that row; removing an item above the viewport
  keeps the visible rows in place.

### 7. Docs (part of the work, not after it)

- KDoc on `AxisOverflow`, `Overflow` and each shorthand, on the `overflow` parameter of every container, and on
  `CustomPanelAPI.scrollPanel`: what each value does, that a scroll axis clips both axes (and why: vanilla's scroll
  panel scissors both, CSS forbids the pair too), what fill means on a scroll axis, that wrapping axes never overflow.
- AGENTS.md Part 1: a new "Overflow: clipping and scrolling" section with the table and rules above, the wheel
  behaviour (innermost first, chaining, plain/Shift axes, vanilla unchanged otherwise), and the `.scrollPanel`
  accessor. Update the Containers table (drop `ScrollPanel`, add `overflow`), the Sizes rules ("nothing can fill
  inside a ScrollPanel's scrolling direction" becomes the fill-what-is-left rule) and the "Where StarUI differs from
  Jetpack Compose" list (overflow is a parameter, CSS-style, not `clipToBounds` / `verticalScroll` modifiers, because
  the native object changes). Part 2: the wheel patch in the source layout and "how it works" lists.
- README: "Clipping and scrolling on any container, both axes" under layout; drop "ScrollPanels"; "Lazy lists" once
  step 6 is done.
- AGENTS.md: that scrolling containers draw only what is in view, the step 5 budgets (what a long list costs), and,
  with step 6, when to use `LazyColumn` instead of `Column(overflow = ...)` (thousands of rows, or heavy rows) and that
  a lazy row loses its native state when it scrolls out.

## Risks and open points

- Fill on a scroll axis collapses to 0 when the content overflows. Documented, not an error; revisit if it surprises.
- Rebuild timing: a content size change after a rebuild must re-run `setContentSize` and `clampOffset` the same frame,
  or the bar flickers.
- Scissor nesting between StarUI's clip and vanilla's scissor (scroll panels, ship displays inside clipped boxes).
- Input on clipped-away parts of vanilla buttons: withholding events from vanilla children may need care.
- The wheel patch finds nested panels by walking children under the cursor every scroll event: fine for events (rare),
  never per frame.
