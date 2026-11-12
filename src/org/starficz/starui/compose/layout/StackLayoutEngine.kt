package org.starficz.starui.compose.layout

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.apache.log4j.Logger
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.modifier.Margin
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.core.relativeToBase
import org.starficz.starui.core.getChildrenCopy
import org.starficz.starui.core.height
import org.starficz.starui.core.setSize
import org.starficz.starui.core.width

// ==========================================
// STACK-SPECIFIC ENUMS
// ==========================================

internal enum class StackDirection {
    DOWN, UP, RIGHT, LEFT;
    val isVertical get() = this == DOWN || this == UP
}

// ==========================================
// STACK LAYOUT ENGINE
// ==========================================

internal class StackLayoutEngine(
    private val plugin: StarUIPanelPlugin,
    internal val direction: StackDirection,
    private val crossAlignValue: Float,
    private val spacing: Float
) : LayoutEngine {

    companion object {
        val log: Logger = Global.getLogger(StackLayoutEngine::class.java)
        var DEBUG_LAYOUT = false
    }

    private val debugId: String get() = "${if (direction.isVertical) "VStack" else "HStack"}@${Integer.toHexString(plugin.component.hashCode())}"

    /** Builds the message only when debugging is on (layout runs often; the strings are not free). */
    private inline fun debugLog(msg: () -> String) {
        if (DEBUG_LAYOUT) log.info(msg())
    }

    private var measuredPrimary = 0f
    private var measuredSecondary = 0f

    override val contentSize: Pair<Float, Float>
        get() = if (direction.isVertical) Pair(measuredSecondary, measuredPrimary) else Pair(measuredPrimary, measuredSecondary)
    private var totalFillWeight = 0f

    // Caches the (Raw Content + Child Padding) footprint for the layout pass. Kept after the layout: a measure reused
    // from an earlier pass (StarUIPanelPlugin.measure) is laid out again from it.
    private val childPaddedIntrinsics = mutableMapOf<UIComponentAPI, Pair<Float, Float>>()

    /** Where each child was last placed (main-axis offset, cross offset): an unchanged placement is not redone. */
    private val placed = java.util.IdentityHashMap<UIComponentAPI, Long>()

    /** Forgets the placements (children were moved by something else, e.g. pinned on a removal). */
    internal fun forgetPlacements() = placed.clear()

    // Dynamically maps Width/Height to Primary/Cross based on layout direction
    private fun getAxisDimension(size: NodeSize, isVertical: Boolean): Pair<Dimension, Dimension> {
        return Pair(
            if (isVertical) size.height else size.width,
            if (isVertical) size.width else size.height
        )
    }

    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        val isVertical = direction.isVertical

        debugLog { "[$debugId] === MEASURE START ===" }
        debugLog { "[$debugId] Inherited Envelope Bounds -> maxW: $maxWidth, maxH: $maxHeight" }
        debugLog { "[$debugId] My Engine Sizing -> ${plugin.myNodeSize}" }

        var fixedPrimarySpace = 0f
        var maxSecondarySpace = 0f
        totalFillWeight = 0f
        childPaddedIntrinsics.clear()

        val children = plugin.panel.getChildrenCopy()
        val containerPadding = plugin.myPadding

        val (myPrimaryDim, myCrossDim) = getAxisDimension(plugin.myNodeSize, isVertical)
        debugLog { "[$debugId] Resolved Engine Axes -> Primary: $myPrimaryDim, Cross: $myCrossDim" }

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
        // When this container's cross size is its content (wrap), a child that fills the cross axis takes its
        // siblings' cross size and never widens the container, so it is measured once that size is known.
        val isMyCrossFromContent = myCrossDim !is Dimension.Exact && myCrossDim !is Dimension.FillMax

        fun dimsOf(child: UIComponentAPI) = getAxisDimension(plugin.childSizing[child] ?: NodeSize(), isVertical)
        fun primaryMarginOf(child: UIComponentAPI) = (plugin.childMargins[child] ?: Margin()).let { if (isVertical) it.vertical else it.horizontal }
        fun crossMarginOf(child: UIComponentAPI) = (plugin.childMargins[child] ?: Margin()).let { if (isVertical) it.horizontal else it.vertical }
        fun minus(space: Float, amount: Float) = if (space == Float.MAX_VALUE) Float.MAX_VALUE else maxOf(0f, space - amount)

        // --- THE HARD MATH EXCEPTIONS ---
        children.forEach { child ->
            val primaryDim = dimsOf(child).first
            if (isMyPrimaryWrap && primaryDim is Dimension.FillMax) {
                throw IllegalStateException("StarUI Layout Exception: A StackLayout set to WrapContent its primary axis cannot contain children that FillMax the primary axis.")
            }
            if (effectiveMaxPrimary == Float.MAX_VALUE && primaryDim is Dimension.FillMax) {
                throw IllegalStateException("StarUI Layout Exception: a child fills the main axis of a Column or Row that has no bounded size on it (nothing above gives it a size). Use WrapContent or Exact for the child, or give the container a size.")
            }
            if (primaryDim is Dimension.FillMax) totalFillWeight += primaryDim.weight
        }
        val totalSpacing = maxOf(0, children.size - 1) * spacing

        // Each child is measured at the size layout will give it wherever that size is known: its exact size, its
        // share of the flexible space when it fills the main axis, the container's cross size (minus its margin) when
        // it fills the cross axis. Otherwise at the room it has. Wrapping text measured any wider reports too few lines.
        fun measure(child: UIComponentAPI, primaryRoom: Float, crossRoom: Float) {
            val (primaryDim, crossDim) = dimsOf(child)
            val primary = if (primaryDim is Dimension.Exact) primaryDim.pixels else primaryRoom
            val cross = when (crossDim) {
                is Dimension.Exact -> crossDim.pixels
                is Dimension.FillMax -> minus(crossRoom, crossMarginOf(child))
                else -> crossRoom
            }
            // The helper takes (width, height): in a Column the cross axis is the width, in a Row the primary axis is.
            val padded = if (isVertical) measureChildPaddedIntrinsic(child, plugin, cross, primary)
                         else measureChildPaddedIntrinsic(child, plugin, primary, cross)
            childPaddedIntrinsics[child] = padded
            debugLog { "  [$debugId] Child (${child.javaClass.simpleName}) | Axes $primaryDim / $crossDim | measured at $primary x $cross -> Padded Intrinsic: ${padded.first} x ${padded.second}" }
        }

        // What a measured child adds to the main axis, margin included (a fill child adds only its margin here).
        fun primaryContribution(child: UIComponentAPI): Float {
            val (pw, ph) = childPaddedIntrinsics[child]!!
            val own = when (val primaryDim = dimsOf(child).first) {
                is Dimension.FillMax -> 0f
                is Dimension.Exact -> primaryDim.pixels
                is Dimension.WrapContent, is Dimension.AspectRatio -> if (isVertical) ph else pw
            }
            return own + primaryMarginOf(child)
        }

        // How much cross room a measured child needs, margin included; a cross-fill child needs none (it stretches).
        fun crossRequirement(child: UIComponentAPI, flexSpace: Float): Float {
            val (paddedW, paddedH) = childPaddedIntrinsics[child]!!
            val (primaryDim, crossDim) = dimsOf(child)
            // Aspect Ratio mathematically derives secondary size from finalized primary size
            val finalizedPrimarySize = when (primaryDim) {
                is Dimension.FillMax -> if (totalFillWeight > 0f) (primaryDim.weight / totalFillWeight) * flexSpace else 0f
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
            return when (crossDim) {
                is Dimension.Exact -> crossDim.pixels + crossMarginOf(child)
                is Dimension.WrapContent, is Dimension.AspectRatio -> paddedIntrinsicSecondary + crossMarginOf(child)
                is Dimension.FillMax -> 0f // Stretch is evaluated in Layout pass
            }
        }

        val fillChildren = children.filter { dimsOf(it).first is Dimension.FillMax }
        val waitsForCross = if (isMyCrossFromContent) children.filter { dimsOf(it).second is Dimension.FillMax }.toSet() else emptySet()
        // With only cross-fill children there is no cross size to take; they are measured at the room there is and the
        // container collapses on that axis (the paradox warning below).
        val hasCrossContent = children.any { dimsOf(it).second !is Dimension.FillMax }

        // ==========================================
        // PASS 1A: children whose size depends on nothing else here, in order
        // ==========================================
        debugLog { "[$debugId] --- PASS 1A: Independent children ---" }
        var independentPrimary = 0f
        for (child in children) {
            if (child in fillChildren || (hasCrossContent && child in waitsForCross)) continue
            measure(child, minus(availablePrimary, independentPrimary), availableCross)
            independentPrimary += primaryContribution(child)
        }
        val independentCross = children
            .filter { childPaddedIntrinsics.containsKey(it) && it !in fillChildren }
            .maxOfOrNull { crossRequirement(it, 0f) } ?: 0f

        // ==========================================
        // PASS 1B: cross-fill children (once the cross size is known) and main-axis fill children (at their share)
        // ==========================================
        // The cross size can grow when a fill child turns out wider than the rest, which changes what the cross-fill
        // children need and so the shares; a second round settles that, a third is a safety margin.
        var crossSize = if (hasCrossContent) independentCross else availableCross
        var actualAvailableFlexSpace = 0f
        for (round in 0 until 3) {
            fixedPrimarySpace = independentPrimary
            for (child in children) {
                if (child in fillChildren || child !in waitsForCross || !hasCrossContent) continue
                measure(child, minus(availablePrimary, fixedPrimarySpace), crossSize)
                fixedPrimarySpace += primaryContribution(child)
            }
            for (child in fillChildren) fixedPrimarySpace += primaryMarginOf(child)
            actualAvailableFlexSpace = maxOf(0f, availablePrimary - fixedPrimarySpace - totalSpacing)

            for (child in fillChildren) {
                val weight = (dimsOf(child).first as Dimension.FillMax).weight
                val share = if (totalFillWeight > 0f) weight / totalFillWeight * actualAvailableFlexSpace else 0f
                measure(child, share, if (child in waitsForCross) crossSize else availableCross)
            }
            if (waitsForCross.isEmpty() || !hasCrossContent) break
            val settledCross = maxOf(independentCross, fillChildren.maxOfOrNull { crossRequirement(it, actualAvailableFlexSpace) } ?: 0f)
            if (kotlin.math.abs(settledCross - crossSize) < 0.5f) break
            debugLog { "[$debugId] Cross size grew from $crossSize to $settledCross: measuring the cross-fill children again" }
            crossSize = settledCross
        }
        debugLog { "[$debugId] Flex Space: $actualAvailableFlexSpace (Total Fixed: $fixedPrimarySpace)" }

        children.forEach { maxSecondarySpace = maxOf(maxSecondarySpace, crossRequirement(it, actualAvailableFlexSpace)) }

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

        debugLog { "[$debugId] MEASURE END | Calculated Intrinsic Bounds: measuredPrimary=$measuredPrimary, measuredSecondary=$measuredSecondary" }
        debugLog { "[$debugId] MEASURE END | Final Constraints Propagated to Parent: primary=$finalPrimary, cross=$finalCross" }

        return Pair(if (isVertical) finalCross else finalPrimary, if (isVertical) finalPrimary else finalCross)
    }

    override fun layout(assignedWidth: Float, assignedHeight: Float) {
        val children = plugin.panel.getChildrenCopy()

        val isVertical = direction.isVertical

        debugLog { "[$debugId] === LAYOUT START ===" }
        debugLog { "[$debugId] Post-Measure Assigned Space from Parent -> assignedW: $assignedWidth, assignedH: $assignedHeight" }

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

        debugLog { "[$debugId] Initial Assigned Flex Space: $initialFlexSpace | Total Flex Weight Demanded: $totalFillWeight" }

        val clips = plugin.overflow.clips
        if (rawAvailableFlexSpace < -0.5f && !clips) {
            log.warn("StarUI Layout Overflow: Primary boundaries exceeded by ${-rawAvailableFlexSpace}px in $debugId.")
        }
        if (measuredSecondary > finalContainerSecondary + 0.5f && !clips) {
            log.warn("StarUI Layout Overflow: Cross boundaries exceeded by ${measuredSecondary - finalContainerSecondary}px in $debugId.")
        }

        // Parent MUST be sized before children are positioned to allow Drift Correction math to work
        plugin.panel.setSize(
            if (isVertical) finalContainerSecondary else finalContainerPrimary,
            if (isVertical) finalContainerPrimary else finalContainerSecondary
        )
        // Sized even when empty (an emptied wrap Column shrinks); with no children there is nothing more to place
        if (children.isEmpty()) return

        // ==========================================
        // FINAL ASSIGNMENT & SIZING
        // ==========================================
        debugLog { "[$debugId] --- FINAL COMPONENT SIZING ASSIGNMENT ---" }
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

            debugLog { "  [$debugId] Child[$i] Final Physical Size -> W: $finalCW x H: $finalCH" }
            commitChildLayout(child, plugin, finalCW, finalCH)
        }

        // ==========================================
        // POSITIONING
        // ==========================================
        // Every child is placed relative to the container at its running offset along the main axis. Anchoring each
        // child on the one before it (as this once did) makes vanilla recompute every later sibling whenever one
        // moves: quadratic in the number of children, which a long list cannot afford.
        debugLog { "[$debugId] --- APPLYING STACK POSITIONING ---" }
        var running = when (direction) {
            StackDirection.DOWN -> containerPadding.top
            StackDirection.UP -> containerPadding.bottom
            StackDirection.RIGHT -> containerPadding.left
            StackDirection.LEFT -> containerPadding.right
        }
        children.forEachIndexed { i, component ->
            val myMargin = plugin.childMargins[component] ?: Margin()
            val (leading, trailing) = when (direction) {
                StackDirection.DOWN -> myMargin.top to myMargin.bottom
                StackDirection.UP -> myMargin.bottom to myMargin.top
                StackDirection.RIGHT -> myMargin.left to myMargin.right
                StackDirection.LEFT -> myMargin.right to myMargin.left
            }
            val offset = running + leading
            applyStackPositioning(component, offset, containerPadding, myMargin, i)
            val size = if (isVertical) component.position.height else component.position.width
            running = offset + size + trailing + spacing
        }
        // children dropped without a removal hook (clearChildren) must not be held on to
        if (placed.size > children.size) placed.keys.retainAll(children.toSet())

        debugLog { "[$debugId] LAYOUT END" }
    }

    /** Places [child] [offset] along the main axis from the container's leading edge, aligned across it. */
    private fun applyStackPositioning(
        child: UIComponentAPI, offset: Float, containerPadding: PaddingValues, myMargin: Margin,
        childIndex: Int
    ) {
        val targetAnchorX: Float; val targetAnchorY: Float
        val myAnchorX: Float; val myAnchorY: Float
        val offsetX: Float; val offsetY: Float

        if (direction.isVertical) {
            targetAnchorX = crossAlignValue
            myAnchorX = -crossAlignValue
            offsetX = (containerPadding.left * (1f - targetAnchorX) - containerPadding.right * targetAnchorX) +
                    (myMargin.left * (1f - targetAnchorX) - myMargin.right * targetAnchorX)
            if (direction == StackDirection.UP) {
                targetAnchorY = 0f; myAnchorY = 0f; offsetY = offset
            } else {
                targetAnchorY = 1f; myAnchorY = -1f; offsetY = -offset
            }
        } else {
            targetAnchorY = crossAlignValue
            myAnchorY = -crossAlignValue
            offsetY = (containerPadding.bottom * (1f - targetAnchorY) - containerPadding.top * targetAnchorY) +
                    (myMargin.bottom * (1f - targetAnchorY) - myMargin.top * targetAnchorY)
            if (direction == StackDirection.RIGHT) {
                targetAnchorX = 0f; myAnchorX = 0f; offsetX = offset
            } else {
                targetAnchorX = 1f; myAnchorX = -1f; offsetX = -offset
            }
        }

        debugLog { "  [$debugId] Child[$childIndex] at $offset along the main axis | Offset X: $offsetX, Y: $offsetY" }
        val key = (offsetX.toRawBits().toLong() shl 32) or (offsetY.toRawBits().toLong() and 0xffffffffL)
        if (placed.put(child, key) == key) return
        child.position.relativeToBase(null, targetAnchorX, targetAnchorY, myAnchorX, myAnchorY, offsetX, offsetY)
    }
}
