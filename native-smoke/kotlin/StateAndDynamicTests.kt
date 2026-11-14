// UIState bindings, ButtonGroup, dynamic rebuild / injection, and the layout exception guards.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.background
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.onAdvance
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.children
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

    StarUITestSuite.group("injection") {
        /** A plain vanilla panel (no StarUI plugin) in the group's root: what inject is for. */
        fun vanillaPanel(w: Float, h: Float, left: Float, top: Float, into: UIPanelAPI = root): CustomPanelAPI =
            Global.getSettings().createCustom(w, h, null).also { into.addComponent(it).inTL(left, top) }

        // ---- one container per key, placement ----
        val host = vanillaPanel(300f, 100f, 40f, 40f)
        vanillaPanel(60f, 30f, 0f, 0f, into = host)
        var builds = 0
        val orange: StarUIBuilder = { builds++; Spacer(Size.exact(50f, 50f), Modifier.background(Color(200, 140, 60))) }
        val a = host.inject("test:a", builder = orange)
        val again = host.inject("test:a", builder = orange)
        expect(again === a, "a second inject with the same key returns the live container")
        expect(builds == 1, "and does not run the builder again ($builds)")
        expect(host.children.size == 2, "one vanilla child and one injection (got ${host.children.size})")
        expect(host.children.last() === a, "Top: added after the panel's own children")
        expect(host.injection("test:a") === a, "injection(key) finds it")
        val b = host.inject("test:b", placement = InjectPlacement.Bottom) { Spacer(Size.exact(20f, 20f)) }
        expect(host.children.first() === b, "Bottom: under the panel's own children")
        expect(host.children.size == 3, "a different key adds a second container (got ${host.children.size})")

        // ---- a clear brings it back at the panel's next render, above what vanilla put back ----
        host.clearChildren()
        val refill = Global.getSettings().createCustom(60f, 30f, null)
        host.addComponent(refill).inTL(0f, 0f)
        expect(host.injection("test:a") == null, "right after a clear the injection is gone")
        frame()
        val a2 = host.injection("test:a")
        expect(a2 != null && a2 !== a, "the next render adds it again as a new container")
        expect(builds == 2, "its builder ran again ($builds)")
        expect(host.children.last() === a2, "Top again: after the children vanilla re-added")
        expect(host.children.first() === host.injection("test:b"), "Bottom again: under them")
        expect(host.children.size == 3, "nothing doubled (got ${host.children.size})")
        expect((a.plugin as StarUIPanelPlugin).isDisposed, "the cleared container was disposed")
        frame()
        expect(builds == 2, "a later frame adds nothing more ($builds)")

        // ---- removeInjection, removeComponent and dispose end it ----
        host.removeInjection("test:b")
        expect(host.injection("test:b") == null && host.children.size == 2, "removeInjection takes it out")
        host.removeComponent(host.injection("test:a")!!)
        host.clearChildren()
        frame()
        expect(host.injection("test:a") == null && builds == 2, "removed by hand, a clear does not bring it back ($builds)")
        expect(host.injection("test:b") == null, "nor one ended by removeInjection")
        val a3 = host.inject("test:a", builder = orange)
        expect(builds == 3 && host.children.size == 1, "inject after the end makes a new one ($builds)")
        (a3.plugin as StarUIPanelPlugin).dispose()
        expect(host.injection("test:a") == null, "a disposed container is not live")
        host.clearChildren()
        frame()
        expect(builds == 3 && host.children.isEmpty(), "disposed, a clear does not bring it back ($builds)")

        // ---- a clear that re-adds the same container (vanilla reordering) keeps it ----
        val keep = host.inject("test:keep") { Spacer(Size.exact(10f, 10f)) }
        host.clearChildren()
        host.addComponent(keep)
        frame()
        expect(host.injection("test:keep") === keep && host.children.size == 1, "re-added by the panel itself, it is kept as it is")
        expect(!(keep.plugin as StarUIPanelPlugin).isDisposed, "and not disposed")

        // ---- the injection has its own reactive scope ----
        val count = UIState(0)
        var label: LabelAPI? = null
        val reactiveHost = vanillaPanel(300f, 40f, 40f, 160f)
        reactiveHost.inject("test:reactive") { label = Text("count ${count.value}") }
        frame()
        count.value = 5
        frame()
        expect(label?.text == "count 5", "a UIState read in the builder rebuilds it (${label?.text})")

        // ---- an anchor to a vanilla sibling ----
        val anchorHost = vanillaPanel(300f, 100f, 40f, 220f)
        val sibling = vanillaPanel(60f, 20f, 10f, 10f, into = anchorHost)
        val anchored = anchorHost.inject("test:anchor", modifier = Modifier.anchor(Anchor.below.sibling(sibling).matchingLeftEdge(0f))) {
            Spacer(Size.exact(40f, 10f), Modifier.background(Color(60, 160, 200)))
        }
        frame()
        near(anchored.relLeft(anchorHost), 10f, "anchored below the vanilla sibling: left")
        near(anchored.relTop(anchorHost), 30f, "anchored below the vanilla sibling: top")
        // (an anchor is vanilla's relativeTo: like vanilla's own, it does not follow a sibling moved later by itself)

        // ---- a handler inside an injection makes the host rebuild, and the rebuild injects again (as a hook does):
        // the old container must not be disposed while its own handler list runs (RefitFilters' picker crash) ----
        val rebuildHost = vanillaPanel(200f, 40f, 40f, 400f)
        var rebuildNow = false
        var rebuilds = 0
        fun injectRebuilding(): CustomPanelAPI = rebuildHost.inject("test:rebuild", modifier = Modifier.onAdvance {
            if (rebuildNow) {
                rebuildNow = false
                rebuilds++
                rebuildHost.clearChildren()
                injectRebuilding()
            }
        }) { Spacer(Size.exact(10f, 10f)) }
        val beforeRebuild = injectRebuilding()
        frame()
        rebuildNow = true
        val rebuildErr = thrown { frame() }
        expect(rebuildErr == null, "an injection's own onAdvance can make the host rebuild and inject again (got $rebuildErr)")
        expect(rebuilds == 1 && rebuildHost.children.size == 1, "one rebuild, one container ($rebuilds, ${rebuildHost.children.size})")
        expect(rebuildHost.injection("test:rebuild").let { it != null && it !== beforeRebuild }, "the new container is the injection")
        frame()
        expect((beforeRebuild.plugin as StarUIPanelPlugin).isDisposed, "the old container is disposed at the next safe point")

        // ---- not for StarUI's reactive containers ----
        val reactive = root.Column(Size.exact(100f, 40f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 340f))) {
            Text("${count.value}")
        }
        val err = thrown { reactive.inject("test:bad") { } }
        expect(err is IllegalStateException && err.message?.contains("inject") == true, "inject on a reactive container throws (got $err)")

        frame()
        screenshot("final")
    }
}

fun registerGuardTests() {
    StarUITestSuite.group("guards-layout-exceptions") {
        // FillMax on the primary axis of a WrapContent stack has no size to divide.
        var r = scratchRoot()
        r.Column(Size.mixed(Axis.Exact(200f), Axis.WrapContent)) { Spacer(Size.fillMaxHeight(width = 10f)) }
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
        expect(err is IllegalStateException && err.message?.contains("only for children of a CustomPanel") == true,
            "Modifier.anchor inside a Column throws while building (got $err)")
        // a Box places its children by alignment, which would overwrite the anchor just as silently
        r = scratchRoot()
        err = thrown { r.Box(Size.exact(100f, 100f)) { Spacer(Size.exact(10f, 10f), Modifier.anchor(Anchor.inside.center.ofParent())) } }
        expect(err is IllegalStateException && err.message?.contains("(Box)") == true,
            "Modifier.anchor inside a Box throws while building (got $err)")
        // inside a CustomPanel it is what places the child
        r = scratchRoot()
        err = thrown { r.CustomPanel(Size.exact(100f, 100f)) { Spacer(Size.exact(10f, 10f), Modifier.anchor(Anchor.inside.center.ofParent())) } }
        expect(err == null, "Modifier.anchor inside a CustomPanel is fine (got $err)")

        // A WrapContent Box holding only FillMax children only warns, and collapses to zero.
        r = scratchRoot()
        val box = r.Box(Size.wrapContent()) { Spacer(Size.fillMaxSize()) }
        err = thrown { NativeSmokeSupport.render(r) }
        expect(err == null, "all-FillMax WrapContent Box does not throw (got $err)")
        near(box.w, 0f, "all-FillMax WrapContent Box collapses to width 0")
    }
}
