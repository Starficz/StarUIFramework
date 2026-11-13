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
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
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

    StarUITestSuite.group("holo-background-and-swap") {
        // over a red backdrop: the Dialog background is opaque, the TitleScreen one lets the red through
        lateinit var dialog: BoxedUIHolo
        lateinit var title: BoxedUIHolo
        root.CustomPanel(Size.exact(700f, 260f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(200, 30, 30))) {
            dialog = Holo(Size.exact(300f, 200f), Modifier.anchor(Anchor.inside.topLeft.ofParent(20f, 20f)), animateFirstOpen = false)
            title = Holo(Size.exact(300f, 200f), Modifier.anchor(Anchor.inside.topLeft.ofParent(360f, 20f)),
                animateFirstOpen = false, background = org.starficz.starui.core.HoloBackground.TitleScreen)
        }
        frame(5)
        val red = { c: Rgb -> c.r > 60 && c.r > c.g * 2 }
        fun redInside(h: BoxedUIHolo) = h.position.let { p -> fraction(p.x + 40f, p.y + 40f, p.width - 80f, p.height - 80f, red) }
        val dialogRed = redInside(dialog)
        val titleRed = redInside(title)
        expect(dialogRed < 0.05f, "HoloBackground.Dialog (the default) hides what is behind it (red fraction $dialogRed)")
        expect(titleRed > 0.3f, "HoloBackground.TitleScreen lets it show through (red fraction $titleRed)")
        screenshot("backgrounds")
        root.clearChildren()

        // contentKey: a change builds the content again and swaps it in through vanilla's static transition
        val page = UIState(0)
        val count = UIState(0)
        lateinit var holo: BoxedUIHolo
        lateinit var label: LabelAPI
        root.CustomPanel(Size.exact(400f, 300f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f))) {
            val p = page.value
            holo = Holo(Size.exact(300f, 200f), contentKey = p, animateFirstOpen = false) {
                label = Text("page $p count ${count.value}")
            }
        }
        frame(5)
        val first = holo.content
        expect(label.text == "page 0 count 0", "the first content shows (${label.text})")
        page.value = 1
        frame(1)
        val second = holo.content
        expect(second !== first, "a contentKey change makes new content")
        expect(StarUIPanelPlugin.getAttached(first)?.isDisposed != false, "and disposes the old content's StarUI state")
        frame(40)
        expect(label.text == "page 1 count 0", "the new content is built for the new key (${label.text})")
        expect(holo.holo.invoke("getCurr") === second, "and the holo shows it once the static has peaked")
        count.value = 3
        frame()
        expect(label.text == "page 1 count 3", "the new content is reactive too (${label.text})")
        page.value = 1
        frame()
        expect(holo.content === second, "the same key keeps the content")
        screenshot("swapped")
        root.clearChildren()
    }
}
