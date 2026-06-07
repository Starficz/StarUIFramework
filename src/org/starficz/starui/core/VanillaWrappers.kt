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
import org.starficz.starui.compose.StarUIDsl
import java.awt.Color

// Abstract base class for Boxed vanilla elements to fix vanilla jank / things with no API's (like images)
abstract class BoxedUIElement(val boxedElement: UIComponentAPI)

@StarUIDsl
class BoxedScrollPanel(val scrollPanel: ScrollPanelAPI): BoxedUIElement(scrollPanel as UIComponentAPI),
    ScrollPanelAPI by scrollPanel {
    var isLeftScrollbar
        get() = scrollPanel.invoke("isLeftScrollbar") as Boolean
        set(isLeftScrollbar) { scrollPanel.invoke("setLeftScrollbar", isLeftScrollbar) }
    var renderShadows
        get() = (!(scrollPanel.invoke("isDoNotRenderShadows") as Boolean))
        set(renderShadows) { scrollPanel.invoke("setDoNotRenderShadows", !renderShadows) }
    var maxShadowHeight
        get() = scrollPanel.invoke("getMaxShadowHeight") as Float
        set(maxShadowHeight) { scrollPanel.invoke("setMaxShadowHeight", maxShadowHeight) }
    var scrollWidgetColor
        get() = scrollPanel.invoke("getScrollWidgetColor") as Color
        set(scrollWidgetColor) { scrollPanel.invoke("setScrollWidgetColor", scrollWidgetColor) }
    var scrollRailColor
        get() = scrollPanel.invoke("getScrollRailColor") as Color
        set(scrollRailColor) { scrollPanel.invoke("setScrollRailColor", scrollRailColor) }
    val contentContainer
        get() = scrollPanel.invoke("getContentContainer") as UIPanelAPI

    fun addToOverlay(uiComponent: UIComponentAPI) { scrollPanel.invoke("addToOverlay", uiComponent) }
    fun removeFromOverlay(uiComponent: UIComponentAPI) { scrollPanel.invoke("removeFromOverlay", uiComponent) }
    fun setContentSize(width: Float, height: Float) { scrollPanel.invoke("setContentSize", width, height) }
    fun setUseSimpleShadows(simpleShadows: Boolean) {scrollPanel.invoke("setUseSimpleShadows", simpleShadows) }

}

@StarUIDsl
class BoxedUILabel(val uiLabel: LabelAPI): BoxedUIElement(uiLabel as UIComponentAPI),
    UIComponentAPI by (uiLabel as UIComponentAPI), LabelAPI by uiLabel {
    override fun advance(amount: Float) { uiLabel.advance(amount) }
    override fun getOpacity() = uiLabel.opacity
    override fun setOpacity(opacity: Float) { uiLabel.opacity = opacity }
    override fun render(alphaMult: Float) { uiLabel.render(alphaMult) }
    override fun getPosition() = uiLabel.position
    fun autoSize() { uiLabel.invoke("autoSize") }
}

@StarUIDsl
class BoxedUIImage(val uiImage: UIComponentAPI): BoxedUIElement(uiImage), UIComponentAPI by uiImage {
    var spriteName
        get() = uiImage.invoke("getSpriteName") as String
        set(newSpriteName) { uiImage.invoke("setSprite", newSpriteName, true) }
    var sprite
        get() = uiImage.invoke("getSprite") as Sprite
        set(newSprite) { uiImage.invoke("setSprite", newSprite, true) }

    var borderColor
        get() = uiImage.invoke("getBorderColor") as Color
        set(newColor) { uiImage.invoke("setBorderColor", newColor, true) }

    var outline
        get() = uiImage.invoke("isWithOutline") as Boolean
        set(withOutline) { uiImage.invoke("setWithOutline", withOutline) }

    var textureClamp
        get() = uiImage.invoke("isTexClamp") as Boolean
        set(texClamp) { uiImage.invoke("setTexClamp", texClamp) }

    var forceNoRounding
        get() = uiImage.invoke("isForceNoRounding") as Boolean
        set(noRounding) { uiImage.invoke("setForceNoRounding", noRounding) }

    val originalAspectRatio
        get() = uiImage.invoke("getOriginalAR") as Float

    fun setStretch(stretch: Boolean) { uiImage.invoke("setStretch", stretch) }
    fun setRenderSchematic(renderSchematic: Boolean) { uiImage.invoke("setRenderSchematic", renderSchematic) }
    fun sizeToOriginalSpriteSize() { uiImage.invoke("autoSize") }
    fun sizeToOriginalAspectRatioWithWidth(width: Float) { uiImage.invoke("autoSizeToWidth", width) }
    fun sizeToOriginalAspectRatioWithHeight(height: Float) { uiImage.invoke("autoSizeToHeight", height) }
}

@StarUIDsl
class BoxedUISliderBar(val uiBar: UIPanelAPI): BoxedUIElement(uiBar), UIComponentAPI by uiBar {
    var bonusColor
        get() = uiBar.invoke("getBonusColor") as Color
        set(color) { uiBar.invoke("setBonusColor", color) }

    var widgetColor
        get() = uiBar.invoke("getWidgetColor") as Color
        set(color) { uiBar.invoke("setWidgetColor", color) }

    var barColor
        get() = uiBar.invoke("getBarColor") as Color
        set(color) { uiBar.invoke("setBarColor", color) }

    var numSubivisions
        get() = uiBar.invoke("getNumSubivisions") as Int
        set(amount) { uiBar.invoke("setNumSubivisions", amount) }

    var roundingIncrement
        get() = uiBar.invoke("getRoundingIncrement") as Int
        set(amount) { uiBar.invoke("setRoundingIncrement", amount) }

    var rangeMax
        get() = uiBar.invoke("getRangeMax") as Float
        set(amount) { uiBar.invoke("setRangeMax", amount) }

    var rangeMin
        get() = uiBar.invoke("getRangeMin") as Float
        set(amount) { uiBar.invoke("setRangeMin", amount) }

    var bonusAmount
        get() = uiBar.invoke("getBonusAmount") as Float
        set(amount) { uiBar.invoke("setBonusAmount", amount) }

    var progress
        get() = uiBar.invoke("getProgress") as Float
        set(amount) { uiBar.invoke("setProgress", amount) }

    var showNotchOnIfBelowProgress
        get() = uiBar.invoke("getShowNotchOnIfBelowProgress") as Float
        set(amount) { uiBar.invoke("setShowNotchOnIfBelowProgress", amount) }

    val isShowNoText
        get() = uiBar.invoke("isShowNoText") as Boolean

    val textLabel
        get() = BoxedUILabel(uiBar.invoke("getValue") as LabelAPI)
}

/**
 * A Boxed representation of a vanilla UI Ship Preview. Generates ShipAPI's on use.
 * For performance reasons, try and call cleanupShips when hidden.
 */
@StarUIDsl
class BoxedUIShipPreview(val uiShipPreview: UIPanelAPI): BoxedUIElement(uiShipPreview), UIComponentAPI by uiShipPreview {
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

    fun getShips(): Array<ShipAPI> {
        uiShipPreview.invoke("prepareShip")
        return uiShipPreview.invoke("getShips") as Array<ShipAPI>
    }
    fun cleanupShips(){ uiShipPreview.invoke("cleanup") }

    var showFighters
        get() = uiShipPreview.invoke("isShowFighters") as Boolean
        set(show) {uiShipPreview.invoke("setShowFighters", show) }

    var showDestroyedFighters
        get() = uiShipPreview.invoke("isShowDestroyedFighters") as Boolean
        set(show) { uiShipPreview.invoke("setShowDestroyedFighters", show) }

    var highlightBrightness
        get() = uiShipPreview.invoke("getHighlightBrightness") as Float
        set(brightness) { uiShipPreview.invoke("setHighlightBrightness", brightness) }

    fun highlight() { uiShipPreview.invoke("highlight") }
    fun unhighlight() { uiShipPreview.invoke("unhighlight") }
    fun getHighlightFader(): Fader = uiShipPreview.invoke("getHighlight") as Fader

    var borderColor
        get() = uiShipPreview.invoke("getBorderColor") as Color
        set(show) { uiShipPreview.invoke("setBorderColor", show) }

    fun setShowBorder(show: Boolean) { uiShipPreview.invoke("setShowBorder", show) }
    fun setBorderNewStyle(newStyle: Boolean) { uiShipPreview.invoke("setShowNewBorder", newStyle) }

    var variant
        get() = uiShipPreview.invoke("getVariant") as ShipVariantAPI?
        set(variant) { uiShipPreview.invoke("setVariant", variant) }

    fun setVariant(variant: ShipVariantAPI, title: String? = null, subtitle: String? = null) {
        uiShipPreview.invoke("setVariant", variant, title, subtitle)
    }
    fun updateWithStatus(fleetMember: FleetMemberAPI, fleetStatus: FleetMemberStatusAPI) {
        uiShipPreview.invoke("updateWithStatus", fleetMember, fleetStatus)
    }
    fun setWingSpec(fighterWingSpec: FighterWingSpecAPI) { uiShipPreview.invoke("setWingSpec", fighterWingSpec) }

    fun setScaleDownSmallerShipsMagnitude(mag: Float) {
        uiShipPreview.invoke("setScaleDownSmallerShipsMagnitude", mag)
    }
    fun adjustOverlay(scanlineAlpha: Float, tintAlpha: Float){
        uiShipPreview.invoke("adjustOverlay", scanlineAlpha, tintAlpha)
    }
    fun makeLookDisabled(){ uiShipPreview.invoke("makeLookDisabled") }

    fun setLabelColor(color: Color) { uiShipPreview.invoke("setLabelColor", color) }
    fun hideLabels(){ uiShipPreview.invoke("hideLabels") }
}