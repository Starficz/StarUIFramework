package org.starficz.starui.compose.components

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.StarUIDsl
import org.starficz.starui.compose.TextAlign
import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.border
import org.starficz.starui.compose.modifier.padding
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.compose.toNodeSize
import org.starficz.starui.core.Font
import org.starficz.starui.core.VanillaColors
import org.starficz.starui.core.addSectionHeading
import org.starficz.starui.core.measureTextSize
import java.awt.Color

/**
 * Vanilla's section heading (`TooltipMakerAPI.addSectionHeading`): [text] on a bar the heading's width, Orbitron 12
 * condensed, one line plus 3 px tall. Fills the width by default. Returns the vanilla label, which draws its own bar;
 * its text updates in place on a rebuild, its colours and alignment are fixed when it is made.
 */
fun UIPanelAPI.SectionHeading(
    text: String,
    size: Size = StarUITheme.current.sectionHeading.size,
    modifier: Modifier = Modifier,
    color: Color = StarUITheme.current.sectionHeading.color ?: VanillaColors.base,
    backgroundColor: Color = StarUITheme.current.sectionHeading.backgroundColor ?: VanillaColors.dark,
    textAlign: TextAlign = StarUITheme.current.sectionHeading.textAlign
): LabelAPI {
    checkDslTarget()
    val slot = childSlot("SectionHeading", listOf(color, backgroundColor, textAlign))
    val reused: LabelAPI? = slot.reused()
    val heading = reused ?: this.addSectionHeading(text, color, backgroundColor, textAlign.toVanilla())
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
 * A panel the way vanilla lays its own out: a [SectionHeading] across the top (none when [title] is null), the
 * builder's components in a column below, [spacing] apart and [contentPadding] in from the border, on vanilla's panel
 * black inside a thin border in the faction's dark colour. Fills the width and wraps its content by default; with a
 * fill height the content column fills too.
 */
fun UIPanelAPI.Section(
    title: String?,
    size: Size = StarUITheme.current.section.size,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = StarUITheme.current.section.contentPadding,
    spacing: Float = StarUITheme.current.section.spacing,
    backgroundColor: Color = StarUITheme.current.section.backgroundColor ?: VanillaColors.panel,
    borderColor: Color = StarUITheme.current.section.borderColor ?: VanillaColors.dark,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): CustomPanelAPI {
    val fillsHeight = size.toNodeSize().height is Dimension.FillMax
    val frame = Modifier.background(backgroundColor).border(1f, borderColor)
    return Column(size, Modifier.then { c -> frame.operations.forEach { it(c) }; modifier.operations.forEach { it(c) } }) {
        if (title != null) SectionHeading(title)
        Column(
            size = if (fillsHeight) Size.fillMaxSize() else Size.fillMaxWidth(),
            modifier = Modifier.padding(contentPadding),
            spacing = spacing,
            builder = builder
        )
    }
}
