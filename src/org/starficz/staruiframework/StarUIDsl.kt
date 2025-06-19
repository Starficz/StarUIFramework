package org.starficz.staruiframework

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.ui.ButtonAPI.UICheckboxSize
import org.lwjgl.input.Keyboard
import org.starficz.staruiframework.internal.HorizontalStrategy
import org.starficz.staruiframework.internal.StackLayout
import org.starficz.staruiframework.internal.VerticalStrategy
import java.awt.Color

fun UIPanelAPI.CustomPanel(
    width: Float,
    height: Float,
    builder: CustomPanelAPI.(plugin: StarUIPanelPlugin) -> Unit = {}
): CustomPanelAPI {
    val panel = Global.getSettings().createCustom(width, height, null)
    val plugin = StarUIPanelPlugin(panel)
    panel.setPlugin(plugin)
    this.addComponent(panel)
    panel.builder(plugin)
    return panel
}

fun UIPanelAPI.VerticalStackLayout(
    xMargin: Float = 0f,
    yMargin: Float = 0f,
    spacing: Float = 0f,
    alignment: Alignment = Alignment.TMID,
    builder: CustomPanelAPI.(plugin: StarUIPanelPlugin) -> Unit = {}
): CustomPanelAPI {
    return this.StackLayout(VerticalStrategy, alignment, yMargin, xMargin, spacing, builder)
}

fun UIPanelAPI.HorizontalStackLayout(
    xMargin: Float = 0f,
    yMargin: Float = 0f,
    spacing: Float = 0f,
    alignment: Alignment = Alignment.LMID,
    builder: CustomPanelAPI.(plugin: StarUIPanelPlugin) -> Unit = {}
): CustomPanelAPI {
    return this.StackLayout(HorizontalStrategy, alignment, xMargin, yMargin, spacing, builder)
}

fun CustomPanelAPI.TooltipMakerPanel(
    width: Float,
    height: Float,
    withScroller: Boolean = false,
    builder: TooltipMakerAPI.() -> Unit = {}
): TooltipMakerAPI {
    val tooltipMakerPanel = createUIElement(width, height, withScroller)
    addUIElement(tooltipMakerPanel)
    return tooltipMakerPanel.apply(builder)
}

fun UIComponentAPI.Tooltip(
    width: Float,
    location: TooltipMakerAPI.TooltipLocation,
    margin: Float? = null,
    builder: TooltipMakerAPI.() -> Unit = {}
) {
    this.addTooltip(location, width, margin, builder)
}

fun UIPanelAPI.Text(
    text: String,
    font: Font? = null,
    color: Color? = null,
    highlightedText: Collection<Pair<String, Color>>? = null,
    widthOverride: Float? = null, xPad: Float = 0f, yPad: Float = 0f,
    builder: BoxedUILabel.() -> Unit = {}
): BoxedUILabel {
    return this.addPara(text, font, color, highlightedText, widthOverride, xPad, yPad).apply(builder)
}

fun UIPanelAPI.LabelledValue(
    width: Float,
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    builder: UIComponentAPI.() -> Unit = {}
): UIComponentAPI {
    return this.addLabelledValue(label, value, labelColor, valueColor, width).apply(builder)
}

fun UIPanelAPI.TextField(
    width: Float,
    height: Float,
    font: Font,
    builder: TextFieldAPI.() -> Unit = {}
): TextFieldAPI {
    return this.addTextField(width, height, font).apply(builder)
}

fun UIPanelAPI.Image(
    width: Float,
    height: Float,
    imageSpritePath: String,
    builder: BoxedUIImage.() -> Unit = {}
): BoxedUIImage {
    return addImage(imageSpritePath, width, height).apply(builder)
}

fun UIPanelAPI.ShipDisplay(
    width: Float,
    height: Float,
    fleetMember: FleetMemberAPI? = null,
    style: BoxedUIShipPreview.Style = BoxedUIShipPreview.Style.NORMAL,
    color: Color = Global.getSettings().getColor("textFriendColor"),
    builder: BoxedUIShipPreview.() -> Unit = {}
): BoxedUIShipPreview {
    return addShipPreview(width, height, fleetMember, style, color).apply(builder)
}

fun UIPanelAPI.Button(
    width: Float,
    height: Float,
    text: String,
    baseColor: Color,
    bgColor: Color,
    font: Font? = null,
    shortcut: Int? = null,
    align: Alignment = Alignment.MID,
    style: CutStyle = CutStyle.TL_BR,
    builder: ButtonAPI.() -> Unit = {}
): ButtonAPI {
    return this.addButton(text, null, baseColor, bgColor, align, style, width, height, font, shortcut).apply(builder)
}

fun UIPanelAPI.AreaCheckbox(
    width: Float,
    height: Float,
    text: String,
    baseColor: Color,
    bgColor: Color,
    brightColor: Color,
    font: Font? = null,
    leftAlign: Boolean = false,
    flag: Flag? = null,
    buttonGroup: ButtonGroup? = null,
    builder: ButtonAPI.() -> Unit = {}
): ButtonAPI {
    val validGroup = (buttonGroup != null && flag != null)

    val button = this.addAreaCheckbox(text, null, baseColor, bgColor, brightColor, width, height, font,
        leftAlign, if (!validGroup) flag else null).apply(builder)

    if (validGroup) buttonGroup!!.addButtonToGroup(button, flag!!)

    return button
}

fun UIPanelAPI.Checkbox(
    width: Float,
    height: Float,
    text: String,
    color: Color,
    font: Font = Font.INSIGNIA_15,
    size: UICheckboxSize? = UICheckboxSize.SMALL,
    flag: Flag? = null,
    buttonGroup: ButtonGroup? = null,
    builder: ButtonAPI.() -> Unit = {}
): ButtonAPI {
    val validGroup = (buttonGroup != null && flag != null)

    val button = this.addCheckbox(width, height, text, null, font, color, size,
        if (!validGroup) flag else null).apply(builder)

    if (validGroup) buttonGroup!!.addButtonToGroup(button, flag!!)

    return button
}

class ButtonGroup {
    private val allFlags: MutableCollection<Flag> = mutableListOf()

    internal fun addButtonToGroup(button: ButtonAPI, flag: Flag){
        allFlags.add(flag)
        button.isChecked = flag.isChecked
        button.onClick {
            if (allFlags.count { it.isChecked } == 1 && flag.isChecked) {
                // If the only active item is clicked, re-enable all items in the group.
                allFlags.forEach { it.isChecked = true }
            } else {
                // if multiselect key (Shift or Ctrl) is held, toggle the clicked filter
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
                    Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ||
                    Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ||
                    Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                    flag.isChecked = !flag.isChecked
                } else { // if no modifier key is held, only exclusively enable the clicked filter
                    allFlags.forEach { it.isChecked = (it === flag) }
                }
            }
            // sync the button to the flag
            button.isChecked = flag.isChecked
        }
    }
}

