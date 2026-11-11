// BoxLayoutEngine geometry, and the zero-wrapper / BoxedUIElement contracts.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.ImageSize
import org.starficz.starui.compose.LayoutSize
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.components.Box
import org.starficz.starui.compose.components.Button
import org.starficz.starui.compose.components.Checkbox
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.Image
import org.starficz.starui.compose.components.ScrollPanel
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
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.BoxedUIElement
import org.starficz.starui.core.children
import org.starficz.starui.core.parent
import java.awt.Color

fun registerBoxTests() {
    StarUITestSuite.group("box-nine-alignments") {
        // Box 300x200 with asymmetric padding l10 t20 r30 b40; nine 20x20 children.
        val placed = mutableMapOf<Alignment, CustomPanelAPI>()
        val box = root.Box(LayoutSize.exact(300f, 200f),
            Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40))
                .padding(left = 10f, top = 20f, right = 30f, bottom = 40f)) {
            for (a in Alignment.values()) placed[a] = Spacer(PanelSize.exact(20f, 20f), Modifier.align(a).background(Color(200, 140, 60)))
        }
        frame()
        fun left(a: Alignment) = when (a) {
            Alignment.TL, Alignment.LMID, Alignment.BL -> 10f
            Alignment.TMID, Alignment.MID, Alignment.BMID -> 130f   // centre 150 shifted by (10-30)/2
            else -> 250f
        }
        fun top(a: Alignment) = when (a) {
            Alignment.TL, Alignment.TMID, Alignment.TR -> 20f
            Alignment.LMID, Alignment.MID, Alignment.RMID -> 80f    // centre 100 shifted up by (40-20)/2
            else -> 140f
        }
        for ((a, c) in placed) expectRect(c, box, left(a), top(a), 20f, 20f, "align $a")
        screenshot("final")
    }

    StarUITestSuite.group("box-wrap-and-fill") {
        lateinit var wide: CustomPanelAPI; lateinit var tall: CustomPanelAPI; lateinit var filler: CustomPanelAPI
        val box = root.Box(LayoutSize.wrap(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40))) {
            wide = Spacer(PanelSize.exact(80f, 20f), Modifier.background(Color(200, 60, 60)))
            tall = Spacer(PanelSize.exact(30f, 60f), Modifier.align(Alignment.BR).background(Color(60, 200, 60)))
            filler = Spacer(PanelSize.fill(), Modifier.background(Color(60, 60, 200, 80)))
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
        val col = root.Column(LayoutSize.exact(400f, 700f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).background(Color(30, 30, 40)), spacing = 4f) {
            made["Text"] = Text("Plain text")
            made["Image"] = Image("graphics/icons/cargo/supplies.png", ImageSize.exact(40f, 40f))
            made["Button"] = Button("Button")
            made["Checkbox"] = Checkbox("Checkbox", bind = flag)
            made["TextField"] = TextField(state, size = org.starficz.starui.compose.Size.exact(200f, 24f))
            made["ShipDisplay"] = ShipDisplay(PanelSize.exact(120f, 120f), variant = Global.getSettings().getVariant("hammerhead_Balanced"))
            made["ScrollPanel"] = ScrollPanel(PanelSize.mixed(Axis.Fill(), Axis.Exact(120f))) { Text("inside scroll") }
        }
        frame()
        val direct = col.children
        for ((name, made1) in made) {
            val actual = if (made1 is BoxedUIElement) made1.boxedElement else made1
            expect(direct.any { it === actual }, "$name's real component is a direct child of the Column")
            expect(actual.parent === col, "$name's parent is the Column itself (no wrapper)")
            expect(actual !is CustomPanelAPI, "$name is a native ${actual.javaClass.simpleName}, not a CustomPanel wrapper")
        }
        expect(direct.size == made.size, "Column holds exactly ${made.size} children (got ${direct.size})")
        expect((made["ScrollPanel"] as? BoxedUIElement)?.boxedElement is ScrollPanelAPI, "ScrollPanel wraps a vanilla ScrollPanelAPI")
        screenshot("final")
    }

    StarUITestSuite.group("contract-all-fonts") {
        // Regression: Combat AI Tweaks' "AI Config" Button(font = ORBITRON_20) crashed the game (NPE in the vanilla
        // label's setText) because measureTextSize used a font Starsector had never loaded as a paragraph font.
        val buttonFonts = setOf(org.starficz.starui.core.Font.ORBITRON_12_CONDENSED, org.starficz.starui.core.Font.VICTOR_10,
            org.starficz.starui.core.Font.VICTOR_14, org.starficz.starui.core.Font.ORBITRON_20, org.starficz.starui.core.Font.ORBITRON_20_BOLD,
            org.starficz.starui.core.Font.ORBITRON_24, org.starficz.starui.core.Font.ORBITRON_24_BOLD)
        val col = root.Column(LayoutSize.exact(900f, 760f), Modifier.anchor(Anchor.inside.topLeft.ofParent(20f, 20f)), spacing = 2f) {
            for (f in org.starficz.starui.core.Font.values()) {
                val err = thrown {
                    val (w, h) = org.starficz.starui.core.measureTextSize("Font $f", f)
                    expect(w > 0f && h > 0f, "measureTextSize with $f gives a real size (${w}x$h)")
                    Text("Font $f", font = f)
                }
                expect(err == null, "Text/measure with $f does not throw ($err)")
                if (f in buttonFonts) {
                    val berr = thrown { Button("Button $f", font = f) }
                    expect(berr == null, "Button with $f does not throw ($berr)")
                }
            }
        }
        val err = thrown { frame() }
        expect(err == null, "rendering every font does not throw ($err)")
        expect(col.children.isNotEmpty(), "font samples were added")
        screenshot("final")
    }

    StarUITestSuite.group("contract-boxed-container-ops") {
        // UIPanelContainerPatch must unwrap BoxedUIElements passed to vanilla container methods.
        lateinit var a: UIComponentAPI; lateinit var boxed: org.starficz.starui.core.BoxedUILabel; lateinit var c: UIComponentAPI
        val host = root.CustomPanel(PanelSize.exact(300f, 100f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f))) {
            a = Spacer(PanelSize.exact(10f, 10f))
            boxed = Text("boxed label")
            c = Spacer(PanelSize.exact(10f, 10f))
        }
        frame()
        val raw = boxed.boxedElement
        fun order() = (host as UIPanelAPI).children
        expect(order().indexOf(raw) == 1, "boxed label's real component is the middle child")

        // Vanilla quirk (UIPanel.sendToBottom): it is a no-op when the component is currently the LAST child, so
        // sendToBottom is exercised from the middle position, before bringComponentToTop.
        var err = thrown { host.sendToBottom(boxed) }
        expect(err == null, "sendToBottom(BoxedUILabel) does not throw ($err)")
        expect(order().firstOrNull() === raw, "sendToBottom moved the real label to the front")

        err = thrown { host.bringComponentToTop(boxed) }
        expect(err == null, "bringComponentToTop(BoxedUILabel) does not throw ($err)")
        expect(order().lastOrNull() === raw, "bringComponentToTop moved the real label to the end")

        host.sendToBottom(boxed)
        expect(order().lastOrNull() === raw, "documented vanilla quirk: sendToBottom on the last child does nothing")

        err = thrown { host.removeComponent(boxed) }
        expect(err == null, "removeComponent(BoxedUILabel) does not throw ($err)")
        expect(order().none { it === raw }, "removeComponent removed the real label")

        err = thrown { host.addComponent(boxed) }
        expect(err == null, "addComponent(BoxedUILabel) does not throw ($err)")
        expect(order().lastOrNull() === raw, "addComponent re-added the real label")
        expect(order().none { it === boxed }, "the Boxed wrapper itself never lands in the child list")
    }
}
