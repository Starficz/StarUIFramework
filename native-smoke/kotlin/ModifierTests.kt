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
import org.starficz.starui.compose.modifier.VanillaFrame
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
            .background(Color(220, 40, 40)).border(Color(40, 220, 40), 3f))
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

    StarUITestSuite.group("modifier-vanilla-border") {
        // vanilla's three sprite frames in use (title menu, codex, combat), and the plain line next to them
        val frames = VanillaFrame.entries.mapIndexed { i, frame ->
            frame to root.CustomPanel(Size.exact(200f, 120f), Modifier.anchor(Anchor.inside.topLeft.ofParent(60f + i * 240f, 60f))
                .border(frame))
        }
        root.CustomPanel(Size.exact(200f, 120f), Modifier.anchor(Anchor.inside.topLeft.ofParent(60f, 240f)).border(Color(40, 220, 40)))
        root.CustomPanel(Size.exact(200f, 120f), Modifier.anchor(Anchor.inside.topLeft.ofParent(300f, 240f)).border(Color(40, 220, 40), 3f))
        val big = root.CustomPanel(Size.exact(400f, 220f), Modifier.anchor(Anchor.inside.topLeft.ofParent(540f, 240f))
            .border(VanillaFrame.TitleMenu))
        frame(); frame()
        val lit = { c: Rgb -> c.r + c.g + c.b > 200 }
        for ((frame, panel) in frames) {
            val p = panel.position
            val edges = listOf(
                fraction(p.x, p.y + 20f, 4f, p.height - 40f, lit),                  // left
                fraction(p.x + p.width - 4f, p.y + 20f, 4f, p.height - 40f, lit),   // right
                fraction(p.x + 20f, p.y, p.width - 40f, 4f, lit),                   // bottom
                fraction(p.x + 20f, p.y + p.height - 4f, p.width - 40f, 4f, lit))   // top
            expect(edges.all { it > 0.1f }, "$frame draws along all four edges (lit fractions $edges)")
            val middle = fraction(p.x + 20f, p.y + 20f, p.width - 40f, p.height - 40f, lit)
            expect(middle < 0.01f, "$frame leaves the middle alone (lit fraction $middle)")
            val outside = fraction(p.x - 12f, p.y, 8f, p.height, lit)
            expect(outside < 0.01f, "$frame stays inside the component (lit fraction $outside)")
        }
        val b = big.position
        val farEdge = fraction(b.x + b.width - 4f, b.y + 20f, 4f, b.height - 40f, lit)
        expect(farEdge > 0.1f, "a bigger component's frame reaches its own right edge (lit fraction $farEdge)")
        screenshot("final")
    }

    StarUITestSuite.group("modifier-border-reserves-space") {
        // CSS's box model: a border takes its depth inside the component's edge, and padding is the gap inside it
        lateinit var lineChild: UIComponentAPI; lateinit var frameChild: UIComponentAPI; lateinit var exactChild: UIComponentAPI
        val line = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(60f, 60f))
            .border(Color(40, 220, 40), 3f).padding(5f)) {
            lineChild = Spacer(Size.exact(50f, 20f), Modifier.background(Color(220, 40, 40)))
        }
        val framed = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(200f, 60f))
            .border(VanillaFrame.TitleMenu).padding(7f)) {
            frameChild = Spacer(Size.exact(50f, 20f), Modifier.background(Color(220, 40, 40)))
        }
        val exact = root.Column(Size.exact(100f, 60f), Modifier.anchor(Anchor.inside.topLeft.ofParent(340f, 60f))
            .border(VanillaFrame.TitleMenu)) {
            exactChild = Spacer(Size.fillMaxSize(), Modifier.background(Color(220, 40, 40)))
        }
        frame(); frame()
        val depth = VanillaFrame.TitleMenu.depth
        near(depth, 8f, "TitleMenu's frame is 8 deep (its corner sprite)")
        near(lineChild.relLeft(line), 3f + 5f, "a 3 px line plus padding 5: the child starts 8 in")
        near(lineChild.relTop(line), 8f, "and 8 down")
        near(line.position.width, 50f + 2 * 8f, "a wrapping container grows by border and padding on both sides")
        near(frameChild.relLeft(framed), depth + 7f, "TitleMenu plus padding 7: the child starts 15 in, as vanilla's menu box does")
        near(framed.position.height, 20f + 2 * (depth + 7f), "and the wrapping container grows to match")
        near(exact.position.width, 100f, "an exact size stays as asked")
        near(exactChild.position.width, 100f - 2 * depth, "and its content gets the frame's depth less on each side")
        near(exactChild.position.height, 60f - 2 * depth, "on both axes")
        screenshot("final")
    }

    StarUITestSuite.group("modifier-border-corners") {
        // a thick translucent line: every corner filled, painted once (an overlap would blend twice and come out
        // brighter), nothing outside the component or inside the band
        val t = 8f
        val panel = root.CustomPanel(Size.exact(240f, 140f), Modifier.anchor(Anchor.inside.topLeft.ofParent(80f, 80f))
            .border(Color(40, 220, 40, 128), t))
        frame()
        val p = panel.position
        val edge = pixel(p.x + p.width / 2f, p.y + t / 2f)
        expect(edge.g > 60, "the line is drawn (mid-bottom edge $edge)")
        val corners = listOf(p.x to p.y, p.x + p.width - t to p.y, p.x to p.y + p.height - t, p.x + p.width - t to p.y + p.height - t)
        for ((i, c) in corners.withIndex()) {
            val (cx, cy) = c
            val odd = mutableListOf<Rgb>()
            // whole pixels: region() rounds, and a half-pixel offset lands on the next pixel out
            for (dy in 0 until t.toInt()) for (dx in 0 until t.toInt()) {
                val px = pixel(cx + dx, cy + dy)
                if (px != edge) odd += px
            }
            expect(odd.isEmpty(), "corner $i is filled once, the same colour as the edges (${odd.size} pixels differ: ${odd.take(3)})")
        }
        val outside = fraction(p.x - 6f, p.y - 6f, p.width + 12f, 5f) { it.g > 60 }
        expect(outside == 0f, "nothing below the component (fraction $outside)")
        val inside = fraction(p.x + t + 1f, p.y + t + 1f, p.width - 2 * t - 2f, p.height - 2 * t - 2f) { it.g > 60 }
        expect(inside == 0f, "nothing inside the band (fraction $inside)")
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
