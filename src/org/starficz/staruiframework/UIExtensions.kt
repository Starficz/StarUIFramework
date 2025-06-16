package org.starficz.staruiframework


import com.fs.graphics.Sprite
import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.ui.ButtonAPI.UICheckboxSize
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable
import org.starficz.staruiframework.ReflectionUtils.getMethodsMatching
import org.starficz.staruiframework.ReflectionUtils.invoke
import org.starficz.staruiframework.ReflectionUtils.set
import java.awt.Color

// UIComponentAPI extensions that expose UIComponent fields/methods
var UIComponentAPI.fader: Fader?
    get() = invoke("getFader") as Fader?
    set(fader) { invoke("setFader", fader) }

var UIComponentAPI.parent: UIPanelAPI?
    get() = invoke("getParent") as UIPanelAPI?
    set(parent) { invoke("setParent", parent) }

fun UIComponentAPI.setMouseOverPad(leftPad: Float, rightPad: Float, topPad: Float, bottomPad: Float) {
    invoke("setMouseOverPad", leftPad, rightPad, topPad, bottomPad)
}

val UIComponentAPI.mouseoverHighlightFader: Fader?
    get() = invoke("getMouseoverHighlightFader") as Fader?

val UIComponentAPI.topAncestor: UIPanelAPI?
    get() = invoke("findTopAncestor") as UIPanelAPI?

fun UIComponentAPI.setTooltipOffsetFromCenter(xMargin: Float, yMargin: Float){
    invoke("setTooltipOffsetFromCenter", xMargin, yMargin)
}

fun UIComponentAPI.setTooltipPositionRelativeToAnchor(xMargin: Float, yMargin: Float, anchor: UIComponentAPI){
    invoke("setTooltipPositionRelativeToAnchor", xMargin, yMargin, anchor)
}

fun UIComponentAPI.setSlideData(xOffset: Float, yOffset: Float, durationIn: Float, durationOut: Float){
    invoke("setSlideData", xOffset, yOffset, durationIn, durationOut)
}

fun UIComponentAPI.slideIn(){
    invoke("slideIn")
}

fun UIComponentAPI.slideOut(){
    invoke("slideOut")
}

fun UIComponentAPI.forceSlideIn(){
    invoke("forceSlideIn")
}

fun UIComponentAPI.forceSlideOut(){
    invoke("forceSlideOut")
}

val UIComponentAPI.sliding: Boolean
    get() = invoke("isSliding") as Boolean

val UIComponentAPI.slidIn: Boolean
    get() = invoke("isSlidIn") as Boolean

val UIComponentAPI.slidOut: Boolean
    get() = invoke("isSlidOut") as Boolean

val UIComponentAPI.slidingIn: Boolean
    get() = invoke("isSlidingIn") as Boolean

var UIComponentAPI.enabled: Boolean
    get() = invoke("isEnabled") as Boolean
    set(enabled) { invoke("setEnabled", enabled) }

var UIComponentAPI.width
    get() = position.width
    set(width) { position.setSize(width, position.height) }

var UIComponentAPI.height
    get() = position.height
    set(height) { position.setSize(position.width, height) }

fun UIComponentAPI.setSize(width: Float, height: Float){
    position.setSize(width, height)
}

val UIComponentAPI.x
    get() = position.x

val UIComponentAPI.y
    get() = position.y

val UIComponentAPI.left
    get() = x

val UIComponentAPI.bottom
    get() = y

val UIComponentAPI.top
    get() = y + height

val UIComponentAPI.right
    get() = x + width

fun UIComponentAPI.setLocation(x: Float, y: Float){
    position.setLocation(x, y)
}

/**
 * Uses x/yAlignOffset to position the bottom left of a UIComponent in absolute pixel coords,
 * where (0,0) is the bottom left of the screen.
 */
fun UIComponentAPI.setAbsoluteLocation(x: Float, y: Float){
    xAlignOffset += (x - position.x)
    yAlignOffset += (y - position.y)
}

val UIComponentAPI.centerX
    get() = position.centerX

val UIComponentAPI.centerY
    get() = position.centerY

var UIComponentAPI.xAlignOffset: Float
    get() = position.invoke("getXAlignOffset") as Float
    set(xOffset) { position.setXAlignOffset(xOffset) }

var UIComponentAPI.yAlignOffset: Float
    get() = position.invoke("getYAlignOffset") as Float
    set(yOffset) { position.setYAlignOffset(yOffset) }



fun UIComponentAPI.anchorInTopLeftOfParent(xMargin: Float = 0f, yMargin: Float = 0f) {
    this.position.inTL(xMargin, yMargin)
}
fun UIComponentAPI.anchorInTopRightOfParent(xMargin: Float = 0f, yMargin: Float = 0f) {
    this.position.inTR(xMargin, yMargin)
}
fun UIComponentAPI.anchorInTopMiddleOfParent(yMargin: Float = 0f) {
    this.position.inTMid(yMargin)
}
fun UIComponentAPI.anchorInBottomLeftOfParent(xMargin: Float = 0f, yMargin: Float = 0f) {
    this.position.inBL(xMargin, yMargin)
}
fun UIComponentAPI.anchorInBottomMiddleOfParent(yMargin: Float = 0f) {
    this.position.inBMid(yMargin)
}
fun UIComponentAPI.anchorInBottomRightOfParent(xMargin: Float = 0f, yMargin: Float = 0f) {
    this.position.inBR(xMargin, yMargin)
}
fun UIComponentAPI.anchorInLeftMiddleOfParent(xMargin: Float = 0f) {
    this.position.inLMid(xMargin)
}
fun UIComponentAPI.anchorInRightMiddleOfParent(xMargin: Float = 0f) {
    this.position.inRMid(xMargin)
}
fun UIComponentAPI.anchorInCenterOfParent() {
    val floatType = Float::class.javaPrimitiveType!!
    val paramTypes = listOf<Class<*>?>(this.position::class.java,
        floatType, floatType, floatType, floatType, floatType, floatType).toTypedArray()

    this.position.getMethodsMatching("relativeTo", parameterTypes = paramTypes)[0]
        .invoke(this.position, null, 0.5f, 0.5f, -0.5f, -0.5f, 0f, 0f)
}

val UIPanelAPI.previousComponent
    get() = getChildrenCopy().lastOrNull()

fun UIComponentAPI.anchorRightOfPreviousMatchingTop(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.rightOfTop(it, margin) }
}
fun UIComponentAPI.anchorLeftOfPreviousMatchingTop(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.leftOfTop(it, margin) }
}
fun UIComponentAPI.anchorLeftOfPreviousMatchingMid(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.leftOfMid(it, margin) }
}
fun UIComponentAPI.anchorLeftOfPreviousMatchingBottom(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.leftOfBottom(it, margin) }
}
fun UIComponentAPI.anchorRightOfPreviousMatchingMid(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.rightOfMid(it, margin) }
}
fun UIComponentAPI.anchorRightOfPreviousMatchingBottom(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.rightOfBottom(it, margin) }
}
fun UIComponentAPI.anchorAbovePreviousMatchingLeft(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.aboveLeft(it, margin) }
}
fun UIComponentAPI.anchorAbovePreviousMatchingMid(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.aboveMid(it, margin) }
}
fun UIComponentAPI.anchorAbovePreviousMatchingRight(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.aboveRight(it, margin) }
}
fun UIComponentAPI.anchorBelowPreviousMatchingLeft(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.belowLeft(it, margin) }
}
fun UIComponentAPI.anchorBelowPreviousMatchingMid(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.belowMid(it, margin) }
}
fun UIComponentAPI.anchorBelowPreviousMatchingRight(margin: Float = 0f) {
    parent?.getChildrenCopy()?.dropLast(1)?.lastOrNull()?.let { this.position.belowRight(it, margin) }
}
fun UIComponentAPI.anchorToPreviousMatchingCenter(xOffset: Float = 0f, yOffset: Float = 0f) {
    val parent = this.parent ?: return; val children = parent.getChildrenCopy(); if (children.size <= 1) return
    val anchor = children.dropLast(1).lastOrNull()
    anchor?.let { nonNullAnchor ->
        this.position.invoke("relativeTo", nonNullAnchor, 0.5f, 0.5f, -0.5f, -0.5f, xOffset, yOffset)
    }
}

fun UIComponentAPI.addTooltip(
    location: TooltipLocation,
    width: Float,
    margin: Float? = null,
    lambda: (TooltipMakerAPI) -> Unit) {
    val tooltip = object: StandardTooltipV2Expandable(width, false, true) {
        override fun createImpl(p0: Boolean) {
            lambda(this)
        }
    }


    val tooltipClass = StandardTooltipV2Expandable::class.java
    if(margin == null){
        when(location){
            TooltipLocation.LEFT -> tooltipClass.invoke("addTooltipLeft", this, tooltip)
            TooltipLocation.RIGHT -> tooltipClass.invoke("addTooltipRight", this, tooltip)
            TooltipLocation.ABOVE -> tooltipClass.invoke("addTooltipAbove", this, tooltip)
            TooltipLocation.BELOW -> tooltipClass.invoke("addTooltipBelow", this, tooltip)
        }
    }
    else{
        when(location){
            TooltipLocation.LEFT -> tooltipClass.invoke("addTooltipLeft", this, tooltip, margin)
            TooltipLocation.RIGHT -> tooltipClass.invoke("addTooltipRight", this, tooltip, margin)
            TooltipLocation.ABOVE -> tooltipClass.invoke("addTooltipAbove", this, tooltip, margin)
            TooltipLocation.BELOW -> tooltipClass.invoke("addTooltipBelow", this, tooltip, margin)
        }
    }
}

// UIPanelAPI extensions that expose UIPanel methods
fun UIPanelAPI.getChildrenCopy(): List<UIComponentAPI> {
    return invoke("getChildrenCopy") as List<UIComponentAPI>
}

fun UIPanelAPI.getChildrenNonCopy(): List<UIComponentAPI> {
    return invoke("getChildrenNonCopy") as List<UIComponentAPI>
}

fun UIPanelAPI.findChildWithMethod(methodName: String): UIComponentAPI? {
    return getChildrenCopy().find { it.getMethodsMatching(methodName).isNotEmpty() }
}

fun UIPanelAPI.allChildsWithMethod(methodName: String): List<UIComponentAPI> {
    return getChildrenCopy().filter { it.getMethodsMatching(methodName).isNotEmpty() }
}

fun UIPanelAPI.clearChildren() {
    invoke("clearChildren")
}

// Abstract base class for Boxed vanilla elements to fix vanilla jank / things with no API's (like images)
abstract class BoxedUIElement(val boxedElement: UIComponentAPI)

class BoxedUILabel(val uiLabel: LabelAPI): BoxedUIElement(uiLabel as UIComponentAPI),
    UIComponentAPI by (uiLabel as UIComponentAPI), LabelAPI by uiLabel {
    override fun advance(amount: Float) { uiLabel.advance(amount) }
    override fun getOpacity() = uiLabel.opacity
    override fun setOpacity(opacity: Float) { uiLabel.opacity = opacity }
    override fun render(alphaMult: Float) { uiLabel.render(alphaMult) }
    override fun getPosition() = uiLabel.position
}

class BoxedUIImage(val uiImage: UIComponentAPI): BoxedUIElement(uiImage), UIComponentAPI by uiImage {
    var spriteName = uiImage.invoke("getSpriteName") as String
        set(newSpriteName) { uiImage.invoke("setSprite", newSpriteName, true) }
    var sprite = uiImage.invoke("getSprite") as Sprite
        set(newSprite) { uiImage.invoke("setSprite", newSprite, true) }

    var borderColor = uiImage.invoke("getBorderColor") as Color
        set(newColor) { uiImage.invoke("setBorderColor", newColor, true) }

    var outline = uiImage.invoke("isWithOutline") as Boolean
        set(withOutline) { uiImage.invoke("setWithOutline", withOutline) }

    var textureClamp = uiImage.invoke("isTexClamp") as Boolean
        set(texClamp) { uiImage.invoke("setTexClamp", texClamp) }

    var forceNoRounding = uiImage.invoke("isForceNoRounding") as Boolean
        set(noRounding) { uiImage.invoke("setForceNoRounding", noRounding) }

    val originalAspectRatio = uiImage.invoke("getOriginalAR") as Float

    fun setStretch(stretch: Boolean) { uiImage.invoke("setStretch", stretch) }
    fun setRenderSchematic(renderSchematic: Boolean) { uiImage.invoke("setRenderSchematic", renderSchematic) }
    fun sizeToOriginalSpriteSize() { uiImage.invoke("autoSize") }
    fun sizeToOriginalAspectRatioWithWidth(width: Float) { uiImage.invoke("autoSizeToWidth", width) }
    fun sizeToOriginalAspectRatioWithHeight(height: Float) { uiImage.invoke("autoSizeToHeight", height) }
}

class BoxedSliderBar(val uiBar: UIPanelAPI): BoxedUIElement(uiBar), UIPanelAPI by uiBar{

}

fun UIPanelAPI.addPara(text: String, font: Font? = null, color: Color? = null,
                                highlightedText: Collection<Pair<String, Color>>? = null,
                                widthOverride: Float? = null, xPad: Float = 0f, yPad: Float = 0f): BoxedUILabel {
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, false)
    color?.let { tempTMAPI.setParaFontColor(it) }
    font?.let { tempTMAPI.setParaFont(getFontPath(font)) }

    val para = if(highlightedText != null){
        val (highlights, highlightColors) = highlightedText.unzip()
        tempTMAPI.addPara(text, 0f, highlightColors.toTypedArray(), *highlights.toTypedArray())
    } else {
        tempTMAPI.addPara(text, 0f)
    }

    this.addComponent(para as UIComponentAPI)
    para.setAlignment(Alignment.MID)
    para.invoke("autoSize")
    widthOverride?.let { para.autoSizeToWidth(it) }

    para.setSize(para.width + xPad*2, para.height + yPad*2)
    return BoxedUILabel(para)
}

fun UIPanelAPI.addImage(imageSpritePath: String, width: Float, height: Float): BoxedUIImage {
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, false)
    tempTMAPI.addImage(imageSpritePath, width, height, 0f)
    val tempTMAPIsUIPanel = tempTMAPI.getChildrenCopy()[0] as UIPanelAPI
    val image = tempTMAPIsUIPanel.getChildrenCopy()[0]

    this.addComponent(image)
    return BoxedUIImage(image)
}

fun UIPanelAPI.addLabelledValue(label: String, value: String, labelColor: Color, valueColor: Color, width: Float): UIComponentAPI {
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, false)
    val labelledValue = tempTMAPI.addLabelledValue(label, value, labelColor, valueColor, width, 0f)
    this.addComponent(labelledValue)
    return BoxedUIImage(labelledValue)
}

fun UIPanelAPI.addTextField(width: Float, height: Float, font: Font): TextFieldAPI {
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, false)
    val textField = tempTMAPI.addTextField(width, height, getFontPath(font), 0f)
    this.addComponent(textField)
    return textField
}

fun UIPanelAPI.addButton(
    text: String, data: Any?, baseColor: Color, bgColor: Color, align: Alignment, style: CutStyle,
    width: Float, height: Float, font: Font? = null, shortcut: Int? = null): ButtonAPI {
    // make a button in a temp panel/element
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, false)
    when(font){
        Font.VICTOR_10 -> tempTMAPI.setButtonFontVictor10()
        Font.VICTOR_14 -> tempTMAPI.setButtonFontVictor14()
        Font.ORBITRON_20 -> tempTMAPI.setButtonFontOrbitron20()
        Font.ORBITRON_20_BOLD -> tempTMAPI.setButtonFontOrbitron20Bold()
        Font.ORBITRON_24 -> tempTMAPI.setButtonFontOrbitron24()
        Font.ORBITRON_24_BOLD -> tempTMAPI.setButtonFontOrbitron24Bold()
        null -> tempTMAPI.setButtonFontDefault()
        else -> throw IllegalArgumentException("Button does not support font of type: $font")
    }
    val button = tempTMAPI.addButton(text, data, baseColor, bgColor, align, style, width, height, 0f)
    shortcut?.let { button.setShortcut(shortcut, true) }

    // hijack button and move it to UIPanel
    this.addComponent(button)
    button.xAlignOffset = 0f
    button.yAlignOffset = 0f
    return button
}

fun UIPanelAPI.addAreaCheckbox(
    text: String, data: Any?, baseColor: Color, bgColor: Color, brightColor: Color,
    width: Float, height: Float, font: Font? = null, leftAlign: Boolean = false, flag: Flag? = null): ButtonAPI {
    // make a button in a temp panel/element
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, false)
    font?.let { tempTMAPI.setAreaCheckboxFont(getFontPath(font)) }

    val button = tempTMAPI.addAreaCheckbox(
        text, data, baseColor, bgColor, brightColor, width, height, 0f, leftAlign)

    this.addComponent(button)
    if (flag != null) {
        button.isChecked = flag.isChecked
        button.onClick { flag.isChecked = button.isChecked }
    }
    button.xAlignOffset = 0f
    button.yAlignOffset = 0f
    return button
}

fun UIPanelAPI.addCheckbox(width: Float, height: Float, text: String, data: Any?, font: Font = Font.INSIGNIA_15,
                                    color: Color, size: UICheckboxSize? = UICheckboxSize.SMALL, flag: Flag? = null): ButtonAPI {
    // make a button in a temp panel/element
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, false)

    val checkbox = tempTMAPI.addCheckbox(width, height, text, data, getFontPath(font), color, size, 0f)

    this.addComponent(checkbox)
    if (flag != null) {
        checkbox.isChecked = flag.isChecked
        checkbox.onClick { flag.isChecked = checkbox.isChecked }
    }
    checkbox.xAlignOffset = 0f
    checkbox.yAlignOffset = 0f
    return checkbox
}


// CustomPanelAPI implements the same Listener that a ButtonAPI requires,
// A CustomPanel then happens to trigger its CustomUIPanelPlugin buttonPressed() method
// thus we can map our functions into a CustomUIPanelPlugin, and have them be triggered
class ButtonListener(button: ButtonAPI) : BaseCustomUIPanelPlugin() {
    private val onClickFunctions = mutableListOf<() -> Unit>()

    init {
        val buttonListener = Global.getSettings().createCustom(0f, 0f, this)
        button.invoke("setListener", buttonListener)
    }
    override fun buttonPressed(buttonId: Any?) { onClickFunctions.forEach { it() } }
    fun addOnClick(function: () -> Unit) { onClickFunctions.add(function) }
    fun clearOnClickFunctions() { onClickFunctions.clear() }
}

// Extension function for ButtonAPI
fun ButtonAPI.onClick(function: () -> Unit) {
    // Use reflection to check if this button already has a listener
    val existingListener = invoke("getListener")
    if (existingListener is CustomPanelAPI && existingListener.plugin is ButtonListener) {
        (existingListener.plugin as ButtonListener).addOnClick(function)
    } else {
        // if not, make one
        val listener = ButtonListener(this)
        listener.addOnClick(function)
    }
}

// Custom CustomUIPanelPlugin extensions that map the plugin position to the panel
val StarUIPanelPlugin.width
    get() = customPanel.width

val StarUIPanelPlugin.height
    get() = customPanel.height

val StarUIPanelPlugin.x
    get() = customPanel.x

val StarUIPanelPlugin.y
    get() = customPanel.y

val StarUIPanelPlugin.left
    get() = x

val StarUIPanelPlugin.bottom
    get() = y

val StarUIPanelPlugin.top
    get() = y + height

val StarUIPanelPlugin.right
    get() = x + width

val StarUIPanelPlugin.centerX
    get() = customPanel.centerX

val StarUIPanelPlugin.centerY
    get() = customPanel.centerY

val StarUIPanelPlugin.xAlignOffset
    get() = customPanel.xAlignOffset

val StarUIPanelPlugin.yAlignOffset
    get() = customPanel.yAlignOffset

fun CustomPanelAPI.setPlugin(plugin: CustomUIPanelPlugin) {
    set(value=plugin)
}
