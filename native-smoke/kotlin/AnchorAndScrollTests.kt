// Anchor system (inside / relative / my), top-level WrapContent re-anchoring, and ScrollPanel behaviour.

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import org.starficz.starui.Alignment
import org.starficz.starui.Anchor
import org.starficz.starui.components.Column
import org.starficz.starui.components.CustomPanel
import org.starficz.starui.components.Spacer
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.background
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.padding
import org.starficz.starui.Overflow
import org.starficz.starui.Size
import org.starficz.starui.components.scrollPanel
import java.awt.Color

private val CHILD = Color(200, 140, 60)

fun registerAnchorTests() {
    StarUITestSuite.group("anchor-inside-parent") {
        // 40x20 children in a 400x300 panel, spacing (10, 15) where the positioner takes it.
        val placed = linkedMapOf<String, Pair<Anchor.AnchorData, Pair<Float, Float>>>(
            "topLeft" to (Anchor.inside.topLeft.ofParent(10f, 15f) to (10f to 15f)),
            "topCenter" to (Anchor.inside.topCenter.ofParent(15f) to (180f to 15f)),
            "topRight" to (Anchor.inside.topRight.ofParent(10f, 15f) to (350f to 15f)),
            "centerLeft" to (Anchor.inside.centerLeft.ofParent(10f) to (10f to 140f)),
            "center" to (Anchor.inside.center.ofParent(10f, 15f) to (190f to 125f)),   // +x right, +y up
            "centerRight" to (Anchor.inside.centerRight.ofParent(10f) to (350f to 140f)),
            "bottomLeft" to (Anchor.inside.bottomLeft.ofParent(10f, 15f) to (10f to 265f)),
            "bottomCenter" to (Anchor.inside.bottomCenter.ofParent(15f) to (180f to 265f)),
            "bottomRight" to (Anchor.inside.bottomRight.ofParent(10f, 15f) to (350f to 265f)),
        )
        val made = linkedMapOf<String, CustomPanelAPI>()
        val host = root.CustomPanel(Size.exact(400f, 300f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40))) {
            for ((name, v) in placed) made[name] = Spacer(Size.exact(40f, 20f), Modifier.anchor(v.first).background(CHILD))
        }
        frame()
        for ((name, v) in placed) expectRect(made[name]!!, host, v.second.first, v.second.second, 40f, 20f, "inside.$name")
        screenshot("final")
    }

    StarUITestSuite.group("anchor-relative") {
        lateinit var ref: CustomPanelAPI
        val got = linkedMapOf<String, CustomPanelAPI>()
        val host = root.CustomPanel(Size.exact(500f, 400f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40))) {
            ref = Spacer(Size.exact(100f, 60f), Modifier.anchor(Anchor.inside.center.ofParent()).background(Color(60, 60, 200)))
            got["above.left"] = Spacer(Size.exact(40f, 20f), Modifier.anchor(Anchor.above.sibling(ref).matchingLeftEdge(5f)).background(CHILD))
            got["below.center"] = Spacer(Size.exact(40f, 20f), Modifier.anchor(Anchor.below.sibling(ref).matchingCenter(5f)).background(CHILD))
            got["leftOf.top"] = Spacer(Size.exact(40f, 20f), Modifier.anchor(Anchor.leftOf.sibling(ref).matchingTopEdge(5f)).background(CHILD))
            got["rightOf.bottom"] = Spacer(Size.exact(40f, 20f), Modifier.anchor(Anchor.rightOf.sibling(ref).matchingBottomEdge(5f)).background(CHILD))
            got["insidePrev.topLeft"] = Spacer(Size.exact(10f, 10f), Modifier.anchor(Anchor.inside.topLeft.ofPreviousComponent(3f, 4f)).background(Color(220, 220, 60)))
            got["my.bottomRight.toTopLeft"] = Spacer(Size.exact(30f, 30f), Modifier.anchor(Anchor.my.bottomRight.toTopLeft.ofParent()).background(Color(220, 60, 220)))
        }
        frame()
        val rl = ref.relLeft(host); val rt = ref.relTop(host)
        expectRect(ref, host, 200f, 170f, 100f, 60f, "reference (inside.center)")
        expectRect(got["above.left"]!!, host, rl, rt - 5f - 20f, 40f, 20f, "above.matchingLeftEdge(5)")
        expectRect(got["below.center"]!!, host, rl + 30f, rt + 60f + 5f, 40f, 20f, "below.matchingCenter(5)")
        expectRect(got["leftOf.top"]!!, host, rl - 5f - 40f, rt, 40f, 20f, "leftOf.matchingTopEdge(5)")
        expectRect(got["rightOf.bottom"]!!, host, rl + 100f + 5f, rt + 60f - 20f, 40f, 20f, "rightOf.matchingBottomEdge(5)")
        val rightOf = got["rightOf.bottom"]!!
        expectRect(got["insidePrev.topLeft"]!!, host, rightOf.relLeft(host) + 3f, rightOf.relTop(host) + 4f, 10f, 10f, "inside.topLeft.ofPreviousComponent(3,4)")
        val my = got["my.bottomRight.toTopLeft"]!!
        near(my.position.x + my.w, host.position.x, "my.bottomRight.toTopLeft: right edge on the parent's left edge")
        near(my.position.y, host.position.y + host.h, "my.bottomRight.toTopLeft: bottom edge on the parent's top edge")
        screenshot("final")
    }

    StarUITestSuite.group("anchor-toplevel-wrap-reanchor") {
        // A top-level WrapContent Column only knows its size after measuring, so its anchor must be re-applied.
        val col = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.bottomRight.ofParent(20f, 30f)).background(Color(30, 30, 40))) {
            Spacer(Size.exact(120f, 40f), Modifier.background(Color(200, 60, 60)))
            Spacer(Size.exact(80f, 50f), Modifier.background(Color(60, 200, 60)))
        }
        frame()
        near(col.w, 120f, "wrapped width")
        near(col.h, 90f, "wrapped height")
        near(col.position.x + col.w, screenW - 20f, "right edge 20px from the screen's right after re-anchoring")
        near(col.position.y, 30f, "bottom edge 30px above the screen's bottom after re-anchoring")
        screenshot("final")
    }
}

fun registerScrollTests() {
    StarUITestSuite.group("scrollpanel-measure-and-scroll") {
        val items = mutableListOf<CustomPanelAPI>()
        lateinit var sp: ScrollPanelAPI
        val content = root.Column(Size.exact(300f, 200f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)).padding(horizontal = 0f, vertical = 10f),
            horizontalAlignment = Alignment.CenterHorizontally, spacing = 5f, overflow = Overflow.ScrollVertical) {
            for (i in 0 until 20) items += Spacer(Size.exact(280f, 30f), Modifier.background(if (i % 2 == 0) Color(200, 60, 60) else Color(60, 60, 200)))
        }
        sp = content.scrollPanel!!
        frame()
        near(sp.position.width, 300f, "ScrollPanel width")
        near(sp.position.height, 200f, "ScrollPanel height")
        near(content.position.height, 20 * 30f + 19 * 5f + 20f, "content height = items + spacing + padding")
        near(items[0].relTop(content), 10f, "first item below the top padding")
        near(items[19].relTop(content), 10f + 19 * 35f, "last item at the end of the flow")
        near(items[0].relLeft(content), 10f, "centre-aligned 280px items in the 300px content", 1.01f)
        screenshot("top")

        sp.scrollToY(100f)
        near(sp.yOffset, 100f, "scrollToY(100)")
        sp.saveVerticalScrollState()
        sp.scrollToY(0f)
        near(sp.yOffset, 0f, "scrollToY(0)")
        sp.restoreVerticalScrollState()
        near(sp.yOffset, 100f, "restoreVerticalScrollState returns to the saved 100")
        sp.scrollToY(100000f)
        near(sp.yOffset, 715f - 200f, "scrollToY clamps to contentHeight - viewHeight")
        sp.setOffset(0f, 50f)
        near(sp.yOffset, 50f, "setOffset(0, 50)")
        frame(30)
        screenshot("scrolled")
    }
}
