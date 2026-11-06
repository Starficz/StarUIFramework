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
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.compose.toNodeSize
import org.starficz.starui.compose.wrapPadding
import org.starficz.starui.core.BoxedScrollPanel
import org.starficz.starui.core.children
import org.starficz.starui.core.setPlugin
import org.starficz.starui.core.setSize
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

internal fun registerComponentSizing(
    component: UIComponentAPI,
    size: StarUISizing,
    isContainer: Boolean,
    containerPlugin: StarUIPanelPlugin? = null
) {
    val actual = component.actual
    val nodeSize = size.toNodeSize()
    val (wrapPadX, wrapPadY) = size.wrapPadding()

    if (isContainer && containerPlugin != null) {
        containerPlugin.myNodeSize = nodeSize
        containerPlugin.isTopLevelNode = StarUIContext.isTopLevel
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

    if (!StarUIContext.isTopLevel) {
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
    val plugin = StarUIPanelPlugin()
    val (initW, initH) = getInitialBounds(size)

    val panel = Global.getSettings().createCustom(initW, initH, plugin)
    plugin.customPanel = panel

    this.addComponent(panel)
    registerComponentSizing(panel, size, isContainer = true, containerPlugin = plugin)

    modifier.operations.forEach { it(panel) }

    StarUIContext.withPlugin(plugin) { panel.builder() }
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
    val plugin = StarUIPanelPlugin()
    val (initW, initH) = getInitialBounds(size)

    val panel = Global.getSettings().createCustom(initW, initH, plugin)
    plugin.customPanel = panel
    plugin.layoutEngine = BoxLayoutEngine(plugin)

    this.addComponent(panel)
    registerComponentSizing(panel, size, isContainer = true, containerPlugin = plugin)

    modifier.operations.forEach { it(panel) }

    StarUIContext.withPlugin(plugin) { panel.builder() }
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
    val plugin = StarUIPanelPlugin()
    plugin.isStackLayout = true
    val (initW, initH) = getInitialBounds(size)

    val panel = Global.getSettings().createCustom(initW, initH, plugin)
    plugin.customPanel = panel

    val padX = if (direction.isVertical) secondaryPadding else primaryPadding
    val padY = if (direction.isVertical) primaryPadding else secondaryPadding
    plugin.myPadding = PaddingValues(left = padX, top = padY, right = padX, bottom = padY)

    plugin.layoutEngine = StackLayoutEngine(
        plugin = plugin,
        direction = direction,
        crossAlignValue = crossAlignValue,
        spacing = spacing
    )

    this.addComponent(panel)
    registerComponentSizing(panel, size, isContainer = true, containerPlugin = plugin)

    modifier.operations.forEach { it(panel) }

    StarUIContext.withPlugin(plugin) { panel.builder() }
    return panel
}

/**
 * A layout container that places its children in a vertical sequence.
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

fun UIPanelAPI.VerticalStackLayout(
    size: LayoutSize = LayoutSize.mixed(xAxis = Axis.Fill(), yAxis = Axis.Wrap()),
    modifier: Modifier = Modifier,
    grow: VGrow = VGrow.Downwards,
    align: XAlign = XAlign.Center,
    yPadding: Float = 0f,
    xPadding: Float = 0f,
    spacing: Float = 0f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI = StackLayout(
    direction = grow.direction,
    crossAlignValue = align.alignValue,
    size = size,
    modifier = modifier,
    primaryPadding = yPadding,
    secondaryPadding = xPadding,
    spacing = spacing,
    builder = builder
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
): CustomPanelAPI = StackLayout(
    direction = grow.direction,
    crossAlignValue = align.alignValue,
    size = size,
    modifier = modifier,
    primaryPadding = xPadding,
    secondaryPadding = yPadding,
    spacing = spacing,
    builder = builder
)

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
    val (initW, initH) = getInitialBounds(size)

    // 1. Spawn the genuine vanilla ScrollPanel
    val tempPanel = Global.getSettings().createCustom(initW, initH, null)
    val tempTMAPI = tempPanel.createUIElement(initW, initH, true)
    tempPanel.addUIElement(tempTMAPI)
    val vanillaScroll = tempPanel.children[0] as ScrollPanelAPI
    vanillaScroll.removeComponent(tempTMAPI)

    val boxedScroll = BoxedScrollPanel(vanillaScroll)
    boxedScroll.setSize(initW, initH)

    // 2. Apply declarative properties directly to the ScrollPanel
    boxedScroll.isLeftScrollbar = isLeftScrollbar
    boxedScroll.renderShadows = renderShadows
    maxShadowHeight?.let { boxedScroll.maxShadowHeight = it }
    shadowColor?.let { boxedScroll.shadowColor = it }
    scrollWidgetColor?.let { boxedScroll.scrollWidgetColor = it }
    scrollRailColor?.let { boxedScroll.scrollRailColor = it }
    boxedScroll.setShowScrollbars(showScrollbars)
    boxedScroll.setUseMWheel(useMouseWheel)

    this.addComponent(vanillaScroll)

    // 3. Attach StarUIPanelPlugin directly to the ScrollPanel's internal contentContainer
    val contentContainer = boxedScroll.contentContainer
    val contentPlugin = StarUIPanelPlugin.getOrCreate(contentContainer)
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

    // 5. Outer Modifiers apply directly to the ScrollPanel!
    modifier.operations.forEach { it(boxedScroll) }

    // 6. Execute builder scoped directly to BoxedScrollPanel!
    StarUIContext.withPlugin(contentPlugin) {
        boxedScroll.builder()
    }

    return boxedScroll
}
