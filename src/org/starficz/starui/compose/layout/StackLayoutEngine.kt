package org.starficz.starui.compose.layout

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.apache.log4j.Logger
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.modifier.Margin
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.core.ReflectionUtils.getMethodsMatching
import org.starficz.starui.core.getChildrenCopy
import org.starficz.starui.core.height
import org.starficz.starui.core.setSize
import org.starficz.starui.core.width

// ==========================================
// STACK-SPECIFIC ENUMS
// ==========================================

enum class StackDirection {
    DOWN, UP, RIGHT, LEFT;
    val isVertical get() = this == DOWN || this == UP
}

enum class HorizontalAlignment(val alignValue: Float) {
    Left(0f),
    Center(0.5f),
    Right(1f)
}

/** Used for the cross-axis of a Row */
enum class VerticalAlignment(val alignValue: Float) {
    Bottom(0f),
    Center(0.5f),
    Top(1f)
}

enum class VGrow(val direction: StackDirection) {
    Downwards(StackDirection.DOWN),
    Upwards(StackDirection.UP)
}

enum class HGrow(val direction: StackDirection) {
    Rightwards(StackDirection.RIGHT),
    Leftwards(StackDirection.LEFT)
}

typealias XAlign = HorizontalAlignment
typealias YAlign = VerticalAlignment

// ==========================================
// STACK LAYOUT ENGINE
// ==========================================

internal class StackLayoutEngine(
    private val plugin: StarUIPanelPlugin,
    private val direction: StackDirection,
    private val crossAlignValue: Float,
    private val spacing: Float
) : LayoutEngine {

    companion object {
        val log: Logger = Global.getLogger(StackLayoutEngine::class.java)
        var DEBUG_LAYOUT = false
    }

    private fun debugLog(msg: String) {
        if (DEBUG_LAYOUT) log.info(msg)
    }

    private var measuredPrimary = 0f
    private var measuredSecondary = 0f
    private var totalFillWeight = 0f

    // Caches the (Raw Content + Child Padding) footprint for the layout pass
    private val childPaddedIntrinsics = mutableMapOf<UIComponentAPI, Pair<Float, Float>>()

    // Dynamically maps Width/Height to Primary/Cross based on layout direction
    private fun getAxisDimension(size: NodeSize, isVertical: Boolean): Pair<Dimension, Dimension> {
        return Pair(
            if (isVertical) size.height else size.width,
            if (isVertical) size.width else size.height
        )
    }

    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        val isVertical = direction.isVertical
        val debugId = "${if (isVertical) "VStack" else "HStack"}@${Integer.toHexString(plugin.component.hashCode())}"

        debugLog("[$debugId] === MEASURE START ===")
        debugLog("[$debugId] Inherited Envelope Bounds -> maxW: $maxWidth, maxH: $maxHeight")
        debugLog("[$debugId] My Engine Sizing -> ${plugin.myNodeSize}")

        var fixedPrimarySpace = 0f
        var maxSecondarySpace = 0f
        totalFillWeight = 0f
        childPaddedIntrinsics.clear()

        val children = plugin.panel.getChildrenCopy()
        val containerPadding = plugin.myPadding

        val (myPrimaryDim, myCrossDim) = getAxisDimension(plugin.myNodeSize, isVertical)
        debugLog("[$debugId] Resolved Engine Axes -> Primary: $myPrimaryDim, Cross: $myCrossDim")

        val primaryPaddingSum = if (isVertical) containerPadding.vertical else containerPadding.horizontal
        val crossPaddingSum = if (isVertical) containerPadding.horizontal else containerPadding.vertical

        val effectiveMaxPrimary = when (myPrimaryDim) {
            is Dimension.Exact -> myPrimaryDim.pixels
            else -> if (isVertical) maxHeight else maxWidth
        }
        val effectiveMaxCross = when (myCrossDim) {
            is Dimension.Exact -> myCrossDim.pixels
            else -> if (isVertical) maxWidth else maxHeight
        }

        // --- PADDING OVERFLOW WARNINGS ---
        if (effectiveMaxPrimary != Float.MAX_VALUE && effectiveMaxPrimary < primaryPaddingSum) {
            log.warn("StarUI Layout Warning: Container primary padding ($primaryPaddingSum) exceeds explicit maximum bound ($effectiveMaxPrimary). Available space clamped to 0.")
        }
        if (effectiveMaxCross != Float.MAX_VALUE && effectiveMaxCross < crossPaddingSum) {
            log.warn("StarUI Layout Warning: Container cross padding ($crossPaddingSum) exceeds explicit maximum bound ($effectiveMaxCross). Available space clamped to 0.")
        }

        val availablePrimary = if (effectiveMaxPrimary == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, effectiveMaxPrimary - primaryPaddingSum)
        val availableCross = if (effectiveMaxCross == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, effectiveMaxCross - crossPaddingSum)

        val isMyPrimaryWrap = myPrimaryDim is Dimension.WrapContent

        // ==========================================
        // PASS 1A: Primary Intrinsic Scrape
        // ==========================================
        debugLog("[$debugId] --- PASS 1A: Primary Intrinsic Scrape & Fixed Tally ---")
        children.forEachIndexed { i, child ->
            val childSizing = plugin.childSizing[child] ?: NodeSize()
            val childMargin = plugin.childMargins[child] ?: Margin()

            val currentAvailablePrimary = if (availablePrimary == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, availablePrimary - fixedPrimarySpace)

            // Measure the child's raw content + its own padding
            val paddedIntrinsic = measureChildPaddedIntrinsic(child, plugin, availableCross, currentAvailablePrimary)
            childPaddedIntrinsics[child] = paddedIntrinsic

            val paddedIntrinsicPrimary = if (isVertical) paddedIntrinsic.second else paddedIntrinsic.first
            val primaryMarginSum = if (isVertical) childMargin.vertical else childMargin.horizontal

            val (primaryDim, crossDim) = getAxisDimension(childSizing, isVertical)

            debugLog("  [$debugId] Child[$i] (${child.javaClass.simpleName}) | Padded Intrinsic: ${paddedIntrinsic.first} x ${paddedIntrinsic.second}")
            debugLog("  [$debugId] Child[$i] Resolved Axes -> Primary: $primaryDim, Cross: $crossDim")

            // --- THE HARD MATH EXCEPTIONS ---
            if (isMyPrimaryWrap && primaryDim is Dimension.FillMax) {
                throw IllegalStateException("StarUI Layout Exception: A StackLayout set to WrapContent its primary axis cannot contain children that FillMax the primary axis.")
            }
            if (effectiveMaxPrimary == Float.MAX_VALUE && primaryDim is Dimension.FillMax) {
                throw IllegalStateException("StarUI Layout Exception: Cannot use Dimension.FillMax inside an unconstrained infinite axis (e.g. ScrollPanel). Use WrapContent or Exact instead.")
            }

            var childPrimaryContributed = 0f
            when (primaryDim) {
                is Dimension.FillMax -> totalFillWeight += primaryDim.weight
                is Dimension.Exact -> childPrimaryContributed = primaryDim.pixels
                is Dimension.WrapContent, is Dimension.AspectRatio -> childPrimaryContributed = paddedIntrinsicPrimary
            }
            fixedPrimarySpace += (childPrimaryContributed + primaryMarginSum)
            debugLog("  [$debugId] Child[$i] contributed Fixed Primary Space: $childPrimaryContributed (Total Fixed: $fixedPrimarySpace)")
        }

        val totalSpacing = maxOf(0, children.size - 1) * spacing
        val actualAvailableFlexSpace = maxOf(0f, availablePrimary - fixedPrimarySpace - totalSpacing)

        debugLog("[$debugId] Pre-Pass-1B Flex Space: $actualAvailableFlexSpace")

        // ==========================================
        // PASS 1B: Cross Axis Resolution
        // ==========================================
        debugLog("[$debugId] --- PASS 1B: Dependent Cross Axes Calculation ---")
        children.forEachIndexed { i, child ->
            val childSizing = plugin.childSizing[child] ?: NodeSize()
            val childMargin = plugin.childMargins[child] ?: Margin()
            val crossMarginSum = if (isVertical) childMargin.horizontal else childMargin.vertical

            val (paddedW, paddedH) = childPaddedIntrinsics[child]!!

            val (primaryDim, crossDim) = getAxisDimension(childSizing, isVertical)

            // Aspect Ratio mathematically derives secondary size from finalized primary size
            val finalizedPrimarySize = when (primaryDim) {
                is Dimension.FillMax -> if (totalFillWeight > 0f) (primaryDim.weight / totalFillWeight) * actualAvailableFlexSpace else 0f
                is Dimension.Exact -> primaryDim.pixels
                is Dimension.WrapContent, is Dimension.AspectRatio -> if (isVertical) paddedH else paddedW
            }

            val paddedIntrinsicSecondary = if (crossDim is Dimension.AspectRatio) {
                val rawAr = if (plugin.childMeasurables.containsKey(child)) {
                    val ratioIntrin = plugin.childMeasurables[child]!!.invoke(100f, 100f)
                    ratioIntrin.first / ratioIntrin.second
                } else 1f

                if (isVertical) finalizedPrimarySize * rawAr else finalizedPrimarySize / rawAr
            } else {
                if (isVertical) paddedW else paddedH
            }

            val resolvedSecondary = when (crossDim) {
                is Dimension.Exact -> crossDim.pixels + crossMarginSum
                is Dimension.WrapContent, is Dimension.AspectRatio -> paddedIntrinsicSecondary + crossMarginSum
                is Dimension.FillMax -> 0f // Stretch is evaluated in Layout pass
            }

            debugLog("  [$debugId] Child[$i] Resolved Secondary Intrinsic Requirement: $resolvedSecondary")
            maxSecondarySpace = maxOf(maxSecondarySpace, resolvedSecondary)
        }

        measuredPrimary = fixedPrimarySpace + totalSpacing + primaryPaddingSum
        measuredSecondary = maxSecondarySpace + crossPaddingSum

        // --- CROSS-AXIS PARADOX WARNING ---
        if (measuredSecondary == crossPaddingSum && myCrossDim is Dimension.WrapContent && children.isNotEmpty()) {
            val allFill = children.all { getAxisDimension(plugin.childSizing[it] ?: NodeSize(), isVertical).second is Dimension.FillMax }
            if (allFill) {
                log.warn("StarUI Layout Paradox: A StackLayout set to WrapContent its cross-axis contains only children set to FillMax that axis. The container has no intrinsic size to wrap around, causing it to collapse.")
            }
        }

        val finalPrimary = when (myPrimaryDim) {
            is Dimension.Exact -> myPrimaryDim.pixels
            is Dimension.FillMax -> if (isVertical) maxHeight else maxWidth
            else -> measuredPrimary
        }
        val finalCross = when (myCrossDim) {
            is Dimension.Exact -> myCrossDim.pixels
            is Dimension.FillMax -> if (isVertical) maxWidth else maxHeight
            else -> measuredSecondary
        }

        debugLog("[$debugId] MEASURE END | Calculated Intrinsic Bounds: measuredPrimary=$measuredPrimary, measuredSecondary=$measuredSecondary")
        debugLog("[$debugId] MEASURE END | Final Constraints Propagated to Parent: primary=$finalPrimary, cross=$finalCross")

        return Pair(if (isVertical) finalCross else finalPrimary, if (isVertical) finalPrimary else finalCross)
    }

    override fun layout(assignedWidth: Float, assignedHeight: Float) {
        val children = plugin.panel.getChildrenCopy()
        if (children.isEmpty()) return

        val isVertical = direction.isVertical
        val debugId = "${if (isVertical) "VStack" else "HStack"}@${Integer.toHexString(plugin.component.hashCode())}"

        debugLog("[$debugId] === LAYOUT START ===")
        debugLog("[$debugId] Post-Measure Assigned Space from Parent -> assignedW: $assignedWidth, assignedH: $assignedHeight")

        val containerPadding = plugin.myPadding
        val (myPrimaryDim, myCrossDim) = getAxisDimension(plugin.myNodeSize, isVertical)

        val finalContainerPrimary = when (myPrimaryDim) {
            is Dimension.Exact -> myPrimaryDim.pixels
            is Dimension.FillMax -> if (isVertical) assignedHeight else assignedWidth
            else -> measuredPrimary
        }

        val finalContainerSecondary = when (myCrossDim) {
            is Dimension.Exact -> myCrossDim.pixels
            is Dimension.FillMax -> if (isVertical) assignedWidth else assignedHeight
            else -> measuredSecondary
        }

        val primaryPaddingSum = if (isVertical) containerPadding.vertical else containerPadding.horizontal
        val crossPaddingSum = if (isVertical) containerPadding.horizontal else containerPadding.vertical

        val rawAvailableFlexSpace = finalContainerPrimary - measuredPrimary
        val initialFlexSpace = maxOf(0f, rawAvailableFlexSpace)

        debugLog("[$debugId] Initial Assigned Flex Space: $initialFlexSpace | Total Flex Weight Demanded: $totalFillWeight")

        if (rawAvailableFlexSpace < -0.5f) {
            log.warn("StarUI Layout Overflow: Primary boundaries exceeded by ${-rawAvailableFlexSpace}px in $debugId.")
        }
        if (measuredSecondary > finalContainerSecondary + 0.5f) {
            log.warn("StarUI Layout Overflow: Cross boundaries exceeded by ${measuredSecondary - finalContainerSecondary}px in $debugId.")
        }

        // Parent MUST be sized before children are positioned to allow Drift Correction math to work
        plugin.panel.setSize(
            if (isVertical) finalContainerSecondary else finalContainerPrimary,
            if (isVertical) finalContainerPrimary else finalContainerSecondary
        )

        // ==========================================
        // FINAL ASSIGNMENT & SIZING
        // ==========================================
        debugLog("[$debugId] --- FINAL COMPONENT SIZING ASSIGNMENT ---")
        children.forEachIndexed { i, child ->
            val childSizing = plugin.childSizing[child] ?: NodeSize()
            val childMargin = plugin.childMargins[child] ?: Margin()
            val crossMarginSum = if (isVertical) childMargin.horizontal else childMargin.vertical

            val (paddedIntrinsicW, paddedIntrinsicH) = childPaddedIntrinsics[child]!!
            val paddedIntrinsicPrimary = if (isVertical) paddedIntrinsicH else paddedIntrinsicW
            val paddedIntrinsicSecondary = if (isVertical) paddedIntrinsicW else paddedIntrinsicH

            val (primaryDim, crossDim) = getAxisDimension(childSizing, isVertical)

            val newPrimary = when (primaryDim) {
                is Dimension.FillMax -> if (totalFillWeight > 0f) (primaryDim.weight / totalFillWeight) * initialFlexSpace else 0f
                is Dimension.Exact -> primaryDim.pixels
                is Dimension.WrapContent, is Dimension.AspectRatio -> paddedIntrinsicPrimary
            }

            // Aspect ratio calculation requires resolving the actual physical primary pixel count first
            val newSecondary = when (crossDim) {
                is Dimension.FillMax -> maxOf(0f, finalContainerSecondary - crossMarginSum - crossPaddingSum)
                is Dimension.Exact -> crossDim.pixels
                is Dimension.WrapContent -> paddedIntrinsicSecondary
                is Dimension.AspectRatio -> {
                    val rawAr = if (plugin.childMeasurables.containsKey(child)) {
                        val ratioIntrin = plugin.childMeasurables[child]!!.invoke(100f, 100f)
                        ratioIntrin.first / ratioIntrin.second
                    } else 1f
                    if (isVertical) newPrimary * rawAr else newPrimary / rawAr
                }
            }

            val finalCW = if (isVertical) newSecondary else newPrimary
            val finalCH = if (isVertical) newPrimary else newSecondary

            debugLog("  [$debugId] Child[$i] Final Physical Size -> W: $finalCW x H: $finalCH")
            commitChildLayout(child, plugin, finalCW, finalCH)
        }

        // ==========================================
        // POSITIONING
        // ==========================================
        debugLog("[$debugId] --- APPLYING STACK POSITIONING ---")
        var previousElement: UIComponentAPI? = null
        children.forEachIndexed { i, component ->
            val myMargin = plugin.childMargins[component] ?: Margin()
            val prevMargin = if (previousElement != null) plugin.childMargins[previousElement] ?: Margin() else Margin()

            applyStackPositioning(
                parent = plugin.panel,
                child = component,
                prev = previousElement,
                containerPadding = containerPadding,
                myMargin = myMargin,
                prevMargin = prevMargin,
                debugId = debugId,
                childIndex = i
            )
            previousElement = component
        }

        childPaddedIntrinsics.clear()
        debugLog("[$debugId] LAYOUT END")
    }

    private fun applyStackPositioning(
        parent: UIComponentAPI, child: UIComponentAPI, prev: UIComponentAPI?,
        containerPadding: PaddingValues, myMargin: Margin, prevMargin: Margin,
        debugId: String, childIndex: Int
    ) {
        val isFirst = prev == null
        val targetAnchorX: Float; val targetAnchorY: Float
        val myAnchorX: Float; val myAnchorY: Float
        var offsetX = 0f; var offsetY = 0f

        if (direction.isVertical) {
            // --- CROSS AXIS ALIGNMENT (X) ---
            targetAnchorX = crossAlignValue
            myAnchorX = -crossAlignValue

            // Linear interpolation of padding and margins
            offsetX = (containerPadding.left * (1f - targetAnchorX) - containerPadding.right * targetAnchorX) +
                    (myMargin.left * (1f - targetAnchorX) - myMargin.right * targetAnchorX)

            // --- ROUNDING DRIFT CORRECTION ---
            // Starsector uses Math.round() on absolute coordinates. If we chain anchors,
            // sub-pixel rounding errors compound. This tracks deviation from the true parent center line.
            if (!isFirst) {
                val parentAnchorPoint = parent.position.x + parent.width * targetAnchorX
                val prevAnchorPoint = prev!!.position.x + prev.width * targetAnchorX
                val drift = parentAnchorPoint - prevAnchorPoint
                if (drift != 0f) debugLog("  [$debugId] Child[$childIndex] Cross-Axis Drift Correction Applied: ${drift}px")
                offsetX += drift
            }

            // --- PRIMARY AXIS FLOW (Y) ---
            if (direction == StackDirection.UP) { // Positive Y
                if (isFirst) {
                    targetAnchorY = 0f; myAnchorY = 0f
                    offsetY = containerPadding.bottom + myMargin.bottom
                } else {
                    targetAnchorY = 1f; myAnchorY = 0f
                    offsetY = spacing + prevMargin.top + myMargin.bottom
                }
            } else { // StackDirection.DOWN (Negative Y)
                if (isFirst) {
                    targetAnchorY = 1f; myAnchorY = -1f
                    offsetY = -(containerPadding.top + myMargin.top)
                } else {
                    targetAnchorY = 0f; myAnchorY = -1f
                    offsetY = -(spacing + prevMargin.bottom + myMargin.top)
                }
            }
        } else {
            // --- CROSS AXIS ALIGNMENT (Y) ---
            targetAnchorY = crossAlignValue
            myAnchorY = -crossAlignValue

            offsetY = (containerPadding.bottom * (1f - targetAnchorY) - containerPadding.top * targetAnchorY) +
                    (myMargin.bottom * (1f - targetAnchorY) - myMargin.top * targetAnchorY)

            // --- ROUNDING DRIFT CORRECTION ---
            if (!isFirst) {
                val parentAnchorPoint = parent.position.y + parent.height * targetAnchorY
                val prevAnchorPoint = prev!!.position.y + prev.height * targetAnchorY
                val drift = parentAnchorPoint - prevAnchorPoint
                if (drift != 0f) debugLog("  [$debugId] Child[$childIndex] Cross-Axis Drift Correction Applied: ${drift}px")
                offsetY += drift
            }

            // --- PRIMARY AXIS FLOW (X) ---
            if (direction == StackDirection.RIGHT) { // Positive X
                if (isFirst) {
                    targetAnchorX = 0f; myAnchorX = 0f
                    offsetX = containerPadding.left + myMargin.left
                } else {
                    targetAnchorX = 1f; myAnchorX = 0f
                    offsetX = spacing + prevMargin.right + myMargin.left
                }
            } else { // StackDirection.LEFT (Negative X)
                if (isFirst) {
                    targetAnchorX = 1f; myAnchorX = -1f
                    offsetX = -(containerPadding.right + myMargin.right)
                } else {
                    targetAnchorX = 0f; myAnchorX = -1f
                    offsetX = -(spacing + prevMargin.left + myMargin.right)
                }
            }
        }

        debugLog("  [$debugId] Child[$childIndex] anchored to ${if (isFirst) "Parent" else "Previous"} | Offset X: $offsetX, Y: $offsetY")

        // Apply via Vanilla Reflection
        val floatType = Float::class.javaPrimitiveType!!
        val paramTypes = arrayOf<Class<*>?>(child.position::class.java, floatType, floatType, floatType, floatType, floatType, floatType)
        child.position.getMethodsMatching("relativeTo", parameterTypes = paramTypes)[0]
            .invoke(child.position, prev?.position ?: parent.position, targetAnchorX, targetAnchorY, myAnchorX, myAnchorY, offsetX, offsetY)
    }
}