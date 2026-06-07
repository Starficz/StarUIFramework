package org.starficz.starui.compose

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.core.addTooltip
import org.starficz.starui.core.applyAnchor
import org.starficz.starui.core.setMouseOverPad
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.children
import org.starficz.starui.core.parent

class Modifier private constructor(
    internal val operations: List<(UIComponentAPI) -> Unit> = emptyList()
) {
    constructor() : this(emptyList())

    fun margin(all: Float) = margin(all, all, all, all)

    fun margin(left: Float = 0f, right: Float = 0f, top: Float = 0f, bottom: Float = 0f): Modifier {
        val newMargin = Margin(left, right, top, bottom)
        val newOp: (UIComponentAPI) -> Unit = { component ->
            if (!StarUIContext.isTopLevel) {
                StarUIContext.currentPlugin.childMargins[component] = newMargin
            }
        }
        return Modifier(operations + newOp)
    }

    fun anchor(anchor: Anchor.AnchorData): Modifier {
        if (!StarUIContext.isTopLevel && StarUIContext.currentPlugin.isStackLayout) {
            throw IllegalStateException(
                "Modifier.anchor() cannot be used inside a StackLayout! " +
                        "StackLayouts automatically position their children. Use Margin/Padding/Sizing instead."
            )
        }

        val newOp: (UIComponentAPI) -> Unit = { component ->
            val targetComponent = when(anchor.reference) {
                is Anchor.AnchorReference.Parent -> component.parent
                is Anchor.AnchorReference.Previous -> {
                    component.parent?.children?.dropLast(1)?.lastOrNull() ?:
                    throw Error("Previous Component does not exist!")
                }
                is Anchor.AnchorReference.Sibling -> anchor.reference.component
            }

            var targetPadX = 0f
            var targetPadY = 0f
            var myPadX = 0f
            var myPadY = 0f

            if (!StarUIContext.isTopLevel) {
                targetComponent?.let{ StarUIContext.currentPlugin.childMargins[it] }?.let { targetMargin ->
                    if (anchor.reference !is Anchor.AnchorReference.Parent) {
                        if (anchor.refAnchorX == 0f && anchor.targetAnchorX == 1f) targetPadX += targetMargin.right
                        if (anchor.refAnchorX == 1f && anchor.targetAnchorX == 0f) targetPadX -= targetMargin.left
                        if (anchor.refAnchorY == 0f && anchor.targetAnchorY == 1f) targetPadY += targetMargin.top
                        if (anchor.refAnchorY == 1f && anchor.targetAnchorY == 0f) targetPadY -= targetMargin.bottom
                    }
                }

                StarUIContext.currentPlugin.childMargins[component]?.let { myMargin ->
                    if (anchor.refAnchorX == 0f) myPadX += myMargin.left
                    if (anchor.refAnchorX == 1f) myPadX -= myMargin.right
                    if (anchor.refAnchorY == 0f) myPadY += myMargin.bottom
                    if (anchor.refAnchorY == 1f) myPadY -= myMargin.top
                }
            }

            val finalOffsetX = anchor.offsetX + myPadX + targetPadX
            val finalOffsetY = anchor.offsetY + myPadY + targetPadY

            val bakedAnchor = anchor.copy(offsetX = finalOffsetX, offsetY = finalOffsetY)
            component.applyAnchor(bakedAnchor)
        }

        return Modifier(operations + newOp)
    }

    fun hitBoxPadding(pad: Float) = hitBoxPadding(pad, pad, pad, pad)

    fun hitBoxPadding(left: Float = 0f, right: Float = 0f, top: Float = 0f, bottom: Float = 0f): Modifier {
        val newOp: (UIComponentAPI) -> Unit = { it.setMouseOverPad(left, right, top, bottom) }
        return Modifier(operations + newOp)
    }

    fun tooltip(text: String, width: Float = 300f, location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE): Modifier {
        val newOp: (UIComponentAPI) -> Unit = { it.addTooltip(location, width, null) { tm -> tm.addPara(text, 0f) } }
        return Modifier(operations + newOp)
    }

    fun tooltip(width: Float = 300f, location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE,
                margin: Float? = null, builder: (@StarUIDsl TooltipMakerAPI).() -> Unit): Modifier {
        val newOp: (UIComponentAPI) -> Unit = { it.addTooltip(location, width, margin) { tm -> tm.builder() } }
        return Modifier(operations + newOp)
    }

    companion object {
        fun margin(all: Float) = Modifier().margin(all)
        fun margin(left: Float = 0f, right: Float = 0f, top: Float = 0f, bottom: Float = 0f) = Modifier().margin(left, right, top, bottom)
        fun anchor(anchor: Anchor.AnchorData) = Modifier().anchor(anchor)
        fun hitBoxPadding(pad: Float) = Modifier().hitBoxPadding(pad)
        fun hitBoxPadding(left: Float = 0f, right: Float = 0f, top: Float = 0f, bottom: Float = 0f) = Modifier().hitBoxPadding(left, right, top, bottom)
        fun tooltip(text: String, width: Float = 300f, location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE) = Modifier().tooltip(text, width, location)
        fun tooltip(width: Float, location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE, margin: Float? = null, builder: (@StarUIDsl TooltipMakerAPI).() -> Unit) = Modifier().tooltip(width, location, margin, builder)
    }
}