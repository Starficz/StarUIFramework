package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.runtime.ButtonGroup
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.core.Font
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.addAreaCheckbox
import org.starficz.starui.core.addButton
import org.starficz.starui.core.addCheckbox
import org.starficz.starui.core.addTextField
import org.starficz.starui.core.measureTextSize
import org.starficz.starui.core.onClick
import java.awt.Color

fun UIPanelAPI.TextField(
    bind: UIState<String>,
    size: Size = StarUITheme.current.textField.size,
    modifier: Modifier = Modifier,
    font: Font = StarUITheme.current.textField.font,
    hint: String? = null
): TextFieldAPI {
    checkDslTarget()

    fun calcIntrinsic(): Pair<Float, Float> {
        val textToMeasure = bind.peek().ifEmpty { hint ?: "" }
        val (tw, th) = measureTextSize(textToMeasure, font)
        return Pair(maxOf(tw, 50f), maxOf(th, 20f))
    }

    val (initW, initH) = getInitialBounds(size, calcIntrinsic())
    val textField = this.addTextField(initW, initH, font)
    if (hint != null) textField.invoke("setHint", hint)

    registerComponentSizing(textField, size, isContainer = false)
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMeasurables[textField] = { _, _ -> calcIntrinsic() }
    }

    modifier.operations.forEach { it(textField) }

    val ownerPlugin = if (!StarUIContext.isTopLevel) StarUIContext.currentPlugin else StarUIPanelPlugin.getOrCreate(textField)
    textField.text = bind.peek()
    bind.onChange(ownerPlugin) { newValue ->
        if (textField.text != newValue) textField.text = newValue
    }
    ownerPlugin.advance {
        if (textField.text != bind.peek()) {
            bind.value = textField.text
        }
    }

    return textField
}

fun UIPanelAPI.Button(
    text: String,
    size: Size = StarUITheme.current.button.size,
    modifier: Modifier = Modifier,
    baseColor: Color = StarUITheme.current.button.baseColor ?: Global.getSettings().basePlayerColor,
    bgColor: Color = StarUITheme.current.button.bgColor ?: Global.getSettings().darkPlayerColor,
    font: Font = StarUITheme.current.button.font,
    align: Alignment = StarUITheme.current.button.align,
    cutStyle: CutStyle = StarUITheme.current.button.cutStyle,
    shortcut: Int? = null,
    onClick: ((ButtonAPI) -> Unit)? = null
): ButtonAPI {
    checkDslTarget()

    fun calcIntrinsic(): Pair<Float, Float> {
        return measureTextSize(text, font)
    }

    val (initW, initH) = getInitialBounds(size, calcIntrinsic())
    val btn = this.addButton(text, null, baseColor, bgColor, align, cutStyle, initW, initH, font, shortcut)

    registerComponentSizing(btn, size, isContainer = false)
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMeasurables[btn] = { _, _ -> calcIntrinsic() }
    }

    modifier.operations.forEach { it(btn) }
    if (onClick != null) btn.onClick { onClick.invoke(btn) }

    return btn
}

fun UIPanelAPI.AreaCheckbox(
    text: String,
    size: Size = StarUITheme.current.areaCheckbox.size,
    modifier: Modifier = Modifier,
    baseColor: Color = StarUITheme.current.areaCheckbox.baseColor ?: Global.getSettings().basePlayerColor,
    bgColor: Color = StarUITheme.current.areaCheckbox.bgColor ?: Global.getSettings().darkPlayerColor,
    brightColor: Color = StarUITheme.current.areaCheckbox.brightColor ?: Global.getSettings().brightPlayerColor,
    font: Font? = StarUITheme.current.areaCheckbox.font,
    leftAlign: Boolean = StarUITheme.current.areaCheckbox.leftAlign,
    bind: UIState<Boolean>? = null,
    buttonGroup: ButtonGroup? = null
): ButtonAPI {
    checkDslTarget()

    fun calcIntrinsic(): Pair<Float, Float> {
        return measureTextSize(text, font)
    }

    val (initW, initH) = getInitialBounds(size, calcIntrinsic())
    val validGroup = (buttonGroup != null && bind != null)

    val button = this.addAreaCheckbox(
        text, null, baseColor, bgColor, brightColor, initW, initH, font, leftAlign
    )

    registerComponentSizing(button, size, isContainer = false)
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMeasurables[button] = { _, _ -> calcIntrinsic() }
    }

    modifier.operations.forEach { it(button) }

    if (validGroup) {
        buttonGroup.addButtonToGroup(button, bind)
    } else if (bind != null) {
        button.isChecked = bind.peek()
        button.onClick { bind.value = button.isChecked }
    }

    val ownerPlugin = if (!StarUIContext.isTopLevel) StarUIContext.currentPlugin else StarUIPanelPlugin.getOrCreate(button)
    bind?.onChange(ownerPlugin) { newValue -> button.isChecked = newValue }

    return button
}

fun UIPanelAPI.Checkbox(
    text: String? = null,
    size: Size = StarUITheme.current.checkbox.size,
    modifier: Modifier = Modifier,
    checkboxSize: ButtonAPI.UICheckboxSize? = StarUITheme.current.checkbox.checkboxSize,
    font: Font = StarUITheme.current.checkbox.font,
    color: Color = StarUITheme.current.checkbox.color ?: Global.getSettings().basePlayerColor,
    bind: UIState<Boolean>? = null,
    buttonGroup: ButtonGroup? = null
): ButtonAPI {
    checkDslTarget()

    fun calcIntrinsic(): Pair<Float, Float> {
        val (tw, th) = if (text != null) measureTextSize(text, font) else Pair(0f, 0f)
        val boxSize = if (checkboxSize == ButtonAPI.UICheckboxSize.LARGE) 24f else 16f
        return Pair(tw + boxSize, maxOf(th, boxSize))
    }

    val (initW, initH) = getInitialBounds(size, calcIntrinsic())
    val validGroup = (buttonGroup != null && bind != null)

    val button = this.addCheckbox(initW, initH, text, null, font, color, checkboxSize)

    registerComponentSizing(button, size, isContainer = false)
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMeasurables[button] = { _, _ -> calcIntrinsic() }
    }

    modifier.operations.forEach { it(button) }

    if (validGroup) {
        buttonGroup.addButtonToGroup(button, bind)
    } else if (bind != null) {
        button.isChecked = bind.peek()
        button.onClick { bind.value = button.isChecked }
    }

    val ownerPlugin = if (!StarUIContext.isTopLevel) StarUIContext.currentPlugin else StarUIPanelPlugin.getOrCreate(button)
    bind?.onChange(ownerPlugin) { newValue -> button.isChecked = newValue }

    return button
}
