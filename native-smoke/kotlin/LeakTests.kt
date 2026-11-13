// Memory: StarUI's state for UI that is thrown away without being disposed (a vanilla screen dropping its panels)
// must not keep that UI alive. Compiled in the same Kotlin module as the framework, so `internal` is reachable.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import org.starficz.starui.components.*
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.onClick
import org.starficz.starui.modifier.tooltip
import org.starficz.starui.runtime.NodeStore
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.Size
import org.starficz.starui.UIState
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import java.awt.Color
import java.lang.ref.WeakReference

/** Builds a little UI on a panel that is attached nowhere and returns weak references to its parts. */
private fun buildAndDrop(state: UIState<Boolean>, text: UIState<String>): List<Pair<String, WeakReference<Any>>> {
    val root = Global.getSettings().createCustom(400f, 300f, null)
    val refs = ArrayList<Pair<String, WeakReference<Any>>>()
    root.Column(Size.exact(400f, 300f)) {
        refs += "a label with no handlers" to WeakReference<Any>(Text("plain"))
        refs += "a label with onClick" to WeakReference<Any>(Text("clickable", modifier = Modifier.onClick { }))
        refs += "a bound Checkbox" to WeakReference<Any>(Checkbox("bound", bind = state))
        refs += "a bound TextField" to WeakReference<Any>(TextField(text))
        refs += "a Button" to WeakReference<Any>(Button("press") { })
    }
    refs += "the root panel" to WeakReference<Any>(root)
    return refs
}

/** The same with only plain labels: no component gets a plugin of its own. */
private fun buildPlainAndDrop(): List<Pair<String, WeakReference<Any>>> {
    val root = Global.getSettings().createCustom(400f, 300f, null)
    val refs = ArrayList<Pair<String, WeakReference<Any>>>()
    root.Column(Size.exact(400f, 300f)) {
        refs += "(plain UI) a label" to WeakReference<Any>(Text("plain"))
    }
    refs += "(plain UI) the root panel" to WeakReference<Any>(root)
    return refs
}

/** StarUI components put straight into a vanilla panel (no StarUI container above them), as a DynamicInjection does. */
private fun buildInVanillaAndDrop(): List<Pair<String, WeakReference<Any>>> {
    val vanillaPanel = Global.getSettings().createCustom(400f, 300f, null)
    val refs = ArrayList<Pair<String, WeakReference<Any>>>()
    refs += "(in a vanilla panel) a Button with onClick" to WeakReference<Any>(vanillaPanel.Button("press", modifier = Modifier.onClick { }) { })
    refs += "(in a vanilla panel) a label with a tooltip" to WeakReference<Any>(vanillaPanel.Text("tip", modifier = Modifier.tooltip("hello")))
    refs += "(in a vanilla panel) the panel" to WeakReference<Any>(vanillaPanel)
    return refs
}

/** A clickable label moved out of a Column into [into]; the Column and its root are dropped. */
private fun moveLabel(into: CustomPanelAPI, onClick: () -> Unit): LabelAPI {
    val root = Global.getSettings().createCustom(400f, 300f, null)
    var label: LabelAPI? = null
    val column = root.Column(Size.exact(400f, 300f)) { label = Text("moved", modifier = Modifier.onClick { onClick() }) }
    column.removeComponent(label!!)
    into.addComponent(label!!)
    return label!!
}

private fun collectGarbage() = repeat(5) {
    System.gc()
    System.runFinalization()
    Thread.sleep(20)
}

fun registerLeakTests() {
    StarUITestSuite.group("leaks-dropped-ui-is-collected") {
        // the states outlive the UI, as a mod's state usually does
        val state = UIState(false)
        val text = UIState("x")
        val refs = buildPlainAndDrop() + buildAndDrop(state, text) + buildInVanillaAndDrop()
        collectGarbage()
        for ((what, ref) in refs) {
            expect(ref.get() == null, "UI thrown away without dispose is collected: $what")
        }
        expect(state.scopeSubscribers.none { it.get() != null }, "no live scope stays subscribed to a state that outlives its UI")
    }

    StarUITestSuite.group("leaks-moved-label-keeps-its-plugin") {
        // a label's node is held by its parent; moved elsewhere it must go with it, or it dies with the old parent
        val newHome = Global.getSettings().createCustom(400f, 300f, null)
        var clicks = 0
        val label = moveLabel(newHome) { clicks++ }
        collectGarbage()
        val plugin = StarUIPanelPlugin.getAttached(label)
        expect(plugin != null, "a label moved to another panel keeps its plugin after its old parent is collected")
        expect(plugin?.describeHandlers()?.contains("onClick") == true, "and its onClick (${plugin?.describeHandlers()})")
        expect(clicks == 0, "(no click yet)")
    }

    StarUITestSuite.group("leaks-rebuilds-do-not-pile-up") {
        // every rebuild makes a new label (its colour changes); the old ones are disposed and their nodes, held by the
        // Column (labels have no store of their own), must go with them
        val flip = UIState(0)
        val column = root.Column(Size.exact(200f, 100f)) {
            Text("flip ${flip.value}", color = if (flip.value % 2 == 0) Color.WHITE else Color.YELLOW)
        }
        frame()
        repeat(20) {
            flip.value++
            frame()
        }
        val held = (column.invoke("getListeners", NodeStore.Holder::class.java) as List<*>).size
        expect(held == 1, "the Column holds only its current label's node after 20 rebuilds ($held)")
    }
}
