package org.starficz.starui.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.*
import org.starficz.starui.Alignment
import org.starficz.starui.layout.*
import org.starficz.starui.modifier.actual
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.PaddingValues
import org.starficz.starui.runtime.*
import org.starficz.starui.vanilla.*
import org.starficz.starui.vanilla.ReflectionUtils.invoke

/**
 * The size a component starts at, before its container lays it out: exact axes as given, wrap axes at [intrinsic]
 * plus [contentPadding] (a leaf's own inside space, a button's around its label), fill axes at 1 until the layout.
 */
internal fun getInitialBounds(
    size: ImageSize, intrinsic: Pair<Float, Float>? = null, contentPadding: PaddingValues = PaddingValues.Zero
): Pair<Float, Float> {
    val w = when (size) {
        is Mixed -> when (val x = size.x) {
            is Axis.Exact -> x.pixels
            is Axis.WrapContent -> (intrinsic?.first ?: 1f) + contentPadding.horizontal
            is Axis.FillMax, AspectRatioAxis -> 1f
        }
        is NativeImage -> size.maxWidth ?: intrinsic?.first ?: 10f
        is AspectWrapImage -> 1f
    }
    val h = when (size) {
        is Mixed -> when (val y = size.y) {
            is Axis.Exact -> y.pixels
            is Axis.WrapContent -> (intrinsic?.second ?: 1f) + contentPadding.vertical
            is Axis.FillMax, AspectRatioAxis -> 1f
        }
        is NativeImage -> size.maxHeight ?: intrinsic?.second ?: 10f
        is AspectWrapImage -> 1f
    }
    return Pair(w, h)
}

/**
 * Called first by every component function. A container whose builder read a UIState replaces its children whenever
 * that state changes, so a child added from anywhere else (an event handler, code that kept a reference to the
 * panel) would silently disappear on the next rebuild. Fail at the call instead, and say what to do.
 */
internal fun UIPanelAPI.checkDslTarget() {
    val target: UIPanelAPI = (this as? ScrollPanelAPI)?.contentContainer ?: this
    val plugin = StarUIPanelPlugin.getAttached(target) ?: return
    val scope = plugin.scope ?: return
    if (!scope.isReactive || StarUIContext.currentPluginOrNull === plugin) return
    throw IllegalStateException(
        "StarUI: a component was added to a reactive container from outside its builder block (for example from an " +
        "onClick handler). This container rebuilds its children whenever a UIState its builder read changes, so " +
        "anything added from outside would be lost. Change a UIState instead and let the builder read it: " +
        "`if (showDetails.value) Text(...)` inside the builder, `showDetails.value = true` in the handler."
    )
}

/**
 * Called by every component function before it creates anything. It fixes this call's `key(...)` and, when the
 * surrounding builder is running again, looks for the old child this call should update instead of creating a new
 * one. [props] are the arguments that cannot be changed on a live native object; an old child is only reused when
 * its props are equal.
 */
internal fun UIPanelAPI.childSlot(kind: String, props: Any? = null): ChildSlot {
    val key = StarUIContext.nextKey()
    val cursor = StarUIContext.currentPluginOrNull?.reuseCursor
    val record = if (cursor != null && cursor.isFor((this as? ScrollPanelAPI)?.contentContainer ?: this)) {
        cursor.take(kind, key, props)
    } else null
    return ChildSlot(kind, key, props, record)
}

/**
 * Sizes a reused component the way a new one would start, except on an axis only the layout pass can resolve (Fill,
 * or Wrap without a known content size): that axis keeps its current size, so a reused component does not collapse
 * for a moment and a scroll panel does not lose its scroll position.
 */
internal fun applyReuseBounds(
    component: UIComponentAPI, size: ImageSize, intrinsic: Pair<Float, Float>? = null,
    contentPadding: PaddingValues = PaddingValues.Zero
) {
    val (initW, initH) = getInitialBounds(size, intrinsic, contentPadding)
    val keepW = when (size) {
        is Mixed -> size.x is Axis.FillMax || (size.x is Axis.WrapContent && intrinsic == null)
        is AspectWrapImage -> true
        else -> false
    }
    val keepH = when (size) {
        is Mixed -> size.y is Axis.FillMax || (size.y is Axis.WrapContent && intrinsic == null)
        is AspectWrapImage -> true
        else -> false
    }
    val w = if (keepW) component.width else initW
    val h = if (keepH) component.height else initH
    if (w != component.width || h != component.height) component.setSize(w, h)
}

/** The CustomPanel for a container call: the reused one, or a new one with a fresh plugin added to this panel. */
internal fun UIPanelAPI.containerPanel(slot: ChildSlot, size: ImageSize): Pair<CustomPanelAPI, StarUIPanelPlugin> {
    val reused: CustomPanelAPI? = slot.reused()
    if (reused != null) {
        applyReuseBounds(reused, size)
        return Pair(reused, reused.plugin as StarUIPanelPlugin)
    }
    val (initW, initH) = getInitialBounds(size)
    val plugin = StarUIPanelPlugin()
    val panel = Global.getSettings().createCustom(initW, initH, plugin)
    plugin.customPanel = panel
    this.addComponent(panel)
    return Pair(panel, plugin)
}

/**
 * The vanilla [ScrollPanelAPI] of a scrolling container: a [Column], [Row], [Box] or [CustomPanel] whose `overflow`
 * scrolls on at least one axis. Use it to read or change the scroll position (`scrollToY`, `ensureVisible`, the
 * offsets) and for the rest of [ScrollPanelAPI]. `null` for any other panel.
 *
 * The container itself is the scroll panel's content, so its size is the size of the scrolled content, not of the
 * visible area.
 */
val CustomPanelAPI.scrollPanel: ScrollPanelAPI?
    get() = (plugin as? StarUIPanelPlugin)?.scrollHost

/**
 * [key] is a utility function that is used to "group" or "key" a block of components inside a builder. This is
 * needed for correctness inside control flow that may cause a given component call to run more than once in a
 * builder, such as a loop over a list.
 *
 * When a builder runs again, StarUI reuses the components it made the last time, matched by position. Without keys,
 * removing the first item of a list makes the second item reuse the first item's components, with their native
 * state (a text field's text and focus, a scroll position). With a key, each item keeps its own components:
 *
 * ```kotlin
 * for (ship in ships.value) key(ship.id) {
 *     Row { Text(ship.name); TextField(ship.note) }
 * }
 * ```
 *
 * The value for a key does not need to be globally unique, and needs only be unique amongst the calls of [key] in
 * the same container.
 *
 * @param id the value that identifies this group. It is compared to its previous value using [equals] and
 *   [hashCode]
 * @param block the components of this group
 * @return the value [block] returns
 */
fun <R> key(id: Any, block: () -> R): R = StarUIContext.withKey(id, block)

/**
 * Records how [component] (already in its parent) is sized, on its node. [contentPadding] is a leaf's own inside space
 * from its component function; `Modifier.padding` adds to it afterwards (a container's padding is all modifiers'). A
 * component no StarUI container lays out is a top-level node: it sizes itself.
 */
internal fun registerComponentSizing(
    component: UIComponentAPI,
    size: ImageSize,
    isContainer: Boolean,
    containerPlugin: StarUIPanelPlugin? = null,
    contentPadding: PaddingValues = PaddingValues.Zero
) {
    val actual = component.actual
    val node = nodeFor(actual)
    node.size = size.toNodeSize()
    if (!isContainer) node.padding = contentPadding
    val topLevel = parentPluginOf(actual)?.layoutEngine == null
    if (isContainer) containerPlugin?.isTopLevelNode = topLevel
    else if (topLevel) StarUIPanelPlugin.getOrCreate(actual).isTopLevelNode = true
}

/**
 * The common end of a leaf component call (Text, Button, Image, ...), once its native [component] is made or reused
 * and in this panel: sizes it as a new or reused one starts ([intrinsic] is its content size now, null for a
 * component without one; [contentPadding] its own inside space), records on its node how it is sized,
 * measured ([measure]: its content size in a room) and laid out ([layout]: after it got its size), remembers it for
 * the next rebuild with [handle] (what the call returns) and applies [modifier]. [startSize] = false leaves its size
 * alone (a component whose own size setter has side effects). Returns its node.
 */
internal fun finishLeaf(
    slot: ChildSlot,
    component: UIComponentAPI,
    size: ImageSize,
    modifier: Modifier,
    handle: Any = component,
    intrinsic: Pair<Float, Float>? = null,
    contentPadding: PaddingValues = PaddingValues.Zero,
    startSize: Boolean = true,
    measure: ((maxWidth: Float, maxHeight: Float) -> Pair<Float, Float>)? = null,
    layout: ((width: Float, height: Float) -> Unit)? = null
): UINode {
    if (startSize) {
        if (slot.isReuse) applyReuseBounds(component, size, intrinsic, contentPadding)
        else getInitialBounds(size, intrinsic, contentPadding).let { (w, h) -> component.setSize(w, h) }
    }
    registerComponentSizing(component, size, isContainer = false, contentPadding = contentPadding)
    val node = slot.remember(component, handle)
    node.measure = measure
    node.layout = layout
    val target = handle as? UIComponentAPI ?: component
    modifier.operations.forEach { it(target) }
    return node
}

/**
 * Theming refers to the customization of StarUI's components to better fit the look of a mod's screens.
 *
 * Components such as [Button] and [Checkbox] use values provided here when retrieving default values. A theme only
 * provides defaults: an argument passed to a component call always wins.
 *
 * [Theme] replaces the whole theme for the components in [builder]. To change only part of it, copy the current
 * theme and override only the parts that need to change:
 *
 * ```kotlin
 * Theme(StarUITheme.current.copy(button = StarUITheme.current.button.copy(font = ButtonFont.ORBITRON_20))) {
 *     Button("Launch")
 * }
 * ```
 *
 * [Theme] adds no panel of its own: the components in [builder] go straight into this panel.
 *
 * @param theme the theme for the components in [builder]
 * @param builder the content using this theme
 * @see StarUITheme
 */
fun UIPanelAPI.Theme(
    theme: StarUITheme,
    builder: (@StarUIDsl UIPanelAPI).() -> Unit
) {
    StarUIContext.withTheme(theme) {
        this.builder()
    }
}

// =====================================================================
// CONTAINERS
// =====================================================================

/**
 * What a container call works with. [panel] and [plugin] are the StarUI container (its children go in it, the call
 * returns it); [node] is what its parent lays out and what the modifiers apply to: the panel itself, or for a
 * scrolling container the vanilla scroll panel holding it, with [nodePlugin] its plugin.
 */
internal class ContainerParts(
    val panel: CustomPanelAPI,
    val plugin: StarUIPanelPlugin,
    val node: UIComponentAPI,
    val nodePlugin: StarUIPanelPlugin
)

/**
 * The container for a call with [overflow]: the reused one, or a new one added to this panel. A scrolling container
 * is a vanilla scroll panel (sized by [size], laid out by a [ViewportLayoutEngine]) with the StarUI container in its
 * content container; the scroll panel and its scroll position are kept across rebuilds.
 */
internal fun UIPanelAPI.containerParts(slot: ChildSlot, size: Size, overflow: Overflow): ContainerParts {
    if (!overflow.scrolls) {
        val (panel, plugin) = containerPanel(slot, size)
        plugin.overflow = overflow
        // Clip on both axes: vanilla's own scissor, which nests (it intersects with the enclosing one). setClipping
        // is a readable engine method of the base panel (inferred, not API). A reused panel has the same overflow
        // (it is part of the slot's props), so only a new one needs it.
        if (overflow == Overflow.Clip && !slot.isReuse) panel.invoke("setClipping", true)
        if (overflow.clips) ClipStack.anyClipContainers = true   // wakes the input hook that hides the cut-off part
        return ContainerParts(panel, plugin, panel, plugin)
    }

    val reused: CustomPanelAPI? = slot.reused()
    val panel: CustomPanelAPI
    val plugin: StarUIPanelPlugin
    val host: ScrollPanelAPI
    if (reused != null) {
        panel = reused
        plugin = reused.plugin as StarUIPanelPlugin
        // the rebuild reset the scroll panel's plugin and node; this container sits below it (and holds the padding)
        plugin.resetForReuse()
        plugin.node.padding = PaddingValues.Zero
        host = plugin.scrollHost!!
        applyReuseBounds(host, size)
    } else {
        val (initW, initH) = getInitialBounds(size)
        val tempPanel = Global.getSettings().createCustom(initW, initH, null)
        val tempTooltip = tempPanel.createUIElement(initW, initH, true)
        tempPanel.addUIElement(tempTooltip)
        host = tempPanel.children[0] as ScrollPanelAPI
        host.removeComponent(tempTooltip)
        tempPanel.removeComponent(host)
        host.setSize(initW, initH)

        plugin = StarUIPanelPlugin()
        panel = Global.getSettings().createCustom(initW, initH, plugin)
        plugin.customPanel = panel
        plugin.scrollHost = host
        host.contentContainer.addComponent(panel).inTL(0f, 0f)
        this.addComponent(host)
    }
    plugin.overflow = overflow
    plugin.isTopLevelNode = false

    val theme = StarUITheme.current.scroll
    host.isLeftScrollbar = theme.isLeftScrollbar
    host.renderShadows = theme.renderShadows
    theme.maxShadowHeight?.let { host.maxShadowHeight = it }
    theme.shadowColor?.let { host.shadowColor = it }
    theme.scrollWidgetColor?.let { host.scrollWidgetColor = it }
    theme.scrollRailColor?.let { host.scrollRailColor = it }
    host.setShowScrollbars(theme.showScrollbars)
    host.setUseMWheel(theme.useMouseWheel)

    // The content container passes layout requests and "rebuild below" marks up to the scroll panel.
    StarUIPanelPlugin.getOrCreate(host.contentContainer).layoutEngine = PassThroughLayoutEngine
    val hostPlugin = StarUIPanelPlugin.getOrCreate(host)
    hostPlugin.scrollContent = plugin
    hostPlugin.wheelEnabled = theme.useMouseWheel
    hostPlugin.layoutEngine = ViewportLayoutEngine(host, plugin, overflow, size.toNodeSize())
    return ContainerParts(panel, plugin, host, hostPlugin)
}

/** Records the sizing, remembers the container for the next rebuild and applies the modifiers. */
internal fun ContainerParts.register(slot: ChildSlot, size: Size, modifier: Modifier) {
    registerComponentSizing(node, size, isContainer = true, containerPlugin = nodePlugin)
    slot.remember(node, panel)
    modifier.operations.forEach { it(node) }
}

/**
 * A layout component that does not place its children: each child is positioned with `Modifier.anchor(...)`,
 * against the [CustomPanel] or against a sibling. For layout components that place their children themselves, see
 * [Column], [Row] and [Box].
 *
 * By default a [CustomPanel] wraps its children: it is as large as needed to reach the far edge of each anchored
 * child (a child anchored to its centre or far edge needs its own size plus its offset). To change the size of the
 * [CustomPanel], pass a different [size]. A child that fills an axis takes the [CustomPanel]'s size on it; a child
 * without an anchor sits at the top left.
 *
 * Children that reach outside the [CustomPanel] are drawn and take input by default. [overflow] can clip them, or
 * make the [CustomPanel] scroll; the content of a scrolling [CustomPanel] reaches as far right and down as its
 * children do.
 *
 * Example usage:
 *
 * ```kotlin
 * CustomPanel(Size.exact(300f, 200f)) {
 *     Text("Top left", modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(10f, 10f)))
 *     Button("Close", modifier = Modifier.anchor(Anchor.inside.bottomRight.ofParent(10f, 10f)))
 * }
 * ```
 *
 * @param size the size of the [CustomPanel]. By default it wraps its children
 * @param modifier the [Modifier] to be applied to this panel
 * @param overflow what happens to children that reach outside the [CustomPanel], per axis. See [Overflow]
 * @param builder the content of the [CustomPanel]. It runs again when a [UIState] it read changes
 * @return the [CustomPanel]'s panel
 * @see Anchor
 */
fun UIPanelAPI.CustomPanel(
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    overflow: Overflow = Overflow.Visible,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    checkDslTarget()
    val slot = childSlot("CustomPanel", overflow)
    val parts = containerParts(slot, size, overflow)
    // sizes its children and itself; the children place themselves by anchor
    if (parts.plugin.layoutEngine !is AnchorLayoutEngine) parts.plugin.layoutEngine = AnchorLayoutEngine(parts.plugin)
    parts.register(slot, size, modifier)
    parts.plugin.attachScope("CustomPanel", builder) { parts.panel.builder() }
    return parts.panel
}

/**
 * A layout component with [builder] content. The [Box] will size itself to fit its largest child. Each child is
 * positioned inside the [Box] with `Modifier.align(...)`, or at the top left without one. When the content has more
 * than one child, the children will be stacked one on top of the other in the order they were added.
 *
 * A child that fills an axis takes the [Box]'s whole size on that axis. This needs the [Box] to have a known size on
 * that axis (exact or fill): a filling child in a wrapping [Box] throws.
 *
 * [overflow] says what happens to children larger than the [Box]; `Overflow.Scroll` makes it a viewport that
 * scrolls on both axes.
 *
 * Example usage:
 *
 * ```kotlin
 * Box(Size.exact(200f, 100f), Modifier.background(VanillaColors.panel)) {
 *     Text("Centered", modifier = Modifier.align(Alignment.Center))
 *     Button("X", modifier = Modifier.align(Alignment.TopRight))
 * }
 * ```
 *
 * @param size the size of the [Box]. By default it wraps its largest child
 * @param modifier the [Modifier] to be applied to this box
 * @param overflow what happens to children larger than the [Box], per axis. See [Overflow]
 * @param builder the content of the [Box]. It runs again when a [UIState] it read changes
 * @return the [Box]'s panel
 */
fun UIPanelAPI.Box(
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    overflow: Overflow = Overflow.Visible,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    checkDslTarget()
    val slot = childSlot("Box", overflow)
    val parts = containerParts(slot, size, overflow)
    if (parts.plugin.layoutEngine !is BoxLayoutEngine) parts.plugin.layoutEngine = BoxLayoutEngine(parts.plugin)
    parts.register(slot, size, modifier)
    parts.plugin.attachScope("Box", builder) { parts.panel.builder() }
    return parts.panel
}

private fun UIPanelAPI.StackLayout(
    direction: StackDirection,
    crossAlignValue: Float,
    size: Size,
    modifier: Modifier,
    spacing: Float,
    overflow: Overflow,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit
): CustomPanelAPI {
    checkDslTarget()
    val kind = if (direction.isVertical) "Column" else "Row"
    val slot = childSlot(kind, overflow)
    val parts = containerParts(slot, size, overflow)
    // a reused container keeps its engine (and what it cached about its children's placements)
    val kept = (parts.plugin.layoutEngine as? StackLayoutEngine)?.takeIf { it.direction == direction }
    val engine = kept ?: StackLayoutEngine(parts.plugin, direction).also { parts.plugin.layoutEngine = it }
    if (engine.crossAlignValue != crossAlignValue || engine.spacing != spacing) {
        engine.crossAlignValue = crossAlignValue
        engine.spacing = spacing
        engine.forgetPlacements()
    }
    parts.register(slot, size, modifier)
    parts.plugin.attachScope(kind, builder) { parts.panel.builder() }
    return parts.panel
}

/**
 * A layout component that places its children in a vertical sequence. For a layout component that places its
 * children in a horizontal sequence, see [Row]. Note that by default items do not scroll; pass
 * `overflow = Overflow.ScrollVertical` to add this behavior. For a vertically scrollable list that only creates and
 * lays out the currently visible items see [LazyColumn].
 *
 * The [Column] layout is able to assign children heights according to their weights, given as
 * `Axis.FillMax(weight)` in a child's size (`Size.fillMaxHeight()` and `Size.fillMaxSize()` have a weight of 1).
 * Children that do not fill are measured first; the height that remains is then divided between the filling
 * children in proportion to their weights. This needs the [Column] to have a known height (exact or fill): a filling
 * child in a wrapping [Column] throws. If the [Column] scrolls vertically, a filling child gets what is left of the
 * visible height, which is 0 once the content overflows.
 *
 * When none of its children fill its height, a [Column] will be as small as possible to fit its children one on top
 * of the other. To change the size of the [Column], pass a different [size]; StarUI has no sizing modifiers. The
 * children are [spacing] apart, and [horizontalAlignment] positions the children that are narrower than the
 * [Column].
 *
 * Example usage:
 *
 * ```kotlin
 * Column(Size.fillMaxHeight(width = 200f), spacing = 5f) {
 *     Text("Fleet")
 *     FlexSpacer()                // takes the height that is left
 *     Button("Close") { close() }
 * }
 * ```
 *
 * @param size the size of the [Column]. By default it wraps its children
 * @param modifier the [Modifier] to be applied to this column
 * @param grow the direction the children are stacked in: [VGrow.Downwards] (top to bottom) or [VGrow.Upwards]
 * @param horizontalAlignment the horizontal alignment of the [Column]'s children
 * @param spacing the gap between two neighbouring children
 * @param overflow what happens to children that do not fit in the [Column], per axis. See [Overflow]
 * @param builder the content of the [Column]. It runs again when a [UIState] it read changes
 * @return the [Column]'s panel
 * @see Row
 * @see LazyColumn
 */
fun UIPanelAPI.Column(
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    grow: VGrow = VGrow.Downwards,
    horizontalAlignment: Alignment.Horizontal = Alignment.Left,
    spacing: Float = 0f,
    overflow: Overflow = Overflow.Visible,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI = StackLayout(
    direction = grow.direction,
    crossAlignValue = horizontalAlignment.alignValue,
    size = size,
    modifier = modifier,
    spacing = spacing,
    overflow = overflow,
    builder = builder
)

/**
 * A layout component that places its children in a horizontal sequence. For a layout component that places its
 * children in a vertical sequence, see [Column]. Note that by default items do not scroll; pass
 * `overflow = Overflow.ScrollHorizontal` to add this behavior. For a horizontally scrollable list that only creates
 * and lays out the currently visible items see [LazyRow].
 *
 * The [Row] layout is able to assign children widths according to their weights, given as `Axis.FillMax(weight)` in
 * a child's size (`Size.fillMaxWidth()` and `Size.fillMaxSize()` have a weight of 1). Children that do not fill are
 * measured first; the width that remains is then divided between the filling children in proportion to their
 * weights. This needs the [Row] to have a known width (exact or fill): a filling child in a wrapping [Row] throws.
 * If the [Row] scrolls horizontally, a filling child gets what is left of the visible width, which is 0 once the
 * content overflows.
 *
 * When none of its children fill its width, a [Row] will be as small as possible to fit its children one next to
 * the other. To change the size of the [Row], pass a different [size]; StarUI has no sizing modifiers. The children
 * are [spacing] apart, and [verticalAlignment] positions the children that are shorter than the [Row].
 *
 * Example usage:
 *
 * ```kotlin
 * Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 10f) {
 *     Text("Name")
 *     FlexSpacer()                // takes the width that is left
 *     Button("Rename") { rename() }
 * }
 * ```
 *
 * @param size the size of the [Row]. By default it wraps its children
 * @param modifier the [Modifier] to be applied to this row
 * @param grow the direction the children are placed in: [HGrow.Rightwards] (left to right) or [HGrow.Leftwards]
 * @param verticalAlignment the vertical alignment of the [Row]'s children
 * @param spacing the gap between two neighbouring children
 * @param overflow what happens to children that do not fit in the [Row], per axis. See [Overflow]
 * @param builder the content of the [Row]. It runs again when a [UIState] it read changes
 * @return the [Row]'s panel
 * @see Column
 * @see LazyRow
 */
fun UIPanelAPI.Row(
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    grow: HGrow = HGrow.Rightwards,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    spacing: Float = 0f,
    overflow: Overflow = Overflow.Visible,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI = StackLayout(
    direction = grow.direction,
    crossAlignValue = verticalAlignment.alignValue,
    size = size,
    modifier = modifier,
    spacing = spacing,
    overflow = overflow,
    builder = builder
)
