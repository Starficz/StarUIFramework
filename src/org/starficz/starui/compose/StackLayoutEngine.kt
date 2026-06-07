package org.starficz.starui.compose

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.apache.log4j.Logger
import org.starficz.starui.core.*

internal class StackLayoutEngine(
    private val panel: UIPanelAPI,
    private val plugin: StarUIPanelPlugin,
    private val strategy: LayoutStrategy,
    private val primaryPadding: Float,
    private val secondaryPadding: Float,
    private val spacing: Float,
    private val sizing: StarUISizing
) : LayoutEngine {

    companion object {
        val log: Logger = Global.getLogger(StackLayoutEngine::class.java)
    }

    private var measuredPrimary = 0f
    private var measuredSecondary = 0f
    private var totalFlexWeight = 0f

    private val childIntrinsicSizes = mutableMapOf<UIComponentAPI, Pair<Float, Float>>()

    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        log.info("StarUI Pass 1: MEASURE | Max Constraints -> W: $maxWidth, H: $maxHeight")
        var fixedPrimary = 0f
        var maxSecondary = 0f
        totalFlexWeight = 0f
        childIntrinsicSizes.clear()

        val children = panel.getChildrenCopy()
        val isSelfWrap = sizing is WrapStackImpl

        val availableCross = if (strategy is VerticalStrategy) {
            if (maxWidth == Float.MAX_VALUE) Float.MAX_VALUE else maxWidth - (secondaryPadding * 2)
        } else {
            if (maxHeight == Float.MAX_VALUE) Float.MAX_VALUE else maxHeight - (secondaryPadding * 2)
        }

        children.forEach { child ->
            val childSizing = plugin.childSizing[child] ?: ContentSize.wrap()
            val childMargin = plugin.childMargins[child] ?: Margin()

            val intrinsicSize: Pair<Float, Float>
            if (child is CustomPanelAPI && child.plugin is StarUIPanelPlugin) {
                val childPlugin = child.plugin as StarUIPanelPlugin
                intrinsicSize = if (childPlugin.layoutEngine != null) {
                    childPlugin.layoutEngine!!.measure(
                        if (strategy is VerticalStrategy) availableCross else Float.MAX_VALUE,
                        if (strategy is HorizontalStrategy) availableCross else Float.MAX_VALUE
                    )
                } else {
                    Pair(child.width, child.height)
                }
            } else if (plugin.childMeasurables.containsKey(child)) {
                intrinsicSize = plugin.childMeasurables[child]!!.invoke(
                    if (strategy is VerticalStrategy) availableCross else Float.MAX_VALUE,
                    if (strategy is HorizontalStrategy) availableCross else Float.MAX_VALUE
                )
            } else {
                intrinsicSize = Pair(child.width, child.height)
            }

            // Clean NaN from bad leaf nodes (like missing images)
            val safeW = if (intrinsicSize.first.isNaN()) 10f else intrinsicSize.first
            val safeH = if (intrinsicSize.second.isNaN()) 10f else intrinsicSize.second
            val safeSize = Pair(safeW, safeH)

            childIntrinsicSizes[child] = safeSize
            log.info(" - Child Intrinsic: W: ${safeSize.first}, H: ${safeSize.second} | Margin: $childMargin")

            val (cw, ch) = safeSize
            var primarySize = if (strategy is VerticalStrategy) ch else cw
            val secondarySize = if (strategy is VerticalStrategy) cw else ch

            if (childSizing is FillCrossImpl) {
                primarySize = childSizing.primarySize
            }

            val cPriMargSum = if (strategy is VerticalStrategy) childMargin.top + childMargin.bottom else childMargin.left + childMargin.right
            val cSecMargSum = if (strategy is VerticalStrategy) childMargin.left + childMargin.right else childMargin.top + childMargin.bottom

            val isPrimaryFlex = childSizing is FlexImpl && childSizing.weight > 0f
            val isCrossFill = (childSizing is FlexImpl && childSizing.crossAxis is CrossFillImpl) || childSizing is FillCrossImpl

            fixedPrimary += cPriMargSum

            if (isPrimaryFlex) {
                if (isSelfWrap) {
                    throw IllegalStateException("StarUIFramework: Impossibility Detected! A Wrap StackLayout cannot contain Flex children.")
                }
                totalFlexWeight += childSizing.weight
            } else {
                fixedPrimary += primarySize
            }

            if (!isCrossFill) {
                maxSecondary = maxOf(maxSecondary, secondarySize + cSecMargSum)
            }
        }

        val totalSpacing = maxOf(0, children.size - 1) * spacing
        measuredPrimary = fixedPrimary + totalSpacing + (primaryPadding * 2)
        measuredSecondary = maxSecondary + (secondaryPadding * 2)

        val finalW = when (sizing) {
            is ExactImpl -> sizing.width
            else -> if (strategy is VerticalStrategy) measuredSecondary else measuredPrimary
        }
        val finalH = when (sizing) {
            is ExactImpl -> sizing.height
            else -> if (strategy is VerticalStrategy) measuredPrimary else measuredSecondary
        }

        log.info("StarUI Pass 1 Output: Requires W: $finalW, H: $finalH")
        return Pair(finalW, finalH)
    }

    override fun layout(assignedWidth: Float, assignedHeight: Float) {
        log.info("StarUI Pass 2 & 3: CONTAINER RESOLUTION & ALLOCATION | Assigned -> W: $assignedWidth, H: $assignedHeight")
        val children = panel.getChildrenCopy()
        if (children.isEmpty()) return

        val finalContainerPrimary = when (sizing) {
            is ExactImpl -> if (strategy is VerticalStrategy) sizing.height else sizing.width
            is FlexImpl -> if (strategy is VerticalStrategy) assignedHeight else assignedWidth
            is FillCrossImpl -> if (strategy is VerticalStrategy) assignedHeight else assignedWidth // FIX: Handle FillCross
            is WrapStackImpl -> measuredPrimary
            else -> measuredPrimary
        }

        val finalContainerSecondary = when (sizing) {
            is ExactImpl -> if (strategy is VerticalStrategy) sizing.width else sizing.height
            is FlexImpl -> if (strategy is VerticalStrategy) assignedWidth else assignedHeight
            is FillCrossImpl -> if (strategy is VerticalStrategy) assignedWidth else assignedHeight // FIX: Handle FillCross
            is WrapStackImpl -> measuredSecondary
            else -> measuredSecondary
        }

        val rawAvailableFlexSpace = finalContainerPrimary - measuredPrimary
        val availableFlexSpace = maxOf(0f, rawAvailableFlexSpace)

        children.forEach { child ->
            val childSizing = plugin.childSizing[child] ?: ContentSize.wrap()
            val childMargin = plugin.childMargins[child] ?: Margin()
            val cSecMargSum = if (strategy is VerticalStrategy) childMargin.left + childMargin.right else childMargin.top + childMargin.bottom

            val (intrinsicW, intrinsicH) = childIntrinsicSizes[child] ?: Pair(child.width, child.height)
            val intrinsicPrimary = if (strategy is VerticalStrategy) intrinsicH else intrinsicW
            val intrinsicSecondary = if (strategy is VerticalStrategy) intrinsicW else intrinsicH

            val isPrimaryFlex = childSizing is FlexImpl && childSizing.weight > 0f
            val isCrossFill = (childSizing is FlexImpl && childSizing.crossAxis is CrossFillImpl) || childSizing is FillCrossImpl
            val crossDirective = (childSizing as? FlexImpl)?.crossAxis

            val newPrimary = if (isPrimaryFlex) {
                if (totalFlexWeight > 0f) (childSizing.weight / totalFlexWeight) * availableFlexSpace else 0f
            } else if (childSizing is FillCrossImpl) {
                childSizing.primarySize
            } else {
                intrinsicPrimary
            }

            val newSecondary = when {
                isCrossFill -> finalContainerSecondary - cSecMargSum - (secondaryPadding * 2)
                crossDirective is CrossAspectRatioImpl -> {
                    if (intrinsicPrimary > 0f) newPrimary * (intrinsicSecondary / intrinsicPrimary) else intrinsicSecondary
                }
                crossDirective is CrossExactImpl -> crossDirective.size
                else -> intrinsicSecondary
            }

            val finalCW = if (strategy is VerticalStrategy) newSecondary else newPrimary
            val finalCH = if (strategy is VerticalStrategy) newPrimary else newSecondary
            child.setSize(finalCW, finalCH)

            if (child is CustomPanelAPI && child.plugin is StarUIPanelPlugin) {
                val childPlugin = child.plugin as StarUIPanelPlugin
                if (childPlugin.layoutEngine != null) {
                    childPlugin.layoutEngine!!.layout(finalCW, finalCH)
                    childPlugin.markLayoutResolved()
                }
            }
        }

        log.info("StarUI Pass 4: POSITIONING")
        var previousElement: UIComponentAPI? = null

        children.forEach { component ->
            val myMargin = plugin.childMargins[component] ?: Margin()
            val prevMargin = if (previousElement != null) plugin.childMargins[previousElement] ?: Margin() else Margin()

            strategy.applyPositioning(
                parent = panel,
                component = component,
                prev = previousElement,
                primaryPadding = primaryPadding,
                secondaryPadding = secondaryPadding,
                spacing = spacing,
                myMargin = myMargin,
                prevMargin = prevMargin
            )
            log.info(" - Placed child at Abs(X:${component.position.x}, Y:${component.position.y}) [W:${component.width}, H:${component.height}]")

            previousElement = component
        }

        strategy.setPanelSize(panel, finalContainerPrimary, finalContainerSecondary)
        childIntrinsicSizes.clear()
    }
}