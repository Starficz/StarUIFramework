package org.starficz.starui.compose

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.core.setSize
import org.starficz.starui.core.ReflectionUtils.getMethodsMatching
import org.starficz.starui.core.height
import org.starficz.starui.core.width


internal data class AnchorMath(val targetAnchor: Float, val myAnchor: Float, val offset: Float)

sealed interface VGrow {
    val isTopDown: Boolean
    companion object {
        val Downwards: VGrow = GrowDownwardsImpl
        val Upwards: VGrow = GrowUpwardsImpl
    }
}

sealed interface HGrow {
    val isLeftToRight: Boolean
    companion object {
        val Rightwards: HGrow = GrowRightwardsImpl
        val Leftwards: HGrow = GrowLeftwardsImpl
    }
}

sealed interface XAlign {
    val alignValue: Float
    companion object {
        val Left: XAlign = XAlignLeftImpl
        val Center: XAlign = XAlignCenterImpl
        val Right: XAlign = XAlignRightImpl
    }
}

sealed interface YAlign {
    val alignValue: Float
    companion object {
        val Top: YAlign = YAlignTopImpl
        val Center: YAlign = YAlignCenterImpl
        val Bottom: YAlign = YAlignBottomImpl
    }
}

internal data object GrowDownwardsImpl : VGrow { override val isTopDown = true }
internal data object GrowUpwardsImpl : VGrow { override val isTopDown = false }
internal data object GrowRightwardsImpl : HGrow { override val isLeftToRight = true }
internal data object GrowLeftwardsImpl : HGrow { override val isLeftToRight = false }
internal data object XAlignLeftImpl : XAlign { override val alignValue = 0f }
internal data object XAlignCenterImpl : XAlign { override val alignValue = 0.5f }
internal data object XAlignRightImpl : XAlign { override val alignValue = 1f }
internal data object YAlignTopImpl : YAlign { override val alignValue = 1f }
internal data object YAlignCenterImpl : YAlign { override val alignValue = 0.5f }
internal data object YAlignBottomImpl : YAlign { override val alignValue = 0f }


internal interface LayoutStrategy {

    fun applyPositioning(
        parent: UIPanelAPI, component: UIComponentAPI, prev: UIComponentAPI?,
        primaryPadding: Float, secondaryPadding: Float, spacing: Float,
        myMargin: Margin, prevMargin: Margin
    )

    fun setPanelSize(panel: UIPanelAPI, primaryTotalSize: Float, secondaryMaxSize: Float)

    fun calcPrimaryMath(
        isPositiveProgression: Boolean, padding: Float, spacing: Float,
        myStartMargin: Float, prevEndMargin: Float,
        isFirst: Boolean
    ): AnchorMath {
        val targetAnchor: Float
        val myAnchor: Float
        val offset: Float

        if (isPositiveProgression) {
            // Left-to-Right or Bottom-to-Top
            if (isFirst) {
                targetAnchor = 0.0f
                myAnchor = 0.0f
                offset = padding + myStartMargin
            } else {
                targetAnchor = 1.0f
                myAnchor = 0.0f
                offset = spacing + prevEndMargin + myStartMargin
            }
        } else {
            // Right-to-Left or Top-to-Bottom
            if (isFirst) {
                targetAnchor = 1.0f
                myAnchor = -1.0f
                offset = -(padding + myStartMargin)
            } else {
                targetAnchor = 0.0f
                myAnchor = -1.0f
                offset = -(spacing + prevEndMargin + myStartMargin)
            }
        }
        return AnchorMath(targetAnchor, myAnchor, offset)
    }

    fun applyRelativeTo(
        component: UIComponentAPI, targetPos: PositionAPI?,
        targetAnchorX: Float, targetAnchorY: Float,
        myAnchorX: Float, myAnchorY: Float,
        offsetX: Float, offsetY: Float
    ) {
        val floatType = Float::class.javaPrimitiveType!!
        val paramTypes = arrayOf<Class<*>?>(
            component.position::class.java, floatType, floatType, floatType, floatType, floatType, floatType
        )
        component.position.getMethodsMatching("relativeTo", parameterTypes = paramTypes)[0]
            .invoke(component.position, targetPos, targetAnchorX, targetAnchorY, myAnchorX, myAnchorY, offsetX, offsetY)
    }
}

internal class VerticalStrategy(
    private val arrangement: VGrow,
    private val alignment: XAlign
) : LayoutStrategy {

    override fun setPanelSize(panel: UIPanelAPI, primaryTotalSize: Float, secondaryMaxSize: Float) {
        panel.setSize(secondaryMaxSize, primaryTotalSize)
    }

    override fun applyPositioning(
        parent: UIPanelAPI, component: UIComponentAPI, prev: UIComponentAPI?,
        primaryPadding: Float, secondaryPadding: Float, spacing: Float,
        myMargin: Margin, prevMargin: Margin
    ) {
        val isTopDown = arrangement.isTopDown
        val isFirst = prev == null

        val primary = calcPrimaryMath(!isTopDown, primaryPadding, spacing, if (isTopDown) myMargin.top else myMargin.bottom, if (isTopDown) prevMargin.bottom else prevMargin.top, isFirst)

        val targetAnchorX = alignment.alignValue
        val myAnchorX = -alignment.alignValue

        // Linear interpolation mapping alignment (0.0 to 1.0) to padding and margin offsets!
        val paddingOffset = secondaryPadding * (1f - targetAnchorX * 2f)
        val marginOffset = (myMargin.left * (1f - targetAnchorX)) - (myMargin.right * targetAnchorX)
        val totalOffset = paddingOffset + marginOffset

        val crossOffset = if (isFirst) {
            totalOffset
        } else {
            val parentAnchorPoint = parent.position.x + parent.width * targetAnchorX
            val prevAnchorPoint = prev!!.position.x + prev.width * targetAnchorX
            val driftCorrection = parentAnchorPoint - prevAnchorPoint

            driftCorrection + totalOffset
        }

        val targetPos = prev?.position
        applyRelativeTo(component, targetPos, targetAnchorX, primary.targetAnchor, myAnchorX, primary.myAnchor, crossOffset, primary.offset)
    }
}

internal class HorizontalStrategy(
    private val arrangement: HGrow,
    private val alignment: YAlign
) : LayoutStrategy {

    override fun setPanelSize(panel: UIPanelAPI, primaryTotalSize: Float, secondaryMaxSize: Float) {
        panel.setSize(primaryTotalSize, secondaryMaxSize)
    }

    override fun applyPositioning(
        parent: UIPanelAPI, component: UIComponentAPI, prev: UIComponentAPI?,
        primaryPadding: Float, secondaryPadding: Float, spacing: Float,
        myMargin: Margin, prevMargin: Margin
    ) {
        val isLeftToRight = arrangement.isLeftToRight
        val isFirst = prev == null

        val primary = calcPrimaryMath(isLeftToRight, primaryPadding, spacing, if (isLeftToRight) myMargin.left else myMargin.right, if (isLeftToRight) prevMargin.right else prevMargin.left, isFirst)

        val targetAnchorY = alignment.alignValue
        val myAnchorY = -alignment.alignValue

        // Linear interpolation mapping alignment (0.0 to 1.0) to padding and margin offsets!
        val paddingOffset = secondaryPadding * (1f - targetAnchorY * 2f)
        val marginOffset = (myMargin.bottom * (1f - targetAnchorY)) - (myMargin.top * targetAnchorY)
        val totalOffset = paddingOffset + marginOffset

        val crossOffset = if (isFirst) {
            totalOffset
        } else {
            val parentAnchorPoint = parent.position.y + parent.height * targetAnchorY
            val prevAnchorPoint = prev!!.position.y + prev.height * targetAnchorY
            val driftCorrection = parentAnchorPoint - prevAnchorPoint

            driftCorrection + totalOffset
        }

        val targetPos = prev?.position
        applyRelativeTo(component, targetPos, primary.targetAnchor, targetAnchorY, primary.myAnchor, myAnchorY, primary.offset, crossOffset)
    }
}

internal fun UIPanelAPI.StackLayout(
    strategy: LayoutStrategy,
    size: StackSize,
    modifier: Modifier = Modifier(),
    primaryPadding: Float = 0f,
    secondaryPadding: Float = 0f,
    spacing: Float = 0f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    val plugin = StarUIPanelPlugin()
    plugin.isStackLayout = true

    val panel = Global.getSettings().createCustom(1f, 1f, plugin)
    plugin.customPanel = panel

    plugin.layoutEngine = StackLayoutEngine(
        panel = panel,
        plugin = plugin,
        strategy = strategy,
        primaryPadding = primaryPadding,
        secondaryPadding = secondaryPadding,
        spacing = spacing,
        sizing = size
    )

    this.addComponent(panel)

    if (!StarUIContext.isTopLevel) { StarUIContext.currentPlugin.childSizing[panel] = size }

    modifier.operations.forEach { it(panel) }

    StarUIContext.withPlugin(plugin) { panel.builder() }

    return panel
}