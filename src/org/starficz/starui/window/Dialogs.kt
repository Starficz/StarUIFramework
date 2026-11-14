package org.starficz.starui.window

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.Alignment
import org.starficz.starui.Size
import org.starficz.starui.StarUIDsl
import org.starficz.starui.TextAlign
import org.starficz.starui.components.Box
import org.starficz.starui.components.Button
import org.starficz.starui.components.Holo
import org.starficz.starui.components.Row
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.align
import org.starficz.starui.modifier.margin
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.vanilla.BoxedUIHolo
import org.starficz.starui.vanilla.ButtonFont
import org.starficz.starui.vanilla.HoloBackground

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
            holo?.close(FOLD_SHUT_SECONDS, flicker = if (lastWasConfirm) static.onConfirm else static.onCancel)
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
 *     if (confirmDismiss.value) Dialog(onDismissRequest = { confirmDismiss.value = false }, 400f, 60f,
 *         confirmText = "Dismiss", cancelText = "Cancel", onConfirm = { dismiss(team); confirmDismiss.value = false }) {
 *         Text("Dismiss ${team.name}? Its officers return to the pool.")
 *     }
 * }
 * ```
 *
 * Works on every screen (the title screen, the campaign and over its dialogs and core tabs, a battle) and stacks: a
 * confirmation over a dialog over a panel. The window is vanilla's custom dialog's: the content, a Box of exactly
 * [width] x [height], 10 px in from the window's inner edge; the buttons (150 x 25, Orbitron 20) 10 px from its
 * bottom right, the cancel button in the corner. Its inner size is ([width] + 20) x ([height] + 55), or
 * ([height] + 20) with no buttons; the frame adds 7 px on every side.
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
 * @param width the width of the content
 * @param height the height of the content
 * @param confirmText the confirm button's text (it also takes the G key), or `null` for none: then Enter and Space
 *   do nothing
 * @param cancelText the cancel button's text, or `null` for none (Escape still cancels)
 * @param onConfirm called by the confirm button, Enter, Space and [StarUIDialog.confirm]
 * @param onCancel called by the cancel button, Escape and [StarUIDialog.cancel]
 * @param static which opens and closes show the burst of static; vanilla's by default
 * @param contentKey a key for the content: when it changes, the window's content (buttons included) is built again
 *   behind vanilla's static transition, for a dialog with pages
 * @param codexEntryId the codex entry F2 opens, or `null` for the codex as it was last left
 * @param background the window's dark background: vanilla's custom dialog keeps the holo's see-through default;
 *   [HoloBackground.Dialog] is the opaque one of vanilla's market pickers
 * @param pause whether the campaign or battle pauses while it is open
 * @param builder the content, in its Box; the parameter confirms or cancels from the content
 * @return the dialog
 * @throws IllegalStateException when called outside a builder (use [showDialog])
 */
fun UIPanelAPI.Dialog(
    onDismissRequest: () -> Unit,
    width: Float,
    height: Float,
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
    ) { layer -> dialogWindow(layer.dialog!!, width, height, confirmText, cancelText, contentKey, background, builder) }
    return dialog
}

/**
 * Opens a dialog from code outside the UI (a click handler, a campaign script, an ability, a hotkey): the window of
 * [Dialog], open until it is confirmed or cancelled. The confirm button, Enter, Space and [StarUIDialog.confirm] run
 * [onConfirm] and close it; the cancel button, Escape and [StarUIDialog.cancel] run [onCancel] and close it.
 *
 * ```kotlin
 * Button("Pets") { showDialog(1040f, 640f, confirmText = "Return") { PetsPanel() } }
 * ```
 *
 * In a builder use [Dialog] instead. The parameters are [Dialog]'s.
 *
 * @return the dialog
 * @throws IllegalStateException when called while a builder runs (use [Dialog]), or before the game has a screen
 */
fun showDialog(
    width: Float,
    height: Float,
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
        "`if (confirm.value) Dialog(onDismissRequest = { confirm.value = false }, 400f, 60f) { ... }`."
    )
    lateinit var dialog: StarUIDialog
    Layers.openLayer(modal = true, dim = StarUIDialog.DIM, pause = pause, dismissOnEscape = true,
        onDismiss = { dialog.cancel() }, onClose = {},
        prepare = { layer ->
            dialog = StarUIDialog(layer).also { layer.dialog = it; it.attach() }
            dialog.update({ onConfirm(); layer.close() }, { onCancel(); layer.close() }, static, codexEntryId,
                confirmText != null)
        }
    ) { layer -> dialogWindow(layer.dialog!!, width, height, confirmText, cancelText, contentKey, background, builder) }
    return dialog
}

/** The window of a dialog, in its layer's full-screen Box: vanilla's custom dialog, measure for measure. */
private fun CustomPanelAPI.dialogWindow(
    dialog: StarUIDialog, width: Float, height: Float, confirmText: String?, cancelText: String?, contentKey: Any?,
    background: HoloBackground, builder: CustomPanelAPI.(StarUIDialog) -> Unit
) {
    val margin = StarUIDialog.MARGIN
    val hasButtons = confirmText != null || cancelText != null
    val innerW = width + 2 * margin
    val innerH = height + 2 * margin + if (hasButtons) StarUIDialog.BUTTON_HEIGHT + margin else 0f
    val inset = StarUIDialog.HOLO_INSET
    val holo = Holo(Size.exact(innerW + 2 * inset, innerH + 2 * inset), Modifier.align(Alignment.Center),
        background = background, contentKey = contentKey, animateFirstOpen = false, contentInset = inset) {
        Box(Size.exact(width, height), Modifier.align(Alignment.TopLeft).margin(margin)) { builder(dialog) }
        if (hasButtons) Row(modifier = Modifier.align(Alignment.BottomRight).margin(margin), spacing = margin) {
            val size = Size.exact(StarUIDialog.BUTTON_WIDTH, StarUIDialog.BUTTON_HEIGHT)
            if (confirmText != null) Button(confirmText, size, font = ButtonFont.ORBITRON_20, textAlign = TextAlign.Center,
                cutStyle = CutStyle.TL_BR, shortcut = Keyboard.KEY_G) { dialog.confirm() }
            if (cancelText != null) Button(cancelText, size, font = ButtonFont.ORBITRON_20, textAlign = TextAlign.Center,
                cutStyle = CutStyle.TL_BR) { dialog.cancel() }
        }
    }
    dialog.holo = holo
    if (!dialog.opened) {
        dialog.opened = true
        holo.forceClose()
        holo.open(StarUIDialog.FOLD_OPEN_SECONDS, flicker = dialog.static.onOpen)
    }
}
