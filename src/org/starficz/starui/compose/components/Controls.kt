package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.SliderAPI
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
import org.starficz.starui.core.addSlider
import org.starficz.starui.core.addTextField
import org.starficz.starui.core.asComponent
import org.starficz.starui.core.clearOnClick
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

    // A reused field keeps its caret, selection and keyboard focus; font and hint are fixed when it is made.
    val slot = childSlot("TextField", listOf(font, hint))
    val reused: TextFieldAPI? = slot.reused()
    val textField: TextFieldAPI
    if (reused != null) {
        textField = reused
        applyReuseBounds(textField, size, calcIntrinsic())
    } else {
        val (initW, initH) = getInitialBounds(size, calcIntrinsic())
        textField = this.addTextField(initW, initH, font)
        if (hint != null) textField.invoke("setHint", hint)
    }

    registerComponentSizing(textField, size, isContainer = false)
    slot.remember(textField, textField)
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMeasurables[textField] = { _, _ -> calcIntrinsic() }
    }

    modifier.operations.forEach { it(textField) }

    val ownerPlugin = if (!StarUIContext.isTopLevel) StarUIContext.currentPlugin else StarUIPanelPlugin.getOrCreate(textField)
    if (textField.text != bind.peek()) textField.text = bind.peek()
    bind.onChange(ownerPlugin) { newValue ->
        if (textField.text != newValue) textField.text = newValue
    }
    // Every text change (typing, backspace, paste, any setText call) reaches here through TextFieldValuePatch.
    StarUIPanelPlugin.getOrCreate(textField).onNativeValueChanged {
        if (textField.text != bind.peek()) bind.value = textField.text
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

    // Colours, font, alignment, cut style and shortcut are fixed when a native button is made; the label is not.
    val slot = childSlot("Button", listOf(baseColor, bgColor, font, align, cutStyle, shortcut))
    val reused: ButtonAPI? = slot.reused()
    val btn: ButtonAPI
    if (reused != null) {
        btn = reused
        if (btn.text != text) btn.text = text
        btn.clearOnClick()
        applyReuseBounds(btn, size, calcIntrinsic())
    } else {
        val (initW, initH) = getInitialBounds(size, calcIntrinsic())
        btn = this.addButton(text, null, baseColor, bgColor, align, cutStyle, initW, initH, font, shortcut)
    }

    registerComponentSizing(btn, size, isContainer = false)
    slot.remember(btn, btn)
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

    val validGroup = (buttonGroup != null && bind != null)

    val slot = childSlot("AreaCheckbox", listOf(text, baseColor, bgColor, brightColor, font, leftAlign))
    val reused: ButtonAPI? = slot.reused()
    val button: ButtonAPI
    if (reused != null) {
        button = reused
        button.clearOnClick()
        applyReuseBounds(button, size, calcIntrinsic())
    } else {
        val (initW, initH) = getInitialBounds(size, calcIntrinsic())
        button = this.addAreaCheckbox(
            text, null, baseColor, bgColor, brightColor, initW, initH, font, leftAlign
        )
    }

    registerComponentSizing(button, size, isContainer = false)
    slot.remember(button, button)
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
    if (bind != null) followCheckedFromCode(button, bind)

    return button
}

/** A setChecked call from code (vanilla, another mod, whoever holds the ButtonAPI) updates [bind]; see ButtonCheckedPatch. */
private fun followCheckedFromCode(button: ButtonAPI, bind: UIState<Boolean>) {
    StarUIPanelPlugin.getOrCreate(button).onNativeValueChanged {
        if (bind.peek() != button.isChecked) bind.value = button.isChecked
    }
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

    val validGroup = (buttonGroup != null && bind != null)

    val slot = childSlot("Checkbox", listOf(text, checkboxSize, font, color))
    val reused: ButtonAPI? = slot.reused()
    val button: ButtonAPI
    if (reused != null) {
        button = reused
        button.clearOnClick()
        applyReuseBounds(button, size, calcIntrinsic())
    } else {
        val (initW, initH) = getInitialBounds(size, calcIntrinsic())
        button = this.addCheckbox(initW, initH, text, null, font, color, checkboxSize)
    }

    registerComponentSizing(button, size, isContainer = false)
    slot.remember(button, button)
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
    if (bind != null) followCheckedFromCode(button, bind)

    return button
}

/**
 * A vanilla slider the player can drag, bound to [bind]. Dragging writes [bind]; writing [bind] moves the slider.
 * Values outside [rangeMin]..[rangeMax] are shown clamped (the state is left as it is). With [step], dragged values snap
 * to `rangeMin + k * step`.
 *
 * The vanilla slider holds one value. Its `min` is a floor the value can't go below and its `max` an overflow mark
 * (both left at the range ends here); set them, colours and the display mode in [configure], which runs on every build.
 */
fun UIPanelAPI.Slider(
    bind: UIState<Float>,
    rangeMin: Float = 0f,
    rangeMax: Float = 100f,
    size: Size = Size.exact(200f, 25f),
    modifier: Modifier = Modifier,
    text: String? = null,
    step: Float? = null,
    configure: (SliderAPI.() -> Unit)? = null
): SliderAPI {
    val slider = nativeSlider("Slider", text, rangeMin, rangeMax, size, modifier)
    slider.setUserAdjustable(true)
    slider.isShowAdjustableIndicator = true
    configure?.invoke(slider)

    // Showing the state (clamped to the range) goes through setProgress too; the hook must not write that back.
    var showingState = false
    fun show(value: Float) {
        val clamped = value.coerceIn(slider.rangeMin, slider.rangeMax)
        if (slider.progress == clamped) return
        showingState = true
        try { slider.progress = clamped; slider.forceSync() } finally { showingState = false }
    }
    show(bind.peek())

    val component = slider.asComponent
    val ownerPlugin = if (!StarUIContext.isTopLevel) StarUIContext.currentPlugin else StarUIPanelPlugin.getOrCreate(component)
    bind.onChange(ownerPlugin) { newValue -> show(newValue) }
    // Every progress change (the player's drag, any setProgress call) reaches here through SliderProgressPatch.
    StarUIPanelPlugin.getOrCreate(component).onNativeValueChanged {
        if (showingState) return@onNativeValueChanged
        var value = slider.progress
        if (step != null && step > 0f) {
            value = (slider.rangeMin + Math.round((value - slider.rangeMin) / step) * step).coerceIn(slider.rangeMin, slider.rangeMax)
        }
        if (bind.peek() != value) bind.value = value
        // Vanilla sets the unsnapped value again on every drag event, release included: snap the bar back each time.
        show(value)
    }
    return slider
}

/**
 * A vanilla bar showing [progress] between [rangeMin] and [rangeMax]; the player can't change it. Pass a state's value
 * and the bar follows it (the builder runs again and the bar is reused). With [animate] it slides to a new value the
 * way vanilla bars do; otherwise it jumps. Colours and the display mode go in [configure], which runs on every build.
 */
fun UIPanelAPI.ProgressBar(
    progress: Float,
    rangeMin: Float = 0f,
    rangeMax: Float = 100f,
    size: Size = Size.exact(200f, 12f),
    modifier: Modifier = Modifier,
    text: String? = null,
    animate: Boolean = true,
    configure: (SliderAPI.() -> Unit)? = null
): SliderAPI {
    val bar = nativeSlider("ProgressBar", text, rangeMin, rangeMax, size, modifier)
    bar.setUserAdjustable(false)
    bar.isShowAdjustableIndicator = false
    if (text == null) bar.isShowNoText = true
    configure?.invoke(bar)
    val shown = progress.coerceIn(rangeMin, rangeMax)
    if (bar.progress != shown) {
        bar.progress = shown
        if (!animate) bar.forceSync()
    }
    return bar
}

/** Makes or reuses the vanilla slider behind [Slider] and [ProgressBar]; every setting has a setter, so none is fixed. */
private fun UIPanelAPI.nativeSlider(
    kind: String, text: String?, rangeMin: Float, rangeMax: Float, size: Size, modifier: Modifier
): SliderAPI {
    checkDslTarget()
    val intrinsic = Pair(200f, 25f)
    val slot = childSlot(kind)
    val reused: SliderAPI? = slot.reused()
    val slider: SliderAPI
    if (reused != null) {
        slider = reused
        applyReuseBounds(slider.asComponent, size, intrinsic)
        slider.setText(text)
    } else {
        val (initW, initH) = getInitialBounds(size, intrinsic)
        slider = this.addSlider(text, rangeMin, rangeMax, initW, initH)
    }
    slider.rangeMin = rangeMin
    slider.rangeMax = rangeMax
    slider.max = rangeMax

    val component = slider.asComponent
    registerComponentSizing(component, size, isContainer = false)
    slot.remember(component, slider)
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMeasurables[component] = { _, _ -> intrinsic }
    }
    modifier.operations.forEach { it(component) }
    return slider
}
