// BoxLayoutEngine geometry, and the zero-wrapper / BoxedUIElement contracts.

import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.compose.components.Theme
import org.starficz.starui.core.scrollPanel
import org.starficz.starui.compose.Overflow
import com.fs.starfarer.api.Global
import org.starficz.starui.compose.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.ImageSize
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.Box
import org.starficz.starui.compose.components.Button
import org.starficz.starui.compose.components.Checkbox
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.Image
import org.starficz.starui.compose.components.ShipDisplay
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.components.TextField
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.align
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.padding
import org.starficz.starui.compose.runtime.StarUIManager
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.BoxedUIElement
import org.starficz.starui.core.children
import org.starficz.starui.core.renderShadows
import org.starficz.starui.core.shadowColor
import java.awt.Color

fun registerBoxTests() {
    StarUITestSuite.group("box-nine-alignments") {
        // Box 300x200 with asymmetric padding l10 t20 r30 b40; nine 20x20 children.
        val placed = mutableMapOf<Alignment, CustomPanelAPI>()
        val box = root.Box(Size.exact(300f, 200f),
            Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40))
                .padding(left = 10f, top = 20f, right = 30f, bottom = 40f)) {
            for (a in Alignment.entries) placed[a] = Spacer(Size.exact(20f, 20f), Modifier.align(a).background(Color(200, 140, 60)))
        }
        frame()
        fun left(a: Alignment) = when (a) {
            Alignment.TopLeft, Alignment.CenterLeft, Alignment.BottomLeft -> 10f
            Alignment.TopCenter, Alignment.Center, Alignment.BottomCenter -> 130f   // centre 150 shifted by (10-30)/2
            else -> 250f
        }
        fun top(a: Alignment) = when (a) {
            Alignment.TopLeft, Alignment.TopCenter, Alignment.TopRight -> 20f
            Alignment.CenterLeft, Alignment.Center, Alignment.CenterRight -> 80f    // centre 100 shifted up by (40-20)/2
            else -> 140f
        }
        for ((a, c) in placed) expectRect(c, box, left(a), top(a), 20f, 20f, "align $a")
        screenshot("final")
    }

    StarUITestSuite.group("box-wrap-and-fill") {
        lateinit var wide: CustomPanelAPI; lateinit var tall: CustomPanelAPI; lateinit var filler: CustomPanelAPI
        val box = root.Box(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40))) {
            wide = Spacer(Size.exact(80f, 20f), Modifier.background(Color(200, 60, 60)))
            tall = Spacer(Size.exact(30f, 60f), Modifier.align(Alignment.BottomRight).background(Color(60, 200, 60)))
            filler = Spacer(Size.fillMaxSize(), Modifier.background(Color(60, 60, 200, 80)))
        }
        frame()
        near(box.w, 80f, "WrapContent Box width = widest child")
        near(box.h, 60f, "WrapContent Box height = tallest child")
        expectRect(tall, box, 50f, 0f, 30f, 60f, "BR child")
        expectRect(filler, box, 0f, 0f, 80f, 60f, "FillMax child stretches to the wrapped bounds")
        screenshot("final")
    }
}

fun registerContractTests() {
    StarUITestSuite.group("contract-zero-wrapper") {
        NativeSmokeSupport.check(StarUIManager.inited, "StarUIManager.inited (ShipDisplay reflection) at onApplicationLoad")
        val state = UIState("typed")
        val flag = UIState(false)
        val made = linkedMapOf<String, UIComponentAPI>()
        val col = root.Column(Size.exact(400f, 700f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)), spacing = 4f) {
            made["Text"] = Text("Plain text")
            made["Image"] = Image("graphics/icons/cargo/supplies.png", Size.exact(40f, 40f))
            made["Button"] = Button("Button")
            made["Checkbox"] = Checkbox("Checkbox", bind = flag)
            made["TextField"] = TextField(state, size = org.starficz.starui.compose.Size.exact(200f, 24f))
            made["ShipDisplay"] = ShipDisplay(Size.exact(120f, 120f), variant = Global.getSettings().getVariant("hammerhead_Balanced"))
        }
        frame()
        val direct = col.children
        for ((name, made1) in made) {
            val actual = if (made1 is BoxedUIElement) made1.obf else made1
            expect(direct.any { it === actual }, "$name's real component is a direct child of the Column")
            expect(actual.parent === col, "$name's parent is the Column itself (no wrapper)")
            expect(actual !is CustomPanelAPI, "$name is a native ${actual.javaClass.simpleName}, not a CustomPanel wrapper")
        }
        expect(direct.size == made.size, "Column holds exactly ${made.size} children (got ${direct.size})")
        expect(made["Text"] is LabelAPI, "Text returns the vanilla LabelAPI itself")
        screenshot("final")
    }

    StarUITestSuite.group("contract-all-fonts") {
        // Regression: Combat AI Tweaks' "AI Config" Button(font = ORBITRON_20) crashed the game (NPE in the vanilla
        // label's setText) because measureTextSize used a font Starsector had never loaded as a paragraph font.
        val col = root.Column(Size.exact(900f, 760f), Modifier.anchor(Anchor.inside.topLeft.ofParent(20f, 20f)), spacing = 2f) {
            for (f in org.starficz.starui.core.Font.values()) {
                val err = thrown {
                    val (w, h) = org.starficz.starui.core.measureTextSize("Font $f", f)
                    expect(w > 0f && h > 0f, "measureTextSize with $f gives a real size (${w}x$h)")
                    Text("Font $f", font = f)
                }
                expect(err == null, "Text/measure with $f does not throw ($err)")
            }
            // every ButtonFont is a font a vanilla button can be made with (any other is a compile error now)
            for (f in org.starficz.starui.core.ButtonFont.values()) {
                val berr = thrown { Button("Button $f", font = f) }
                expect(berr == null, "Button with $f does not throw ($berr)")
            }
        }
        val err = thrown { frame() }
        expect(err == null, "rendering every font does not throw ($err)")
        expect(col.children.isNotEmpty(), "font samples were added")
        screenshot("final")
    }

    StarUITestSuite.group("contract-boxed-container-ops") {
        // UIPanelContainerPatch must unwrap BoxedUIElements (images, ship previews) passed to vanilla container methods.
        lateinit var a: UIComponentAPI; lateinit var boxed: org.starficz.starui.core.BoxedUIImage; lateinit var c: UIComponentAPI
        val host = root.CustomPanel(Size.exact(300f, 100f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f))) {
            a = Spacer(Size.exact(10f, 10f))
            boxed = Image("graphics/icons/cargo/supplies.png", Size.exact(20f, 20f))
            c = Spacer(Size.exact(10f, 10f))
        }
        frame()
        val raw = boxed.obf
        fun order() = (host as UIPanelAPI).children
        expect(order().indexOf(raw) == 1, "boxed image's real component is the middle child")

        var err = thrown { host.sendToBottom(boxed) }
        expect(err == null, "sendToBottom(BoxedUIImage) does not throw ($err)")
        expect(order().firstOrNull() === raw, "sendToBottom moved the real image to the front")

        err = thrown { host.bringComponentToTop(boxed) }
        expect(err == null, "bringComponentToTop(BoxedUIImage) does not throw ($err)")
        expect(order().lastOrNull() === raw, "bringComponentToTop moved the real image to the end")

        // Until 0.98a, sendToBottom did nothing when the component was the LAST child; 0.98.5a fixed that.
        host.sendToBottom(boxed)
        expect(order().firstOrNull() === raw, "sendToBottom on the last child moves it to the front (fixed in 0.98.5a)")
        host.bringComponentToTop(boxed)

        err = thrown { host.removeComponent(boxed) }
        expect(err == null, "removeComponent(BoxedUIImage) does not throw ($err)")
        expect(order().none { it === raw }, "removeComponent removed the real image")

        err = thrown { host.addComponent(boxed) }
        expect(err == null, "addComponent(BoxedUIImage) does not throw ($err)")
        expect(order().lastOrNull() === raw, "addComponent re-added the real image")
        expect(order().none { it === boxed }, "the Boxed wrapper itself never lands in the child list")
    }

    StarUITestSuite.group("contract-api-0985") {
        // children is UIPanelAPI.getChildrenCopy2: a snapshot that later changes to the panel do not touch.
        val col = root.Column(Size.exact(300f, 200f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f))) {
            Spacer(Size.exact(10f, 10f))
        }
        frame()
        val before = col.children
        col.Spacer(Size.exact(10f, 10f))
        expect(before.size == 1 && col.children.size == 2, "an earlier children list stays as it was (${before.size}, ${col.children.size})")

        // A label StarUI did not make (a plain vanilla para, moved into the Column) still asks the Column to lay out
        // again when its text changes: that is the LabelAPI.setText hook, not a wrapper.
        val temp = Global.getSettings().createCustom(400f, 100f, null).createUIElement(400f, 100f, false)
        val vanilla: LabelAPI = temp.addPara("x", 0f)
        col.addComponent(vanilla)
        frame()
        val plugin = col.plugin as StarUIPanelPlugin
        var passes = plugin.layoutPassCount
        vanilla.text = "a different text"
        frame()
        expect(plugin.layoutPassCount == passes + 1, "a new text on a vanilla label lays the Column out once (${plugin.layoutPassCount} vs $passes)")
        passes = plugin.layoutPassCount
        vanilla.text = "a different text"
        frame()
        expect(plugin.layoutPassCount == passes, "the same text again does not (${plugin.layoutPassCount} vs $passes)")

        // The scroll panel extras that ScrollPanelAPI still leaves out, set through the theme.
        lateinit var sp: ScrollPanelAPI
        lateinit var list: CustomPanelAPI
        root.CustomPanel(Size.exact(200f, 100f), Modifier.anchor(Anchor.inside.topLeft.ofParent(400f, 40f))) {
            Theme(StarUITheme.current.copy(scroll = StarUITheme.current.scroll.copy(renderShadows = false, shadowColor = Color.RED))) {
                list = Column(Size.exact(200f, 100f), overflow = Overflow.ScrollVertical) { Text("in") }
            }
            sp = list.scrollPanel!!
        }
        frame()
        expect(!sp.renderShadows, "renderShadows reads back false")
        expect(sp.shadowColor == Color.RED && sp.darkColor == Color.RED, "shadowColor is the API's darkColor")
        expect(list.children.size == 1 && list.parent === sp.contentContainer, "the builder's Text went into the Column in the content container")
        screenshot("final")
    }
}
