package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Alignment
import org.starficz.starui.compose.AspectWrapImage
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.HGrow
import org.starficz.starui.compose.ImageSize
import org.starficz.starui.compose.Mixed
import org.starficz.starui.compose.NativeImage
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.StarUIDsl
import org.starficz.starui.compose.VGrow
import org.starficz.starui.compose.layout.BoxLayoutEngine
import org.starficz.starui.compose.layout.StackDirection
import org.starficz.starui.compose.layout.StackLayoutEngine
import org.starficz.starui.compose.layout.PassThroughLayoutEngine
import org.starficz.starui.compose.layout.ViewportLayoutEngine
import org.starficz.starui.compose.Overflow
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.compose.modifier.actual
import org.starficz.starui.compose.runtime.ChildSlot
import org.starficz.starui.compose.runtime.ClipStack
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.compose.toNodeSize
import org.starficz.starui.core.renderShadows
import org.starficz.starui.core.shadowColor
import org.starficz.starui.core.children
import org.starficz.starui.core.height
import org.starficz.starui.core.setSize
import org.starficz.starui.core.width

// =====================================================================
// SIZING HELPER
// Evaluates Tick-0 dimensions for elements before the 3-Pass Solver runs.
// =====================================================================
/** [contentPadding] is a leaf's own inside space (a button's around its label): part of its size on a wrap axis. */
internal fun getInitialBounds(
    size: ImageSize, intrinsic: Pair<Float, Float>? = null, contentPadding: PaddingValues = PaddingValues.Zero
): Pair<Float, Float> {
    val w = when (size) {
        is Mixed -> when (val x = size.x) {
            is Axis.Exact -> x.pixels
            is Axis.Wrap -> (intrinsic?.first ?: 1f) + contentPadding.horizontal
            is Axis.Fill -> 1f // Fill strictly resolves in Pass 3 Layout
        }
        is NativeImage -> size.maxWidth ?: intrinsic?.first ?: 10f
        is AspectWrapImage -> 1f
    }
    val h = when (size) {
        is Mixed -> when (val y = size.y) {
            is Axis.Exact -> y.pixels
            is Axis.Wrap -> (intrinsic?.second ?: 1f) + contentPadding.vertical
            is Axis.Fill -> 1f
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
    val container = StarUIContext.currentPluginOrNull
    val key = StarUIContext.nextKey()
    val cursor = container?.reuseCursor
    val record = if (cursor != null && cursor.isFor((this as? ScrollPanelAPI)?.contentContainer ?: this)) {
        cursor.take(kind, key, props)
    } else null
    return ChildSlot(container, kind, key, props, record)
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
        is Mixed -> size.x is Axis.Fill || (size.x is Axis.Wrap && intrinsic == null)
        is AspectWrapImage -> true
        else -> false
    }
    val keepH = when (size) {
        is Mixed -> size.y is Axis.Fill || (size.y is Axis.Wrap && intrinsic == null)
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
 * Ties the components in [block] to [id], so they stay with their item when a list changes.
 *
 * Without keys, a rebuild reuses components by position: remove the first row and the second row gets the first
 * row's text field, focus included. Wrap each list item in a key:
 *
 * ```
 * for (ship in ships.value) key(ship.id) {
 *     Row { Text(ship.name); TextField(ship.note) }
 * }
 * ```
 *
 * Keys must be unique within one container. Use ids, strings or numbers.
 */
fun <R> key(id: Any, block: () -> R): R = StarUIContext.withKey(id, block)

/**
 * Records how [component] is sized, with its container or (top level) on itself. [contentPadding] is a leaf's own
 * inside space from its component function; `Modifier.padding` adds to it afterwards.
 */
internal fun registerComponentSizing(
    component: UIComponentAPI,
    size: ImageSize,
    isContainer: Boolean,
    containerPlugin: StarUIPanelPlugin? = null,
    contentPadding: PaddingValues = PaddingValues.Zero
) {
    val actual = component.actual
    val nodeSize = size.toNodeSize()

    val isManagedByParent = !StarUIContext.isTopLevel && StarUIContext.currentPlugin.layoutEngine != null

    if (isContainer && containerPlugin != null) {
        containerPlugin.myNodeSize = nodeSize
        containerPlugin.isTopLevelNode = !isManagedByParent
    }

    if (isManagedByParent) {
        val parentPlugin = StarUIContext.currentPlugin
        parentPlugin.childSizing[actual] = nodeSize
        if (!isContainer) {
            if (contentPadding != PaddingValues.Zero) parentPlugin.childPadding[actual] = contentPadding
            else parentPlugin.childPadding.remove(actual)
        }
    } else if (!isContainer) {
        val selfPlugin = StarUIPanelPlugin.getOrCreate(actual)
        selfPlugin.myNodeSize = nodeSize
        selfPlugin.isTopLevelNode = true
    }
}

/**
 * Uses [theme] for the default arguments of every component called in [builder]. Adds no panel: the components go
 * straight into this one.
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
        plugin.resetForReuse()   // the rebuild reset the scroll panel's plugin; this one sits below it
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
 * A panel that does not place its children: position each one with `Modifier.anchor(...)`. Wraps its children by
 * default. [overflow] says what happens to children outside it (see [Overflow]); a scrolling CustomPanel's content
 * reaches as far right and down as its children do.
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
    parts.register(slot, size, modifier)
    parts.plugin.attachScope("CustomPanel", builder) { parts.panel.builder() }
    return parts.panel
}

/**
 * Puts its children on top of each other, later ones drawn over earlier ones. Place each child with
 * `Modifier.align(...)` (top left by default). Wraps its largest child by default. [overflow] says what happens to
 * children bigger than it (see [Overflow]): `Overflow.Scroll` makes it a 2D viewport.
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
    parts.plugin.layoutEngine = BoxLayoutEngine(parts.plugin)
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
    parts.plugin.isStackLayout = true
    parts.plugin.layoutEngine = StackLayoutEngine(
        plugin = parts.plugin,
        direction = direction,
        crossAlignValue = crossAlignValue,
        spacing = spacing
    )
    parts.register(slot, size, modifier)
    parts.plugin.attachScope(kind, builder) { parts.panel.builder() }
    return parts.panel
}

/**
 * Places its children top to bottom, [spacing] apart, aligned by [horizontalAlignment] (left by default).
 * Fills the width and wraps its children's height by default. `grow = VGrow.Upwards` stacks bottom to top instead.
 * [overflow] says what happens to children that do not fit (see [Overflow]): `Overflow.ScrollVertical` makes it a
 * scrolling list.
 */
fun UIPanelAPI.Column(
    size: Size = Size.fillMaxWidth(),
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
 * Places its children left to right, [spacing] apart, aligned by [verticalAlignment] (top by default).
 * Wraps its children's width and fills the height by default. `grow = HGrow.Leftwards` stacks right to left instead.
 * [overflow] says what happens to children that do not fit (see [Overflow]): `Overflow.ScrollHorizontal` makes it a
 * scrolling strip.
 */
fun UIPanelAPI.Row(
    size: Size = Size.fillMaxHeight(),
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
