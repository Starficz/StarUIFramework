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

// Abstract base class for Boxed vanilla elements to fix vanilla jank / things with no API's (like images)
abstract class BoxedUIElement(val boxedElement: UIComponentAPI)

interface ExtendedScrollPanelAPI : ScrollPanelAPI, UIPanelAPI {
    var isLeftScrollbar: Boolean
    var renderShadows: Boolean
    var maxShadowHeight: Float
    var scrollWidgetColor: Color
    var scrollRailColor: Color
    val contentContainer: UIPanelAPI
    var shadowColor: Color

    fun addToOverlay(uiComponent: UIComponentAPI)
    fun removeFromOverlay(uiComponent: UIComponentAPI)
    fun setContentSize(width: Float, height: Float)
    fun setUseSimpleShadows(simpleShadows: Boolean)

    // Missing Vanilla Methods!
    fun scrollToBottom()
    fun scrollToY(y: Float)
    fun setShowScrollbars(show: Boolean)
    fun setUseMWheel(use: Boolean)
    fun setOffset(xOffset: Float, yOffset: Float)
    fun resetOffset()
    fun ensureVisible(component: UIComponentAPI)
    fun saveVerticalScrollState()
    fun restoreVerticalScrollState()
}

class BoxedScrollPanel(val scrollPanel: ScrollPanelAPI) :
    BoxedUIElement(scrollPanel),
    ExtendedScrollPanelAPI,
    ScrollPanelAPI by scrollPanel,
    UIPanelAPI by (scrollPanel as UIPanelAPI)
{
    override fun getPosition(): com.fs.starfarer.api.ui.PositionAPI = scrollPanel.position
    override fun render(alphaMult: Float) = scrollPanel.render(alphaMult)
    override fun processInput(events: MutableList<com.fs.starfarer.api.input.InputEventAPI>?) = scrollPanel.processInput(events)
    override fun advance(amount: Float) = scrollPanel.advance(amount)
    override fun addComponent(custom: UIComponentAPI?): com.fs.starfarer.api.ui.PositionAPI = scrollPanel.addComponent(custom)
    override fun removeComponent(custom: UIComponentAPI?) = scrollPanel.removeComponent(custom)
    override fun bringComponentToTop(c: UIComponentAPI?) = scrollPanel.bringComponentToTop(c)
    override fun sendToBottom(c: UIComponentAPI?) = scrollPanel.sendToBottom(c)
    override fun getOpacity(): Float = scrollPanel.opacity
    override fun setOpacity(opacity: Float) { scrollPanel.opacity = opacity }

    override var isLeftScrollbar: Boolean
        get() = scrollPanel.invoke("isLeftScrollbar") as Boolean
        set(value) { scrollPanel.invoke("setLeftScrollbar", value) }
    override var renderShadows: Boolean
        get() = (!(scrollPanel.invoke("isDoNotRenderShadows") as Boolean))
        set(value) { scrollPanel.invoke("setDoNotRenderShadows", !value) }
    override var maxShadowHeight: Float
        get() = scrollPanel.invoke("getMaxShadowHeight") as Float
        set(value) { scrollPanel.invoke("setMaxShadowHeight", value) }
    override var scrollWidgetColor: Color
        get() = scrollPanel.invoke("getScrollWidgetColor") as Color
        set(value) { scrollPanel.invoke("setScrollWidgetColor", value) }
    override var scrollRailColor: Color
        get() = scrollPanel.invoke("getScrollRailColor") as Color
        set(value) { scrollPanel.invoke("setScrollRailColor", value) }
    override val contentContainer: UIPanelAPI
        get() = scrollPanel.invoke("getContentContainer") as UIPanelAPI
    override var shadowColor: Color
        get() = scrollPanel.invoke("getDarkColor") as Color
        set(value) { scrollPanel.invoke("setDarkColor", value) }

    override fun addToOverlay(uiComponent: UIComponentAPI) { scrollPanel.invoke("addToOverlay", uiComponent) }
    override fun removeFromOverlay(uiComponent: UIComponentAPI) { scrollPanel.invoke("removeFromOverlay", uiComponent) }
    override fun setContentSize(width: Float, height: Float) { scrollPanel.invoke("setContentSize", width, height) }
    override fun setUseSimpleShadows(simpleShadows: Boolean) { scrollPanel.invoke("setUseSimpleShadows", simpleShadows) }

    // Invoke the missing methods
    override fun scrollToBottom() { scrollPanel.invoke("scrollToBottom") }
    override fun scrollToY(y: Float) { scrollPanel.invoke("scrollToY", y) }
    override fun setShowScrollbars(show: Boolean) { scrollPanel.invoke("setShowScrollbars", show) }
    override fun setUseMWheel(use: Boolean) { scrollPanel.invoke("setUseMWheel", use) }
    override fun setOffset(xOffset: Float, yOffset: Float) { scrollPanel.invoke("setOffset", xOffset, yOffset) }
    override fun resetOffset() { scrollPanel.invoke("resetOffset") }
    override fun saveVerticalScrollState() { scrollPanel.invoke("saveVerticalScrollState") }
    override fun restoreVerticalScrollState() { scrollPanel.invoke("restoreVerticalScrollState") }
    override fun ensureVisible(component: UIComponentAPI) {
        val actualComponent = if (component is BoxedUIElement) component.boxedElement else component
        scrollPanel.invoke("ensureVisible", actualComponent)
    }
}

interface ExtendedLabelAPI : LabelAPI {
    fun autoSize()
}

class BoxedUILabel(val uiLabel: LabelAPI) :
    BoxedUIElement(uiLabel as UIComponentAPI),
    UIComponentAPI by (uiLabel as UIComponentAPI),
    LabelAPI by uiLabel,
    ExtendedLabelAPI
{
    override fun getPosition(): com.fs.starfarer.api.ui.PositionAPI = uiLabel.position
    override fun render(alphaMult: Float) = uiLabel.render(alphaMult)
    override fun advance(amount: Float) = uiLabel.advance(amount)
    override fun getOpacity(): Float = uiLabel.opacity
    override fun setOpacity(opacity: Float) { uiLabel.opacity = opacity }

    override fun autoSize() { (uiLabel as UIComponentAPI).invoke("autoSize") }
}

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

interface ShipPreviewAPI : UIPanelAPI {
    var scaleDownToFitWidth: Float
    var scaleDownToFitHeight: Float
    var overwriteScale: Float?
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

    override var scaleDownToFitWidth: Float
        get() = uiShipPreview.invoke("getScaleDownToFitWidth") as Float
        set(width) { uiShipPreview.invoke("setScaleDownToFitWidth", width) }

    override var scaleDownToFitHeight: Float
        get() = uiShipPreview.invoke("getScaleDownToFitHeight") as Float
        set(height) { uiShipPreview.invoke("setScaleDownToFitHeight", height) }

    override var overwriteScale: Float?
        get() = uiShipPreview.invoke("getOverwriteScale") as Float?
        set(scale) { uiShipPreview.invoke("setOverwriteScale", scale) }

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