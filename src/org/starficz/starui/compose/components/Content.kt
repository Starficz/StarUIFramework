package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.AspectWrapImage
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
 * A line or paragraph of text. Wraps the text by default; with a fill or exact width it wraps lines to fit.
 * [highlightedText] colours parts of it: `listOf("1200" to Color.YELLOW)`.
 * Returns the vanilla [LabelAPI]. Setting `label.text` later is fine: the layout around it updates.
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
 * A sprite, by its path (`"graphics/icons/cargo/supplies.png"`). [size] also takes `ImageSize.native()` (the
 * sprite's own size) and `ImageSize.aspectWrap()`; [contentScale] says how the sprite fills a box of another shape.
 * The sprite must be loaded, or it draws nothing.
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

    // The size the image wants in a room of availW x availH (Float.MAX_VALUE where the room is unbounded).
    fun calcIntrinsic(availW: Float = Float.MAX_VALUE, availH: Float = Float.MAX_VALUE): Pair<Float, Float> {
        var w = origW
        var h = origH
        if (size is AspectWrapImage && origW > 0f && origH > 0f) {
            // The largest scale at which the sprite fits the room on both axes; the sprite's own size if unbounded.
            val fitW = if (availW == Float.MAX_VALUE) Float.MAX_VALUE else availW / origW
            val fitH = if (availH == Float.MAX_VALUE) Float.MAX_VALUE else availH / origH
            val scale = minOf(fitW, fitH)
            if (scale != Float.MAX_VALUE) return Pair(origW * scale, origH * scale)
        }
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

        parentPlugin.childMeasurables[actual] = { availW, availH -> calcIntrinsic(availW, availH) }
        parentPlugin.childLayoutDelegates[actual] = { assignedW, assignedH ->
            applyScaleMode()
            rawImage.setSize(assignedW, assignedH)
        }
    }

    modifier.operations.forEach { it(rawImage) }
    return rawImage
}

/**
 * Vanilla's ship display, showing [fleetMember] or [variant]. Its size is exact or fill (it has no content size).
 *
 * Vanilla shrinks the ship to 95% of the space and draws it 3% above centre. `exactScale = 1f` draws it at its real
 * size instead, centred and pixel-sharp (`1f / Global.getSettings().screenScaleMult` for one sprite pixel per screen
 * pixel). It is made new on every rebuild, since the ship it shows may have changed.
 *
 * Labels show only for a [variant] with a [shipName]: [style] `NORMAL` puts the name at the top (Orbitron 20) and the
 * hull class and designation at the bottom, `MINI` the name alone at the top left (Victor 10), `NONE` nothing. They are
 * as wide as the display minus 10, so a small display wraps them. A [fleetMember] display never has labels (vanilla
 * makes it with style NONE), so [style], [labelColor] and [shipName] with a fleetMember are an error.
 *
 * [scaleSmallerShips] shrinks smaller hulls by a fixed table (frigate 50%, destroyer 55%, cruiser 70%, fighter 75%), so
 * a fleet of mixed sizes reads by size. [scanlines] darkens the display with vanilla's scanline overlay: 0 is none,
 * 1 solid black (vanilla's own displays use 0.2 to 0.4).
 */
fun UIPanelAPI.ShipDisplay(
    size: BoundedSize = StarUITheme.current.shipDisplay.size,
    modifier: Modifier = Modifier,
    fleetMember: FleetMemberAPI? = null,
    variant: ShipVariantAPI? = null,
    style: BoxedUIShipPreview.Style? = null,
    labelColor: Color? = null,
    shipName: String? = null,
    hideLabels: Boolean = false,
    showFighters: Boolean = StarUITheme.current.shipDisplay.showFighters,
    showDestroyedFighters: Boolean = StarUITheme.current.shipDisplay.showDestroyedFighters,
    showBorder: Boolean = StarUITheme.current.shipDisplay.showBorder,
    borderNewStyle: Boolean = StarUITheme.current.shipDisplay.newBorderStyle,
    borderColor: Color? = null,
    scaleSmallerShips: Boolean = StarUITheme.current.shipDisplay.scaleSmallerShips,
    scanlines: Float = StarUITheme.current.shipDisplay.scanlines,
    exactScale: Float? = null
): BoxedUIShipPreview {
    checkDslTarget()
    require(fleetMember == null || (style == null && labelColor == null && shipName == null)) {
        "ShipDisplay: a fleetMember display has no labels (vanilla makes it with style NONE), so style, labelColor " +
            "and shipName do nothing with one. Pass the fleet member's variant instead to get labels."
    }
    // Never reused: a ship display snapshots its fleet member or variant when it is made, and those objects are
    // usually edited in place (the refit screen), so "same arguments" does not mean "same picture".
    val slot = childSlot("ShipDisplay", Any())
    val (initW, initH) = getInitialBounds(size, Pair(100f, 100f))

    val theme = StarUITheme.current.shipDisplay
    val rawShip = this.addShipPreview(initW, initH, fleetMember, style ?: theme.vanillaStyle,
        labelColor ?: theme.labelColor ?: Global.getSettings().getColor("textFriendColor"))

    if (variant != null) {
        if (shipName != null) rawShip.setVariant(variant, shipName)
        else rawShip.variant = variant
    }
    rawShip.showFighters = showFighters
    rawShip.showDestroyedFighters = showDestroyedFighters
    rawShip.setShowBorder(showBorder)
    rawShip.setBorderNewStyle(borderNewStyle)
    borderColor?.let { rawShip.borderColor = it }
    rawShip.setScaleDownSmallerShips(scaleSmallerShips)
    rawShip.exactScale = exactScale
    rawShip.setScanlines(scanlines)
    if (hideLabels) rawShip.hideLabels()

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
