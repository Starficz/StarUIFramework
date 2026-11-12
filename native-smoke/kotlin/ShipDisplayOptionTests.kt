// ShipDisplay's options as vanilla implements them (title/ooOO/do in the 0.98.5a decompile): labels need a variant and
// a shipName, a fleetMember display has none (so style, labelColor and shipName with one are an error),
// scaleSmallerShips is vanilla's fixed per-hull-size table, and scanlines darkens the display.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.ShipDisplay
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.BoxedUIShipPreview
import org.starficz.starui.core.getChildrenCopy

private fun displayAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

/** Width of the lit part of a component's on-screen rectangle, and its summed brightness. */
private fun litStats(c: UIComponentAPI): Pair<Int, Long> {
    val w = Math.round(c.w); val h = Math.round(c.h)
    val bytes = NativeSmokeSupport.region(c)
    var left = w; var right = -1; var total = 0L
    for (y in 0 until h) for (x in 0 until w) {
        val i = (y * w + x) * 4
        val sum = (bytes[i].toInt() and 255) + (bytes[i + 1].toInt() and 255) + (bytes[i + 2].toInt() and 255)
        total += sum
        if (sum > 90) { if (x < left) left = x; if (x > right) right = x }
    }
    return Pair(maxOf(0, right - left + 1), total)
}

private fun labelTexts(display: BoxedUIShipPreview) =
    display.getChildrenCopy().filterIsInstance<LabelAPI>().map { it.text }.filter { it.isNotBlank() }

fun registerShipDisplayOptionTests() {
    StarUITestSuite.group("shipdisplay-fleetmember-rejects-label-options") {
        val member = Global.getSettings().createFleetMember(FleetMemberType.SHIP, "lasher_Assault")
        for ((name, call) in listOf<Pair<String, () -> Unit>>(
            "style" to { root.ShipDisplay(Size.exact(100f, 100f), fleetMember = member, style = BoxedUIShipPreview.Style.MINI) },
            "labelColor" to { root.ShipDisplay(Size.exact(100f, 100f), fleetMember = member, labelColor = java.awt.Color.RED) },
            "shipName" to { root.ShipDisplay(Size.exact(100f, 100f), fleetMember = member, shipName = "ISS Example") }
        )) {
            val error = thrown(call)
            expect(error is IllegalArgumentException && error.message?.contains("fleetMember") == true,
                "a fleetMember with $name fails, naming fleetMember (got $error)")
        }
        expect(thrown { root.ShipDisplay(Size.exact(100f, 100f), displayAt(40f, 40f), fleetMember = member) } == null,
            "a fleetMember alone is fine")
    }

    StarUITestSuite.group("shipdisplay-labels-need-shipname") {
        val lasher = Global.getSettings().getVariant("lasher_Assault")
        lateinit var named: BoxedUIShipPreview
        lateinit var mini: BoxedUIShipPreview
        lateinit var unnamed: BoxedUIShipPreview
        root.CustomPanel(Size.exact(800f, 200f), displayAt(0f, 0f)) {
            named = ShipDisplay(Size.exact(220f, 160f), displayAt(20f, 20f), variant = lasher, shipName = "ISS Example")
            mini = ShipDisplay(Size.exact(220f, 160f), displayAt(260f, 20f), variant = lasher, shipName = "ISS Example",
                style = BoxedUIShipPreview.Style.MINI)
            unnamed = ShipDisplay(Size.exact(220f, 160f), displayAt(500f, 20f), variant = lasher)
        }
        frame()
        val namedTexts = labelTexts(named)
        expect("ISS Example" in namedTexts, "NORMAL with a shipName shows the name ($namedTexts)")
        expect(namedTexts.size == 3, "and two more labels, hull class and designation ($namedTexts)")
        expect(labelTexts(mini) == listOf("ISS Example"), "MINI shows the name alone (${labelTexts(mini)})")
        expect(labelTexts(unnamed).isEmpty(), "without a shipName there are no labels (${labelTexts(unnamed)})")
        screenshot("final")
    }

    StarUITestSuite.group("shipdisplay-scale-smaller-and-scanlines") {
        val lasher = Global.getSettings().getVariant("lasher_Assault")
        lateinit var full: BoxedUIShipPreview
        lateinit var shrunk: BoxedUIShipPreview
        lateinit var dark: BoxedUIShipPreview
        root.CustomPanel(Size.exact(800f, 240f), displayAt(0f, 0f)) {
            full = ShipDisplay(Size.exact(200f, 200f), displayAt(20f, 20f), variant = lasher)
            shrunk = ShipDisplay(Size.exact(200f, 200f), displayAt(240f, 20f), variant = lasher, scaleSmallerShips = true)
            dark = ShipDisplay(Size.exact(200f, 200f), displayAt(460f, 20f), variant = lasher, scanlines = 1f)
        }
        frame()
        val (fullW, fullLight) = litStats(full)
        val (shrunkW, _) = litStats(shrunk)
        val (_, darkLight) = litStats(dark)
        expect(fullW > 40, "the plain display draws the lasher ($fullW px wide)")
        expect(shrunkW in (fullW * 0.4f).toInt()..(fullW * 0.6f).toInt(),
            "scaleSmallerShips = true draws a frigate at about half size ($shrunkW of $fullW px)")
        expect(darkLight < fullLight * 0.85, "scanlines = 1 darkens the display ($darkLight of $fullLight)")
        screenshot("final")
    }
}
