package org.starficz.starui.components

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.measure
import org.starficz.starui.vanilla.VanillaColors
import org.starficz.starui.vanilla.VanillaFactory
import java.awt.Color

/**
 * Vanilla's section heading: [text] on a coloured bar, as on vanilla's own panels. Fills the width by default.
 * Returns the vanilla [LabelAPI].
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
    val heading = reused ?: VanillaFactory.onScratch {
        addSectionHeading(text, color, backgroundColor, textAlign.toVanilla(), 300f, 0f)
    }.also { this.addComponent(it) }
    if (reused != null && heading.text != text) heading.text = text

    // vanilla's height (one line plus 3 px); the width is the text plus vanilla's 5 px either side
    val barHeight = heading.position.height
    fun intrinsic() = Pair(heading.measure(heading.text).first + 10f, barHeight)
    finishLeaf(slot, heading, size, modifier, intrinsic = intrinsic(), measure = { _, _ -> intrinsic() })
    return heading
}

/**
 * A vanilla-style panel: a [SectionHeading] with [title] on top (none when null), then the builder's components in a
 * column, [spacing] apart and [contentPadding] in from a thin border. Fills the width and wraps its content by
 * default.
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
    val fillsHeight = size.toNodeSize().height is Axis.FillMax
    return Column(size, Modifier.background(backgroundColor).border(borderColor) then modifier) {
        if (title != null) SectionHeading(title)
        Column(
            size = if (fillsHeight) Size.fillMaxSize() else Size.fillMaxWidth(),
            modifier = Modifier.padding(contentPadding),
            spacing = spacing,
            builder = builder
        )
    }
}
