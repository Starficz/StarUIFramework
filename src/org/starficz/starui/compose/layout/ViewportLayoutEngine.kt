package org.starficz.starui.compose.layout

import com.fs.starfarer.api.ui.ScrollPanelAPI
import org.starficz.starui.compose.AxisOverflow
import org.starficz.starui.compose.Overflow
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.getChildrenNonCopy
import org.starficz.starui.core.setSize
import kotlin.math.abs

/**
 * The layout of a scrolling container's vanilla scroll panel ([host]): sizes the panel by the container's `size`
 * ([size]), then lays the StarUI container inside it ([content]) out at the content size.
 *
 * Per axis, with the visible size (the viewport) known:
 * - `Scroll`: the children are measured with the viewport as their room; the content is max(viewport, what they
 *   need), so a fill child gets what is left of the viewport (0 once the content overflows);
 * - `Clip`: the content is the viewport.
 * A wrapping axis has no viewport: the panel takes what the children need there and never scrolls on it.
 */
internal class ViewportLayoutEngine(
    private val host: ScrollPanelAPI,
    private val content: StarUIPanelPlugin,
    private val overflow: Overflow,
    private val size: NodeSize
) : LayoutEngine {

    /**
     * True when the scroll panel's own size does not depend on its content (exact or fill on both axes): a change
     * inside then only needs the content laid out again, not everything above it.
     */
    val isLayoutBoundary: Boolean = size.width !is Dimension.WrapContent && size.width !is Dimension.AspectRatio &&
        size.height !is Dimension.WrapContent && size.height !is Dimension.AspectRatio

    /** The viewport the content was last measured at by [measure], when both axes were known; else null. */
    private var measuredAt: Pair<Float, Float>? = null
    private var needed = Pair(0f, 0f)

    private fun viewportAxis(dimension: Dimension, room: Float): Float? = when (dimension) {
        is Dimension.Exact -> dimension.pixels
        is Dimension.FillMax -> if (room == Float.MAX_VALUE) null else room
        else -> null
    }

    /** Measures the children with the content container [exactW] x [exactH] (null: wrapping), into [needed]. */
    private fun measureContent(roomW: Float, roomH: Float, exactW: Float?, exactH: Float?) {
        content.myNodeSize = NodeSize(
            exactW?.let { Dimension.Exact(it) } ?: Dimension.WrapContent,
            exactH?.let { Dimension.Exact(it) } ?: Dimension.WrapContent
        )
        val engine = content.layoutEngine
        needed = if (engine != null) {
            val measured = content.measure(roomW, roomH)
            engine.contentSize ?: measured
        } else {
            // A CustomPanel places its children by anchors: what it needs is how far they reach from its top left.
            if (exactW != null && exactH != null) content.panel.setSize(exactW, exactH)
            val p = content.panel.position
            var w = 0f
            var h = 0f
            for (child in content.panel.getChildrenNonCopy()) {
                val c = child.position
                w = maxOf(w, c.x + c.width - p.x)
                h = maxOf(h, p.y + p.height - c.y)
            }
            Pair(w, h)
        }
    }

    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        val viewW = viewportAxis(size.width, maxWidth)
        val viewH = viewportAxis(size.height, maxHeight)
        measureContent(viewW ?: maxWidth, viewH ?: maxHeight, viewW, viewH)
        measuredAt = if (viewW != null && viewH != null) Pair(viewW, viewH) else null
        return Pair(viewW ?: needed.first, viewH ?: needed.second)
    }

    override fun layout(assignedWidth: Float, assignedHeight: Float) {
        host.setSize(assignedWidth, assignedHeight)
        val viewW = assignedWidth
        val viewH = assignedHeight
        val at = measuredAt
        if (at == null || abs(at.first - viewW) > 0.5f || abs(at.second - viewH) > 0.5f) {
            measureContent(viewW, viewH, viewW, viewH)
        }
        measuredAt = null

        val contentW = if (overflow.horizontal == AxisOverflow.Scroll) maxOf(viewW, needed.first) else viewW
        val contentH = if (overflow.vertical == AxisOverflow.Scroll) maxOf(viewH, needed.second) else viewH

        // A Column's children measure the same at any Column height (a fill child gets 0 either way once the content
        // overflows), and a Row's at any Row width; any other growth changes what the children were measured at.
        val stack = content.layoutEngine as? StackLayoutEngine
        val widthMatters = content.layoutEngine != null && !(stack != null && !stack.direction.isVertical)
        val heightMatters = content.layoutEngine != null && !(stack != null && stack.direction.isVertical)
        if ((widthMatters && contentW > viewW + 0.5f) || (heightMatters && contentH > viewH + 0.5f)) {
            measureContent(contentW, contentH, contentW, contentH)
        }

        content.myNodeSize = NodeSize(Dimension.Exact(contentW), Dimension.Exact(contentH))
        content.panel.setSize(contentW, contentH)
        host.setContentSize(contentW, contentH)
        content.beginLayout()
        content.laidOutW = contentW
        content.laidOutH = contentH
        content.layoutEngine?.layout(contentW, contentH)
        content.finishLayout()
        StarUIPanelPlugin.getAttached(host.contentContainer)?.markLayoutResolved()

        // A list that got shorter must not stay scrolled past its end. x offsets are 0 or less (content moves left),
        // y offsets 0 or more.
        val minX = minOf(0f, viewW - contentW)
        val maxY = maxOf(0f, contentH - viewH)
        val x = host.xOffset.coerceIn(minX, 0f)
        val y = host.yOffset.coerceIn(0f, maxY)
        if (x != host.xOffset || y != host.yOffset) host.setOffset(x, y)
    }
}

/**
 * On a scroll panel's content container: lets layout requests from the StarUI container inside pass up to the
 * scroll panel. The scroll panel's [ViewportLayoutEngine] does the work.
 */
internal object PassThroughLayoutEngine : LayoutEngine {
    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> = Pair(0f, 0f)
    override fun layout(assignedWidth: Float, assignedHeight: Float) {}
}
