package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.BoundedSize
import org.starficz.starui.compose.ContentScale
import org.starficz.starui.compose.ImageSize
import org.starficz.starui.compose.NativeImage
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.TextAlign
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.core.BoxedUIImage
import org.starficz.starui.core.BoxedUIShipPreview
import org.starficz.starui.core.Font
import org.starficz.starui.core.addImage
import org.starficz.starui.core.addPara
import org.starficz.starui.core.addShipPreview
import org.starficz.starui.core.height
import org.starficz.starui.core.measureTextSize
import org.starficz.starui.core.setSize
import org.starficz.starui.core.width
import java.awt.Color

/** The vanilla label alignment for [this], vertically centred as vanilla text is. */
internal fun TextAlign.toVanilla(): com.fs.starfarer.api.ui.Alignment = when (this) {
    TextAlign.Left -> com.fs.starfarer.api.ui.Alignment.LMID
    TextAlign.Center -> com.fs.starfarer.api.ui.Alignment.MID
    TextAlign.Right -> com.fs.starfarer.api.ui.Alignment.RMID
}

/**
 * Creates a native Starsector label ([LabelAPI]) directly in this panel with zero wrapper
 * [com.fs.starfarer.api.ui.CustomPanelAPI] nodes. Changing its text later (`label.text = ...`) re-lays out the
 * surrounding StarUI layout on the next frame.
 */
fun UIPanelAPI.Text(
    text: String,
    size: Size = StarUITheme.current.text.size,
    modifier: Modifier = Modifier,
    font: Font? = StarUITheme.current.text.font,
    color: Color? = StarUITheme.current.text.color,
    textAlign: TextAlign = StarUITheme.current.text.textAlign,
    highlightedText: Collection<Pair<String, Color>>? = null
): LabelAPI {
    checkDslTarget()
    // Font, colour and highlights are fixed when a native label is made, so a call that changes them gets a new
    // label; the text itself is updated in place (highlighted labels excepted: their highlights index the text).
    val slot = childSlot("Text", listOf(font, color, highlightedText, if (highlightedText != null) text else null))
    val reused: LabelAPI? = slot.reused()
    val rawLabel = reused ?: this.addPara(text, font, color, highlightedText)
    if (reused != null && rawLabel.text != text) rawLabel.text = text

    fun applyTextAlign() = rawLabel.setAlignment(textAlign.toVanilla())
    applyTextAlign()

    if (reused != null) {
        applyReuseBounds(rawLabel, size, measureTextSize(text, font))
    } else {
        val (initW, initH) = getInitialBounds(size, measureTextSize(text, font))
        rawLabel.setSize(initW, initH)
    }

    registerComponentSizing(rawLabel, size, isContainer = false)
    slot.remember(rawLabel, rawLabel)

    if (!StarUIContext.isTopLevel) {
        val parentPlugin = StarUIContext.currentPlugin
        val actual: UIComponentAPI = rawLabel

        parentPlugin.childMeasurables[actual] = { maxW, _ ->
            val currentStr = rawLabel.text
            if (currentStr.isEmpty()) {
                Pair(0f, 0f)
            } else {
                val (unwrappedW, singleLineH) = measureTextSize(currentStr, font)
                if (unwrappedW <= maxW) {
                    Pair(unwrappedW, singleLineH)
                } else {
                    rawLabel.autoSizeToWidth(maxW)
                    Pair(maxW, rawLabel.height)
                }
            }
        }

        parentPlugin.childLayoutDelegates[actual] = { assignedW, assignedH ->
            val pad = parentPlugin.childPadding[actual] ?: PaddingValues()
            val availW = maxOf(1f, assignedW - pad.horizontal)
            rawLabel.autoSizeToWidth(availW)
            rawLabel.setSize(assignedW, assignedH)
            applyTextAlign()
        }
    }

    modifier.operations.forEach { it(rawLabel) }
    return rawLabel
}

/**
 * Creates a native Starsector [BoxedUIImage] directly in this panel with zero wrapper [com.fs.starfarer.api.ui.CustomPanelAPI] nodes.
 */
fun UIPanelAPI.Image(
    spritePath: String,
    size: ImageSize = StarUITheme.current.image.size,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = StarUITheme.current.image.contentScale,
    withOutline: Boolean = StarUITheme.current.image.withOutline,
    textureClamp: Boolean = StarUITheme.current.image.textureClamp,
    forceNoRounding: Boolean = StarUITheme.current.image.forceNoRounding,
    renderSchematic: Boolean = StarUITheme.current.image.renderSchematic,
    showBorder: Boolean = StarUITheme.current.image.showBorder,
    borderColor: Color? = StarUITheme.current.image.borderColor
): BoxedUIImage {
    checkDslTarget()
    val slot = childSlot("Image", spritePath)
    val rawImage = slot.reused<BoxedUIImage>() ?: this.addImage(spritePath, 100f, 100f)

    rawImage.sizeToOriginalSpriteSize()
    val origW = rawImage.width
    val origH = rawImage.height

    rawImage.outline = withOutline
    rawImage.textureClamp = textureClamp
    rawImage.forceNoRounding = forceNoRounding
    rawImage.setRenderSchematic(renderSchematic)
    rawImage.setShowBorder(showBorder)
    borderColor?.let { rawImage.borderColor = it }

    fun applyScaleMode() {
        when (contentScale) {
            ContentScale.Fit -> {
                rawImage.resize = true
                rawImage.setStretch(false)
            }
            ContentScale.FillBounds -> {
                rawImage.resize = true
                rawImage.setStretch(true)
            }
            ContentScale.None -> {
                rawImage.resize = false
                rawImage.restoreImageSize()
            }
        }
    }
    applyScaleMode()

    fun calcIntrinsic(): Pair<Float, Float> {
        var w = origW
        var h = origH
        if (size is NativeImage) {
            w *= size.scale
            h *= size.scale
            val ar = rawImage.originalAspectRatio
            if (size.maxWidth != null && w > size.maxWidth) {
                w = size.maxWidth
                h = w / ar
            }
            if (size.maxHeight != null && h > size.maxHeight) {
                h = size.maxHeight
                w = h * ar
            }
        }
        return Pair(w, h)
    }

    val (initW, initH) = getInitialBounds(size, calcIntrinsic())
    rawImage.setSize(initW, initH)

    registerComponentSizing(rawImage, size, isContainer = false)
    slot.remember(rawImage.boxedElement, rawImage)

    if (!StarUIContext.isTopLevel) {
        val parentPlugin = StarUIContext.currentPlugin
        val actual = rawImage.boxedElement

        parentPlugin.childMeasurables[actual] = { _, _ -> calcIntrinsic() }
        parentPlugin.childLayoutDelegates[actual] = { assignedW, assignedH ->
            applyScaleMode()
            rawImage.setSize(assignedW, assignedH)
        }
    }

    modifier.operations.forEach { it(rawImage) }
    return rawImage
}

/**
 * Creates a native Starsector [BoxedUIShipPreview] directly in this panel with zero wrapper
 * [com.fs.starfarer.api.ui.CustomPanelAPI] nodes. A ship display has no size of its own, so its size is exact or fill.
 *
 * Vanilla fits the ship into the display and then always shrinks it to 95%, 3% above centre. [exactScale] draws it at
 * exactly that scale instead (1 = one sprite pixel per UI unit; `1f / Global.getSettings().screenScaleMult` for one
 * per screen pixel), centred and on whole pixels.
 */
fun UIPanelAPI.ShipDisplay(
    size: BoundedSize = StarUITheme.current.shipDisplay.size,
    modifier: Modifier = Modifier,
    fleetMember: FleetMemberAPI? = null,
    variant: ShipVariantAPI? = null,
    style: BoxedUIShipPreview.Style = StarUITheme.current.shipDisplay.vanillaStyle,
    color: Color = StarUITheme.current.shipDisplay.color ?: Global.getSettings().getColor("textFriendColor"),
    isSchematicMode: Boolean = false,
    showFighters: Boolean = StarUITheme.current.shipDisplay.showFighters,
    showDestroyedFighters: Boolean = StarUITheme.current.shipDisplay.showDestroyedFighters,
    showBorder: Boolean = StarUITheme.current.shipDisplay.showBorder,
    borderNewStyle: Boolean = StarUITheme.current.shipDisplay.newBorderStyle,
    borderColor: Color? = null,
    scaleSmallerShips: Float? = StarUITheme.current.shipDisplay.scaleSmallerShips,
    exactScale: Float? = null,
    scanlineAlpha: Float? = null,
    tintAlpha: Float? = null,
    isDisabled: Boolean = false,
    hideLabels: Boolean = false,
    labelColor: Color? = null,
    title: String? = null,
    subtitle: String? = null
): BoxedUIShipPreview {
    checkDslTarget()
    // Never reused: a ship display snapshots its fleet member or variant when it is made, and those objects are
    // usually edited in place (the refit screen), so "same arguments" does not mean "same picture".
    val slot = childSlot("ShipDisplay", Any())
    val (initW, initH) = getInitialBounds(size, Pair(100f, 100f))

    val rawShip = this.addShipPreview(initW, initH, fleetMember, style, color)
    rawShip.isSchematicMode = isSchematicMode

    if (variant != null) {
        if (title != null || subtitle != null) rawShip.setVariant(variant, title, subtitle)
        else rawShip.variant = variant
    }
    rawShip.showFighters = showFighters
    rawShip.showDestroyedFighters = showDestroyedFighters
    rawShip.setShowBorder(showBorder)
    rawShip.setBorderNewStyle(borderNewStyle)
    borderColor?.let { rawShip.borderColor = it }
    scaleSmallerShips?.let { rawShip.setScaleDownSmallerShipsMagnitude(it) }
    rawShip.exactScale = exactScale
    if (scanlineAlpha != null && tintAlpha != null) rawShip.adjustOverlay(scanlineAlpha, tintAlpha)
    if (isDisabled) rawShip.makeLookDisabled()
    if (hideLabels) rawShip.hideLabels()
    labelColor?.let { rawShip.setLabelColor(it) }

    registerComponentSizing(rawShip, size, isContainer = false)
    slot.remember(rawShip.boxedElement, rawShip)

    if (!StarUIContext.isTopLevel) {
        val parentPlugin = StarUIContext.currentPlugin
        val actual = rawShip.boxedElement

        parentPlugin.childMeasurables[actual] = { maxW, maxH ->
            val w = if (maxW == Float.MAX_VALUE) 100f else maxW
            val h = if (maxH == Float.MAX_VALUE) 100f else maxH
            Pair(w, h)
        }

        parentPlugin.childLayoutDelegates[actual] = { assignedW, assignedH ->
            rawShip.setSize(assignedW, assignedH)
        }
    }

    modifier.operations.forEach { it(rawShip) }
    return rawShip
}
