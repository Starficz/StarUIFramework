package org.starficz.starui.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import org.starficz.starui.*
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.PaddingValues
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.*
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import java.awt.Color

// Argument order for every control: what it shows or edits (text, bind), then size, modifier, the optional settings,
// and last a click handler where there is one. `contentPadding` is the control's own space between its edge and its
// content (themeable); `Modifier.padding` adds to it.

/**
 * A text box for [bind]. Typing writes the state; writing the state changes the text. [placeholder] shows while it
 * is empty. Keeps its caret and focus across rebuilds.
 */
fun UIPanelAPI.TextField(
    bind: UIState<String>,
    size: Size = StarUITheme.current.textField.size,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = StarUITheme.current.textField.contentPadding,
    font: Font = StarUITheme.current.textField.font,
    placeholder: String? = null
): TextFieldAPI {
    checkDslTarget()

    fun calcIntrinsic(): Pair<Float, Float> {
        val textToMeasure = bind.peek().ifEmpty { placeholder ?: "" }
        val (tw, th) = VanillaFactory.measure(textToMeasure, font)
        return Pair(maxOf(tw, 50f), maxOf(th, 20f))
    }

    // A reused field keeps its caret, selection and keyboard focus; font and placeholder are fixed when it is made.
    val slot = childSlot("TextField", listOf(font, placeholder))
    val textField: TextFieldAPI = slot.reused() ?: run {
        // what TooltipMakerAPI.addTextField does: vanilla's text field with a 3 px inner pad
        val made = Global.getSettings().createTextField("", font.path)
        made.invoke("setPad", 3f)
        this.addComponent(made)
        if (placeholder != null) made.invoke("setHint", placeholder)
        made
    }
    finishLeaf(slot, textField, size, modifier, intrinsic = calcIntrinsic(), contentPadding = contentPadding,
        measure = { _, _ -> calcIntrinsic() })

    if (textField.text != bind.peek()) textField.text = bind.peek()
    val plugin = StarUIPanelPlugin.getOrCreate(textField)
    bind.bindTo(plugin) { newValue ->
        if (textField.text != newValue) textField.text = newValue
    }
    // Every text change (typing, backspace, paste, any setText call) reaches here through TextFieldValuePatch.
    plugin.onNativeValueChanged {
        if (textField.text != bind.peek()) bind.value = textField.text
    }

    return textField
}

/**
 * A button that runs [onClick] when clicked. As large as its text plus [contentPadding] by default.
 * [shortcut] is a `Keyboard.KEY_*` code that presses it too. [font] is a [ButtonFont], the fonts a vanilla button
 * can use. Returns the vanilla [ButtonAPI].
 */
fun UIPanelAPI.Button(
    text: String,
    size: Size = StarUITheme.current.button.size,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = StarUITheme.current.button.contentPadding,
    color: Color = StarUITheme.current.button.color ?: Global.getSettings().basePlayerColor,
    backgroundColor: Color = StarUITheme.current.button.backgroundColor ?: Global.getSettings().darkPlayerColor,
    font: ButtonFont = StarUITheme.current.button.font,
    textAlign: TextAlign = StarUITheme.current.button.textAlign,
    cutStyle: CutStyle = StarUITheme.current.button.cutStyle,
    shortcut: Int? = null,
    onClick: (() -> Unit)? = null
): ButtonAPI {
    checkDslTarget()

    var made: ButtonAPI? = null
    fun calcIntrinsic(): Pair<Float, Float> = VanillaFactory.measure(made?.text ?: text, font.font)

    // Colours, font, alignment, cut style and shortcut are fixed when a native button is made; the label is not.
    val slot = childSlot("Button", listOf(color, backgroundColor, font, textAlign, cutStyle, shortcut))
    val reused: ButtonAPI? = slot.reused()
    val btn: ButtonAPI
    if (reused != null) {
        btn = reused
        if (btn.text != text) btn.text = text
        btn.clearOnClick()
    } else {
        val (initW, initH) = getInitialBounds(size, calcIntrinsic(), contentPadding)
        btn = VanillaFactory.onScratch {
            when (font) {
                ButtonFont.ORBITRON_12_CONDENSED -> setButtonFontDefault()
                ButtonFont.ORBITRON_20 -> setButtonFontOrbitron20()
                ButtonFont.ORBITRON_20_BOLD -> setButtonFontOrbitron20Bold()
                ButtonFont.ORBITRON_24 -> setButtonFontOrbitron24()
                ButtonFont.ORBITRON_24_BOLD -> setButtonFontOrbitron24Bold()
                ButtonFont.VICTOR_10 -> setButtonFontVictor10()
                ButtonFont.VICTOR_14 -> setButtonFontVictor14()
            }
            addButton(text, null, color, backgroundColor, textAlign.toVanilla(), cutStyle, initW, initH, 0f)
        }
        shortcut?.let { btn.setShortcut(it, true) }
        this.addComponent(btn)
        btn.clearScratchOffsets()
    }
    made = btn

    finishLeaf(slot, btn, size, modifier, intrinsic = calcIntrinsic(), contentPadding = contentPadding,
        measure = { _, _ -> calcIntrinsic() })
    if (onClick != null) btn.onClick(onClick)

    return btn
}

/**
 * A button that stays pressed or unpressed: on while [bind] is true. Clicking flips the state. In a [buttonGroup] it
 * acts as a filter or radio button (see [ButtonGroup]), which needs [bind].
 */
fun UIPanelAPI.AreaCheckbox(
    text: String,
    bind: UIState<Boolean>? = null,
    size: Size = StarUITheme.current.areaCheckbox.size,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = StarUITheme.current.areaCheckbox.contentPadding,
    baseColor: Color = StarUITheme.current.areaCheckbox.baseColor ?: Global.getSettings().basePlayerColor,
    darkColor: Color = StarUITheme.current.areaCheckbox.darkColor ?: Global.getSettings().darkPlayerColor,
    brightColor: Color = StarUITheme.current.areaCheckbox.brightColor ?: Global.getSettings().brightPlayerColor,
    font: Font? = StarUITheme.current.areaCheckbox.font,
    leftAlign: Boolean = StarUITheme.current.areaCheckbox.leftAlign,
    buttonGroup: ButtonGroup? = null
): ButtonAPI {
    checkDslTarget()
    requireBindFor(buttonGroup, bind, "AreaCheckbox")

    fun calcIntrinsic(): Pair<Float, Float> = VanillaFactory.measure(text, font)

    val slot = childSlot("AreaCheckbox", listOf(text, baseColor, darkColor, brightColor, font, leftAlign))
    val reused: ButtonAPI? = slot.reused()
    val button: ButtonAPI
    if (reused != null) {
        button = reused
        button.clearOnClick()
    } else {
        val (initW, initH) = getInitialBounds(size, calcIntrinsic(), contentPadding)
        button = VanillaFactory.onScratch {
            if (font != null) setAreaCheckboxFont(font.path) else setAreaCheckboxFontDefault()
            addAreaCheckbox(text, null, baseColor, darkColor, brightColor, initW, initH, 0f, leftAlign)
        }
        this.addComponent(button)
        button.clearScratchOffsets()
    }

    finishLeaf(slot, button, size, modifier, intrinsic = calcIntrinsic(), contentPadding = contentPadding,
        measure = { _, _ -> calcIntrinsic() })
    bindChecked(button, bind, buttonGroup)
    return button
}

/**
 * A tick box with an optional label: ticked while [bind] is true. Clicking flips the state. In a [buttonGroup] it
 * acts as a filter or radio button (see [ButtonGroup]), which needs [bind].
 */
fun UIPanelAPI.Checkbox(
    text: String? = null,
    bind: UIState<Boolean>? = null,
    size: Size = StarUITheme.current.checkbox.size,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = StarUITheme.current.checkbox.contentPadding,
    checkboxSize: ButtonAPI.UICheckboxSize? = StarUITheme.current.checkbox.checkboxSize,
    font: Font = StarUITheme.current.checkbox.font,
    color: Color = StarUITheme.current.checkbox.color ?: Global.getSettings().basePlayerColor,
    buttonGroup: ButtonGroup? = null
): ButtonAPI {
    checkDslTarget()
    requireBindFor(buttonGroup, bind, "Checkbox")

    fun calcIntrinsic(): Pair<Float, Float> {
        val (tw, th) = if (text != null) VanillaFactory.measure(text, font) else Pair(0f, 0f)
        val boxSize = if (checkboxSize == ButtonAPI.UICheckboxSize.LARGE) 24f else 16f
        return Pair(tw + boxSize, maxOf(th, boxSize))
    }

    val slot = childSlot("Checkbox", listOf(text, checkboxSize, font, color))
    val reused: ButtonAPI? = slot.reused()
    val button: ButtonAPI
    if (reused != null) {
        button = reused
        button.clearOnClick()
    } else {
        // what TooltipMakerAPI.addCheckbox makes, from SettingsAPI's own factory
        button = Global.getSettings().createCheckbox(text, font.path, color, checkboxSize)
        this.addComponent(button)
    }

    finishLeaf(slot, button, size, modifier, intrinsic = calcIntrinsic(), contentPadding = contentPadding,
        measure = { _, _ -> calcIntrinsic() })
    bindChecked(button, bind, buttonGroup)
    return button
}

/** A group works on the states of its buttons, so a button in a group needs one. */
private fun requireBindFor(buttonGroup: ButtonGroup?, bind: UIState<Boolean>?, component: String) {
    require(buttonGroup == null || bind != null) {
        "StarUI: $component(buttonGroup = ...) needs bind = a UIState<Boolean>: a ButtonGroup selects and clears its " +
        "buttons through their states, so a button without one cannot take part."
    }
}

/** Ties a checkbox to its state: clicks (through the group when there is one), state changes, and setChecked calls. */
private fun bindChecked(button: ButtonAPI, bind: UIState<Boolean>?, buttonGroup: ButtonGroup?) {
    if (bind == null) return
    if (buttonGroup != null) {
        buttonGroup.addButtonToGroup(button, bind)
    } else {
        button.isChecked = bind.peek()
        button.onClick { bind.value = button.isChecked }
    }
    val plugin = StarUIPanelPlugin.getOrCreate(button)
    bind.bindTo(plugin) { newValue -> button.isChecked = newValue }
    // A setChecked call from code (vanilla, another mod, whoever holds the ButtonAPI): see ButtonCheckedPatch.
    plugin.onNativeValueChanged {
        if (bind.peek() != button.isChecked) bind.value = button.isChecked
    }
}

/**
 * A bar the player drags to set [bind] within [valueRange]. Dragging writes the state; writing the state moves the
 * bar. [step] snaps dragged values to multiples of it from the range start (a step size, not Compose's step count).
 * Its size is exact or fill.
 *
 * [configure] runs any other [SliderAPI] setter on it each build: `configure = { setShowValueOnly(true) }`.
 */
fun UIPanelAPI.Slider(
    bind: UIState<Float>,
    size: BoundedSize = StarUITheme.current.slider.size,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    text: String? = null,
    barColor: Color? = StarUITheme.current.slider.barColor,
    widgetColor: Color? = StarUITheme.current.slider.widgetColor,
    configure: (SliderAPI.() -> Unit)? = null,
    step: Float? = null
): BoxedUISlider {
    val slider = nativeSlider("Slider", text, valueRange, size, modifier)
    slider.setUserAdjustable(true)
    slider.isShowAdjustableIndicator = true
    barColor?.let { slider.barColor = it }
    widgetColor?.let { slider.widgetColor = it }
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

    val plugin = StarUIPanelPlugin.getOrCreate(slider.asComponent)
    bind.bindTo(plugin) { newValue -> show(newValue) }
    // Every progress change (the player's drag, any setProgress call) reaches here through SliderProgressPatch.
    plugin.onNativeValueChanged {
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
 * A bar showing [progress] within [valueRange], which the player can't change. Pass `state.value` and it follows the
 * state. [animate] slides it to a new value instead of jumping. Its size is exact or fill.
 *
 * [configure] runs any other [SliderAPI] setter on it each build.
 */
fun UIPanelAPI.ProgressBar(
    progress: Float,
    size: BoundedSize = StarUITheme.current.progressBar.size,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    text: String? = null,
    barColor: Color? = StarUITheme.current.progressBar.barColor,
    configure: (SliderAPI.() -> Unit)? = null,
    animate: Boolean = true
): BoxedUISlider {
    val bar = nativeSlider("ProgressBar", text, valueRange, size, modifier)
    bar.setUserAdjustable(false)
    bar.isShowAdjustableIndicator = false
    if (text == null) bar.isShowNoText = true
    barColor?.let { bar.barColor = it }
    configure?.invoke(bar)
    val shown = progress.coerceIn(valueRange.start, valueRange.endInclusive)
    if (bar.progress != shown) {
        bar.progress = shown
        if (!animate) bar.forceSync()
    }
    return bar
}

/** Makes or reuses the vanilla slider behind [Slider] and [ProgressBar]; every setting has a setter, so none is fixed. */
private fun UIPanelAPI.nativeSlider(
    kind: String, text: String?, valueRange: ClosedFloatingPointRange<Float>, size: BoundedSize, modifier: Modifier
): BoxedUISlider {
    checkDslTarget()
    val slot = childSlot(kind)
    val reused: BoxedUISlider? = slot.reused()
    val slider: BoxedUISlider
    if (reused != null) {
        slider = reused
        slider.setText(text)
    } else {
        slider = BoxedUISlider(Global.getSettings().createSlider(text, valueRange.start, valueRange.endInclusive))
        this.addComponent(slider.asComponent)
    }
    slider.rangeMin = valueRange.start
    slider.rangeMax = valueRange.endInclusive
    slider.max = valueRange.endInclusive

    finishLeaf(slot, slider.asComponent, size, modifier, handle = slider, measure = { _, _ -> Pair(1f, 1f) })
    return slider
}

/** The tooltip placed it with alignment offsets (5 px in for its first element); its real panel places it afresh. */
private fun ButtonAPI.clearScratchOffsets() {
    xAlignOffset = 0f
    yAlignOffset = 0f
}
