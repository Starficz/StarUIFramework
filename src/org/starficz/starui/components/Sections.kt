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
 * A section heading: [text] on a coloured bar, as on vanilla's own panels (vanilla's `addSectionHeading`). Its
 * height is vanilla's (one line of Orbitron 12 condensed plus 3 pixels); by default it fills the width.
 *
 * @param text the text of the heading
 * @param size the size of the [SectionHeading]. By default it fills the width
 * @param modifier the [Modifier] to be applied to this heading
 * @param color the colour of the text. By default the player faction's UI colour
 * @param backgroundColor the colour of the bar. By default the player faction's dark UI colour
 * @param textAlign the alignment of the text on the bar
 * @return the vanilla label
 * @see Section
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
 * Sections contain content that relates to one subject, in the look of vanilla's campaign panels: a
 * [SectionHeading] with [title] on top, then the content in a column, on a dark background inside a thin border.
 *
 * By default a [Section] fills the width and wraps its content in height.
 *
 * Example usage:
 *
 * ```kotlin
 * Section("Fleet") {
 *     Text("Ships: ${fleet.size}")
 *     Text("Supplies: ${supplies}")
 * }
 * ```
 *
 * @param title the text of the heading, or `null` for a section without one
 * @param size the size of the [Section]. By default it fills the width and wraps its content in height
 * @param modifier the [Modifier] to be applied to this section
 * @param contentPadding the spacing values to apply internally between the border and the content
 * @param spacing the gap between two neighbouring children of the content
 * @param backgroundColor the colour of the background. By default [VanillaColors.panel]
 * @param borderColor the colour of the border. By default the player faction's dark UI colour
 * @param builder the content displayed in the section. It runs again when a [UIState] it read changes
 * @return the section's panel
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
