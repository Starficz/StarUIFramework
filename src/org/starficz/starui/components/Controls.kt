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
 * Text fields allow the player to enter text into a UI: a vanilla text field.
 *
 * The [TextField] uses [bind] to keep track of its text: typing writes the state, and writing the state changes the
 * text. Its caret and focus are kept when its builder runs again.
 *
 * Example usage:
 *
 * ```kotlin
 * val search = mutableStateOf("")
 * TextField(search, placeholder = "Search")
 * ```
 *
 * @param bind the state that holds the text of the text field
 * @param size the size of the [TextField]. By default it fills the width and wraps one line of [font]
 * @param modifier the [Modifier] to be applied to this text field
 * @param contentPadding the spacing values to apply internally between the field's edges and its text.
 *   `Modifier.padding` adds to it
 * @param font the font of the text
 * @param placeholder the optional placeholder to be displayed when the text is empty
 * @return the vanilla text field
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
 * Buttons help the player initiate actions: a vanilla button.
 *
 * By default a [Button] is as large as its text plus [contentPadding]. Vanilla buttons can only use the fonts in
 * [ButtonFont].
 *
 * Example usage:
 *
 * ```kotlin
 * Button("Confirm", shortcut = Keyboard.KEY_RETURN) { confirm() }
 * ```
 *
 * For a button that stays pressed, see [AreaCheckbox].
 *
 * @param text the text displayed on the button
 * @param size the size of the [Button]. By default it wraps its text plus [contentPadding]
 * @param modifier the [Modifier] to be applied to this button
 * @param contentPadding the spacing values to apply internally between the button's edges and its text.
 *   `Modifier.padding` adds to it
 * @param color the colour of the text and the border. By default the player faction's UI colour
 * @param backgroundColor the colour of the button's background. By default the player faction's dark UI colour
 * @param font the font of the text
 * @param textAlign the alignment of the text within the button
 * @param cutStyle which corners of the button are cut off
 * @param shortcut a `Keyboard.KEY_*` code that also presses the button, or `null` for none
 * @param onClick called when this button is clicked
 * @return the vanilla button
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
 * Area checkboxes allow the player to turn an option on or off: a vanilla button that stays pressed while it is
 * on, the look of vanilla's filter bars.
 *
 * The [AreaCheckbox] is on while [bind] is `true`; clicking it flips the state, and writing the state changes the
 * button. In a [buttonGroup] (a [FilterGroup] or a [RadioGroup]) the group decides what a click selects, through
 * [bind], which it then needs.
 *
 * Example usage:
 *
 * ```kotlin
 * val showBeams = mutableStateOf(true)
 * AreaCheckbox("BEAM", showBeams)
 * ```
 *
 * @param text the text displayed on the button
 * @param bind the state that holds whether the button is on. Without one, the button flips by itself and nothing
 *   reads it
 * @param size the size of the [AreaCheckbox]. By default it wraps its text plus [contentPadding]
 * @param modifier the [Modifier] to be applied to this button
 * @param contentPadding the spacing values to apply internally between the button's edges and its text.
 *   `Modifier.padding` adds to it
 * @param baseColor the colour of the glow while the cursor is over the button. By default the player faction's UI
 *   colour
 * @param darkColor the colour of the border, and of the fill while the button is on. By default the player
 *   faction's dark UI colour
 * @param brightColor the colour of the text. By default the player faction's bright UI colour
 * @param font the font of the text. `null` is vanilla's area checkbox font
 * @param leftAlign whether the text is aligned to the left instead of centred
 * @param buttonGroup the group this button belongs to, or `null` for none
 * @return the vanilla button
 * @see FilterGroup
 * @see RadioGroup
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
 * Checkboxes allow the player to select one or more items from a set. Checkboxes can turn an option on or off: a
 * vanilla tick box, with an optional label.
 *
 * The [Checkbox] is ticked while [bind] is `true`; clicking it flips the state, and writing the state changes the
 * tick. In a [buttonGroup] (a [FilterGroup] or a [RadioGroup]) the group decides what a click selects, through [bind],
 * which it then needs.
 *
 * Example usage:
 *
 * ```kotlin
 * val autoResolve = mutableStateOf(false)
 * Checkbox("Auto-resolve", autoResolve)
 * ```
 *
 * @param text the label shown next to the box, or `null` for a box alone
 * @param bind the state that holds whether the box is ticked. Without one, the box flips by itself and nothing
 *   reads it
 * @param size the size of the [Checkbox]. By default it wraps the box and its label plus [contentPadding]
 * @param modifier the [Modifier] to be applied to this checkbox
 * @param contentPadding the spacing values to apply internally between the checkbox's edges and its content.
 *   `Modifier.padding` adds to it
 * @param checkboxSize the size of the box: vanilla's small (16 pixels) or large (24 pixels) one. `null` is the small
 *   one
 * @param font the font of the label
 * @param color the colour of the box and label. By default the player faction's UI colour
 * @param buttonGroup the group this checkbox belongs to, or `null` for none
 * @return the vanilla checkbox, a [ButtonAPI]
 * @see FilterGroup
 * @see RadioGroup
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
        "StarUI: $component(buttonGroup = ...) needs bind = a UIState<Boolean>: a FilterGroup or RadioGroup selects and clears its " +
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
 * Sliders allow the player to make selections from a range of values: a vanilla slider.
 *
 * Sliders reflect a range of values along a bar, from which the player may select a single value. Dragging writes
 * [bind], and writing [bind] moves the bar. Its size is exact or fill, since a slider has no content size.
 *
 * [step] lets the player choose only between values a fixed step apart. Note that it is a step size, where
 * Compose's `steps` is a count: a range from 0 to 10 with `step = 2f` allows 0, 2, 4, 6, 8 and 10.
 *
 * Example usage:
 *
 * ```kotlin
 * val crew = mutableStateOf(50f)
 * Slider(crew, Size.exact(250f, 25f), valueRange = 0f..200f, text = "Crew", step = 10f)
 * ```
 *
 * @param bind the state that holds the slider's value. It is shown coerced to [valueRange]
 * @param size the size of the [Slider]
 * @param modifier the [Modifier] to be applied to this slider
 * @param valueRange range of values that this slider can take
 * @param text the label vanilla draws on the bar, or `null` for none
 * @param barColor the colour of the filled part of the bar. `null` keeps vanilla's
 * @param widgetColor the colour of the handle. `null` keeps vanilla's
 * @param configure called on the vanilla [SliderAPI] each time the builder runs, for the setters this function
 *   does not cover: `configure = { setShowValueOnly(true) }`
 * @param step if set, the distance between two allowed values, counted from the start of [valueRange]
 * @return the vanilla slider
 * @see ProgressBar
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
 * Progress bars display how far a process has got, or a value the player cannot change: a vanilla bar.
 *
 * Pass a state's value as [progress] and the bar follows the state. By default the bar slides to a new value;
 * `animate = false` makes it jump. Its size is exact or fill, since a bar has no content size.
 *
 * Example usage:
 *
 * ```kotlin
 * ProgressBar(repairs.value, Size.fillMaxWidth(height = 12f), valueRange = 0f..1f)
 * ```
 *
 * @param progress the value shown, coerced to [valueRange]
 * @param size the size of the [ProgressBar]
 * @param modifier the [Modifier] to be applied to this bar
 * @param valueRange the values at the empty and the full end of the bar
 * @param text the label drawn on the bar, as it is, or `null` for none. (Vanilla's bar would add "value / max" after
 *   it; `configure = { setShowLabelOnly(false) }` brings that back.)
 * @param barColor the colour of the filled part of the bar. `null` keeps vanilla's
 * @param configure called on the vanilla [SliderAPI] each time the builder runs, for the setters this function
 *   does not cover
 * @param animate whether the bar slides to a new [progress] instead of jumping to it
 * @return the vanilla bar, a [SliderAPI]
 * @see Slider
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
    // set both ways: a reused bar may have had a text, or none, before
    bar.isShowNoText = text == null
    bar.setShowLabelOnly(text != null)
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
