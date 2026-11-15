package org.starficz.starui.components

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.*
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.PaddingValues
import org.starficz.starui.patches.LabelTextPatch
import org.starficz.starui.runtime.ChildSlot
import org.starficz.starui.runtime.StarUIPanelPlugin
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
 * High level element that displays text: a vanilla label.
 *
 * The default [font], [color], [size] and [textAlign] come from the [StarUITheme] provided by [Theme]. If a
 * parameter is set here, it will always be used; if it is not set, the value from the theme is used instead. A
 * [font] or [color] that is `null` in both falls back to vanilla's own paragraph font and text colour.
 *
 * By default the [Text] wraps its text: it is as wide as its longest line. With an exact or fill width, the text is
 * wrapped to fit that width and the [Text] grows in height to show every line.
 *
 * With `overflow = TextOverflow.Ellipsis` it stays on one line instead: text wider than the room it gets (its exact or
 * fill width, or what its container has left) is cut and ends in "...". A cut [Text] shows the whole text in a
 * tooltip, unless it has a tooltip of its own.
 *
 * The returned [LabelAPI] can be changed later: setting `label.text` lays out the components around it again (for a
 * cut [Text], `label.text` reads the shortened text it shows).
 *
 * Example usage:
 *
 * ```kotlin
 * Text("Supplies: 1200", highlightedText = listOf("1200" to VanillaColors.highlight))
 * Text(ship.name, Size.fillMaxWidth(), overflow = TextOverflow.Ellipsis)
 * ```
 *
 * @param text the text to be displayed
 * @param size the size of the [Text]. By default it wraps its text
 * @param modifier the [Modifier] to be applied to this text
 * @param font the font of the text. `null` is vanilla's paragraph font
 * @param color the colour of the text. `null` is vanilla's text colour
 * @param textAlign the alignment of the text within the lines of the paragraph
 * @param highlightedText parts of [text] drawn in another colour, each one a piece of the text and its colour, in
 *   the order they appear in [text]. Vanilla's label does the matching, as for `addPara` with highlights: each piece
 *   is found after the previous one, as a whole word (bounded by spaces or punctuation), so `"1"` colours the 1 of
 *   "Costs 1 point" and never the 1 of "10". A highlight that a cut leaves out is not drawn
 * @param overflow what happens to text wider than its room: [TextOverflow.Wrap] onto more lines, or
 *   [TextOverflow.Ellipsis], one line cut short with "..."
 * @return the vanilla label
 */
fun UIPanelAPI.Text(
    text: String,
    size: Size = StarUITheme.current.text.size,
    modifier: Modifier = Modifier,
    font: Font? = StarUITheme.current.text.font,
    color: Color? = StarUITheme.current.text.color,
    textAlign: TextAlign = StarUITheme.current.text.textAlign,
    highlightedText: Collection<Pair<String, Color>>? = null,
    overflow: TextOverflow = StarUITheme.current.text.overflow
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

    if (overflow == TextOverflow.Ellipsis) {
        ellipsisText(slot, rawLabel, text, initialMeasure, size, modifier, ::applyTextAlign)
        return rawLabel
    }

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
 * The one-line [Text] of `TextOverflow.Ellipsis`. The label holds what it shows, so the full text is kept here: a
 * label text that is not the one this put there came from elsewhere (a `setText` call) and becomes the full text.
 */
private fun ellipsisText(
    slot: ChildSlot, rawLabel: LabelAPI, text: String, initialMeasure: Pair<Float, Float>, size: Size,
    modifier: Modifier, applyTextAlign: () -> Unit
) {
    var fullText = text
    var shownText: String? = null
    var measuredText = text
    var fullMeasure = initialMeasure   // rawLabel.measure(measuredText)
    fun currentFullText(): String {
        val onLabel = rawLabel.text
        if (onLabel != shownText) fullText = onLabel
        return fullText
    }
    // The full text in a tooltip while the text is cut, unless a Modifier.tooltip of this build is on the label (known
    // once finishLeaf applied the modifiers). A tooltip already on the label is taken as one a previous build of it
    // added: the layout below replaces or removes it.
    var ownTooltipAllowed = false
    var tooltipShows: String? = null

    var node: UINode? = null
    node = finishLeaf(slot, rawLabel, size, modifier, intrinsic = initialMeasure,
        measure = { maxW, _ ->
            val full = currentFullText()
            if (full != measuredText) {
                measuredText = full
                fullMeasure = rawLabel.measure(full)
            }
            if (full.isEmpty()) Pair(0f, 0f) else Pair(minOf(fullMeasure.first, maxW), fullMeasure.second)
        },
        layout = { assignedW, assignedH ->
            val pad = node?.padding ?: PaddingValues.Zero
            val room = maxOf(0f, assignedW - pad.horizontal)
            val full = currentFullText()
            val shown = ellipsize(full, room) { rawLabel.measure(it).first }
            if (rawLabel.text != shown) LabelTextPatch.ownWrite { rawLabel.text = shown }
            shownText = shown
            rawLabel.autoSizeToWidth(maxOf(1f, room))
            rawLabel.setSize(assignedW, assignedH)
            applyTextAlign()

            if (ownTooltipAllowed) {
                val wanted = if (shown != full) full else null
                if (wanted != tooltipShows) {
                    if (wanted == null) rawLabel.removeTooltip()
                    else rawLabel.addTooltip(TooltipMakerAPI.TooltipLocation.ABOVE, ELLIPSIS_TOOLTIP_WIDTH) { it.addPara(wanted, 0f) }
                    tooltipShows = wanted
                }
            }
        }
    )
    ownTooltipAllowed = StarUIPanelPlugin.getAttached(rawLabel)?.tooltipAppliedThisBuild != true
    if (ownTooltipAllowed && rawLabel.hasTooltip()) tooltipShows = ""
}

private const val ELLIPSIS = "..."
private const val ELLIPSIS_TOOLTIP_WIDTH = 400f

/**
 * [text] as it fits [room] wide: whole, or its longest start that fits with "..." after it (trailing spaces dropped),
 * or "" when not even "..." fits. [width] measures a string in the label's font.
 */
internal fun ellipsize(text: String, room: Float, width: (String) -> Float): String {
    if (text.isEmpty() || width(text) <= room) return text
    if (width(ELLIPSIS) > room) return ""
    // the longest start that fits: binary search on its length, each step one measure
    var fits = 0
    var tooLong = text.length
    while (tooLong - fits > 1) {
        val mid = (fits + tooLong) / 2
        if (width(text.substring(0, mid).trimEnd() + ELLIPSIS) <= room) fits = mid else tooLong = mid
    }
    return text.substring(0, fits).trimEnd() + ELLIPSIS
}

/**
 * A component that lays out and draws a sprite, by its path (`"graphics/icons/cargo/supplies.png"`). The sprite must
 * be loaded (listed in the mod's `settings.json` graphics, or loaded with `SettingsAPI.loadTexture`), or it draws
 * nothing.
 *
 * Besides the usual sizes, [size] takes `ImageSize.native()`, the sprite's own size (scaled, and shrunk to a maximum
 * when given), and `ImageSize.aspectWrap()`, as large as the space allows while keeping the sprite's shape. When the
 * [Image]'s box has another shape than the sprite, [contentScale] says how the sprite fills it.
 *
 * Example usage:
 *
 * ```kotlin
 * Image("graphics/icons/cargo/supplies.png", ImageSize.native(scale = 2f))
 * ```
 *
 * @param spritePath the path of the sprite to draw
 * @param size the size of the [Image]. See [ImageSize]
 * @param modifier the [Modifier] to be applied to this image
 * @param contentScale the scale used to determine how the sprite fills the bounds when they are a different shape
 *   from the sprite
 * @param withOutline whether to draw a 1 pixel black outline around the sprite's shape
 * @param textureClamp whether to clamp the sprite's texture at its edges while drawing, so a scaled sprite shows no
 *   pixels from its opposite edge
 * @param forceNoRounding whether to draw the sprite at its exact position. By default vanilla rounds the position of
 *   an even-sized sprite to whole pixels, so it stays sharp
 * @param renderSchematic whether to draw vanilla's schematic effect over the sprite: a yellow grid and a soft yellow
 *   glow, shown only where the sprite is opaque
 * @param showBorder whether to draw a 1 pixel border in [borderColor] around the [Image]'s bounds
 * @param borderColor the colour of the border. `null` keeps vanilla's default
 * @return the vanilla image
 * @see ContentScale
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

    val node = finishLeaf(slot, rawImage.vanillaComponent, size, modifier, handle = rawImage, intrinsic = calcIntrinsic(),
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
 * A component that draws a ship or a fighter wing: vanilla's ship display, showing [fleetMember] or [variant]. Its
 * size is exact or fill, since a ship display has no content size of its own.
 *
 * By default vanilla shrinks the ship to 95% of the display and draws it 3% above its centre. [exactScale] draws it
 * at a fixed scale instead, centred and pixel-sharp: `exactScale = 1f` is one sprite pixel per UI pixel, and
 * `1f / Global.getSettings().screenScaleMult` one sprite pixel per screen pixel.
 *
 * Labels show only for a [variant] with a [shipName]. A [fleetMember] display never has labels (vanilla makes it
 * with no label style), so passing [style], [labelColor] or [shipName] with a [fleetMember] throws.
 *
 * A [ShipDisplay] is made again each time its builder runs, since the fleet member or variant it shows is usually
 * changed in place.
 *
 * Example usage:
 *
 * ```kotlin
 * ShipDisplay(Size.exact(120f, 120f), variant = member.variant, shipName = member.shipName)
 * ```
 *
 * @param size the size of the [ShipDisplay]
 * @param modifier the [Modifier] to be applied to this display
 * @param fleetMember the fleet member to show, with its damage and its fighters' state
 * @param variant the variant to show, when there is no [fleetMember]
 * @param style the label style: `NORMAL` puts the name at the top (Orbitron 20) and the hull class and designation
 *   at the bottom, `MINI` puts the name alone at the top left (Victor 10), `NONE` shows no labels. `null` is the
 *   theme's style. The labels are as wide as the display minus 10, so a small display wraps them
 * @param labelColor the colour of the labels. `null` is the theme's colour, or vanilla's friendly text colour
 * @param shipName the ship name shown in the labels. Without one, no labels are shown
 * @param hideLabels whether to hide the labels even when [style] and [shipName] would show them
 * @param showFighters whether to draw the ship's fighters next to it
 * @param showDestroyedFighters whether to also draw the fighters that were destroyed
 * @param showBorder whether to draw a border around the display
 * @param newBorderStyle whether the border is a 1 pixel line in [borderColor], instead of vanilla's older sprite
 *   frame (`ui_border4`)
 * @param borderColor the colour of the line border. `null` keeps vanilla's, its neutral text colour. The sprite
 *   frame is not tinted
 * @param scaleSmallerShips whether to shrink smaller hulls by vanilla's fixed table (frigate 50%, destroyer 55%,
 *   cruiser 70%, fighter 75%), so a fleet of mixed sizes reads by size
 * @param scanlines how dark vanilla's scanline overlay makes the display, from 0 (none) to 1 (solid black).
 *   Vanilla's own displays use 0.2 to 0.4
 * @param exactScale a fixed scale for the ship, or `null` for vanilla's fit
 * @return the vanilla ship display
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

    finishLeaf(slot, rawShip.vanillaComponent, size, modifier, handle = rawShip, intrinsic = Pair(100f, 100f),
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
