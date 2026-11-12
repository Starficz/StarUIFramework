package org.starficz.starui.compose.layout

import com.fs.starfarer.api.Global
import org.starficz.starui.compose.Alignment
import com.fs.starfarer.api.ui.UIComponentAPI
import org.apache.log4j.Logger
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.modifier.Margin
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.core.relativeToBase
import org.starficz.starui.core.getChildrenCopy

internal class BoxLayoutEngine(private val plugin: StarUIPanelPlugin) : LayoutEngine {

    companion object {
        val log: Logger = Global.getLogger(BoxLayoutEngine::class.java)
        var DEBUG_LAYOUT = false
    }

    private fun debugLog(msg: String) {
        if (DEBUG_LAYOUT) log.info(msg)
    }

    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        val debugId = "Box@${Integer.toHexString(plugin.component.hashCode())}"

        debugLog("[$debugId] === MEASURE START ===")
        debugLog("[$debugId] Inherited Envelope Bounds -> maxW: $maxWidth, maxH: $maxHeight")

        var maxW = 0f
        var maxH = 0f

        val containerPadding = plugin.myPadding

        // --- PADDING OVERFLOW WARNINGS ---
        if (maxWidth != Float.MAX_VALUE && maxWidth < containerPadding.horizontal) {
            log.warn("StarUI Layout Warning: Container horizontal padding (${containerPadding.horizontal}) exceeds explicit maximum bound ($maxWidth). Available space clamped to 0.")
        }
        if (maxHeight != Float.MAX_VALUE && maxHeight < containerPadding.vertical) {
            log.warn("StarUI Layout Warning: Container vertical padding (${containerPadding.vertical}) exceeds explicit maximum bound ($maxHeight). Available space clamped to 0.")
        }

        val innerAvailableW = if (maxWidth == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, maxWidth - containerPadding.horizontal)
        val innerAvailableH = if (maxHeight == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, maxHeight - containerPadding.vertical)

        val children = plugin.panel.getChildrenCopy()

        // ==========================================
        // PASS 1: Intrinsic Scrape & Infinite Bounds Check
        // ==========================================
        debugLog("[$debugId] --- PASS 1: Intrinsic Scrape ---")
        children.forEachIndexed { i, child ->
            val sizeConfig = plugin.childSizing[child] ?: NodeSize()

            // --- THE HARD MATH EXCEPTIONS ---
            if (innerAvailableW == Float.MAX_VALUE && sizeConfig.width is Dimension.FillMax) {
                throw IllegalStateException("StarUI Layout Exception: a child fills the width of a Box that has no bounded width (nothing above gives it one). Use WrapContent or Exact for the child, or give the Box a size.")
            }
            if (innerAvailableH == Float.MAX_VALUE && sizeConfig.height is Dimension.FillMax) {
                throw IllegalStateException("StarUI Layout Exception: a child fills the height of a Box that has no bounded height (nothing above gives it one). Use WrapContent or Exact for the child, or give the Box a size.")
            }

            // A child's margin is space outside it: it gets that much less room, and the Box wraps around it too
            val margin = plugin.childMargins[child] ?: Margin()
            val childAvailableW = if (innerAvailableW == Float.MAX_VALUE) innerAvailableW else maxOf(0f, innerAvailableW - margin.horizontal)
            val childAvailableH = if (innerAvailableH == Float.MAX_VALUE) innerAvailableH else maxOf(0f, innerAvailableH - margin.vertical)

            // Shared helper handles raw measurement + child padding + NaN fallbacks
            val (paddedIntrinsicW, paddedIntrinsicH) = measureChildPaddedIntrinsic(child, plugin, childAvailableW, childAvailableH)

            debugLog("  [$debugId] Child[$i] (${child.javaClass.simpleName}) | Sizing: $sizeConfig | Padded Intrinsic: $paddedIntrinsicW x $paddedIntrinsicH")

            // FillMax elements do not contribute to a Box's intrinsic size; they wait for the Box to size itself
            val childW = when (sizeConfig.width) {
                is Dimension.Exact -> sizeConfig.width.pixels + margin.horizontal
                is Dimension.WrapContent, is Dimension.AspectRatio -> paddedIntrinsicW + margin.horizontal
                is Dimension.FillMax -> 0f
            }
            val childH = when (sizeConfig.height) {
                is Dimension.Exact -> sizeConfig.height.pixels + margin.vertical
                is Dimension.WrapContent, is Dimension.AspectRatio -> paddedIntrinsicH + margin.vertical
                is Dimension.FillMax -> 0f
            }

            debugLog("  [$debugId] Child[$i] contributed bounds: W=$childW, H=$childH")

            maxW = maxOf(maxW, childW)
            maxH = maxOf(maxH, childH)
        }

        // --- ALL-FILL PARADOX WARNING ---
        if (plugin.myNodeSize.width is Dimension.WrapContent && maxW == 0f && children.isNotEmpty()) {
            if (children.all { (plugin.childSizing[it] ?: NodeSize()).width is Dimension.FillMax }) {
                log.warn("StarUI Layout Paradox: A Box set to WrapContent width contains only children set to FillMax width. The Box has no intrinsic width to wrap around, causing it to collapse to 0.")
            }
        }
        if (plugin.myNodeSize.height is Dimension.WrapContent && maxH == 0f && children.isNotEmpty()) {
            if (children.all { (plugin.childSizing[it] ?: NodeSize()).height is Dimension.FillMax }) {
                log.warn("StarUI Layout Paradox: A Box set to WrapContent height contains only children set to FillMax height. The Box has no intrinsic height to wrap around, causing it to collapse to 0.")
            }
        }

        val finalIntrinsicW = maxW + containerPadding.horizontal
        val finalIntrinsicH = maxH + containerPadding.vertical

        debugLog("[$debugId] MEASURE END | Final Intrinsic Bounds: $finalIntrinsicW x $finalIntrinsicH")
        return Pair(finalIntrinsicW, finalIntrinsicH)
    }

    override fun layout(assignedWidth: Float, assignedHeight: Float) {
        val debugId = "Box@${Integer.toHexString(plugin.component.hashCode())}"

        debugLog("[$debugId] === LAYOUT START ===")
        debugLog("[$debugId] Assigned Bounds: W=$assignedWidth, H=$assignedHeight")

        val containerPadding = plugin.myPadding
        val innerAvailableW = maxOf(0f, assignedWidth - containerPadding.horizontal)
        val innerAvailableH = maxOf(0f, assignedHeight - containerPadding.vertical)

        val children = plugin.panel.getChildrenCopy()

        // ==========================================
        // FINAL ASSIGNMENT & SIZING
        // ==========================================
        debugLog("[$debugId] --- FINAL COMPONENT SIZING ASSIGNMENT ---")
        children.forEachIndexed { i, child ->
            val sizeConfig = plugin.childSizing[child] ?: NodeSize()

            // The room this child has: the Box's inside minus the child's own margin (applyBoxAlignment offsets it)
            val margin = plugin.childMargins[child] ?: Margin()
            val childAvailableW = maxOf(0f, innerAvailableW - margin.horizontal)
            val childAvailableH = maxOf(0f, innerAvailableH - margin.vertical)

            // Re-measure intrinsic in case it depends on the final layout bounds
            val (paddedIntrinsicW, paddedIntrinsicH) = measureChildPaddedIntrinsic(child, plugin, childAvailableW, childAvailableH)

            val finalW = when (sizeConfig.width) {
                is Dimension.Exact -> sizeConfig.width.pixels
                is Dimension.FillMax -> childAvailableW
                is Dimension.WrapContent -> paddedIntrinsicW
                is Dimension.AspectRatio -> {
                    val baseH = when (sizeConfig.height) {
                        is Dimension.Exact -> sizeConfig.height.pixels
                        is Dimension.FillMax -> childAvailableH
                        else -> paddedIntrinsicH
                    }
                    val rawIntrinsic = plugin.childMeasurables[child]?.invoke(childAvailableW, childAvailableH) ?: Pair(1f, 1f)
                    val ar = if (rawIntrinsic.second == 0f) 1f else rawIntrinsic.first / rawIntrinsic.second
                    baseH * (if (ar.isNaN() || ar.isInfinite() || ar == 0f) 1f else ar)
                }
            }

            val finalH = when (sizeConfig.height) {
                is Dimension.Exact -> sizeConfig.height.pixels
                is Dimension.FillMax -> childAvailableH
                is Dimension.WrapContent -> paddedIntrinsicH
                is Dimension.AspectRatio -> {
                    val rawIntrinsic = plugin.childMeasurables[child]?.invoke(childAvailableW, childAvailableH) ?: Pair(1f, 1f)
                    val ar = if (rawIntrinsic.second == 0f) 1f else rawIntrinsic.first / rawIntrinsic.second
                    finalW / (if (ar.isNaN() || ar.isInfinite() || ar == 0f) 1f else ar)
                }
            }

            debugLog("  [$debugId] Child[$i] Final Physical Size -> W: $finalW x H: $finalH")

            // Shared helper sets the size and cascades layout calls downwards to nested containers
            commitChildLayout(child, plugin, finalW, finalH)

            // ==========================================
            // POSITIONING
            // ==========================================
            val alignment = plugin.childAlignments[child] ?: Alignment.TopLeft
            applyBoxAlignment(child, alignment, containerPadding, debugId, i)
        }

        debugLog("[$debugId] LAYOUT END")
    }

    private fun applyBoxAlignment(child: UIComponentAPI, alignment: Alignment, containerPadding: PaddingValues, debugId: String, childIndex: Int) {
        var offsetX = 0f
        var offsetY = 0f

        val alignX = alignment.x
        val alignY = alignment.y

        // 1. Margins push the child away from its aligned edge
        plugin.childMargins[child]?.let { margin ->
            offsetX += margin.left * (1f - alignX) - margin.right * alignX
            offsetY += margin.bottom * (1f - alignY) - margin.top * alignY
        }

        // 2. Container Padding squeezes the aligned edge inwards
        offsetX += containerPadding.left * (1f - alignX) - containerPadding.right * alignX
        offsetY += containerPadding.bottom * (1f - alignY) - containerPadding.top * alignY

        debugLog("  [$debugId] Child[$childIndex] anchored to Box bounds (AlignX: $alignX, AlignY: $alignY) | Offset X: $offsetX, Y: $offsetY")

        // 3. Bake coordinates for Starsector's relativeTo method
        val targetAnchorX = alignX
        val targetAnchorY = alignY
        val myAnchorX = -alignX
        val myAnchorY = -alignY

        child.position.relativeToBase(null, targetAnchorX, targetAnchorY, myAnchorX, myAnchorY, offsetX, offsetY)
    }
}