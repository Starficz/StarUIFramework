package org.starficz.starui.layout

import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.Anchor
import org.starficz.starui.AspectRatioAxis
import org.starficz.starui.Axis
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.runtime.nodeOf
import org.starficz.starui.vanilla.applyAnchor
import org.starficz.starui.vanilla.relativeToBase
import java.util.IdentityHashMap
import kotlin.math.abs

/**
 * A CustomPanel's engine: its children place themselves by `Modifier.anchor`; this sizes them (a fill child takes the
 * panel's size on that axis) and the panel itself.
 *
 * Wrapping, the panel is as large as needed for each child to reach its far edge: a child anchored to the panel's
 * left (top) edge needs its offset plus its size; one anchored to the panel's centre or far edge needs its size plus
 * its offset (it moves with the panel's size); one anchored to an earlier sibling is placed from that sibling's place
 * (a chain from the panel's top left resolves in one pass), and else measured where it is. A StarUI child
 * without an anchor sits at the top left. Components added by hand with no StarUI node keep their size and place and
 * count where they are.
 *
 * Anchors are measured from the panel's own edges, padding not included (an anchor's spacing is the gap).
 */
internal class AnchorLayoutEngine(private val plugin: StarUIPanelPlugin) : LayoutEngine {

    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        val panel = plugin.panel
        var needW = 0f
        var needH = 0f
        // children with a place that does not depend on the panel's size, for siblings anchored to them
        val rects = IdentityHashMap<UIComponentAPI, FloatArray>()
        for (child in panel.getChildrenCopy2()) {
            val node = nodeOf(child)
            if (node == null) {
                // placed by hand: it reaches where it is now
                needW = maxOf(needW, child.position.x - panel.position.x + child.position.width)
                needH = maxOf(needH, panel.position.y + panel.position.height - child.position.y)
                continue
            }
            val margin = node.margin
            val (iw, ih) = measureChildPaddedIntrinsic(child, plugin,
                if (maxWidth == Float.MAX_VALUE) maxWidth else maxOf(0f, maxWidth - margin.horizontal),
                if (maxHeight == Float.MAX_VALUE) maxHeight else maxOf(0f, maxHeight - margin.vertical))
            // a fill child takes what the panel ends up with: it asks for nothing
            val w = when (val d = node.size.width) {
                is Axis.Exact -> d.pixels
                is Axis.FillMax -> 0f
                else -> iw
            }
            val h = when (val d = node.size.height) {
                is Axis.Exact -> d.pixels
                is Axis.FillMax -> 0f
                else -> ih
            }
            val anchor = node.anchor
            // The child's rectangle where it can be worked out without the panel's size: x right and y down from the
            // panel's top left. Vanilla places a component at ref + refAnchor * refSize + targetAnchor * size + offset
            // (y up), which top-down is y = refTop + (1 - refAnchorY) * refH - (1 + targetAnchorY) * h - offsetY.
            val rect: FloatArray? = when {
                anchor == null -> floatArrayOf(margin.left, margin.top, w, h)
                anchor.reference is Anchor.AnchorReference.Parent -> {
                    val x = if (anchor.refAnchorX == 0f) anchor.targetAnchorX * w + anchor.offsetX else null
                    val y = if (anchor.refAnchorY == 1f) -(1f + anchor.targetAnchorY) * h - anchor.offsetY else null
                    // on an axis anchored to the centre or the far edge it moves with the panel: it needs its size
                    // plus its offset there, and has no fixed place
                    if (x == null) needW = maxOf(needW, w + abs(anchor.offsetX))
                    if (y == null) needH = maxOf(needH, h + abs(anchor.offsetY))
                    if (x != null && y != null) floatArrayOf(x, y, w, h) else {
                        if (x != null) needW = maxOf(needW, x + w)
                        if (y != null) needH = maxOf(needH, y + h)
                        null
                    }
                }
                else -> {
                    val sibling = (anchor.reference as? Anchor.AnchorReference.Sibling)?.component
                    val r = sibling?.let { rects[it] }
                    if (r != null) floatArrayOf(
                        r[0] + anchor.refAnchorX * r[2] + anchor.targetAnchorX * w + anchor.offsetX,
                        r[1] + (1f - anchor.refAnchorY) * r[3] - (1f + anchor.targetAnchorY) * h - anchor.offsetY,
                        w, h)
                    else floatArrayOf(   // a sibling with no fixed place (or not this panel's): where the child is now
                        child.position.x - panel.position.x,
                        panel.position.y + panel.position.height - child.position.y - child.position.height,
                        w, h)
                }
            }
            if (rect != null) {
                rects[child] = rect
                needW = maxOf(needW, rect[0] + rect[2])
                needH = maxOf(needH, rect[1] + rect[3])
            }
        }
        return Pair(maxOf(0f, needW), maxOf(0f, needH))
    }

    override fun layout(assignedWidth: Float, assignedHeight: Float) {
        for (child in plugin.panel.getChildrenCopy2()) {
            val node = nodeOf(child) ?: continue   // placed by hand: left as it is
            val margin = node.margin
            val roomW = maxOf(0f, assignedWidth - margin.horizontal)
            val roomH = maxOf(0f, assignedHeight - margin.vertical)
            val (iw, ih) = measureChildPaddedIntrinsic(child, plugin, roomW, roomH)
            val w = when (val d = node.size.width) {
                is Axis.Exact -> d.pixels
                is Axis.FillMax -> roomW
                is Axis.WrapContent -> iw
                is AspectRatioAxis -> (when (val hd = node.size.height) {
                    is Axis.Exact -> hd.pixels
                    is Axis.FillMax -> roomH
                    else -> ih
                }) * aspectRatioOf(child, plugin)
            }
            val h = when (val d = node.size.height) {
                is Axis.Exact -> d.pixels
                is Axis.FillMax -> roomH
                is Axis.WrapContent -> ih
                is AspectRatioAxis -> w / aspectRatioOf(child, plugin)
            }
            commitChildLayout(child, plugin, w, h)
            val anchor = node.anchor
            if (anchor != null) child.applyAnchor(anchor)
            // no anchor: the top left, as layouts read (vanilla's default is the bottom left), its margin in from it
            else child.position.relativeToBase(null, 0f, 1f, 0f, -1f, margin.left, -margin.top)
        }
    }
}

/** Whether children of a container with [engine] place themselves (anchors) rather than being placed by it. */
internal fun childrenPlaceThemselves(engine: LayoutEngine?): Boolean = engine == null || engine is AnchorLayoutEngine
