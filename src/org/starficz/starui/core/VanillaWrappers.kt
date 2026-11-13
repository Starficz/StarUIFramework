package org.starficz.starui.core

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.starfarer.api.ui.SliderAPI
import org.lwjgl.util.vector.Vector2f
import com.fs.graphics.Sprite
import com.fs.graphics.util.Fader
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberStatusAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.core.ReflectionUtils.invoke
import java.awt.Color

// Abstract base class for Boxed vanilla elements: one type for a vanilla object whose API interface is not a component
// type, or that has no API interface at all. Images and ship previews have none; SliderAPI and SectorMapAPI do, but
// extend nothing, so the box adds the component side (as BoxedUILabel did before 0.98.5a made LabelAPI a component).
// Where the API interface already is a component (LabelAPI, ScrollPanelAPI) it is used directly, and the methods it
// leaves out are extensions on it.
abstract class BoxedUIElement(
    /**
     * The vanilla object itself (an obfuscated engine class): what the box delegates to, and what StarUI and
     * ReflectionUtils unwrap a box to. Reflective calls on the box reach it already (`box.invoke("x")`).
     */
    val obf: UIComponentAPI
)

// The few scroll panel methods ScrollPanelAPI still leaves out (readable engine names).

/** Whether the scroll panel draws shadows at its top and bottom edges. */
var ScrollPanelAPI.renderShadows: Boolean
    get() = !(invoke("isDoNotRenderShadows") as Boolean)
    set(value) { invoke("setDoNotRenderShadows", !value) }

/** The colour of the edge shadows; the same value as [ScrollPanelAPI.getDarkColor]. */
var ScrollPanelAPI.shadowColor: Color
    get() = darkColor
    set(value) { darkColor = value }

/** Adds a component that is drawn over the scrolling content and does not scroll with it. */
fun ScrollPanelAPI.addToOverlay(uiComponent: UIComponentAPI) { invoke("addToOverlay", uiComponent) }
fun ScrollPanelAPI.removeFromOverlay(uiComponent: UIComponentAPI) { invoke("removeFromOverlay", uiComponent) }

/** Sizes the label to its text (vanilla's autoSize, which LabelAPI leaves out). */
fun LabelAPI.autoSize() { invoke("autoSize") }

interface ImageAPI {
    var spriteName: String
    var sprite: Sprite
    var borderColor: Color
    var outline: Boolean
    var textureClamp: Boolean
    var forceNoRounding: Boolean
    val originalAspectRatio: Float
    var resize: Boolean
    fun setShowBorder(show: Boolean)
    fun setStretch(stretch: Boolean)
    fun setRenderSchematic(renderSchematic: Boolean)
    fun restoreImageSize()
    fun sizeToOriginalSpriteSize()
    fun sizeToOriginalAspectRatioWithWidth(width: Float)
    fun sizeToOriginalAspectRatioWithHeight(height: Float)
}

// The Core Wrapper implements both UIComponentAPI and ImageAPI
class BoxedUIImage(obf: UIComponentAPI) :
    BoxedUIElement(obf),
    UIComponentAPI by obf,
    ImageAPI
{
    override var spriteName: String
        get() = obf.invoke("getSpriteName") as String
        set(newSpriteName) { obf.invoke("setSprite", newSpriteName, true) }

    override var sprite: Sprite
        get() = obf.invoke("getSprite") as Sprite
        set(newSprite) { obf.invoke("setSprite", newSprite, true) }

    override var borderColor: Color
        get() = obf.invoke("getBorderColor") as Color
        set(newColor) { obf.invoke("setBorderColor", newColor) }

    override var outline: Boolean
        get() = obf.invoke("isWithOutline") as Boolean
        set(withOutline) { obf.invoke("setWithOutline", withOutline) }

    override var textureClamp: Boolean
        get() = obf.invoke("isTexClamp") as Boolean
        set(texClamp) { obf.invoke("setTexClamp", texClamp) }

    override var forceNoRounding: Boolean
        get() = obf.invoke("isForceNoRounding") as Boolean
        set(noRounding) { obf.invoke("setForceNoRounding", noRounding) }

    override val originalAspectRatio: Float
        get() = obf.invoke("getOriginalAR") as Float

    override var resize: Boolean
        get() = obf.invoke("isResize") as Boolean
        set(value) { obf.invoke("setResize", value) }

    override fun setShowBorder(show: Boolean) { obf.invoke("setShowBorder", show) }
    override fun setStretch(stretch: Boolean) { obf.invoke("setStretch", stretch) }
    override fun setRenderSchematic(renderSchematic: Boolean) { obf.invoke("setRenderSchematic", renderSchematic) }
    override fun restoreImageSize() { obf.invoke("restoreImageSize") }
    override fun sizeToOriginalSpriteSize() { obf.invoke("autoSize") }
    override fun sizeToOriginalAspectRatioWithWidth(width: Float) { obf.invoke("autoSizeToWidth", width) }
    override fun sizeToOriginalAspectRatioWithHeight(height: Float) { obf.invoke("autoSizeToHeight", height) }
}

interface ShipPreviewAPI : UIPanelAPI {
    var exactScale: Float?
    var showFighters: Boolean
    var showDestroyedFighters: Boolean
    var highlightBrightness: Float
    var borderColor: Color
    var variant: ShipVariantAPI?

    fun getShips(): Array<ShipAPI>
    fun cleanupShips()
    fun highlight()
    fun unhighlight()
    fun getHighlightFader(): Fader
    fun setShowBorder(show: Boolean)
    fun setBorderNewStyle(newStyle: Boolean)
    /** Shows [variant] named [shipName]; with a name, a NORMAL or MINI display fills its labels. */
    fun setVariant(variant: ShipVariantAPI, shipName: String?)
    fun updateWithStatus(fleetMember: FleetMemberAPI, fleetStatus: FleetMemberStatusAPI)
    fun setWingSpec(fighterWingSpec: FighterWingSpecAPI)
    /** Shrinks smaller hulls by vanilla's fixed table (frigate 50%, destroyer 55%, cruiser 70%, fighter 75%). */
    fun setScaleDownSmallerShips(enabled: Boolean)
    /** Darkens the display with vanilla's scanline overlay: 0 is none, 1 solid black. */
    fun setScanlines(alpha: Float)
    fun setLabelColor(color: Color)
    fun hideLabels()
}

class BoxedUIShipPreview(obf: UIPanelAPI):
    BoxedUIElement(obf),
    UIPanelAPI by obf,
    ShipPreviewAPI
{
    internal companion object{
        var SHIP_PREVIEW_CLASS: Class<*>? = null
        var FLEETMEMBER_CONSTRUCTOR: ReflectionUtils.ReflectedConstructor? = null
        var ENUM_CONSTRUCTOR: ReflectionUtils.ReflectedConstructor? = null
        var ENUM_ARRAY: Array<*>? = null
    }

    enum class Style {
        NONE,
        NORMAL,
        MINI
    }

    /**
     * An exact scale for the ship (1 = one sprite pixel per UI unit; at a UI scale above 100% use
     * `1f / Global.getSettings().screenScaleMult` for one per screen pixel), centred exactly and pixel aligned.
     * Null (the default) keeps vanilla's fit, which never reaches 1:1 (it always takes 95%). Done by
     * ShipDisplayExactScalePatch.
     */
    override var exactScale: Float?
        get() = ShipDisplayScale.of(obf)
        set(scale) {
            if (ShipDisplayScale.of(obf) == scale) return
            ShipDisplayScale.set(obf, scale)
            // the ship's centring happens when its ships are made; make them again on the next render
            obf.invoke("cleanup")
        }

    override fun getShips(): Array<ShipAPI> {
        obf.invoke("prepareShip")
        return obf.invoke("getShips") as Array<ShipAPI>
    }
    override fun cleanupShips() { obf.invoke("cleanup") }

    override var showFighters
        get() = obf.invoke("isShowFighters") as Boolean
        set(show) {obf.invoke("setShowFighters", show) }

    override var showDestroyedFighters
        get() = obf.invoke("isShowDestroyedFighters") as Boolean
        set(show) { obf.invoke("setShowDestroyedFighters", show) }

    override var highlightBrightness
        get() = obf.invoke("getHighlightBrightness") as Float
        set(brightness) { obf.invoke("setHighlightBrightness", brightness) }

    override fun highlight() { obf.invoke("highlight") }
    override fun unhighlight() { obf.invoke("unhighlight") }
    override fun getHighlightFader(): Fader = obf.invoke("getHighlight") as Fader

    override var borderColor
        get() = obf.invoke("getBorderColor") as Color
        set(show) { obf.invoke("setBorderColor", show) }

    override fun setShowBorder(show: Boolean) { obf.invoke("setShowBorder", show) }
    override fun setBorderNewStyle(newStyle: Boolean) { obf.invoke("setShowNewBorder", newStyle) }

    override var variant
        get() = obf.invoke("getVariant") as ShipVariantAPI?
        set(variant) { obf.invoke("setVariant", variant) }

    // vanilla's third argument only seeds a random name when the name is null; it is never shown
    override fun setVariant(variant: ShipVariantAPI, shipName: String?) {
        obf.invoke("setVariant", variant, shipName, null)
    }
    override fun updateWithStatus(fleetMember: FleetMemberAPI, fleetStatus: FleetMemberStatusAPI) {
        obf.invoke("updateWithStatus", fleetMember, fleetStatus)
    }
    override fun setWingSpec(fighterWingSpec: FighterWingSpecAPI) { obf.invoke("setWingSpec", fighterWingSpec) }

    // Vanilla reads any magnitude other than 1 as "on" (it also enlarges fighter wings a little as it drops);
    // 0.25 is its own default.
    override fun setScaleDownSmallerShips(enabled: Boolean) {
        obf.invoke("setScaleDownSmallerShipsMagnitude", if (enabled) 0.25f else 1f)
    }
    // Vanilla's adjustOverlay(a, b) draws the scanline texture twice, b at the top and a 1 px lower, both black.
    override fun setScanlines(alpha: Float) {
        obf.invoke("adjustOverlay", alpha, alpha)
    }

    override fun setLabelColor(color: Color) { obf.invoke("setLabelColor", color) }
    override fun hideLabels(){ obf.invoke("hideLabels") }
}

/**
 * A vanilla slider or bar as one type: [SliderAPI] and the [UIComponentAPI] it is (the API interface does not say so).
 * What `Slider` and `ProgressBar` return.
 */
class BoxedUISlider(slider: SliderAPI) :
    BoxedUIElement(slider as UIComponentAPI),
    UIComponentAPI by (slider as UIComponentAPI),
    SliderAPI by slider

/**
 * Vanilla's sector map as one type: [SectorMapAPI] (pings), the [UIPanelAPI] it is (the API interface does not say
 * so), and the map's readable methods SectorMapAPI leaves out (centring, zoom). `.sectorMap` on a `SectorMap` gives
 * one.
 */
class BoxedSectorMap(map: SectorMapAPI) :
    BoxedUIElement(map as UIPanelAPI),
    UIPanelAPI by (map as UIPanelAPI),
    SectorMapAPI by map
{
    /** Centres the map on a point of the location it shows, in world coordinates. */
    fun centerOn(location: Vector2f) { obf.invoke("centerOn", Vector2f(location)) }
    /** Shows [entity]'s location (switching between hyperspace and a system as needed) and centres on it. */
    fun centerOnEntity(entity: SectorEntityToken) { obf.invoke("centerOnEntity", entity) }
    /** Zooms all the way out and centres the map. */
    fun zoomOutAndCenter() { obf.invoke("zoomOutAndCenter") }
    /** The world point at the centre of the view. */
    val worldCenter: Vector2f get() = obf.invoke("getWorldCenter") as Vector2f
}

/**
 * Vanilla's holo panel, what `Holo` returns: the framed window vanilla's dialogs and title screen menu sit in (its
 * own name for it: `getHolo()`). It draws the `ui_border1` frame, a dark scanline background over the frame's inner
 * edge, its content, a teal scanline tint and a flickering noise layer, and folds open and shut: the frame grows
 * from or shrinks to a line through its middle, the content shows only in the last quarter of the fold.
 *
 * The holo has no API interface: this box gives the component and the holo's own readable methods. [content] is the
 * StarUI container the holo frames (drawn, advanced and given input by the holo itself, not a child in the tree).
 */
class BoxedUIHolo internal constructor(holo: UIComponentAPI, content: CustomPanelAPI) :
    BoxedUIElement(holo),
    UIComponentAPI by holo
{
    internal companion object {
        /** The holo class, found by its readable methods at application load (StarUIManager). */
        var HOLO_CLASS: Class<*>? = null
        /** (width, height, frame sprite set, content inset). */
        var CONSTRUCTOR: ReflectionUtils.ReflectedConstructor? = null
    }

    /** The StarUI container the holo shows now (a `contentKey` change swaps in a new one). */
    var content: CustomPanelAPI = content
        internal set

    /**
     * Folds the holo open over [duration] seconds (vanilla: `foldOut`), with the burst of static vanilla's dialogs
     * open with ([flicker]: `flickerNoise(0, 1.25)`).
     */
    fun open(duration: Float = 0.25f, flicker: Boolean = true) {
        obf.invoke("foldOut", duration)
        if (flicker) flickerNoise(0f, 1.25f)
    }
    /** Folds the holo shut over [duration] seconds (vanilla: `foldIn`), with vanilla's closing static ([flicker]). */
    fun close(duration: Float = 0.25f, flicker: Boolean = true) {
        obf.invoke("foldIn", duration)
        if (flicker) flickerNoise(0f, 1.25f)
    }
    /** Fully open at once, no animation. */
    fun forceOpen() { obf.invoke("forceFoldOut") }
    /** Fully shut at once, no animation. */
    fun forceClose() { obf.invoke("forceFoldIn") }

    /** The fold: brightness 1 is open, 0 shut; `isFadingIn` / `isFadingOut` while it moves. */
    val fold: Fader get() = obf.invoke("getFanOut") as Fader
    /** Fully open. */
    val isOpen: Boolean get() = fold.isFadedIn
    /** Fully shut (it draws nothing and takes no input then). */
    val isClosed: Boolean get() = fold.isFadedOut

    /** How strongly the noise layer shows over the content (vanilla's dialogs fade it as they settle). */
    var noiseAlpha: Float
        get() = obf.invoke("getNoiseAlpha") as Float
        set(value) { obf.invoke("setNoiseAlpha", value) }

    /**
     * A burst of static: the noise rises over [rampUp] seconds and dies away over [decay]. Vanilla's dialogs use
     * (0, 1.25) when they open and close, and (0, 0.5) when a click lands outside a modal one. No static shows when
     * the player has turned UI noise off in the game's settings.
     */
    fun flickerNoise(rampUp: Float, decay: Float) { obf.invoke("flickerNoise", rampUp, decay) }

    /** The content's inset from the holo's edge on every side. */
    val contentInset: Float get() = obf.invoke("getBorderThickness") as Float

    /** Off: no frame, background, tint or noise; only the content (and the fold) remain. */
    fun setRenderBackground(render: Boolean) { obf.invoke("setRenderBackground", render) }

    /**
     * The dark background's alpha (0..255): it is two interlaced sets of black scanlines, [lines] and the set one
     * pixel lower, [offsetLines]. Vanilla's default 125 / 175 is the see-through title screen look; most of its
     * dialogs use 255 / 255. See [HoloBackground].
     */
    fun setBackgroundAlpha(lines: Int, offsetLines: Int) { obf.invoke("setBackgroundAlpha", lines, offsetLines) }

    /** Clip the content to the frame even when fully open (it is always clipped while folding). */
    fun setAlwaysScissor(scissor: Boolean) { obf.invoke("setAlwaysScissor", scissor) }

    /** The `open` argument of the last `Holo` call: a change folds the holo open or shut. */
    internal var lastOpenArgument: Boolean? = null
    /** The `background` and `contentKey` arguments of the last `Holo` call. */
    internal var lastBackground: HoloBackground? = null
    internal var lastContentKey: Any? = null

    /** Off: new content shows at once, without the noise transition. */
    var transitionsEnabled: Boolean
        get() = obf.invoke("isTransitionsEnabled") as Boolean
        set(value) { obf.invoke("setTransitionsEnabled", value) }
}

/**
 * A holo's dark background: the alphas (0..255) of its two interlaced sets of black scanlines (the second one pixel
 * lower). [Dialog] is what most of vanilla's dialogs use, [TitleScreen] the see-through look of the title menu (the
 * holo's own default).
 */
data class HoloBackground(val lines: Int, val offsetLines: Int) {
    companion object {
        val Dialog = HoloBackground(255, 255)
        val TitleScreen = HoloBackground(125, 175)
    }
}
