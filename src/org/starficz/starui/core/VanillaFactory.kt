package org.starficz.starui.core

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.core.ReflectionUtils.getConstructorsMatching
import org.starficz.starui.core.ReflectionUtils.set

/**
 * What StarUI's components need to make vanilla elements that only [TooltipMakerAPI] makes (buttons, area checkboxes,
 * section headings, paragraphs, images), and to measure text.
 *
 * Those are made on one scratch tooltip, kept, instead of a new panel and tooltip each. The scratch holds nothing:
 * each element is taken off it at once, and it forgets its last element (`getPrev()`), which it would otherwise
 * place the next one below. Each call sets the fonts it uses: nothing carries over from the call before.
 *
 * Text fields, checkboxes and sliders have SettingsAPI factories (what the tooltip versions call underneath) and do
 * not come here.
 */
internal object VanillaFactory {
    private const val SCRATCH_SIZE = 10000f

    private val scratch: TooltipMakerAPI by lazy {
        Global.getSettings().createCustom(SCRATCH_SIZE, SCRATCH_SIZE, null).createUIElement(SCRATCH_SIZE, SCRATCH_SIZE, false)
    }

    /** Runs [make] on the scratch tooltip and returns what it made, taken off the tooltip (placed nowhere). */
    fun <T : UIComponentAPI> onScratch(make: TooltipMakerAPI.() -> T): T {
        val made = scratch.make()
        made.parent?.removeComponent(made)   // the tooltip adds to an inner panel of its own
        // the next element would be placed below this one, which is about to be in another panel: vanilla refuses
        scratch.set("prev", null, searchSuperclass = true)
        return made
    }

    // --- images: the tooltip constructs its image class from the sprite path; learned from the first one ---

    private var imageConstructor: ReflectionUtils.ReflectedConstructor? = null

    /** A vanilla image of the sprite at [spritePath], not yet added anywhere. */
    fun image(spritePath: String): UIComponentAPI {
        imageConstructor?.let { return it.newInstance(spritePath) as UIComponentAPI }
        val made = onScratch { addImage(spritePath, 100f, 100f, 0f); prev }
        imageConstructor = made.javaClass.getConstructorsMatching(1, arrayOf(String::class.java)).singleOrNull()
        return made
    }

    // --- measuring: one label per font, kept, instead of a panel, tooltip and label per measured string ---

    private val measuringLabels = HashMap<Font?, LabelAPI>()

    /** The size of [text] on one line (more where it has line breaks) in [font], or the paragraph font for null. */
    fun measure(text: String, font: Font?): Pair<Float, Float> {
        if (text.isEmpty()) return Pair(0f, 0f)
        val label = measuringLabels.getOrPut(font) {
            onScratch {
                if (font != null) setParaFont(font.path) else setParaFontDefault()
                addPara(" ", 0f)
            }
        }
        return label.measure(text)
    }
}

/** The size of [text] in this label's font, on one line (more where it has line breaks). */
internal fun LabelAPI.measure(text: String): Pair<Float, Float> =
    if (text.isEmpty()) Pair(0f, 0f) else Pair(computeTextWidth(text), computeTextHeight(text))

/**
 * Builds [block] on a temporary tooltip [width] wide and returns the tooltip itself, detached and sized to what was
 * added: for vanilla content made of several components placed against each other (a bar and its markers).
 */
internal fun buildTooltipBlock(width: Float, block: TooltipMakerAPI.() -> Unit): UIComponentAPI {
    val tempPanel = Global.getSettings().createCustom(width, 4000f, null)
    val tooltip = tempPanel.createUIElement(width, 4000f, false)
    tempPanel.addUIElement(tooltip)
    tooltip.block()
    tempPanel.removeComponent(tooltip)
    tooltip.position.setSize(width, tooltip.heightSoFar)
    return tooltip
}


/**
 * Builds vanilla tooltip content at [width] on a temporary TooltipMakerAPI and returns the component it added last,
 * detached, for adding anywhere else. Most TooltipMakerAPI builders (grids, bars, tables, ship lists) add exactly one
 * component through `addCustom`, which is what this takes; an empty panel when it added nothing. [block] gets the
 * temporary tooltip.
 */
internal fun buildFromTooltip(width: Float, block: TooltipMakerAPI.() -> Unit): UIComponentAPI {
    val tempPanel = Global.getSettings().createCustom(width, 4000f, null)
    val tooltip = tempPanel.createUIElement(width, 4000f, false)
    tempPanel.addUIElement(tooltip)
    tooltip.block()
    // some builders add nothing for empty input (a stat with no modifiers): an empty panel then
    val made = tooltip.prev ?: return Global.getSettings().createCustom(width, 0f, null)
    made.parent?.removeComponent(made)
    return made
}
