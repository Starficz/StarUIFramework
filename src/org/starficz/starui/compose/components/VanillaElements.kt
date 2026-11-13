package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.campaign.PersonImportance
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.MutableStat
import com.fs.starfarer.api.combat.StatBonus
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.IconRenderMode
import com.fs.starfarer.api.ui.MapParams
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation
import org.starficz.starui.core.buildTooltipBlock
import org.starficz.starui.compose.BoundedSize
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.StarUIDsl
import org.starficz.starui.compose.layout.VanillaElementEngine
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.core.BoxedSectorMap
import org.starficz.starui.core.VanillaColors
import org.starficz.starui.core.buildFromTooltip
import java.awt.Color

// Vanilla TooltipMakerAPI content as StarUI components. Each is built on a temporary tooltip at the width its
// container gives it (or a fixed one), detached and held in a StarUI panel, which is what each call returns. It is
// made again when the builder calling it runs again and whenever its width changes. All of them wrap their content
// by default, at the width there is room for.

/** Holds one vanilla component made by [build] at a width (see VanillaElementEngine). */
private fun UIPanelAPI.VanillaElement(
    kind: String,
    size: Size,
    modifier: Modifier,
    fixedWidth: Float? = null,
    build: TooltipMakerAPI.(width: Float) -> Unit
): CustomPanelAPI = VanillaComponent(kind, size, modifier, fixedWidth) { width -> buildFromTooltip(width) { build(width) } }

/** Like [VanillaElement], with [make] building the component for a width itself. */
private fun UIPanelAPI.VanillaComponent(
    kind: String,
    size: Size,
    modifier: Modifier,
    fixedWidth: Float? = null,
    make: (width: Float) -> UIComponentAPI
): CustomPanelAPI {
    checkDslTarget()
    val slot = childSlot(kind)
    val (panel, plugin) = containerPanel(slot, size)
    val engine = (plugin.layoutEngine as? VanillaElementEngine) ?: VanillaElementEngine(plugin).also { plugin.layoutEngine = it }
    engine.fixedWidth = fixedWidth
    engine.afterBuild = null
    engine.afterLayout = null
    engine.buildSized = null
    engine.onSwap = null
    engine.build = make

    registerComponentSizing(panel, size, isContainer = true, containerPlugin = plugin)
    slot.remember(panel, panel)
    modifier.operations.forEach { it(panel) }
    engine.invalidate()
    return panel
}

/**
 * Holds one vanilla component made at the whole size its container gets by [make] (content vanilla lays out once,
 * at a fixed size), made again when that size changes. [onSwap] sees each new component before it goes in.
 */
private fun UIPanelAPI.VanillaSizedComponent(
    kind: String,
    size: BoundedSize,
    modifier: Modifier,
    onSwap: ((old: UIComponentAPI?, made: UIComponentAPI) -> Unit)? = null,
    make: (width: Float, height: Float) -> UIComponentAPI
): CustomPanelAPI {
    checkDslTarget()
    val slot = childSlot(kind)
    val (panel, plugin) = containerPanel(slot, size)
    val engine = (plugin.layoutEngine as? VanillaElementEngine) ?: VanillaElementEngine(plugin).also { plugin.layoutEngine = it }
    engine.fixedWidth = null
    engine.afterBuild = null
    engine.afterLayout = null
    engine.onSwap = onSwap
    engine.buildSized = make

    registerComponentSizing(panel, size, isContainer = true, containerPlugin = plugin)
    slot.remember(panel, panel)
    modifier.operations.forEach { it(panel) }
    engine.invalidate()
    return panel
}

// ---------------------------------------------------------------------------------------------------------------
// Stat grids
// ---------------------------------------------------------------------------------------------------------------

/** The entries of a [StatGrid], in reading order (left to right, then down). */
@StarUIDsl
class StatGridScope internal constructor() {
    internal val entries = ArrayList<Triple<String, String, Color?>>()

    /** One label and its value; [valueColor] overrides the grid's value colour (vanilla's highlight). */
    fun entry(label: String, value: String, valueColor: Color? = null) {
        entries += Triple(label, value, valueColor)
    }
}

/**
 * Vanilla's label and value grid (as in ship and weapon stat tooltips): the entries in [columns] columns, filled left
 * to right, then down. `valueFirst = true` is vanilla's flipped grid: each value in a [valueWidth] column before its
 * label, [valuePad] apart.
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
    return VanillaElement("StatGrid", size, modifier) { width ->
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
 * Vanilla's breakdown of a stat by source (as in the refit screen's stat tooltips): one line per modifier, its value
 * in a [valueWidth] column. [getter] formats the values; null uses vanilla's.
 */
fun UIPanelAPI.StatModGrid(
    stat: MutableStat,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    valueWidth: Float = 50f,
    valuePad: Float = 10f,
    getter: TooltipMakerAPI.StatModValueGetter? = null
): CustomPanelAPI = VanillaElement("StatModGrid", size, modifier) { width ->
    if (getter != null) addStatModGrid(width, valueWidth, valuePad, 0f, stat, getter)
    else addStatModGrid(width, valueWidth, valuePad, 0f, stat)
}

/** [StatModGrid] for a [StatBonus]. */
fun UIPanelAPI.StatModGrid(
    stat: StatBonus,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    valueWidth: Float = 50f,
    valuePad: Float = 10f,
    getter: TooltipMakerAPI.StatModValueGetter? = null
): CustomPanelAPI = VanillaElement("StatModGrid", size, modifier) { width ->
    if (getter != null) addStatModGrid(width, valueWidth, valuePad, 0f, stat, getter)
    else addStatModGrid(width, valueWidth, valuePad, 0f, stat)
}

/** Vanilla's labelled value box (as on the market screen): [label] above [value], 42 tall. */
fun UIPanelAPI.LabelledValue(
    label: String,
    value: String,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    labelColor: Color = VanillaColors.text,
    valueColor: Color = VanillaColors.highlight
): CustomPanelAPI = VanillaElement("LabelledValue", size, modifier) { width ->
    addLabelledValue(label, value, labelColor, valueColor, width, 0f)
}

// ---------------------------------------------------------------------------------------------------------------
// Relationship and importance
// ---------------------------------------------------------------------------------------------------------------

/** Vanilla's reputation bar for a relationship [value] from -1 (vengeful) to 1 (cooperative), 19 tall. */
fun UIPanelAPI.RelationshipBar(value: Float, size: Size = Size.wrapContent(), modifier: Modifier = Modifier): CustomPanelAPI =
    VanillaElement("RelationshipBar", size, modifier) { width -> addRelationshipBar(value, width, 0f) }

/** [RelationshipBar] for the player's standing with [faction]. Needs a campaign. */
fun UIPanelAPI.RelationshipBar(faction: FactionAPI, size: Size = Size.wrapContent(), modifier: Modifier = Modifier): CustomPanelAPI =
    VanillaElement("RelationshipBar", size, modifier) { width -> addRelationshipBar(faction, width, 0f) }

/** [RelationshipBar] for the player's standing with [person]. Needs a campaign. */
fun UIPanelAPI.RelationshipBar(person: PersonAPI, size: Size = Size.wrapContent(), modifier: Modifier = Modifier): CustomPanelAPI =
    VanillaElement("RelationshipBar", size, modifier) { width -> addRelationshipBar(person, width, 0f) }

/** Vanilla's importance pips for a contact or other person (very low to very high). */
fun UIPanelAPI.ImportanceIndicator(importance: PersonImportance, size: Size = Size.wrapContent(), modifier: Modifier = Modifier): CustomPanelAPI =
    VanillaElement("ImportanceIndicator", size, modifier) { width -> addImportanceIndicator(importance, width, 0f) }

// ---------------------------------------------------------------------------------------------------------------
// Commodities and costs
// ---------------------------------------------------------------------------------------------------------------

/** The icons of a [CommodityIcons] group, in order. */
@StarUIDsl
class CommodityIconsScope internal constructor() {
    internal val icons = ArrayList<Triple<CommoditySpecAPI, Int, IconRenderMode>>()

    /** [count] icons of a commodity (`"supplies"`, `Commodities.FUEL`, ...), drawn in [mode] (dim, red, green, ...). */
    fun icons(commodityId: String, count: Int, mode: IconRenderMode = IconRenderMode.NORMAL) {
        val spec = Global.getSettings().getCommoditySpec(commodityId)
            ?: throw IllegalArgumentException("CommodityIcons: no commodity \"$commodityId\"")
        icons += Triple(spec, count, mode)
    }

    fun icons(commodity: CommoditySpecAPI, count: Int, mode: IconRenderMode = IconRenderMode.NORMAL) {
        icons += Triple(commodity, count, mode)
    }
}

/**
 * Vanilla's row of commodity icons (as in market supply and demand tooltips), wrapping onto more rows when they do not
 * fit. [rowHeight] sets the icon size; [rows] fixes the number of rows; [centered] centres a single row.
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
    return VanillaElement("CommodityIcons", size, modifier) { _ ->
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

/** The costs of a [CostDisplay], in order. */
@StarUIDsl
class CostDisplayScope internal constructor() {
    internal val costs = ArrayList<Triple<String, Int, Boolean>>()

    /** [quantity] of a commodity; [consumed] = false shows it as needed but not used up (vanilla draws it dimmer). */
    fun cost(commodityId: String, quantity: Int, consumed: Boolean = true) {
        costs += Triple(commodityId, quantity, consumed)
    }
}

/**
 * Vanilla's resource cost boxes (as in build and survey dialogs), under [title] (none when null). With
 * [withAvailable] each shows the player's stock and turns red when it is short. Needs a campaign: vanilla reads the
 * player fleet's cargo even without [withAvailable].
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
    return VanillaElement("CostDisplay", size, modifier) { width ->
        showCost(title, withAvailable, width, boxHeight ?: -1f, color, darkColor, 0f,
            items.map { it.first }.toTypedArray(), items.map { it.second }.toIntArray(), items.map { it.third }.toBooleanArray())
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Ships and codex
// ---------------------------------------------------------------------------------------------------------------

/**
 * Vanilla's grid of ship icons (as in fleet tooltips): [columns] by [rows] icons [iconSize] square, in [baseColor].
 * Ships beyond columns x rows are left out.
 */
fun UIPanelAPI.ShipList(
    ships: List<FleetMemberAPI>,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    columns: Int = 8,
    rows: Int = 1,
    iconSize: Float = 48f,
    baseColor: Color = VanillaColors.base
): CustomPanelAPI = VanillaElement("ShipList", size, modifier) { _ ->
    addShipList(columns, rows, iconSize, baseColor, ships, 0f)
}

/**
 * Vanilla's fleet summary (as in a fleet's tooltip): each ship's icon, name and class, in two columns when there is
 * room (330 wide or more). [sort] orders them largest first; empty shows vanilla's "None".
 */
fun UIPanelAPI.FleetSummary(
    ships: List<FleetMemberAPI>,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    sort: Boolean = true,
    showBaseHullForDHulls: Boolean = false
): CustomPanelAPI = VanillaElement("FleetSummary", size, modifier) { _ ->
    // max = the whole list, so vanilla adds no "... and N other ships" line after the summary
    showShips(ships, maxOf(1, ships.size), sort, showBaseHullForDHulls, 0f)
}

/** Vanilla's list of codex links under [title]: each entry id opens its codex page when clicked. */
fun UIPanelAPI.CodexEntries(
    title: String,
    entryIds: Set<String>,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    sort: Boolean = true
): CustomPanelAPI = VanillaElement("CodexEntries", size, modifier) { _ ->
    addCodexEntries(title, entryIds, sort, 0f)
}

// ---------------------------------------------------------------------------------------------------------------
// Campaign content: needs a running campaign (people, intel)
// ---------------------------------------------------------------------------------------------------------------

/**
 * Vanilla's skill panel for [person]: their skills by aptitude, with elite markers and each skill's tooltip, as on
 * the character and officer screens. `admin = true` shows an administrator's skills; `oneColumn = true` stacks the
 * aptitudes in one column (for narrow places). Needs a campaign.
 */
fun UIPanelAPI.SkillPanel(
    person: PersonAPI,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    admin: Boolean = false,
    oneColumn: Boolean = false
): CustomPanelAPI = VanillaElement("SkillPanel", size, modifier) { _ ->
    if (oneColumn) addSkillPanelOneColumn(person, admin, 0f) else addSkillPanel(person, admin, 0f)
}

/**
 * An event's progress bar as its intel screen shows it (Hostile Activity, Hyperspace Topography, ...): the bar,
 * the icon of each stage above it at its progress, the marker of the current progress below it, and the event's own
 * tooltips on the bar and the stage icons. Fills the width it gets. Needs a campaign.
 */
fun UIPanelAPI.EventProgressBar(
    intel: BaseEventIntel,
    size: Size = Size.fillMaxWidth(),
    modifier: Modifier = Modifier
): CustomPanelAPI = VanillaComponent("EventProgressBar", size, modifier) { width ->
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
 * Vanilla's planet display (as in planet tooltips and the survey dialog): the planet, lit by its star, at the size it
 * gets; [showName] adds its name, [withClass] its type under it, [showConditions] the row of condition icons and
 * [showHazardRating] the hazard rating. Shows nothing for a planet that is not in the campaign (`PlanetAPI` objects
 * from the sector are). Needs a campaign.
 */
fun UIPanelAPI.PlanetInfo(
    planet: PlanetAPI,
    size: BoundedSize = Size.exact(400f, 400f),
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    withClass: Boolean = false,
    showConditions: Boolean = false,
    showHazardRating: Boolean = false
): CustomPanelAPI = VanillaSizedComponent("PlanetInfo", size, modifier) { width, height ->
    val params = TooltipMakerAPI.PlanetInfoParams().apply {
        this.showName = showName
        this.withClass = withClass
        this.showConditions = showConditions
        this.showHazardRating = showHazardRating
    }
    buildFromTooltip(width) { showPlanetInfo(planet, width, height, params, 0f) }
}

/**
 * What the player's fleet needs to survey [planet] fully (as in the survey dialog): each resource, the amount, and
 * whether the fleet has it. [withText] adds vanilla's line above the grid. Needs a campaign: vanilla reads the player
 * fleet's cargo.
 */
fun UIPanelAPI.SurveyRequirements(
    planet: PlanetAPI,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    withText: Boolean = true
): CustomPanelAPI = VanillaComponent("SurveyRequirements", size, modifier) { width ->
    buildTooltipBlock(width) { showFullSurveyReqs(planet, withText, 0f) }
}

/**
 * Vanilla's cargo list (as in a fleet's or a market's tooltip): an icon, the name and the quantity of each stack, in
 * two columns when there is room (330 wide or more), up to [max] stacks. [sort] orders them largest first.
 */
fun UIPanelAPI.CargoList(
    cargo: CargoAPI,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    max: Int = 100,
    sort: Boolean = true,
    itemHeight: Float = 20f,
    itemPad: Float = 3f
): CustomPanelAPI = VanillaComponent("CargoList", size, modifier) { width ->
    buildTooltipBlock(width) { showCargo(cargo, max, sort, 0f, itemHeight, itemPad) }
}

/**
 * Vanilla's playthrough record (the intel screen's "Playthrough" tab: the timeline of the player's achievements, with
 * its own scrolling table) at the size it gets. Needs a campaign.
 */
fun UIPanelAPI.PlaythroughData(
    size: BoundedSize = Size.fillMaxSize(),
    modifier: Modifier = Modifier
): CustomPanelAPI = VanillaSizedComponent("PlaythroughData", size, modifier) { width, height ->
    buildTooltipBlock(width) { addPlaythroughDataPanel(width, height) }
}

/**
 * Vanilla's sector map (as in intel and mission screens) at the size it gets, showing what [params] asks for
 * (`MapParams.showSystem`, `positionToShowAllMarkersAndSystems`, filters, ...). With a [title] it sits in vanilla's
 * titled box, in [color] and [dark]. Returns a [BoxedSectorMap]: SectorMapAPI (pings) and the map's centring and zoom.
 * Vanilla lays a map out once, so a size change makes it again (keeping its centre), and so does the builder calling
 * this running again. Needs a campaign.
 */
fun UIPanelAPI.SectorMap(
    params: MapParams,
    size: BoundedSize = Size.fillMaxSize(),
    modifier: Modifier = Modifier,
    title: String? = null,
    color: Color = Global.getSector()?.playerFaction?.baseUIColor ?: VanillaColors.base,
    dark: Color = Global.getSector()?.playerFaction?.darkUIColor ?: VanillaColors.dark
): BoxedSectorMap {
    lateinit var boxed: BoxedSectorMap
    val panel = VanillaSizedComponent("SectorMap", size, modifier, onSwap = { old, made -> boxed.swapped(old, made) }) { width, height ->
        val tempPanel = Global.getSettings().createCustom(width, height, null)
        val tooltip = tempPanel.createUIElement(width, height, false)
        tooltip.createSectorMap(width, height, params, title, color, dark)
    }
    boxed = boxedMaps.getOrPut(panel) { BoxedSectorMap(panel) }
    return boxed
}

/** One box per map panel, so a reused panel keeps its pending calls. */
private val boxedMaps = java.util.WeakHashMap<CustomPanelAPI, BoxedSectorMap>()
