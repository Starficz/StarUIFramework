// Step 5 of docs/scrolling-plan.md: long, text-heavy lists. Each row is what a real mod list holds: an Orbitron
// title, plain labels, a wrapped 3-5 line paragraph, a highlighted label, a button, and a label that follows a
// per-row UIState. Measured per case: first build, a full relayout, one row's state change, a label's text change,
// idle frames and scrolling frames. Logged as "PERF list ..." lines; the budgets are the plan's.

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Overflow
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.Button
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.Row
import org.starficz.starui.compose.components.StatGrid
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.components.key
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.padding
import org.starficz.starui.compose.runtime.ClipStack
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.Font
import org.starficz.starui.core.scrollPanel
import java.awt.Color

private val ROW_BG = Color(25, 35, 45)
private val YELLOW = Color(230, 200, 60)
private val GREENISH = Color(90, 220, 90)

private val WORDS = ("The fleet left the jump point under a dim red star, its escorts spread wide to watch the debris field " +
    "while the tenders matched course with the freighters and the flagship called for a full sensor sweep of the " +
    "system before anyone committed to the approach").split(' ')

/** A paragraph of 30-60 words: 3-5 lines at the list's width. Different per row, as real text is. */
private fun paragraph(i: Int): String {
    val n = 30 + (i * 7) % 31
    return (0 until n).joinToString(" ") { WORDS[(i + it * 3) % WORDS.size] }
}

private class BenchRow(var paragraph: LabelAPI? = null)

private fun UIPanelAPI.benchRow(i: Int, counter: UIState<Int>, out: BenchRow?, statGrid: Boolean) {
    Row(Size.fillMaxWidth(), Modifier.background(ROW_BG).padding(6f), spacing = 8f) {
        Column(Size.fillMaxWidth(), spacing = 2f) {
            Text("Ship $i: Onslaught-class battleship", font = Font.ORBITRON_20)
            Row(Size.wrapContent(), spacing = 12f) {
                Text("Hull ${8000 + i}")
                Text("Armor ${1500 + i % 100}")
                Text("Flux ${12000 - i}")
            }
            val p = Text(paragraph(i), Size.fillMaxWidth())
            if (out != null) out.paragraph = p
            Text("Cost: ${320 + i} credits, CR ${i % 100}%", highlightedText = listOf("${320 + i}" to YELLOW, "${i % 100}%" to GREENISH))
            Text("Changed ${counter.value} times")
            if (statGrid) StatGrid(Size.fillMaxWidth(), columns = 2) {
                entry("Max speed", "${60 + i % 40}"); entry("Armor", "${1500 + i % 100}")
                entry("Hull", "${8000 + i}"); entry("Flux capacity", "${12000 - i}")
            }
        }
        Button("Sell", size = Size.exact(60f, 22f)) { }
    }
}

private class ListResult(
    val firstBuildMs: Double, val relayoutMs: Double, val idleMs: Double, val rowChangeMs: Double,
    val setTextMs: Double, val scrollMs: Double
)

private fun TestContext.timeMs(repeats: Int, block: () -> Unit): Double {
    val t0 = System.nanoTime()
    repeat(repeats) { block() }
    return (System.nanoTime() - t0) / 1e6 / repeats
}

/** One engine frame of the whole root, as the game runs it. */
private fun TestContext.fullFrame() {
    root.advance(1f / 60f)
    root.processInput(mutableListOf())
    root.render(1f)
}

/** Best of a few windows: other load on the PC only ever makes a window slower. */
private fun TestContext.idleMs(windows: Int = 4, frames: Int = 30): Double {
    var best = Double.MAX_VALUE
    repeat(windows) { best = minOf(best, timeMs(frames) { fullFrame() }) }
    return best
}

private fun TestContext.benchList(name: String, rows: Int, cull: Boolean, statGrid: Boolean, emptyMs: Double,
                                  perFrameCull: Boolean = true): ListResult {
    // without culling every frame draws every row: a few frames are enough to see that (and take seconds each)
    val windows = if (cull) 3 else 1
    root.clearChildren()
    NativeSmokeSupport.render(root)
    ClipStack.cullingEnabled = cull
    ClipStack.cullPerFrameWork = perFrameCull
    try {
        val counters = List(rows) { UIState(0) }
        val visibleRow = BenchRow()
        lateinit var list: CustomPanelAPI
        val firstBuild = timeMs(1) {
            list = root.Column(Size.exact(700f, 700f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)), spacing = 4f,
                overflow = Overflow.ScrollVertical) {
                for (i in 0 until rows) key(i) { benchRow(i, counters[i], if (i == 2) visibleRow else null, statGrid) }
            }
            NativeSmokeSupport.render(root)
        }
        repeat(if (cull) 5 else 1) { fullFrame() }
        val idle = idleMs(windows, if (cull) 30 else 3) - emptyMs

        val listPlugin = StarUIPanelPlugin.getAttached(list)!!
        val relayout = timeMs(if (cull) 3 else 1) { listPlugin.requestLayout(); fullFrame() } - idle - emptyMs

        var best = Double.MAX_VALUE
        repeat(windows) { best = minOf(best, timeMs(if (cull) 5 else 1) { counters[3].value++; fullFrame() }) }
        val rowChange = best - idle - emptyMs

        val label = visibleRow.paragraph!!
        val original = label.text
        var flip = false
        best = Double.MAX_VALUE
        repeat(windows) { best = minOf(best, timeMs(if (cull) 5 else 1) { flip = !flip; label.text = if (flip) original + " Edited." else original; fullFrame() }) }
        val setText = best - idle - emptyMs

        val sp = list.scrollPanel!!
        var y = 0f
        best = Double.MAX_VALUE
        repeat(windows) { best = minOf(best, timeMs(if (cull) 30 else 3) { y += 37f; sp.yOffset = y % (list.position.height - 700f); fullFrame() }) }
        val scroll = best - emptyMs

        log(String.format("PERF list %s: rows=%d cull=%s statGrid=%s content=%.0f px | first build %.0f ms | relayout %.1f ms | " +
            "idle +%.3f ms/frame | one row's state +%.2f ms | label setText +%.2f ms | scrolling %.3f ms/frame",
            name, rows, cull, statGrid, list.position.height, firstBuild, relayout, idle, rowChange, setText, scroll))
        screenshot(name)
        return ListResult(firstBuild, relayout, idle, rowChange, setText, scroll)
    } finally {
        ClipStack.cullingEnabled = true
        ClipStack.cullPerFrameWork = true
        root.clearChildren()
        NativeSmokeSupport.render(root)
    }
}

/**
 * Vanilla only, for the floor: [rows] rows of the same text as paragraphs in a vanilla tooltip, scrolling (vanilla's
 * own scroll panel) or not. A non-scrolling one with the visible rows only is what drawing that text costs at all.
 */
private fun TestContext.vanillaReference(name: String, rows: Int, scrolling: Boolean, emptyMs: Double): Pair<Double, Double> {
    root.clearChildren()
    val holder = com.fs.starfarer.api.Global.getSettings().createCustom(700f, 700f, null)
    root.addComponent(holder).inTL(40f, 40f)
    val tooltip = holder.createUIElement(700f, 700f, scrolling)
    val t0 = System.nanoTime()
    for (i in 0 until rows) {
        tooltip.setParaFont(org.starficz.starui.core.getFontPath(Font.ORBITRON_20))
        tooltip.addPara("Ship $i: Onslaught-class battleship", 4f)
        tooltip.setParaFontDefault()
        tooltip.addPara("Hull ${8000 + i}   Armor ${1500 + i % 100}   Flux ${12000 - i}", 2f)
        tooltip.addPara(paragraph(i), 2f)
        tooltip.addPara("Cost: %s credits, CR %s", 2f, YELLOW, "${320 + i}", "${i % 100}%")
        tooltip.addPara("Changed 0 times", 2f)
    }
    holder.addUIElement(tooltip).inTL(0f, 0f)
    NativeSmokeSupport.render(root)
    val build = (System.nanoTime() - t0) / 1e6
    repeat(5) { fullFrame() }
    val idle = idleMs(3, 30) - emptyMs
    var scroll = 0.0
    val sp = holder.getChildrenCopy2().firstOrNull { it is com.fs.starfarer.api.ui.ScrollPanelAPI } as com.fs.starfarer.api.ui.ScrollPanelAPI?
    if (scrolling && sp != null) {
        var y = 0f
        var best = Double.MAX_VALUE
        val maxY = maxOf(1f, tooltip.heightSoFar - 700f)
        repeat(3) { best = minOf(best, timeMs(30) { y += 37f; sp.yOffset = y % maxY; fullFrame() }) }
        scroll = best - emptyMs
    }
    log(String.format("PERF list vanilla %s: rows=%d scrolling=%s | build %.0f ms | idle +%.3f ms/frame | scrolling %.3f ms/frame",
        name, rows, scrolling, build, idle, scroll))
    root.clearChildren()
    NativeSmokeSupport.render(root)
    return Pair(idle, scroll)
}

fun registerLongListPerfTests() {
    StarUITestSuite.group("perf-long-lists") {
        NativeSmokeSupport.beginFrame()
        repeat(30) { fullFrame() }
        val empty = idleMs()
        log(String.format("PERF list baseline: empty root %.3f ms/frame", empty))

        val (drawFloor, _) = vanillaReference("visible-rows-only", 6, scrolling = false, emptyMs = empty)
        val (vanillaIdle, vanillaScroll) = vanillaReference("scroll-1000", 1000, scrolling = true, emptyMs = empty)
        log(String.format("PERF list floors: drawing the visible rows %.3f ms, vanilla's own 1000-row scroll panel idle %.3f ms, scrolling %.3f ms",
            drawFloor, vanillaIdle, vanillaScroll))

        val noCull = benchList("1000-no-cull", 1000, cull = false, statGrid = false, emptyMs = empty)
        benchList("1000-culled-render-only", 1000, cull = true, statGrid = false, emptyMs = empty, perFrameCull = false)
        val culled = benchList("1000-culled", 1000, cull = true, statGrid = false, emptyMs = empty)
        val big = benchList("3000-culled", 3000, cull = true, statGrid = false, emptyMs = empty)
        val grids = benchList("300-statgrid-culled", 300, cull = true, statGrid = true, emptyMs = empty)

        // Budgets (docs/scrolling-plan.md, step 5). "Floor" is vanilla drawing only the visible rows' text: what any
        // list shows costs that much. Scrolling and a change elsewhere in the list still grow with the row count in a
        // plain scrolling Column (vanilla recomputes every position on a scroll; StarUI walks every sibling on a
        // change); LazyColumn is the answer for lists far longer than these.
        expect(culled.idleMs * 20 < noCull.idleMs, "culling makes idle frames at least 20x cheaper (${"%.3f".format(culled.idleMs)} vs ${"%.3f".format(noCull.idleMs)} ms)")
        expect(culled.idleMs * 20 < vanillaIdle, "a culled StarUI list idles at least 20x cheaper than vanilla's own scroll panel with the same text (${"%.3f".format(culled.idleMs)} vs ${"%.3f".format(vanillaIdle)} ms)")
        expect(culled.idleMs < drawFloor + 3.0, "1000 text rows: idle frame within 3 ms of drawing the visible text (${"%.3f".format(culled.idleMs)} ms, floor ${"%.3f".format(drawFloor)} ms)")
        expect(big.idleMs < drawFloor + 5.0, "3000 text rows: idle frame within 5 ms of drawing the visible text (${"%.3f".format(big.idleMs)} ms)")
        expect(culled.scrollMs < drawFloor + 12.0, "1000 text rows: scrolling frames within 12 ms of drawing the visible text (${"%.3f".format(culled.scrollMs)} ms)")
        expect(culled.rowChangeMs < 15.0, "1000 text rows: one row's state change under 15 ms (${"%.2f".format(culled.rowChangeMs)} ms)")
        expect(culled.setTextMs < 15.0, "1000 text rows: a label's text change under 15 ms (${"%.2f".format(culled.setTextMs)} ms)")
        expect(culled.relayoutMs < 20.0, "1000 text rows: a relayout with nothing changed under 20 ms (${"%.2f".format(culled.relayoutMs)} ms)")
        expect(grids.idleMs < 10.0, "300 rows with a StatGrid: idle frame under 10 ms (${"%.3f".format(grids.idleMs)} ms)")
    }
}
