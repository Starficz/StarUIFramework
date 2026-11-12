package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.apache.log4j.Logger
import org.starficz.starui.compose.AspectWrapImage
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.ExactSize
import org.starficz.starui.compose.LayoutSize
import org.starficz.starui.compose.Mixed
import org.starficz.starui.compose.NativeImage
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.StarUISizing
import org.starficz.starui.compose.layout.BoxLayoutEngine
import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.layout.HGrow
import org.starficz.starui.compose.layout.HorizontalAlignment
import org.starficz.starui.compose.layout.LayoutEngine
import org.starficz.starui.compose.layout.NodeSize
import org.starficz.starui.compose.layout.StackDirection
import org.starficz.starui.compose.layout.StackLayoutEngine
import org.starficz.starui.compose.layout.VGrow
import org.starficz.starui.compose.layout.VerticalAlignment
import org.starficz.starui.compose.layout.XAlign
import org.starficz.starui.compose.layout.YAlign
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.compose.modifier.actual
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.border
import org.starficz.starui.compose.runtime.ChildSlot
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.compose.toNodeSize
import org.starficz.starui.compose.wrapPadding
import org.starficz.starui.core.BoxedScrollPanel
import org.starficz.starui.core.children
import org.starficz.starui.core.setPlugin
import org.starficz.starui.core.height
import org.starficz.starui.core.setSize
import org.starficz.starui.core.width
import java.awt.Color

@DslMarker
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
annotation class StarUIDsl

private val containerLogger: Logger = Global.getLogger(StarUIPanelPlugin::class.java)

// =====================================================================
// SIZING HELPER
// Evaluates Tick-0 dimensions for elements before the 3-Pass Solver runs.
// =====================================================================
internal fun getInitialBounds(size: StarUISizing, intrinsic: Pair<Float, Float>? = null): Pair<Float, Float> {
    val w = when (size) {
        is ExactSize -> size.width
        is Mixed -> when (val x = size.x) {
            is Axis.Exact -> x.pixels
            is Axis.Wrap -> (intrinsic?.first ?: 1f) + x.padding * 2f
            else -> 1f // Fill strictly resolves in Pass 3 Layout
        }
        is NativeImage -> size.maxWidth ?: intrinsic?.first ?: 10f
        is AspectWrapImage -> 1f
    }
    val h = when (size) {
        is ExactSize -> size.height
        is Mixed -> when (val y = size.y) {
            is Axis.Exact -> y.pixels
            is Axis.Wrap -> (intrinsic?.second ?: 1f) + y.padding * 2f
            else -> 1f
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
    val target: UIPanelAPI = (this as? BoxedScrollPanel)?.contentContainer ?: this
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
    val record = if (cursor != null && cursor.isFor((this as? BoxedScrollPanel)?.contentContainer ?: this)) {
        cursor.take(kind, key, props)
    } else null
    return ChildSlot(container, kind, key, props, record)
}

/**
 * Sizes a reused component the way a new one would start, except on an axis only the layout pass can resolve (Fill,
 * or Wrap without a known content size): that axis keeps its current size, so a reused component does not collapse
 * for a moment and a ScrollPanel does not lose its scroll position.
 */
internal fun applyReuseBounds(component: UIComponentAPI, size: StarUISizing, intrinsic: Pair<Float, Float>? = null) {
    val (initW, initH) = getInitialBounds(size, intrinsic)
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
private fun UIPanelAPI.containerPanel(slot: ChildSlot, size: StarUISizing): Pair<CustomPanelAPI, StarUIPanelPlugin> {
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

internal fun registerComponentSizing(
    component: UIComponentAPI,
    size: StarUISizing,
    isContainer: Boolean,
    containerPlugin: StarUIPanelPlugin? = null
) {
    val actual = component.actual
    val nodeSize = size.toNodeSize()
    val (wrapPadX, wrapPadY) = size.wrapPadding()

    val isManagedByParent = !StarUIContext.isTopLevel && StarUIContext.currentPlugin.layoutEngine != null

    if (isContainer && containerPlugin != null) {
        containerPlugin.myNodeSize = nodeSize
        containerPlugin.isTopLevelNode = !isManagedByParent
        if (wrapPadX != 0f || wrapPadY != 0f) {
            val existing = containerPlugin.myPadding
            containerPlugin.myPadding = PaddingValues(
                left = existing.left + wrapPadX,
                top = existing.top + wrapPadY,
                right = existing.right + wrapPadX,
                bottom = existing.bottom + wrapPadY
            )
        }
    }

    if (isManagedByParent) {
        val parentPlugin = StarUIContext.currentPlugin
        parentPlugin.childSizing[actual] = nodeSize
        if (!isContainer && (wrapPadX != 0f || wrapPadY != 0f)) {
            val existing = parentPlugin.childPadding[actual] ?: PaddingValues()
            parentPlugin.childPadding[actual] = PaddingValues(
                left = existing.left + wrapPadX,
                top = existing.top + wrapPadY,
                right = existing.right + wrapPadX,
                bottom = existing.bottom + wrapPadY
            )
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

fun UIPanelAPI.CustomPanel(
    size: PanelSize = PanelSize.wrap(),
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

fun UIPanelAPI.FramedPanel(
    size: PanelSize = PanelSize.wrap(),
    modifier: Modifier = Modifier,
    bgColor: Color = Color(15, 20, 28, 240),
    borderColor: Color = Global.getSettings().basePlayerColor,
    borderThickness: Float = 1f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    return CustomPanel(
        size = size,
        modifier = Modifier.background(bgColor).border(borderColor, borderThickness).then { c ->
            modifier.operations.forEach { op -> op(c) }
        },
        builder = builder
    )
}

@Deprecated(
    message = "Plugin blocks are deprecated in favor of the Modifier system.",
    replaceWith = ReplaceWith("modifier = Modifier.plugin { builder() }")
)
fun CustomPanelAPI.Plugin(builder: (@StarUIDsl StarUIPanelPlugin).() -> Unit): CustomUIPanelPlugin {
    val plugin = this.plugin as? StarUIPanelPlugin ?: StarUIPanelPlugin().also {
        it.customPanel = this
        this.setPlugin(it)
    }
    plugin.builder()
    return plugin
}

/**
 * A container that stacks its children on top of each other (Z-axis).
 * Use `Modifier.align(Alignment.MID)` on children to position them within the Box.
 */
fun UIPanelAPI.Box(
    size: LayoutSize = LayoutSize.wrap(),
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

internal fun UIPanelAPI.StackLayout(
    direction: StackDirection,
    crossAlignValue: Float,
    size: LayoutSize,
    modifier: Modifier,
    primaryPadding: Float,
    secondaryPadding: Float,
    spacing: Float,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit
): CustomPanelAPI {
    checkDslTarget()
    val kind = if (direction.isVertical) "Column" else "Row"
    val slot = childSlot(kind)
    val (panel, plugin) = containerPanel(slot, size)
    plugin.isStackLayout = true

    val padX = if (direction.isVertical) secondaryPadding else primaryPadding
    val padY = if (direction.isVertical) primaryPadding else secondaryPadding
    plugin.myPadding = PaddingValues(left = padX, top = padY, right = padX, bottom = padY)

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
 * A layout container that places its children in a vertical sequence.
 * Defaults follow Jetpack Compose: children start at the left ([HorizontalAlignment.Left]), the Column fills the
 * available width and wraps its content's height. [grow] = [VGrow.Upwards] stacks from the bottom instead.
 */
fun UIPanelAPI.Column(
    size: LayoutSize = LayoutSize.mixed(xAxis = Axis.Fill(), yAxis = Axis.Wrap()),
    modifier: Modifier = Modifier,
    grow: VGrow = VGrow.Downwards,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left,
    yPadding: Float = 0f,
    xPadding: Float = 0f,
    spacing: Float = 0f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI = StackLayout(
    direction = grow.direction,
    crossAlignValue = horizontalAlignment.alignValue,
    size = size,
    modifier = modifier,
    primaryPadding = yPadding,
    secondaryPadding = xPadding,
    spacing = spacing,
    builder = builder
)

/**
 * A layout container that places its children in a horizontal sequence.
 * Defaults follow Jetpack Compose: children align to the top ([VerticalAlignment.Top]), the Row wraps its content's
 * width and fills the available height. [grow] = [HGrow.Leftwards] stacks from the right instead.
 */
fun UIPanelAPI.Row(
    size: LayoutSize = LayoutSize.mixed(xAxis = Axis.Wrap(), yAxis = Axis.Fill()),
    modifier: Modifier = Modifier,
    grow: HGrow = HGrow.Rightwards,
    verticalAlignment: VerticalAlignment = VerticalAlignment.Top,
    xPadding: Float = 0f,
    yPadding: Float = 0f,
    spacing: Float = 0f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI = StackLayout(
    direction = grow.direction,
    crossAlignValue = verticalAlignment.alignValue,
    size = size,
    modifier = modifier,
    primaryPadding = xPadding,
    secondaryPadding = yPadding,
    spacing = spacing,
    builder = builder
)

/**
 * Old name for [Column]. Same engine, but it centres children by default where [Column] starts them at the left,
 * so the replacement spells the alignment out to keep existing layouts unchanged.
 */
@Deprecated(
    message = "Use Column. VerticalStackLayout centres by default; Column defaults to HorizontalAlignment.Left.",
    replaceWith = ReplaceWith("Column(size = size, modifier = modifier, grow = grow, horizontalAlignment = align, yPadding = yPadding, xPadding = xPadding, spacing = spacing, builder = builder)")
)
fun UIPanelAPI.VerticalStackLayout(
    size: LayoutSize = LayoutSize.mixed(xAxis = Axis.Fill(), yAxis = Axis.Wrap()),
    modifier: Modifier = Modifier,
    grow: VGrow = VGrow.Downwards,
    align: XAlign = XAlign.Center,
    yPadding: Float = 0f,
    xPadding: Float = 0f,
    spacing: Float = 0f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI = Column(size, modifier, grow, align, yPadding, xPadding, spacing, builder)

/**
 * Old name for [Row]. Same engine, but it centres children vertically by default where [Row] aligns them to the top,
 * so the replacement spells the alignment out to keep existing layouts unchanged.
 */
@Deprecated(
    message = "Use Row. HorizontalStackLayout centres by default; Row defaults to VerticalAlignment.Top.",
    replaceWith = ReplaceWith("Row(size = size, modifier = modifier, grow = grow, verticalAlignment = align, xPadding = xPadding, yPadding = yPadding, spacing = spacing, builder = builder)")
)
fun UIPanelAPI.HorizontalStackLayout(
    size: LayoutSize = LayoutSize.mixed(xAxis = Axis.Wrap(), yAxis = Axis.Fill()),
    modifier: Modifier = Modifier,
    grow: HGrow = HGrow.Rightwards,
    align: YAlign = YAlign.Center,
    xPadding: Float = 0f,
    yPadding: Float = 0f,
    spacing: Float = 0f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI = Row(size, modifier, grow, align, xPadding, yPadding, spacing, builder)

typealias StarUIScrollPanel = BoxedScrollPanel

/**
 * Creates a native Starsector [ScrollPanelAPI] wrapped in [BoxedScrollPanel] (which implements both
 * [ScrollPanelAPI] and [UIPanelAPI]), with zero wrapper [CustomPanelAPI] nodes.
 */
fun UIPanelAPI.ScrollPanel(
    size: PanelSize = PanelSize.fill(1f),
    modifier: Modifier = Modifier,
    align: XAlign = XAlign.Center,
    yPadding: Float = 0f,
    xPadding: Float = 0f,
    spacing: Float = 0f,
    isLeftScrollbar: Boolean = false,
    renderShadows: Boolean = StarUITheme.current.scrollPanel.renderShadows,
    maxShadowHeight: Float? = StarUITheme.current.scrollPanel.maxShadowHeight,
    shadowColor: Color? = StarUITheme.current.scrollPanel.shadowColor,
    scrollWidgetColor: Color? = StarUITheme.current.scrollPanel.scrollWidgetColor,
    scrollRailColor: Color? = StarUITheme.current.scrollPanel.scrollRailColor,
    showScrollbars: Boolean = StarUITheme.current.scrollPanel.showScrollbars,
    useMouseWheel: Boolean = StarUITheme.current.scrollPanel.useMouseWheel,
    builder: (@StarUIDsl BoxedScrollPanel).() -> Unit = {}
): BoxedScrollPanel {
    checkDslTarget()
    val slot = childSlot("ScrollPanel")
    val (initW, initH) = getInitialBounds(size)

    // 1. Spawn the genuine vanilla ScrollPanel, or keep the one from the previous build (and its scroll position)
    val reused: BoxedScrollPanel? = slot.reused()
    val boxedScroll: BoxedScrollPanel
    val vanillaScroll: ScrollPanelAPI
    if (reused != null) {
        boxedScroll = reused
        vanillaScroll = reused.scrollPanel
        applyReuseBounds(vanillaScroll, size)
    } else {
        val tempPanel = Global.getSettings().createCustom(initW, initH, null)
        val tempTMAPI = tempPanel.createUIElement(initW, initH, true)
        tempPanel.addUIElement(tempTMAPI)
        vanillaScroll = tempPanel.children[0] as ScrollPanelAPI
        vanillaScroll.removeComponent(tempTMAPI)

        boxedScroll = BoxedScrollPanel(vanillaScroll)
        boxedScroll.setSize(initW, initH)
    }

    // 2. Apply declarative properties directly to the ScrollPanel
    boxedScroll.isLeftScrollbar = isLeftScrollbar
    boxedScroll.renderShadows = renderShadows
    maxShadowHeight?.let { boxedScroll.maxShadowHeight = it }
    shadowColor?.let { boxedScroll.shadowColor = it }
    scrollWidgetColor?.let { boxedScroll.scrollWidgetColor = it }
    scrollRailColor?.let { boxedScroll.scrollRailColor = it }
    boxedScroll.setShowScrollbars(showScrollbars)
    boxedScroll.setUseMWheel(useMouseWheel)

    if (reused == null) this.addComponent(vanillaScroll)

    // 3. Attach StarUIPanelPlugin directly to the ScrollPanel's internal contentContainer
    val contentContainer = boxedScroll.contentContainer
    val contentPlugin = StarUIPanelPlugin.getOrCreate(contentContainer)
    if (reused != null) contentPlugin.resetForReuse()
    contentPlugin.isStackLayout = true
    contentPlugin.myNodeSize = NodeSize(Dimension.FillMax(1f), Dimension.WrapContent)
    contentPlugin.myPadding = PaddingValues(left = xPadding, top = yPadding, right = xPadding, bottom = yPadding)
    contentPlugin.layoutEngine = StackLayoutEngine(
        plugin = contentPlugin,
        direction = StackDirection.DOWN,
        crossAlignValue = align.alignValue,
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
            boxedScroll.setSize(assignedWidth, assignedHeight)
            val (contentW, contentH) = contentPlugin.layoutEngine!!.measure(assignedWidth, Float.MAX_VALUE)
            boxedScroll.setContentSize(contentW, contentH)
            contentPlugin.layoutEngine!!.layout(contentW, contentH)
            contentPlugin.markLayoutResolved()
        }
    }

    registerComponentSizing(vanillaScroll, size, isContainer = true, containerPlugin = outerPlugin)
    slot.remember(vanillaScroll, boxedScroll)

    // 5. Outer Modifiers apply directly to the ScrollPanel!
    modifier.operations.forEach { it(boxedScroll) }

    // 6. Execute builder scoped directly to BoxedScrollPanel!
    contentPlugin.attachScope("ScrollPanel", builder) { boxedScroll.builder() }

    return boxedScroll
}
