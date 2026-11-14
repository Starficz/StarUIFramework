package org.starficz.starui.vanilla

import com.fs.graphics.Sprite
import com.fs.graphics.util.Fader
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberStatusAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.ui.*
import org.lwjgl.util.vector.Vector2f
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import java.awt.Color

/**
 * A box: one type for a vanilla object whose API interface is not a component type, or that has no API interface
 * at all. Images and ship displays have none; [SliderAPI] and [SectorMapAPI] have one, but it extends nothing, so
 * the box adds the component side. Where the API interface already is a component ([LabelAPI], [ScrollPanelAPI]),
 * StarUI uses it directly, and the methods it leaves out are extensions on it.
 *
 * Pass a box to vanilla's panel methods freely: StarUI unwraps it to [vanillaComponent].
 *
 * @property vanillaComponent the vanilla object itself (an obfuscated engine class): what the box delegates to, and
 *   what StarUI and reflective calls unwrap a box to
 */
abstract class BoxedUIElement(
    val vanillaComponent: UIComponentAPI
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

/**
 * Adds a component that is drawn over the scrolling content and does not scroll with it.
 *
 * @param uiComponent the component to draw over the content
 */
fun ScrollPanelAPI.addToOverlay(uiComponent: UIComponentAPI) { invoke("addToOverlay", uiComponent) }
/**
 * Removes a component added with [addToOverlay].
 *
 * @param uiComponent the component to remove
 */
fun ScrollPanelAPI.removeFromOverlay(uiComponent: UIComponentAPI) { invoke("removeFromOverlay", uiComponent) }

/** Sizes the label to its text (vanilla's autoSize, which LabelAPI leaves out). */
fun LabelAPI.autoSize() { invoke("autoSize") }


/** Vanilla's image component, what `Image` returns: the component, and the image's readable methods. */
class BoxedUIImage(vanillaComponent: UIComponentAPI) :
    BoxedUIElement(vanillaComponent),
    UIComponentAPI by vanillaComponent
{
    /** The path of the sprite shown. Setting it shows another sprite. */
    var spriteName: String
        get() = vanillaComponent.invoke("getSpriteName") as String
        set(newSpriteName) { vanillaComponent.invoke("setSprite", newSpriteName, true) }

    /** The sprite shown. Setting it shows another sprite. */
    var sprite: Sprite
        get() = vanillaComponent.invoke("getSprite") as Sprite
        set(newSprite) { vanillaComponent.invoke("setSprite", newSprite, true) }

    /** The colour of the border drawn with [setShowBorder]. */
    var borderColor: Color
        get() = vanillaComponent.invoke("getBorderColor") as Color
        set(newColor) { vanillaComponent.invoke("setBorderColor", newColor) }

    /** Whether a 1 pixel black outline is drawn around the sprite's shape. */
    var outline: Boolean
        get() = vanillaComponent.invoke("isWithOutline") as Boolean
        set(withOutline) { vanillaComponent.invoke("setWithOutline", withOutline) }

    /** Whether the texture is clamped at its edges while drawing. */
    var textureClamp: Boolean
        get() = vanillaComponent.invoke("isTexClamp") as Boolean
        set(texClamp) { vanillaComponent.invoke("setTexClamp", texClamp) }

    /** Whether the sprite is drawn at its exact position, instead of on whole pixels. */
    var forceNoRounding: Boolean
        get() = vanillaComponent.invoke("isForceNoRounding") as Boolean
        set(noRounding) { vanillaComponent.invoke("setForceNoRounding", noRounding) }

    /** The sprite's own width divided by its height. */
    val originalAspectRatio: Float
        get() = vanillaComponent.invoke("getOriginalAR") as Float

    /**
     * Whether the sprite is scaled to the image's bounds when it is set and when the image is resized (fitted, or
     * stretched with [setStretch]). Off, the sprite keeps its own size. On by default.
     */
    var resize: Boolean
        get() = vanillaComponent.invoke("isResize") as Boolean
        set(value) { vanillaComponent.invoke("setResize", value) }

    /** Whether a 1 pixel border in [borderColor] is drawn around the image's bounds. */
    fun setShowBorder(show: Boolean) { vanillaComponent.invoke("setShowBorder", show) }
    /** Whether [resize] stretches the sprite to the image's bounds, instead of fitting it and keeping its shape. */
    fun setStretch(stretch: Boolean) { vanillaComponent.invoke("setStretch", stretch) }
    /** Whether vanilla's schematic effect is drawn over the sprite: a yellow grid and glow where it is opaque. */
    fun setRenderSchematic(renderSchematic: Boolean) { vanillaComponent.invoke("setRenderSchematic", renderSchematic) }
    /** Sizes the sprite back to its own size; the image keeps its bounds. */
    fun restoreImageSize() { vanillaComponent.invoke("restoreImageSize") }
    /** Sizes the image to the sprite's own size. */
    fun sizeToOriginalSpriteSize() { vanillaComponent.invoke("autoSize") }
    /**
     * Sizes the image to [width], with the height that keeps the sprite's shape.
     *
     * @param width the new width
     */
    fun sizeToOriginalAspectRatioWithWidth(width: Float) { vanillaComponent.invoke("autoSizeToWidth", width) }
    /**
     * Sizes the image to [height], with the width that keeps the sprite's shape.
     *
     * @param height the new height
     */
    fun sizeToOriginalAspectRatioWithHeight(height: Float) { vanillaComponent.invoke("autoSizeToHeight", height) }
}


/** Vanilla's ship display, what `ShipDisplay` returns: the panel, and the display's readable methods. */
class BoxedUIShipPreview(vanillaComponent: UIPanelAPI):
    BoxedUIElement(vanillaComponent),
    UIPanelAPI by vanillaComponent
{
    /** The label styles of a ship display. See `ShipDisplay`. */
    enum class Style {
        /** No labels. */
        NONE,
        /** The name at the top (Orbitron 20), and the hull class and designation at the bottom. */
        NORMAL,
        /** The name alone at the top left (Victor 10). */
        MINI
    }

    /**
     * An exact scale for the ship (1 = one sprite pixel per UI unit; at a UI scale above 100% use
     * `1f / Global.getSettings().screenScaleMult` for one per screen pixel), centred exactly and pixel aligned.
     * Null (the default) keeps vanilla's fit, which never reaches 1:1 (it always takes 95%). Done by
     * ShipDisplayExactScalePatch.
     */
    var exactScale: Float?
        get() = ShipDisplayScale.of(vanillaComponent)
        set(scale) {
            if (ShipDisplayScale.of(vanillaComponent) == scale) return
            ShipDisplayScale.set(vanillaComponent, scale)
            // the ship's centring happens when its ships are made; make them again on the next render
            vanillaComponent.invoke("cleanup")
        }

    /** Returns the ships the display draws: the ship, and its fighters when they are shown. Made now if needed. */
    fun getShips(): Array<ShipAPI> {
        vanillaComponent.invoke("prepareShip")
        return vanillaComponent.invoke("getShips") as Array<ShipAPI>
    }
    /** Throws away the ships the display draws; they are made again on the next render. */
    fun cleanupShips() { vanillaComponent.invoke("cleanup") }

    /** Whether the ship's fighters are drawn next to it. */
    var showFighters
        get() = vanillaComponent.invoke("isShowFighters") as Boolean
        set(show) {vanillaComponent.invoke("setShowFighters", show) }

    /** Whether destroyed fighters are also drawn. */
    var showDestroyedFighters
        get() = vanillaComponent.invoke("isShowDestroyedFighters") as Boolean
        set(show) { vanillaComponent.invoke("setShowDestroyedFighters", show) }

    /** How bright the display is while highlighted. */
    var highlightBrightness
        get() = vanillaComponent.invoke("getHighlightBrightness") as Float
        set(brightness) { vanillaComponent.invoke("setHighlightBrightness", brightness) }

    /** Highlights the display, as vanilla does for a selected ship. */
    fun highlight() { vanillaComponent.invoke("highlight") }
    /** Ends the highlight. */
    fun unhighlight() { vanillaComponent.invoke("unhighlight") }
    /** Returns the fader of the highlight: its brightness over time. */
    fun getHighlightFader(): Fader = vanillaComponent.invoke("getHighlight") as Fader

    /** The colour of the line border ([setBorderNewStyle]); the sprite frame is not tinted. */
    var borderColor
        get() = vanillaComponent.invoke("getBorderColor") as Color
        set(show) { vanillaComponent.invoke("setBorderColor", show) }

    /** Whether a border is drawn around the display. */
    fun setShowBorder(show: Boolean) { vanillaComponent.invoke("setShowBorder", show) }
    /** Whether the border is a 1 pixel line in [borderColor], instead of vanilla's `ui_border4` sprite frame. */
    fun setBorderNewStyle(newStyle: Boolean) { vanillaComponent.invoke("setShowNewBorder", newStyle) }

    /** The variant shown. Setting it shows another variant, without a name. */
    var variant
        get() = vanillaComponent.invoke("getVariant") as ShipVariantAPI?
        set(variant) { vanillaComponent.invoke("setVariant", variant) }

    // vanilla's third argument only seeds a random name when the name is null; it is never shown
    /** Shows [variant] named [shipName]; with a name, a NORMAL or MINI display fills its labels. */
    fun setVariant(variant: ShipVariantAPI, shipName: String?) {
        vanillaComponent.invoke("setVariant", variant, shipName, null)
    }
    /**
     * Shows [fleetMember] in the state [fleetStatus] records, as vanilla does for ships in battle.
     *
     * @param fleetMember the ship
     * @param fleetStatus its status
     */
    fun updateWithStatus(fleetMember: FleetMemberAPI, fleetStatus: FleetMemberStatusAPI) {
        vanillaComponent.invoke("updateWithStatus", fleetMember, fleetStatus)
    }
    /**
     * Shows a fighter wing instead of a ship.
     *
     * @param fighterWingSpec the wing to show
     */
    fun setWingSpec(fighterWingSpec: FighterWingSpecAPI) { vanillaComponent.invoke("setWingSpec", fighterWingSpec) }

    // Vanilla reads any magnitude other than 1 as "on" (it also enlarges fighter wings a little as it drops);
    // 0.25 is its own default.
    /** Shrinks smaller hulls by vanilla's fixed table (frigate 50%, destroyer 55%, cruiser 70%, fighter 75%). */
    fun setScaleDownSmallerShips(enabled: Boolean) {
        vanillaComponent.invoke("setScaleDownSmallerShipsMagnitude", if (enabled) 0.25f else 1f)
    }
    // Vanilla's adjustOverlay(a, b) draws the scanline texture twice, b at the top and a 1 px lower, both black.
    /** Darkens the display with vanilla's scanline overlay: 0 is none, 1 solid black. */
    fun setScanlines(alpha: Float) {
        vanillaComponent.invoke("adjustOverlay", alpha, alpha)
    }

    /** Sets the colour of the labels. */
    fun setLabelColor(color: Color) { vanillaComponent.invoke("setLabelColor", color) }
    /** Hides the labels. */
    fun hideLabels(){ vanillaComponent.invoke("hideLabels") }
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
    fun centerOn(location: Vector2f) { vanillaComponent.invoke("centerOn", Vector2f(location)) }
    /** Shows [entity]'s location (switching between hyperspace and a system as needed) and centres on it. */
    fun centerOnEntity(entity: SectorEntityToken) { vanillaComponent.invoke("centerOnEntity", entity) }
    /** Zooms all the way out and centres the map. */
    fun zoomOutAndCenter() { vanillaComponent.invoke("zoomOutAndCenter") }
    /** The world point at the centre of the view. */
    val worldCenter: Vector2f get() = vanillaComponent.invoke("getWorldCenter") as Vector2f
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
    /** The StarUI container the holo shows now (a `contentKey` change swaps in a new one). */
    var content: CustomPanelAPI = content
        internal set

    /**
     * Folds the holo open over [duration] seconds (vanilla: `foldOut`), with the burst of static vanilla's dialogs
     * open with ([flicker]: `flickerNoise(0, 1.25)`).
     */
    fun open(duration: Float = 0.25f, flicker: Boolean = true) {
        vanillaComponent.invoke("foldOut", duration)
        if (flicker) flickerNoise(0f, 1.25f)
    }
    /** Folds the holo shut over [duration] seconds (vanilla: `foldIn`), with vanilla's closing static ([flicker]). */
    fun close(duration: Float = 0.25f, flicker: Boolean = true) {
        vanillaComponent.invoke("foldIn", duration)
        if (flicker) flickerNoise(0f, 1.25f)
    }
    /** Fully open at once, no animation. */
    fun forceOpen() { vanillaComponent.invoke("forceFoldOut") }
    /** Fully shut at once, no animation. */
    fun forceClose() { vanillaComponent.invoke("forceFoldIn") }

    /** The fold: brightness 1 is open, 0 shut; `isFadingIn` / `isFadingOut` while it moves. */
    val fold: Fader get() = vanillaComponent.invoke("getFanOut") as Fader
    /** Fully open. */
    val isOpen: Boolean get() = fold.isFadedIn
    /** Fully shut (it draws nothing and takes no input then). */
    val isClosed: Boolean get() = fold.isFadedOut

    /** How strongly the noise layer shows over the content (vanilla's dialogs fade it as they settle). */
    var noiseAlpha: Float
        get() = vanillaComponent.invoke("getNoiseAlpha") as Float
        set(value) { vanillaComponent.invoke("setNoiseAlpha", value) }

    /**
     * A burst of static: the noise rises over [rampUp] seconds and dies away over [decay]. Vanilla's dialogs use
     * (0, 1.25) when they open and close, and (0, 0.5) when a click lands outside a modal one. No static shows when
     * the player has turned UI noise off in the game's settings.
     */
    fun flickerNoise(rampUp: Float, decay: Float) { vanillaComponent.invoke("flickerNoise", rampUp, decay) }

    /** The content's inset from the holo's edge on every side. */
    val contentInset: Float get() = vanillaComponent.invoke("getBorderThickness") as Float

    /** Off: no frame, background, tint or noise; only the content (and the fold) remain. */
    fun setRenderBackground(render: Boolean) { vanillaComponent.invoke("setRenderBackground", render) }

    /**
     * The dark background's alpha (0..255): it is two interlaced sets of black scanlines, [lines] and the set one
     * pixel lower, [offsetLines]. Vanilla's default 125 / 175 is the see-through title screen look; most of its
     * dialogs use 255 / 255. See [HoloBackground].
     */
    fun setBackgroundAlpha(lines: Int, offsetLines: Int) { vanillaComponent.invoke("setBackgroundAlpha", lines, offsetLines) }

    /** Clip the content to the frame even when fully open (it is always clipped while folding). */
    fun setAlwaysScissor(scissor: Boolean) { vanillaComponent.invoke("setAlwaysScissor", scissor) }

    /** The `open` argument of the last `Holo` call: a change folds the holo open or shut. */
    internal var lastOpenArgument: Boolean? = null
    /** The `background` and `contentKey` arguments of the last `Holo` call. */
    internal var lastBackground: HoloBackground? = null
    internal var lastContentKey: Any? = null

    /** Off: new content shows at once, without the noise transition. */
    var transitionsEnabled: Boolean
        get() = vanillaComponent.invoke("isTransitionsEnabled") as Boolean
        set(value) { vanillaComponent.invoke("setTransitionsEnabled", value) }
}

/**
 * A holo's dark background: the alphas (0 to 255) of its two interlaced sets of black scanlines, the second one
 * pixel lower. [Dialog] is what most of vanilla's dialogs use, [TitleScreen] the see-through look of the title menu.
 *
 * @property lines the alpha of the first set of scanlines
 * @property offsetLines the alpha of the set one pixel lower
 */
data class HoloBackground(val lines: Int, val offsetLines: Int) {
    companion object {
        /** The opaque background most of vanilla's dialogs use (its market pickers): 255 / 255. */
        val Dialog = HoloBackground(255, 255)
        /**
         * The holo's own default, 125 / 175: dark but see-through. Vanilla's custom dialog (`showCustomDialog`) and the
         * title menu keep it.
         */
        val Default = HoloBackground(125, 175)
        /** The see-through background of the title menu, the holo's own default: 125 / 175. */
        val TitleScreen = HoloBackground(125, 175)
    }
}
