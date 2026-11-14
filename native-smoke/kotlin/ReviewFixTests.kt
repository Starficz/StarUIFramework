// Regression tests for four review findings: a reused component keeps a tooltip it no longer asks for; Box ignores
// child margins when measuring and sizing; ButtonGroup keeps the states of buttons that are gone; an emptied
// Column/Row returns from layout before sizing itself.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.Anchor
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.Size
import org.starficz.starui.UIState
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import java.awt.Color

private val FIX_BG = Color(30, 40, 30)

private fun at(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

/** Non-null while vanilla holds a tooltip for the component (one slot per component; labels and panels alike). */
private fun UIComponentAPI.vanillaTooltip(): Any? = invoke("getTooltipLogic")

private fun TestContext.clickOn(c: UIComponentAPI) {
    Input.sendList(root, Input.move(c.cx, c.cy))
    Input.sendList(root, Input.down(c.cx, c.cy))
    Input.sendList(root, Input.up(c.cx, c.cy))
    NativeSmokeSupport.advance(root, 2)
}

fun registerReviewFixTests() {
    StarUITestSuite.group("fix-tooltip-dropped-on-reuse") {
        val warn = UIState(true)
        val tick = UIState(0)
        lateinit var label: LabelAPI
        lateinit var steady: LabelAPI
        root.Column(Size.wrapContent(), at(40f, 40f).background(FIX_BG)) {
            label = Text("status ${tick.value}", modifier = if (warn.value) Modifier.tooltip("careful") else Modifier)
            steady = Text("always ${tick.value}", modifier = Modifier.tooltip("always here"))
        }
        frame()
        val first = label
        val steadyTooltip = steady.vanillaTooltip()
        expect(label.vanillaTooltip() != null, "the conditional tooltip is attached while warn is true")
        expect(steadyTooltip != null, "the unconditional tooltip is attached")

        tick.value = 1
        frame()
        expect(label === first && label.vanillaTooltip() != null, "a rebuild that applies it again keeps a tooltip")

        warn.value = false
        frame()
        expect(label === first, "the label is reused in place")
        expect(label.vanillaTooltip() == null, "and its tooltip is gone once the builder stops asking for it")
        expect(steady.vanillaTooltip() != null, "the other label's tooltip is untouched")

        warn.value = true
        frame()
        expect(label.vanillaTooltip() != null, "asking again attaches a tooltip again")
    }

    StarUITestSuite.group("fix-box-child-margins") {
        lateinit var wrapped: CustomPanelAPI
        lateinit var filled: CustomPanelAPI
        val wrapBox = root.Box(Size.wrapContent(), at(40f, 40f).background(FIX_BG)) {
            wrapped = Spacer(Size.exact(50f, 30f), Modifier.margin(left = 10f, top = 5f, right = 20f, bottom = 15f).background(Color.RED))
        }
        val fillBox = root.Box(Size.exact(200f, 100f), at(40f, 200f).background(FIX_BG)) {
            filled = Spacer(Size.fillMaxSize(), Modifier.margin(left = 10f, top = 5f, right = 20f, bottom = 15f).background(Color.BLUE))
        }
        frame()
        near(wrapBox.w, 50f + 10f + 20f, "a wrap Box is as wide as its child plus the child's left and right margin")
        near(wrapBox.h, 30f + 5f + 15f, "and as tall as the child plus its top and bottom margin")
        near(wrapped.relLeft(wrapBox), 10f, "the child sits its left margin in from the left")
        near(wrapped.relTop(wrapBox), 5f, "and its top margin down from the top")
        near(filled.w, 200f - 30f, "a fill child is narrowed by its horizontal margin")
        near(filled.h, 100f - 20f, "and shortened by its vertical margin")
        near(filled.relLeft(fillBox), 10f, "and placed inside the margins (left)")
        near(filled.relTop(fillBox), 5f, "and placed inside the margins (top)")
        screenshot("final")
    }

    StarUITestSuite.group("fix-buttongroup-forgets-removed-buttons") {
        // A group that outlives the builder, with toggles that come and go: the classic "solo / select all" group.
        val group = FilterGroup()
        val a = UIState(true); val b = UIState(true); val c = UIState(true)
        val shown = UIState(listOf("a" to a, "b" to b, "c" to c))
        val buttons = linkedMapOf<String, ButtonAPI>()
        root.Column(Size.wrapContent(), at(40f, 40f).background(FIX_BG), spacing = 4f) {
            buttons.clear()
            for ((name, state) in shown.value) key(name) {
                buttons[name] = AreaCheckbox(name.uppercase(), state, Size.exact(120f, 24f), buttonGroup = group)
            }
        }
        frame()

        shown.value = listOf("a" to a, "b" to b)   // C's button is gone; its state lives on elsewhere
        frame()
        expect(buttons.keys.toList() == listOf("a", "b"), "two buttons left (${buttons.keys})")
        c.value = false

        clickOn(buttons["a"]!!)   // solo A
        expect(a.value && !b.value, "clicking A selects only A (a=${a.value} b=${b.value})")
        clickOn(buttons["a"]!!)   // A was the only one selected: select all (of the group's buttons)
        expect(a.value && b.value, "clicking the only selected button selects every button again (a=${a.value} b=${b.value})")
        expect(!c.value, "a state whose button was removed is no longer part of the group (c=${c.value})")
    }

    StarUITestSuite.group("fix-emptied-stack-resizes") {
        val items = UIState(listOf("one", "two", "three"))
        lateinit var col: CustomPanelAPI
        root.CustomPanel(Size.exact(300f, 300f), at(40f, 40f)) {
            col = Column(Size.wrapContent(), Modifier.background(FIX_BG)) {
                for (item in items.value) Text(item)
            }
        }
        frame()
        expect(col.h > 20f, "the Column is as tall as its three rows (${col.h})")
        items.value = emptyList()
        frame()
        near(col.h, 0f, "an emptied wrap Column shrinks to nothing")
        near(col.w, 0f, "in both directions")
    }
}
