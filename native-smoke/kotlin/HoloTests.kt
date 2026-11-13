// Holo: vanilla's own holo panel (its framed dialog window) around StarUI content, with its fold animation.

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.Button
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.Holo
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.margin
import org.starficz.starui.compose.runtime.StarUIManager
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.BoxedUIHolo
import java.awt.Color

fun registerHoloTests() {
    StarUITestSuite.group("holo-basics") {
        expect(BoxedUIHolo.HOLO_CLASS != null && BoxedUIHolo.CONSTRUCTOR != null,
            "vanilla's holo panel class and constructor were found at load (${BoxedUIHolo.HOLO_CLASS?.name})")
        val open = UIState(true)
        val count = UIState(0)
        val show = UIState(true)
        lateinit var holo: BoxedUIHolo
        lateinit var fill: CustomPanelAPI
        lateinit var label: LabelAPI
        lateinit var button: ButtonAPI
        var clicks = 0
        root.CustomPanel(Size.exact(600f, 400f), Modifier.anchor(Anchor.inside.topLeft.ofParent(60f, 60f))) {
            if (show.value) holo = Holo(Size.exact(300f, 200f), Modifier.anchor(Anchor.inside.topLeft.ofParent(20f, 20f)),
                open = open.value) {
                fill = Spacer(Size.fillMaxSize(), Modifier.background(Color(40, 40, 160)))
                Column(Size.wrapContent(), Modifier.margin(10f)) {
                    label = Text("count ${count.value}")
                    button = Button("Click") { clicks++ }
                }
            }
        }
        frame(40)   // the first open folds out over 0.25 s
        val h = holo.position
        near(h.width, 300f, "the holo takes its exact size")
        expect(holo.isOpen, "it folded open (fold ${holo.fold.brightness})")
        near(holo.contentInset, 7f, "its content is 7 in (contentInset)")
        near(fill.position.width, 300f - 14f, "the content's fill child spans the holo minus the inset on both sides")
        near(fill.position.height, 200f - 14f, "on both axes")
        near(fill.position.x - h.x, 7f, "and sits inside the inset")
        val lit = { c: Rgb -> c.r + c.g + c.b > 200 }
        val frameEdge = fraction(h.x, h.y + 40f, 4f, h.height - 80f, lit)
        expect(frameEdge > 0.2f, "vanilla draws the frame along the edge (lit fraction $frameEdge)")
        val blue = fraction(fill.position.x + 40f, fill.position.y + 20f, 60f, 20f) { it.b > it.r + 40 }
        expect(blue > 0.5f, "and the content inside it (blue fraction $blue)")
        screenshot("open")

        // the content is a reactive scope of its own
        count.value = 5
        frame()
        expect(label.text == "count 5", "the content rebuilds when its state changes (${label.text})")

        // input reaches the content through the holo once it is open
        val bx = button.position.x + button.position.width / 2f
        val by = button.position.y + button.position.height / 2f
        Input.sendList(root, Input.move(bx, by)); frame(1)
        Input.sendList(root, Input.down(bx, by)); Input.sendList(root, Input.up(bx, by)); frame(1)
        expect(clicks == 1, "a click reaches a button in the holo's content ($clicks)")

        // close: it folds shut and draws nothing
        open.value = false
        frame(2)
        expect(holo.fold.isFadingOut || holo.isClosed, "open = false folds it shut (fold ${holo.fold.brightness})")
        frame(40)
        expect(holo.isClosed, "and it ends closed")
        val after = fraction(h.x, h.y, h.width, h.height, lit)
        expect(after < 0.01f, "a closed holo draws nothing (lit fraction $after)")
        screenshot("closed")
        open.value = true
        frame(40)
        expect(holo.isOpen, "open = true folds it open again")

        // removing the call disposes the content's scope with the holo
        val scopesBefore = StarUIManager.liveScopeCount
        show.value = false
        frame()
        expect(StarUIManager.liveScopeCount < scopesBefore, "leaving the call out disposes the content's scopes ($scopesBefore -> ${StarUIManager.liveScopeCount})")
        root.clearChildren()
    }
}
