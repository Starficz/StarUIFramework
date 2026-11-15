// Test-only. Default package on purpose: Kotlin in a named package cannot see the default-package harness classes
// (NativeSmokeSupport, StarUITestSupport) that the native-smoke runner puts on the game's classpath.
// Compiled by run-test-suite.ps1 into a test-only copy of StarUIFramework.jar; never part of the shipped jar.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.dumpLayout
import java.lang.reflect.InvocationTargetException
import kotlin.math.abs

class TestGroup(val name: String, val body: TestContext.() -> Unit)

/** One fresh full-screen root per group, so groups never see each other's components. */
class TestContext(val group: String) {
    val screenW: Float = NativeSmokeSupport.width()
    val screenH: Float = NativeSmokeSupport.height()
    val root: CustomPanelAPI = Global.getSettings().createCustom(screenW, screenH, null).also {
        it.position.setSize(screenW, screenH)
        it.position.inTL(0f, 0f)
    }
    var failures = 0
        private set
    var checks = 0
        private set

    fun log(message: String) = NativeSmokeSupport.log("[$group] $message")

    fun expect(condition: Boolean, message: String): Boolean {
        checks++
        val ok = NativeSmokeSupport.expect(condition, "[$group] $message")
        if (!ok) failures++
        return ok
    }

    /** Starsector rounds absolute positions to whole pixels, so geometry compares with a half-pixel tolerance. */
    fun near(actual: Float, expected: Float, message: String, tolerance: Float = 0.51f): Boolean =
        expect(abs(actual - expected) <= tolerance, "$message: expected $expected, got $actual")

    /** Advance (faders, StarUI advance hooks), then render once (layout runs lazily on the first render). */
    fun frame(advanceFrames: Int = 3) {
        NativeSmokeSupport.advance(root, advanceFrames)
        NativeSmokeSupport.render(root)
    }

    fun screenshot(tag: String) = NativeSmokeSupport.screenshot("$group-$tag")

    /** Writes the layout below [component] to `<group>-<tag>.layout.txt` in the output directory and returns it. */
    fun dumpLayout(tag: String, component: UIComponentAPI = root): String {
        val text = component.dumpLayout()
        StarUITestSupport.writeText("$group-$tag.layout.txt", text)
        return text
    }

    /** Runs [block] and returns the (unwrapped) throwable, or null when it completed. */
    fun thrown(block: () -> Unit): Throwable? = try {
        block(); null
    } catch (t: Throwable) {
        unwrap(t)
    }
}

fun unwrap(t: Throwable): Throwable {
    var cur = t
    while (cur is InvocationTargetException && cur.targetException != null) cur = cur.targetException
    return cur
}

// ---------- geometry relative to a container's top-left, the way layouts are written ----------
fun UIComponentAPI.relLeft(container: UIComponentAPI) = position.x - container.position.x
fun UIComponentAPI.relTop(container: UIComponentAPI) =
    (container.position.y + container.position.height) - (position.y + position.height)
val UIComponentAPI.w get() = position.width
val UIComponentAPI.h get() = position.height
val UIComponentAPI.cx get() = position.x + position.width / 2f
val UIComponentAPI.cy get() = position.y + position.height / 2f

fun TestContext.expectRect(
    c: UIComponentAPI, container: UIComponentAPI,
    left: Float, top: Float, width: Float, height: Float, label: String, tolerance: Float = 0.51f
) {
    near(c.relLeft(container), left, "$label left", tolerance)
    near(c.relTop(container), top, "$label top", tolerance)
    near(c.w, width, "$label width", tolerance)
    near(c.h, height, "$label height", tolerance)
}

// ---------- real engine input events ----------
object Input {
    fun down(x: Float, y: Float, button: Int = 0): InputEventAPI = StarUITestSupport.mouseButton(InputEventType.MOUSE_DOWN, x, y, button)
    fun up(x: Float, y: Float, button: Int = 0): InputEventAPI = StarUITestSupport.mouseButton(InputEventType.MOUSE_UP, x, y, button)
    fun move(x: Float, y: Float): InputEventAPI = StarUITestSupport.mouseMove(x, y)
    /** A wheel notch at (x, y): [notches] > 0 is wheel up, < 0 wheel down. */
    fun wheel(x: Float, y: Float, notches: Int, shift: Boolean = false): InputEventAPI = StarUITestSupport.mouseWheel(x, y, notches, shift)
    fun keyDown(code: Int, ch: Char = '\u0000'): InputEventAPI = StarUITestSupport.key(InputEventType.KEY_DOWN, code, ch)
    fun keyUp(code: Int, ch: Char = '\u0000'): InputEventAPI = StarUITestSupport.key(InputEventType.KEY_UP, code, ch)

    /** The engine's top-level path: UIComponentAPI.processInput(List). Each call is one emulated input frame. */
    fun sendList(target: UIComponentAPI, vararg events: InputEventAPI) {
        StarUITestSupport.beginInputFrame()
        target.processInput(StarUITestSupport.list(*events))
    }

    /** The in-tree path panels use for their children: the internal list overload. One emulated input frame. */
    fun sendInternal(target: UIComponentAPI, vararg events: InputEventAPI) {
        StarUITestSupport.beginInputFrame()
        StarUITestSupport.dispatchInternal(target, StarUITestSupport.list(*events))
    }
}

// ---------- pixels ----------
data class Rgb(val r: Int, val g: Int, val b: Int)

/** Colour of the back-buffer pixel at UI coordinates (x, y). */
fun pixel(x: Float, y: Float): Rgb {
    val p = NativeSmokeSupport.region(x, y, 1f, 1f)
    return Rgb(p[0].toInt() and 255, p[1].toInt() and 255, p[2].toInt() and 255)
}

/** Fraction of pixels in a UI-unit rectangle that satisfy [match]. */
fun fraction(x: Float, y: Float, w: Float, h: Float, match: (Rgb) -> Boolean): Float {
    val p = NativeSmokeSupport.region(x, y, w, h)
    var hit = 0
    var total = 0
    var i = 0
    while (i + 2 < p.size) {
        if (match(Rgb(p[i].toInt() and 255, p[i + 1].toInt() and 255, p[i + 2].toInt() and 255))) hit++
        total++
        i += 4
    }
    return if (total == 0) 0f else hit.toFloat() / total
}

object StarUITestSuite {
    private val groups = mutableListOf<TestGroup>()
    private val previews = linkedMapOf<String, TestContext.() -> Unit>()

    fun group(name: String, body: TestContext.() -> Unit) {
        groups += TestGroup(name, body)
    }

    /**
     * Registers a piece of UI to look at with `run-test-suite.ps1 -Preview <name>`: [body] builds it on `root`
     * (a full-screen panel), the runner renders two frames and writes `<name>.png` and `<name>.layout.txt`.
     */
    fun preview(name: String, body: TestContext.() -> Unit) {
        previews[name] = body
    }

    private fun runPreview(name: String) {
        registerPreviews()
        val body = previews[name]
        NativeSmokeSupport.check(body != null, "preview '$name' is registered (available: ${previews.keys.joinToString()})")
        NativeSmokeSupport.log("=== PREVIEW $name")
        val ctx = TestContext(name)
        ctx.(body!!)()
        ctx.frame()
        ctx.frame()   // a second frame lets reactive rebuilds and re-layouts from the first one settle
        NativeSmokeSupport.screenshot(name)
        val layout = ctx.root.dumpLayout()
        StarUITestSupport.writeText("$name.layout.txt", layout)
        layout.lineSequence().filter { it.isNotBlank() }.forEach { NativeSmokeSupport.log("LAYOUT $it") }
    }

    /**
     * Entry point for the Java probe. Needs vanilla rendering: under Fast Rendering onApplicationLoad runs on FR's
     * loader thread and OpenGL lives on FR's render thread, so the harness's raw GL (frame setup, glReadPixels) has no
     * context on any thread the suite could run on. Fail fast with a clear message instead of 400 confusing failures.
     */
    @JvmStatic
    fun start() {
        NativeSmokeSupport.run {
            NativeSmokeSupport.check(Thread.currentThread().name == "main",
                "suite runs on the main GL thread (got ${Thread.currentThread().name}; Fast Rendering is not supported here)")
            runAll()
        }
    }

    /** Runs every registered group. A group that throws is recorded as a failure and the next group still runs. */
    @JvmStatic
    fun runAll() {
        val previewName = System.getenv("STARUI_PREVIEW")?.takeIf { it.isNotBlank() }
        if (previewName != null) {
            runPreview(previewName)
            return
        }
        registerAll()
        val filter = System.getenv("STARUI_TEST_FILTER")?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }
        var totalChecks = 0
        var totalFailures = 0
        val failedGroups = mutableListOf<String>()
        for (g in groups) {
            if (filter != null && filter.none { g.name.contains(it, ignoreCase = true) }) continue
            // benchmarks (bench-*) are long: they run only when the filter asks for them (-Filter bench or the name)
            if (g.name.startsWith("bench-") && (filter == null || filter.none { it.startsWith("bench", ignoreCase = true) })) continue
            NativeSmokeSupport.log("=== GROUP ${g.name}")
            val ctx = TestContext(g.name)
            val error = ctx.thrown { ctx.(g.body)() }
            if (error != null) {
                ctx.expect(false, "group aborted: $error")
                error.printStackTrace()
                ctx.thrown { ctx.screenshot("aborted") }
            }
            totalChecks += ctx.checks
            totalFailures += ctx.failures
            if (ctx.failures > 0) failedGroups += "${g.name}(${ctx.failures})"
        }
        NativeSmokeSupport.log("SUITE SUMMARY checks=$totalChecks failures=$totalFailures failedGroups=$failedGroups")
    }

    private fun registerAll() {
        if (groups.isNotEmpty()) return
        registerContractTests()
        registerStackTests()
        registerBoxTests()
        registerModifierVisualTests()
        registerAnchorTests()
        registerScrollTests()
        registerOverflowTests()
        registerLazyListTests()
        registerStateTests()
        registerDynamicTests()
        registerRelayoutTests()
        registerScopeTests()
        registerReactiveTests()
        registerReuseTests()
        registerToolingTests()
        registerGuardTests()
        registerInputTests()
        registerHoverStateTests()
        registerInteropTests()
        registerApiConsolidationTests()
        registerReviewFixTests()
        registerFillMeasureTests()
        registerValueHookTests()
        registerShipDisplayScaleTests()
        registerSectionTests()
        registerApiShapeTests()
        registerToolkitTests()
        registerSkillRecipeTests()
        registerTextOverflowTests()
        registerLayerTests()
        registerDialogTests()
        registerToastTests()
        registerFindTests()
        registerToastRecipeTests()
        registerHotReloadTests()
        registerImageSizeTests()
        registerShipDisplayOptionTests()
        registerVanillaElementTests()
        registerVanillaPortTests()
        registerTooltipMakerPanelTests()
        registerSkillIconTests()
        registerHoloTests()
        registerLeakTests()
        registerLongListPerfTests()
        registerPerfTests()   // last: it registers 20 permanent dynamic injections
    }
}
