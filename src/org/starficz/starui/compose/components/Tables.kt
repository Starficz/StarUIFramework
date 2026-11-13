package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.Fonts
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.StarUIDsl
import org.starficz.starui.compose.TextAlign
import org.starficz.starui.compose.layout.VanillaElementEngine
import org.starficz.starui.compose.layout.commitChildLayout
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.VanillaColors
import org.starficz.starui.core.buildFromTooltip
import java.awt.Color
import kotlin.experimental.ExperimentalTypeInference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** What one table cell shows: text (see [TableCellScope.cell]) or StarUI components ([TableCellScope.custom]). */
sealed class TableCell {
    // Compared to tell whether the table must be made again (see VanillaElementEngine.update).
    internal data class Text(
        val text: String, val color: Color?, val align: TextAlign?, val highlightedText: List<Pair<String, Color>>?
    ) : TableCell()

    /** Equal to any other: what a custom cell shows is its own Box's business (its own scope), not the table's. */
    internal class Custom(val builder: CustomPanelAPI.() -> Unit) : TableCell() {
        override fun equals(other: Any?) = other is Custom
        override fun hashCode() = 1
    }
}

/**
 * What a Table keeps across its calls: a table kept through a call that changed nothing it shows still calls the
 * newest [rowTooltip] and [onRowClick], with the newest [items]; and the row height and column widths it was made with.
 */
private class TableCallerState<T> {
    var items: List<T> = emptyList()
    var rowTooltip: (TooltipMakerAPI.(T) -> Unit)? = null
    var onRowClick: ((T) -> Unit)? = null
    var builtRowHeight = Float.NaN
    var builtWidths: List<Float> = emptyList()
}

/** The receiver of a column's content lambda: makes cells that are more than plain text. */
object TableCellScope {
    /** Text with its own [color], [align] (instead of the column's) or highlighted parts. Long text is cut to fit. */
    fun cell(text: String, color: Color? = null, align: TextAlign? = null,
             highlightedText: Collection<Pair<String, Color>>? = null): TableCell =
        TableCell.Text(text, color, align, highlightedText?.toList())

    /**
     * StarUI components in the cell: the builder runs in a Box the size of the cell, so place its children with
     * `Modifier.align(...)`. It is reactive like any builder. Rows grow to fit the tallest custom cell.
     */
    fun custom(builder: (@StarUIDsl CustomPanelAPI).() -> Unit): TableCell = TableCell.Custom(builder)
}

internal class TableColumnSpec<T>(
    val title: String,
    val width: Float?,
    val weight: Float,
    val align: TextAlign,
    val tooltip: String?,
    val content: TableCellScope.(T) -> TableCell
)

/** The columns of a [Table], declared in its trailing block, left to right. */
@StarUIDsl
class TableScope<T> internal constructor() {
    internal val columns = ArrayList<TableColumnSpec<T>>()

    /**
     * A text column: [content] gives a row's text. [width] makes it exactly that wide; without one it takes a share
     * of the table's leftover width by [weight]. [tooltip] shows over its header.
     */
    @OptIn(ExperimentalTypeInference::class)
    @OverloadResolutionByLambdaReturnType
    fun column(title: String, width: Float? = null, weight: Float = 1f, align: TextAlign = TextAlign.Center,
               tooltip: String? = null, content: TableCellScope.(T) -> String) {
        columns += TableColumnSpec(title, width, weight, align, tooltip) { item -> TableCell.Text(content(item), null, null, null) }
    }

    /** A column whose cells are made with `cell(...)` (styled text) or `custom { ... }` (StarUI components). */
    @OptIn(ExperimentalTypeInference::class)
    @OverloadResolutionByLambdaReturnType
    @JvmName("cellColumn")
    fun column(title: String, width: Float? = null, weight: Float = 1f, align: TextAlign = TextAlign.Center,
               tooltip: String? = null, content: TableCellScope.(T) -> TableCell) {
        columns += TableColumnSpec(title, width, weight, align, tooltip, content)
    }
}

/**
 * Vanilla's table (as on the intel and market screens): one row per item of [items], with the columns declared in
 * [columns]:
 *
 * ```
 * Table(ships.value, Size.fillMaxWidth(), emptyText = "No ships") {
 *     column("Ship", weight = 1f) { ship -> ship.name }
 *     column("Cost", width = 80f) { ship -> cell("${ship.cost}", color = VanillaColors.highlight) }
 *     column("", width = 70f) { ship -> custom { Button("Sell", modifier = Modifier.align(Alignment.Center)) { sell(ship) } } }
 * }
 * ```
 *
 * Columns with a `width` are exactly that wide; the others share what is left of the table's width by `weight`. With
 * only exact columns the table is as wide as they are plus vanilla's 3 px frame; with weighted ones give it a fill or
 * exact width. It is as tall
 * as its rows (at least three when empty, where it shows [emptyText]); every row is [itemHeight] tall, or as tall as the tallest custom cell needs.
 *
 * [rowKey] ties each row's custom cells to its item, like `key(...)`. [rowTooltip] adds a tooltip to every row and
 * [onRowClick] makes rows clickable. The table is made again when what it shows changes (its cells, columns, colours,
 * row height), or its width: a builder running again for something else keeps it. Returns the StarUI panel that
 * holds it.
 */
fun <T> UIPanelAPI.Table(
    items: List<T>,
    size: Size = StarUITheme.current.table.size,
    modifier: Modifier = Modifier,
    rowKey: ((T) -> Any)? = null,
    itemHeight: Float = StarUITheme.current.table.itemHeight,
    withHeader: Boolean = StarUITheme.current.table.withHeader,
    withBorder: Boolean = StarUITheme.current.table.withBorder,
    baseColor: Color = StarUITheme.current.table.baseColor ?: VanillaColors.base,
    darkColor: Color = StarUITheme.current.table.darkColor ?: VanillaColors.dark,
    brightColor: Color = StarUITheme.current.table.brightColor ?: VanillaColors.bright,
    emptyText: String = "",
    rowTooltipWidth: Float = 300f,
    rowTooltipLocation: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE,
    rowTooltip: ((@StarUIDsl TooltipMakerAPI).(T) -> Unit)? = null,
    onRowClick: ((T) -> Unit)? = null,
    columns: TableScope<T>.() -> Unit
): CustomPanelAPI {
    checkDslTarget()
    val specs = TableScope<T>().apply(columns).columns
    require(specs.isNotEmpty()) { "Table: declare at least one column in its trailing block" }
    val slot = childSlot("Table")
    val (panel, plugin) = containerPanel(slot, size)
    val engine = (plugin.layoutEngine as? VanillaElementEngine) ?: VanillaElementEngine(plugin).also { plugin.layoutEngine = it }

    registerComponentSizing(panel, size, isContainer = true, containerPlugin = plugin)
    slot.remember(panel, panel)
    modifier.operations.forEach { it(panel) }

    val exactWidth = specs.sumOf { (it.width ?: 0f).toDouble() }.toFloat()
    val weightSum = specs.filter { it.width == null }.sumOf { it.weight.toDouble() }.toFloat()
    engine.fixedWidth = if (weightSum == 0f) exactWidth + TABLE_FRAME_WIDTH else null
    engine.fallbackWidth = exactWidth + 100f * weightSum + TABLE_FRAME_WIDTH

    plugin.attachScope("Table", columns) {
        // Read every cell here, inside the builder, so a cell that reads a UIState rebuilds the table.
        val cells = items.map { item -> specs.map { spec -> spec.content(TableCellScope, item) } }
        val customCells = ArrayList<Triple<Int, Int, CustomPanelAPI>>()
        items.forEachIndexed { row, item ->
            val id = rowKey?.invoke(item) ?: row
            cells[row].forEachIndexed { col, cell ->
                if (cell is TableCell.Custom) {
                    val box = key(Pair(id, col)) { panel.Box(Size.fillMaxSize()) { cell.builder(this) } }
                    customCells += Triple(row, col, box)
                }
            }
        }
        val needsInput = customCells.isNotEmpty() || rowTooltip != null || onRowClick != null || specs.any { it.tooltip != null }

        // The table's listeners read these, so a table kept through this call still calls the newest lambdas.
        @Suppress("UNCHECKED_CAST")
        val state = (engine.callerState as? TableCallerState<T>) ?: TableCallerState<T>().also { engine.callerState = it }
        state.items = items
        state.rowTooltip = rowTooltip
        state.onRowClick = onRowClick

        // Rows are as tall as itemHeight or the tallest custom cell's content at its column's width, whichever is
        // more: a button in a 20 px row would spill over the next one.
        fun rowHeightFor(widths: List<Float>): Float {
            var rowHeight = itemHeight
            for ((_, col, box) in customCells) {
                val needed = StarUIPanelPlugin.getAttached(box)
                    ?.measure(widths[col], Float.MAX_VALUE)?.second ?: continue
                rowHeight = maxOf(rowHeight, ceil(needed))
            }
            return rowHeight
        }

        engine.build = { width ->
            val widths = columnWidths(specs, width, exactWidth, weightSum)
            val rowHeight = rowHeightFor(widths)
            state.builtWidths = widths
            state.builtRowHeight = rowHeight
            val table = buildFromTooltip(width) {
                val header = ArrayList<Any>()
                specs.forEachIndexed { i, spec -> header += spec.title; header += widths[i] }
                beginTable(baseColor, darkColor, brightColor, rowHeight, withBorder, withHeader, *header.toTypedArray())
                specs.forEachIndexed { i, spec -> spec.tooltip?.let { addTableHeaderTooltip(i, it) } }
                if (onRowClick != null) {
                    makeTableItemsClickable()
                    setActionListenerDelegate { data, _ ->
                        val index = (data as? IntelInfoPlugin.TableRowClickData)?.rowId as? Int
                        val now = state.items
                        if (index != null && index in now.indices) state.onRowClick?.invoke(now[index])
                    }
                }
                items.forEachIndexed { row, _ ->
                    addRow(*rowData(cells[row], specs, baseColor))
                    if (onRowClick != null) setIdForAddedRow(row)
                    if (rowTooltip != null) addTooltipToAddedRow(object : TooltipMakerAPI.TooltipCreator {
                        override fun isTooltipExpandable(tooltipParam: Any?) = false
                        override fun getTooltipWidth(tooltipParam: Any?) = rowTooltipWidth
                        override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any?) {
                            val item = state.items.getOrNull(row) ?: return
                            state.rowTooltip?.let { tooltip.it(item) }
                        }
                    }, rowTooltipLocation)
                }
                addTable(emptyText, 0, 0f)
            }
            if (needsInput) table.invoke("setSkipInputProcessing", false)
            table
        }
        // The custom cells sit over the table, so they draw above it and take the mouse first. Re-added rather than
        // brought to top: vanilla's bringComponentToTop also raises the panel in its parent, and so on up, which
        // reorders the Columns and Rows above.
        engine.afterBuild = { _ -> for ((_, _, box) in customCells) { panel.removeComponent(box); panel.addComponent(box) } }
        // Vanilla makes a row's cell panels when the row is first drawn, which can be after this layout (or much later,
        // while the table is scrolled out of view): check each frame until they exist, then lay out once more.
        var waitingForCells = false
        // A custom cell's own scope can change what it needs (its content grows) without the table's call changing:
        // the rows are then made again at the new height. Checked after each layout, acted on in the next advance.
        var rowsNeedRemaking = false
        engine.afterLayout = { table ->
            // measured before the cells are placed: placing a cell marks its Box laid out, and its measure is then
            // taken as current
            if (customCells.isNotEmpty() && state.builtWidths.size == specs.size &&
                abs(rowHeightFor(state.builtWidths) - state.builtRowHeight) > 0.5f) rowsNeedRemaking = true
            waitingForCells = !placeCustomCells(panel, table, customCells)
        }
        if (customCells.isNotEmpty()) plugin.advance {
            val table = engine.current
            if (rowsNeedRemaking) {
                rowsNeedRemaking = false
                engine.invalidate()
            } else if (waitingForCells && table != null && customCellsReady(table, customCells)) {
                waitingForCells = false
                plugin.requestLayout()
            }
        }
        engine.update(listOf(
            cells, specs.map { listOf(it.title, it.width, it.weight, it.align, it.tooltip) }, itemHeight, withHeader,
            withBorder, baseColor, darkColor, brightColor, emptyText, rowTooltipWidth, rowTooltipLocation,
            rowTooltip != null, onRowClick != null
        ))
        // this build's end removes every old child no call asked for, the vanilla table too: a kept one stays. (A
        // table about to be made again stays until the new one replaces it.)
        engine.current?.let { plugin.reuseCursor?.keep(it) }
    }
    return panel
}

/** How much wider vanilla's table is than the sum of its columns (its frame). */
private const val TABLE_FRAME_WIDTH = 3f

/**
 * Each column's width for a table [width] wide: exact ones as given, weighted ones sharing the rest (whole pixels), so
 * that the columns plus vanilla's frame make [width].
 */
private fun <T> columnWidths(specs: List<TableColumnSpec<T>>, width: Float, exactWidth: Float, weightSum: Float): List<Float> {
    val columnsWidth = width - TABLE_FRAME_WIDTH
    val leftover = maxOf(0f, columnsWidth - exactWidth)
    val widths = specs.map { spec -> spec.width ?: if (weightSum > 0f) floor(leftover * spec.weight / weightSum) else 0f }.toMutableList()
    // the rounding remainder goes to the last weighted column, so the columns fill the width exactly
    val lastWeighted = specs.indexOfLast { it.width == null }
    if (lastWeighted >= 0) widths[lastWeighted] += columnsWidth - widths.sum()
    return widths
}

/** The values vanilla's addRow takes for one row: a String, or Alignment, Color and a String or a label. */
private fun <T> rowData(cells: List<TableCell>, specs: List<TableColumnSpec<T>>, baseColor: Color): Array<Any> {
    val data = ArrayList<Any>()
    cells.forEachIndexed { i, cell ->
        val spec = specs[i]
        when (cell) {
            is TableCell.Custom -> data += ""
            is TableCell.Text -> {
                val align = (cell.align ?: spec.align).toVanilla()
                val color = cell.color ?: baseColor
                val highlights = cell.highlightedText
                if (highlights.isNullOrEmpty()) {
                    data += align; data += color; data += cell.text
                } else {
                    val label = Global.getSettings().createLabel(cell.text, Fonts.DEFAULT_SMALL)
                    label.setColor(color)
                    label.setHighlight(*highlights.map { it.first }.toTypedArray())
                    label.setHighlightColors(*highlights.map { it.second }.toTypedArray())
                    data += align; data += color; data += label
                }
            }
        }
    }
    return data.toTypedArray()
}

/** Puts each custom cell's Box over its cell panel (the row's column panel, read from the table); false when some cell panel does not exist yet. */
private fun placeCustomCells(panel: CustomPanelAPI, table: UIComponentAPI,
                             customCells: List<Triple<Int, Int, CustomPanelAPI>>): Boolean {
    if (customCells.isEmpty()) return true
    val plugin = panel.plugin as StarUIPanelPlugin
    val rows = table.invoke("getRows") as? List<*> ?: return true
    val panelLeft = panel.position.x
    val panelTop = panel.position.y + panel.position.height
    var waiting = false
    for ((row, col, box) in customCells) {
        val rowComponent = rows.getOrNull(row) ?: continue
        val cellPanel = cellPanel(rowComponent, col)
        if (cellPanel == null) { waiting = true; continue }
        val cell = cellPanel.position
        box.position.inTL(cell.x - panelLeft, panelTop - (cell.y + cell.height))
        commitChildLayout(box, plugin, cell.width, cell.height)
    }
    return !waiting
}

private fun cellPanel(row: Any, col: Int): UIComponentAPI? =
    runCatching { row.invoke("getCol", col) as? UIComponentAPI }.getOrNull()

/** Whether every custom cell's panel exists now. */
private fun customCellsReady(table: UIComponentAPI, customCells: List<Triple<Int, Int, CustomPanelAPI>>): Boolean {
    val rows = table.invoke("getRows") as? List<*> ?: return true
    return customCells.all { (row, col, _) -> rows.getOrNull(row)?.let { cellPanel(it, col) } != null }
}
