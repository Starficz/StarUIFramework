// An exact scale for vanilla ship displays (BoxedUIShipPreview.overwriteScale, done by ShipDisplayExactScalePatch):
// vanilla always draws at 95% of the fit and 3% of the panel height above centre; with an exact scale the ship
// draws at that scale, centred, and on whole pixels whatever the panel's size.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.ShipDisplay
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.BoxedUIShipPreview
import org.starficz.starui.internal.ShipDisplayExactScalePatch
import patchlib.api.PatchLib

/** The lit pixels of a component's on-screen rectangle (1 UI unit = 1 pixel in the suite's window). */
private class Lit(val bytes: ByteArray, val w: Int, val h: Int, val left: Int, val right: Int, val bottom: Int, val top: Int) {
    val width get() = right - left + 1
    val height get() = top - bottom + 1
    val centreY get() = (bottom + top) / 2f
    /** The top [rows] rows of the lit box: the hull, without the engine flames (vanilla animates those per ship). */
    fun crop(rows: Int): List<Int> {
        val out = ArrayList<Int>(width * rows)
        for (y in (top - rows + 1)..top) for (x in left..right) {
            val i = (y * w + x) * 4
            out += (bytes[i].toInt() and 255) shl 16 or ((bytes[i + 1].toInt() and 255) shl 8) or (bytes[i + 2].toInt() and 255)
        }
        return out
    }
}

private fun lit(c: UIComponentAPI): Lit {
    val w = Math.round(c.w); val h = Math.round(c.h)
    val bytes = NativeSmokeSupport.region(c)
    var left = w; var right = -1; var bottom = h; var top = -1
    for (y in 0 until h) for (x in 0 until w) {
        val i = (y * w + x) * 4
        val sum = (bytes[i].toInt() and 255) + (bytes[i + 1].toInt() and 255) + (bytes[i + 2].toInt() and 255)
        if (sum > 90) { if (x < left) left = x; if (x > right) right = x; if (y < bottom) bottom = y; if (y > top) top = y }
    }
    return Lit(bytes, w, h, left, right, bottom, top)
}

fun registerShipDisplayScaleTests() {
    StarUITestSuite.group("fix-shipdisplay-exact-scale") {
        val variant = Global.getSettings().getVariant("hammerhead_Balanced")
        val shown = linkedMapOf<String, BoxedUIShipPreview>()
        fun at(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))
        root.CustomPanel(PanelSize.exact(1280f, 720f), at(0f, 0f)) {
            fun display(name: String, x: Float, y: Float, w: Float, h: Float, scale: Float?) {
                val d = ShipDisplay(PanelSize.exact(w, h), at(x, y), variant = variant,
                    style = BoxedUIShipPreview.Style.NONE, showBorder = false)
                d.overwriteScale = scale
                shown[name] = d
            }
            display("vanilla", 20f, 40f, 400f, 300f, null)
            display("exact", 440f, 40f, 400f, 300f, 1f)
            display("half", 20f, 380f, 400f, 300f, 0.5f)
            display("exactOdd", 440f, 380f, 401f, 301f, 1f)
            display("exactTwin", 860f, 380f, 400f, 300f, 1f)
        }
        frame()
        frame()
        screenshot("final")

        val sprite = shown.getValue("exact").getShips().first().spriteAPI
        val vanilla = lit(shown.getValue("vanilla"))
        val exact = lit(shown.getValue("exact"))
        val half = lit(shown.getValue("half"))
        val odd = lit(shown.getValue("exactOdd"))
        log("sprite ${sprite.width}x${sprite.height}; lit widths vanilla ${vanilla.width} exact ${exact.width} half ${half.width} odd ${odd.width}")
        expect(exact.width > 20 && vanilla.width > 20, "both ships drew (vanilla ${vanilla.width} px, exact ${exact.width} px wide)")

        expect(exact.width <= sprite.width + 1, "at scale 1 the ship is no wider than its sprite (${exact.width} <= ${sprite.width})")
        near(exact.width * 0.95f, vanilla.width.toFloat(), "vanilla draws it at 95% of that", tolerance = 2f)
        near(half.width * 2f, exact.width.toFloat(), "scale 0.5 draws it half as wide", tolerance = 2f)

        // vanilla: 3% of the panel height above centre, at 95% scale; exact: centred
        near(vanilla.centreY - exact.centreY, 0.03f * 300f * 0.95f, "the exact ship sits 3% (x 0.95) lower: centred", tolerance = 1.5f)

        // odd panel sizes put vanilla's centre on a half pixel; snapped, the hull lands on the same pixels. Two identical
        // displays already differ in a few pixels (vanilla animates small parts per ship), so the twin sets the noise.
        fun differing(a: Lit, b: Lit): Int { val x = a.crop(120); val y = b.crop(120); return x.indices.count { x[it] != y.getOrNull(it) } }
        val noise = differing(exact, lit(shown.getValue("exactTwin")))
        val shifted = differing(exact, odd)
        log("hull pixels differing: identical twin $noise, odd-sized panel $shifted (of ${exact.width * 120})")
        expect(exact.width == odd.width && shifted <= noise + exact.width,
            "on a 401x301 panel the hull lands on the same pixels as on 400x300 ($shifted differ, an identical twin: $noise)")

        // the same comparison with the patch off: vanilla's half-pixel centre on the odd panel resamples the hull
        PatchLib.disablePatch(ShipDisplayExactScalePatch::class.java)
        try {
            frame()
            val unpatched = differing(lit(shown.getValue("exact")), lit(shown.getValue("exactOdd")))
            log("hull pixels differing with the patch off: odd-sized panel $unpatched")
            expect(unpatched > 10 * maxOf(shifted, 1), "without the patch the odd panel's hull is resampled ($unpatched differ vs $shifted with it)")
        } finally {
            PatchLib.enablePatch(ShipDisplayExactScalePatch::class.java)
        }
    }
}
