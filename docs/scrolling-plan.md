# Scrolling plan: 2D ScrollPanel, scrolling Rows and Columns, wheel on both axes

Status: agreed with the user (2026-11-12), not started. Work in this order; each step ends with the in-game suite
passing (`native-smoke/run-test-suite.ps1`, under `smoke-slot.sh run`) and the PNGs looked at.

## Decisions

- **No "everything is a scroll panel".** Only containers that ask for scrolling become vanilla scroll panels.
  Reasons: a scroll panel scissors its content every frame (button glow, section headings that draw 5 px outside
  their label, ship displays would be cut), nested scissors, twice the panels and per-frame fader and scrollbar
  work per container, and wrapping containers can never overflow anyway.
- **`ScrollPanel` becomes the 2D viewport**: content laid out like a `Box` (children placed with
  `Modifier.align`), scrolls on whichever axis overflows. Returns the vanilla `ScrollPanelAPI`. Its list settings
  (`spacing`, `horizontalAlignment`) go: lists move to Row and Column. No deprecations (the user is the only user).
- **Row and Column get `scroll: Scroll = Scroll.None`** with `None`, `Vertical`, `Horizontal`, `Both`, named by
  screen axis (Horizontal means the same on a Row and a Column). A parameter, not a modifier: the native object has
  to be chosen before it exists, and modifiers run after.
- **Return types do not change.** A scrolling Column is a vanilla scroll panel holding the StarUI Column; the call
  returns that Column's `CustomPanelAPI`. A `core` accessor `CustomPanelAPI.scrollPanel: ScrollPanelAPI?` gives the
  scroll API (`scrollToY`, `ensureVisible`, offsets) for a scrolling container.
- **Layout rule on a scrolled axis**: the room offered to the content is the viewport's size (so fill, wrapping text
  and alignment have something to work with); the content's size is max(viewport, what it measures). Something
  bigger than the viewport makes the content bigger and vanilla shows that axis's bar by itself.
  **Fill on a scrolled axis means "what is left of the viewport"**: 0 when the content already overflows that axis.
  This replaces the current error "Cannot use Dimension.FillMax inside an unconstrained infinite axis". On an
  unscrolled axis the content is bounded by the viewport, as now.
- **Wheel (PatchLib patch, game-wide)**: plain wheel scrolls y when the content overflows vertically, otherwise x
  when it overflows horizontally; Shift+wheel scrolls x when both overflow. A panel with no horizontal overflow keeps
  vanilla's behaviour exactly (including Shift = 5x faster vertical). Chosen over a StarUI-only wheel
  (`setUseMWheel(false)` plus StarUI handling) because it keeps vanilla's wheel feel and covers every panel.
- **Clipping (`Modifier.clip()`, overflow: hidden) and text ellipsis are separate, later work**, not part of this.

## What vanilla does (0.98.5a decompile, `C:\Users\starf\starsector-reference-0.98.5a`)

The scroll panel is `obf/com/fs/starfarer/ui/g.java` (`class g extends z implements ScrollPanelAPI`). Line numbers are
from that decompile; names are obfuscated unless they are API or readable.

- **Already 2D.** Horizontal widget and rail rectangles (`class.do()`, `oÖO000()`, ~894-928), a 4 px bar along the
  bottom drawn whenever content width > panel width, drawn in the same render method as the vertical bar
  (`Öo0000`, ~716-846). Clicking the horizontal rail jumps, dragging its widget scrolls (`processScrollInputOnly`,
  ~216-430). Right-drag pans the content on both axes (default button field `ÔØÔo00 = g.o.Ò00000` = RMB).
- **Wheel is vertical only**, in `processScrollInputOnly(events, boolean)` (~123-160): step -100, capped at 33% of
  the panel height, times 5 with Shift, clamped to the content height; then `oØÔo00.setOffset(x, y)` and a position
  recompute; the event is consumed. Only handled when `setUseMWheel` is on and the panel overflows vertically (or is
  already scrolled).
- **Callers of `processScrollInputOnly`**: the panel's own `processInputImpl` (line 447), the trade screen's cargo
  grids (`campaign/ui/trade/F.java:254-255`, passing `true`) and a core-UI list (`coreui/publicsuper.java:146`).
  Hooking that one method covers every wheel path.
- **API setters keep vanilla consistent**: `setXOffset` / `setYOffset` write the internal offset fields and
  `forceOffset` the content (lines 433-441); `setContentSize(w, h)` sizes the content container (119);
  `clampOffset` (942) zeroes an axis that does not overflow. `getContentContainer()` (100) is the content panel
  (obfuscated type, a UIPanelAPI, not a CustomPanelAPI).
- **Gotcha found earlier**: vanilla `bringComponentToTop` also raises the panel in its parent, all the way up
  (`ui/z.java` `bringToTop`). Never use it to reorder inside StarUI; remove and re-add instead.

## What StarUI does now (to change)

- `compose/components/Containers.kt`, `ScrollPanel(...)`: makes a vanilla scroll panel through a temporary tooltip,
  attaches a `StarUIPanelPlugin` to its content container with `myNodeSize = NodeSize(FillMax(1f), WrapContent)`
  and a vertical `StackLayoutEngine`; another plugin on the outer panel. FillMax width is why content can never be
  wider than the panel.
- `compose/layout/StackLayoutEngine.kt` throws for FillMax on an unbounded axis (line ~120).
- StarUI's input hook is an `@After` on `processInput` (`internal/UIComponentLifecyclePatch.kt` ~104-121), so vanilla
  consumes wheel events before StarUI sees them: the wheel change needs its own `@Before`.
- Current uses of `ScrollPanel(`: only the samples (`ShowcaseUIPlugin.kt`: the page body, the task list, the two
  "ScrollPanel options" cards; `ExampleUIPlugin.kt:213`). Refit Filters, Combat AI Tweaks and Scy Nation have none.

## Steps

### 1. Scrolling Row and Column

- `enum class Scroll { None, Vertical, Horizontal, Both }` in `compose/` (next to `VGrow`/`HGrow`).
- `Column(..., scroll: Scroll = Scroll.None)` and `Row(...)`: with scroll, create the vanilla scroll panel (reuse the
  existing ScrollPanel creation code, factored out), put the StarUI Column/Row panel inside its content container,
  size the outer panel by the call's `size`, and return the inner panel. Scroll panel options (shadows, colours,
  scrollbars, left scrollbar, mouse wheel) come from `StarUITheme.scrollPanel`; decide whether Row/Column expose
  them or only the theme does (lean: theme only, plus `.scrollPanel` for anything else).
- Reuse: the scroll panel and its inner container must be reused across rebuilds (keep scroll position), like
  `ScrollPanel` does today (`slot.reused()` / `applyReuseBounds`). Changing `scroll` between builds makes a new one.
- Layout engine: a small viewport step between the outer scroll panel and the inner container. Per scrolled axis:
  measure the inner container with room = viewport size, content size = max(viewport, measured), then
  `setContentSize` and lay the inner container out at that size. Fill children on a scrolled axis get the viewport's
  leftover (0 when overflowing): change the FillMax-on-unbounded-axis error in `StackLayoutEngine` so a scrolled axis
  passes a bounded room instead of `Float.MAX_VALUE`.
- After a layout that changes content size: `clampOffset()` so a shrunk list does not stay scrolled past its end.
- `core`: `val CustomPanelAPI.scrollPanel: ScrollPanelAPI?` (parent chain check: inner panel -> content container ->
  scroll panel), plus `dumpLayout` showing `scroll=Vertical` etc.
- Migrate the samples: the showcase page body, task list and option cards; `ExampleUIPlugin`.

Tests (new group file `native-smoke/kotlin/ScrollTests2.kt` or extend `AnchorAndScrollTests.kt`):
- a `Column(scroll = Vertical)` with tall content: content height = sum, width = viewport, vertical bar only, scroll
  position kept across a rebuild;
- a `Row(scroll = Horizontal)` of cards wider than the viewport: content width = sum, horizontal offset can be set
  and is clamped;
- `Column(scroll = Vertical)` with a short list and a `Spacer(Size.fillMaxSize())` footer: the footer reaches the
  bottom of the viewport; with a long list the spacer is 0;
- an exact 2000 px child in `Column(scroll = Both)`: content width 2000, both bars;
- nested: a scrolling Row inside a scrolling Column;
- `scroll = None` behaves exactly as now (the existing suite covers it).

### 2. ScrollPanel as the 2D viewport

- `ScrollPanel(size: BoundedSize, modifier, <scroll panel options>, builder)`: content laid out by a
  `BoxLayoutEngine`, both axes scrolled with the step 1 rule. Remove `spacing` and `horizontalAlignment` (and
  `ScrollPanelTheme.horizontalAlignment`).
- Update the docs in `Containers.kt`, AGENTS.md (Containers table, Sizes rules: "nothing can fill inside a
  ScrollPanel's scrolling direction" becomes the fill-what-is-left rule) and README.
- Tests: a 1500 x 1200 child in a 400 x 300 ScrollPanel (both bars, offsets clamp on both axes); a small child with
  `Modifier.align(Alignment.Center)` sits centred in the viewport.

### 3. Wheel patch

Read the patchlib skill's `SKILL.md`, `references/guidelines.md` and `references/library-notes.md` (and
`obfuscated-code.md`) again before writing it; state the skill version (1.0.0) against the install (1.1.0).

- New file `internal/ScrollWheelPatch.kt`: `@Patch(target = ClassMatch(subtype = ScrollPanelAPI::class))`,
  `@Before(target = MethodMatch(methodName = "processScrollInputOnly", parameterCount = 2, ...))`. Comment the
  build-labelled obfuscated class name (`com.fs.starfarer.ui.g` on 0.98.5a-RC4). `processScrollInputOnly` is a
  readable non-API name: an inferred target, first to re-check after a game update.
- Handler: `self as ScrollPanelAPI`; content size from `contentContainer.position`, viewport from `self.position`.
  For each unconsumed mouse scroll event inside the panel (`isMouseScrollEvent`, contains check against the panel's
  position by coordinates, not `containsEvent` on an obfuscated type): if horizontal overflow and (Shift held and
  vertical overflow, or no vertical overflow) and the wheel is on (no API getter for `setUseMWheel`: decide whether
  to respect it, e.g. a readable field by shape or skip the check) -> step = vanilla's (-100, capped at 33% of the
  panel width), `setXOffset` clamped to [viewport - contentWidth, 0] (vanilla's x offset is <= 0), consume the event.
  Everything else is left for vanilla. The event list arg is `new` (obfuscated list type): iterate it as a
  `List<InputEventAPI>` through the API interface.
- Keep the handler trivial (it runs every frame for every scroll panel): bail out first when there is no horizontal
  overflow.
- Tests: send real scroll events (`StarUITestSupport` has mouse events; check whether it can make a wheel event, add
  one if not): plain wheel on a horizontal-only Row moves x; Shift+wheel on a Both panel moves x; plain wheel on a
  Both panel moves y; a vertical-only panel with Shift keeps vanilla's 5x vertical step. Verify the
  `Installed a hook patch site` log line.

### 4. Showcase

- Replace the "ScrollPanel options" card with: a horizontal card strip (`Row(scroll = Horizontal)`), a 2D map-like
  grid in a `ScrollPanel`, a short scrolling Column with a fill footer, and the old options (left scrollbar, no
  shadows, widget colour) on one of them.
- The page body becomes `Column(Size.fillMaxSize(), scroll = Scroll.Vertical)`.
- Check in the test game (`scratchpad/live-showcase`, staged jar in `scratchpad/stage-pl`), then offer a play-mode
  boot (`smoke.sh play ... --jar` with the showcase-registering build from `scratchpad/play-showcase`).

## Risks and open points

- Fill-what-is-left on a scrolled axis: fill children collapse to 0 when the content overflows. Documented, not an
  error. Revisit if it surprises.
- Rebuild timing: content size changes after a rebuild must re-run `setContentSize` and `clampOffset` the same
  frame, or the bar flickers.
- Nested wheel: the inner panel's `processScrollInputOnly` runs first (children process input before the outer's
  `@After`, and the outer's `processScrollInputOnly` runs before its children: check the order, because vanilla
  calls `processScrollInputOnly` before `super.processInputImpl`, i.e. the OUTER panel sees the wheel first).
  Vanilla only consumes when it can scroll, so an outer panel that cannot scroll on that axis passes it on; make the
  patch do the same.
- `setUseMWheel(false)` panels: the patch must not scroll them horizontally.
