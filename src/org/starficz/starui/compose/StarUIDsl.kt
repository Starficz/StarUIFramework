package org.starficz.starui.compose

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.ui.ButtonAPI.UICheckboxSize
import org.lazywizard.lazylib.opengl.ColorUtils
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.starficz.starui.core.*
import org.starficz.starui.core.ReflectionUtils.invoke
import java.awt.Color

@DslMarker
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
annotation class StarUIDsl

fun UIPanelAPI.CustomPanel(
    size: PanelSize,
    modifier: Modifier = Modifier(),
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    val plugin = StarUIPanelPlugin()
    val (initW, initH) = when (size) {
        is ExactImpl -> Pair(size.width, size.height)
        is FlexImpl -> Pair(1f, 1f)
        is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
    }
    val panel = Global.getSettings().createCustom(initW, initH, plugin)
    plugin.customPanel = panel

    this.addComponent(panel)
    if (!StarUIContext.isTopLevel) { StarUIContext.currentPlugin.childSizing[panel] = size }

    modifier.operations.forEach { it(panel) }

    StarUIContext.withPlugin(plugin) { panel.builder() }
    return panel
}

fun CustomPanelAPI.Plugin(builder: (@StarUIDsl StarUIPanelPlugin).() -> Unit): CustomUIPanelPlugin {
    val plugin = this.plugin as? StarUIPanelPlugin ?: StarUIPanelPlugin().also {
        it.customPanel = this
        this.setPlugin(it)
    }

    plugin.builder()
    return plugin
}

fun UIPanelAPI.ScrollPanel(
    size: PanelSize,
    modifier: Modifier = Modifier(),
    builder: BoxedScrollPanel.() -> Unit = {}
): BoxedScrollPanel {
    val (initW, initH) = when (size) {
        is ExactImpl -> Pair(size.width, size.height)
        is FlexImpl -> Pair(1f, 1f)
        is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
    }
    val tempPanel = Global.getSettings().createCustom(initW, initH, null)
    val tempTMAPI = tempPanel.createUIElement(initW, initH, true)
    tempPanel.addUIElement(tempTMAPI)
    val scrollPanel = tempPanel.children[0] as ScrollPanelAPI
    scrollPanel.removeComponent(tempTMAPI)

    val boxedScrollPanel = BoxedScrollPanel(scrollPanel)
    boxedScrollPanel.setSize(initW, initH)
    boxedScrollPanel.setContentSize(initW, initH)
    this.addComponent(scrollPanel)

    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childSizing[scrollPanel] = size
    }
    modifier.operations.forEach { it(scrollPanel) }

    return boxedScrollPanel.apply(builder)
}

fun UIPanelAPI.VerticalStackLayout(
    size: StackSize = StackSize.wrap(),
    modifier: Modifier = Modifier(),
    grow: VGrow = VGrow.Downwards,
    align: XAlign = XAlign.Center,
    yPadding: Float = 0f,
    xPadding: Float = 0f,
    spacing: Float = 0f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    return this.StackLayout(
        strategy = VerticalStrategy(grow, align),
        size = size,
        modifier = modifier,
        primaryPadding = yPadding,
        secondaryPadding = xPadding,
        spacing = spacing,
        builder = builder
    )
}

fun UIPanelAPI.HorizontalStackLayout(
    size: StackSize = StackSize.wrap(),
    modifier: Modifier = Modifier(),
    grow: HGrow = HGrow.Rightwards,
    align: YAlign = YAlign.Center,
    xPadding: Float = 0f,
    yPadding: Float = 0f,
    spacing: Float = 0f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    return this.StackLayout(
        strategy = HorizontalStrategy(grow, align),
        size = size,
        modifier = modifier,
        primaryPadding = xPadding,
        secondaryPadding = yPadding,
        spacing = spacing,
        builder = builder
    )
}

fun CustomPanelAPI.TooltipMakerPanel(
    size: PanelSize,
    modifier: Modifier = Modifier(),
    withScroller: Boolean = false,
    builder: (@StarUIDsl TooltipMakerAPI).() -> Unit = {}
): TooltipMakerAPI {
    val (initW, initH) = when (size) {
        is ExactImpl -> Pair(size.width, size.height)
        is FlexImpl -> Pair(1f, 1f)
        is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
    }
    val tooltipMakerPanel = createUIElement(initW, initH, withScroller)
    addUIElement(tooltipMakerPanel)

    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childSizing[tooltipMakerPanel] = size
    }
    modifier.operations.forEach { it(tooltipMakerPanel) }

    return tooltipMakerPanel.apply(builder)
}

enum class TextAlign(val starsectorAlignment: Alignment) {
    Left(Alignment.LMID),
    Center(Alignment.MID),
    Right(Alignment.RMID)
}

fun UIPanelAPI.Text(
    text: String,
    size: LabelSize = LabelSize.wrap(),
    modifier: Modifier = Modifier(),
    font: Font? = null,
    color: Color? = null,
    textAlign: TextAlign = TextAlign.Left,
    highlightedText: Collection<Pair<String, Color>>? = null
): BoxedUILabel {

    val label = this.addPara(text, font, color, highlightedText)
    label.setAlignment(textAlign.starsectorAlignment)

    // 1. IMMEDIATE TICK-0 SIZING
    // Calculate its natural, single-line width first
    label.autoSize()

    val explicitMax = (size as? WrapLabelImpl)?.maxWidth ?: Float.MAX_VALUE

    // Only force wrap if the natural text exceeds the requested limit
    if (label.width > explicitMax && explicitMax > 0f) {
        label.autoSizeToWidth(explicitMax)
    }

    // 2. LAYOUT ENGINE HOOK
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childSizing[label] = size

        StarUIContext.currentPlugin.childMeasurables[label] = { maxW, maxH ->
            // Reset to natural width to test against constraints
            label.autoSize()

            // The ultimate constraint is whichever is tighter: the user's explicit cap, or the container's physical limit
            val wrapTarget = minOf(explicitMax, maxW)

            // If the text naturally exceeds the tightest constraint, stretch the box and wrap!
            if (label.width > wrapTarget && wrapTarget > 0f && wrapTarget != Float.MAX_VALUE) {
                label.autoSizeToWidth(wrapTarget)
            }

            Pair(label.width, label.height)
        }
    }

    modifier.operations.forEach { it(label) }

    return label
}


fun UIPanelAPI.TextField(
    bind: UIState<String>,
    size: ContentSize = ContentSize.exact(200f, 30f),
    modifier: Modifier = Modifier(),
    font: Font = Font.INSIGNIA_15,
    hint: String? = null
): TextFieldAPI {

    fun calcIntrinsic(): Pair<Float, Float> {
        val textToMeasure = bind.value.ifEmpty { hint ?: "" }
        val (tw, th) = measureTextSize(textToMeasure, font)
        val padX = (size as? WrapContentImpl)?.xPad ?: 4f
        val padY = (size as? WrapContentImpl)?.yPad ?: 4f
        return Pair(maxOf(tw + padX * 2f, 50f), maxOf(th + padY * 2f, 20f))
    }

    val (initW, initH) = when (size) {
        is ExactImpl -> Pair(size.width, size.height)
        is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
        else -> calcIntrinsic()
    }

    val textField = this.addTextField(initW, initH, font)
    if (hint != null) textField.invoke("setHint", hint)

    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childSizing[textField] = size
        StarUIContext.currentPlugin.childMeasurables[textField] = { _, _ ->
            when (size) {
                is ExactImpl -> Pair(size.width, size.height)
                is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
                else -> calcIntrinsic() // Fixes TextField cross-axis collapsing if Flexed!
            }
        }
    }

    modifier.operations.forEach { it(textField) }

    textField.text = bind.value
    bind.onChange { newValue ->
        if (textField.text != newValue) textField.text = newValue
    }
    StarUIContext.currentPlugin.advance {
        if (textField.text != bind.value) {
            bind.value = textField.text
        }
    }

    return textField
}

fun UIPanelAPI.Image(
    imageSpritePath: String,
    size: ImageSize = ImageSize.native(),
    modifier: Modifier = Modifier()
): BoxedUIImage {

    // Spawner requires initial dummy bounds
    val img = addImage(imageSpritePath, 10f, 10f)

    img.sizeToOriginalSpriteSize()
    val nativeW = img.width
    val nativeH = img.height

    // Centralized aspect ratio and bounding math
    fun calcBounds(maxW: Float, maxH: Float): Pair<Float, Float> {
        val rawAr = img.originalAspectRatio
        val ar = if (rawAr.isNaN() || rawAr == 0f) 1f else rawAr

        return when (size) {
            // Absolute Intrinsic
            is ExactImpl -> Pair(size.width, size.height)
            is ScaledWidthImpl -> Pair(size.width, size.width / ar)
            is ScaledHeightImpl -> Pair(size.height * ar, size.height)

            // Bounded Intrinsic
            is NativeImageImpl -> {
                val desiredW = nativeW * size.scale
                val desiredH = nativeH * size.scale

                var finalW = desiredW
                var finalH = desiredH

                // Shrink to fit X
                if (finalW > maxW && maxW != Float.MAX_VALUE && maxW > 0f) {
                    finalW = maxW
                    finalH = finalW / ar
                }
                // Shrink to fit Y (if it still overflows after X adjustment)
                if (finalH > maxH && maxH != Float.MAX_VALUE && maxH > 0f) {
                    finalH = maxH
                    finalW = finalH * ar
                }

                Pair(finalW, finalH)
            }

            // External Relational
            // (Returns dummy bounds during Measure; Flex is fully calculated in Pass 3: Allocation)
            is FlexImpl -> Pair(1f, 1f)
        }
    }

    // 1. IMMEDIATE TICK-0 SIZING
    // Prevents visual flickering before the 4-Pass Layout Engine resolves
    if (size !is FlexImpl) {
        val (initW, initH) = calcBounds(Float.MAX_VALUE, Float.MAX_VALUE)
        img.setSize(initW, initH)
    }

    // 2. LAYOUT ENGINE HOOK
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childSizing[img] = size

        StarUIContext.currentPlugin.childMeasurables[img] = { maxW, maxH ->
            val (w, h) = calcBounds(maxW, maxH)

            // We only set explicit sizes if the image is Intrinsic.
            // If it is Flex, the StackLayoutEngine sets the size bounds physically via `child.setSize()` in Pass 3.
            if (size !is FlexImpl) {
                img.setSize(w, h)
            }
            Pair(w, h)
        }
    }

    modifier.operations.forEach { it(img) }

    return img
}

/**
 * Renders a holographic Ship Preview.
 *
 * Note on Sizing: Starsector's engine automatically clamps the rendered ship to a maximum of 1.0x scale
 * If the provided constraints are larger than the capped size, the ship will simply render dead-center in the available space.
 */
fun UIPanelAPI.ShipDisplay(
    size: PanelSize = PanelSize.flex(1f),
    modifier: Modifier = Modifier(),
    fleetMember: FleetMemberAPI? = null,
    style: BoxedUIShipPreview.Style = BoxedUIShipPreview.Style.NORMAL,
    color: Color = Global.getSettings().getColor("textFriendColor")
): BoxedUIShipPreview {

    // Initial instantiation bounds
    val (initW, initH) = when (size) {
        is ExactImpl -> Pair(size.width, size.height)
        is FlexImpl -> Pair(1f, 1f)
        is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
    }

    val shipPreview = addShipPreview(initW, initH, fleetMember, style, color)

    // LAYOUT ENGINE HOOK
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childSizing[shipPreview] = size

        StarUIContext.currentPlugin.childMeasurables[shipPreview] = { maxW, maxH ->
            val w = if (size is ExactImpl) size.width else maxW
            val h = if (size is ExactImpl) size.height else maxH

            // Only explicitly size if it's Exact. Flex does this in Pass 3.
            if (size !is FlexImpl) {
                shipPreview.setSize(w, h)
            }
            Pair(w, h)
        }
    }

    modifier.operations.forEach { it(shipPreview) }

    return shipPreview
}

/**
 * The fundamental layout blank-space node.
 * Can optionally render a background color to act as a physical divider.
 */
fun UIPanelAPI.Spacer(
    size: PanelSize = PanelSize.flex(1f),
    modifier: Modifier = Modifier(),
    color: Color? = null
): CustomPanelAPI {

    val (initW, initH) = when (size) {
        is ExactImpl -> Pair(size.width, size.height)
        is FillCrossImpl -> Pair(size.primarySize, size.primarySize) // Dummy bounds, Engine sets it perfectly in Pass 3
        is FlexImpl -> Pair(1f, 1f)
    }

    val plugin = StarUIPanelPlugin()
    val panel = Global.getSettings().createCustom(initW, initH, plugin)
    plugin.customPanel = panel
    this.addComponent(panel)

    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childSizing[panel] = size

        StarUIContext.currentPlugin.childMeasurables[panel] = { _, _ ->
            val w = if (size is ExactImpl) size.width else 1f
            val h = if (size is ExactImpl) size.height else 1f
            Pair(w, h)
        }
    }

    modifier.operations.forEach { it(panel) }

    if (color != null) {
        plugin.renderBelow { alphaMult ->
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            ColorUtils.glColor(color, alphaMult, false)
            GL11.glRectf(plugin.left, plugin.bottom, plugin.right, plugin.top)

            GL11.glEnable(GL11.GL_TEXTURE_2D)
        }
    }

    return panel
}

// =========================================================================================
// SPACER PRESET ALIASES
// =========================================================================================

/** A semantic line that stretches completely across the layout axis. Automatically adapts to Horizontal or Vertical StackLayouts! */
fun UIPanelAPI.Divider(
    thickness: Float = 2f,
    color: Color = Color.DARK_GRAY,
    modifier: Modifier = Modifier()
) = Spacer(size = PanelSize.fillCross(thickness), modifier = modifier, color = color)

/** A flexible invisible gap that pushes elements apart */
fun UIPanelAPI.FlexSpacer(
    weight: Float = 1f,
    modifier: Modifier = Modifier()
) = Spacer(size = PanelSize.flex(weight), modifier = modifier)


fun UIPanelAPI.Button(
    text: String,
    size: ContentSize = ContentSize.wrap(),
    modifier: Modifier = Modifier(),
    baseColor: Color = Global.getSettings().basePlayerColor,
    bgColor: Color = Global.getSettings().darkPlayerColor,
    font: Font = Font.ORBITRON_12_CONDENSED,
    align: Alignment = Alignment.MID,
    cutStyle: CutStyle = CutStyle.TL_BR,
    shortcut: Int? = null,
    onClick: ((ButtonAPI) -> Unit)? = null
): ButtonAPI {

    // Calculates the true natural size of the button based on its text
    fun calcIntrinsic(): Pair<Float, Float> {
        val (tw, th) = measureTextSize(text, font)
        val padX = (size as? WrapContentImpl)?.xPad ?: 4f
        val padY = (size as? WrapContentImpl)?.yPad ?: 4f
        return Pair(tw + padX * 2f, th + padY * 2f)
    }

    val (initW, initH) = when (size) {
        is ExactImpl -> Pair(size.width, size.height)
        is FillCrossImpl -> Pair(size.primarySize, size.primarySize) // Dummy cross, Layout sets it in Pass 3
        else -> calcIntrinsic() // Both Wrap and Flex get natural bounds on Tick 0
    }

    val btn = this.addButton(text, null, baseColor, bgColor, align, cutStyle, initW, initH, font, shortcut)

    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childSizing[btn] = size

        StarUIContext.currentPlugin.childMeasurables[btn] = { _, _ ->
            when (size) {
                is ExactImpl -> Pair(size.width, size.height)
                is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
                else -> calcIntrinsic() // Crucial: Flex returns intrinsic so Cross-Axis wrap math works!
            }
        }
    }

    modifier.operations.forEach { it(btn) }
    if (onClick != null) btn.onClick { onClick.invoke(btn) }

    return btn
}

fun UIPanelAPI.AreaCheckbox(
    text: String,
    size: ContentSize = ContentSize.wrap(),
    modifier: Modifier = Modifier(),
    baseColor: Color = Global.getSettings().basePlayerColor,
    bgColor: Color = Global.getSettings().darkPlayerColor,
    brightColor: Color = Global.getSettings().brightPlayerColor,
    font: Font? = null,
    leftAlign: Boolean = false,
    bind: UIState<Boolean>? = null,
    buttonGroup: ButtonGroup? = null
): ButtonAPI {

    fun calcIntrinsic(): Pair<Float, Float> {
        val (tw, th) = measureTextSize(text, font)
        val padX = (size as? WrapContentImpl)?.xPad ?: 4f
        val padY = (size as? WrapContentImpl)?.yPad ?: 4f
        return Pair(tw + padX * 2f, th + padY * 2f)
    }

    val (initW, initH) = when (size) {
        is ExactImpl -> Pair(size.width, size.height)
        is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
        else -> calcIntrinsic()
    }

    val validGroup = (buttonGroup != null && bind != null)
    val button = this.addAreaCheckbox(
        text, null, baseColor, bgColor, brightColor, initW, initH, font, leftAlign,
        if (!validGroup) bind else null
    )

    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childSizing[button] = size
        StarUIContext.currentPlugin.childMeasurables[button] = { _, _ ->
            when (size) {
                is ExactImpl -> Pair(size.width, size.height)
                is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
                else -> calcIntrinsic()
            }
        }
    }

    modifier.operations.forEach { it(button) }

    if (validGroup) buttonGroup.addButtonToGroup(button, bind)
    bind?.onChange { newValue -> button.isChecked = newValue }

    return button
}

fun UIPanelAPI.Checkbox(
    text: String? = null,
    size: ContentSize = ContentSize.wrap(),
    modifier: Modifier = Modifier(),
    checkboxSize: UICheckboxSize? = UICheckboxSize.SMALL,
    font: Font = Font.INSIGNIA_15,
    color: Color = Global.getSettings().basePlayerColor,
    bind: UIState<Boolean>? = null,
    buttonGroup: ButtonGroup? = null
): ButtonAPI {

    fun calcIntrinsic(): Pair<Float, Float> {
        val (tw, th) = if (text != null) measureTextSize(text, font) else Pair(0f, 0f)
        val boxSize = if (checkboxSize == UICheckboxSize.LARGE) 24f else 16f
        val padX = (size as? WrapContentImpl)?.xPad ?: 8f
        val padY = (size as? WrapContentImpl)?.yPad ?: 4f
        return Pair(tw + boxSize + padX * 2f, maxOf(th, boxSize) + padY * 2f)
    }

    val (initW, initH) = when (size) {
        is ExactImpl -> Pair(size.width, size.height)
        is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
        else -> calcIntrinsic()
    }

    val validGroup = (buttonGroup != null && bind != null)
    val button = this.addCheckbox(initW, initH, text, null, font, color, checkboxSize, if (!validGroup) bind else null)

    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childSizing[button] = size
        StarUIContext.currentPlugin.childMeasurables[button] = { _, _ ->
            when (size) {
                is ExactImpl -> Pair(size.width, size.height)
                is FillCrossImpl -> Pair(size.primarySize, size.primarySize)
                else -> calcIntrinsic()
            }
        }
    }

    modifier.operations.forEach { it(button) }

    if (validGroup) buttonGroup.addButtonToGroup(button, bind!!)
    bind?.onChange { newValue -> button.isChecked = newValue }

    return button
}

class ButtonGroup {
    private val allFlags: MutableCollection<UIState<Boolean>> = mutableListOf()

    internal fun addButtonToGroup(button: ButtonAPI, bind: UIState<Boolean>){
        allFlags.add(bind)
        button.isChecked = bind.value == true
        button.onClick {
            if (allFlags.count { it.value } == 1 && bind.value) {
                // If the only active item is clicked, re-enable all items in the group.
                allFlags.forEach { it.value = true }
            } else {
                // if multiselect key (Shift or Ctrl) is held, toggle the clicked filter
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
                    Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ||
                    Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ||
                    Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                    bind.value = !bind.value
                } else { // if no modifier key is held, only exclusively enable the clicked filter
                    allFlags.forEach { it.value = (it === bind) }
                }
            }
            // sync the button to the flag
            button.isChecked = bind.value
        }
    }
}