// UIState bindings, ButtonGroup, dynamic rebuild / injection, and the layout exception guards.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.AreaCheckbox
import org.starficz.starui.compose.components.Box
import org.starficz.starui.compose.components.Checkbox
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.TextField
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.runtime.ButtonGroup
import org.starficz.starui.compose.runtime.DynamicInjection
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIManager
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.children
import java.awt.Color

/** Real click (move, down, up) at a component's centre, through the root like the engine does. */
private fun TestContext.click(c: UIComponentAPI) {
    Input.sendList(root, Input.move(c.cx, c.cy))
    Input.sendList(root, Input.down(c.cx, c.cy))
    Input.sendList(root, Input.up(c.cx, c.cy))
    NativeSmokeSupport.advance(root, 2)
}

/** A detached root for layouts expected to throw, so a broken tree never reaches the group's own root. */
private fun TestContext.scratchRoot(): CustomPanelAPI = Global.getSettings().createCustom(screenW, screenH, null).also {
    it.position.setSize(screenW, screenH); it.position.inTL(0f, 0f)
}

fun registerStateTests() {
    StarUITestSuite.group("state-two-way-binding") {
        val flag = UIState(false)
        val text = UIState("initial")
        var observed = mutableListOf<Boolean>()
        lateinit var box: ButtonAPI
        lateinit var field: TextFieldAPI
        root.CustomPanel(Size.exact(400f, 200f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40))) {
            box = Checkbox("Bound checkbox", bind = flag, modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(10f, 10f)))
            field = TextField(text, size = Size.exact(200f, 24f), modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(10f, 60f)))
            flag.onChange { observed += it }
        }
        frame()
        expect(!box.isChecked, "checkbox starts unchecked from the state")
        expect(field.text == "initial", "text field starts with the state's text (got '${field.text}')")

        flag.value = true
        expect(box.isChecked, "state -> checkbox: setting the state checks the box")
        click(box)
        expect(!box.isChecked, "a real click unchecks the box")
        expect(!flag.value, "checkbox -> state: the click wrote false back to the state")
        expect(observed == listOf(true, false), "onChange saw exactly [true, false] (got $observed)")
        flag.value = false
        expect(observed.size == 2, "setting an equal value does not notify (got $observed)")

        text.value = "from state"
        expect(field.text == "from state", "state -> text field (got '${field.text}')")
        field.text = "typed by user"
        NativeSmokeSupport.advance(root, 2)
        expect(text.value == "typed by user", "text field -> state on advance (got '${text.value}')")
        frame()
        screenshot("final")
    }

    StarUITestSuite.group("state-buttongroup-radio") {
        val group = ButtonGroup()
        val a = UIState(true); val b = UIState(false); val c = UIState(false)
        val buttons = mutableListOf<ButtonAPI>()
        root.Column(Size.exact(220f, 140f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)), spacing = 5f) {
            for ((name, s) in listOf("A" to a, "B" to b, "C" to c)) buttons += AreaCheckbox(name, size = Size.exact(200f, 30f), bind = s, buttonGroup = group)
        }
        frame()
        fun states() = "a=${a.value} b=${b.value} c=${c.value} checked=${buttons.map { it.isChecked }}"
        expect(buttons.map { it.isChecked } == listOf(true, false, false), "initial checked state follows the binds (${states()})")
        click(buttons[1])
        expect(!a.value && b.value && !c.value, "radio: clicking B selects only B (${states()})")
        expect(buttons.map { it.isChecked } == listOf(false, true, false), "radio: buttons reflect the selection (${states()})")
        click(buttons[1])
        expect(a.value && b.value && c.value, "clicking the only selected button selects all (${states()})")
        click(buttons[2])
        expect(!a.value && !b.value && c.value, "radio again: clicking C selects only C (${states()})")
        log("multi-select needs a held Shift/Ctrl (Keyboard.isKeyDown); not driven here")
        frame()
        screenshot("final")
    }
}

fun registerDynamicTests() {
    StarUITestSuite.group("dynamic-unmanaged-child-relayout") {
        // A child added outside any builder context is not registered with the container, but it still takes part
        // in the flow (measured by its current size) and the container still re-lays out for it.
        val col = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)), spacing = 5f) {
            Spacer(Size.exact(100f, 20f), Modifier.background(Color(200, 60, 60)))
            Spacer(Size.exact(100f, 20f), Modifier.background(Color(60, 200, 60)))
        }
        frame()
        near(col.h, 45f, "initial wrapped height")
        val plugin = col.plugin as StarUIPanelPlugin
        val added = Global.getSettings().createCustom(150f, 30f, null)
        col.addComponent(added)
        frame()
        near(col.h, 80f, "a plain addComponent re-wraps all three children")
        near(col.w, 150f, "and re-wraps the new widest child")
        near(added.relTop(col), 50f, "the new child flows after the old ones")
        expect(plugin.layoutPassCount == 2, "one pass for the first frame, one for the added child (${plugin.layoutPassCount})")
        screenshot("final")
    }

    StarUITestSuite.group("dynamic-injection") {
        val host = root.CustomPanel(Size.exact(300f, 100f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)))
        var injections = 0
        val injection = object : DynamicInjection {
            override val id = "starui-test-injection"
            override fun findTarget(): UIPanelAPI = host
            override fun UIPanelAPI.injectUI() {
                injections++
                CustomPanel(Size.exact(50f, 50f), Modifier.anchor(Anchor.inside.center.ofParent()).background(Color(200, 140, 60)))
            }
        }
        StarUIManager.registerDynamicInjection(injection)
        StarUIManager.advance()
        StarUIManager.advance()
        expect(injections == 1, "an injection runs once while its element is still in the target ($injections)")
        expect(host.children.size == 1, "exactly one injected child (got ${host.children.size})")
        host.removeComponent(host.children.first())
        StarUIManager.advance()
        expect(injections == 2, "removing the injected element makes the next advance re-inject ($injections)")
        frame()
        screenshot("final")
    }
}

fun registerGuardTests() {
    StarUITestSuite.group("guards-layout-exceptions") {
        // FillMax on the primary axis of a WrapContent stack has no size to divide.
        var r = scratchRoot()
        r.Column(Size.mixed(Axis.Exact(200f), Axis.Wrap)) { Spacer(Size.fillMaxHeight(width = 10f)) }
        var err = thrown { NativeSmokeSupport.render(r) }
        expect(err is IllegalStateException && err.message?.contains("WrapContent") == true,
            "FillMax child in a WrapContent Column throws IllegalStateException (got $err)")

        // FillMax on a truly unconstrained axis: a Fill-height Column whose parent has no height.
        val flat = Global.getSettings().createCustom(screenW, 0f, null).also { it.position.inTL(0f, 0f) }
        flat.Column(Size.fillMaxHeight(width = 200f)) { Spacer(Size.fillMaxHeight(width = 10f)) }
        err = thrown { NativeSmokeSupport.render(flat) }
        expect(err is IllegalStateException && err.message?.contains("has no bounded size") == true,
            "FillMax child on an unconstrained axis throws the no-bounded-size error (got $err)")

        // Anchors are meaningless inside a stack: rejected while building.
        r = scratchRoot()
        err = thrown { r.Column(Size.exact(100f, 100f)) { Spacer(Size.exact(10f, 10f), Modifier.anchor(Anchor.inside.center.ofParent())) } }
        expect(err is IllegalStateException && err.message?.contains("StackLayout") == true,
            "Modifier.anchor inside a Column throws while building (got $err)")

        // A WrapContent Box holding only FillMax children only warns, and collapses to zero.
        r = scratchRoot()
        val box = r.Box(Size.wrapContent()) { Spacer(Size.fillMaxSize()) }
        err = thrown { NativeSmokeSupport.render(r) }
        expect(err == null, "all-FillMax WrapContent Box does not throw (got $err)")
        near(box.w, 0f, "all-FillMax WrapContent Box collapses to width 0")
    }
}
