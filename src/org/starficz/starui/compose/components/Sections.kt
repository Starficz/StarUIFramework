package org.starficz.starui.compose.components

import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.LayoutSize
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.border
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.toNodeSize
import org.starficz.starui.core.Font
import org.starficz.starui.core.VanillaColors
import org.starficz.starui.core.addSectionHeading
import org.starficz.starui.core.measureTextSize
import java.awt.Color

/**
 * Vanilla's section heading (`TooltipMakerAPI.addSectionHeading`): [text] centred (by default) on a bar the heading's
 * width, Orbitron 12 condensed, one line plus 3 px tall. Fills the width by default. Returns the vanilla label, which
 * draws its own bar; its text updates in place on a rebuild, its colours and alignment are fixed when it is made.
 */
fun UIPanelAPI.SectionHeading(
    text: String,
    size: Size = Size.fillX(),
    modifier: Modifier = Modifier,
    textColor: Color? = null,   // null = the player faction's base UI colour
    bgColor: Color? = null,     // null = the player faction's dark UI colour
    alignment: Alignment = Alignment.MID
): LabelAPI {
    checkDslTarget()
    val fg = textColor ?: VanillaColors.base
    val bg = bgColor ?: VanillaColors.dark
    val slot = childSlot("SectionHeading", listOf(fg, bg, alignment))
    val reused: LabelAPI? = slot.reused()
    val heading = reused ?: this.addSectionHeading(text, fg, bg, alignment)
    if (reused != null && heading.text != text) heading.text = text

    // vanilla's height (one line plus 3 px); the width is the text plus vanilla's 5 px either side
    val barHeight = heading.position.height
    fun intrinsic() = Pair(measureTextSize(heading.text, Font.ORBITRON_12_CONDENSED).first + 10f, barHeight)
    if (reused != null) applyReuseBounds(heading, size, intrinsic())
    else getInitialBounds(size, intrinsic()).let { (w, h) -> heading.position.setSize(w, h) }

    registerComponentSizing(heading, size, isContainer = false)
    slot.remember(heading, heading)
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMeasurables[heading] = { _, _ -> intrinsic() }
    }
    modifier.operations.forEach { it(heading) }
    return heading
}

/**
 * A titled section the way vanilla lays out its panels: a [SectionHeading] across the top, the builder's components in
 * a padded column below, on vanilla's panel black inside a thin border in the faction's dark colour. Fills the width
 * and wraps its content by default; with a fill height the content column fills too.
 */
fun UIPanelAPI.Section(
    title: String,
    size: LayoutSize = LayoutSize.fillX(),
    modifier: Modifier = Modifier,
    bgColor: Color? = null,       // null = VanillaColors.panel
    borderColor: Color? = null,   // null = the player faction's dark UI colour
    xPadding: Float = 10f,
    yPadding: Float = 8f,
    spacing: Float = 6f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    val fillsHeight = size.toNodeSize().height is Dimension.FillMax
    val frame = Modifier.background(bgColor ?: VanillaColors.panel).border(borderColor ?: VanillaColors.dark)
    return Column(size, Modifier.then { c -> frame.operations.forEach { it(c) }; modifier.operations.forEach { it(c) } }) {
        SectionHeading(title)
        Column(if (fillsHeight) LayoutSize.fill() else LayoutSize.fillX(),
            xPadding = xPadding, yPadding = yPadding, spacing = spacing, builder = builder)
    }
}
