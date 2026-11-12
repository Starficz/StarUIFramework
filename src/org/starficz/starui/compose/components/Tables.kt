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
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.VanillaColors
import org.starficz.starui.core.buildFromTooltip
import java.awt.Color
import kotlin.experimental.ExperimentalTypeInference

/** What one table cell shows: text (see [TableCellScope.cell]) or StarUI components ([TableCellScope.custom]). */
sealed class TableCell {
    internal class Text(
        val text: String, val color: Color?, val align: TextAlign?, val highlightedText: Collection<Pair<String, Color>>?
    ) : TableCell()

    internal class Custom(val builder: CustomPanelAPI.() -> Unit) : TableCell()
}

/** The receiver of a column's content lambda: makes cells that are more than plain text. */
object TableCellScope {
    /** Text with its own [color], [align] (instead of the column's) or highlighted parts. Long text is cut to fit. */
    fun cell(text: String, color: Color? = null, align: TextAlign? = null,
             highlightedText: Collection<Pair<String, Color>>? = null): TableCell =
        TableCell.Text(text, color, align, highlightedText)

    /**
     * StarUI components in the cell: the builder runs in a Box the size of the cell, so place its children with
     * `Modifier.align(...)`. It is reactive like any builder. A cell cannot make its row taller than `itemHeight`.
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
 * as its rows (at least three when empty, where it shows [emptyText]); every row is [itemHeight] tall.
 *
 * [rowKey] ties each row's custom cells to its item, like `key(...)`. [rowTooltip] adds a tooltip to every row and
 * [onRowClick] makes rows clickable. The table is made again when the builder that calls it runs again, or when its
 * width changes. Returns the StarUI panel that holds it.
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

        engine.build = { width ->
            val widths = columnWidths(specs, width, exactWidth, weightSum)
            val table = buildFromTooltip(width) {
                val header = ArrayList<Any>()
                specs.forEachIndexed { i, spec -> header += spec.title; header += widths[i] }
                beginTable(baseColor, darkColor, brightColor, itemHeight, withBorder, withHeader, *header.toTypedArray())
                specs.forEachIndexed { i, spec -> spec.tooltip?.let { addTableHeaderTooltip(i, it) } }
                if (onRowClick != null) {
                    makeTableItemsClickable()
                    setActionListenerDelegate { data, _ ->
                        val index = (data as? IntelInfoPlugin.TableRowClickData)?.rowId as? Int
                        if (index != null && index in items.indices) onRowClick(items[index])
                    }
                }
                items.forEachIndexed { row, item ->
                    addRow(*rowData(cells[row], specs, baseColor))
                    if (onRowClick != null) setIdForAddedRow(row)
                    if (rowTooltip != null) addTooltipToAddedRow(object : TooltipMakerAPI.TooltipCreator {
                        override fun isTooltipExpandable(tooltipParam: Any?) = false
                        override fun getTooltipWidth(tooltipParam: Any?) = rowTooltipWidth
                        override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any?) {
                            tooltip.rowTooltip(item)
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
        engine.afterLayout = { table -> placeCustomCells(panel, plugin, table, customCells) }
        engine.invalidate()
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
    val widths = specs.map { spec -> spec.width ?: if (weightSum > 0f) kotlin.math.floor(leftover * spec.weight / weightSum) else 0f }.toMutableList()
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

/** Puts each custom cell's Box over its cell: the row's column panel, read from the table itself. */
private fun placeCustomCells(panel: CustomPanelAPI, plugin: org.starficz.starui.compose.runtime.StarUIPanelPlugin,
                             table: UIComponentAPI, customCells: List<Triple<Int, Int, CustomPanelAPI>>) {
    if (customCells.isEmpty()) return
    val rows = table.invoke("getRows") as? List<*> ?: return
    val panelLeft = panel.position.x
    val panelTop = panel.position.y + panel.position.height
    var waiting = false
    for ((row, col, box) in customCells) {
        val rowComponent = rows.getOrNull(row) ?: continue
        // a row makes its cell panels when it is first sized, which can be after this layout: place it next frame
        val cellPanel = runCatching { rowComponent.invoke("getCol", col) as? UIComponentAPI }.getOrNull()
        if (cellPanel == null) { waiting = true; continue }
        val cell = cellPanel.position
        box.position.inTL(cell.x - panelLeft, panelTop - (cell.y + cell.height))
        commitChildLayout(box, plugin, cell.width, cell.height)
    }
    if (waiting) plugin.requestLayout()
}
