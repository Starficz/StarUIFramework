// StackLayoutEngine geometry: exact child rectangles relative to the container's top-left.
// Children are exact-size Spacers (plain CustomPanels), so the numbers depend only on the engine.

import org.starficz.starui.compose.modifier.padding
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.FlexSpacer
import org.starficz.starui.compose.components.Row
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.HGrow
import org.starficz.starui.compose.Alignment
import org.starficz.starui.compose.VGrow
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.margin
import org.starficz.starui.core.Anchor
import java.awt.Color

private val ORIGIN = Anchor.inside.topLeft.ofParent(40f, 40f)

/** Three exact children used by most cases: 100x30, 50x40, 200x10. */
private class Three { lateinit var a: CustomPanelAPI; lateinit var b: CustomPanelAPI; lateinit var c: CustomPanelAPI }

private fun CustomPanelAPI.addThree(t: Three) {
    t.a = Spacer(Size.exact(100f, 30f), Modifier.background(Color(200, 60, 60)))
    t.b = Spacer(Size.exact(50f, 40f), Modifier.background(Color(60, 200, 60)))
    t.c = Spacer(Size.exact(200f, 10f), Modifier.background(Color(60, 60, 200)))
}

fun registerStackTests() {
    StarUITestSuite.group("stack-column-align") {
        // Column 300x400, padding 10 across and 20 up and down, spacing 5. Children flow down from the top padding.
        for ((i, align) in listOf(Alignment.Left, Alignment.CenterHorizontally, Alignment.Right).withIndex()) {
            val t = Three()
            val col = root.Column(
                Size.exact(300f, 400f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f + i * 320f, 40f)).background(Color(30, 30, 40)).padding(10f, 20f),
                horizontalAlignment = align, spacing = 5f
            ) { addThree(t) }
            frame()
            fun x(w: Float) = when (align) {
                Alignment.Left -> 10f
                Alignment.CenterHorizontally -> 10f + (280f - w) / 2f
                else -> 300f - 10f - w
            }
            expectRect(t.a, col, x(100f), 20f, 100f, 30f, "$align a")
            expectRect(t.b, col, x(50f), 55f, 50f, 40f, "$align b")
            expectRect(t.c, col, x(200f), 100f, 200f, 10f, "$align c")
        }
        screenshot("final")
    }

    StarUITestSuite.group("stack-column-upwards") {
        val t = Three()
        val col = root.Column(
            Size.exact(300f, 400f), Modifier.anchor(ORIGIN).background(Color(30, 30, 40)).padding(10f, 20f),
            grow = VGrow.Upwards, spacing = 5f
        ) { addThree(t) }
        frame()
        // first child sits on the bottom padding, the rest stack above it
        expectRect(t.a, col, 10f, 400f - 20f - 30f, 100f, 30f, "a")
        expectRect(t.b, col, 10f, 350f - 5f - 40f, 50f, 40f, "b")
        expectRect(t.c, col, 10f, 305f - 5f - 10f, 200f, 10f, "c")
        screenshot("final")
    }

    StarUITestSuite.group("stack-row-align-grow") {
        // Row 400x100, padding 10 across and 20 up and down, spacing 5.
        val cases = listOf(
            Triple(HGrow.Rightwards, Alignment.Top, "right-top"),
            Triple(HGrow.Rightwards, Alignment.CenterVertically, "right-center"),
            Triple(HGrow.Rightwards, Alignment.Bottom, "right-bottom"),
            Triple(HGrow.Leftwards, Alignment.Top, "left-top"),
        )
        for ((i, case) in cases.withIndex()) {
            val (grow, align, label) = case
            val t = Three()
            val row = root.Row(
                Size.exact(400f, 100f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f + i * 120f)).background(Color(30, 30, 40)).padding(10f, 20f),
                grow = grow, verticalAlignment = align, spacing = 5f
            ) { addThree(t) }
            frame()
            fun top(h: Float) = when (align) {
                Alignment.Top -> 20f
                Alignment.CenterVertically -> 20f + (60f - h) / 2f
                else -> 100f - 20f - h
            }
            val xs = if (grow == HGrow.Rightwards) listOf(10f, 115f, 170f) else listOf(290f, 235f, 30f)
            expectRect(t.a, row, xs[0], top(30f), 100f, 30f, "$label a")
            expectRect(t.b, row, xs[1], top(40f), 50f, 40f, "$label b")
            expectRect(t.c, row, xs[2], top(10f), 200f, 10f, "$label c")
        }
        screenshot("final")
    }

    StarUITestSuite.group("stack-fill-weights") {
        // Column 300x400, padding 20 up and down, spacing 5: 350px of flex split 1:2:1.
        lateinit var a: CustomPanelAPI; lateinit var b: CustomPanelAPI; lateinit var c: CustomPanelAPI
        val col = root.Column(Size.exact(300f, 400f), Modifier.anchor(ORIGIN).background(Color(30, 30, 40)).padding(10f, 20f),
            spacing = 5f) {
            a = Spacer(Size.mixed(Axis.Exact(100f), Axis.Fill(1f)), Modifier.background(Color(200, 60, 60)))
            b = Spacer(Size.mixed(Axis.Fill(1f), Axis.Fill(2f)), Modifier.background(Color(60, 200, 60)))
            c = Spacer(Size.mixed(Axis.Exact(100f), Axis.Fill(1f)), Modifier.background(Color(60, 60, 200)))
        }
        frame()
        expectRect(a, col, 10f, 20f, 100f, 87.5f, "weight1 a", 1.01f)
        expectRect(b, col, 10f, 112.5f, 280f, 175f, "weight2 b (cross Fill = inner width)", 1.01f)
        expectRect(c, col, 10f, 292.5f, 100f, 87.5f, "weight1 c", 1.01f)

        // FlexSpacer pushes the last child to the far edge of a Row.
        lateinit var left: CustomPanelAPI; lateinit var right: CustomPanelAPI
        val row = root.Row(Size.exact(400f, 50f), Modifier.anchor(Anchor.inside.topLeft.ofParent(400f, 40f)).background(Color(30, 30, 40))) {
            left = Spacer(Size.exact(100f, 50f), Modifier.background(Color(200, 60, 60)))
            FlexSpacer()
            right = Spacer(Size.exact(50f, 50f), Modifier.background(Color(60, 60, 200)))
        }
        frame()
        near(left.relLeft(row), 0f, "FlexSpacer row: left child at x=0")
        near(right.relLeft(row), 350f, "FlexSpacer row: right child pushed to x=350")
        screenshot("final")
    }

    StarUITestSuite.group("stack-margins") {
        // Margins are (left, right, top, bottom); named arguments everywhere.
        lateinit var a: CustomPanelAPI; lateinit var b: CustomPanelAPI
        val col = root.Column(Size.exact(300f, 300f), Modifier.anchor(ORIGIN).background(Color(30, 30, 40)), spacing = 5f) {
            a = Spacer(Size.exact(100f, 30f), Modifier.margin(left = 7f, top = 3f, bottom = 11f).background(Color(200, 60, 60)))
            b = Spacer(Size.exact(100f, 30f), Modifier.margin(left = 2f, top = 4f).background(Color(60, 200, 60)))
        }
        frame()
        expectRect(a, col, 7f, 3f, 100f, 30f, "a with margin l7 t3 b11")
        // b.top = a.bottom + a.margin.bottom + spacing + b.margin.top = 33 + 11 + 5 + 4
        expectRect(b, col, 2f, 53f, 100f, 30f, "b after a's bottom margin")

        // Positional two-argument margin(x, y) resolves to margin(horizontal, vertical).
        lateinit var p: CustomPanelAPI
        val col2 = root.Column(Size.exact(200f, 100f), Modifier.anchor(Anchor.inside.topLeft.ofParent(400f, 40f)).background(Color(30, 30, 40))) {
            p = Spacer(Size.exact(50f, 20f), Modifier.margin(6f, 9f).background(Color(200, 200, 60)))
        }
        frame()
        near(p.relLeft(col2), 6f, "margin(6f, 9f) = horizontal 6")
        near(p.relTop(col2), 9f, "margin(6f, 9f) = vertical 9")
        screenshot("final")
    }

    StarUITestSuite.group("stack-row-text-measure") {
        // A short Row must not measure its children against its own height: text inside a 600x24 Row
        // should keep its single-line width, not wrap to ~24px.
        val text = "This label is much wider than the row is tall"
        lateinit var label: UIComponentAPI
        lateinit var columnLabel: UIComponentAPI
        root.Row(Size.exact(600f, 24f), Modifier.anchor(ORIGIN).background(Color(30, 30, 40))) {
            label = Text(text)
        }
        root.Column(Size.exact(600f, 60f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 100f)).background(Color(30, 30, 40))) {
            columnLabel = Text(text)
        }
        frame()
        log("row label ${label.w}x${label.h}, column label ${columnLabel.w}x${columnLabel.h}")
        expect(label.w > 150f, "Text in a short Row keeps its unwrapped width (got ${label.w})")
        near(label.w, columnLabel.w, "Row and Column measure the same text to the same width", 1.01f)
        screenshot("final")
    }

    StarUITestSuite.group("stack-nested-drift") {
        // Centre-aligned children with odd widths in a nested Column must keep a common centre line.
        val kids = mutableListOf<CustomPanelAPI>()
        lateinit var inner: CustomPanelAPI
        root.Column(Size.exact(301f, 400f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40.5f, 40f)).background(Color(30, 30, 40)),
            horizontalAlignment = Alignment.CenterHorizontally, spacing = 3f) {
            inner = Column(Size.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, spacing = 3f) {
                for (w in listOf(33f, 71f, 12f, 99f, 7f, 55f, 101f, 3f)) kids += Spacer(Size.exact(w, 9f), Modifier.background(Color(200, 120, 60)))
            }
        }
        frame()
        val centre = inner.cx
        for ((i, k) in kids.withIndex()) near(k.cx, centre, "child $i centre stays on the parent centre line", 1.01f)
        screenshot("final")
    }
}
