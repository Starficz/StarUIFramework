# Overflow plan: clipping and scrolling as one per-axis setting, wheel on both axes

Status: agreed with the user (2026-11-12), not started. Work in the order of the steps below; each step ends with the
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

### 5. Docs (part of the work, not after it)

- KDoc on `AxisOverflow`, `Overflow` and each shorthand, on the `overflow` parameter of every container, and on
  `CustomPanelAPI.scrollPanel`: what each value does, that a scroll axis clips both axes (and why: vanilla's scroll
  panel scissors both, CSS forbids the pair too), what fill means on a scroll axis, that wrapping axes never overflow.
- AGENTS.md Part 1: a new "Overflow: clipping and scrolling" section with the table and rules above, the wheel
  behaviour (innermost first, chaining, plain/Shift axes, vanilla unchanged otherwise), and the `.scrollPanel`
  accessor. Update the Containers table (drop `ScrollPanel`, add `overflow`), the Sizes rules ("nothing can fill
  inside a ScrollPanel's scrolling direction" becomes the fill-what-is-left rule) and the "Where StarUI differs from
  Jetpack Compose" list (overflow is a parameter, CSS-style, not `clipToBounds` / `verticalScroll` modifiers, because
  the native object changes). Part 2: the wheel patch in the source layout and "how it works" lists.
- README: "Clipping and scrolling on any container, both axes" under layout; drop "ScrollPanels".

## Risks and open points

- Fill on a scroll axis collapses to 0 when the content overflows. Documented, not an error; revisit if it surprises.
- Rebuild timing: a content size change after a rebuild must re-run `setContentSize` and `clampOffset` the same frame,
  or the bar flickers.
- Scissor nesting between StarUI's clip and vanilla's scissor (scroll panels, ship displays inside clipped boxes).
- Input on clipped-away parts of vanilla buttons: withholding events from vanilla children may need care.
- The wheel patch finds nested panels by walking children under the cursor every scroll event: fine for events (rare),
  never per frame.
