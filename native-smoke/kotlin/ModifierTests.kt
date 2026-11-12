// Universal modifiers on native components: visual (pixels), sizing, lifecycle counts.

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.compose.LayoutSize
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.border
import org.starficz.starui.compose.modifier.fillMaxWidth
import org.starficz.starui.compose.modifier.height
import org.starficz.starui.compose.modifier.onAdvance
import org.starficz.starui.compose.modifier.onLayout
import org.starficz.starui.compose.modifier.onPositionChanged
import org.starficz.starui.compose.modifier.width
import org.starficz.starui.core.Anchor
import java.awt.Color

private fun Rgb.isRed() = r > 170 && g < 100 && b < 100
private fun Rgb.isGreen() = g > 170 && r < 120 && b < 120
private fun Rgb.isBlue() = b > 170 && r < 120 && g < 120

fun registerModifierVisualTests() {
    StarUITestSuite.group("modifier-background-border") {
        val panel = root.CustomPanel(PanelSize.exact(100f, 80f), Modifier.anchor(Anchor.inside.topLeft.ofParent(100f, 100f))
            .background(Color(220, 40, 40)).border(Color(40, 220, 40), 3f))
        lateinit var label: UIComponentAPI
        root.CustomPanel(PanelSize.exact(300f, 60f), Modifier.anchor(Anchor.inside.topLeft.ofParent(300f, 100f))) {
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

    StarUITestSuite.group("modifier-sizing") {
        lateinit var fixed: CustomPanelAPI; lateinit var wide: CustomPanelAPI
        val col = root.Column(LayoutSize.exact(300f, 200f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)), xPadding = 10f) {
            fixed = Spacer(PanelSize.exact(10f, 10f), Modifier.width(120f).height(25f).background(Color(200, 60, 60)))
            wide = Spacer(PanelSize.exact(10f, 10f), Modifier.fillMaxWidth().background(Color(60, 200, 60)))
        }
        frame()
        near(fixed.w, 120f, "Modifier.width overrides the size argument")
        near(fixed.h, 25f, "Modifier.height overrides the size argument")
        near(wide.w, 280f, "Modifier.fillMaxWidth fills the Column's inner width")
        near(wide.h, 10f, "fillMaxWidth keeps the exact height")
        near(wide.relTop(col), 25f, "the second child flows below the resized first")
        screenshot("final")
    }

    StarUITestSuite.group("modifier-lifecycle-counts") {
        var advances = 0; var layouts = 0; var moves = 0
        lateinit var label: UIComponentAPI
        root.CustomPanel(PanelSize.exact(300f, 60f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f))) {
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
