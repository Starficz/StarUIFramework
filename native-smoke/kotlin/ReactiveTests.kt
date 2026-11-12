// Reactive scopes: plain if / for in a builder follow UIState, plus disposal and the rules around rebuild timing.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.LayoutSize
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.AreaCheckbox
import org.starficz.starui.compose.components.Button
import org.starficz.starui.compose.components.Checkbox
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.Row
import org.starficz.starui.compose.components.ScrollPanel
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.components.TextField
import org.starficz.starui.compose.components.Theme
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.onAdvance
import org.starficz.starui.compose.modifier.onClick
import org.starficz.starui.compose.runtime.ButtonGroup
import org.starficz.starui.compose.runtime.Scope
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.compose.runtime.TextTheme
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.BoxedScrollPanel
import org.starficz.starui.core.BoxedUILabel
import org.starficz.starui.core.children
import org.starficz.starui.core.clearChildren
import java.awt.Color
import java.lang.ref.WeakReference

private val PANEL = Color(30, 30, 40)
private val ROW = Color(200, 140, 60)

private fun at(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

/** Texts of the native labels directly inside a container, in child order. */
private fun UIPanelAPI.labelTexts(): List<String> = children.filterIsInstance<LabelAPI>().map { it.text }

/** A real click (move, down, up) with no advance afterwards, so the test controls when the next frame happens. */
private fun TestContext.pressOnly(c: UIComponentAPI) {
    Input.sendList(root, Input.move(c.cx, c.cy))
    Input.sendList(root, Input.down(c.cx, c.cy))
    Input.sendList(root, Input.up(c.cx, c.cy))
}

/** Builds a reactive Column that is never attached anywhere and returns only a weak reference to it. */
private fun buildUnattachedScope(state: UIState<Int>): WeakReference<CustomPanelAPI> {
    val host = Global.getSettings().createCustom(200f, 100f, null)
    val column = host.Column(LayoutSize.wrap()) { Text("n=${state.value}") }
    return WeakReference(column)
}

fun registerScopeTests() {
    StarUITestSuite.group("scope-dispose") {
        val flag = UIState(false)
        var clicks = 0; var changes = 0
        lateinit var box: ButtonAPI
        val panel = root.CustomPanel(PanelSize.exact(300f, 80f), at(40f, 40f).background(PANEL).onClick { clicks++ }) {
            box = Checkbox("bound", bind = flag, modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(10f, 10f)))
            flag.onChange { changes++ }
        }
        val plugin = panel.plugin as StarUIPanelPlugin
        frame()
        expect(flag.observers.size == 2, "two listeners on the state: the Checkbox binding and the explicit onChange (${flag.observers.size})")
        flag.value = true
        expect(changes == 1 && box.isChecked, "before dispose the bindings work ($changes, ${box.isChecked})")
        Input.sendList(root, Input.down(panel.position.x + 250f, panel.cy))
        expect(clicks == 1, "before dispose the click handler works ($clicks)")

        plugin.dispose()
        expect(plugin.isDisposed, "isDisposed is set")
        expect(flag.observers.isEmpty(), "dispose() removes the UIState listeners right away (${flag.observers.size} left)")
        flag.value = false
        expect(changes == 1, "a disposed onChange no longer fires ($changes)")
        expect(box.isChecked, "a disposed Checkbox binding no longer follows the state")
        Input.sendList(root, Input.down(panel.position.x + 250f, panel.cy))
        expect(clicks == 1, "disposed handlers no longer fire ($clicks)")
        frame()
        screenshot("final")
    }

    StarUITestSuite.group("scope-clearchildren-and-move") {
        // clearChildren() from outside StarUI (vanilla rebuilding a panel StarUI was injected into).
        val state = UIState(0)
        var builds = 0
        val host = Global.getSettings().createCustom(300f, 60f, null)
        root.addComponent(host).inTL(40f, 40f)
        val inner = host.Column(LayoutSize.wrap()) { builds++; Text("n=${state.value}") }
        val innerPlugin = inner.plugin as StarUIPanelPlugin
        frame()
        expect(state.scopeSubscribers.size == 1, "the builder's read subscribed its scope (${state.scopeSubscribers.size})")

        host.clearChildren()
        host.addComponent(inner)
        Scope.disposeDetachedPending()
        expect(!innerPlugin.isDisposed, "clearChildren() followed by adding the same component back does not dispose it")
        state.value = 1
        frame()
        expect(builds == 2, "and its scope still rebuilds ($builds)")

        host.clearChildren()
        expect(!innerPlugin.isDisposed, "disposal after clearChildren() is deferred, not immediate")
        Scope.disposeDetachedPending()
        expect(innerPlugin.isDisposed, "a StarUI child dropped by clearChildren() is disposed once it stays detached")
        expect(state.scopeSubscribers.isEmpty(), "its scope is unsubscribed (${state.scopeSubscribers.size})")
        state.value = 2
        frame()
        expect(builds == 2, "and never rebuilds again ($builds)")

        // removeComponent + addComponent is a move, not a teardown.
        val moveState = UIState(0)
        var moveBuilds = 0
        val a = Global.getSettings().createCustom(300f, 60f, null)
        val b = Global.getSettings().createCustom(300f, 60f, null)
        root.addComponent(a).inTL(40f, 120f)
        root.addComponent(b).inTL(40f, 200f)
        val moved = a.Column(LayoutSize.wrap(), Modifier.background(PANEL)) { moveBuilds++; Text("moved ${moveState.value}") }
        frame()
        a.removeComponent(moved)
        b.addComponent(moved).inTL(0f, 0f)
        Scope.disposeDetachedPending()
        frame()
        expect(!(moved.plugin as StarUIPanelPlugin).isDisposed, "a component moved with removeComponent + addComponent is not disposed")
        moveState.value = 1
        frame()
        expect(moveBuilds == 2, "and still rebuilds in its new parent ($moveBuilds)")
        expect(moved.labelTexts() == listOf("moved 1"), "showing the new state (${moved.labelTexts()})")
        screenshot("final")

        // Nothing but the UI itself keeps a scope alive: states and the dirty list hold it weakly.
        val weakState = UIState(0)
        val ref = buildUnattachedScope(weakState)
        weakState.value = 1   // marks it dirty; it is detached, so it can never be flushed
        var collected = false
        for (i in 0 until 20) {
            System.gc()
            Thread.sleep(25)
            if (ref.get() == null) { collected = true; break }
        }
        expect(collected, "an unreferenced reactive container is garbage collected even while dirty")
        weakState.value = 2
        expect(weakState.scopeSubscribers.isEmpty(), "and the state forgets it on the next write (${weakState.scopeSubscribers.size})")
    }
}

fun registerReactiveTests() {
    StarUITestSuite.group("reactive-if-for") {
        val flag = UIState(true)
        val count = UIState(2)
        val unread = UIState(0)
        var builds = 0
        val col = root.Column(LayoutSize.wrap(), at(40f, 40f).background(PANEL), spacing = 5f) {
            builds++
            if (flag.value) Text("state A") else Text("state B")
            for (i in 0 until count.value) Spacer(PanelSize.exact(100f, 20f), Modifier.background(ROW))
            if (flag.value && count.value > 100) Text("never")   // reads both states a second time
        }
        frame()
        expect(col.labelTexts() == listOf("state A"), "first build takes the if branch (${col.labelTexts()})")
        expect(col.children.size == 3, "first build runs the for loop twice (${col.children.size} children)")
        val h2 = col.h
        expect(flag.scopeSubscribers.size == 1 && count.scopeSubscribers.size == 1,
            "a state read several times in one builder subscribes once (${flag.scopeSubscribers.size}, ${count.scopeSubscribers.size})")
        screenshot("first")

        flag.value = false
        expect(col.labelTexts() == listOf("state A") && builds == 1, "writing a state changes nothing by itself")
        frame()
        expect(col.labelTexts() == listOf("state B"), "the else branch shows after the next frame (${col.labelTexts()})")
        expect(builds == 2, "one rebuild ($builds)")

        count.value = 4
        frame()
        expect(col.children.size == 5, "the for loop now adds four rows (${col.children.size} children)")
        near(col.h, h2 + 2 * 25f, "and the Column re-wraps them")
        count.value = 0
        frame()
        expect(col.children.size == 1, "an empty loop removes the rows (${col.children.size} children)")

        val before = builds
        flag.value = true
        count.value = 1
        frame()
        expect(builds == before + 1, "two writes before a frame cause one rebuild (${builds - before})")
        expect(col.labelTexts() == listOf("state A") && col.children.size == 2, "showing both changes")

        unread.value = 5
        frame(); frame()
        expect(builds == before + 1, "a state the builder never read does not rebuild it")
        flag.value = true
        frame()
        expect(builds == before + 1, "writing an equal value does not rebuild it")
        screenshot("final")
    }

    StarUITestSuite.group("reactive-nested-scopes") {
        val outerState = UIState(0)
        val innerState = UIState(0)
        var outerBuilds = 0; var innerBuilds = 0
        lateinit var inner: CustomPanelAPI
        val outer = root.Column(LayoutSize.wrap(), at(40f, 40f).background(PANEL), spacing = 5f) {
            outerBuilds++
            Text("outer ${outerState.value}")
            inner = Row(LayoutSize.wrap(), Modifier.background(Color(50, 50, 70))) {
                innerBuilds++
                Text("inner ${innerState.value}")
            }
        }
        frame()
        val firstInner = inner

        innerState.value = 1
        frame()
        expect(outerBuilds == 1 && innerBuilds == 2, "an inner state rebuilds only the inner scope ($outerBuilds, $innerBuilds)")
        expect(inner === firstInner && firstInner in outer.children, "the inner container itself is kept")
        expect(firstInner.labelTexts() == listOf("inner 1"), "and shows the new value (${firstInner.labelTexts()})")

        outerState.value = 1
        frame()
        expect(outerBuilds == 2 && innerBuilds == 3, "an outer state rebuilds the outer scope and what is inside it ($outerBuilds, $innerBuilds)")
        expect(inner === firstInner, "the inner container is reused, not replaced")
        expect(!(firstInner.plugin as StarUIPanelPlugin).isDisposed, "and not disposed")
        expect(outer.labelTexts() == listOf("outer 1") && firstInner.labelTexts() == listOf("inner 1"), "both levels show their state")
        expect(innerState.scopeSubscribers.size == 1, "the inner scope is subscribed once (${innerState.scopeSubscribers.size})")

        outerState.value = 2
        innerState.value = 2
        frame()
        expect(outerBuilds == 3 && innerBuilds == 4, "both dirty: the outer rebuild covers the inner one, which is not built twice ($outerBuilds, $innerBuilds)")

        innerState.value = 1234567890
        frame()
        near(outer.w, inner.w, "layout follows an inner rebuild up to the top-level node", 1.01f)
        screenshot("final")
    }

    StarUITestSuite.group("reactive-input-timing") {
        val open = UIState(false)
        var builds = 0
        lateinit var button: ButtonAPI
        val col = root.Column(LayoutSize.wrap(), at(40f, 40f).background(PANEL), spacing = 5f) {
            builds++
            button = Button("Toggle", size = Size.exact(120f, 24f)) { open.value = !open.value }
            if (open.value) Text("details")
        }
        frame()
        expect(col.children.size == 1, "closed: only the button")

        pressOnly(button)
        expect(open.value, "the click handler ran during input dispatch")
        expect(col.children.size == 1 && builds == 1, "but nothing is rebuilt during dispatch (${col.children.size} children, $builds builds)")
        frame()
        expect(col.children.size == 2 && col.labelTexts() == listOf("details"), "the UI follows on the next frame (${col.labelTexts()})")
        screenshot("open")

        pressOnly(button)   // `button` is the rebuilt one now
        frame()
        expect(!open.value && col.children.size == 1, "the rebuilt button works too (${open.value}, ${col.children.size} children)")
        expect(builds == 3, "three builds in total ($builds)")
    }

    StarUITestSuite.group("reactive-detached-then-shown") {
        val state = UIState(0)
        var builds = 0
        val host = root.CustomPanel(PanelSize.exact(300f, 60f), at(40f, 40f).background(PANEL))
        val col = host.Column(LayoutSize.wrap()) { builds++; Text("v=${state.value}") }
        frame()
        expect(builds == 1, "built once")

        host.removeComponent(col)
        state.value = 1
        frame(); frame()
        expect(builds == 1, "a detached scope never rebuilds ($builds)")

        host.addComponent(col).inTL(0f, 0f)
        frame()
        expect(builds == 2, "it rebuilds once when it is shown again ($builds)")
        expect(col.labelTexts() == listOf("v=1"), "with the current state (${col.labelTexts()})")
        frame(); frame()
        expect(builds == 2, "and only once ($builds)")
        screenshot("final")
    }

    StarUITestSuite.group("reactive-loop-guard") {
        val state = UIState(0)
        var builds = 0
        val col = root.Column(LayoutSize.wrap(), at(40f, 40f).background(PANEL)) {
            builds++
            Text("n=${state.value}")
            if (state.value > 0) state.value = state.value + 1   // a builder must not do this
        }
        frame()
        expect(builds == 1, "built once")
        state.value = 1
        val error = thrown { frame() }
        expect(error == null, "a builder that keeps dirtying itself does not hang or throw (got $error)")
        expect(builds == 1 + Scope.MAX_PASSES_PER_FLUSH, "it is rebuilt ${Scope.MAX_PASSES_PER_FLUSH} times, then stopped ($builds builds)")
        frame(); frame()
        expect(builds == 1 + Scope.MAX_PASSES_PER_FLUSH, "and stays stopped on later frames ($builds builds)")
        expect(col.children.size == 1, "the last build is what stays on screen")
    }

    StarUITestSuite.group("reactive-textfield-focus") {
        val text = UIState("abc")
        val extra = UIState(false)
        lateinit var field: TextFieldAPI
        val col = root.Column(LayoutSize.exact(300f, 100f), at(40f, 40f).background(PANEL), spacing = 5f) {
            field = TextField(text, size = Size.exact(200f, 24f))
            if (extra.value) Text("extra line")
        }
        frame()
        val first = field
        first.grabFocus()
        expect(first.hasFocus(), "the field has keyboard focus")
        first.text = "typed"
        NativeSmokeSupport.advance(root, 1)
        expect(text.value == "typed", "typing reaches the state (${text.value})")

        extra.value = true
        frame()
        expect(field === first, "the rebuild reused the native field")
        expect(first.hasFocus(), "which keeps keyboard focus through the rebuild")
        expect(field.text == "typed", "and its text (${field.text})")
        expect(col.labelTexts() == listOf("extra line"), "the new branch is there")
        expect(text.observers.size == 1, "one text binding, not one per build (${text.observers.size})")
        screenshot("final")

        // A field that the new build no longer declares is removed, and must give up the keyboard.
        val showField = UIState(true)
        lateinit var optional: TextFieldAPI
        root.Column(LayoutSize.exact(300f, 60f), at(40f, 160f).background(PANEL)) {
            if (showField.value) optional = TextField(text, size = Size.exact(200f, 24f))
        }
        frame()
        optional.grabFocus()
        expect(optional.hasFocus(), "the optional field has keyboard focus")
        showField.value = false
        frame()
        expect(!optional.hasFocus(), "a removed field releases keyboard focus (it would keep eating keystrokes)")
        expect(text.observers.size == 1, "and its binding is gone (${text.observers.size})")
    }

    StarUITestSuite.group("reactive-outside-add-error") {
        val state = UIState(0)
        val reactive = root.Column(LayoutSize.wrap(), at(40f, 40f).background(PANEL)) { Text("n=${state.value}") }
        val static = root.Column(LayoutSize.wrap(), at(40f, 100f).background(PANEL)) { Text("no state read here") }
        frame()
        val error = thrown { reactive.Text("added from a handler") }
        expect(error is IllegalStateException && error.message?.contains("reactive container") == true && error.message?.contains("UIState") == true,
            "adding to a reactive container from outside its builder throws an error that names the fix (got $error)")
        expect(reactive.children.size == 1, "and nothing was added (${reactive.children.size} children)")
        expect(thrown { static.Text("added later") } == null, "a container whose builder read no state still accepts children from outside")
        frame()
        expect(static.children.size == 2, "the outside child is kept")
    }

    StarUITestSuite.group("reactive-cleanup-on-rebuild") {
        val tick = UIState(0)
        val text = UIState("x")
        var changes = 0; var containerAdvances = 0
        val col = root.Column(LayoutSize.exact(300f, 80f), at(40f, 40f).background(PANEL).onAdvance { containerAdvances++ }, spacing = 5f) {
            Text("tick ${tick.value}")
            TextField(text, size = Size.exact(200f, 24f))
            text.onChange { changes++ }
        }
        frame()
        for (i in 1..3) { tick.value = i; frame() }
        expect(text.observers.size == 2, "after three rebuilds the state has two listeners, not eight (${text.observers.size})")
        text.value = "y"
        expect(changes == 1, "the builder's onChange fires once per change ($changes)")
        val before = containerAdvances
        NativeSmokeSupport.advance(root, 1)
        expect(containerAdvances == before + 1, "the container's own modifiers survive rebuilds (${containerAdvances - before} advance calls)")

        // ButtonGroup across rebuilds: the same states are registered again for the new buttons.
        val group = ButtonGroup()
        val a = UIState(true); val b = UIState(false)
        val buttons = mutableListOf<ButtonAPI>()
        root.Column(LayoutSize.exact(220f, 80f), at(40f, 140f).background(PANEL), spacing = 5f) {
            tick.value
            buttons.clear()
            buttons += AreaCheckbox("A", size = Size.exact(200f, 30f), bind = a, buttonGroup = group)
            buttons += AreaCheckbox("B", size = Size.exact(200f, 30f), bind = b, buttonGroup = group)
        }
        frame()
        tick.value = 10
        frame()
        pressOnly(buttons[1])
        NativeSmokeSupport.advance(root, 2)
        expect(!a.value && b.value, "radio selection still works on rebuilt buttons (a=${a.value} b=${b.value})")
        expect(buttons.map { it.isChecked } == listOf(false, true), "and the rebuilt buttons show it (${buttons.map { it.isChecked }})")
        frame()
        screenshot("final")
    }

    StarUITestSuite.group("reactive-scrollpanel-and-theme") {
        val rows = UIState(3)
        lateinit var sp: BoxedScrollPanel
        sp = root.ScrollPanel(PanelSize.exact(300f, 100f), at(40f, 40f).background(PANEL), spacing = 5f) {
            for (i in 0 until rows.value) Spacer(PanelSize.exact(280f, 30f), Modifier.background(ROW))
        }
        frame()
        val content = sp.contentContainer
        near(content.position.height, 3 * 30f + 2 * 5f, "initial scroll content")
        rows.value = 10
        frame()
        near(content.position.height, 10 * 30f + 9 * 5f, "a ScrollPanel's builder is a scope too: the content follows the state")
        expect(content.children.size == 10, "ten rows (${content.children.size})")
        sp.scrollToY(1000f)
        near(sp.yOffset, 345f - 100f, "the new content can be scrolled")

        // The theme in force where a container was declared is in force again when it rebuilds.
        val themed = StarUITheme.Default.copy(text = TextTheme(color = Color(255, 0, 255)))
        val label = UIState("themed")
        lateinit var text: BoxedUILabel
        root.Theme(themed) {
            Column(LayoutSize.wrap(), at(400f, 40f).background(PANEL)) { text = Text(label.value) }
        }
        frame()
        expect(text.color == Color(255, 0, 255), "first build uses the surrounding theme (${text.color})")
        label.value = "themed again"
        frame()
        expect(text.text == "themed again" && text.color == Color(255, 0, 255), "a rebuild uses the same theme (${text.text}, ${text.color})")
        screenshot("final")
    }
}
