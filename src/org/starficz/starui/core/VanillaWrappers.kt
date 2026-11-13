package org.starficz.starui.core

import com.fs.graphics.Sprite
import com.fs.graphics.util.Fader
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberStatusAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.impl.campaign.procgen.Constellation
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.core.ReflectionUtils.invoke
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

// Abstract base class for Boxed vanilla elements that still have no API (images, ship previews).
// Labels, scroll panels and sliders are used directly: LabelAPI, ScrollPanelAPI and SliderAPI cover them since 0.98.5a.
abstract class BoxedUIElement(val boxedElement: UIComponentAPI)

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
class BoxedUIImage(val innerImage: UIComponentAPI) :
    BoxedUIElement(innerImage),
    UIComponentAPI by innerImage,
    ImageAPI
{
    override var spriteName: String
        get() = innerImage.invoke("getSpriteName") as String
        set(newSpriteName) { innerImage.invoke("setSprite", newSpriteName, true) }

    override var sprite: Sprite
        get() = innerImage.invoke("getSprite") as Sprite
        set(newSprite) { innerImage.invoke("setSprite", newSprite, true) }

    override var borderColor: Color
        get() = innerImage.invoke("getBorderColor") as Color
        set(newColor) { innerImage.invoke("setBorderColor", newColor) }

    override var outline: Boolean
        get() = innerImage.invoke("isWithOutline") as Boolean
        set(withOutline) { innerImage.invoke("setWithOutline", withOutline) }

    override var textureClamp: Boolean
        get() = innerImage.invoke("isTexClamp") as Boolean
        set(texClamp) { innerImage.invoke("setTexClamp", texClamp) }

    override var forceNoRounding: Boolean
        get() = innerImage.invoke("isForceNoRounding") as Boolean
        set(noRounding) { innerImage.invoke("setForceNoRounding", noRounding) }

    override val originalAspectRatio: Float
        get() = innerImage.invoke("getOriginalAR") as Float

    override var resize: Boolean
        get() = innerImage.invoke("isResize") as Boolean
        set(value) { innerImage.invoke("setResize", value) }

    override fun setShowBorder(show: Boolean) { innerImage.invoke("setShowBorder", show) }
    override fun setStretch(stretch: Boolean) { innerImage.invoke("setStretch", stretch) }
    override fun setRenderSchematic(renderSchematic: Boolean) { innerImage.invoke("setRenderSchematic", renderSchematic) }
    override fun restoreImageSize() { innerImage.invoke("restoreImageSize") }
    override fun sizeToOriginalSpriteSize() { innerImage.invoke("autoSize") }
    override fun sizeToOriginalAspectRatioWithWidth(width: Float) { innerImage.invoke("autoSizeToWidth", width) }
    override fun sizeToOriginalAspectRatioWithHeight(height: Float) { innerImage.invoke("autoSizeToHeight", height) }
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

class BoxedUIShipPreview(val uiShipPreview: UIPanelAPI):
    BoxedUIElement(uiShipPreview),
    UIPanelAPI by uiShipPreview,
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
        get() = ShipDisplayScale.of(uiShipPreview)
        set(scale) {
            if (ShipDisplayScale.of(uiShipPreview) == scale) return
            ShipDisplayScale.set(uiShipPreview, scale)
            // the ship's centring happens when its ships are made; make them again on the next render
            uiShipPreview.invoke("cleanup")
        }

    override fun getShips(): Array<ShipAPI> {
        uiShipPreview.invoke("prepareShip")
        return uiShipPreview.invoke("getShips") as Array<ShipAPI>
    }
    override fun cleanupShips() { uiShipPreview.invoke("cleanup") }

    override var showFighters
        get() = uiShipPreview.invoke("isShowFighters") as Boolean
        set(show) {uiShipPreview.invoke("setShowFighters", show) }

    override var showDestroyedFighters
        get() = uiShipPreview.invoke("isShowDestroyedFighters") as Boolean
        set(show) { uiShipPreview.invoke("setShowDestroyedFighters", show) }

    override var highlightBrightness
        get() = uiShipPreview.invoke("getHighlightBrightness") as Float
        set(brightness) { uiShipPreview.invoke("setHighlightBrightness", brightness) }

    override fun highlight() { uiShipPreview.invoke("highlight") }
    override fun unhighlight() { uiShipPreview.invoke("unhighlight") }
    override fun getHighlightFader(): Fader = uiShipPreview.invoke("getHighlight") as Fader

    override var borderColor
        get() = uiShipPreview.invoke("getBorderColor") as Color
        set(show) { uiShipPreview.invoke("setBorderColor", show) }

    override fun setShowBorder(show: Boolean) { uiShipPreview.invoke("setShowBorder", show) }
    override fun setBorderNewStyle(newStyle: Boolean) { uiShipPreview.invoke("setShowNewBorder", newStyle) }

    override var variant
        get() = uiShipPreview.invoke("getVariant") as ShipVariantAPI?
        set(variant) { uiShipPreview.invoke("setVariant", variant) }

    // vanilla's third argument only seeds a random name when the name is null; it is never shown
    override fun setVariant(variant: ShipVariantAPI, shipName: String?) {
        uiShipPreview.invoke("setVariant", variant, shipName, null)
    }
    override fun updateWithStatus(fleetMember: FleetMemberAPI, fleetStatus: FleetMemberStatusAPI) {
        uiShipPreview.invoke("updateWithStatus", fleetMember, fleetStatus)
    }
    override fun setWingSpec(fighterWingSpec: FighterWingSpecAPI) { uiShipPreview.invoke("setWingSpec", fighterWingSpec) }

    // Vanilla reads any magnitude other than 1 as "on" (it also enlarges fighter wings a little as it drops);
    // 0.25 is its own default.
    override fun setScaleDownSmallerShips(enabled: Boolean) {
        uiShipPreview.invoke("setScaleDownSmallerShipsMagnitude", if (enabled) 0.25f else 1f)
    }
    // Vanilla's adjustOverlay(a, b) draws the scanline texture twice, b at the top and a 1 px lower, both black.
    override fun setScanlines(alpha: Float) {
        uiShipPreview.invoke("adjustOverlay", alpha, alpha)
    }

    override fun setLabelColor(color: Color) { uiShipPreview.invoke("setLabelColor", color) }
    override fun hideLabels(){ uiShipPreview.invoke("hideLabels") }
}

/**
 * A sector map made by `SectorMap`: the StarUI panel holding vanilla's map, with SectorMapAPI and the map's own
 * readable methods that API leaves out (centring, zoom). Vanilla lays a map out once, at the size it is made, so
 * StarUI makes it again when its size changes; this always talks to the current one, and a new one keeps the old
 * one's centre. Calls made before the map exists (it is made when the panel is first laid out) wait for it.
 */
class BoxedSectorMap internal constructor(val holder: UIPanelAPI) :
    BoxedUIElement(holder),
    UIPanelAPI by holder,
    SectorMapAPI
{
    private val pending = ArrayList<(UIPanelAPI) -> Unit>()

    /** Vanilla's map panel, or null before the first layout (inside its title box when it has a title). */
    val map: UIPanelAPI?
        get() = holder.getChildrenNonCopy().firstNotNullOfOrNull { findMap(it) }

    private fun findMap(component: UIComponentAPI): UIPanelAPI? {
        if (component is SectorMapAPI) return component as UIPanelAPI
        if (component !is UIPanelAPI) return null
        return component.getChildrenNonCopy().firstNotNullOfOrNull { findMap(it) }
    }

    private fun onMap(action: (UIPanelAPI) -> Unit) {
        val current = map
        if (current != null) action(current) else pending += action
    }

    /** Called by StarUI before [made] replaces [old] (null the first time). */
    internal fun swapped(old: UIComponentAPI?, made: UIComponentAPI) {
        val newMap = findMap(made) ?: return
        val oldMap = old?.let { findMap(it) }
        if (oldMap != null) newMap.invoke("centerOn", Vector2f(oldMap.invoke("getWorldCenter") as Vector2f))
        pending.forEach { it(newMap) }
        pending.clear()
    }

    /** Centres the map on a point of the location it shows, in world coordinates. */
    fun centerOn(location: Vector2f) = onMap { it.invoke("centerOn", Vector2f(location)) }
    /** Shows [entity]'s location (switching between hyperspace and a system as needed) and centres on it. */
    fun centerOnEntity(entity: SectorEntityToken) = onMap { it.invoke("centerOnEntity", entity) }
    /** Zooms all the way out and centres the map. */
    fun zoomOutAndCenter() = onMap { it.invoke("zoomOutAndCenter") }
    /** The world point at the centre of the view; null before the map exists. */
    val worldCenter: Vector2f? get() = map?.invoke("getWorldCenter") as Vector2f?

    private val api: SectorMapAPI? get() = map as SectorMapAPI?
    override fun getConstellationLabelEntity(c: Constellation?): SectorEntityToken? = api?.getConstellationLabelEntity(c)
    override fun getIntelIconEntity(plugin: IntelInfoPlugin?): SectorEntityToken? = api?.getIntelIconEntity(plugin)
    override fun addPing(entity: SectorEntityToken?, color: Color?, maxRange: Float, num: Int) =
        onMap { (it as SectorMapAPI).addPing(entity, color, maxRange, num) }
    override fun addPing(entity: SectorEntityToken?, color: Color?, maxRange: Float, num: Int, interval: Float) =
        onMap { (it as SectorMapAPI).addPing(entity, color, maxRange, num, interval) }
}
