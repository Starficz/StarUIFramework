// Text(overflow = TextOverflow.Ellipsis): one line cut to its room with "...", the full text in a tooltip while cut;
// ProgressBar's text as the whole label; Modifier.tooltip on a button showing while it is disabled.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.BoxedUISlider
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import org.starficz.starui.vanilla.VanillaColors
import org.starficz.starui.vanilla.hasTooltip
import org.starficz.starui.vanilla.measureTextSize

private fun overflowAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))
private val width120 = Size.mixed(Axis.Exact(120f), Axis.WrapContent)

fun registerTextOverflowTests() {
    StarUITestSuite.group("text-ellipsis") {
        val long = "ISS Korolev Dreaming of Distant Shores"
        val oneLine = measureTextSize(long).second
        val name = UIState(long)
        lateinit var exact: LabelAPI; lateinit var fits: LabelAPI; lateinit var inRow: LabelAPI
        lateinit var ownTip: LabelAPI; lateinit var reactive: LabelAPI; lateinit var highlighted: LabelAPI
        lateinit var row: CustomPanelAPI
        val column = root.Column(Size.exact(400f, 300f), overflowAt(40f, 40f), spacing = 5f) {
            exact = Text(long, width120, overflow = TextOverflow.Ellipsis)
            fits = Text("Short", width120, overflow = TextOverflow.Ellipsis)
            // a wrapping Text gets what its Row has left: 100 of 200
            row = Row(Size.exact(200f, 20f)) {
                Spacer(Size.exact(100f, 20f))
                inRow = Text(long, overflow = TextOverflow.Ellipsis)
            }
            ownTip = Text(long, width120, Modifier.tooltip("own tooltip"), overflow = TextOverflow.Ellipsis)
            Column { reactive = Text(name.value, width120, overflow = TextOverflow.Ellipsis) }
            highlighted = Text("Supplies: 1200 of 5000 still needed", width120,
                highlightedText = listOf("1200" to VanillaColors.highlight), overflow = TextOverflow.Ellipsis)
        }
        frame(); frame()

        expect(exact.text.endsWith("...") && exact.text.length < long.length, "a long text is cut with ... ('${exact.text}')")
        expect(measureTextSize(exact.text).first <= 120f, "and the cut text fits its 120 px (${measureTextSize(exact.text).first})")
        near(exact.position.width, 120f, "the Text keeps its exact width")
        near(exact.position.height, oneLine, "and stays one line high")
        expect(exact.hasTooltip(), "a cut text carries the full text as a tooltip")
        expect(fits.text == "Short" && !fits.hasTooltip(), "a text that fits is shown whole, with no tooltip ('${fits.text}')")
        expect(inRow.text.endsWith("...") && inRow.position.width <= 100.5f,
            "a wrapping Text is cut to the room its Row has left ('${inRow.text}', ${inRow.position.width} wide)")
        near(row.position.width, 200f, "and the Row keeps its size")
        expect(StarUIPanelPlugin.getAttached(ownTip)?.tooltipAppliedThisBuild == true && ownTip.text.endsWith("..."),
            "a cut Text with its own tooltip keeps that tooltip")
        expect(highlighted.text.endsWith("..."), "a highlighted text is cut too ('${highlighted.text}')")

        val plugin = column.plugin as StarUIPanelPlugin
        val passes = plugin.layoutPassCount
        frame(); frame()
        expect(plugin.layoutPassCount == passes, "cut texts settle: no layout pass while nothing changes (${plugin.layoutPassCount - passes})")

        name.value = "Short name"
        frame(); frame()
        expect(reactive.text == "Short name" && !reactive.hasTooltip(), "a rebuild with a short text shows it whole, tooltip gone ('${reactive.text}')")
        name.value = long
        frame(); frame()
        expect(reactive.text.endsWith("...") && reactive.hasTooltip(), "and a long one again cuts it, with the tooltip ('${reactive.text}')")

        exact.text = "Another very long name for a ship of the line"
        frame(); frame()
        expect(exact.text.startsWith("Another") && exact.text.endsWith("..."), "a setText from code is cut the same way ('${exact.text}')")

        // the cut itself, with 10 px per character
        val tenEach = { s: String -> s.length * 10f }
        expect(ellipsize("abcdef", 60f, tenEach) == "abcdef", "ellipsize: a text that fits stays")
        expect(ellipsize("abcdefg", 50f, tenEach) == "ab...", "ellipsize: the longest start that fits with ...")
        expect(ellipsize("ab  cdefg", 60f, tenEach) == "ab...", "ellipsize: spaces before the ... are dropped")
        expect(ellipsize("abcdef", 20f, tenEach) == "", "ellipsize: nothing when not even ... fits")
        screenshot("final")
    }

    StarUITestSuite.group("progressbar-label") {
        lateinit var labelled: BoxedUISlider; lateinit var bare: BoxedUISlider; lateinit var vanillaValue: BoxedUISlider
        lateinit var changing: BoxedUISlider
        val text = UIState<String?>(null)
        root.Column(Size.exact(300f, 120f), overflowAt(40f, 40f), spacing = 5f) {
            labelled = ProgressBar(4200f, Size.fillMaxWidth(height = 20f), valueRange = 0f..5000f, text = "4,200 / 5,000")
            bare = ProgressBar(30f, Size.fillMaxWidth(height = 20f))
            vanillaValue = ProgressBar(30f, Size.fillMaxWidth(height = 20f), text = "Crew", configure = { setShowLabelOnly(false) })
            Column(Size.fillMaxWidth()) { changing = ProgressBar(50f, Size.fillMaxWidth(height = 20f), text = text.value) }
        }
        frame()
        expect(labelled.isShowLabelOnly && !labelled.isShowNoText, "a bar with a text shows only that text")
        expect(bare.isShowNoText, "a bar without one shows nothing")
        expect(!vanillaValue.isShowLabelOnly, "configure can bring vanilla's value back")
        text.value = "Now labelled"
        val before = changing
        frame()
        expect(changing === before && changing.isShowLabelOnly && !changing.isShowNoText,
            "a reused bar that gets a text shows it (reused: ${changing === before})")
        screenshot("final")
    }

    StarUITestSuite.group("tooltip-disabled-button") {
        lateinit var withTip: ButtonAPI; lateinit var without: ButtonAPI
        root.Row(Size.wrapContent(), overflowAt(40f, 40f), spacing = 5f) {
            withTip = Button("Charter", modifier = Modifier.tooltip("Needs 40 more supplies."))
            without = Button("Plain")
        }
        withTip.isEnabled = false
        frame()
        expect(withTip.invoke("isShowTooltipWhileInactive") == true, "a button's Modifier.tooltip shows while it is disabled")
        expect(without.invoke("isShowTooltipWhileInactive") == false, "a button without one is left as vanilla makes it")
    }
}
