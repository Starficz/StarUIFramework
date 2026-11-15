package org.starficz.starui.layout

import org.starficz.starui.Anchor
import org.starficz.starui.AspectRatioAxis
import org.starficz.starui.Axis
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.runtime.nodeOf
import org.starficz.starui.vanilla.applyAnchor
import org.starficz.starui.vanilla.relativeToBase
import kotlin.math.abs

/**
 * A CustomPanel's engine: its children place themselves by `Modifier.anchor`; this sizes them (a fill child takes the
 * panel's size on that axis) and the panel itself.
 *
 * Wrapping, the panel is as large as needed for each child to reach its far edge: a child anchored to the panel's
 * left (top) edge needs its offset plus its size; one anchored to the panel's centre or far edge needs its size plus
 * its offset (it moves with the panel's size); one anchored to a sibling is measured where it is. A StarUI child
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
            when {
                anchor == null -> {
                    needW = maxOf(needW, w + margin.horizontal)
                    needH = maxOf(needH, h + margin.vertical)
                }
                anchor.reference is Anchor.AnchorReference.Parent -> {
                    // x: from the left edge, the child's left is targetX * w + offsetX; elsewhere it moves with the width
                    needW = maxOf(needW, if (anchor.refAnchorX == 0f) anchor.targetAnchorX * w + anchor.offsetX + w
                                         else w + abs(anchor.offsetX))
                    // y is up: from the top edge, the child's top is -(targetY * h + offsetY) down from it
                    needH = maxOf(needH, if (anchor.refAnchorY == 1f) -(anchor.targetAnchorY * h + anchor.offsetY)
                                         else h + abs(anchor.offsetY))
                }
                else -> {
                    // anchored to a sibling: where it is now (the sibling places it)
                    needW = maxOf(needW, child.position.x - panel.position.x + w)
                    needH = maxOf(needH, panel.position.y + panel.position.height - child.position.y - child.position.height + h)
                }
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
