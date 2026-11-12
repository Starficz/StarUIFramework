// Universal modifiers on native components: visual (pixels), sizing, lifecycle counts.

import org.starficz.starui.compose.modifier.margin
import org.starficz.starui.compose.modifier.padding
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.border
import org.starficz.starui.compose.modifier.onAdvance
import org.starficz.starui.compose.modifier.onLayout
import org.starficz.starui.compose.modifier.onPositionChanged
import org.starficz.starui.core.Anchor
import java.awt.Color

private fun Rgb.isRed() = r > 170 && g < 100 && b < 100
private fun Rgb.isGreen() = g > 170 && r < 120 && b < 120
private fun Rgb.isBlue() = b > 170 && r < 120 && g < 120

fun registerModifierVisualTests() {
    StarUITestSuite.group("modifier-background-border") {
        val panel = root.CustomPanel(Size.exact(100f, 80f), Modifier.anchor(Anchor.inside.topLeft.ofParent(100f, 100f))
            .background(Color(220, 40, 40)).border(3f, Color(40, 220, 40)))
        lateinit var label: UIComponentAPI
        root.CustomPanel(Size.exact(300f, 60f), Modifier.anchor(Anchor.inside.topLeft.ofParent(300f, 100f))) {
            label = Text("Native label with background", modifier = Modifier.background(Color(40, 40, 220))
                .anchor(Anchor.inside.center.ofParent()))
        }
        frame()
        val centre = pixel(panel.cx, panel.cy)
        expect(centre.isRed(), "CustomPanel background fills its centre (got $centre)")
        val leftEdge = fraction(panel.position.x - 2f, panel.cy - 5f, 5f, 10f) { it.isGreen() }
        expect(leftEdge > 0f, "border draws on the left edge (green fraction $leftEdge)")
        val inset = pixel(panel.position.x + 10f, panel.cy)
        expect(inset.isRed(), "border does not bleed 10px inside (got $inset)")
        val outside = pixel(panel.position.x - 15f, panel.cy)
        expect(!outside.isRed() && !outside.isGreen(), "nothing drawn 15px outside the panel (got $outside)")

        val blue = fraction(label.position.x, label.position.y, label.w, label.h) { it.isBlue() }
        expect(blue > 0.4f, "background modifier on a native label (no wrapper) covers its rect (blue fraction $blue)")
        screenshot("final")
    }

    StarUITestSuite.group("modifier-spacing-shapes") {
        // margin, padding and hitBoxPadding take the same three shapes: (all), (horizontal, vertical), sides by name
        lateinit var all: CustomPanelAPI; lateinit var pair: CustomPanelAPI; lateinit var sides: CustomPanelAPI
        val col = root.Column(Size.exact(300f, 200f),
            Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)).padding(left = 10f, top = 4f)) {
            all = Spacer(Size.exact(50f, 10f), Modifier.margin(3f).background(Color(200, 60, 60)))
            pair = Spacer(Size.exact(50f, 10f), Modifier.margin(6f, 2f).background(Color(60, 200, 60)))
            sides = Spacer(Size.exact(50f, 10f), Modifier.margin(left = 20f, bottom = 5f).background(Color(60, 60, 200)))
        }
        frame()
        near(all.relLeft(col), 10f + 3f, "padding(left =) plus margin(all): left")
        near(all.relTop(col), 4f + 3f, "padding(top =) plus margin(all): top")
        near(pair.relLeft(col), 10f + 6f, "margin(horizontal, vertical): left")
        near(pair.relTop(col), 4f + 3f + 10f + 3f + 2f, "margin(horizontal, vertical): below the first child and both margins")
        near(sides.relLeft(col), 10f + 20f, "margin(left =): only that side")
        near(sides.relTop(col), pair.relTop(col) + 10f + 2f, "margin with no top: straight below the second child's bottom margin")
        screenshot("final")
    }

    StarUITestSuite.group("modifier-lifecycle-counts") {
        var advances = 0; var layouts = 0; var moves = 0
        lateinit var label: UIComponentAPI
        root.CustomPanel(Size.exact(300f, 60f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f))) {
            label = Text("lifecycle", modifier = Modifier.onAdvance { advances++ }.onLayout { layouts++ }.onPositionChanged { moves++ }
                .anchor(Anchor.inside.center.ofParent()))
        }
        NativeSmokeSupport.advance(root, 10)
        expect(advances == 10, "onAdvance on a native label fires once per frame: 10 frames -> $advances")
        NativeSmokeSupport.render(root)
        NativeSmokeSupport.render(root)
        expect(layouts == 1, "onLayout fires exactly once across two renders ($layouts)")
        val before = moves
        label.position.setXAlignOffset(5f)
        expect(moves > before, "onPositionChanged fires when the native component moves ($before -> $moves)")
    }
}
