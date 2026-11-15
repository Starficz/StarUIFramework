package org.starficz.starui.components

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.PersonImportance
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.MutableStat
import com.fs.starfarer.api.combat.StatBonus
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation
import com.fs.starfarer.api.util.Misc
import org.starficz.starui.BoundedSize
import org.starficz.starui.layout.VanillaElementEngine
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.Scope
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.Size
import org.starficz.starui.StarUIDsl
import org.starficz.starui.UIState
import org.starficz.starui.vanilla.*
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import java.awt.Color

// Vanilla TooltipMakerAPI content as StarUI components. Each is built on a temporary tooltip at the width its
// container gives it (or a fixed one), detached and held in a StarUI panel, which is what each call returns. It is
// made again whenever its width changes, and when the builder calling it runs again with different arguments: each
// call passes its `inputs` (a fresh snapshot of what it shows) to VanillaElementEngine.update. Components that read
// live game objects (a stat, fleet members, a person, a planet, cargo, an event) pass null and are made again on
// every call, since those objects change without any argument changing. All of them wrap their content by default,
// at the width there is room for.

/** Holds one vanilla component made by [build] at a width (see VanillaElementEngine). */
private fun UIPanelAPI.VanillaElement(
    kind: String,
    size: Size,
    modifier: Modifier,
    inputs: Any?,
    fixedWidth: Float? = null,
    build: TooltipMakerAPI.(width: Float) -> Unit
): CustomPanelAPI = VanillaComponent(kind, size, modifier, inputs, fixedWidth) { width -> buildFromTooltip(width) { build(width) } }

/** Like [VanillaElement], with [make] building the component for a width itself. */
private fun UIPanelAPI.VanillaComponent(
    kind: String,
    size: Size,
    modifier: Modifier,
    inputs: Any?,
    fixedWidth: Float? = null,
    make: (width: Float) -> UIComponentAPI
): CustomPanelAPI = VanillaHolder(kind, size, modifier, inputs) { engine ->
    engine.fixedWidth = fixedWidth
    engine.build = make
}

/**
 * Holds one vanilla component made at the whole size its container gets by [make] (content vanilla lays out once,
 * at a fixed size), made again when that size changes. [onSwap] sees each new component before it goes in.
 */
private fun UIPanelAPI.VanillaSizedComponent(
    kind: String,
    size: BoundedSize,
    modifier: Modifier,
    inputs: Any?,
    onSwap: ((old: UIComponentAPI?, made: UIComponentAPI) -> Unit)? = null,
    make: (width: Float, height: Float) -> UIComponentAPI
): CustomPanelAPI = VanillaHolder(kind, size, modifier, inputs) { engine ->
    engine.onSwap = onSwap
    engine.buildSized = make
}

/** The StarUI panel holding a vanilla component, its engine reset and then set up by [configure]. */
private inline fun UIPanelAPI.VanillaHolder(
    kind: String,
    size: Size,
    modifier: Modifier,
    inputs: Any?,
    configure: (VanillaElementEngine) -> Unit
): CustomPanelAPI {
    checkDslTarget()
    val slot = childSlot(kind)
    val (panel, plugin) = containerPanel(slot, size)
    val engine = (plugin.layoutEngine as? VanillaElementEngine) ?: VanillaElementEngine(plugin).also { plugin.layoutEngine = it }
    engine.fixedWidth = null
    engine.afterBuild = null
    engine.afterLayout = null
    engine.buildSized = null
    engine.onSwap = null
    configure(engine)

    registerComponentSizing(panel, size, isContainer = true, containerPlugin = plugin)
    slot.remember(panel, panel)
    modifier.operations.forEach { it(panel) }
    engine.update(inputs)
    return panel
}

// ---------------------------------------------------------------------------------------------------------------
// Any tooltip content
// ---------------------------------------------------------------------------------------------------------------

/** What a TooltipMakerPanel's latest call asked for, read by content made by an earlier call. */
private class TooltipMakerPanelState(var onButtonClick: ((data: Any?) -> Unit)?)

/**
 * A component whose content is made with vanilla's [TooltipMakerAPI]: a last resort, for when it really is the
 * simplest choice. Most things are doable with StarUI's own components, and better there (text with highlights is
 * [Text] with `highlightedText`, headings [SectionHeading], tables [Table], stat blocks [StatGrid]); use this for
 * vanilla content with no StarUI component and no simple StarUI build, such as a mod's helper that fills a tooltip or
 * a TooltipMakerAPI call StarUI has no port of. [content] gets a fresh tooltip [width][content] wide (the width the panel has room for) and
 * fills it from the top as vanilla does; the panel is as tall as what was added (`heightSoFar`).
 *
 * Vanilla lays tooltip content out once, at one width, so the content is made again (with [content] run again on a
 * new tooltip) whenever:
 * - the width it gets changes;
 * - a [UIState] that [content] read changes: the reads are tracked as in a builder, but only this content is made
 *   again, not the builder around it;
 * - the builder around it runs again, unless [inputs] is given and equals the last call's (pass what the content
 *   shows, as a fresh snapshot: a list, a data class).
 *
 * Anything that lives in the content (a text field's caret, a scroll position) is lost when it is made again, and
 * [content] may run more than once per layout while the panel's width settles: keep it free of side effects.
 *
 * Buttons made with `addButton` call [onButtonClick] with their data; `onClick { }` from the vanilla toolkit works
 * on a returned [ButtonAPI] too. StarUI components cannot be called inside [content]: build those around the panel.
 *
 * Example usage:
 *
 * ```kotlin
 * val danger = UIState(2)
 * TooltipMakerPanel(Size.fillMaxWidth()) { width ->
 *     addTitle("Survey")
 *     addPara("Hazard rating: %s", 10f, VanillaColors.highlight, "${100 + 25 * danger.value}%")
 *     addButton("Send probe", "probe", width / 2, 25f, 10f)
 * }
 * ```
 *
 * @param size the size of the [TooltipMakerPanel]: wrap (the default) takes the width there is room for (300 when
 *   unbounded) and the content's height
 * @param modifier the [Modifier] to be applied to this panel
 * @param inputs what the content shows, to keep it when the builder runs again with equal inputs; `null` (the
 *   default) makes it again on every run of the builder
 * @param onButtonClick called with a button's data when a button made by `addButton` in [content] is clicked
 * @param content fills the tooltip; gets the width it is made at
 * @return the panel holding the tooltip
 */
fun UIPanelAPI.TooltipMakerPanel(
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    inputs: Any? = null,
    onButtonClick: ((data: Any?) -> Unit)? = null,
    content: TooltipMakerAPI.(width: Float) -> Unit
): CustomPanelAPI = VanillaHolder("TooltipMakerPanel", size, modifier, inputs) { engine ->
    val plugin = engine.plugin
    val state = (engine.callerState as? TooltipMakerPanelState)?.also { it.onButtonClick = onButtonClick }
        ?: TooltipMakerPanelState(onButtonClick).also { engine.callerState = it }
    // the content's state reads make it again, without running the builder around it (which would throw it away)
    val reads = plugin.scope?.takeIf { !it.disposed && it.onDirty != null }
        ?: Scope(plugin, StarUIContext.currentTheme, "TooltipMakerPanel", content.javaClass.name.substringBefore("$$"), builder = {})
            .also { it.onDirty = { engine.invalidate() }; plugin.scope = it }
    val buttons = object : BaseCustomUIPanelPlugin() {
        override fun buttonPressed(buttonId: Any?) { state.onButtonClick?.invoke(buttonId) }
    }
    engine.build = { width ->
        reads.trackReads {
            StarUIContext.tooltipContentDepth++
            try {
                buildTooltipBlock(width, buttons) { content(width) }
            } finally {
                StarUIContext.tooltipContentDepth--
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Stat grids
// ---------------------------------------------------------------------------------------------------------------

/**
 * Receiver scope of a [StatGrid]'s entries block: the entries, in reading order (left to right, then down).
 */
@StarUIDsl
class StatGridScope internal constructor() {
    internal val entries = ArrayList<Triple<String, String, Color?>>()

    /**
     * Adds one label and its value.
     *
     * @param label the label of the entry
     * @param value the value of the entry
     * @param valueColor the colour of the value, or `null` for the grid's (vanilla's highlight colour)
     */
    fun entry(label: String, value: String, valueColor: Color? = null) {
        entries += Triple(label, value, valueColor)
    }
}

/**
 * A component that shows vanilla's grid of labels and values, as in ship and weapon stat tooltips. The entries fill
 * [columns] columns left to right, then down. With [valueFirst], it is vanilla's flipped grid: each value in a
 * [valueWidth] column before its label.
 *
 * By default it wraps its content, at the width there is room for. It is made again when one of its arguments changes.
 *
 * Example usage:
 *
 * ```kotlin
 * StatGrid(columns = 2) {
 *     entry("Hull", "12000")
 *     entry("Armor", "1500")
 *     entry("Flux capacity", "18000", valueColor = VanillaColors.positive)
 * }
 * ```
 *
 * @param size the size of the [StatGrid]
 * @param modifier the [Modifier] to be applied to this grid
 * @param columns the number of columns
 * @param labelColor the colour of the labels, or `null` for vanilla's
 * @param valueFirst whether each value comes before its label
 * @param valueWidth the width of the value column when [valueFirst] is set
 * @param valuePad the gap between a value and its label when [valueFirst] is set
 * @param entries the entries of the grid
 * @return the panel holding the vanilla grid
 */
fun UIPanelAPI.StatGrid(
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    columns: Int = 1,
    labelColor: Color? = null,
    valueFirst: Boolean = false,
    valueWidth: Float = 50f,
    valuePad: Float = 10f,
    entries: StatGridScope.() -> Unit
): CustomPanelAPI {
    require(columns >= 1) { "StatGrid: columns must be at least 1" }
    val items = StatGridScope().apply(entries).entries
    return VanillaElement("StatGrid", size, modifier, inputs = listOf(columns, labelColor, valueFirst, valueWidth, valuePad, items)) { width ->
        val itemWidth = width / columns
        when {
            valueFirst && labelColor != null -> beginGridFlipped(itemWidth, columns, labelColor, valueWidth, valuePad)
            valueFirst -> beginGridFlipped(itemWidth, columns, valueWidth, valuePad)
            labelColor != null -> beginGrid(itemWidth, columns, labelColor)
            else -> beginGrid(itemWidth, columns)
        }
        items.forEachIndexed { i, (label, value, color) ->
            if (color != null) addToGrid(i % columns, i / columns, label, value, color)
            else addToGrid(i % columns, i / columns, label, value)
        }
        addGrid(0f)
    }
}

/**
 * A component that shows vanilla's breakdown of a stat by source, as in the refit screen's stat tooltips: one line per
 * modifier, its value in a [valueWidth] column.
 *
 * By default it wraps its content, at the width there is room for. Since it reads live game objects, it is made again
 * each time its builder runs.
 *
 * @param stat the stat to break down
 * @param size the size of the [StatModGrid]
 * @param modifier the [Modifier] to be applied to this grid
 * @param valueWidth the width of the value column
 * @param valuePad the gap after the value column
 * @param getter formats the values, or `null` for vanilla's formatting
 * @return the panel holding the vanilla grid
 */
fun UIPanelAPI.StatModGrid(
    stat: MutableStat,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    valueWidth: Float = 50f,
    valuePad: Float = 10f,
    getter: TooltipMakerAPI.StatModValueGetter? = null
): CustomPanelAPI = VanillaElement("StatModGrid", size, modifier, inputs = null /* the live stat */) { width ->
    if (getter != null) addStatModGrid(width, valueWidth, valuePad, 0f, stat, getter)
    else addStatModGrid(width, valueWidth, valuePad, 0f, stat)
}

/**
 * A component that shows vanilla's breakdown of a [StatBonus] by source. See the [MutableStat] overload.
 *
 * @param stat the bonus to break down
 * @param size the size of the [StatModGrid]
 * @param modifier the [Modifier] to be applied to this grid
 * @param valueWidth the width of the value column
 * @param valuePad the gap after the value column
 * @param getter formats the values, or `null` for vanilla's formatting
 * @return the panel holding the vanilla grid
 */
fun UIPanelAPI.StatModGrid(
    stat: StatBonus,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    valueWidth: Float = 50f,
    valuePad: Float = 10f,
    getter: TooltipMakerAPI.StatModValueGetter? = null
): CustomPanelAPI = VanillaElement("StatModGrid", size, modifier, inputs = null /* the live stat */) { width ->
    if (getter != null) addStatModGrid(width, valueWidth, valuePad, 0f, stat, getter)
    else addStatModGrid(width, valueWidth, valuePad, 0f, stat)
}

/**
 * A component that shows vanilla's labelled value box, as on the market screen: [label] above [value], 42 pixels
 * tall.
 *
 * By default it wraps its content, at the width there is room for. It is made again when one of its arguments changes.
 *
 * @param label the label, on top
 * @param value the value, below it
 * @param size the size of the [LabelledValue]
 * @param modifier the [Modifier] to be applied to this box
 * @param labelColor the colour of the label
 * @param valueColor the colour of the value
 * @return the panel holding the vanilla box
 */
fun UIPanelAPI.LabelledValue(
    label: String,
    value: String,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    labelColor: Color = VanillaColors.text,
    valueColor: Color = VanillaColors.highlight
): CustomPanelAPI = VanillaElement("LabelledValue", size, modifier, inputs = listOf(label, value, labelColor, valueColor)) { width ->
    addLabelledValue(label, value, labelColor, valueColor, width, 0f)
}

// ---------------------------------------------------------------------------------------------------------------
// Relationship and importance
// ---------------------------------------------------------------------------------------------------------------

/**
 * A component that shows vanilla's reputation bar for a relationship value, 19 pixels tall.
 *
 * By default it is as wide as there is room for. It is made again when one of its arguments changes.
 *
 * @param value the relationship, from -1 (vengeful) to 1 (cooperative)
 * @param size the size of the [RelationshipBar]
 * @param modifier the [Modifier] to be applied to this bar
 * @return the panel holding the vanilla bar
 */
fun UIPanelAPI.RelationshipBar(value: Float, size: Size = Size.wrapContent(), modifier: Modifier = Modifier): CustomPanelAPI =
    VanillaElement("RelationshipBar", size, modifier, inputs = value) { width -> addRelationshipBar(value, width, 0f) }

/**
 * A component that shows vanilla's reputation bar for the player's standing with [faction]. Needs a campaign.
 * Since it reads live game objects, it is made again each time its builder runs.
 *
 * @param faction the faction whose standing with the player is shown
 * @param size the size of the [RelationshipBar]
 * @param modifier the [Modifier] to be applied to this bar
 * @return the panel holding the vanilla bar
 */
fun UIPanelAPI.RelationshipBar(faction: FactionAPI, size: Size = Size.wrapContent(), modifier: Modifier = Modifier): CustomPanelAPI =
    VanillaElement("RelationshipBar", size, modifier, inputs = null /* the live standing */) { width -> addRelationshipBar(faction, width, 0f) }

/**
 * A component that shows vanilla's reputation bar for the player's standing with [person]. Needs a campaign.
 * Since it reads live game objects, it is made again each time its builder runs.
 *
 * @param person the person whose standing with the player is shown
 * @param size the size of the [RelationshipBar]
 * @param modifier the [Modifier] to be applied to this bar
 * @return the panel holding the vanilla bar
 */
fun UIPanelAPI.RelationshipBar(person: PersonAPI, size: Size = Size.wrapContent(), modifier: Modifier = Modifier): CustomPanelAPI =
    VanillaElement("RelationshipBar", size, modifier, inputs = null /* the live standing */) { width -> addRelationshipBar(person, width, 0f) }

/**
 * A component that shows vanilla's importance pips for a contact or another person, from very low to very high.
 * It is made again when one of its arguments changes.
 *
 * @param importance the importance shown
 * @param size the size of the [ImportanceIndicator]
 * @param modifier the [Modifier] to be applied to this indicator
 * @return the panel holding the vanilla indicator
 */
fun UIPanelAPI.ImportanceIndicator(importance: PersonImportance, size: Size = Size.wrapContent(), modifier: Modifier = Modifier): CustomPanelAPI =
    VanillaElement("ImportanceIndicator", size, modifier, inputs = importance) { width -> addImportanceIndicator(importance, width, 0f) }

// ---------------------------------------------------------------------------------------------------------------
// Commodities and costs
// ---------------------------------------------------------------------------------------------------------------

/**
 * Receiver scope of a [CommodityIcons]' icons block: the icons, in order.
 */
@StarUIDsl
class CommodityIconsScope internal constructor() {
    internal val icons = ArrayList<Triple<CommoditySpecAPI, Int, IconRenderMode>>()

    /**
     * Adds [count] icons of a commodity.
     *
     * @param commodityId the commodity's id (`"supplies"`, `Commodities.FUEL`, ...)
     * @param count the number of icons
     * @param mode how the icons are drawn (normal, dim, red, green, ...)
     * @throws IllegalArgumentException if there is no commodity with that id
     */
    fun icons(commodityId: String, count: Int, mode: IconRenderMode = IconRenderMode.NORMAL) {
        val spec = Global.getSettings().getCommoditySpec(commodityId)
            ?: throw IllegalArgumentException("CommodityIcons: no commodity \"$commodityId\"")
        icons += Triple(spec, count, mode)
    }

    /**
     * Adds [count] icons of a commodity.
     *
     * @param commodity the commodity
     * @param count the number of icons
     * @param mode how the icons are drawn (normal, dim, red, green, ...)
     */
    fun icons(commodity: CommoditySpecAPI, count: Int, mode: IconRenderMode = IconRenderMode.NORMAL) {
        icons += Triple(commodity, count, mode)
    }
}

/**
 * A component that shows vanilla's row of commodity icons, as in market supply and demand tooltips. The icons wrap
 * onto more rows when they do not fit.
 *
 * By default it wraps its content, at the width there is room for. It is made again when one of its arguments changes.
 *
 * @param size the size of the [CommodityIcons]
 * @param modifier the [Modifier] to be applied to this row
 * @param rowHeight the height of a row, which sets the size of the icons, or `null` for vanilla's
 * @param rows a fixed number of rows, or `null` to use as many as the icons need
 * @param centered whether to centre a single row of icons
 * @param icons the icons, in order
 * @return the panel holding the vanilla icons
 */
fun UIPanelAPI.CommodityIcons(
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    rowHeight: Float? = null,
    rows: Int? = null,
    centered: Boolean = false,
    icons: CommodityIconsScope.() -> Unit
): CustomPanelAPI {
    val items = CommodityIconsScope().apply(icons).icons
    return VanillaElement("CommodityIcons", size, modifier, inputs = listOf(rowHeight, rows, centered, items)) { _ ->
        beginIconGroup()
        for ((spec, count, mode) in items) addIcons(spec, count, mode)
        when {
            centered -> addIconGroupAndCenter(0f)
            rows != null -> addIconGroup(rowHeight ?: 32f, rows, 0f)
            rowHeight != null -> addIconGroup(rowHeight, 0f)
            else -> addIconGroup(0f)
        }
    }
}

/**
 * Receiver scope of a [CostDisplay]'s costs block: the costs, in order.
 */
@StarUIDsl
class CostDisplayScope internal constructor() {
    internal val costs = ArrayList<Triple<String, Int, Boolean>>()

    /**
     * Adds the cost of a commodity.
     *
     * @param commodityId the commodity's id
     * @param quantity the amount needed
     * @param consumed whether the cost is used up. `false` shows it as needed but kept, drawn dimmer
     */
    fun cost(commodityId: String, quantity: Int, consumed: Boolean = true) {
        costs += Triple(commodityId, quantity, consumed)
    }
}

/**
 * A component that shows vanilla's resource cost boxes, as in the build and survey dialogs, under an optional
 * [title]. With [withAvailable], each box also shows the player's stock and turns red when it is short.
 *
 * By default it wraps its content, at the width there is room for. It is made again when one of its arguments
 * changes, and with [withAvailable] each time its builder runs, since the stock is read live. Needs a campaign:
 * vanilla reads the player fleet's cargo even without [withAvailable].
 *
 * @param size the size of the [CostDisplay]
 * @param modifier the [Modifier] to be applied to this display
 * @param title the title above the boxes, or `null` for none
 * @param withAvailable whether each box shows the player's stock
 * @param color the main colour of the boxes
 * @param darkColor the dark colour of the boxes
 * @param boxHeight the height of the boxes, or `null` for vanilla's
 * @param costs the costs, in order
 * @return the panel holding the vanilla boxes
 */
fun UIPanelAPI.CostDisplay(
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    title: String? = null,
    withAvailable: Boolean = false,
    color: Color = VanillaColors.base,
    darkColor: Color = VanillaColors.dark,
    boxHeight: Float? = null,
    costs: CostDisplayScope.() -> Unit
): CustomPanelAPI {
    val items = CostDisplayScope().apply(costs).costs
    // with the player's stock shown, it reads the live cargo
    val inputs = if (withAvailable) null else listOf(title, color, darkColor, boxHeight, items)
    return VanillaElement("CostDisplay", size, modifier, inputs) { width ->
        showCost(title, withAvailable, width, boxHeight ?: -1f, color, darkColor, 0f,
            items.map { it.first }.toTypedArray(), items.map { it.second }.toIntArray(), items.map { it.third }.toBooleanArray())
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Ships and codex
// ---------------------------------------------------------------------------------------------------------------

/**
 * A component that shows vanilla's grid of ship icons, as in fleet tooltips: [columns] by [rows] icons. Ships
 * beyond `columns * rows` are left out.
 *
 * Since it reads live game objects, it is made again each time its builder runs.
 *
 * @param ships the ships shown
 * @param size the size of the [ShipList]
 * @param modifier the [Modifier] to be applied to this list
 * @param columns the number of icons in a row
 * @param rows the number of rows
 * @param iconSize the width and height of an icon
 * @param baseColor the tint of the ship sprites in the icons, also the colour of the list's scrollbar handle
 * @return the panel holding the vanilla list
 */
fun UIPanelAPI.ShipList(
    ships: List<FleetMemberAPI>,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    columns: Int = 8,
    rows: Int = 1,
    iconSize: Float = 48f,
    baseColor: Color = VanillaColors.base
): CustomPanelAPI = VanillaElement("ShipList", size, modifier, inputs = null /* live fleet members */) { _ ->
    addShipList(columns, rows, iconSize, baseColor, ships, 0f)
}

/**
 * A component that shows vanilla's fleet summary, as in a fleet's tooltip: each ship's icon, name and class, in two
 * columns when there is room (330 pixels or more). With no [ships], it shows vanilla's "None".
 *
 * By default it wraps its content, at the width there is room for. Since it reads live game objects, it is made again
 * each time its builder runs.
 *
 * @param ships the ships shown
 * @param size the size of the [FleetSummary]
 * @param modifier the [Modifier] to be applied to this summary
 * @param sort whether to order the ships largest first
 * @param showBaseHullForDHulls whether d-hulls are shown as their base hull
 * @return the panel holding the vanilla summary
 */
fun UIPanelAPI.FleetSummary(
    ships: List<FleetMemberAPI>,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    sort: Boolean = true,
    showBaseHullForDHulls: Boolean = false
): CustomPanelAPI = VanillaElement("FleetSummary", size, modifier, inputs = null /* live fleet members */) { _ ->
    // max = the whole list, so vanilla adds no "... and N other ships" line after the summary
    showShips(ships, maxOf(1, ships.size), sort, showBaseHullForDHulls, 0f)
}

/**
 * A component that shows vanilla's list of codex links under [title]. Each entry opens its codex page when clicked.
 *
 * By default it wraps its content, at the width there is room for. It is made again when one of its arguments changes.
 *
 * @param title the title above the links
 * @param entryIds the ids of the codex entries
 * @param size the size of the [CodexEntries]
 * @param modifier the [Modifier] to be applied to this list
 * @param sort whether vanilla sorts the entries
 * @return the panel holding the vanilla list
 */
fun UIPanelAPI.CodexEntries(
    title: String,
    entryIds: Set<String>,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    sort: Boolean = true
): CustomPanelAPI = VanillaElement("CodexEntries", size, modifier, inputs = listOf(title, entryIds.toSet(), sort)) { _ ->
    addCodexEntries(title, entryIds, sort, 0f)
}

// ---------------------------------------------------------------------------------------------------------------
// Skill icons
// ---------------------------------------------------------------------------------------------------------------

/**
 * Vanilla's framed skill icon for [skillId] at a [stats]' level: the engine's own icon, found at load, which draws
 * the skill's art (dimmed when not learned), the cut-corner frame in the aptitude's colour (doubled when learned) and
 * the elite frame at level 2, and reads the level from [stats] every frame. It builds its sprites for its first size,
 * so it is made at its final size.
 */
private fun newSkillIcon(skillId: String, stats: MutableCharacterStatsAPI, thinBorder: Boolean, width: Float, height: Float): UIComponentAPI {
    val constructor = VanillaClasses.skillIconConstructor ?: throw IllegalStateException(
        "StarUI: SkillIcon needs vanilla's skill icon class, which was not found at load (the log says why)."
    )
    val spec = Global.getSettings().getSkillSpec(skillId)
        ?: throw IllegalArgumentException("SkillIcon: no skill with the id \"$skillId\"")
    // vanilla's frame colour: the aptitude's, a third of the way to the light highlight colour, opaque
    val light = Misc.setAlpha(Global.getSettings().getColor("tooltipTitleAndLightHighlightColor"), 255)
    val color = Misc.interpolateColor(spec.governingAptitudeColor, light, 0.35f)
    val icon = constructor.newInstance(spec, stats, color, false) as UIComponentAPI
    if (thinBorder) icon.invoke("setThinBorder", true)   // read when the icon builds itself: set before its size
    icon.position.setSize(width, height)
    return icon
}

/** A person whose only skill is [skillId] at [level], for an icon that shows a level rather than someone's. */
private fun skillLevelHolder(skillId: String, level: Float): MutableCharacterStatsAPI =
    Global.getFactory().createPerson().stats.apply { setSkillLevel(skillId, level) }

/**
 * A component that shows vanilla's framed skill icon, as on the character screen and in vanilla's skill panels: the
 * skill's art in its aptitude's cut-corner frame, with the elite frame for an elite skill and the art dimmed for one
 * not learned. For a compact strip of skills; [SkillPanel] shows a person's whole panel, with names.
 *
 * This overload shows a level you give: [learned] and [elite]. The other one shows a person's own level, live.
 *
 * It has vanilla's look and no tooltip of its own (vanilla's skill tooltip is not in the API): add one with
 * [Modifier.tooltip][org.starficz.starui.modifier.tooltip]. It is made again when its size or its arguments change.
 *
 * Example usage:
 *
 * ```kotlin
 * Row(spacing = 4f) {
 *     for (id in listOf("helmsmanship", "target_analysis")) {
 *         SkillIcon(id, elite = id == "helmsmanship", modifier = Modifier.tooltip(Global.getSettings().getSkillSpec(id).name))
 *     }
 * }
 * ```
 *
 * @param skillId the skill's id (`"helmsmanship"`, `Skills.HELMSMANSHIP`)
 * @param size the size of the icon: exact or fill, 40 x 40 (vanilla's skill panel) by default; the elite frame
 *   follows it (vanilla has one for 64 and over, one for 48 and over, and one below)
 * @param modifier the [Modifier] to be applied to this icon
 * @param learned whether the skill is shown as learned; not learned dims its art and thins its frame
 * @param elite whether the skill is shown as elite
 * @param thinBorder whether to draw vanilla's thin frame (one line, no glow), for small icons
 * @return the panel holding the vanilla icon
 */
fun UIPanelAPI.SkillIcon(
    skillId: String,
    size: BoundedSize = Size.exact(40f, 40f),
    modifier: Modifier = Modifier,
    learned: Boolean = true,
    elite: Boolean = false,
    thinBorder: Boolean = false
): CustomPanelAPI {
    val level = if (!learned) 0f else if (elite) 2f else 1f
    return VanillaSizedComponent("SkillIcon", size, modifier, inputs = listOf(skillId, level, thinBorder)) { w, h ->
        newSkillIcon(skillId, skillLevelHolder(skillId, level), thinBorder, w, h)
    }
}

/**
 * A component that shows vanilla's framed skill icon at [person]'s level of [skillId], read every frame: dimmed
 * until they learn it, the elite frame once they make it elite. See the other overload for the look.
 *
 * @param skillId the skill's id
 * @param person the person whose level is shown
 * @param size the size of the icon: exact or fill, 40 x 40 by default
 * @param modifier the [Modifier] to be applied to this icon
 * @param thinBorder whether to draw vanilla's thin frame (one line, no glow), for small icons
 * @return the panel holding the vanilla icon
 */
fun UIPanelAPI.SkillIcon(
    skillId: String,
    person: PersonAPI,
    size: BoundedSize = Size.exact(40f, 40f),
    modifier: Modifier = Modifier,
    thinBorder: Boolean = false
): CustomPanelAPI = VanillaSizedComponent("SkillIcon", size, modifier, inputs = listOf(skillId, person, thinBorder)) { w, h ->
    newSkillIcon(skillId, person.stats, thinBorder, w, h)
}

// ---------------------------------------------------------------------------------------------------------------
// Campaign content: needs a running campaign (people, intel)
// ---------------------------------------------------------------------------------------------------------------

/**
 * A component that shows vanilla's skill panel for [person], as on the character and officer screens: their skills
 * by aptitude, with elite markers and each skill's tooltip. Needs a campaign.
 *
 * Since it reads live game objects, it is made again each time its builder runs.
 *
 * @param person the person whose skills are shown
 * @param size the size of the [SkillPanel]
 * @param modifier the [Modifier] to be applied to this panel
 * @param admin whether to show the skills of an administrator
 * @param oneColumn whether to stack the aptitudes in one column, for narrow places
 * @return the panel holding the vanilla skill panel
 */
fun UIPanelAPI.SkillPanel(
    person: PersonAPI,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    admin: Boolean = false,
    oneColumn: Boolean = false
): CustomPanelAPI = VanillaElement("SkillPanel", size, modifier, inputs = null /* the live person */) { _ ->
    if (oneColumn) addSkillPanelOneColumn(person, admin, 0f) else addSkillPanel(person, admin, 0f)
}

/**
 * A component that shows an event's progress bar as its intel screen does (Hostile Activity, Hyperspace Topography,
 * ...): the bar, the icon of each stage above it at its progress, the marker of the current progress below it, and
 * the event's own tooltips on the bar and the stage icons. Needs a campaign.
 *
 * Vanilla's bar is the event's own `getBarWidth()` wide (750 pixels for most events) whatever room it gets, so the
 * component is built at that width, plus the event's bar offset and half a stage icon at the end, and wraps it.
 * Since it reads live game objects, it is made again each time its builder runs.
 *
 * @param intel the event shown
 * @param size the size of the [EventProgressBar]
 * @param modifier the [Modifier] to be applied to this bar
 * @return the panel holding the vanilla bar
 */
fun UIPanelAPI.EventProgressBar(
    intel: BaseEventIntel,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier
): CustomPanelAPI = VanillaComponent(
    "EventProgressBar", size, modifier, inputs = null /* the live event */,
    fixedWidth = maxOf(0f, intel.barXOffset) + intel.barWidth +
        (intel.stages.maxOfOrNull { intel.createDisplayData(it.id).size } ?: 0f) / 2f
) { width ->
    buildTooltipBlock(width) {
        // the stages shown, as BaseEventIntel.createLargeDescription picks them
        val shown = intel.stages.filter { curr ->
            !(curr.progress <= 0 && !curr.showIconIfFirstStage) &&
                BaseEventIntel.RANDOM_EVENT_NONE != curr.rollData &&
                !(curr.wasEverReached && curr.isOneOffEvent && !curr.isRepeatable) &&
                !(curr.hideIconWhenPastStageUnlessLastActive && curr.progress <= intel.progress &&
                    intel.getLastActiveStage(true) != curr)
        }
        val displays = shown.map { intel.createDisplayData(it.id) }
        // room above the bar for the stage icons and their lines
        val above = displays.maxOfOrNull { it.size + it.downLineLength } ?: 0f
        val extraXOff = intel.barXOffset
        val bar = addEventProgressBar(intel, above + 2f)
        if (extraXOff != 0f) bar.position.setXAlignOffset(extraXOff)
        intel.barTooltip?.let { addTooltipToPrevious(it, TooltipLocation.BELOW, false) }
        for ((stage, data) in shown.zip(displays)) {
            val marker = addEventStageMarker(data)
            val xOff = bar.getXCoordinateForProgress(stage.progress.toFloat()) - bar.position.x + extraXOff
            marker.position.aboveLeft(bar, data.downLineLength).setXAlignOffset(xOff - data.size / 2f - 1)
            intel.getStageTooltip(stage.id)?.let { addTooltipTo(it, marker, TooltipLocation.LEFT, false) }
        }
        val marker = addEventProgressMarker(intel)
        val xOff = bar.getXCoordinateForProgress(intel.progress.toFloat()) - bar.position.x
        marker.position.belowLeft(bar, -intel.barProgressIndicatorHeight * 0.5f - 2)
            .setXAlignOffset(xOff - intel.barProgressIndicatorWidth / 2 - 1)
        // the progress marker reaches below the bar
        addSpacer(intel.barProgressIndicatorHeight * 0.5f)
    }
}

/**
 * A component that shows vanilla's planet display, as in planet tooltips and the survey dialog: the planet, lit by
 * its star, at the size it is given. Shows nothing for a planet that is not in the campaign; the sector's own
 * `PlanetAPI` objects are. Needs a campaign.
 *
 * Since it reads live game objects, it is made again each time its builder runs.
 *
 * @param planet the planet shown
 * @param size the size of the [PlanetInfo]
 * @param modifier the [Modifier] to be applied to this display
 * @param showName whether to show the planet's name
 * @param withClass whether to show the planet's type under its name
 * @param showConditions whether to show the row of condition icons
 * @param showHazardRating whether to show the hazard rating
 * @return the panel holding the vanilla display
 */
fun UIPanelAPI.PlanetInfo(
    planet: PlanetAPI,
    size: BoundedSize = Size.exact(400f, 400f),
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    withClass: Boolean = false,
    showConditions: Boolean = false,
    showHazardRating: Boolean = false
): CustomPanelAPI = VanillaSizedComponent("PlanetInfo", size, modifier, inputs = null /* the live planet */) { width, height ->
    val params = TooltipMakerAPI.PlanetInfoParams().apply {
        this.showName = showName
        this.withClass = withClass
        this.showConditions = showConditions
        this.showHazardRating = showHazardRating
    }
    buildFromTooltip(width) { showPlanetInfo(planet, width, height, params, 0f) }
}

/**
 * A component that shows what the player's fleet needs to survey [planet] fully, as in the survey dialog: each
 * resource, the amount, and whether the fleet has it. Needs a campaign: vanilla reads the player fleet's cargo.
 *
 * By default it wraps its content, at the width there is room for. Since it reads live game objects, it is made again
 * each time its builder runs.
 *
 * @param planet the planet to survey
 * @param size the size of the [SurveyRequirements]
 * @param modifier the [Modifier] to be applied to this display
 * @param withText whether to show vanilla's line of text above the grid
 * @return the panel holding the vanilla display
 */
fun UIPanelAPI.SurveyRequirements(
    planet: PlanetAPI,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    withText: Boolean = true
): CustomPanelAPI = VanillaComponent("SurveyRequirements", size, modifier, inputs = null /* the live planet and cargo */) { width ->
    buildTooltipBlock(width) { showFullSurveyReqs(planet, withText, 0f) }
}

/**
 * A component that shows vanilla's cargo list, as in a fleet's or a market's tooltip: an icon, the name and the
 * quantity of each stack, in two columns when there is room (330 pixels or more).
 *
 * By default it wraps its content, at the width there is room for. Since it reads live game objects, it is made again
 * each time its builder runs.
 *
 * @param cargo the cargo shown
 * @param size the size of the [CargoList]
 * @param modifier the [Modifier] to be applied to this list
 * @param max the largest number of stacks shown
 * @param sort whether to order the stacks largest first
 * @param itemHeight the height of a stack's line
 * @param itemPad the gap between two lines
 * @return the panel holding the vanilla list
 */
fun UIPanelAPI.CargoList(
    cargo: CargoAPI,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    max: Int = 100,
    sort: Boolean = true,
    itemHeight: Float = 20f,
    itemPad: Float = 3f
): CustomPanelAPI = VanillaComponent("CargoList", size, modifier, inputs = null /* the live cargo */) { width ->
    buildTooltipBlock(width) { showCargo(cargo, max, sort, 0f, itemHeight, itemPad) }
}

/**
 * A component that shows vanilla's playthrough record, the intel screen's "Playthrough" tab: the timeline of the
 * player's achievements, with its own scrolling table, at the size it is given. Needs a campaign.
 *
 * Since it reads live game objects, it is made again each time its builder runs.
 *
 * @param size the size of the [PlaythroughData]
 * @param modifier the [Modifier] to be applied to this record
 * @return the panel holding the vanilla record
 */
fun UIPanelAPI.PlaythroughData(
    size: BoundedSize = Size.fillMaxSize(),
    modifier: Modifier = Modifier
): CustomPanelAPI = VanillaSizedComponent("PlaythroughData", size, modifier, inputs = null /* the live campaign */) { width, height ->
    buildTooltipBlock(width) { addPlaythroughDataPanel(width, height) }
}

/**
 * The vanilla map of a [SectorMap], boxed: its pings, centring and zoom, and the panel it is. `null` for any other
 * panel, and before the map is first laid out, since vanilla makes a map at the size it gets.
 *
 * Vanilla lays a map out once, so StarUI makes it again when its size changes: read this when you use it, and do
 * not keep it.
 */
val CustomPanelAPI.sectorMap: BoxedSectorMap?
    get() = findSectorMap(this)?.let { BoxedSectorMap(it) }

internal fun findSectorMap(component: UIComponentAPI): SectorMapAPI? {
    if (component is SectorMapAPI) return component
    if (component !is UIPanelAPI) return null
    return component.getChildrenNonCopy().firstNotNullOfOrNull { findSectorMap(it) }
}

/**
 * A component that shows vanilla's sector map, as in the intel and mission screens, at the size it is given and
 * showing what [params] asks for (`MapParams.showSystem`, `positionToShowAllMarkersAndSystems`, filters, ...). With a
 * [title], the map sits in vanilla's titled box. Needs a campaign.
 *
 * `.sectorMap` on the returned panel gives the map as a [BoxedSectorMap] (pings, `centerOn`, `centerOnEntity`,
 * `zoomOutAndCenter`) once it is laid out. Vanilla lays a map out once, so a size change makes it again, keeping
 * its centre. It is also made again each time its builder runs.
 *
 * @param params what the map shows
 * @param size the size of the [SectorMap]
 * @param modifier the [Modifier] to be applied to this map
 * @param title the title of the box around the map, or `null` for a map without a box
 * @param color the main colour of the box. By default the player faction's UI colour
 * @param dark the dark colour of the box. By default the player faction's dark UI colour
 * @return the panel holding the vanilla map
 */
fun UIPanelAPI.SectorMap(
    params: MapParams,
    size: BoundedSize = Size.fillMaxSize(),
    modifier: Modifier = Modifier,
    title: String? = null,
    color: Color = Global.getSector()?.playerFaction?.baseUIColor ?: VanillaColors.base,
    dark: Color = Global.getSector()?.playerFaction?.darkUIColor ?: VanillaColors.dark
): CustomPanelAPI = VanillaSizedComponent("SectorMap", size, modifier, inputs = null /* the live sector */, onSwap = ::keepMapCenter) { width, height ->
    val tempPanel = Global.getSettings().createCustom(width, height, null)
    val tooltip = tempPanel.createUIElement(width, height, false)
    tooltip.createSectorMap(width, height, params, title, color, dark)
}

/** A sector map made again (a new size) starts where the old one was looking. */
private fun keepMapCenter(old: UIComponentAPI?, made: UIComponentAPI) {
    val oldMap = old?.let { findSectorMap(it) } ?: return
    val newMap = findSectorMap(made) ?: return
    BoxedSectorMap(newMap).centerOn(BoxedSectorMap(oldMap).worldCenter)
}
