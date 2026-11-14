// Dialog and showDialog (Dialogs.kt): vanilla's custom dialog window on a modal layer. Like LayerTests, the layers
// open on the group's root (Layers.testHost) without fades. The look was compared with vanilla's showCustomDialog
// in a hot game (identical to the pixel); this checks the geometry numbers and the behaviour.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.children
import org.starficz.starui.window.*

private fun TestContext.withDialogsOnRoot(body: () -> Unit) {
    val oldScale = Layers.fadeScale
    Layers.testHost = root
    Layers.fadeScale = 0f
    try {
        body()
    } finally {
        Layers.testHost = null
        Layers.fadeScale = oldScale
    }
}

private fun TestContext.key(code: Int, ch: Char = '\u0000') = Input.sendList(root, Input.keyDown(code, ch))

fun registerDialogTests() {
    StarUITestSuite.group("dialog-window") {
        withDialogsOnRoot {
            var confirmed = 0
            var cancelled = 0
            val dialog = showDialog(400f, 120f, cancelText = "Cancel", onConfirm = { confirmed++ }, onCancel = { cancelled++ }) {
                Text("Dismiss the survey team?")
            }
            frame(30)
            val holo = dialog.holo!!
            near(holo.position.width, 434f, "the window is (w + 20) wide inside a 7 px frame")
            near(holo.position.height, 189f, "and (h + 55) high inside it")
            near(holo.cx, screenW / 2, "centred across the screen")
            near(holo.cy, screenH / 2, "and down it")
            val inner = holo.content
            val kids = inner.children
            val box = kids.first { it.position.width == 400f }
            near(box.relLeft(inner), 10f, "the content Box is 10 px in from the left")
            near(box.relTop(inner), 10f, "and from the top")
            near(box.position.height, 120f, "at exactly the height asked for")
            val buttons = (kids.last() as com.fs.starfarer.api.ui.UIPanelAPI).children.filterIsInstance<ButtonAPI>()
            expect(buttons.map { it.text } == listOf("Confirm [G]", "Cancel"), "Confirm (vanilla adds its key, [G]) then Cancel along the bottom (${buttons.map { it.text }})")
            for (b in buttons) {
                near(b.position.width, 150f, "${b.text} is 150 wide")
                near(b.position.height, 25f, "${b.text} is 25 high")
            }
            val cancel = buttons.last()
            near(inner.position.x + inner.position.width - (cancel.position.x + cancel.position.width), 10f, "Cancel sits 10 px from the right")
            near(cancel.position.y - inner.position.y, 10f, "and 10 px from the bottom")
            expect(dialog.layer.dim == 0.85f && dialog.layer.modal && dialog.layer.pause, "a modal, pausing layer dimmed to 0.85, as vanilla's")
            screenshot("window")

            key(Keyboard.KEY_RETURN)
            frame()
            expect(confirmed == 1 && !dialog.isOpen, "Enter confirms and closes it (confirmed $confirmed, open ${dialog.isOpen})")
            dialog.confirm()
            expect(confirmed == 1, "a closed dialog confirms no more")

            val second = showDialog(300f, 60f, cancelText = "Cancel", onConfirm = { confirmed++ }, onCancel = { cancelled++ }) { }
            frame()
            key(Keyboard.KEY_SPACE, ' ')
            expect(confirmed == 2 && !second.isOpen, "Space confirms")
            val third = showDialog(300f, 60f, cancelText = "Cancel", onConfirm = { confirmed++ }, onCancel = { cancelled++ }) { }
            frame()
            key(Keyboard.KEY_ESCAPE)
            expect(cancelled == 1 && !third.isOpen, "Escape cancels")

            val noConfirm = showDialog(300f, 60f, confirmText = null, onConfirm = { confirmed++ }, onCancel = { cancelled++ }) { }
            frame()
            near(noConfirm.holo!!.position.height, 60f + 20f + 14f, "with no buttons the window is (h + 20) high inside its frame")
            key(Keyboard.KEY_RETURN)
            expect(confirmed == 2 && noConfirm.isOpen, "with no confirm button Enter does nothing")
            key(Keyboard.KEY_ESCAPE)
            expect(cancelled == 2 && !noConfirm.isOpen, "and Escape still cancels")

            var fromContent: StarUIDialog? = null
            val selfConfirming = showDialog(300f, 60f, onConfirm = { confirmed++ }) { d ->
                fromContent = d
                Box(Size.exact(100f, 40f), Modifier.align(Alignment.Center).onClick { d.confirm() })
            }
            frame(30)
            expect(fromContent === selfConfirming, "the builder gets the dialog")
            val target = selfConfirming.holo!!
            Input.sendList(root, Input.down(target.cx, target.cy + 7f), Input.up(target.cx, target.cy + 7f))
            expect(confirmed == 3 && !selfConfirming.isOpen, "content confirms through it (confirmed $confirmed)")
            frame()

            // a text field with the keyboard takes Space before the dialog does
            val typed = UIState("")
            lateinit var field: TextFieldAPI
            val withField = showDialog(300f, 60f, onConfirm = { confirmed++ }) { field = TextField(typed, Size.exact(200f, 24f)) }
            frame(30)
            Input.sendList(root, Input.down(field.cx, field.cy), Input.up(field.cx, field.cy))
            frame()
            expect(field.hasFocus(), "a click focuses the field in the dialog")
            key(Keyboard.KEY_SPACE, ' ')
            frame()
            expect(confirmed == 3 && withField.isOpen, "Space goes to the focused field, not to confirm (confirmed $confirmed)")
            withField.cancel()
            frame()

            val inBuilder = thrown { root.Column { showDialog(10f, 10f) { } } }
            expect(inBuilder is IllegalStateException && inBuilder.message!!.contains("Dialog("), "showDialog in a builder throws and points to Dialog")
            val outside = thrown { root.Dialog(onDismissRequest = {}, 10f, 10f) { } }
            expect(outside is IllegalStateException && outside.message!!.contains("showDialog"), "Dialog outside a builder throws and points to showDialog")
            frame()
        }
    }

    StarUITestSuite.group("dialog-declarative") {
        withDialogsOnRoot {
            val show = UIState(false)
            val confirmInner = UIState(false)
            var deleted = 0
            var outer: StarUIDialog? = null
            var inner: StarUIDialog? = null
            root.Box(Size.exact(10f, 10f), Modifier.anchor(Anchor.inside.topLeft.ofParent())) {
                if (show.value) outer = Dialog(onDismissRequest = { show.value = false }, 500f, 300f, confirmText = "Return") {
                    Column {
                        Text("Plans")
                        if (confirmInner.value) inner = Dialog(onDismissRequest = { confirmInner.value = false }, 380f, 40f,
                            confirmText = "Delete", cancelText = "Cancel", onConfirm = { deleted++; confirmInner.value = false }) {
                            Text("Delete this plan?")
                        }
                    }
                }
            }
            show.value = true
            frame()
            expect(outer!!.isOpen, "the state opens the Dialog")
            confirmInner.value = true
            frame()
            expect(inner!!.isOpen && root.children.last() === inner!!.layer.panel, "a Dialog in its content opens above it")
            key(Keyboard.KEY_RETURN)
            frame()
            expect(deleted == 1 && !inner!!.isOpen && outer!!.isOpen, "Enter confirms only the top one (deleted $deleted)")
            confirmInner.value = true
            frame()
            key(Keyboard.KEY_ESCAPE)
            frame()
            expect(!confirmInner.value && !inner!!.isOpen && outer!!.isOpen, "Escape cancels only the top one")
            confirmInner.value = true
            frame()
            val second = inner!!
            show.value = false
            frame()
            expect(!outer!!.isOpen && !second.isOpen, "closing the outer Dialog closes the one in its content with it")
            frame()
            confirmInner.value = false
            show.value = true
            frame()
            key(Keyboard.KEY_ESCAPE)
            frame()
            expect(!show.value && !outer!!.isOpen, "Escape on a Dialog with only a confirm button calls onDismissRequest")
            frame()
        }
    }
}
