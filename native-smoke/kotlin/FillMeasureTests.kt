// Column/Row measure each child at the size it will be given: a fill child at its share of the main axis, an exact
// child at its exact size, and a child filling the cross axis of a wrap container at its siblings' cross size (it never
// widens the container). Wrapping text shows the difference: measured too wide, it reports too few lines.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.LayoutSize
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.Row
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.core.Anchor
import java.awt.Color

private val MEASURE_BG = Color(30, 30, 45)
private const val LONG_TEXT =
    "This paragraph is long enough to need several lines once it is squeezed into a narrow column of a wider row."

private fun placeAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

fun registerFillMeasureTests() {
    StarUITestSuite.group("fix-fill-children-measured-at-their-share") {
        lateinit var oneLine: LabelAPI
        root.Column(LayoutSize.wrap(), placeAt(40f, 20f)) { oneLine = Text("One line") }

        val texts = mutableListOf<LabelAPI>()
        val columns = mutableListOf<CustomPanelAPI>()
        // The fixed spacer comes last: the fill columns must not be measured as if it took no room.
        val row = root.Row(LayoutSize.mixed(Axis.Exact(600f), Axis.Wrap()), placeAt(40f, 60f).background(MEASURE_BG), spacing = 10f) {
            repeat(3) {
                columns += Column(modifier = Modifier.background(Color(60, 40, 40))) { texts += Text(LONG_TEXT) }
            }
            Spacer(PanelSize.exact(60f, 10f), Modifier.background(Color.GRAY))
        }
        frame()
        val share = (600f - 60f - 3 * 10f) / 3f
        for (i in 0 until 3) {
            near(columns[i].w, share, "column $i gets a third of what is left")
            expect(texts[i].w <= columns[i].w + 0.5f, "text $i fits its column (${texts[i].w} <= ${columns[i].w})")
            expect(texts[i].h > oneLine.h * 1.5f, "text $i wraps onto several lines (${texts[i].h} vs one line ${oneLine.h})")
            expect(columns[i].h >= texts[i].h - 0.5f, "column $i is as tall as its text (${columns[i].h} >= ${texts[i].h})")
        }
        expect(row.h >= texts[0].h - 0.5f, "the wrap-height Row is as tall as the wrapped text (${row.h})")
        screenshot("final")
    }

    StarUITestSuite.group("fix-exact-children-measured-at-their-size") {
        lateinit var oneLine: LabelAPI
        root.Column(LayoutSize.wrap(), placeAt(40f, 20f)) { oneLine = Text("One line") }

        lateinit var text: LabelAPI
        val row = root.Row(LayoutSize.mixed(Axis.Exact(600f), Axis.Wrap()), placeAt(40f, 60f).background(MEASURE_BG)) {
            text = Text(LONG_TEXT, Size.mixed(Axis.Exact(150f), Axis.Wrap()))
        }
        frame()
        near(text.w, 150f, "the text is its exact width")
        expect(text.h > oneLine.h * 1.5f, "and wraps to that width, not the Row's (${text.h} vs one line ${oneLine.h})")
        expect(row.h >= text.h - 0.5f, "the wrap-height Row is as tall as the wrapped text (${row.h} >= ${text.h})")
        screenshot("final")
    }

    StarUITestSuite.group("fix-cross-fill-does-not-widen-wrap-container") {
        lateinit var oneLine: LabelAPI
        root.Column(LayoutSize.wrap(), placeAt(40f, 20f)) { oneLine = Text("One line") }

        lateinit var text: LabelAPI
        val col = root.Column(LayoutSize.wrap(), placeAt(40f, 60f).background(MEASURE_BG)) {
            Spacer(PanelSize.exact(200f, 10f), Modifier.background(Color.GRAY))
            text = Text(LONG_TEXT, Size.fillX())
        }
        frame()
        near(col.w, 200f, "the wrap Column is as wide as its widest non-fill child")
        near(text.w, 200f, "the fill text takes that width")
        expect(text.h > oneLine.h * 1.5f, "and wraps to it (${text.h} vs one line ${oneLine.h})")
        near(col.h, 10f + text.h, "the Column is as tall as the spacer plus the wrapped text")

        // Only fill children: nothing to take the width from, so the Column still collapses (and warns), as before.
        val allFill = root.Column(LayoutSize.wrap(), placeAt(300f, 60f)) { Text("only fill", Size.fillX()) }
        frame()
        near(allFill.w, 0f, "a wrap Column of only fill children has no width to give them")
        screenshot("final")
    }
}
