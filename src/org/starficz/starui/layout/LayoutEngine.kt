package org.starficz.starui.layout

import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.modifier.PaddingValues
import org.starficz.starui.runtime.childNode
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.height
import org.starficz.starui.vanilla.setSize
import org.starficz.starui.vanilla.width

internal interface LayoutEngine {
    fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float>
    fun layout(assignedWidth: Float, assignedHeight: Float)

    /**
     * What the children need (padding included) according to the last [measure], whatever the container's own size
     * rule made of it; null when [measure] already returns that. A scrolling container sizes its content from it.
     */
    val contentSize: Pair<Float, Float>? get() = null
}

// ==========================================
// SHARED ENGINE HELPERS
// ==========================================

/**
 * A child's size with its padding in a room of [availableW] x [availableH]: its component's own measure, a nested
 * container's engine, or else its current size. NaN (a measure that could not tell) counts as 10.
 */
internal fun measureChildPaddedIntrinsic(
    child: UIComponentAPI,
    plugin: StarUIPanelPlugin,
    availableW: Float,
    availableH: Float
): Pair<Float, Float> {
    val node = childNode(child)
    val childPlugin = StarUIPanelPlugin.getAttached(child)
    // a container's measure includes its own padding; a leaf's padding is around what it measures
    val padding = if (childPlugin?.layoutEngine != null) PaddingValues.Zero else node.padding
    val contentAvailW = if (availableW == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, availableW - padding.horizontal)
    val contentAvailH = if (availableH == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, availableH - padding.vertical)

    val measure = node.measure
    val rawIntrinsic = if (measure != null) {
        measure(contentAvailW, contentAvailH)
    } else if (childPlugin?.layoutEngine != null) {
        childPlugin.measure(contentAvailW, contentAvailH)
    } else {
        Pair(child.width, child.height)
    }

    val rawW = if (rawIntrinsic.first.isNaN()) 10f else rawIntrinsic.first
    val rawH = if (rawIntrinsic.second.isNaN()) 10f else rawIntrinsic.second

    return Pair(rawW + padding.horizontal, rawH + padding.vertical)
}

/** Gives [child] its size, then runs its component's layout and, for a container, its own layout. */
internal fun commitChildLayout(
    child: UIComponentAPI,
    plugin: StarUIPanelPlugin,
    finalW: Float,
    finalH: Float
) {
    child.setSize(finalW, finalH)
    childNode(child).layout?.invoke(finalW, finalH)

    // Every child with a plugin is told, engine or not, so onLayout on a leaf fires on each pass of its container.
    val childPlugin = StarUIPanelPlugin.getAttached(child) ?: return
    val engine = childPlugin.layoutEngine
    // A container nothing changed in, given the size it already has, is already laid out: skipping it is what keeps
    // a change in one row of a long list from laying out every row.
    val clean = childPlugin.isLayoutClean && childPlugin.laidOutW == finalW && childPlugin.laidOutH == finalH
    childPlugin.beginLayout()
    if (engine != null && !clean) {
        childPlugin.laidOutW = finalW
        childPlugin.laidOutH = finalH
        engine.layout(finalW, finalH)
    }
    childPlugin.finishLayout()
}

/** A child's width / height for an aspect-ratio axis (an `ImageSize.aspectWrap()` image); 1 when it has none. */
internal fun aspectRatioOf(child: UIComponentAPI, plugin: StarUIPanelPlugin): Float {
    val ratio = childNode(child).aspectRatio?.invoke() ?: return 1f
    return if (ratio.isFinite() && ratio > 0f) ratio else 1f
}
