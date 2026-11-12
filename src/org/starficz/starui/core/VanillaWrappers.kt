package org.starficz.starui.core

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
    var isSchematicMode: Boolean
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
    fun setVariant(variant: ShipVariantAPI, title: String? = null, subtitle: String? = null)
    fun updateWithStatus(fleetMember: FleetMemberAPI, fleetStatus: FleetMemberStatusAPI)
    fun setWingSpec(fighterWingSpec: FighterWingSpecAPI)
    fun setScaleDownSmallerShipsMagnitude(mag: Float)
    fun adjustOverlay(scanlineAlpha: Float, tintAlpha: Float)
    fun makeLookDisabled()
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

    override var isSchematicMode: Boolean
        get() = uiShipPreview.invoke("isSchematicMode") as Boolean
        set(schematicMode) { uiShipPreview.invoke("setSchematicMode", schematicMode) }

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

    override fun setVariant(variant: ShipVariantAPI, title: String?, subtitle: String?) {
        uiShipPreview.invoke("setVariant", variant, title, subtitle)
    }
    override fun updateWithStatus(fleetMember: FleetMemberAPI, fleetStatus: FleetMemberStatusAPI) {
        uiShipPreview.invoke("updateWithStatus", fleetMember, fleetStatus)
    }
    override fun setWingSpec(fighterWingSpec: FighterWingSpecAPI) { uiShipPreview.invoke("setWingSpec", fighterWingSpec) }

    override fun setScaleDownSmallerShipsMagnitude(mag: Float) {
        uiShipPreview.invoke("setScaleDownSmallerShipsMagnitude", mag)
    }
    override fun adjustOverlay(scanlineAlpha: Float, tintAlpha: Float){
        uiShipPreview.invoke("adjustOverlay", scanlineAlpha, tintAlpha)
    }
    override fun makeLookDisabled(){ uiShipPreview.invoke("makeLookDisabled") }

    override fun setLabelColor(color: Color) { uiShipPreview.invoke("setLabelColor", color) }
    override fun hideLabels(){ uiShipPreview.invoke("hideLabels") }
}