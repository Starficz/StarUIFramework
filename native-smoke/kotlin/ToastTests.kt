// showToast (window/Toasts.kt): notices that go away by themselves, on the toasts' own layer, kept above other
// layers, moving like Windows' notifications (vanilla's slide in and out, the newest nearest the edge, the others
// gliding to their slots, at most three per place, the rest waiting, hover stopping the time). Like LayerTests, the
// layers open on the group's root (Layers.testHost) with fades off: slides and glides are instant here. Seen moving
// in a hot game on the campaign map and over a dialog.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.LabelAPI
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.children
import org.starficz.starui.vanilla.isSlidIn
import org.starficz.starui.vanilla.measureTextSize
import org.starficz.starui.window.*

private fun TestContext.withToastsOnRoot(body: () -> Unit) {
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

/** The distance of [toast]'s top edge from the top of the screen (the root). */
private fun TestContext.fromTop(toast: StarUIToast): Float = toast.panel!!.let { screenH - (it.position.y + it.position.height) }

fun registerToastTests() {
    StarUITestSuite.group("text-exact-width-wraps") {
        val long = "A much longer notice that goes on and on about the survey team, its officers and its charter"
        lateinit var label: LabelAPI
        val box = root.Box(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).padding(5f)) {
            label = Text(long, Size.mixed(Axis.Exact(200f), Axis.WrapContent))
        }
        frame()
        val oneLine = measureTextSize(long).second
        expect(label.position.height > oneLine * 1.5f, "an exact-width Text wraps (${label.position.height} high, one line is $oneLine)")
        near(box.position.height, label.position.height + 10f,
            "a wrapping container measures it at its own width: the wrapped height, not one line")
        near(box.position.width, 210f, "and its width is the exact one plus the padding, counted once")
    }

    StarUITestSuite.group("toasts") {
        withToastsOnRoot {
            // the default place: bottom right, 10 px from the corner off the campaign
            val first = showToast("Charter renewed", duration = 0f)
            frame(); frame()
            val host = first.host
            val layerPanel = host.layer.panel
            expect(first.isOpen && root.children.last() === layerPanel, "a toast shows on its own layer, on top")
            expect(!host.layer.modal && host.layer.dim == 0f && !host.layer.pause, "a non-modal layer: no dim, no pause")
            val a = first.panel!!
            expect(a.isSlidIn, "it slid in (vanilla's slide)")
            near(screenW - (a.position.x + a.position.width), 10f, "by default it sits at the bottom right, 10 px from the right")
            near(a.position.y, 10f, "and from the bottom (in the campaign, above the minimap)")

            // newest nearest the edge, the older one moved aside
            val second = showToast("Not enough supplies", duration = 0f)
            frame(); frame()
            val b = second.panel!!
            expect(second.host === host, "a second toast joins the same layer")
            near(b.position.y, 10f, "the newest takes the place nearest the edge")
            near(a.position.y, 10f + b.position.height + 5f, "and the older one moved up by its height and the spacing")

            // at most three per place; the rest wait
            val third = showToast("Survey complete", duration = 0f)
            val fourth = showToast("Waiting", duration = 0f)
            frame(); frame()
            expect(third.placed && !fourth.shown && !fourth.placed, "a fourth toast at one place waits for a slot")
            frame()
            val chip = host.chips.peek().singleOrNull()
            expect(chip != null && chip.count.peek() == 1 && chip.slot.placed, "a '+1 more' chip counts it")
            val chipPanel = chip!!.slot.panel!!
            near(chipPanel.position.y, 10f, "nearest the edge")
            near(third.panel!!.position.y, 10f + chipPanel.position.height + 5f, "with the newest toast past it")
            second.close()
            frame(); frame()
            expect(second !in host.entries.peek() && b.parent == null, "a closed toast slides out and goes")
            expect(fourth.shown && fourth.placed && fourth.panel!!.isSlidIn, "and the waiting one takes its slot")
            near(fourth.panel!!.position.y, 10f, "nearest the edge: it is the newest shown")
            frame()
            expect(host.chips.peek().isEmpty() && chipPanel.parent == null, "with nothing waiting, the chip slides out and goes")

            // other places, other stacks
            val top = showToast("Top", duration = 0f, alignment = Alignment.TopCenter)
            val topNewer = showToast("Top newer", duration = 0f, alignment = Alignment.TopCenter)
            frame(); frame()
            near(fromTop(topNewer), 40f, "at the top centre the newest sits 40 px down, under the campaign's 'Game paused' line")
            expect(fromTop(top) > fromTop(topNewer), "and the older one below it")
            near(top.panel!!.let { it.position.x + it.position.width / 2 }, screenW / 2, "centred")

            // a layer opened later stays under the toasts
            val dialog = showLayer { Text("a dialog") }
            frame()
            expect(root.children.last() === layerPanel, "a layer opened later stays under the toasts")
            dialog.close()
            frame()

            // the mouse: a click dismisses, the space between toasts lets clicks through
            val tp = top.panel!!.position
            Input.sendList(root, Input.down(tp.x + 5f, tp.y + 5f), Input.up(tp.x + 5f, tp.y + 5f))
            expect(!top.isOpen, "a click on a toast dismisses it")
            val elsewhere = Input.down(screenW / 2, screenH / 2)
            Input.sendList(root, elsewhere)
            expect(!elsewhere.isConsumed, "a click away from the toasts passes through their layer")
            frame(); frame()

            // time: it runs out, and stops while the mouse is over the toast
            val brief = showToast("Saved", duration = 0.05f, alignment = Alignment.TopLeft)
            frame()
            val bp = brief.panel!!.position
            Input.sendList(root, Input.move(bp.x + 5f, bp.y + 5f))
            frame(30)
            expect(brief.isOpen, "while the mouse is over a toast its time stops")
            Input.sendList(root, Input.move(screenW / 2, screenH / 2))
            frame(30)
            expect(!brief.isOpen && brief !in host.entries.peek(), "away from it, its time runs out and it goes")

            for (t in listOf(first, third, fourth, topNewer)) t.close()
            frame(); frame()
            expect(host.entries.peek().isEmpty() && !host.layer.isOpen, "the toasts' layer closes with its last toast")
            frame()
            expect(layerPanel.parent == null, "and is taken off")
            val again = showToast("Again", duration = 0f)
            frame()
            expect(again.isOpen && again.host !== host, "the next toast gets a new layer")
            again.close()
            frame(); frame()

            val inBuilder = thrown { root.Column { showToast("x") } }
            expect(inBuilder is IllegalStateException, "showToast in a builder throws")
            frame(); frame()
        }
    }

    StarUITestSuite.group("toast-chip-timing") {
        val oldScale = Layers.fadeScale
        Layers.testHost = root
        Layers.fadeScale = 1f
        try {
            val toasts = (1..4).map { showToast("Toast $it", duration = 0f) }
            frame(60)
            frame(60)
            val host = toasts.first().host
            val chip = host.chips.peek().singleOrNull()
            expect(chip != null && !chip.going && toasts[3].shown == false, "with slides on: three show, one waits, a chip counts it")
            toasts[0].close()
            frame(1)
            expect(toasts[0] in host.entries.peek() && toasts[0].panel!!.parent != null, "the closed toast is still sliding out")
            expect(chip!!.going, "and the chip already goes with it: it counts the slot the leaving toast frees")
            frame(60)
            frame(60)
            expect(host.chips.peek().isEmpty() && toasts[3].placed, "then the waiting toast takes the slot")
            toasts.forEach { it.close() }
            frame(60)
            frame(60)
        } finally {
            Layers.testHost = null
            Layers.fadeScale = oldScale
        }
    }
}
