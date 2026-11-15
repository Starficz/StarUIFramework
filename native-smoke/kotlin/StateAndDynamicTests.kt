// UIState bindings, FilterGroup and RadioGroup, dynamic rebuild / injection, and the layout exception guards.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
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

    StarUITestSuite.group("state-filter-group") {
        val group = FilterGroup()
        val a = UIState(true); val b = UIState(false); val c = UIState(false)
        val buttons = mutableListOf<ButtonAPI>()
        root.Column(Size.exact(220f, 140f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)), spacing = 5f) {
            for ((name, s) in listOf("A" to a, "B" to b, "C" to c)) buttons += AreaCheckbox(name, size = Size.exact(200f, 30f), bind = s, buttonGroup = group)
        }
        frame()
        fun states() = "a=${a.value} b=${b.value} c=${c.value} checked=${buttons.map { it.isChecked }}"
        expect(buttons.map { it.isChecked } == listOf(true, false, false), "initial checked state follows the binds (${states()})")
        click(buttons[1])
        expect(!a.value && b.value && !c.value, "filter: clicking B selects only B (${states()})")
        expect(buttons.map { it.isChecked } == listOf(false, true, false), "filter: buttons reflect the selection (${states()})")
        click(buttons[1])
        expect(a.value && b.value && c.value, "clicking the only selected button selects all (${states()})")
        click(buttons[2])
        expect(!a.value && !b.value && c.value, "filter again: clicking C selects only C (${states()})")
        log("multi-select needs a held Shift/Ctrl (Keyboard.isKeyDown); not driven here")
        frame()
        screenshot("final")
    }

    StarUITestSuite.group("state-radio-group") {
        val group = RadioGroup()
        val states = List(3) { UIState(it == 0) }
        val buttons = mutableListOf<ButtonAPI>()
        root.Row(Size.exact(400f, 30f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)), spacing = 5f) {
            buttons.clear()
            listOf("Orbital", "Ground", "Deep core").forEachIndexed { i, name ->
                buttons += AreaCheckbox(name, states[i], Size.fillMaxWidth(height = 22f), buttonGroup = group)
            }
        }
        frame()
        fun selection() = "states=${states.map { it.value }} checked=${buttons.map { it.isChecked }}"
        fun only(i: Int) = states.indices.all { states[it].value == (it == i) && buttons[it].isChecked == (it == i) }
        expect(only(0), "starts on the state that is true (${selection()})")
        click(buttons[1])
        expect(only(1), "a click selects that button only (${selection()})")
        click(buttons[1])
        expect(only(1), "a click on the selected one keeps it, and only it, selected (${selection()})")
        states[2].value = true
        frame()
        expect(only(2), "a state set true from code turns the others off (${selection()})")
        click(buttons[0])
        expect(only(0), "and clicks still work after it (${selection()})")
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

    StarUITestSuite.group("injection-fill") {
        // feedback: inject(key, Size.fillMaxSize()) into a vanilla dialog's panel came out 0x0 at its bottom left
        fun vanillaPanel(w: Float, h: Float, left: Float, top: Float): CustomPanelAPI =
            Global.getSettings().createCustom(w, h, null).also { root.addComponent(it).inTL(left, top) }
        val text = "Current skills: a line long enough to wrap if the injection is given a sensible width at all"

        // ---- the panel's size is known when inject runs ----
        val known = vanillaPanel(400f, 300f, 40f, 40f)
        lateinit var label: LabelAPI
        val a = known.inject("test:fill-known", Size.fillMaxSize()) {
            Column(Size.fillMaxSize()) { label = Text(text, Size.fillMaxWidth()) }
        }
        frame(3)
        near(a.position.width, 400f, "a fill injection is as wide as the vanilla panel (${a.position.width})")
        near(a.position.height, 300f, "and as tall (${a.position.height})")
        near(a.relLeft(known), 0f, "at its left edge")
        near(a.relTop(known), 0f, "and its top edge, not its bottom (${a.relTop(known)})")
        expect(label.position.height < 40f, "the text wraps to a line or two, not a letter a line (${label.position.height} high)")

        // ---- vanilla sizes the panel after the hook injected (a constructor hook, a dialog sized later) ----
        val late = vanillaPanel(0f, 0f, 460f, 40f)
        val b = late.inject("test:fill-late", Size.fillMaxSize()) {
            Column(Size.fillMaxSize(), Modifier.padding(3f)) { Text(text, Size.fillMaxWidth()) }
        }
        frame(2)
        expect(b.position.width <= 1f && b.position.height <= 1f,
            "before the panel has a size, the fill injection waits instead of laying out (${b.position.width}x${b.position.height})")
        late.position.setSize(300f, 200f)
        frame(3)
        near(b.position.width, 300f, "sized after the inject: the injection follows to the panel's width (${b.position.width})")
        near(b.position.height, 200f, "and height (${b.position.height})")
        near(b.relTop(late), 0f, "still at the top (${b.relTop(late)})")

        // ---- and again when vanilla resizes it later ----
        late.position.setSize(350f, 120f)
        frame(3)
        near(b.position.width, 350f, "a later resize is followed too (${b.position.width})")
        near(b.position.height, 120f, "in both directions (${b.position.height})")

        // ---- no anchor: top left, as a layout reads, not vanilla's bottom left ----
        val plain = vanillaPanel(300f, 200f, 40f, 380f)
        val c = plain.inject("test:wrap-unanchored") { Spacer(Size.exact(50f, 20f)) }
        frame(3)
        near(c.relLeft(plain), 0f, "an injection without an anchor sits at the left edge")
        near(c.relTop(plain), 0f, "and the top edge (${c.relTop(plain)})")
        val anchored = plain.inject("test:wrap-anchored", modifier = Modifier.anchor(Anchor.inside.bottomRight.ofParent(5f, 5f))) {
            Spacer(Size.exact(40f, 10f))
        }
        frame(3)
        near(anchored.relLeft(plain), 300f - 40f - 5f, "an anchor of its own still wins")
        screenshot("injection-fill")
    }

    StarUITestSuite.group("anchor-panel") {
        fun vanillaPanel(w: Float, h: Float, left: Float, top: Float): CustomPanelAPI =
            Global.getSettings().createCustom(w, h, null).also { root.addComponent(it).inTL(left, top) }

        // ---- AnchorPanel wraps its anchored children ----
        val wrap = root.AnchorPanel(modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(40, 60, 90))) {
            Spacer(Size.exact(50f, 20f), Modifier.anchor(Anchor.inside.topLeft.ofParent(10f, 10f)))
            Spacer(Size.exact(40f, 10f), Modifier.anchor(Anchor.inside.bottomRight.ofParent(5f, 5f)))
        }
        frame(3)
        near(wrap.position.width, 60f, "an AnchorPanel is as wide as its top-left child reaches (10 + 50) (${wrap.position.width})")
        near(wrap.position.height, 30f, "and as tall (10 + 20); the bottom-right one needs only 10 + 5 (${wrap.position.height})")
        val loose = root.AnchorPanel(modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(200f, 40f))) {
            Spacer(Size.exact(30f, 30f))
        }
        frame(3)
        val child = loose.children.first()
        near(child.relTop(loose), 0f, "a child without an anchor sits at the top, not vanilla's bottom")
        near(loose.position.width, 30f, "and the panel wraps it (${loose.position.width})")
        // a chain of siblings from the top left: placed from each other's places, no frame needed to find them
        val chain = root.AnchorPanel(modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(300f, 40f)).background(Color(40, 60, 90))) {
            val a = Spacer(Size.exact(50f, 20f), Modifier.anchor(Anchor.inside.topLeft.ofParent(8f, 8f)))
            Spacer(Size.exact(80f, 10f), Modifier.anchor(Anchor.below.sibling(a).matchingLeftEdge(4f)))
            Spacer(Size.exact(30f, 30f), Modifier.anchor(Anchor.rightOf.sibling(a).matchingTopEdge(6f)))
        }
        frame(1)
        near(chain.position.width, 94f, "chained siblings: as wide as the one right of the first reaches (8 + 50 + 6 + 30) (${chain.position.width})")
        near(chain.position.height, 42f, "as tall as the one below it (8 + 20 + 4 + 10), after one frame (${chain.position.height})")
        val anchored = thrown { root.AnchorPanel { Spacer(Size.exact(10f, 10f), Modifier.anchor(Anchor.inside.center.ofParent())) } }
        expect(anchored == null, "Modifier.anchor inside an AnchorPanel is fine (got $anchored)")

        // ---- CustomPanel added straight to a vanilla panel: fills it and follows it; its children lay themselves out ----
        val host = vanillaPanel(300f, 150f, 40f, 200f)
        lateinit var column: CustomPanelAPI
        val free = host.CustomPanel(Size.fillMaxSize()) { column = Column(Size.fillMaxSize()) { Text("free") } }
        frame(3)
        near(free.position.width, 300f, "a fill CustomPanel in a vanilla panel takes its width (${free.position.width})")
        near(free.position.height, 150f, "and height (${free.position.height})")
        near(column.position.width, 300f, "its fill child fills it (${column.position.width})")
        host.position.setSize(360f, 180f)
        frame(3)
        near(free.position.width, 360f, "and both follow the vanilla panel's resize (${free.position.width}, ${column.position.width})")
        near(column.position.height, 180f, "on both axes (${column.position.height})")
        screenshot("anchor-panel")
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
