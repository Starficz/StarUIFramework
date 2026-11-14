// The recipes of the starsector-starui skill (skill/starsector-starui/references/recipes.md), compiled and run here so
// the skill never ships code that was not built and exercised. Keep the two in step: a change to a recipe changes
// both files.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.opengl.GL11
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.Font
import org.starficz.starui.vanilla.VanillaColors
import org.starficz.starui.vanilla.getChildrenNonCopy
import org.starficz.starui.vanilla.withAlpha
import org.starficz.starui.window.*
import java.awt.Color

// --- recipe: change row ---

/** "Label ........ old -> new": the old value gray and struck through, the new one in highlight. */
fun UIPanelAPI.ChangeRow(label: String, old: String, new: String) {
    Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        FlexSpacer()
        Text(old, color = VanillaColors.gray, modifier = Modifier.onRender { alpha ->
            // the strike line: text colour, 1 px, 2 px longer than the digits on each side; y up, from the bottom edge
            val lineY = (y + height / 2f).toInt().toFloat()
            val c = VanillaColors.text
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glColor4f(c.red / 255f, c.green / 255f, c.blue / 255f, alpha)
            GL11.glRectf(x - 2f, lineY, x + width + 2f, lineY + 1f)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
        })
        Text(" -> ", color = VanillaColors.gray)
        Text(new, color = VanillaColors.highlight)
    }
}

// --- recipe: guarded button ---

/**
 * A button that stays in place when it is blocked: [reason] non-null greys it out and shows the reason in red on a
 * line under it whose 20 px are reserved also while the button is enabled, so nothing below it jumps.
 */
fun UIPanelAPI.GuardedButton(
    text: String, reason: String?, size: Size = Size.exact(120f, 20f), backgroundColor: Color = VanillaColors.dark,
    tooltip: String? = null, onClick: () -> Unit
): ButtonAPI {
    lateinit var button: ButtonAPI
    Column(spacing = 3f) {
        button = Button(text, size, backgroundColor = backgroundColor,
            modifier = if (tooltip != null) Modifier.tooltip(tooltip) else Modifier) { onClick() }
        button.isEnabled = reason == null
        // its own width (the reason may be wider than the button), a fixed 20 px height
        Box(Size.mixed(Axis.WrapContent, Axis.Exact(20f))) {
            if (reason != null) Text(reason, color = VanillaColors.negative, modifier = Modifier.align(Alignment.CenterLeft))
        }
    }
    return button
}

// --- recipe: cost button ---

/**
 * A button with its price on the face: icon, label, amount (red when the player is short). The face is a Row laid
 * over an empty button; the button takes the click under it.
 */
fun UIPanelAPI.CostButton(
    label: String, iconSprite: String, amount: Int, affordable: Boolean, width: Float = 140f,
    cutStyle: CutStyle = CutStyle.ALL, backgroundColor: Color = VanillaColors.dark, onClick: () -> Unit
): ButtonAPI {
    lateinit var button: ButtonAPI
    Box(Size.exact(width, 20f)) {
        button = Button("", Size.fillMaxSize(), backgroundColor = backgroundColor, cutStyle = cutStyle) { onClick() }
        button.isEnabled = affordable
        Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
            Image(iconSprite, Size.exact(16f, 16f))
            Text(label, font = Font.ORBITRON_12_CONDENSED, color = if (affordable) VanillaColors.base else VanillaColors.gray)
            Text("$amount", color = if (affordable) VanillaColors.highlight else VanillaColors.negative)
        }
    }
    return button
}

// --- recipe: tabs ---

/** TOP cut buttons on one `UIState<Int>`: the selected tab bright on the opaque dark fill. */
fun UIPanelAPI.Tabs(labels: List<String>, selected: UIState<Int>, width: Float = 130f) {
    Row(spacing = 2f) {
        val current = selected.value
        labels.forEachIndexed { i, label ->
            key(label) {
                val on = i == current
                Button(label, Size.exact(width, 20f), cutStyle = CutStyle.TOP,
                    color = if (on) VanillaColors.bright else VanillaColors.base,
                    backgroundColor = if (on) VanillaColors.dark.withAlpha(255) else VanillaColors.dark.withAlpha(153)) {
                    selected.value = i
                }
            }
        }
    }
}

// --- recipe: toast content ---

/** A toast like vanilla's intel notices: an icon, the title in the faction colour, the text in gray. */
fun UIPanelAPI.IntelToast(title: String, text: String, icon: String) {
    Row(spacing = 8f, verticalAlignment = Alignment.CenterVertically) {
        Image(icon, ImageSize.native(maxWidth = 32f, maxHeight = 32f))
        Column(spacing = 2f) {
            Text(title, font = Font.INSIGNIA_21, color = VanillaColors.base)
            Text(text, color = VanillaColors.gray)
        }
    }
}

/** A toast with a thin bar in [accent] down its left edge, the title in highlight, the text in gray. */
fun UIPanelAPI.AccentToast(title: String, text: String, accent: Color = VanillaColors.base) {
    Column(modifier = Modifier.padding(left = 11f).onRenderBelow { alpha ->
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glColor4f(accent.red / 255f, accent.green / 255f, accent.blue / 255f, alpha)
        GL11.glRectf(left, bottom, left + 3f, top)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
    }, spacing = 2f) {
        Text(title, color = VanillaColors.highlight)
        Text(text, color = VanillaColors.gray)
    }
}

/** A toast like a small intel panel: vanilla's section heading bar over the text, [width] wide. */
fun UIPanelAPI.HeadingToast(title: String, text: String, width: Float = 280f) {
    Column(spacing = 4f) {
        SectionHeading(title, Size.exact(width, 20f))
        Text(text, Size.mixed(Axis.Exact(width), Axis.WrapContent))
    }
}

// --- checks ---

private fun recipeAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

private fun TestContext.recipeClick(x: Float, y: Float) {
    Input.sendList(root, Input.move(x, y))
    NativeSmokeSupport.advance(root, 2)
    Input.sendList(root, Input.down(x, y))
    Input.sendList(root, Input.up(x, y))
    NativeSmokeSupport.advance(root, 2)
}

fun registerSkillRecipeTests() {
    StarUITestSuite.group("skill-recipes") {
        val blocked = UIState<String?>(null)
        var clicks = 0
        var costClicks = 0
        lateinit var guarded: ButtonAPI
        lateinit var guardHolder: CustomPanelAPI
        lateinit var cost: ButtonAPI
        lateinit var costBox: CustomPanelAPI
        val tab = UIState(0)
        lateinit var tabRow: CustomPanelAPI
        val icon = Global.getSettings().getCommoditySpec("supplies").iconName
        root.Column(Size.exact(400f, 380f), recipeAt(40f, 40f).background(Color(20, 20, 30)), spacing = 8f) {
            ChangeRow("Days left", "48", "24")
            guardHolder = Column {
                guarded = GuardedButton("Train", blocked.value, tooltip = "Starts training") { clicks++ }
            }
            costBox = Box {
                cost = CostButton("Send", icon, 40, affordable = true) { costClicks++ }
            }
            tabRow = Row { Tabs(listOf("Teams (4/5)", "Charter"), tab) }
        }
        frame()

        val enabledHeight = guardHolder.position.height
        expect(guarded.isEnabled, "the guarded button is enabled without a reason")
        blocked.value = "Needs 40 more Supplies"
        frame()
        expect(!guarded.isEnabled, "a reason greys it out")
        near(guardHolder.position.height, enabledHeight, "and nothing jumps: the reason line was reserved")
        fun labels(p: UIPanelAPI): List<LabelAPI> = p.getChildrenNonCopy().flatMap {
            when (it) { is LabelAPI -> listOf(it); is UIPanelAPI -> labels(it); else -> emptyList() }
        }
        val reasonLabel = labels(guardHolder).first { it.text.startsWith("Needs") }
        expect(reasonLabel.position.height <= 20f, "the reason stays on one line (${reasonLabel.position.height})")

        val p = costBox.position
        recipeClick(p.x + p.width / 2f, p.y + p.height / 2f)
        expect(costClicks == 1, "a click on the cost button's face reaches the button under it ($costClicks)")

        fun tabButtons(): List<ButtonAPI> = tabRow.getChildrenNonCopy().flatMap {
            (it as? UIPanelAPI)?.getChildrenNonCopy()?.filterIsInstance<ButtonAPI>() ?: emptyList()
        }
        val charter = tabButtons().first { it.text == "Charter" }.position
        recipeClick(charter.x + charter.width / 2f, charter.y + charter.height / 2f)
        frame()
        val tabs = tabButtons()
        expect(tab.value == 1, "a click on a tab selects it (${tab.value})")
        expect(tabs.size == 2 && tabs.first { it.text == "Charter" }.position.width == 130f, "tabs stay two, 130 wide (${tabs.size})")
        log(root.dumpLayout())
        screenshot("final")
    }
}

fun registerToastRecipeTests() {
    StarUITestSuite.group("skill-recipes-toasts") {
        val icon = Global.getSettings().getCommoditySpec("supplies").iconName
        lateinit var intel: CustomPanelAPI
        lateinit var accent: CustomPanelAPI
        lateinit var heading: CustomPanelAPI
        root.Column(Size.wrapContent(), recipeAt(40f, 40f).background(Color(20, 20, 30)), spacing = 8f) {
            intel = Box { IntelToast("Not enough supplies", "Needs 40 more for the survey run.", icon) }
            accent = Box { AccentToast("Survey complete", "Halberd team found 3 ruins in Corvus.") }
            heading = Box { HeadingToast("Charter renewed", "Halberd survey team: 30 more days, and a long line that wraps.") }
        }
        frame()
        expect(intel.position.height >= 32f, "the intel toast is at least its 32 px icon high (${intel.position.height})")
        val accentTitle = labelsIn(accent).first { it.text == "Survey complete" }
        expect(accentTitle.position.x - accent.position.x >= 11f, "the accent toast's text clears its bar")
        near(heading.position.width, 280f, "the heading toast is its width wide")
        expect(labelsIn(heading).any { it.position.height > 20f }, "and its text wraps under the heading")

        val oldScale = Layers.fadeScale
        Layers.testHost = root
        Layers.fadeScale = 0f
        try {
            val toast = showToast(duration = 0f) { IntelToast("Charter renewed", "Halberd survey team: 30 more days.", icon) }
            frame(); frame()
            val panel = toast.panel!!
            expect(panel.position.width > 200f && toast.isOpen, "in a toast it is laid out in the toast's panel (${panel.position.width})")
            screenshot("in-toast")
            toast.close()
            frame(); frame()
        } finally {
            Layers.testHost = null
            Layers.fadeScale = oldScale
        }
        screenshot("final")
    }
}

private fun labelsIn(p: UIPanelAPI): List<LabelAPI> = p.getChildrenNonCopy().flatMap {
    when (it) { is LabelAPI -> listOf(it); is UIPanelAPI -> labelsIn(it); else -> emptyList() }
}
