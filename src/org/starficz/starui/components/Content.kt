package org.starficz.starui.components

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.*
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.PaddingValues
import org.starficz.starui.runtime.UINode
import org.starficz.starui.vanilla.*
import org.starficz.starui.vanilla.ReflectionUtils.invoke
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
    val rawLabel = reused ?: newLabel(text, font, color, highlightedText)
    if (reused != null && rawLabel.text != text) rawLabel.text = text

    fun applyTextAlign() = rawLabel.setAlignment(textAlign.toVanilla())
    applyTextAlign()

    val initialMeasure = rawLabel.measure(text)

    // Vanilla lays wrapped text out again on every autoSizeToWidth, the costliest part of a layout pass with much
    // text. The wrapped height per width is kept for the current text, and the label itself is only sized again when
    // its text or width changed.
    var cachedText: String? = text
    var singleLine = initialMeasure   // rawLabel.measure(cachedText)
    val heightAtWidth = HashMap<Float, Float>(4)
    var labelSizedText: String? = null
    var labelSizedWidth = Float.NaN
    var labelSizedHeight = 0f
    /** Wraps the label at [width] (unless it already is) and returns the wrapped height. */
    fun autoSize(str: String, width: Float): Float {
        if (str == labelSizedText && width == labelSizedWidth) return labelSizedHeight
        rawLabel.autoSizeToWidth(width)
        labelSizedText = str
        labelSizedWidth = width
        // the label's own height changes again when its container sizes it: keep the wrapped one
        labelSizedHeight = rawLabel.height
        return labelSizedHeight
    }

    var node: UINode? = null
    node = finishLeaf(slot, rawLabel, size, modifier, intrinsic = initialMeasure,
        measure = { maxW, _ ->
            val currentStr = rawLabel.text
            if (currentStr.isEmpty()) {
                Pair(0f, 0f)
            } else {
                if (currentStr != cachedText) {
                    cachedText = currentStr
                    singleLine = rawLabel.measure(currentStr)
                    heightAtWidth.clear()
                }
                val (unwrappedW, singleLineH) = singleLine
                if (unwrappedW <= maxW) {
                    Pair(unwrappedW, singleLineH)
                } else {
                    val height = heightAtWidth.getOrPut(maxW) { autoSize(currentStr, maxW) }
                    Pair(maxW, height)
                }
            }
        },
        layout = { assignedW, assignedH ->
            val pad = node?.padding ?: PaddingValues.Zero
            autoSize(rawLabel.text, maxOf(1f, assignedW - pad.horizontal))
            rawLabel.setSize(assignedW, assignedH)
            applyTextAlign()
        }
    )
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
    val rawImage = slot.reused<BoxedUIImage>()
        ?: BoxedUIImage(VanillaFactory.image(spritePath).also { this.addComponent(it) })

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

    val node = finishLeaf(slot, rawImage.obf, size, modifier, handle = rawImage, intrinsic = calcIntrinsic(),
        measure = { availW, availH -> calcIntrinsic(availW, availH) },
        layout = { assignedW, assignedH ->
            applyScaleMode()
            rawImage.setSize(assignedW, assignedH)
        }
    )
    if (size is AspectWrapImage) node.aspectRatio = { if (origH > 0f) origW / origH else 1f }
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
    newBorderStyle: Boolean = StarUITheme.current.shipDisplay.newBorderStyle,
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
    val rawShip = newShipPreview(fleetMember, style ?: theme.style,
        labelColor ?: theme.labelColor ?: Global.getSettings().getColor("textFriendColor"))
    rawShip.setSize(initW, initH)   // before the variant: vanilla lays the ship out for its size

    if (variant != null) {
        if (shipName != null) rawShip.setVariant(variant, shipName)
        else rawShip.variant = variant
    }
    rawShip.showFighters = showFighters
    rawShip.showDestroyedFighters = showDestroyedFighters
    rawShip.setShowBorder(showBorder)
    rawShip.setBorderNewStyle(newBorderStyle)
    borderColor?.let { rawShip.borderColor = it }
    rawShip.setScaleDownSmallerShips(scaleSmallerShips)
    rawShip.exactScale = exactScale
    rawShip.setScanlines(scanlines)
    if (hideLabels) rawShip.hideLabels()

    finishLeaf(slot, rawShip.obf, size, modifier, handle = rawShip, intrinsic = Pair(100f, 100f),
        measure = { maxW, maxH -> Pair(if (maxW == Float.MAX_VALUE) 100f else maxW, if (maxH == Float.MAX_VALUE) 100f else maxH) },
        layout = { assignedW, assignedH -> rawShip.setSize(assignedW, assignedH) }
    )
    return rawShip
}

/**
 * A vanilla paragraph label (only TooltipMakerAPI makes one), added to this panel. [highlightedText] goes through
 * vanilla's String.format, so a bare % in [text] is escaped.
 */
private fun UIPanelAPI.newLabel(
    text: String, font: Font?, color: Color?, highlightedText: Collection<Pair<String, Color>>?
): LabelAPI {
    val label = VanillaFactory.onScratch {
        if (font != null) setParaFont(font.path) else setParaFontDefault()
        if (highlightedText != null) {
            val (highlights, highlightColors) = highlightedText.unzip()
            addPara(text.replace("%", "%%"), 0f, highlightColors.toTypedArray(), *highlights.toTypedArray())
        } else {
            addPara(text, 0f)
        }
    }
    color?.let { label.setColor(it) }
    this.addComponent(label)
    label.autoSize()
    return label
}

/** Vanilla's ship preview (no API factory: its constructors, found at load), added to this panel. */
private fun UIPanelAPI.newShipPreview(
    fleetMember: FleetMemberAPI?, style: BoxedUIShipPreview.Style, labelColor: Color
): BoxedUIShipPreview {
    val preview = if (fleetMember != null) {
        shipPreviewConstructor(VanillaClasses.shipPreviewForMember).newInstance(fleetMember)
    } else {
        val enum = VanillaClasses.shipPreviewStyles?.find { it?.invoke("name") == style.name }
        shipPreviewConstructor(VanillaClasses.shipPreviewWithStyle).newInstance(enum, labelColor)
    } as UIPanelAPI
    this.addComponent(preview)
    return BoxedUIShipPreview(preview)
}

private fun shipPreviewConstructor(found: ReflectionUtils.ReflectedConstructor?): ReflectionUtils.ReflectedConstructor =
    found ?: throw IllegalStateException(
        "StarUI: ShipDisplay needs vanilla's ship preview, which was not found at application load. A game update " +
            "may have changed it.")
