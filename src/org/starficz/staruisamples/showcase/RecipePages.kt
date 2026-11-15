package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.opengl.GL11
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.*
import org.starficz.starui.window.showToast
import java.awt.Color

// The recipes of the agent skill's references/recipes.md, each next to a use of it. Keep the three copies (recipes.md,
// the suite's SkillRecipes.kt, this file) the same.

private const val FILE = "RecipePages.kt"

// snippet: change-row
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
// end snippet

// snippet: guarded
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

private val stock = UIState(25)
// end snippet

// snippet: cost-button
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
// end snippet

// snippet: tabs
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

private val tab = UIState(0)
// end snippet

// snippet: toasts
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
// end snippet

private val recipes = Page("Recipes", "Recipes",
    "Small component functions for parts StarUI has no component for, built from its components. Copy them into a " +
        "mod and rename freely.", FILE) {

    card("Change row", "change-row", "A value before and after a change, the old one struck through.") {
        // snippet: change-row
        ChangeRow("Max burn", "9", "10")
        ChangeRow("Supplies a month", "48", "52")
        // end snippet
    }

    card("Guarded button", "guarded",
        "A blocked button stays in place, grey, with its reason on a line that keeps its height when there is none.") {
        // snippet: guarded
        Row(spacing = 6f) {
            Button("+10 supplies") { stock.value += 10 }
            Button("-10") { stock.value = maxOf(0, stock.value - 10) }
        }
        val short = 40 - stock.value
        GuardedButton("Train", reason = if (short > 0) "Needs $short more supplies" else null) { stock.value -= 40 }
        Text("Supplies: ${stock.value}", color = VanillaColors.gray)
        // end snippet
    }

    card("Cost button", "cost-button", "One cost on its button: icon, label and amount, red when the player is short.") {
        // snippet: cost-button
        val supplies = Global.getSettings().getCommoditySpec("supplies").iconName
        Row(spacing = 6f) {
            CostButton("Send", supplies, 40, affordable = true) { }
            CostButton("Send", supplies, 400, affordable = false) { }
        }
        // end snippet
    }

    card("Tabs", "tabs", "TOP cut buttons, the selected one bright on the opaque dark fill, a divider under them.") {
        // snippet: tabs
        Tabs(listOf("Ships", "Weapons", "Fighters"), tab)
        Divider(thickness = 1f)
        Text(listOf("Ships", "Weapons", "Fighters")[tab.value] + " go here", color = VanillaColors.gray)
        // end snippet
    }

    card("Toast content", "toasts", "Three looks for showToast's content; the toast keeps its panel and slide around them.") {
        // snippet: toasts
        val icon = Global.getSettings().getCommoditySpec("supplies").iconName
        Row(spacing = 6f) {
            Button("Intel") { showToast(duration = 5f) { IntelToast("Charter renewed", "Halberd survey team: 30 more days.", icon) } }
            Button("Accent") { showToast(duration = 5f) { AccentToast("Not enough fuel", "The jump needs 40.", VanillaColors.negative) } }
            Button("Heading") { showToast(duration = 5f) { HeadingToast("Bounty posted", "A pirate fleet near Askonia, 80,000 credits.") } }
        }
        // end snippet
    }
}

internal val recipePages = listOf(recipes)
