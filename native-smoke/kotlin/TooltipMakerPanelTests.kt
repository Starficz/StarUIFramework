// TooltipMakerPanel: any TooltipMakerAPI content as a StarUI component, made at the width it gets, made again on a
// width change, on a change of a state its content read (without running the builder around it), and when the
// builder runs again unless its inputs are equal.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.*
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.layout.VanillaElementEngine
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.getChildrenNonCopy
import org.starficz.starui.vanilla.onClick
import org.starficz.starui.vanilla.VanillaColors

private fun engineOf(panel: CustomPanelAPI) = StarUIPanelPlugin.getAttached(panel)!!.layoutEngine as VanillaElementEngine

/** The tooltip the panel holds now. */
private fun tooltipOf(panel: CustomPanelAPI) = engineOf(panel).current as TooltipMakerAPI

/** Every component below [panel], depth first. */
private fun allBelow(panel: UIPanelAPI): List<UIComponentAPI> =
    panel.getChildrenNonCopy().flatMap { listOf(it) + ((it as? UIPanelAPI)?.let(::allBelow) ?: emptyList()) }

private fun TestContext.click(target: UIComponentAPI) {
    Input.sendList(root, Input.move(target.cx, target.cy))
    Input.sendList(root, Input.down(target.cx, target.cy))
    Input.sendList(root, Input.up(target.cx, target.cy))
}

fun registerTooltipMakerPanelTests() {
    StarUITestSuite.group("tooltipmaker-panel") {
        val hazard = UIState(2)
        val columnWidth = UIState(400f)
        val tick = UIState(0)
        var outerRuns = 0
        val widths = ArrayList<Float>()
        val pressed = ArrayList<Any?>()
        var toolkitClicks = 0
        lateinit var panel: CustomPanelAPI
        lateinit var below: LabelAPI
        // the outer Box's builder reads the width, so a change gives the Column its new size
        root.Box(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f))) {
            Column(Size.mixed(Axis.Exact(columnWidth.value), Axis.WrapContent), spacing = 6f) {
                outerRuns++
                Text("tick ${tick.value}")
                panel = TooltipMakerPanel(Size.fillMaxWidth(), onButtonClick = { pressed += it }) { width ->
                    widths += width
                    addTitle("Survey")
                    addPara("Hazard rating: %s", 10f, VanillaColors.highlight, "${100 + 25 * hazard.value}%")
                    addButton("Send probe", "probe", 150f, 25f, 10f)
                    addButton("Toolkit", "toolkit", 150f, 25f, 10f).onClick { toolkitClicks++ }
                }
                below = Text("under the tooltip")
            }
        }
        frame(); frame()
        val first = tooltipOf(panel)
        near(panel.w, 400f, "the panel takes the column's width")
        near(widths.last(), 400f, "and the content is made at it (${widths.last()})")
        near(panel.h, first.heightSoFar, "the panel is as tall as what the content added (${panel.h})")
        expect(panel.h > 60f, "which is a title, a paragraph and two buttons (${panel.h})")
        near(below.position.y + below.position.height, panel.position.y - 6f, "the next component follows it in the column")
        val para = allBelow(first).filterIsInstance<LabelAPI>().first { it.text.startsWith("Hazard") }
        expect(para.text == "Hazard rating: 150%", "the paragraph shows the state's value (${para.text})")
        screenshot("built")

        // a state read in the content makes the content again, without running the builder around it
        val runsBefore = outerRuns
        hazard.value = 4
        frame(); frame()
        val second = tooltipOf(panel)
        expect(second !== first, "a state the content read makes the content again")
        expect(outerRuns == runsBefore, "without running the builder around it (ran ${outerRuns - runsBefore} more times)")
        val para2 = allBelow(second).filterIsInstance<LabelAPI>().first { it.text.startsWith("Hazard") }
        expect(para2.text == "Hazard rating: 200%", "with the new value (${para2.text})")

        // a new width makes it again at that width
        columnWidth.value = 300f
        frame(); frame()
        near(panel.w, 300f, "a narrower column narrows the panel")
        near(tooltipOf(panel).position.width, 300f, "and the content is made again at 300")

        // the builder around it running again makes it again (no inputs given)
        val third = tooltipOf(panel)
        tick.value = 1
        frame(); frame()
        expect(tooltipOf(panel) !== third, "without inputs, a run of the builder around it makes the content again")

        // buttons: addButton's data reaches onButtonClick; the toolkit's onClick works on a returned button
        val buttons = allBelow(tooltipOf(panel)).filterIsInstance<ButtonAPI>()
        expect(buttons.size == 2, "both buttons are in the content (${buttons.size})")
        click(buttons.first { it.text == "Send probe" })
        frame()
        expect(pressed == listOf<Any?>("probe"), "a click on an addButton button calls onButtonClick with its data ($pressed)")
        click(buttons.first { it.text == "Toolkit" })
        frame()
        expect(toolkitClicks == 1, "onClick from the vanilla toolkit works on a content button ($toolkitClicks)")
        screenshot("final")
    }

    StarUITestSuite.group("tooltipmaker-panel-inputs-and-guard") {
        val tick = UIState(0)
        val label = UIState("Alpha")
        lateinit var kept: CustomPanelAPI
        root.Column(Size.exact(360f, 400f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f))) {
            Text("tick ${tick.value}")
            val name = label.value
            kept = TooltipMakerPanel(Size.fillMaxWidth(), inputs = name) { addPara(name, 0f) }
        }
        frame(); frame()
        val first = tooltipOf(kept)
        tick.value = 1
        frame(); frame()
        expect(tooltipOf(kept) === first, "with equal inputs, a run of the builder around it keeps the content")
        label.value = "Beta"
        frame(); frame()
        val texts = allBelow(tooltipOf(kept)).filterIsInstance<LabelAPI>().map { it.text }
        expect(tooltipOf(kept) !== first && "Beta" in texts, "changed inputs make it again ($texts)")

        // a StarUI component called inside the content fails clearly, where the content is made
        lateinit var misuse: CustomPanelAPI
        val misuseColumn = root.Column(Size.exact(300f, 100f), Modifier.anchor(Anchor.inside.topLeft.ofParent(450f, 40f))) {
            misuse = TooltipMakerPanel(Size.fillMaxWidth()) { Text("not here") }
        }
        val error = thrown { engineOf(misuse).measure(300f, Float.MAX_VALUE) }
        expect(error is IllegalStateException && error.message!!.contains("TooltipMakerPanel"),
            "a StarUI call inside the content throws and names TooltipMakerPanel (${error?.message})")
        root.removeComponent(misuseColumn)
    }
}
