package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.apache.log4j.Logger
import org.starficz.starui.compose.Alignment
import org.starficz.starui.compose.AspectWrapImage
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.BoundedSize
import org.starficz.starui.compose.HGrow
import org.starficz.starui.compose.ImageSize
import org.starficz.starui.compose.Mixed
import org.starficz.starui.compose.NativeImage
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.StarUIDsl
import org.starficz.starui.compose.VGrow
import org.starficz.starui.compose.layout.BoxLayoutEngine
import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.layout.LayoutEngine
import org.starficz.starui.compose.layout.NodeSize
import org.starficz.starui.compose.layout.StackDirection
import org.starficz.starui.compose.layout.StackLayoutEngine
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.compose.modifier.actual
import org.starficz.starui.compose.runtime.ChildSlot
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
import java.awt.Color

private val containerLogger: Logger = Global.getLogger(StarUIPanelPlugin::class.java)

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
 * for a moment and a ScrollPanel does not lose its scroll position.
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
private fun UIPanelAPI.containerPanel(slot: ChildSlot, size: ImageSize): Pair<CustomPanelAPI, StarUIPanelPlugin> {
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
 * Gives the components emitted directly inside [block] a stable identity within their container.
 *
 * When a builder runs again, StarUI matches each component call to an old child of the same kind in order. That is
 * right for fixed layouts, but in a list whose items are inserted, removed or reordered it hands an item the native
 * objects of a different item (a text field keeps the focus of the row that used to be there, a ScrollPanel its
 * scroll position). Wrap each item in `key(item.id) { ... }` and its components follow the item:
 *
 * ```
 * for (ship in ships.value) key(ship.id) {
 *     Row { Text(ship.name); TextField(ship.note) }
 * }
 * ```
 *
 * Keys must be unique within one container and should have a stable `equals` (ids, strings, numbers).
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
 * Overrides the current StarUITheme for all components instantiated inside this block.
 * This does not create a physical UI panel.
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

/** A panel that places nothing: position its children with `Modifier.anchor(...)`. */
fun UIPanelAPI.CustomPanel(
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    checkDslTarget()
    val slot = childSlot("CustomPanel")
    val (panel, plugin) = containerPanel(slot, size)

    registerComponentSizing(panel, size, isContainer = true, containerPlugin = plugin)
    slot.remember(panel, panel)

    modifier.operations.forEach { it(panel) }

    plugin.attachScope("CustomPanel", builder) { panel.builder() }
    return panel
}

/**
 * A container that stacks its children on top of each other (Z-axis).
 * Use `Modifier.align(Alignment.Center)` on children to position them within the Box.
 */
fun UIPanelAPI.Box(
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    checkDslTarget()
    val slot = childSlot("Box")
    val (panel, plugin) = containerPanel(slot, size)
    plugin.layoutEngine = BoxLayoutEngine(plugin)

    registerComponentSizing(panel, size, isContainer = true, containerPlugin = plugin)
    slot.remember(panel, panel)

    modifier.operations.forEach { it(panel) }

    plugin.attachScope("Box", builder) { panel.builder() }
    return panel
}

private fun UIPanelAPI.StackLayout(
    direction: StackDirection,
    crossAlignValue: Float,
    size: Size,
    modifier: Modifier,
    spacing: Float,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit
): CustomPanelAPI {
    checkDslTarget()
    val kind = if (direction.isVertical) "Column" else "Row"
    val slot = childSlot(kind)
    val (panel, plugin) = containerPanel(slot, size)
    plugin.isStackLayout = true

    plugin.layoutEngine = StackLayoutEngine(
        plugin = plugin,
        direction = direction,
        crossAlignValue = crossAlignValue,
        spacing = spacing
    )

    registerComponentSizing(panel, size, isContainer = true, containerPlugin = plugin)
    slot.remember(panel, panel)

    modifier.operations.forEach { it(panel) }

    plugin.attachScope(kind, builder) { panel.builder() }
    return panel
}

/**
 * A layout container that places its children in a vertical sequence, [spacing] apart.
 * Defaults follow Jetpack Compose: children start at the left ([Alignment.Left]), the Column fills the available
 * width and wraps its content's height. [grow] = [VGrow.Upwards] stacks from the bottom instead. Space inside its
 * edges is `Modifier.padding`.
 */
fun UIPanelAPI.Column(
    size: Size = Size.fillMaxWidth(),
    modifier: Modifier = Modifier,
    grow: VGrow = VGrow.Downwards,
    horizontalAlignment: Alignment.Horizontal = Alignment.Left,
    spacing: Float = 0f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI = StackLayout(
    direction = grow.direction,
    crossAlignValue = horizontalAlignment.alignValue,
    size = size,
    modifier = modifier,
    spacing = spacing,
    builder = builder
)

/**
 * A layout container that places its children in a horizontal sequence, [spacing] apart.
 * Defaults follow Jetpack Compose: children align to the top ([Alignment.Top]), the Row wraps its content's width
 * and fills the available height. [grow] = [HGrow.Leftwards] stacks from the right instead. Space inside its edges is
 * `Modifier.padding`.
 */
fun UIPanelAPI.Row(
    size: Size = Size.fillMaxHeight(),
    modifier: Modifier = Modifier,
    grow: HGrow = HGrow.Rightwards,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    spacing: Float = 0f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI = StackLayout(
    direction = grow.direction,
    crossAlignValue = verticalAlignment.alignValue,
    size = size,
    modifier = modifier,
    spacing = spacing,
    builder = builder
)

/**
 * Creates a native Starsector [ScrollPanelAPI] with zero wrapper [CustomPanelAPI] nodes. Components called in
 * [builder] go into its content container, which scrolls: a column, [spacing] apart. A ScrollPanel has no content
 * size of its own, so its size is exact or fill. `Modifier.padding` is space inside the scrolling content.
 */
fun UIPanelAPI.ScrollPanel(
    size: BoundedSize = Size.fillMaxSize(),
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = StarUITheme.current.scrollPanel.horizontalAlignment,
    spacing: Float = 0f,
    isLeftScrollbar: Boolean = false,
    renderShadows: Boolean = StarUITheme.current.scrollPanel.renderShadows,
    maxShadowHeight: Float? = StarUITheme.current.scrollPanel.maxShadowHeight,
    shadowColor: Color? = StarUITheme.current.scrollPanel.shadowColor,
    scrollWidgetColor: Color? = StarUITheme.current.scrollPanel.scrollWidgetColor,
    scrollRailColor: Color? = StarUITheme.current.scrollPanel.scrollRailColor,
    showScrollbars: Boolean = StarUITheme.current.scrollPanel.showScrollbars,
    useMouseWheel: Boolean = StarUITheme.current.scrollPanel.useMouseWheel,
    builder: (@StarUIDsl ScrollPanelAPI).() -> Unit = {}
): ScrollPanelAPI {
    checkDslTarget()
    val slot = childSlot("ScrollPanel")
    val (initW, initH) = getInitialBounds(size)

    // 1. Spawn the genuine vanilla ScrollPanel, or keep the one from the previous build (and its scroll position)
    val reused: ScrollPanelAPI? = slot.reused()
    val vanillaScroll: ScrollPanelAPI
    if (reused != null) {
        vanillaScroll = reused
        applyReuseBounds(vanillaScroll, size)
    } else {
        val tempPanel = Global.getSettings().createCustom(initW, initH, null)
        val tempTMAPI = tempPanel.createUIElement(initW, initH, true)
        tempPanel.addUIElement(tempTMAPI)
        vanillaScroll = tempPanel.children[0] as ScrollPanelAPI
        vanillaScroll.removeComponent(tempTMAPI)
        vanillaScroll.setSize(initW, initH)
    }

    // 2. Apply declarative properties directly to the ScrollPanel
    vanillaScroll.isLeftScrollbar = isLeftScrollbar
    vanillaScroll.renderShadows = renderShadows
    maxShadowHeight?.let { vanillaScroll.maxShadowHeight = it }
    shadowColor?.let { vanillaScroll.shadowColor = it }
    scrollWidgetColor?.let { vanillaScroll.scrollWidgetColor = it }
    scrollRailColor?.let { vanillaScroll.scrollRailColor = it }
    vanillaScroll.setShowScrollbars(showScrollbars)
    vanillaScroll.setUseMWheel(useMouseWheel)

    if (reused == null) this.addComponent(vanillaScroll)

    // 3. Attach StarUIPanelPlugin directly to the ScrollPanel's internal contentContainer
    val contentContainer = vanillaScroll.contentContainer
    val contentPlugin = StarUIPanelPlugin.getOrCreate(contentContainer)
    if (reused != null) contentPlugin.resetForReuse()
    contentPlugin.isStackLayout = true
    contentPlugin.myNodeSize = NodeSize(Dimension.FillMax(1f), Dimension.WrapContent)
    contentPlugin.myPadding = PaddingValues.Zero
    contentPlugin.layoutEngine = StackLayoutEngine(
        plugin = contentPlugin,
        direction = StackDirection.DOWN,
        crossAlignValue = horizontalAlignment.alignValue,
        spacing = spacing
    )

    // 4. Attach StarUIPanelPlugin directly to the outer ScrollPanel to handle outer modifiers & layout cascade
    val outerPlugin = StarUIPanelPlugin.getOrCreate(vanillaScroll)
    outerPlugin.kind = "ScrollPanel"
    val nodeSize = size.toNodeSize()
    outerPlugin.layoutEngine = object : LayoutEngine {
        override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
            val physW = when (val wDim = nodeSize.width) {
                is Dimension.Exact -> wDim.pixels
                is Dimension.WrapContent -> {
                    containerLogger.warn("StarUI Layout Warning: ScrollPanel width was set to WrapContent. ScrollPanels require bounded constraints; defaulting to 100px.")
                    100f
                }
                else -> maxWidth
            }
            val physH = when (val hDim = nodeSize.height) {
                is Dimension.Exact -> hDim.pixels
                is Dimension.WrapContent -> {
                    containerLogger.warn("StarUI Layout Warning: ScrollPanel height was set to WrapContent. ScrollPanels require bounded constraints; defaulting to 100px.")
                    100f
                }
                else -> maxHeight
            }
            return Pair(physW, physH)
        }

        override fun layout(assignedWidth: Float, assignedHeight: Float) {
            vanillaScroll.setSize(assignedWidth, assignedHeight)
            val (contentW, contentH) = contentPlugin.layoutEngine!!.measure(assignedWidth, Float.MAX_VALUE)
            vanillaScroll.setContentSize(contentW, contentH)
            contentPlugin.layoutEngine!!.layout(contentW, contentH)
            contentPlugin.markLayoutResolved()
        }
    }

    registerComponentSizing(vanillaScroll, size, isContainer = true, containerPlugin = outerPlugin)
    slot.remember(vanillaScroll, vanillaScroll)

    // 5. Outer Modifiers apply directly to the ScrollPanel!
    modifier.operations.forEach { it(vanillaScroll) }

    // 6. Execute the builder on the ScrollPanel; vanilla adds its components to the content container
    contentPlugin.attachScope("ScrollPanel", builder) { vanillaScroll.builder() }

    return vanillaScroll
}
