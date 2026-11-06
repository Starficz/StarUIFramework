package org.starficz.starui.core

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable
import org.starficz.starui.core.ReflectionUtils.invoke
import java.awt.Color

fun UIComponentAPI.addTooltip(
    location: TooltipMakerAPI.TooltipLocation,
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
            TooltipMakerAPI.TooltipLocation.LEFT -> tooltipClass.invoke("addTooltipLeft", this, tooltip)
            TooltipMakerAPI.TooltipLocation.RIGHT -> tooltipClass.invoke("addTooltipRight", this, tooltip)
            TooltipMakerAPI.TooltipLocation.ABOVE -> tooltipClass.invoke("addTooltipAbove", this, tooltip)
            TooltipMakerAPI.TooltipLocation.BELOW -> tooltipClass.invoke("addTooltipBelow", this, tooltip)
        }
    }
    else{
        when(location){
            TooltipMakerAPI.TooltipLocation.LEFT -> tooltipClass.invoke("addTooltipLeft", this, tooltip, margin)
            TooltipMakerAPI.TooltipLocation.RIGHT -> tooltipClass.invoke("addTooltipRight", this, tooltip, margin)
            TooltipMakerAPI.TooltipLocation.ABOVE -> tooltipClass.invoke("addTooltipAbove", this, tooltip, margin)
            TooltipMakerAPI.TooltipLocation.BELOW -> tooltipClass.invoke("addTooltipBelow", this, tooltip, margin)
        }
    }
}

fun UIPanelAPI.addImage(
    imageSpritePath: String,
    width: Float = 100f,
    height: Float = 100f
): BoxedUIImage {
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, false)
    tempTMAPI.addImage(imageSpritePath, width, height, 0f)

    val innerImg = (tempTMAPI.children[0] as UIPanelAPI).children[0]
    this.addComponent(innerImg)

    return BoxedUIImage(innerImg)
}

fun UIPanelAPI.addShipPreview(
    width: Float, height: Float,
    fleetMember: FleetMemberAPI? = null,
    style: BoxedUIShipPreview.Style = BoxedUIShipPreview.Style.NORMAL,
    color: Color = Global.getSettings().getColor("textFriendColor")
): BoxedUIShipPreview {
    val enum = BoxedUIShipPreview.ENUM_ARRAY!!.find { it!!.invoke("name") == style.name }
    val shipPreview = if(fleetMember != null) {
        BoxedUIShipPreview.FLEETMEMBER_CONSTRUCTOR!!.newInstance(fleetMember)
    } else{
        BoxedUIShipPreview.ENUM_CONSTRUCTOR!!.newInstance(enum, color)
    } as UIPanelAPI
    val boxedUIShipPreview = BoxedUIShipPreview(shipPreview)

    shipPreview.setSize(width, height)
    boxedUIShipPreview.setBorderNewStyle(true)
    boxedUIShipPreview.setShowBorder(false)
    boxedUIShipPreview.adjustOverlay(0f, 0f)
    this.addComponent(shipPreview)

    return boxedUIShipPreview
}


fun UIPanelAPI.addPara(
    text: String, font: Font? = null, color: Color? = null,
    highlightedText: Collection<Pair<String, Color>>? = null,
    widthOverride: Float? = null, xPad: Float = 0f, yPad: Float = 0f
): BoxedUILabel {
    val tempPanel = Global.getSettings().createCustom(10000f, 10000f, null)
    val tempTMAPI = tempPanel.createUIElement(10000f, 10000f, false)
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
    if (widthOverride != null) {
        para.autoSizeToWidth(widthOverride)
    } else {
        para.invoke("autoSize")
    }
    para.setSize(para.width + xPad * 2, para.height + yPad * 2)

    return BoxedUILabel(para)
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
        Font.ORBITRON_12_CONDENSED -> tempTMAPI.setButtonFontDefault()
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
    width: Float, height: Float, font: Font? = null, leftAlign: Boolean = false): ButtonAPI {
    // make a button in a temp panel/element
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, false)
    font?.let { tempTMAPI.setAreaCheckboxFont(getFontPath(font)) }

    val button = tempTMAPI.addAreaCheckbox(
        text, data, baseColor, bgColor, brightColor, width, height, 0f, leftAlign)

    this.addComponent(button)
    button.xAlignOffset = 0f
    button.yAlignOffset = 0f
    return button
}

fun UIPanelAPI.addCheckbox(width: Float, height: Float, text: String? = null, data: Any? = null,
                           font: Font = Font.INSIGNIA_15, color: Color = Global.getSettings().basePlayerColor,
                           size: ButtonAPI.UICheckboxSize? = ButtonAPI.UICheckboxSize.SMALL): ButtonAPI {
    // make a button in a temp panel/element
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tempTMAPI = tempPanel.createUIElement(width, height, false)

    val checkbox = tempTMAPI.addCheckbox(width, height, text, data, getFontPath(font), color, size, 0f)

    this.addComponent(checkbox)
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