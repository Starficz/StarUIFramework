// The API clean-up: a control's own contentPadding (themeable) with Modifier.padding added on top, a ButtonGroup
// needing bound states, an untitled Section, and sizes that fill one axis at an exact other.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import org.starficz.starui.Anchor
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.Size
import org.starficz.starui.StarUITheme
import org.starficz.starui.vanilla.ButtonFont
import org.starficz.starui.vanilla.measureTextSize
import java.awt.Color

private fun shapeAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

fun registerApiShapeTests() {
    StarUITestSuite.group("api-content-padding") {
        val (textW, textH) = measureTextSize("Go", ButtonFont.ORBITRON_12_CONDENSED.font)
        lateinit var plain: ButtonAPI; lateinit var bare: ButtonAPI; lateinit var padded: ButtonAPI; lateinit var themed: ButtonAPI
        val fat = StarUITheme.Default.copy(button = StarUITheme.Default.button.copy(contentPadding = PaddingValues.all(20f)))
        root.Column(Size.wrapContent(), shapeAt(40f, 40f), spacing = 4f) {
            plain = Button("Go")
            bare = Button("Go", contentPadding = PaddingValues.Zero)
            padded = Button("Go", modifier = Modifier.padding(5f))
            Theme(fat) { themed = Button("Go") }
        }
        frame()
        near(plain.w, textW + 20f, "a button is its label plus the theme's content padding (10 a side)")
        near(plain.h, textH + 8f, "and 4 above and below")
        near(bare.w, textW, "contentPadding = Zero leaves just the label")
        near(padded.w, textW + 20f + 10f, "Modifier.padding adds to the content padding")
        near(themed.w, textW + 40f, "the theme sets the default content padding")
        screenshot("final")
    }

    StarUITestSuite.group("api-buttongroup-needs-bind") {
        val error = thrown { root.Column(Size.wrapContent(), shapeAt(40f, 40f)) { Checkbox("grouped", buttonGroup = ButtonGroup()) } }
        expect(error is IllegalArgumentException && error.message?.contains("bind") == true,
            "a button in a ButtonGroup without bind fails with a message naming bind (got $error)")
    }

    StarUITestSuite.group("api-section-untitled-and-fill-sizes") {
        lateinit var section: CustomPanelAPI
        lateinit var bar: CustomPanelAPI
        root.Column(Size.exact(300f, 200f), shapeAt(40f, 40f)) {
            section = Section(null, contentPadding = PaddingValues.all(6f)) {
                bar = Spacer(Size.fillMaxWidth(height = 12f), Modifier.background(Color(200, 140, 60)))
            }
        }
        frame()
        expect(section.getChildrenCopy2().none { it is LabelAPI }, "a Section with a null title has no heading")
        // plus the section's 1 px border on every side
        near(bar.relTop(section), 1f + 6f, "its content starts at the content padding, inside the border")
        near(bar.w, 300f - 2f - 12f, "Size.fillMaxWidth(height =) fills the width inside the border and padding")
        near(bar.h, 12f, "at the exact height")
        near(section.h, 2f + 12f + 12f, "and the section wraps it")
        screenshot("final")
    }
}
