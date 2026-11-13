package org.starficz.starui.layout

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.UIComponentAPI
import org.apache.log4j.Logger
import org.starficz.starui.Alignment
import org.starficz.starui.AspectRatioAxis
import org.starficz.starui.Axis
import org.starficz.starui.modifier.PaddingValues
import org.starficz.starui.runtime.childNode
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.relativeToBase

internal class BoxLayoutEngine(private val plugin: StarUIPanelPlugin) : LayoutEngine {

    companion object {
        val log: Logger = Global.getLogger(BoxLayoutEngine::class.java)
        var DEBUG_LAYOUT = false
    }

    private val debugId: String get() = "Box@${Integer.toHexString(plugin.component.hashCode())}"

    /** Builds the message only when debugging is on (layout runs often; the strings are not free). */
    private inline fun debugLog(msg: () -> String) {
        if (DEBUG_LAYOUT) log.info(msg())
    }

    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {

        debugLog { "[$debugId] === MEASURE START ===" }
        debugLog { "[$debugId] Inherited Envelope Bounds -> maxW: $maxWidth, maxH: $maxHeight" }

        var maxW = 0f
        var maxH = 0f

        val containerPadding = plugin.node.padding

        // --- PADDING OVERFLOW WARNINGS ---
        if (maxWidth != Float.MAX_VALUE && maxWidth < containerPadding.horizontal) {
            log.warn("StarUI Layout Warning: Container horizontal padding (${containerPadding.horizontal}) exceeds explicit maximum bound ($maxWidth). Available space clamped to 0.")
        }
        if (maxHeight != Float.MAX_VALUE && maxHeight < containerPadding.vertical) {
            log.warn("StarUI Layout Warning: Container vertical padding (${containerPadding.vertical}) exceeds explicit maximum bound ($maxHeight). Available space clamped to 0.")
        }

        val innerAvailableW = if (maxWidth == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, maxWidth - containerPadding.horizontal)
        val innerAvailableH = if (maxHeight == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, maxHeight - containerPadding.vertical)

        val children = plugin.panel.getChildrenCopy2()

        // ==========================================
        // PASS 1: Intrinsic Scrape & Infinite Bounds Check
        // ==========================================
        debugLog { "[$debugId] --- PASS 1: Intrinsic Scrape ---" }
        children.forEachIndexed { i, child ->
            val node = childNode(child)
            val sizeConfig = node.size

            // --- THE HARD MATH EXCEPTIONS ---
            if (innerAvailableW == Float.MAX_VALUE && sizeConfig.width is Axis.FillMax) {
                throw IllegalStateException("StarUI Layout Exception: a child fills the width of a Box that has no bounded width (nothing above gives it one). Use WrapContent or Exact for the child, or give the Box a size.")
            }
            if (innerAvailableH == Float.MAX_VALUE && sizeConfig.height is Axis.FillMax) {
                throw IllegalStateException("StarUI Layout Exception: a child fills the height of a Box that has no bounded height (nothing above gives it one). Use WrapContent or Exact for the child, or give the Box a size.")
            }

            // A child's margin is space outside it: it gets that much less room, and the Box wraps around it too
            val margin = node.margin
            val childAvailableW = if (innerAvailableW == Float.MAX_VALUE) innerAvailableW else maxOf(0f, innerAvailableW - margin.horizontal)
            val childAvailableH = if (innerAvailableH == Float.MAX_VALUE) innerAvailableH else maxOf(0f, innerAvailableH - margin.vertical)

            // Shared helper handles raw measurement + child padding + NaN fallbacks
            val (paddedIntrinsicW, paddedIntrinsicH) = measureChildPaddedIntrinsic(child, plugin, childAvailableW, childAvailableH)

            debugLog { "  [$debugId] Child[$i] (${child.javaClass.simpleName}) | Sizing: $sizeConfig | Padded Intrinsic: $paddedIntrinsicW x $paddedIntrinsicH" }

            // FillMax elements do not contribute to a Box's intrinsic size; they wait for the Box to size itself
            val childW = when (sizeConfig.width) {
                is Axis.Exact -> sizeConfig.width.pixels + margin.horizontal
                is Axis.WrapContent, is AspectRatioAxis -> paddedIntrinsicW + margin.horizontal
                is Axis.FillMax -> 0f
            }
            val childH = when (sizeConfig.height) {
                is Axis.Exact -> sizeConfig.height.pixels + margin.vertical
                is Axis.WrapContent, is AspectRatioAxis -> paddedIntrinsicH + margin.vertical
                is Axis.FillMax -> 0f
            }

            debugLog { "  [$debugId] Child[$i] contributed bounds: W=$childW, H=$childH" }

            maxW = maxOf(maxW, childW)
            maxH = maxOf(maxH, childH)
        }

        // --- ALL-FILL PARADOX WARNING ---
        if (plugin.node.size.width is Axis.WrapContent && maxW == 0f && children.isNotEmpty()) {
            if (children.all { childNode(it).size.width is Axis.FillMax }) {
                log.warn("StarUI Layout Paradox: A Box set to WrapContent width contains only children set to FillMax width. The Box has no intrinsic width to wrap around, causing it to collapse to 0.")
            }
        }
        if (plugin.node.size.height is Axis.WrapContent && maxH == 0f && children.isNotEmpty()) {
            if (children.all { childNode(it).size.height is Axis.FillMax }) {
                log.warn("StarUI Layout Paradox: A Box set to WrapContent height contains only children set to FillMax height. The Box has no intrinsic height to wrap around, causing it to collapse to 0.")
            }
        }

        val finalIntrinsicW = maxW + containerPadding.horizontal
        val finalIntrinsicH = maxH + containerPadding.vertical

        debugLog { "[$debugId] MEASURE END | Final Intrinsic Bounds: $finalIntrinsicW x $finalIntrinsicH" }
        return Pair(finalIntrinsicW, finalIntrinsicH)
    }

    override fun layout(assignedWidth: Float, assignedHeight: Float) {

        debugLog { "[$debugId] === LAYOUT START ===" }
        debugLog { "[$debugId] Assigned Bounds: W=$assignedWidth, H=$assignedHeight" }

        val containerPadding = plugin.node.padding
        val innerAvailableW = maxOf(0f, assignedWidth - containerPadding.horizontal)
        val innerAvailableH = maxOf(0f, assignedHeight - containerPadding.vertical)

        val children = plugin.panel.getChildrenCopy2()

        // ==========================================
        // FINAL ASSIGNMENT & SIZING
        // ==========================================
        debugLog { "[$debugId] --- FINAL COMPONENT SIZING ASSIGNMENT ---" }
        children.forEachIndexed { i, child ->
            val node = childNode(child)
            val sizeConfig = node.size

            // The room this child has: the Box's inside minus the child's own margin (applyBoxAlignment offsets it)
            val margin = node.margin
            val childAvailableW = maxOf(0f, innerAvailableW - margin.horizontal)
            val childAvailableH = maxOf(0f, innerAvailableH - margin.vertical)

            // Re-measure intrinsic in case it depends on the final layout bounds
            val (paddedIntrinsicW, paddedIntrinsicH) = measureChildPaddedIntrinsic(child, plugin, childAvailableW, childAvailableH)

            val finalW = when (sizeConfig.width) {
                is Axis.Exact -> sizeConfig.width.pixels
                is Axis.FillMax -> childAvailableW
                is Axis.WrapContent -> paddedIntrinsicW
                is AspectRatioAxis -> {
                    val baseH = when (sizeConfig.height) {
                        is Axis.Exact -> sizeConfig.height.pixels
                        is Axis.FillMax -> childAvailableH
                        else -> paddedIntrinsicH
                    }
                    baseH * aspectRatioOf(child, plugin)
                }
            }

            val finalH = when (sizeConfig.height) {
                is Axis.Exact -> sizeConfig.height.pixels
                is Axis.FillMax -> childAvailableH
                is Axis.WrapContent -> paddedIntrinsicH
                is AspectRatioAxis -> finalW / aspectRatioOf(child, plugin)
            }

            debugLog { "  [$debugId] Child[$i] Final Physical Size -> W: $finalW x H: $finalH" }

            // Shared helper sets the size and cascades layout calls downwards to nested containers
            commitChildLayout(child, plugin, finalW, finalH)

            // ==========================================
            // POSITIONING
            // ==========================================
            applyBoxAlignment(child, node.alignment ?: Alignment.TopLeft, margin, containerPadding, i)
        }

        debugLog { "[$debugId] LAYOUT END" }
    }

    private fun applyBoxAlignment(
        child: UIComponentAPI, alignment: Alignment, margin: PaddingValues, containerPadding: PaddingValues, childIndex: Int
    ) {
        val alignX = alignment.x
        val alignY = alignment.y

        // 1. Margins push the child away from its aligned edge
        var offsetX = margin.left * (1f - alignX) - margin.right * alignX
        var offsetY = margin.bottom * (1f - alignY) - margin.top * alignY

        // 2. Container Padding squeezes the aligned edge inwards
        offsetX += containerPadding.left * (1f - alignX) - containerPadding.right * alignX
        offsetY += containerPadding.bottom * (1f - alignY) - containerPadding.top * alignY

        debugLog { "  [$debugId] Child[$childIndex] anchored to Box bounds (AlignX: $alignX, AlignY: $alignY) | Offset X: $offsetX, Y: $offsetY" }

        // 3. Bake coordinates for Starsector's relativeTo method
        val targetAnchorX = alignX
        val targetAnchorY = alignY
        val myAnchorX = -alignX
        val myAnchorY = -alignY

        child.position.relativeToBase(null, targetAnchorX, targetAnchorY, myAnchorX, myAnchorY, offsetX, offsetY)
    }
}