package org.starficz.starui.compose.layout

import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.height
import org.starficz.starui.core.setSize
import org.starficz.starui.core.width

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
 * Shared logic to safely measure a child's intrinsic size, including its padding,
 * and handling fallbacks for NaN or nested LayoutEngines.
 */
internal fun measureChildPaddedIntrinsic(
    child: UIComponentAPI,
    plugin: StarUIPanelPlugin,
    availableW: Float,
    availableH: Float
): Pair<Float, Float> {
    val padding = plugin.childPadding[child] ?: PaddingValues()
    val contentAvailW = if (availableW == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, availableW - padding.horizontal)
    val contentAvailH = if (availableH == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, availableH - padding.vertical)

    val childPlugin = StarUIPanelPlugin.getAttached(child)
    val rawIntrinsic = if (plugin.childMeasurables.containsKey(child)) {
        plugin.childMeasurables[child]!!.invoke(contentAvailW, contentAvailH)
    } else if (childPlugin?.layoutEngine != null) {
        childPlugin.layoutEngine!!.measure(contentAvailW, contentAvailH)
    } else {
        Pair(child.width, child.height)
    }

    val rawW = if (rawIntrinsic.first.isNaN()) 10f else rawIntrinsic.first
    val rawH = if (rawIntrinsic.second.isNaN()) 10f else rawIntrinsic.second

    return Pair(rawW + padding.horizontal, rawH + padding.vertical)
}

/**
 * Shared logic to physically apply sizes and trigger downward layout cascades.
 */
internal fun commitChildLayout(
    child: UIComponentAPI,
    plugin: StarUIPanelPlugin,
    finalW: Float,
    finalH: Float
) {
    child.setSize(finalW, finalH)
    plugin.childLayoutDelegates[child]?.invoke(finalW, finalH)

    // Every child with a plugin is told, engine or not, so onLayout on a leaf fires on each pass of its container.
    val childPlugin = StarUIPanelPlugin.getAttached(child) ?: return
    childPlugin.layoutEngine?.layout(finalW, finalH)
    childPlugin.markLayoutResolved()
}
