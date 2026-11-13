// Automatic re-layout: a StarUI layout container lays out again by itself when its children or a Text change.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import org.starficz.starui.Anchor
import org.starficz.starui.components.Column
import org.starficz.starui.components.Row
import org.starficz.starui.components.Spacer
import org.starficz.starui.components.Text
import org.starficz.starui.modifier.*
import org.starficz.starui.Overflow
import org.starficz.starui.requestLayout
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.Size
import org.starficz.starui.vanilla.children
import org.starficz.starui.vanilla.scrollPanel
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
        val col = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(BG), spacing = 5f) {
            Spacer(Size.exact(100f, 20f), Modifier.background(RED))
            Spacer(Size.exact(100f, 20f), Modifier.background(GREEN))
        }
        val plugin = col.plugin as StarUIPanelPlugin
        frame()
        near(col.h, 45f, "initial wrapped height")
        expect(plugin.layoutPassCount == 1, "one layout pass for the first frame (${plugin.layoutPassCount})")
        frame(); frame()
        expect(plugin.layoutPassCount == 1, "no layout work while nothing changes (${plugin.layoutPassCount})")

        val added = col.build { Spacer(Size.exact(150f, 30f), Modifier.background(BLUE)) }
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
        val again = col.build { Spacer(Size.exact(60f, 10f), Modifier.background(GREEN)) }
        frame()
        near(col.h, 10f, "re-adding after clearChildren() wraps the new child")
        near(col.w, 60f, "re-adding after clearChildren() wraps the new child width")
        near(again.relTop(col), 0f, "the new only child sits at the top")
        frame()
        screenshot("final")
    }

    StarUITestSuite.group("relayout-remove-middle-child") {
        // Stack children are anchored on their previous sibling; removing one in the middle used to leave the next
        // child anchored on a component that is no longer there, and the engine threw "May only anchor on siblings".
        lateinit var middle: CustomPanelAPI
        lateinit var last: CustomPanelAPI
        val col = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(BG), spacing = 5f) {
            Spacer(Size.exact(100f, 20f), Modifier.background(RED))
            middle = Spacer(Size.exact(100f, 20f), Modifier.background(GREEN))
            last = Spacer(Size.exact(100f, 20f), Modifier.background(BLUE))
        }
        frame()
        near(last.relTop(col), 50f, "the last child starts after two others")
        col.removeComponent(middle)
        val error = thrown { frame() }
        expect(error == null, "removing a middle child does not break the next layout (got $error)")
        near(last.relTop(col), 25f, "the last child moves up into the gap")
        near(col.h, 45f, "the Column shrinks")
        screenshot("final")
    }

    StarUITestSuite.group("relayout-text-change") {
        lateinit var label: LabelAPI
        lateinit var next: CustomPanelAPI
        val row = root.Row(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(BG), spacing = 5f) {
            label = Text("short")
            next = Spacer(Size.exact(20f, 20f), Modifier.background(RED))
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
        val outer = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.bottomRight.ofParent(20f, 30f)).background(BG), spacing = 5f) {
            Spacer(Size.exact(100f, 20f), Modifier.background(RED))
            inner = Row(Size.wrapContent(), spacing = 5f) { Spacer(Size.exact(30f, 30f), Modifier.background(GREEN)) }
        }
        frame()
        near(outer.w, 100f, "initial outer width")
        near(outer.h, 55f, "initial outer height")
        near(outer.position.x + outer.w, screenW - 20f, "initially pinned 20px from the right")

        inner.build { Spacer(Size.exact(200f, 60f), Modifier.background(BLUE)) }
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
        lateinit var sp: ScrollPanelAPI
        val content = root.Column(Size.exact(300f, 100f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(BG), spacing = 5f,
            overflow = Overflow.ScrollVertical) {
            Spacer(Size.exact(280f, 30f), Modifier.background(RED))
        }
        sp = content.scrollPanel!!
        frame()
        near(content.position.height, 100f, "initial content height: the viewport's (the content is shorter)")
        val contentPlugin = StarUIPanelPlugin.getAttached(content)!!
        StarUIContext.withPlugin(contentPlugin) {
            for (i in 0 until 5) content.Spacer(Size.exact(280f, 30f), Modifier.background(if (i % 2 == 0) BLUE else GREEN))
        }
        frame()
        near(content.position.height, 6 * 30f + 5 * 5f, "content added later grows the scrollable content")
        near(sp.position.height, 100f, "the scroll panel keeps its own size")
        sp.scrollToY(1000f)
        near(sp.yOffset, 205f - 100f, "the new content can be scrolled to")
        frame()
        screenshot("final")
    }

    StarUITestSuite.group("relayout-onlayout-semantics") {
        var layouts = 0; var firsts = 0; var leafLayouts = 0; var leafFirsts = 0; var nestedLayouts = 0
        lateinit var label: LabelAPI
        val col = root.Column(Size.wrapContent(),
            Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(BG).onLayout { layouts++ }.onFirstLayout { firsts++ }) {
            label = Text("leaf", modifier = Modifier.onLayout { leafLayouts++ }.onFirstLayout { leafFirsts++ })
            Row(Size.wrapContent(), Modifier.onLayout { nestedLayouts++ }) { Spacer(Size.exact(30f, 30f), Modifier.background(RED)) }
        }
        val plugin = col.plugin as StarUIPanelPlugin
        frame(); frame()
        expect(layouts == 1 && firsts == 1, "container: onLayout and onFirstLayout fire once for the first pass ($layouts, $firsts)")
        expect(leafLayouts == 1 && leafFirsts == 1, "leaf: onLayout and onFirstLayout fire once for the first pass ($leafLayouts, $leafFirsts)")
        expect(nestedLayouts == 1, "nested container: onLayout fires once for the first pass ($nestedLayouts)")

        col.build { Spacer(Size.exact(50f, 10f), Modifier.background(GREEN)) }
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
        lateinit var sizeLabel: LabelAPI
        val reporter = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 140f)).background(BG)
            .onLayout { passesSeen++; sizeLabel.text = "passes so far: at least one" }) {
            sizeLabel = Text("?")
        }
        frame(); frame(); frame()
        expect(passesSeen == 2, "a text change made in onLayout causes exactly one follow-up pass, then settles ($passesSeen)")
        near(reporter.w, sizeLabel.position.width, "and the container wraps the text set in onLayout")
        screenshot("final")
    }
}
