// SectionHeading (vanilla's addSectionHeading label) and Section (a heading over a bordered, padded column).
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.LayoutSize
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.Section
import org.starficz.starui.compose.components.SectionHeading
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.VanillaColors
import org.starficz.starui.core.getChildrenCopy

private fun sectionAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

fun registerSectionTests() {
    StarUITestSuite.group("section-heading") {
        val title = UIState("Fleet")
        lateinit var full: LabelAPI
        lateinit var wrapped: LabelAPI
        root.Column(LayoutSize.mixed(Axis.Exact(300f), Axis.Wrap()), sectionAt(40f, 40f), spacing = 6f) {
            full = SectionHeading(title.value)
            wrapped = SectionHeading("Left", Size.wrap(), alignment = Alignment.LMID)
        }
        frame()
        val first = full
        near(full.w, 300f, "a heading fills its column's width by default")
        expect(full.h in 14f..24f, "and is one line plus 3 px tall (${full.h})")
        expect(full.text == "Fleet", "with its text (${full.text})")
        expect(wrapped.w in 20f..80f, "Size.wrap() makes it as wide as its text plus vanilla's 5 px a side (${wrapped.w})")
        val bar = fraction(full.x + 2f, full.y + 2f, 30f, full.h - 4f) { it.r + it.g + it.b > 15 }
        expect(bar > 0.9f, "the label draws its own bar, even where there is no text (${bar})")

        title.value = "Fleet (3)"
        frame()
        expect(full === first && full.text == "Fleet (3)", "a rebuild updates the same label's text (${full.text})")
        screenshot("final")
    }

    StarUITestSuite.group("section-container") {
        lateinit var section: CustomPanelAPI
        lateinit var heading: LabelAPI
        lateinit var content: CustomPanelAPI
        root.Column(LayoutSize.mixed(Axis.Exact(320f), Axis.Wrap()), sectionAt(40f, 40f)) {
            section = Section("Cargo", xPadding = 10f, yPadding = 8f) {
                content = Spacer(PanelSize.exact(100f, 40f), color = VanillaColors.base)
            }
        }
        frame()
        heading = section.getChildrenCopy().first() as LabelAPI
        expect(heading.text == "Cargo", "the section starts with its heading (${heading.text})")
        near(section.w, 320f, "a section fills its column's width")
        near(heading.relTop(section), 0f, "the heading sits at the top")
        near(content.relTop(section), heading.h + 8f, "the content starts below it, after the top padding")
        near(content.relLeft(section), 10f, "and the side padding in")
        near(section.h, heading.h + 8f + 40f + 8f, "the section wraps its heading, padding and content")
        screenshot("final")
    }
}
