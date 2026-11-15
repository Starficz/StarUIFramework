package org.starficz.starui.window

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.Alignment
import org.starficz.starui.Axis
import org.starficz.starui.Size
import org.starficz.starui.StarUIDsl
import org.starficz.starui.TextAlign
import org.starficz.starui.requestLayout
import org.starficz.starui.toNodeSize
import org.starficz.starui.components.Box
import org.starficz.starui.components.Button
import org.starficz.starui.components.Holo
import org.starficz.starui.components.Row
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.align
import org.starficz.starui.modifier.margin
import org.starficz.starui.modifier.onLayout
import org.starficz.starui.layout.NodeSize
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.runtime.nodeFor
import org.starficz.starui.vanilla.BoxedUIHolo
import org.starficz.starui.vanilla.ButtonFont
import org.starficz.starui.vanilla.HoloBackground
import org.starficz.starui.vanilla.ReflectionUtils.invoke

/**
 * Which of a [Dialog]'s opens and closes show vanilla's burst of static over its window (the holo's noise layer; no
 * static shows when the player turned UI noise off in the game's settings).
 *
 * @property onOpen static as the window folds open
 * @property onConfirm static as it folds shut after a confirm
 * @property onCancel static as it folds shut after a cancel (the cancel button, Escape, or the dialog left out)
 */
data class DialogStatic(val onOpen: Boolean = true, val onConfirm: Boolean = true, val onCancel: Boolean = true) {
    companion object {
        /** What vanilla's custom dialog does: static as it opens, and as it closes either way. */
        val Vanilla = DialogStatic()
        /** Static on a cancel only: vanilla's dialogs with `setNoiseOnConfirmDismiss(false)`. */
        val CancelOnly = DialogStatic(onConfirm = false)
        /** No static at all. */
        val None = DialogStatic(onOpen = false, onConfirm = false, onCancel = false)
    }
}

/**
 * An open [Dialog] or [showDialog], handed to its builder: confirms or cancels it from the content (a list's double
 * click, a Save button of the content's own), as its buttons do.
 */
class StarUIDialog internal constructor(internal val layer: StarUILayer) {
    internal var onConfirm: () -> Unit = {}
    internal var onCancel: () -> Unit = {}
    internal var static: DialogStatic = DialogStatic.Vanilla
    internal var codexEntryId: String? = null
    internal var hasConfirm = true
    internal var holo: BoxedUIHolo? = null
    internal var opened = false
    /** The content's measured size on a wrapping axis, kept so a rebuild opens the window at it again. */
    internal var wrapWidth: Float? = null
    internal var wrapHeight: Float? = null
    private var lastWasConfirm = false

    /** Whether the dialog is showing: false from the moment it starts closing. */
    val isOpen: Boolean get() = layer.isOpen

    /** Confirms, as the confirm button, Enter and Space do: `onConfirm` runs. Does nothing once it is closing. */
    fun confirm() {
        if (!layer.isOpen) return
        lastWasConfirm = true
        onConfirm()
    }

    /** Cancels, as the cancel button and Escape do: `onCancel` runs. Does nothing once it is closing. */
    fun cancel() {
        if (!layer.isOpen) return
        lastWasConfirm = false
        onCancel()
    }

    internal fun update(
        onConfirm: () -> Unit, onCancel: () -> Unit, static: DialogStatic, codexEntryId: String?, hasConfirm: Boolean
    ) {
        this.onConfirm = onConfirm
        this.onCancel = onCancel
        this.static = static
        this.codexEntryId = codexEntryId
        this.hasConfirm = hasConfirm
    }

    /** Vanilla's custom dialog: fades in over 0.3 s and out over 0.2 s, the window folds in half of that. */
    internal fun attach() {
        layer.fadeIn = 0.3f
        layer.fadeOut = 0.2f
        layer.onClosing = {
            if (Layers.fadeScale <= 0f) holo?.forceClose()
            else holo?.close(FOLD_SHUT_SECONDS, flicker = if (lastWasConfirm) static.onConfirm else static.onCancel)
        }
        layer.onKey = { event -> key(event) }
        layer.onClickOutsideContent = { event ->
            val window = holo
            if (window != null && !contains(window, event)) window.flickerNoise(0f, 0.5f)
        }
    }

    private fun key(event: InputEventAPI) {
        if (event.isRepeat) return
        when (event.eventValue) {
            Keyboard.KEY_RETURN, Keyboard.KEY_SPACE -> if (hasConfirm) {
                event.consume()
                confirm()
            }
            Keyboard.KEY_F2 -> {
                event.consume()
                Global.getSettings().showCodex(codexEntryId)
            }
        }
    }

    private fun contains(window: BoxedUIHolo, event: InputEventAPI): Boolean {
        val p = window.position
        val x = event.x.toFloat()
        val y = event.y.toFloat()
        return x >= p.x && x <= p.x + p.width && y >= p.y && y <= p.y + p.height
    }

    internal companion object {
        const val DIM = 0.85f
        const val FOLD_OPEN_SECONDS = 0.15f
        const val FOLD_SHUT_SECONDS = 0.1f
        const val MARGIN = 10f
        const val BUTTON_WIDTH = 150f
        const val BUTTON_HEIGHT = 25f
        const val HOLO_INSET = 7f
        /** The least space between a dialog's window and the screen's edge: a larger size shrinks to keep it. */
        const val SCREEN_EDGE = 50f
    }
}

/**
 * Shows a dialog for as long as this builder calls it: vanilla's dialog window (the framed holo, folding open with a
 * burst of static) in the middle of the screen over a dim, with [confirmText] and [cancelText] buttons along its
 * bottom, on a modal [Layer]. Put the call under the state that says it is open, as Compose's `Dialog`:
 *
 * ```kotlin
 * val confirmDismiss = UIState(false)
 * Column {
 *     Button("Dismiss") { confirmDismiss.value = true }
 *     if (confirmDismiss.value) Dialog(onDismissRequest = { confirmDismiss.value = false },
 *         confirmText = "Dismiss", cancelText = "Cancel", onConfirm = { dismiss(team); confirmDismiss.value = false }) {
 *         Text("Dismiss ${team.name}? Its officers return to the pool.")
 *     }
 * }
 * ```
 *
 * Works on every screen (the title screen, the campaign and over its dialogs and core tabs, a battle) and stacks: a
 * confirmation over a dialog over a panel. The window is vanilla's custom dialog's: the content, a Box of [size], 10 px
 * in from the window's inner edge; the buttons (150 x 25, Orbitron 20) 10 px from its bottom right, the cancel button
 * in the corner. Its inner size is (width + 20) x (height + 55), or (height + 20) with no buttons; the frame adds 7 px
 * on every side, and the window is a pixel taller when its height would be even (its see-through background then
 * hides the frame's top inner line).
 *
 * [size] is the content's, per axis: wrap (the default) makes the window as large as the content, measured before it
 * first shows and again whenever the content changes; exact is that size; fill is as large as the screen allows.
 * The window never comes closer than 50 px to the screen's edge: a larger size shrinks to fit. On a wrapping axis the
 * content needs a size of its own there (a Text that fills the width has nothing to fill): for a paragraph, give
 * the width exactly and let the height wrap, `Size.mixed(Axis.Exact(400f), Axis.WrapContent)`.
 *
 * - The confirm button, Enter and Space call [onConfirm] (not while a text field in the content has the keyboard: it
 *   takes Space and letters first). The cancel button and Escape call [onCancel]. Both default to
 *   [onDismissRequest]: change the state that shows the dialog there.
 * - It closes (the window folds shut, the dim fades) when a rebuild leaves the call out, or the UI that asked for it
 *   goes away. Each run of the builder keeps the same window and runs its content again with the new values.
 * - F2 opens the codex at [codexEntryId] (vanilla's dialogs open it too). A click outside the window flickers it.
 * - It pauses the campaign or battle while open ([pause]).
 *
 * @param onDismissRequest called when the player closes it (the buttons and keys, unless [onConfirm] or [onCancel]
 *   are given): change the state that shows it
 * @param size the content's size: wrap (the default), exact or fill on each axis, never closer than 50 px to the
 *   screen's edge
 * @param confirmText the confirm button's text (it also takes the G key), or `null` for none: then Enter and Space
 *   do nothing
 * @param cancelText the cancel button's text, or `null` for none (Escape still cancels)
 * @param onConfirm called by the confirm button, Enter, Space and [StarUIDialog.confirm]
 * @param onCancel called by the cancel button, Escape and [StarUIDialog.cancel]
 * @param static which opens and closes show the burst of static; vanilla's by default
 * @param contentKey a key for the content: when it changes, the window's content (buttons included) is built again
 *   behind vanilla's static transition, for a dialog with pages
 * @param codexEntryId the codex entry F2 opens, or `null` for the codex as it was last left
 * @param background the window's dark background: [HoloBackground.Default] (the default) is vanilla's custom
 *   dialog's, see-through, with the frame's inner line showing; `HoloBackground(200, 225)` is vanilla's save dialog's,
 *   nearly opaque; [HoloBackground.Dialog] is vanilla's market dialogs', opaque (it hides the inner line)
 * @param pause whether the campaign or battle pauses while it is open
 * @param builder the content, in its Box; the parameter confirms or cancels from the content
 * @return the dialog
 * @throws IllegalStateException when called outside a builder (use [showDialog])
 */
fun UIPanelAPI.Dialog(
    onDismissRequest: () -> Unit,
    size: Size = Size.wrapContent(),
    confirmText: String? = "Confirm",
    cancelText: String? = null,
    onConfirm: () -> Unit = onDismissRequest,
    onCancel: () -> Unit = onDismissRequest,
    static: DialogStatic = DialogStatic.Vanilla,
    contentKey: Any? = null,
    codexEntryId: String? = null,
    background: HoloBackground = HoloBackground.Default,
    pause: Boolean = true,
    builder: (@StarUIDsl CustomPanelAPI).(dialog: StarUIDialog) -> Unit
): StarUIDialog {
    lateinit var dialog: StarUIDialog
    Layers.declareLayer("Dialog", onDismiss = { dialog.cancel() }, modal = true, dim = StarUIDialog.DIM, pause = pause,
        dismissOnEscape = true,
        prepare = { layer ->
            dialog = layer.dialog ?: StarUIDialog(layer).also { layer.dialog = it; it.attach() }
            dialog.update(onConfirm, onCancel, static, codexEntryId, confirmText != null)
        }
    ) { layer -> dialogWindow(layer.dialog!!, size, confirmText, cancelText, contentKey, background, builder) }
    return dialog
}

/**
 * Opens a dialog from code outside the UI (a click handler, a campaign script, an ability, a hotkey): the window of
 * [Dialog], open until it is confirmed or cancelled. The confirm button, Enter, Space and [StarUIDialog.confirm] run
 * [onConfirm] and close it; the cancel button, Escape and [StarUIDialog.cancel] run [onCancel] and close it.
 *
 * ```kotlin
 * Button("Pets") { showDialog(Size.exact(1040f, 640f), confirmText = "Return") { PetsPanel() } }
 * ```
 *
 * In a builder use [Dialog] instead. The parameters are [Dialog]'s.
 *
 * @return the dialog
 * @throws IllegalStateException when called while a builder runs (use [Dialog]), or before the game has a screen
 */
fun showDialog(
    size: Size = Size.wrapContent(),
    confirmText: String? = "Confirm",
    cancelText: String? = null,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    static: DialogStatic = DialogStatic.Vanilla,
    contentKey: Any? = null,
    codexEntryId: String? = null,
    background: HoloBackground = HoloBackground.Default,
    pause: Boolean = true,
    builder: (@StarUIDsl CustomPanelAPI).(dialog: StarUIDialog) -> Unit
): StarUIDialog {
    if (StarUIContext.currentScope != null) throw IllegalStateException(
        "StarUI: showDialog(...) was called while a builder runs, so it would open another dialog every time that " +
        "builder runs again. In a builder use Dialog(...), which is shown for as long as the builder calls it: " +
        "`if (confirm.value) Dialog(onDismissRequest = { confirm.value = false }) { ... }`."
    )
    lateinit var dialog: StarUIDialog
    Layers.openLayer(modal = true, dim = StarUIDialog.DIM, pause = pause, dismissOnEscape = true,
        onDismiss = { dialog.cancel() }, onClose = {},
        prepare = { layer ->
            dialog = StarUIDialog(layer).also { layer.dialog = it; it.attach() }
            dialog.update({ onConfirm(); layer.close() }, { onCancel(); layer.close() }, static, codexEntryId,
                confirmText != null)
        }
    ) { layer -> dialogWindow(layer.dialog!!, size, confirmText, cancelText, contentKey, background, builder) }
    return dialog
}

/**
 * A holo height made odd (one pixel more when even). The holo's see-through background alternates two strengths of
 * scanline, row by row from its bottom edge: at an even height the frame's top inner line falls on the darker row
 * and disappears, while at an odd one it shows as on the other edges (vanilla's own dialogs have odd heights).
 */
internal fun oddHolo(height: Float): Float {
    val h = kotlin.math.ceil(height)
    return if (h.toInt() % 2 == 0) h + 1f else h
}

/** The window of a dialog, in its layer's full-screen Box: vanilla's custom dialog, measure for measure. */
private fun CustomPanelAPI.dialogWindow(
    dialog: StarUIDialog, size: Size, confirmText: String?, cancelText: String?, contentKey: Any?,
    background: HoloBackground, builder: CustomPanelAPI.(StarUIDialog) -> Unit
) {
    val margin = StarUIDialog.MARGIN
    val hasButtons = confirmText != null || cancelText != null
    // what the window adds around the content, and the most room the screen leaves for it
    val inset = StarUIDialog.HOLO_INSET
    val chromeW = 2 * margin + 2 * inset
    val chromeH = 2 * margin + 2 * inset + if (hasButtons) StarUIDialog.BUTTON_HEIGHT + margin else 0f
    val screen = dialog.layer.host?.position
    val edge = 2 * StarUIDialog.SCREEN_EDGE
    val maxW = if (screen == null) Float.MAX_VALUE else maxOf(1f, screen.width - edge - chromeW)
    val maxH = if (screen == null) Float.MAX_VALUE else maxOf(1f, screen.height - edge - chromeH)
    // exact (shrunk to fit) and fill (all the room) axes are known now; a wrapping one is measured once the content exists
    val asked = size.toNodeSize()
    fun known(axis: Axis, max: Float): Float? = when (axis) {
        is Axis.Exact -> minOf(axis.pixels, max)
        is Axis.FillMax -> max
        else -> null
    }
    // the buttons along the bottom: the window is never narrower than they are
    val buttonCount = (if (confirmText != null) 1 else 0) + (if (cancelText != null) 1 else 0)
    val buttonsWidth = if (buttonCount == 0) 0f else buttonCount * StarUIDialog.BUTTON_WIDTH + (buttonCount - 1) * margin
    val knownW = known(asked.width, maxW)
    val knownH = known(asked.height, maxH)
    val width = knownW ?: dialog.wrapWidth ?: 1f
    val height = knownH ?: dialog.wrapHeight ?: 1f
    val contentSize = Size.mixed(if (knownW != null) Axis.Exact(knownW) else Axis.WrapContent,
                                 if (knownH != null) Axis.Exact(knownH) else Axis.WrapContent)

    lateinit var content: CustomPanelAPI
    lateinit var holo: BoxedUIHolo
    // a wrapping axis: the content's size in all the room the screen leaves, and the window sized around it
    fun fit() {
        if (knownW != null && knownH != null) return
        val measured = (content.plugin as StarUIPanelPlugin).measure(knownW ?: maxW, knownH ?: maxH)
        // whole pixels (a text measures 312.6 wide: a window of fractions draws its frame and background a pixel
        // apart), and on a wrapping width never narrower than the buttons along its bottom
        val w = knownW ?: minOf(maxW, kotlin.math.ceil(maxOf(1f, measured.first, buttonsWidth)))
        val h = knownH ?: minOf(maxH, kotlin.math.ceil(maxOf(1f, measured.second)))
        if (knownW == null) dialog.wrapWidth = w
        if (knownH == null) dialog.wrapHeight = h
        val holoSize = NodeSize(Axis.Exact(w + chromeW), Axis.Exact(oddHolo(h + chromeH)))
        val node = nodeFor(holo.vanillaComponent)
        if (node.size != holoSize) {
            node.size = holoSize
            // the holo's own size now, not at the next layout: it folds open to the size it has when it opens, and
            // a frame opened at the first, provisional size stayed that size around the larger content
            val holoW = w + chromeW
            val holoH = oddHolo(h + chromeH)
            val p = holo.vanillaComponent.position
            if (p.width != holoW || p.height != holoH) holo.vanillaComponent.invoke("setSize", holoW, holoH)
            holo.vanillaComponent.requestLayout()
        }
    }
    holo = Holo(Size.exact(width + chromeW, oddHolo(height + chromeH)), Modifier.align(Alignment.Center),
        background = background, contentKey = contentKey, animateFirstOpen = false, contentInset = inset) {
        // after every layout of the content (it changed): fit the window to it again
        content = Box(contentSize, Modifier.align(Alignment.TopLeft).margin(margin).onLayout { fit() }) { builder(dialog) }
        if (hasButtons) Row(modifier = Modifier.align(Alignment.BottomRight).margin(margin), spacing = margin) {
            val buttonSize = Size.exact(StarUIDialog.BUTTON_WIDTH, StarUIDialog.BUTTON_HEIGHT)
            if (confirmText != null) Button(confirmText, buttonSize, font = ButtonFont.ORBITRON_20, textAlign = TextAlign.Center,
                cutStyle = CutStyle.TL_BR, shortcut = Keyboard.KEY_G) { dialog.confirm() }
            if (cancelText != null) Button(cancelText, buttonSize, font = ButtonFont.ORBITRON_20, textAlign = TextAlign.Center,
                cutStyle = CutStyle.TL_BR) { dialog.cancel() }
        }
    }
    // the content exists now: fit before the first layout, so the window opens at its size
    fit()
    dialog.holo = holo
    if (!dialog.opened) {
        dialog.opened = true
        if (Layers.fadeScale <= 0f) {
            holo.forceOpen()
        } else {
            holo.forceClose()
            holo.open(StarUIDialog.FOLD_OPEN_SECONDS, flicker = dialog.static.onOpen)
        }
    }
}
