// Interop with UI StarUI does not own (vanilla buttons, foreign CustomPanel plugins), API consolidation checks,
// and measured costs of the per-frame paths (lifecycle hooks, text measuring, injection polling).

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.Button
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.Row
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.Alignment
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.margin
import org.starficz.starui.compose.modifier.onClick
import org.starficz.starui.compose.runtime.DynamicInjection
import org.starficz.starui.compose.runtime.StarUIManager
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.getChildrenNonCopy
import org.starficz.starui.core.measureTextSize
import org.starficz.starui.core.measureTextSizeUncached
import org.starficz.starui.core.onClick
import java.awt.Color

private class CountingPlugin : BaseCustomUIPanelPlugin() {
    var renderBelow = 0; var render = 0; var advance = 0; var input = 0; var moved = 0; var pressed = 0
    override fun renderBelow(alphaMult: Float) { renderBelow++ }
    override fun render(alphaMult: Float) { render++ }
    override fun advance(amount: Float) { advance++ }
    override fun processInput(events: MutableList<InputEventAPI>) { input++ }
    override fun positionChanged(position: PositionAPI) { moved++ }
    override fun buttonPressed(buttonId: Any?) { pressed++ }
    override fun toString() = "renderBelow=$renderBelow render=$render advance=$advance input=$input moved=$moved pressed=$pressed"
}

private fun TestContext.clickAt(b: com.fs.starfarer.api.ui.UIComponentAPI) {
    Input.sendList(root, Input.move(b.cx, b.cy))
    Input.sendList(root, Input.down(b.cx, b.cy))
    Input.sendList(root, Input.up(b.cx, b.cy))
    NativeSmokeSupport.advance(root, 2)
}

fun registerInteropTests() {
    StarUITestSuite.group("interop-button-listener-chain") {
        // A plain vanilla button: made by a TooltipMakerAPI in a CustomPanel, so its listener is that panel,
        // which reports presses to the panel's own plugin. Attaching StarUI's onClick must keep that working.
        val owner = CountingPlugin()
        val host = Global.getSettings().createCustom(300f, 60f, owner)
        root.addComponent(host).inTL(40f, 40f)
        val tm = host.createUIElement(300f, 60f, false)
        val button: ButtonAPI = tm.addButton("Vanilla button", "vanilla-id", 200f, 30f, 0f)
        host.addUIElement(tm).inTL(0f, 0f)
        frame()

        clickAt(button)
        expect(owner.pressed == 1, "baseline: the vanilla owner sees the click ($owner)")

        var ours = 0
        var ours2 = 0
        button.onClick { ours++ }
        clickAt(button)
        expect(ours == 1, "StarUI onClick fires on a vanilla button (ours=$ours)")
        expect(owner.pressed == 2, "the vanilla owner still sees the click after onClick was attached ($owner)")

        button.onClick { ours2++ }
        clickAt(button)
        expect(ours == 2 && ours2 == 1, "a second onClick is added to the same listener (ours=$ours ours2=$ours2)")
        expect(owner.pressed == 3, "and the original action still runs exactly once per click ($owner)")
        screenshot("final")
    }

    StarUITestSuite.group("interop-foreign-plugin-wrap") {
        // Modifiers on a CustomPanel that already has someone else's plugin must not switch that plugin off.
        val foreign = CountingPlugin()
        val panel = Global.getSettings().createCustom(120f, 80f, foreign)
        root.addComponent(panel).inTL(100f, 200f)
        var clicks = 0
        Modifier.background(Color(220, 40, 40)).onClick { clicks++ }.operations.forEach { it(panel) }

        val wrapper = panel.plugin as? StarUIPanelPlugin
        check(wrapper != null) { "panel plugin is a StarUIPanelPlugin after applying modifiers (got ${panel.plugin})" }
        expect(wrapper!!.delegate === foreign, "the StarUI plugin wraps the foreign plugin as its delegate")

        frame()
        clickAt(panel)
        log("foreign plugin after a frame and a click: $foreign")
        expect(foreign.renderBelow > 0 && foreign.render > 0, "foreign plugin keeps rendering ($foreign)")
        expect(foreign.advance > 0, "foreign plugin keeps advancing ($foreign)")
        expect(foreign.input > 0, "foreign plugin keeps receiving input ($foreign)")
        expect(clicks == 1, "the StarUI onClick on the same panel fires once ($clicks)")
        frame()
        val centre = pixel(panel.cx, panel.cy)
        expect(centre.r > 170 && centre.g < 100, "StarUI background renders on the wrapped panel (got $centre)")
        screenshot("final")
    }
}

fun registerApiConsolidationTests() {
    StarUITestSuite.group("api-margin-order-and-defaults") {
        // margin now uses padding's side order: left, top, right, bottom.
        lateinit var a: CustomPanelAPI; lateinit var b: CustomPanelAPI
        val col = root.Column(Size.exact(300f, 200f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)), spacing = 5f) {
            a = Spacer(Size.exact(100f, 20f), Modifier.margin(left = 1f, top = 2f, right = 3f, bottom = 4f).background(Color(200, 60, 60)))
            b = Spacer(Size.exact(100f, 20f), Modifier.background(Color(60, 200, 60)))
        }
        frame()
        near(a.relLeft(col), 1f, "margin(1, 2, 3, 4): left = 1")
        near(a.relTop(col), 2f, "margin(1, 2, 3, 4): top = 2")
        near(b.relTop(col), 2f + 20f + 4f + 5f, "margin(1, 2, 3, 4): bottom = 4 pushes the next child")

        // The defaults follow Compose: a Column starts its children at the left, a Row at the top.
        lateinit var v: CustomPanelAPI; lateinit var c: CustomPanelAPI; lateinit var h: CustomPanelAPI; lateinit var r: CustomPanelAPI
        val colD = root.Column(Size.exact(300f, 60f), Modifier.anchor(Anchor.inside.topLeft.ofParent(400f, 40f))) { v = Spacer(Size.exact(100f, 20f)) }
        val colC = root.Column(Size.exact(300f, 60f), Modifier.anchor(Anchor.inside.topLeft.ofParent(400f, 120f)), horizontalAlignment = Alignment.CenterHorizontally) { c = Spacer(Size.exact(100f, 20f)) }
        val rowD = root.Row(Size.exact(300f, 60f), Modifier.anchor(Anchor.inside.topLeft.ofParent(400f, 200f))) { h = Spacer(Size.exact(100f, 20f)) }
        val rowC = root.Row(Size.exact(300f, 60f), Modifier.anchor(Anchor.inside.topLeft.ofParent(400f, 280f)), verticalAlignment = Alignment.CenterVertically) { r = Spacer(Size.exact(100f, 20f)) }
        frame()
        near(v.relLeft(colD), 0f, "a Column starts its children at the left by default")
        near(c.relLeft(colC), 100f, "Alignment.CenterHorizontally centres them")
        near(h.relTop(rowD), 0f, "a Row starts its children at the top by default")
        near(r.relTop(rowC), 20f, "Alignment.CenterVertically centres them")
        screenshot("final")
    }
}

fun registerPerfTests() {
    StarUITestSuite.group("perf-per-frame-costs") {
        // 1. Lifecycle hook overhead on UI StarUI does not own: 300 plain vanilla labels in a vanilla tooltip.
        //    hasAttachedPlugins=false short-circuits every hook; true is the state of any game where StarUI is used.
        val vanilla = Global.getSettings().createCustom(600f, 760f, null)
        root.addComponent(vanilla).inTL(0f, 0f)
        val tm = vanilla.createUIElement(600f, 760f, false)
        repeat(300) { tm.addPara("Vanilla label $it", 0f) }
        vanilla.addUIElement(tm).inTL(0f, 0f)
        NativeSmokeSupport.beginFrame()
        fun msPerFrame(frames: Int): Double {
            val t0 = System.nanoTime()
            repeat(frames) {
                vanilla.advance(1f / 60f)
                vanilla.processInput(mutableListOf())
                vanilla.render(1f)
            }
            return (System.nanoTime() - t0) / 1e6 / frames
        }
        // Alternate short off/on windows and keep each mode's best one: other load on the PC (it varied 3x between
        // runs) only ever makes a window slower, so the minimum is the fair comparison.
        val saved = StarUIPanelPlugin.hasAttachedPlugins
        StarUIPanelPlugin.hasAttachedPlugins = false; msPerFrame(120)
        StarUIPanelPlugin.hasAttachedPlugins = true; msPerFrame(120)
        var off = Double.MAX_VALUE
        var on = Double.MAX_VALUE
        repeat(6) {
            StarUIPanelPlugin.hasAttachedPlugins = false; off = minOf(off, msPerFrame(100))
            StarUIPanelPlugin.hasAttachedPlugins = true; on = minOf(on, msPerFrame(100))
        }
        StarUIPanelPlugin.hasAttachedPlugins = saved
        val delta = on - off
        log(String.format("PERF hooks: 300 vanilla labels, best of 6 x 100 frames: %.3f ms/frame hooks short-circuited, %.3f ms/frame hooks active -> overhead %.3f ms/frame, %.2f us per label per frame",
            off, on, delta, delta * 1000 / 300))
        expect(delta < 1.0, "lifecycle hooks add under 1 ms per frame on 300 vanilla labels (${"%.3f".format(delta)} ms)")

        // 2. Text measuring: uncached builds a panel + tooltip + label per call.
        val texts = (0 until 200).map { "Measured text number $it" }
        var t0 = System.nanoTime(); texts.forEach { measureTextSizeUncached(it, null) }
        val uncachedUs = (System.nanoTime() - t0) / 1e3 / texts.size
        texts.forEach { measureTextSize(it) }
        t0 = System.nanoTime(); repeat(5) { texts.forEach { measureTextSize(it) } }
        val cachedUs = (System.nanoTime() - t0) / 1e3 / (texts.size * 5)
        log(String.format("PERF measureTextSize: %.1f us uncached, %.2f us cached", uncachedUs, cachedUs))
        expect(measureTextSize(texts[7]) == measureTextSizeUncached(texts[7], null), "cached measurement equals a fresh one")
        expect(cachedUs * 10 < uncachedUs, "cache makes repeated measuring at least 10x cheaper")

        // 2b. Brute-force scope rebuild: a Column of 100 rows (label + button each), rebuilt and laid out again.
        val perfTick = UIState(0)
        val list = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(620f, 0f)), spacing = 1f) {
            val tick = perfTick.value
            for (i in 0 until 100) Row(Size.wrapContent(), spacing = 4f) {
                Text("Row $i tick $tick")
                Button("Go", size = Size.exact(40f, 16f)) { }
            }
        }
        NativeSmokeSupport.render(root)
        repeat(5) { perfTick.value++; NativeSmokeSupport.render(root) }
        t0 = System.nanoTime(); repeat(20) { NativeSmokeSupport.render(root) }
        val idleMs = (System.nanoTime() - t0) / 1e6 / 20
        t0 = System.nanoTime(); repeat(20) { perfTick.value++; NativeSmokeSupport.render(root) }
        val rebuildMs = (System.nanoTime() - t0) / 1e6 / 20 - idleMs
        log(String.format("PERF scope rebuild: 100 rows (300 components) rebuilt + laid out in %.2f ms (idle frame %.2f ms)", rebuildMs, idleMs))
        expect(list.getChildrenNonCopy().size == 100, "the rebuilt list has its 100 rows")
        expect(rebuildMs < 50.0, "rebuilding 100 rows stays under 50 ms (${"%.2f".format(rebuildMs)} ms)")
        root.removeComponent(list)

        // 3. Injection polling: StarUIManager.advance with 20 injections whose element is already present.
        val targets = (0 until 20).map { i -> root.CustomPanel(Size.exact(10f, 10f), Modifier.anchor(Anchor.inside.topLeft.ofParent(700f + i * 12f, 600f))) }
        targets.forEachIndexed { i, target ->
            StarUIManager.registerDynamicInjection(object : DynamicInjection {
                override val id = "perf-injection-$i"
                override fun findTarget(): UIPanelAPI = target
                override fun UIPanelAPI.injectUI() { Spacer(Size.exact(4f, 4f)) }
            })
        }
        repeat(200) { StarUIManager.advance() }
        t0 = System.nanoTime(); repeat(1000) { StarUIManager.advance() }
        val advanceUs = (System.nanoTime() - t0) / 1e3 / 1000
        log(String.format("PERF StarUIManager.advance with 20 settled injections: %.1f us per frame", advanceUs))
        expect(targets.all { it.getChildrenNonCopy().size == 1 }, "each injection ran exactly once while its element stayed")
        expect(advanceUs < 500.0, "injection polling stays under 0.5 ms per frame (${"%.1f".format(advanceUs)} us)")
        frame()
        screenshot("final")
    }
}
