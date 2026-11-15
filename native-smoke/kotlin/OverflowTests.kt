// Overflow: scrolling Row / Column / Box / CustomPanel (the vanilla scroll panel holding the StarUI container), the
// viewport layout rules, scroll position across rebuilds, and render culling of off-screen children.

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.*
import org.starficz.starui.Alignment
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.children
import org.starficz.starui.components.scrollPanel
import java.awt.Color

private fun overflowAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))
private val OVF_BG = Color(30, 30, 40)
private fun stripe(i: Int) = Modifier.background(if (i % 2 == 0) Color(200, 60, 60) else Color(60, 60, 200))

fun registerOverflowTests() {
    StarUITestSuite.group("overflow-scroll-column") {
        val rows = UIState(20)
        val header = UIState(0)
        lateinit var list: CustomPanelAPI
        root.Column(Size.wrapContent(), overflowAt(40f, 40f), spacing = 5f) {
            Text("header ${header.value}")
            list = Column(Size.exact(300f, 200f), Modifier.background(OVF_BG), spacing = 5f, overflow = Overflow.ScrollVertical) {
                Spacer(Size.exact(500f, 30f), Modifier.background(Color(60, 200, 60)))   // wider than the viewport
                for (i in 1 until rows.value) Spacer(Size.exact(280f, 30f), stripe(i))
            }
        }
        frame()
        val sp = list.scrollPanel
        if (!expect(sp != null, "a scrolling Column has a scroll panel")) return@group
        sp!!
        near(sp.position.width, 300f, "the scroll panel is the call's size (width)")
        near(sp.position.height, 200f, "the scroll panel is the call's size (height)")
        expect(list.parent === sp.contentContainer, "the returned Column sits in the scroll panel's content container")
        near(list.position.height, 20 * 30f + 19 * 5f, "content height = rows + spacing")
        near(list.position.width, 300f, "content width stays the viewport's: the 500 px row is cut, not scrolled")
        near(sp.contentContainer.position.height, 695f, "the content container is the content's size")
        expect(list.children.size == 20, "the builder's components are in the returned Column (${list.children.size})")
        expect(root.Column(Size.exact(10f, 10f)).scrollPanel == null, "a Column without overflow has no scroll panel")
        screenshot("top")

        sp.scrollToY(150f)
        header.value = 1
        frame()
        expect(list.scrollPanel === sp, "rebuilding the enclosing Column keeps the scroll panel")
        near(sp.yOffset, 150f, "and its scroll position")

        rows.value = 5
        frame()
        near(list.position.height, 200f, "a short list's content is the viewport's height")
        near(sp.yOffset, 0f, "a shrunk list does not stay scrolled past its end")
        screenshot("short")
    }

    StarUITestSuite.group("overflow-scroll-row") {
        val cards = UIState(10)
        val strip = root.Row(Size.exact(400f, 100f), overflowAt(40f, 40f).background(OVF_BG), spacing = 10f,
            verticalAlignment = Alignment.CenterVertically, overflow = Overflow.ScrollHorizontal) {
            for (i in 0 until cards.value) Spacer(Size.exact(80f, 60f), stripe(i))
        }
        frame()
        val sp = strip.scrollPanel!!
        near(strip.position.width, 10 * 80f + 9 * 10f, "content width = cards + spacing")
        near(strip.position.height, 100f, "content height is the viewport's (the clipped axis)")
        near(strip.children[0].relTop(strip), 20f, "cards are centred in the viewport's height")
        sp.setOffset(-300f, 0f)
        frame()
        near(sp.xOffset, -300f, "scrolled 300 px right")
        screenshot("scrolled")

        cards.value = 4
        frame()
        near(strip.position.width, 400f, "four cards: the content is the viewport's width")
        near(sp.xOffset, 0f, "and the x offset is clamped back")
    }

    StarUITestSuite.group("overflow-fill-footer") {
        val n = UIState(2)
        lateinit var gap: UIComponentAPI
        lateinit var footer: UIComponentAPI
        val list = root.Column(Size.exact(300f, 200f), overflowAt(40f, 40f).background(OVF_BG), overflow = Overflow.ScrollVertical) {
            for (i in 0 until n.value) Spacer(Size.exact(280f, 30f), stripe(i))
            gap = Spacer(Size.fillMaxHeight(width = 10f))
            footer = Spacer(Size.exact(280f, 20f), Modifier.background(Color(60, 200, 60)))
        }
        frame()
        near(gap.position.height, 200f - 60f - 20f, "a fill spacer takes what is left of the viewport")
        near(footer.relTop(list) + 20f, 200f, "so the footer sits at the viewport's bottom")
        near(list.position.height, 200f, "the content is the viewport's height")
        screenshot("short")

        n.value = 10
        frame()
        near(gap.position.height, 0f, "once the list overflows the fill spacer is 0")
        near(list.position.height, 10 * 30f + 20f, "and the content is what the rows need")
        near(footer.relTop(list), 300f, "the footer follows the rows")
    }

    StarUITestSuite.group("overflow-scroll-box") {
        lateinit var fill: UIComponentAPI
        val map = root.Box(Size.exact(400f, 300f), overflowAt(40f, 40f), overflow = Overflow.Scroll) {
            fill = Spacer(Size.fillMaxSize(), Modifier.background(Color(20, 40, 20)))
            Spacer(Size.exact(1500f, 1200f), Modifier.background(Color(60, 60, 200)))
            Spacer(Size.exact(100f, 100f), Modifier.align(Alignment.BottomRight).background(Color(200, 60, 60)))
        }
        frame()
        val sp = map.scrollPanel!!
        near(map.position.width, 1500f, "content width = the big child")
        near(map.position.height, 1200f, "content height = the big child")
        near(fill.position.width, 1500f, "a fill child spans the whole content (width)")
        near(fill.position.height, 1200f, "a fill child spans the whole content (height)")
        sp.setOffset(-1100f, 900f)
        frame()
        near(sp.xOffset, -1100f, "x offset")
        near(sp.yOffset, 900f, "y offset")
        screenshot("bottom-right")

        lateinit var small: UIComponentAPI
        val centred = root.Box(Size.exact(400f, 300f), overflowAt(500f, 40f).background(OVF_BG), overflow = Overflow.Scroll) {
            small = Spacer(Size.exact(100f, 50f), Modifier.align(Alignment.Center).background(Color(200, 140, 60)))
        }
        frame()
        near(centred.position.width, 400f, "a small child: the content is the viewport (width)")
        near(small.relLeft(centred), 150f, "and it is centred in the viewport (x)")
        near(small.relTop(centred), 125f, "and it is centred in the viewport (y)")
    }

    StarUITestSuite.group("overflow-nested") {
        lateinit var strip: CustomPanelAPI
        val page = root.Column(Size.exact(300f, 200f), overflowAt(40f, 40f).background(OVF_BG), spacing = 5f, overflow = Overflow.ScrollVertical) {
            for (i in 0 until 3) Spacer(Size.exact(280f, 40f), stripe(i))
            strip = Row(Size.fillMaxWidth(height = 60f), spacing = 5f, overflow = Overflow.ScrollHorizontal) {
                for (i in 0 until 8) Spacer(Size.exact(70f, 50f), stripe(i + 1))
            }
            for (i in 0 until 3) Spacer(Size.exact(280f, 40f), stripe(i))
        }
        frame()
        val inner = strip.scrollPanel!!
        near(page.position.height, 6 * 40f + 60f + 6 * 5f, "the page's content includes the strip's 60 px")
        expect(inner.parent === page, "the strip's scroll panel is a child of the page")
        near(inner.position.width, 300f, "the strip fills the page's width")
        near(inner.position.height, 60f, "and is 60 px tall")
        near(strip.position.width, 8 * 70f + 7 * 5f, "the strip's content is its cards")
        screenshot("final")
    }

    StarUITestSuite.group("overflow-values") {
        val err = thrown { Overflow(AxisOverflow.Visible, AxisOverflow.Scroll) }
        expect(err is IllegalArgumentException && err.message?.contains("a scroll axis clips both axes") == true,
            "Overflow(Visible, Scroll) throws and says why (got $err)")
        expect(thrown { Overflow(AxisOverflow.Scroll, AxisOverflow.Visible) } is IllegalArgumentException, "so does Overflow(Scroll, Visible)")
        expect(Overflow(AxisOverflow.Clip, AxisOverflow.Scroll) == Overflow.ScrollVertical, "the shorthands are plain values")
        expect(Overflow.ScrollVertical.toString() == "ScrollVertical" &&
            Overflow(AxisOverflow.Clip, AxisOverflow.Visible).toString() == "Overflow(Clip, Visible)", "toString names them")
        val percent = root.Text("CR 70% of 100%", highlightedText = listOf("70%" to Color.YELLOW))
        expect(percent.text == "CR 70% of 100%", "a highlighted Text may contain % (vanilla formats it) (${percent.text})")
        val clipped = root.CustomPanel(Size.exact(100f, 100f), overflow = Overflow.Clip)
        expect(clipped.scrollPanel == null, "a clipping panel is a plain panel, no scroll panel")
    }

    StarUITestSuite.group("overflow-render-culling") {
        val rendered = HashSet<Int>()
        val list = root.Column(Size.exact(300f, 200f), overflowAt(40f, 40f).background(OVF_BG), overflow = Overflow.ScrollVertical) {
            for (i in 0 until 200) Row(Size.fillMaxWidth(height = 20f), stripe(i).onRender { rendered += i }) { Text("Row $i") }
        }
        // a plain Column inside the scrolling one: its rows are culled too
        val nestedRendered = HashSet<Int>()
        val outer = root.Column(Size.exact(300f, 200f), overflowAt(400f, 40f).background(OVF_BG), overflow = Overflow.ScrollVertical) {
            Column(Size.fillMaxWidth()) {
                for (i in 0 until 200) Row(Size.fillMaxWidth(height = 20f), stripe(i).onRender { nestedRendered += i }) { Text("Nested $i") }
            }
        }
        frame()
        rendered.clear(); nestedRendered.clear()
        frame(1)
        expect((0..9).all { it in rendered }, "the visible rows render ($rendered)")
        expect(rendered.size <= 12, "only the rows in view render: ${rendered.size} of 200")
        expect((0..9).all { it in nestedRendered } && nestedRendered.size <= 12,
            "rows of a Column nested in the scrolling one are culled too: ${nestedRendered.size} of 200")
        screenshot("top")

        list.scrollPanel!!.setOffset(0f, 2000f)
        outer.scrollPanel!!.setOffset(0f, 2000f)
        rendered.clear(); nestedRendered.clear()
        frame(1)
        expect(100 in rendered && 0 !in rendered && rendered.size <= 12, "scrolled to row 100: those render instead ($rendered)")
        expect(100 in nestedRendered && 0 !in nestedRendered, "and in the nested Column ($nestedRendered)")
        screenshot("scrolled")

        // outside any clipping container nothing is culled, even far off screen
        val offRendered = HashSet<Int>()
        root.Column(Size.exact(100f, 40f), overflowAt(40f, 2000f)) {
            Row(Size.fillMaxWidth(height = 20f), Modifier.onRender { offRendered += 1 }) {}
        }
        frame(1)
        expect(1 in offRendered, "a plain Column's off-screen child still renders (no culling outside overflow)")
    }

    StarUITestSuite.group("overflow-clip") {
        val red = Color(200, 60, 60)
        val green = Color(60, 200, 60)
        fun Rgb.red() = r > 170 && g < 100 && b < 100
        fun Rgb.green() = g > 170 && r < 100 && b < 100

        val box = root.Box(Size.exact(200f, 60f), overflowAt(40f, 40f), overflow = Overflow.Clip) {
            Spacer(Size.exact(300f, 120f), Modifier.background(red))
        }
        val clipX = root.Box(Size.exact(200f, 60f), overflowAt(40f, 250f), overflow = Overflow(AxisOverflow.Clip, AxisOverflow.Visible)) {
            Spacer(Size.exact(300f, 120f), Modifier.background(red))
        }
        val clipY = root.Box(Size.exact(200f, 60f), overflowAt(750f, 250f), overflow = Overflow(AxisOverflow.Visible, AxisOverflow.Clip)) {
            Spacer(Size.exact(300f, 120f), Modifier.background(red))
        }
        val outer = root.CustomPanel(Size.exact(200f, 200f), overflowAt(400f, 0f), overflow = Overflow.Clip) {
            Box(Size.exact(200f, 200f), Modifier.anchor(Anchor.inside.topLeft.ofParent(100f, 100f)), overflow = Overflow.Clip) {
                Spacer(Size.exact(400f, 400f), Modifier.background(green))
            }
        }
        frame()
        screenshot("final")
        val p = box.position
        expect(pixel(p.x + 100f, p.y + 30f).red(), "Clip: the child shows inside the box")
        expect(!pixel(p.x + 250f, p.y + 30f).red(), "Clip: cut right of the box")
        expect(!pixel(p.x + 100f, p.y - 30f).red(), "Clip: cut below the box")

        val q = clipX.position
        expect(!pixel(q.x + 250f, q.y + 30f).red(), "Overflow(Clip, Visible): cut right of the box")
        expect(pixel(q.x + 100f, q.y - 30f).red(), "Overflow(Clip, Visible): spills below the box")
        val v = clipY.position
        expect(pixel(v.x + 250f, v.y + 30f).red(), "Overflow(Visible, Clip): spills right of the box")
        expect(!pixel(v.x + 100f, v.y - 30f).red(), "Overflow(Visible, Clip): cut below the box")

        val o = outer.position
        expect(pixel(o.x + 150f, o.y + 50f).green(), "clip in clip: the overlap shows")
        expect(!pixel(o.x + 250f, o.y + 50f).green(), "clip in clip: cut by the outer clip (right)")
        expect(!pixel(o.x + 150f, o.y - 50f).green(), "clip in clip: cut by the outer clip (below)")
        expect(!pixel(o.x + 50f, o.y + 150f).green(), "clip in clip: nothing outside the inner box")

        // input on the cut-off part reaches nothing
        var buttonClicks = 0
        var spacerClicks = 0
        val clickBox = root.Column(Size.exact(100f, 60f), overflowAt(40f, 450f), overflow = Overflow.Clip) {
            Button("Wide button wide button", size = Size.exact(300f, 30f)) { buttonClicks++ }
            Spacer(Size.exact(300f, 30f), Modifier.background(red).onClick { spacerClicks++ })
        }
        frame()
        val c = clickBox.position
        fun clickAt(x: Float, y: Float) {
            Input.sendList(root, Input.move(x, y))
            NativeSmokeSupport.advance(root, 2)
            Input.sendList(root, Input.down(x, y))
            Input.sendList(root, Input.up(x, y))
            NativeSmokeSupport.advance(root, 2)
        }
        clickAt(c.x + 250f, c.y + 45f)
        clickAt(c.x + 250f, c.y + 15f)
        expect(buttonClicks == 0, "a click on the cut-off part of a button does nothing ($buttonClicks)")
        expect(spacerClicks == 0, "nor on the cut-off part of a StarUI onClick ($spacerClicks)")
        clickAt(c.x + 50f, c.y + 45f)
        clickAt(c.x + 50f, c.y + 15f)
        expect(buttonClicks == 1, "the visible part of the button still clicks ($buttonClicks)")
        expect(spacerClicks == 1, "and so does the visible part of the onClick ($spacerClicks)")
    }

    StarUITestSuite.group("overflow-wheel") {
        fun wheel(c: UIComponentAPI, notches: Int, shift: Boolean = false) {
            Input.sendList(root, Input.wheel(c.cx, c.position.y + c.position.height / 2f, notches, shift))
            NativeSmokeSupport.advance(root, 1)
        }

        // a horizontal strip: the plain wheel scrolls x
        val strip = root.Row(Size.exact(400f, 80f), overflowAt(40f, 40f).background(OVF_BG), spacing = 10f, overflow = Overflow.ScrollHorizontal) {
            for (i in 0 until 10) Spacer(Size.exact(80f, 60f), stripe(i))
        }
        // a 2D box: the wheel scrolls y, Shift+wheel x
        val map = root.Box(Size.exact(400f, 300f), overflowAt(40f, 150f), overflow = Overflow.Scroll) {
            Spacer(Size.exact(1500f, 1200f), Modifier.background(Color(60, 60, 200)))
        }
        // a vertical list: Shift keeps vanilla's 5x step
        val plain = root.Column(Size.exact(300f, 200f), overflowAt(500f, 40f).background(OVF_BG), overflow = Overflow.ScrollVertical) {
            for (i in 0 until 40) Spacer(Size.exact(280f, 30f), stripe(i))
        }
        // a list inside a scrolling page: the inner list first, then the page
        lateinit var inner: CustomPanelAPI
        val page = root.Column(Size.exact(300f, 200f), overflowAt(850f, 40f).background(OVF_BG), spacing = 5f, overflow = Overflow.ScrollVertical) {
            Spacer(Size.exact(280f, 40f), stripe(0))
            inner = Column(Size.fillMaxWidth(height = 120f), overflow = Overflow.ScrollVertical) {
                for (i in 0 until 10) Spacer(Size.exact(260f, 30f), stripe(i + 1))
            }
            for (i in 0 until 6) Spacer(Size.exact(280f, 40f), stripe(i))
        }
        frame()

        val stripSp = strip.scrollPanel!!
        wheel(stripSp, -1)
        near(stripSp.xOffset, -100f, "wheel down on a horizontal strip scrolls it 100 px right")
        near(stripSp.yOffset, 0f, "and not vertically")
        wheel(stripSp, 1)
        near(stripSp.xOffset, 0f, "wheel up scrolls back")
        wheel(stripSp, 1)
        near(stripSp.xOffset, 0f, "and stops at the start")

        val mapSp = map.scrollPanel!!
        wheel(mapSp, -1)
        near(mapSp.yOffset, 99f, "wheel down on a 2D box scrolls y (vanilla's step: a third of 300)")
        near(mapSp.xOffset, 0f, "not x")
        wheel(mapSp, -1, shift = true)
        near(mapSp.xOffset, -100f, "Shift+wheel scrolls x")
        near(mapSp.yOffset, 99f, "and leaves y")

        val plainSp = plain.scrollPanel!!
        wheel(plainSp, -1, shift = true)
        near(plainSp.yOffset, 5 * 66f, "Shift+wheel on a vertical list keeps vanilla's 5x step", 1.5f)

        val pageSp = page.scrollPanel!!
        val innerSp = inner.scrollPanel!!
        wheel(innerSp, -1)
        expect(innerSp.yOffset > 0f, "the wheel over the inner list scrolls the inner list (${innerSp.yOffset})")
        near(pageSp.yOffset, 0f, "not the page")
        var notches = 1
        while (innerSp.yOffset < 300f - 120f - 0.5f && notches < 20) { wheel(innerSp, -1); notches++ }
        near(innerSp.yOffset, 300f - 120f, "the inner list reaches its end ($notches notches)")
        near(pageSp.yOffset, 0f, "and the page has not moved meanwhile")
        val pageBefore = pageSp.yOffset
        wheel(innerSp, -1)
        expect(pageSp.yOffset > pageBefore, "at the inner list's end the wheel scrolls the page (${pageSp.yOffset})")
        screenshot("final")
    }

    StarUITestSuite.group("showcase-idle-layout") {
        // Every page of the showcase, one under the other in one scrolling page (the showcase itself shows one at a
        // time on a layer): built once, left idle, scrolled down and back. The pages read the player faction's
        // colours: a sector is needed (left in place: vanilla code that ran meanwhile may hold on to it)
        if (com.fs.starfarer.api.Global.getSector() == null) com.fs.starfarer.api.Global.setSector(com.fs.starfarer.campaign.CampaignEngine.getInstance())
        val pages = org.starficz.staruisamples.showcase.Showcase.pages
        val t0 = System.nanoTime()
        val pageColumn = root.Column(Size.exact(screenW, screenH), overflow = Overflow.ScrollVertical) {
            Column(Size.fillMaxWidth(), Modifier.padding(right = 14f), spacing = 12f) {
                for (page in pages) key(page.title) {
                    Text(page.title)
                    page.body(org.starficz.staruisamples.showcase.Cards(this, page.file))
                }
            }
        }
        frame(1)
        log("showcase: ${pages.size} pages, first build + layout: ${(System.nanoTime() - t0) / 1_000_000} ms")
        frame(10)
        val before = org.starficz.starui.runtime.StarUIPanelPlugin.layoutRequests
        org.starficz.starui.runtime.StarUIPanelPlugin.traceLayoutRequests = 5
        val t1 = System.nanoTime()
        for (i in 0 until 20) frame(1)
        log("20 idle showcase frames: ${(System.nanoTime() - t1) / 1_000_000} ms")
        org.starficz.starui.runtime.StarUIPanelPlugin.traceLayoutRequests = 0
        val idleRequests = org.starficz.starui.runtime.StarUIPanelPlugin.layoutRequests - before

        // the wrapped paragraph in the Text page's Wrapping card must not run into the label below it
        fun findLabel(c: com.fs.starfarer.api.ui.UIComponentAPI, prefix: String): com.fs.starfarer.api.ui.LabelAPI? {
            if (c is com.fs.starfarer.api.ui.LabelAPI && c.text.startsWith(prefix)) return c
            if (c is com.fs.starfarer.api.ui.UIPanelAPI) for (child in c.getChildrenCopy2()) findLabel(child, prefix)?.let { return it }
            return null
        }
        val para = findLabel(root, "This one fills the width of its column")
        val below = findLabel(root, "This one is exactly 180 wide")
        fun overlap() = if (para == null || below == null) 0f else (below.position.y + below.position.height) - para.position.y
        if (para != null && below != null) {
            log("showcase paragraph ${para.position.width}x${para.position.height} at y ${para.position.y}; label below top ${below.position.y + below.position.height}")
            expect(overlap() <= 0.5f, "showcase Text page: the label below starts under the wrapped paragraph " +
                "(paragraph ${para.position.width}x${para.position.height}, overlap ${overlap()})")
        } else expect(false, "showcase Text page labels found")

        // scroll the page down and back, as a player does: nothing may change size, and the paragraph stays apart
        val page = pageColumn.scrollPanel
        if (page != null && para != null && below != null) {
            val paraH = para.position.height
            val maxY = page.contentContainer.position.height - page.position.height
            log("showcase page: ${page.contentContainer.position.height.toInt()} tall")
            var y = 0f
            var step = 0
            while (y < maxY) {
                y = minOf(maxY, y + 600f); step++
                page.yOffset = y
                frame(2)
                if (step % 6 == 0) screenshot("scrolled-$step")
                expect(overlap() <= 0.5f, "after scrolling to $y the Text page's label below still starts under the paragraph (overlap ${overlap()})")
            }
            page.yOffset = 0f
            frame(2)
            near(para.position.height, paraH, "after scrolling down and back the paragraph has its first height")
        } else expect(false, "showcase page scroll panel and labels found (page=$page)")
        // nothing on an idle page changes: anything per frame is a bug (a layout every frame)
        expect(idleRequests <= 4L, "the idle showcase does not lay out every frame ($idleRequests requests in 20 frames; traces in the log)")
        screenshot("final")
        root.clearChildren()
    }

    StarUITestSuite.group("text-wrap-no-overlap") {
        // A wrapped paragraph above a label, laid out at several widths in turn: the paragraph's height must follow
        // its wrapping every time (a stale cached height made the next label overlap it).
        val width = UIState(500f)
        lateinit var para: com.fs.starfarer.api.ui.LabelAPI
        lateinit var below: com.fs.starfarer.api.ui.LabelAPI
        root.Column(Size.wrapContent(), overflowAt(40f, 40f), spacing = 4f) {
            Column(Size.exact(width.value, 300f), spacing = 2f) {
                para = Text("A Text with a fill width wraps onto as many lines as it needs, and its height follows. " +
                    "Resize the window and it wraps again.", Size.fillMaxWidth())
                below = Text("An exact width of 220 wraps here, whatever the column does.", Size.exact(220f, 30f))
            }
        }
        for (w in listOf(500f, 300f, 700f, 300f, 500f, 260f)) {
            width.value = w
            frame()
            val paraBottom = para.position.y
            val belowTop = below.position.y + below.position.height
            expect(belowTop <= paraBottom + 0.5f, "at width $w the label below starts under the paragraph (paragraph bottom $paraBottom, label top $belowTop)")
            expect(para.position.height >= 14f, "at width $w the paragraph has its lines (${para.position.height})")
        }
        // and the paragraph's height is what vanilla wraps it to at its width
        val h = para.position.height
        para.autoSizeToWidth(para.position.width)
        near(para.position.height, h, "the laid-out height equals vanilla's wrapped height", 1f)
        screenshot("final")
    }
}
