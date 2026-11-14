// Step 2 of reactive scopes: a builder that runs again updates its old native children in place instead of replacing
// them, and key(...) keeps list items attached to their own native objects.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import org.starficz.starui.Anchor
import org.starficz.starui.components.*
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.background
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.onClick
import org.starficz.starui.Overflow
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.Size
import org.starficz.starui.UIState
import org.starficz.starui.vanilla.ButtonFont
import org.starficz.starui.vanilla.children
import org.starficz.starui.vanilla.Font
import org.starficz.starui.components.scrollPanel
import java.awt.Color

private val REUSE_BG = Color(30, 30, 40)

private fun origin(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

private fun UIPanelAPI.texts(): List<String> = children.filterIsInstance<LabelAPI>().map { it.text }

/** A real click (move, down, up) and two advanced frames, the way the engine delivers one. */
private fun TestContext.clickOnce(c: UIComponentAPI) {
    Input.sendList(root, Input.move(c.cx, c.cy))
    Input.sendList(root, Input.down(c.cx, c.cy))
    Input.sendList(root, Input.up(c.cx, c.cy))
    NativeSmokeSupport.advance(root, 2)
}

fun registerReuseTests() {
    StarUITestSuite.group("reuse-in-place") {
        val tick = UIState(0)
        lateinit var label: LabelAPI
        lateinit var spacer: CustomPanelAPI
        lateinit var inner: CustomPanelAPI
        val col = root.Column(Size.wrapContent(), origin(40f, 40f).background(REUSE_BG), spacing = 5f) {
            label = Text("tick ${tick.value}")
            spacer = Spacer(Size.exact(60f + tick.value * 20f, 20f), Modifier.background(Color(200, 140, 60)))
            inner = Row(Size.wrapContent()) { Text("inner") }
        }
        frame()
        val first = Triple(label, spacer, inner)
        val nativeLabel = label

        tick.value = 2
        frame()
        expect(label === first.first && label === nativeLabel, "the Text call reuses the same native label")
        expect(label.text == "tick 2", "with its text updated in place (${label.text})")
        expect(spacer === first.second, "the Spacer is reused")
        near(spacer.w, 100f, "and resized to its new exact size")
        expect(inner === first.third, "the nested Row is reused")
        expect(col.children.size == 3, "no extra children (${col.children.size})")
        expect(col.children == listOf(nativeLabel, spacer, inner), "children stay in call order")
        near(col.w, 100f, "the Column re-wraps the resized child")
        screenshot("final")
    }

    StarUITestSuite.group("reuse-keeps-ancestor-order") {
        // Vanilla bringToTop also moves every ancestor to the top of its own parent. A rebuild must never reorder
        // anything outside the rebuilt container: a blinking caret in Refit Filters hoisted the whole weapon picker
        // above vanilla tooltips and the console overlay twice a second.
        val tick = UIState(0)
        val host = root.CustomPanel(Size.exact(400f, 200f), origin(40f, 40f).background(REUSE_BG))
        val reactive = host.Column(Size.wrapContent(), Modifier.background(Color(50, 50, 70))) {
            Text("a ${tick.value}")
            Text("b")
            if (tick.value % 2 == 1) Text("c")
        }
        val laterSibling = Global.getSettings().createCustom(10f, 10f, null)
        host.addComponent(laterSibling).inTL(300f, 0f)
        val overlay = Global.getSettings().createCustom(10f, 10f, null)
        root.addComponent(overlay).inTL(0f, 0f)
        frame()
        val hostOrder = host.children.toList()
        val rootOrder = root.children.toList()

        for (i in 1..3) { tick.value = i; frame() }
        expect(host.children == hostOrder, "the rebuilt container keeps its place among its siblings")
        expect(root.children == rootOrder, "and so does every ancestor (the overlay stays on top)")
        expect(reactive.texts() == listOf("a 3", "b", "c"), "the rebuilt children are in call order (${reactive.texts()})")
        tick.value = 4
        frame()
        expect(reactive.texts() == listOf("a 4", "b"), "and after removing one (${reactive.texts()})")
        screenshot("final")
    }

    StarUITestSuite.group("reuse-recreate-on-fixed-props") {
        val big = UIState(false)
        lateinit var button: ButtonAPI
        lateinit var colored: LabelAPI
        lateinit var plain: LabelAPI
        val col = root.Column(Size.wrapContent(), origin(40f, 40f).background(REUSE_BG), spacing = 5f) {
            button = Button("Font", font = if (big.value) ButtonFont.ORBITRON_20 else ButtonFont.ORBITRON_12_CONDENSED)
            colored = Text("Colour", color = if (big.value) Color(255, 80, 80) else Color(255, 255, 255))
            plain = Text("unchanged")
        }
        frame()
        val oldButton = button; val oldColored = colored; val oldPlain = plain

        big.value = true
        frame()
        expect(button !== oldButton, "a Button whose font changed is a new native button (fonts are fixed at creation)")
        expect(colored !== oldColored, "a Text whose colour changed is a new native label")
        expect(plain === oldPlain, "a Text whose fixed properties did not change is reused")
        expect(oldButton !in col.children && oldColored !in col.children, "the replaced objects are removed")
        expect(col.children == listOf(button, colored, plain), "and the order still follows the calls")
        expect(colored.color == Color(255, 80, 80), "the new label has the new colour (${colored.color})")
        screenshot("final")
    }

    StarUITestSuite.group("reuse-no-duplicate-handlers") {
        val tick = UIState(0)
        var buttonClicks = 0
        var spacerClicks = 0
        lateinit var button: ButtonAPI
        lateinit var spacer: CustomPanelAPI
        root.Column(Size.wrapContent(), origin(40f, 40f).background(REUSE_BG), spacing = 5f) {
            button = Button("Clicked ${tick.value}", size = Size.exact(140f, 24f)) { buttonClicks++ }
            spacer = Spacer(Size.exact(140f, 24f), Modifier.background(Color(60, 60, 200)).onClick { spacerClicks++ })
        }
        frame()
        val firstButton = button
        for (i in 1..3) { tick.value = i; frame() }
        expect(button === firstButton, "the button survived three rebuilds")
        expect(button.text == "Clicked 3", "with its label updated (${button.text})")

        clickOnce(button)
        expect(buttonClicks == 1, "one click calls the button's handler once, not once per build ($buttonClicks)")
        Input.sendList(root, Input.down(spacer.cx, spacer.cy))
        expect(spacerClicks == 1, "one click calls a reused component's onClick modifier once ($spacerClicks)")
        val handlers = (spacer.plugin as StarUIPanelPlugin).describeHandlers()
        expect(handlers == "onClick, onRenderBelow", "the reused Spacer holds one handler of each kind ($handlers)")
        frame()
        screenshot("final")
    }

    StarUITestSuite.group("reuse-keyed-rows") {
        val ids = UIState(listOf(1, 2, 3))
        val notes = (0..9).associateWith { UIState("note $it") }
        val rowsById = mutableMapOf<Int, CustomPanelAPI>()
        val fieldsById = mutableMapOf<Int, TextFieldAPI>()
        val col = root.Column(Size.wrapContent(), origin(40f, 40f).background(REUSE_BG), spacing = 5f) {
            for (id in ids.value) key(id) {
                rowsById[id] = Row(Size.wrapContent(), spacing = 5f) {
                    Text("row $id")
                    fieldsById[id] = TextField(notes.getValue(id), size = Size.exact(150f, 22f))
                }
            }
        }
        frame()
        val firstRows = rowsById.toMap()
        val firstFields = fieldsById.toMap()
        fieldsById.getValue(2).grabFocus()
        expect(fieldsById.getValue(2).hasFocus(), "row 2's field has keyboard focus")

        ids.value = listOf(3, 1, 2)
        frame()
        expect((1..3).all { rowsById[it] === firstRows[it] }, "reordered keyed rows keep their own containers")
        expect(col.children == listOf(firstRows[3], firstRows[1], firstRows[2]), "in the new order")
        near(firstRows.getValue(3).relTop(col), 0f, "and the layout follows the new order")
        expect(fieldsById.getValue(2) === firstFields[2] && fieldsById.getValue(2).hasFocus(), "the focused field moved with its row and kept focus")

        ids.value = listOf(0, 3, 1, 2)
        frame()
        expect((1..3).all { rowsById[it] === firstRows[it] }, "inserting a row in front keeps the existing rows")
        expect(rowsById[0] !in firstRows.values && col.children.first() === rowsById[0], "the new row is created at the front")
        expect(fieldsById.getValue(2).hasFocus(), "focus is still on row 2's field")
        expect(fieldsById.getValue(2).text == "note 2" && fieldsById.getValue(0).text == "note 0", "each field shows its own row's text")

        ids.value = listOf(0, 3, 2)
        frame()
        val removed = firstRows.getValue(1)
        expect(removed !in col.children, "removing an id removes its row")
        expect((removed.plugin as StarUIPanelPlugin).isDisposed, "and disposes it")
        expect(notes.getValue(1).observers.isEmpty(), "and its field's binding is gone (${notes.getValue(1).observers.size})")
        expect(fieldsById.getValue(2).hasFocus(), "the focused row is untouched")
        screenshot("final")

        // Without keys, calls are matched by position: the same native objects show whichever item is now there.
        val names = UIState(listOf("a", "b", "c"))
        val unkeyed = root.Column(Size.wrapContent(), origin(40f, 200f).background(REUSE_BG)) {
            for (n in names.value) Text("item $n")
        }
        frame()
        val firstLabel = unkeyed.children.first()
        names.value = listOf("c", "a", "b")
        frame()
        expect(unkeyed.children.first() === firstLabel && unkeyed.texts().first() == "item c",
            "unkeyed: the first label object stays first and now shows the item that moved there (${unkeyed.texts()})")
    }

    StarUITestSuite.group("reuse-scroll-position") {
        val header = UIState(0)
        val rows = UIState(20)
        lateinit var sp: ScrollPanelAPI
        root.Column(Size.wrapContent(), origin(40f, 40f).background(REUSE_BG), spacing = 5f) {
            Text("header ${header.value}")
            sp = Column(Size.exact(300f, 100f), overflow = Overflow.ScrollVertical) {
                for (i in 0 until rows.value) Spacer(Size.exact(280f, 30f),
                    Modifier.background(if (i % 2 == 0) Color(200, 60, 60) else Color(60, 60, 200)))
            }.scrollPanel!!
        }
        frame()
        val first = sp
        sp.scrollToY(200f)
        near(sp.yOffset, 200f, "scrolled to 200")

        header.value = 1
        frame()
        expect(sp === first, "rebuilding the enclosing Column reuses the scroll panel")
        near(sp.yOffset, 200f, "which keeps its scroll position")

        rows.value = 25
        frame()
        val list = sp.contentContainer.children[0] as CustomPanelAPI
        near(list.position.height, 25 * 30f, "new rows extend the content")
        near(sp.yOffset, 200f, "and the scroll position survives the content rebuild")
        expect(list.children.size == 25, "25 rows (${list.children.size})")
        frame()
        screenshot("final")
    }
}
