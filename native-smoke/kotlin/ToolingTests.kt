// dumpLayout(): the text view of a UI tree that agents and tests use instead of a screenshot.

import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.Button
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.Row
import org.starficz.starui.compose.components.ScrollPanel
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.margin
import org.starficz.starui.compose.modifier.onClick
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.compose.runtime.dumpLayout
import org.starficz.starui.core.Anchor
import java.awt.Color

fun registerToolingTests() {
    StarUITestSuite.group("tooling-layout-dump") {
        val count = UIState(2)
        val col = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)), spacing = 5f) {
            Text("Rows: ${count.value}")
            for (i in 0 until count.value) Spacer(Size.exact(100f, 20f), Modifier.background(Color(200, 140, 60)).onClick { })
            Row(Size.wrapContent(), Modifier.margin(left = 4f), spacing = 5f) {
                Button("Add", size = Size.exact(60f, 20f)) { count.value++ }
            }
            ScrollPanel(Size.exact(120f, 40f)) { Spacer(Size.exact(100f, 80f)) }
        }
        frame()
        val dump = dumpLayout("first", col)
        log("layout dump:\n$dump")
        val lines = dump.lines().filter { it.isNotBlank() }
        expect(lines[0].startsWith("Column ") && " at (0, 0)" in lines[0] && "size=(wrap, wrap)" in lines[0],
            "the first line is the dumped component itself, at its own origin (${lines[0]})")
        expect("scope(builds=1, reactive)" in lines[0] && "renderBelow" in lines[0], "it names the scope and the attached modifiers (${lines[0]})")
        expect(lines[1].trim().startsWith("Text \"Rows: 2\"") && lines[1].startsWith("  "), "children are indented and labelled with their text (${lines[1]})")
        expect(lines.count { it.trim().startsWith("Spacer 100x20") && "size=(exact 100, exact 20)" in it && "onClick" in it } == 2,
            "both Spacers appear with their size, sizing and handlers")
        expect(lines.any { it.trim().startsWith("Row ") && "margin=(4, 0, 0, 0)" in it }, "the Row shows its margin")
        expect(lines.any { it.startsWith("    Button \"Add\" 60x20") }, "the Button is nested under the Row with its label")
        expect(lines.any { it.trim().startsWith("ScrollPanel 120x40") }, "the ScrollPanel is listed")
        expect(lines.last().startsWith("    Spacer 100x80"), "and its content is nested under it (${lines.last()})")
        val spacerLines = lines.filter { it.trim().startsWith("Spacer 100x20") }
        expect("at (0, 20)" in spacerLines[0] || "at (0, 2" in spacerLines[0], "positions are relative to the dumped component, y downwards (${spacerLines[0]})")

        count.value = 3
        frame()
        val after = col.dumpLayout().lines()
        expect(after.count { it.trim().startsWith("Spacer 100x20") } == 3 && "scope(builds=2, reactive)" in after[0],
            "after a state change the dump shows the rebuilt tree (${after[0]})")
        screenshot("final")
    }
}
