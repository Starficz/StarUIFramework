package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.ImageSize
import org.starficz.starui.compose.NativeImage
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.core.BoxedUIImage
import org.starficz.starui.core.BoxedUILabel
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

enum class TextAlign {
    Left,
    Center,
    Right;

    companion object {
        val Start = Left
        val End = Right
    }
}

enum class ContentScale {
    /** Scales the image uniformly (maintaining aspect ratio) so both dimensions fit inside the box. */
    Fit,
    /** Stretches the image non-uniformly to fill the exact bounds of the box. */
    FillBounds,
    /** Keeps the image at its native pixel resolution, centered inside the box. */
    None
}

typealias StarUIText = BoxedUILabel
typealias StarUIImage = BoxedUIImage
typealias StarUIShipDisplay = BoxedUIShipPreview

/**
 * Creates a native Starsector [BoxedUILabel] directly in this panel with zero wrapper [com.fs.starfarer.api.ui.CustomPanelAPI] nodes.
 */
fun UIPanelAPI.Text(
    text: String,
    size: Size = StarUITheme.current.text.size,
    modifier: Modifier = Modifier,
    font: Font? = StarUITheme.current.text.font,
    color: Color? = StarUITheme.current.text.color,
    textAlign: TextAlign = StarUITheme.current.text.textAlign,
    highlightedText: Collection<Pair<String, Color>>? = null
): BoxedUILabel {
    checkDslTarget()
    // Font, colour and highlights are fixed when a native label is made, so a call that changes them gets a new
    // label; the text itself is updated in place (highlighted labels excepted: their highlights index the text).
    val slot = childSlot("Text", listOf(font, color, highlightedText, if (highlightedText != null) text else null))
    val reused: BoxedUILabel? = slot.reused()
    val rawLabel = reused ?: this.addPara(text, font, color, highlightedText)
    if (reused != null && rawLabel.text != text) rawLabel.text = text

    fun applyTextAlign() {
        rawLabel.setAlignment(
            when (textAlign) {
                TextAlign.Left -> Alignment.LMID
                TextAlign.Center -> Alignment.MID
                TextAlign.Right -> Alignment.RMID
            }
        )
    }
    applyTextAlign()

    if (reused != null) {
        applyReuseBounds(rawLabel.boxedElement, size, measureTextSize(text, font))
    } else {
        val (initW, initH) = getInitialBounds(size, measureTextSize(text, font))
        rawLabel.setSize(initW, initH)
    }

    registerComponentSizing(rawLabel, size, isContainer = false)
    slot.remember(rawLabel.boxedElement, rawLabel)

    if (!StarUIContext.isTopLevel) {
        val parentPlugin = StarUIContext.currentPlugin
        val actual = rawLabel.boxedElement

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

/** Alias for [Text] for Gen 3 compatibility. */
fun UIPanelAPI.Label(
    text: String,
    size: Size = StarUITheme.current.text.size,
    modifier: Modifier = Modifier,
    font: Font? = StarUITheme.current.text.font,
    color: Color? = StarUITheme.current.text.color,
    align: TextAlign = StarUITheme.current.text.textAlign,
    highlightedText: Collection<Pair<String, Color>>? = null
): BoxedUILabel = Text(text, size, modifier, font, color, align, highlightedText)

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
 * Creates a native Starsector [BoxedUIShipPreview] directly in this panel with zero wrapper [com.fs.starfarer.api.ui.CustomPanelAPI] nodes.
 */
fun UIPanelAPI.ShipDisplay(
    size: PanelSize = StarUITheme.current.shipDisplay.size,
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

/** Alias for [ShipDisplay] for Gen 3 compatibility. */
fun UIPanelAPI.ShipPreview(
    size: PanelSize = StarUITheme.current.shipDisplay.size,
    modifier: Modifier = Modifier,
    fleetMember: FleetMemberAPI? = null,
    variant: ShipVariantAPI? = null,
    style: BoxedUIShipPreview.Style = StarUITheme.current.shipDisplay.vanillaStyle,
    isSchematicMode: Boolean = false
): BoxedUIShipPreview = ShipDisplay(
    size = size,
    modifier = modifier,
    fleetMember = fleetMember,
    variant = variant,
    style = style,
    isSchematicMode = isSchematicMode
)
