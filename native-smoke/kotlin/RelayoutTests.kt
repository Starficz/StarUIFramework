// Automatic re-layout: a StarUI layout container lays out again by itself when its children or a Text change.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.CustomPanelAPI
import org.starficz.starui.compose.LayoutSize
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.Row
import org.starficz.starui.compose.components.ScrollPanel
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.onFirstLayout
import org.starficz.starui.compose.modifier.onLayout
import org.starficz.starui.compose.modifier.requestLayout
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.BoxedScrollPanel
import org.starficz.starui.core.BoxedUILabel
import org.starficz.starui.core.children
import org.starficz.starui.core.clearChildren
import java.awt.Color

private val BG = Color(30, 30, 40)
private val RED = Color(200, 60, 60)
private val GREEN = Color(60, 200, 60)
private val BLUE = Color(60, 60, 200)

/** Adds children to an existing StarUI container the way a builder would (the container manages them). */
private fun <T> CustomPanelAPI.build(block: CustomPanelAPI.() -> T): T =
    StarUIContext.withPlugin(plugin as StarUIPanelPlugin) { block() }

fun registerRelayoutTests() {
    StarUITestSuite.group("relayout-children-added-removed") {
        val col = root.Column(LayoutSize.wrap(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(BG), spacing = 5f) {
            Spacer(PanelSize.exact(100f, 20f), Modifier.background(RED))
            Spacer(PanelSize.exact(100f, 20f), Modifier.background(GREEN))
        }
        val plugin = col.plugin as StarUIPanelPlugin
        frame()
        near(col.h, 45f, "initial wrapped height")
        expect(plugin.layoutPassCount == 1, "one layout pass for the first frame (${plugin.layoutPassCount})")
        frame(); frame()
        expect(plugin.layoutPassCount == 1, "no layout work while nothing changes (${plugin.layoutPassCount})")

        val added = col.build { Spacer(PanelSize.exact(150f, 30f), Modifier.background(BLUE)) }
        near(col.h, 45f, "adding a child does not lay out immediately (it waits for the next rendered frame)")
        frame()
        near(col.h, 80f, "the Column re-wraps all three children without a manual call")
        near(col.w, 150f, "and re-wraps the new widest child")
        near(added.relTop(col), 50f, "the new child flows after the old ones")
        expect(plugin.layoutPassCount == 2, "exactly one more layout pass (${plugin.layoutPassCount})")
        screenshot("added")

        col.removeComponent(added)
        frame()
        near(col.h, 45f, "removing the child shrinks the Column again")
        near(col.w, 100f, "and its width")

        val first = col.children[0]
        col.bringComponentToTop(first)
        frame()
        near(first.relTop(col), 25f, "bringComponentToTop moves the child to the end of the flow")

        col.clearChildren()
        frame()
        val emptyH = col.h
        expect(emptyH < 45f, "clearChildren() re-lays out the now empty Column (height $emptyH)")
        val again = col.build { Spacer(PanelSize.exact(60f, 10f), Modifier.background(GREEN)) }
        frame()
        near(col.h, 10f, "re-adding after clearChildren() wraps the new child")
        near(col.w, 60f, "re-adding after clearChildren() wraps the new child width")
        near(again.relTop(col), 0f, "the new only child sits at the top")
        frame()
        screenshot("final")
    }

    StarUITestSuite.group("relayout-text-change") {
        lateinit var label: BoxedUILabel
        lateinit var next: CustomPanelAPI
        val row = root.Row(LayoutSize.wrap(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(BG), spacing = 5f) {
            label = Text("short")
            next = Spacer(PanelSize.exact(20f, 20f), Modifier.background(RED))
        }
        val plugin = row.plugin as StarUIPanelPlugin
        frame()
        val w0 = label.position.width
        near(next.relLeft(row), w0 + 5f, "the spacer starts right after the label")
        screenshot("before")

        label.text = "a considerably longer string than before"
        frame()
        val w1 = label.position.width
        expect(w1 > w0 + 50f, "the label re-measures to its new text ($w0 -> $w1)")
        near(next.relLeft(row), w1 + 5f, "the next sibling moves to stay after the label")
        near(row.w, w1 + 5f + 20f, "the Row re-wraps")
        screenshot("after")

        val passes = plugin.layoutPassCount
        label.text = label.text
        frame()
        expect(plugin.layoutPassCount == passes, "setting the same text again does not lay out (${plugin.layoutPassCount} vs $passes)")
    }

    StarUITestSuite.group("relayout-nested-propagates-and-reanchors") {
        lateinit var inner: CustomPanelAPI
        val outer = root.Column(LayoutSize.wrap(), Modifier.anchor(Anchor.inside.bottomRight.ofParent(20f, 30f)).background(BG), spacing = 5f) {
            Spacer(PanelSize.exact(100f, 20f), Modifier.background(RED))
            inner = Row(LayoutSize.wrap(), spacing = 5f) { Spacer(PanelSize.exact(30f, 30f), Modifier.background(GREEN)) }
        }
        frame()
        near(outer.w, 100f, "initial outer width")
        near(outer.h, 55f, "initial outer height")
        near(outer.position.x + outer.w, screenW - 20f, "initially pinned 20px from the right")

        inner.build { Spacer(PanelSize.exact(200f, 60f), Modifier.background(BLUE)) }
        frame()
        near(inner.w, 235f, "the inner Row re-wraps its new child")
        near(inner.h, 60f, "the inner Row grows to the taller child")
        near(outer.w, 235f, "the change travels up: the outer Column re-wraps the wider Row")
        near(outer.h, 85f, "the outer Column re-wraps the taller Row")
        near(outer.position.x + outer.w, screenW - 20f, "the top-level node stays pinned 20px from the right")
        near(outer.position.y, 30f, "and 30px above the bottom")
        expect((inner.plugin as StarUIPanelPlugin).layoutPassCount == 0, "the nested Row never runs a pass of its own; its parent lays it out")
        screenshot("final")
    }

    StarUITestSuite.group("relayout-scrollpanel-content") {
        lateinit var sp: BoxedScrollPanel
        sp = root.ScrollPanel(PanelSize.exact(300f, 100f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(BG), spacing = 5f) {
            Spacer(PanelSize.exact(280f, 30f), Modifier.background(RED))
        }
        frame()
        val content = sp.contentContainer
        near(content.position.height, 30f, "initial content height")
        val contentPlugin = StarUIPanelPlugin.getAttached(content)!!
        StarUIContext.withPlugin(contentPlugin) {
            for (i in 0 until 5) sp.Spacer(PanelSize.exact(280f, 30f), Modifier.background(if (i % 2 == 0) BLUE else GREEN))
        }
        frame()
        near(content.position.height, 6 * 30f + 5 * 5f, "content added later grows the scrollable content")
        near(sp.position.height, 100f, "the ScrollPanel keeps its own size")
        sp.scrollToY(1000f)
        near(sp.yOffset, 205f - 100f, "the new content can be scrolled to")
        frame()
        screenshot("final")
    }

    StarUITestSuite.group("relayout-onlayout-semantics") {
        var layouts = 0; var firsts = 0; var leafLayouts = 0; var leafFirsts = 0; var nestedLayouts = 0
        lateinit var label: BoxedUILabel
        val col = root.Column(LayoutSize.wrap(),
            Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(BG).onLayout { layouts++ }.onFirstLayout { firsts++ }) {
            label = Text("leaf", modifier = Modifier.onLayout { leafLayouts++ }.onFirstLayout { leafFirsts++ })
            Row(LayoutSize.wrap(), Modifier.onLayout { nestedLayouts++ }) { Spacer(PanelSize.exact(30f, 30f), Modifier.background(RED)) }
        }
        val plugin = col.plugin as StarUIPanelPlugin
        frame(); frame()
        expect(layouts == 1 && firsts == 1, "container: onLayout and onFirstLayout fire once for the first pass ($layouts, $firsts)")
        expect(leafLayouts == 1 && leafFirsts == 1, "leaf: onLayout and onFirstLayout fire once for the first pass ($leafLayouts, $leafFirsts)")
        expect(nestedLayouts == 1, "nested container: onLayout fires once for the first pass ($nestedLayouts)")

        col.build { Spacer(PanelSize.exact(50f, 10f), Modifier.background(GREEN)) }
        frame(); frame()
        expect(layouts == 2 && leafLayouts == 2 && nestedLayouts == 2, "onLayout fires again for the automatic re-layout ($layouts, $leafLayouts, $nestedLayouts)")
        expect(firsts == 1 && leafFirsts == 1, "onFirstLayout does not fire again ($firsts, $leafFirsts)")

        plugin.requestLayout()
        frame()
        expect(layouts == 3, "requestLayout() on the container schedules one pass ($layouts)")
        label.requestLayout()
        frame(); frame()
        expect(layouts == 4 && leafLayouts == 4, "UIComponentAPI.requestLayout() on a child reaches the top-level node ($layouts, $leafLayouts)")

        // A callback that changes layout input (a common pattern: a label showing a measured size) settles by itself.
        var passesSeen = 0
        lateinit var sizeLabel: BoxedUILabel
        val reporter = root.Column(LayoutSize.wrap(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 140f)).background(BG)
            .onLayout { passesSeen++; sizeLabel.text = "passes so far: at least one" }) {
            sizeLabel = Text("?")
        }
        frame(); frame(); frame()
        expect(passesSeen == 2, "a text change made in onLayout causes exactly one follow-up pass, then settles ($passesSeen)")
        near(reporter.w, sizeLabel.position.width, "and the container wraps the text set in onLayout")
        screenshot("final")
    }
}
