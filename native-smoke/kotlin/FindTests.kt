// findAll / find (Find.kt), StarUITesting (Testing.kt) and the find request (DebugDump.find): what outside code sees
// of StarUI's tree, and driving what it finds with real input.

import com.fs.starfarer.api.ui.ButtonAPI
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.runtime.DebugDump
import org.starficz.starui.window.*

fun registerFindTests() {
    StarUITestSuite.group("find-tree-query") {
        var clicks = 0
        val ids = listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta")
        root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 60f)), spacing = 4f) {
            Text("Fleet roster")
            Column(Size.exact(200f, 60f), overflow = Overflow.ScrollVertical) {
                for (id in ids) key(id) {
                    Row(Size.fillMaxWidth(height = 25f)) { Button("Pick $id", Size.exact(120f, 25f)) { clicks++ } }
                }
            }
        }
        frame()

        val self = root.findAll().first()
        expect(self.component === root && self.parent == null, "the search's start is the first result, with no parent")
        near(self.left, 0f, "positions are on the screen, from its left")
        near(self.top, 0f, "and its top")

        val title = root.find { it.text == "Fleet roster" }
        expect(title != null && title.kind == "Text", "a Text is found by its text, kind Text (${title?.kind})")
        near(title!!.left, 40f, "at its screen position: 40 from the left")
        near(title.top, 60f, "60 from the top, y downwards")
        expect(title.isShown && title.isStarUI, "shown, and StarUI's")
        expect(title.path.startsWith("CustomPanel > Column > Text"), "its path runs from the start (${title.path})")

        val rows = root.findAll { it.key != null }
        expect(rows.map { it.key } == ids, "the keyed rows, in order, by key (${rows.map { it.key }})")
        val first = rows.first().find { it.kind == "Button" }
        val last = rows.last().find { it.kind == "Button" }
        expect(first != null && first.isShown, "a button found inside a keyed row; the first row's is shown")
        expect(last != null && !last.isShown, "the last row's is scrolled out of view: not shown")
        val third = rows[2].find { it.kind == "Button" }!!
        expect(third.isShown, "the third row's is cut by the list's bottom edge but partly shown")
        near(third.visibleBottom, third.parent!!.parent!!.bottom, "its shown part ends at the list's edge", tolerance = 1.01f)

        // a real click at the shown centre: the same input path as the game's
        fun clickAt(x: Float, y: Float) {
            val vy = screenH - y
            Input.sendList(root, Input.move(x, vy))
            NativeSmokeSupport.advance(root, 2)
            Input.sendList(root, Input.down(x, vy))
            Input.sendList(root, Input.up(x, vy))
            NativeSmokeSupport.advance(root, 2)
        }
        clickAt(first!!.visibleCenterX, first.visibleCenterY)
        expect(clicks == 1, "a real click at a found button's shown centre presses it ($clicks)")

        val dump = DebugDump.find("t1\tButton\tcontains\tpick", root)
        expect(dump.startsWith("StarUI find t1: 6 matches (3 shown) for \"pick\" of kind Button"),
            "a find request counts matches and shown ones (${dump.lineSequence().first()})")
        expect(dump.contains("click=(") && dump.trimEnd().endsWith("StarUI find t1 end"), "and gives click points, between its start and end lines")
        val exact = DebugDump.find("t2\t\texact\tbeta", root)
        expect(exact.startsWith("StarUI find t2: 1 match (1 shown)"), "an exact request matches a key too (${exact.lineSequence().first()})")
        dumpLayout("tree")
    }

    StarUITestSuite.group("find-testing-switches") {
        StarUITesting.windowHost = root
        StarUITesting.instantAnimations = true
        try {
            expect(StarUITesting.windowHost === root && StarUITesting.instantAnimations, "the switches read back what was set")
            var confirmed = 0
            val dialog = showDialog(cancelText = "Cancel", onConfirm = { confirmed++ }) {
                Text("Recruit the officer?")
            }
            frame(3)
            val text = root.find { it.text == "Recruit the officer?" }
            expect(text != null && text.isShown, "a dialog's content is found and shown three frames after it opens: no fold to wait for")
            expect(text?.path?.contains("Holo") == true, "found under its holo, though held outside the tree (${text?.path})")
            val confirm = root.find { it.kind == "Button" && (it.component as ButtonAPI).text.startsWith("Confirm") }
            expect(confirm != null && confirm.isShown, "and its Confirm button")
            val c = confirm!!
            val vy = screenH - c.visibleCenterY
            Input.sendList(root, Input.move(c.visibleCenterX, vy))
            NativeSmokeSupport.advance(root, 2)
            Input.sendList(root, Input.down(c.visibleCenterX, vy))
            Input.sendList(root, Input.up(c.visibleCenterX, vy))
            frame(3)
            expect(confirmed == 1, "a real click on it confirms ($confirmed)")
            expect(!dialog.isOpen, "and the dialog is gone at once")
            expect(root.find { it.text == "Recruit the officer?" } == null, "its content is no longer found")
        } finally {
            StarUITesting.reset()
        }
        expect(StarUITesting.windowHost == null && !StarUITesting.instantAnimations, "reset puts both back")
    }
}
