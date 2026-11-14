package org.starficz.starui.components

import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.ui.*
import org.starficz.starui.layout.commitChildLayout
import org.starficz.starui.layout.VanillaElementEngine
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.Size
import org.starficz.starui.StarUIDsl
import org.starficz.starui.StarUITheme
import org.starficz.starui.TextAlign
import org.starficz.starui.UIState
import org.starficz.starui.vanilla.buildFromTooltip
import org.starficz.starui.vanilla.getChildrenNonCopy
import org.starficz.starui.vanilla.onClick
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import org.starficz.starui.vanilla.VanillaColors
import java.awt.Color
import kotlin.experimental.ExperimentalTypeInference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * What one cell of a [Table] shows: styled text, made with [TableCellScope.cell], or StarUI components, made with
 * [TableCellScope.custom].
 */
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
    /** The sort a table keeps itself when its call gives it no `sort` state. */
    val ownSort = UIState<TableSort?>(null)
    /** The sort state and columns of the newest call, for the header buttons' click handlers. */
    var sortState: UIState<TableSort?> = ownSort
    var specs: List<TableColumnSpec<T>> = emptyList()
    /** The header's column buttons of the table made last (the sort icons are drawn over them). */
    var headerButtons: List<ButtonAPI> = emptyList()
}

/**
 * How a [Table] is sorted: by the column at index [column] (0 is the first declared), in [descending] order. Only a
 * column with a `sortBy` can sort a table.
 *
 * @property column the index of the column the rows are sorted by
 * @property descending whether the rows are in descending order
 */
data class TableSort(val column: Int, val descending: Boolean = false)

/**
 * Receiver scope of a [Table] column's content, for cells that are more than plain text.
 */
object TableCellScope {
    /**
     * A text cell with its own style. Text too long for the column is cut to fit.
     *
     * @param text the text of the cell
     * @param color the colour of the text, or `null` for the table's `baseColor`
     * @param align the alignment of the text, or `null` for the column's
     * @param highlightedText parts of [text] drawn in another colour, each one a piece of the text and its colour
     * @return the cell
     */
    fun cell(text: String, color: Color? = null, align: TextAlign? = null,
             highlightedText: Collection<Pair<String, Color>>? = null): TableCell =
        TableCell.Text(text, color, align, highlightedText?.toList())

    /**
     * A cell of StarUI components. [builder] runs in a [Box] the size of the cell, so its children are placed with
     * `Modifier.align(...)`. It is reactive like any builder. A row grows to fit its tallest custom cell.
     *
     * @param builder the content of the cell
     * @return the cell
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
) {
    var sortBy: ((T) -> Comparable<*>?)? = null
    var sortDescendingFirst = false
}

/**
 * A column of a [Table], as [TableScope.column] returns it: for making the column sortable with [sortBy].
 */
class TableColumn<T> internal constructor(private val spec: TableColumnSpec<T>) {
    /**
     * Makes the column sortable: a click on its header sorts the table's rows by [selector], and another click
     * reverses the order. Sorting is optional: a table where no column calls this is never sorted.
     *
     * ```kotlin
     * column("Cost", width = 80f) { "${it.cost}" }.sortBy(descendingFirst = true) { it.cost }
     * ```
     *
     * @param descendingFirst whether the first click on the header sorts in descending order
     * @param selector the value the rows are sorted by; `null` values come first in ascending order
     * @return this column
     */
    fun sortBy(descendingFirst: Boolean = false, selector: (T) -> Comparable<*>?): TableColumn<T> {
        spec.sortBy = selector
        spec.sortDescendingFirst = descendingFirst
        return this
    }
}

/**
 * Receiver scope of a [Table]'s trailing block, where its columns are declared, left to right.
 */
@StarUIDsl
class TableScope<T> internal constructor() {
    internal val columns = ArrayList<TableColumnSpec<T>>()

    /**
     * Adds a column of plain text.
     *
     * @param title the text of the column's header
     * @param width the exact width of the column, or `null` to take a share of the table's width that is left
     * @param weight the share of the leftover width this column takes, as related to the total of all columns without
     *   a [width]
     * @param align the alignment of the column's text
     * @param tooltip a tooltip shown over the column's header, or `null` for none
     * @param content the text of the cell for an item
     * @return the column, which [TableColumn.sortBy] makes sortable
     */
    @OptIn(ExperimentalTypeInference::class)
    @OverloadResolutionByLambdaReturnType
    fun column(title: String, width: Float? = null, weight: Float = 1f, align: TextAlign = TextAlign.Center,
               tooltip: String? = null, content: TableCellScope.(T) -> String): TableColumn<T> {
        val spec = TableColumnSpec<T>(title, width, weight, align, tooltip) { item -> TableCell.Text(content(item), null, null, null) }
        columns += spec
        return TableColumn(spec)
    }

    /**
     * Adds a column whose cells are made with `cell(...)` (styled text) or `custom { }` (StarUI components).
     *
     * @param title the text of the column's header
     * @param width the exact width of the column, or `null` to take a share of the table's width that is left
     * @param weight the share of the leftover width this column takes, as related to the total of all columns without
     *   a [width]
     * @param align the alignment of the column's text cells, unless a cell has its own
     * @param tooltip a tooltip shown over the column's header, or `null` for none
     * @param content the cell for an item
     * @return the column, which [TableColumn.sortBy] makes sortable
     */
    @OptIn(ExperimentalTypeInference::class)
    @OverloadResolutionByLambdaReturnType
    @JvmName("cellColumn")
    fun column(title: String, width: Float? = null, weight: Float = 1f, align: TextAlign = TextAlign.Center,
               tooltip: String? = null, content: TableCellScope.(T) -> TableCell): TableColumn<T> {
        val spec = TableColumnSpec(title, width, weight, align, tooltip, content)
        columns += spec
        return TableColumn(spec)
    }
}

/**
 * A component that shows [items] as the rows of a vanilla table, as on vanilla's intel and market screens, with the
 * columns declared in [columns].
 *
 * Columns with a `width` are exactly that wide; the others share what is left of the table's width by their
 * `weight`. With only exact columns, the table is as wide as its columns plus vanilla's 3 pixel frame; with weighted
 * ones, give the table a fill or exact width. The table is as tall as its rows, and at least three rows tall when
 * there are no [items], where it shows [emptyText]. Every row is [itemHeight] tall, or as tall as its tallest custom
 * cell needs.
 *
 * The table is made again when what it shows changes (its cells, columns, colours or row height) or when its width
 * changes. A builder that runs again for another reason keeps it.
 *
 * Sorting is optional. A column made sortable with [TableColumn.sortBy] can sort the table: a click on its header
 * sorts the rows by it, and another click reverses the order. Such a column shows vanilla's sort arrow in
 * [sortIconColor]: dim while another column sorts the table, pointing up while this one sorts it in ascending order.
 * A table without sortable columns shows its [items] in their own order, and its headers do nothing when clicked.
 * The sort is kept by the table, or in [sort] when a state is given, so code can set it, read it or keep it.
 *
 * Example usage:
 *
 * ```kotlin
 * Table(ships.value, Size.fillMaxWidth(), emptyText = "No ships") {
 *     column("Ship") { ship -> ship.name }.sortBy { it.name }
 *     column("Cost", width = 80f) { ship -> cell("${ship.cost}", color = VanillaColors.highlight) }
 *         .sortBy(descendingFirst = true) { it.cost }
 *     column("", width = 70f) { ship ->
 *         custom { Button("Sell", modifier = Modifier.align(Alignment.Center)) { sell(ship) } }
 *     }
 * }
 * ```
 *
 * @param items the data list, one row per item
 * @param size the size of the [Table]. By default it wraps its columns and rows
 * @param modifier the [Modifier] to be applied to this table
 * @param rowKey a factory of stable and unique keys representing the item, which ties each row's custom cells to
 *   its item, as [key] does. If `null` is passed the position in the list will represent the key
 * @param itemHeight the height of a row
 * @param withHeader whether to show the row of column titles
 * @param withBorder whether to draw vanilla's frame around the table
 * @param baseColor the colour of the column titles, the scrollbar's handle, [emptyText] and text cells without a
 *   colour of their own
 * @param darkColor the colour of the frame, the column titles' borders, the scrollbar's rail and the glow of a row
 *   under the cursor
 * @param sortIconColor the colour of the sort arrow on the headers of sortable columns
 * @param emptyText the text shown when there are no [items]
 * @param rowTooltipWidth the width of the row tooltips
 * @param rowTooltipLocation where the row tooltips show, relative to the row
 * @param rowTooltip fills the tooltip of an item's row, or `null` for no row tooltips
 * @param onRowClick called with the item when its row is clicked, or `null` for rows that cannot be clicked
 * @param sort the state that holds how the table is sorted, `null` for unsorted. A header click writes it. Without
 *   one, the table keeps its sort itself
 * @param columns the columns of the table, declared left to right
 * @return the panel holding the vanilla table
 * @see TableScope
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
    sortIconColor: Color = StarUITheme.current.table.sortIconColor ?: VanillaColors.bright,
    emptyText: String = "",
    rowTooltipWidth: Float = 300f,
    rowTooltipLocation: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE,
    rowTooltip: ((@StarUIDsl TooltipMakerAPI).(T) -> Unit)? = null,
    onRowClick: ((T) -> Unit)? = null,
    sort: UIState<TableSort?>? = null,
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
    val frame = tableFrameWidth(specs.size)
    engine.fixedWidth = if (weightSum == 0f) exactWidth + frame else null
    engine.fallbackWidth = exactWidth + 100f * weightSum + frame

    plugin.attachScope("Table", columns) {
        // The table's listeners read these, so a table kept through this call still calls the newest lambdas.
        @Suppress("UNCHECKED_CAST")
        val state = (engine.callerState as? TableCallerState<T>) ?: TableCallerState<T>().also { engine.callerState = it }
        val sortState = sort ?: state.ownSort
        state.sortState = sortState
        state.specs = specs
        val sortable = specs.any { it.sortBy != null }
        // Read the sort here, inside the builder, so a new sort rebuilds the table. Without sortable columns the sort
        // is not read at all: such a table is never sorted.
        val shown = if (sortable) sortedItems(items, specs, sortState.value) else items
        // Read every cell here, inside the builder, so a cell that reads a UIState rebuilds the table.
        val cells = shown.map { item -> specs.map { spec -> spec.content(TableCellScope, item) } }
        val customCells = ArrayList<Triple<Int, Int, CustomPanelAPI>>()
        shown.forEachIndexed { row, item ->
            val id = rowKey?.invoke(item) ?: row
            cells[row].forEachIndexed { col, cell ->
                if (cell is TableCell.Custom) {
                    val box = key(Pair(id, col)) { panel.Box(Size.fillMaxSize()) { cell.builder(this) } }
                    customCells += Triple(row, col, box)
                }
            }
        }
        val sortHeaders = sortable && withHeader
        val needsInput = customCells.isNotEmpty() || rowTooltip != null || onRowClick != null || sortHeaders ||
            specs.any { it.tooltip != null }

        state.items = shown
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
                // beginTable's third colour (vanilla's "bright") only colours the sort icon of sortable columns, and
                // beginTable makes none (0.98.5a: its header adds every column unsortable, and the table gets no
                // delegate to sort with). StarUI sorts the items itself and draws the same icon in sortIconColor
                // (drawSortIcons), so that is passed here too.
                beginTable(
                    baseColor, darkColor, sortIconColor, rowHeight, withBorder, withHeader, *header.toTypedArray()
                )
                specs.forEachIndexed { i, spec -> spec.tooltip?.let { addTableHeaderTooltip(i, it) } }
                if (onRowClick != null) {
                    makeTableItemsClickable()
                    setActionListenerDelegate { data, _ ->
                        val index = (data as? IntelInfoPlugin.TableRowClickData)?.rowId as? Int
                        val now = state.items
                        if (index != null && index in now.indices) state.onRowClick?.invoke(now[index])
                    }
                }
                shown.forEachIndexed { row, _ ->
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
        engine.afterBuild = { table ->
            for ((_, _, box) in customCells) { panel.removeComponent(box); panel.addComponent(box) }
            state.headerButtons = if (sortHeaders) hookSortHeaders(table, state) else emptyList()
        }
        if (sortHeaders) plugin.onRender { alphaMult -> drawSortIcons(state, sortIconColor, alphaMult) }
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
        if (customCells.isNotEmpty()) plugin.onAdvance {
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
            cells, specs.map { listOf(it.title, it.width, it.weight, it.align, it.tooltip, it.sortBy != null) }, itemHeight,
            withHeader, withBorder, baseColor, darkColor, sortIconColor, emptyText, rowTooltipWidth, rowTooltipLocation,
            rowTooltip != null, onRowClick != null
        ))
        // this build's end removes every old child no call asked for, the vanilla table too: a kept one stays. (A
        // table about to be made again stays until the new one replaces it.)
        engine.current?.let { plugin.reuseCursor?.keep(it) }
    }
    return panel
}

/**
 * [items] in the order [sort] asks for: by the sorted column's `sortBy`, stable, so equal items keep their order. The
 * items as they are when there is no sort, or its column has no `sortBy`.
 */
private fun <T> sortedItems(items: List<T>, specs: List<TableColumnSpec<T>>, sort: TableSort?): List<T> {
    if (sort == null) return items
    val selector = specs.getOrNull(sort.column)?.sortBy ?: return items
    val ascending = Comparator<T> { a, b -> compareValues(selector(a), selector(b)) }
    return items.sortedWith(if (sort.descending) ascending.reversed() else ascending)
}

/**
 * Makes the header buttons of the sortable columns sort the table, and returns the header's column buttons. The
 * header (`UITable.getHeader`, a readable name) holds one button per column, in order. The click is added next to the
 * button's own listener, which is kept: the table's own handler sees the click too, and ignores it, since it made
 * every column unsortable.
 */
private fun <T> hookSortHeaders(table: UIComponentAPI, state: TableCallerState<T>): List<ButtonAPI> {
    val header = runCatching { table.invoke("getHeader") as? UIPanelAPI }.getOrNull() ?: return emptyList()
    val buttons = header.getChildrenNonCopy().filterIsInstance<ButtonAPI>()
    buttons.forEachIndexed { col, button ->
        if (state.specs.getOrNull(col)?.sortBy == null) return@forEachIndexed
        button.onClick {
            // the newest call's state and columns: a table kept through a call sorts by what it was given last
            val spec = state.specs.getOrNull(col) ?: return@onClick
            val now = state.sortState.value
            state.sortState.value =
                if (now?.column == col) now.copy(descending = !now.descending) else TableSort(col, spec.sortDescendingFirst)
        }
    }
    return buttons
}

private var sortIcon: SpriteAPI? = null

/**
 * Vanilla's sort arrow (the `ui` sprite `sortIcon`, 15 pixels, 3 in from the header button's right edge, as vanilla's
 * sortable header draws it) on each sortable column: pointing down, and dim on the columns that do not sort the table;
 * pointing up on the column that sorts it in ascending order.
 */
private fun <T> drawSortIcons(state: TableCallerState<T>, color: Color, alphaMult: Float) {
    val buttons = state.headerButtons
    if (buttons.isEmpty()) return
    val icon = sortIcon ?: Global.getSettings().getSprite("ui", "sortIcon").also { sortIcon = it }
    val sort = state.sortState.peek()
    buttons.forEachIndexed { col, button ->
        if (state.specs.getOrNull(col)?.sortBy == null) return@forEachIndexed
        val active = sort != null && sort.column == col
        icon.setSize(15f, 15f)
        icon.color = color
        icon.angle = if (active && sort?.descending == false) 180f else 0f
        icon.alphaMult = alphaMult * (if (active) 1f else 0.4f)
        val p = button.position
        icon.renderAtCenter(p.x + p.width - 3f - 7.5f, p.y + p.height / 2f)
    }
}

/** The gap vanilla's table puts between two columns (0.98.5a-RC4: header buttons 3 px apart, none at the ends). */
private const val TABLE_COLUMN_GAP = 3f

/** How much wider vanilla's table is than the sum of its [columnCount] columns: one gap between each two. */
private fun tableFrameWidth(columnCount: Int): Float = TABLE_COLUMN_GAP * maxOf(0, columnCount - 1)

/**
 * Each column's width for a table [width] wide: exact ones as given, weighted ones sharing the rest (whole pixels), so
 * that the columns plus vanilla's frame make [width].
 */
private fun <T> columnWidths(specs: List<TableColumnSpec<T>>, width: Float, exactWidth: Float, weightSum: Float): List<Float> {
    val columnsWidth = width - tableFrameWidth(specs.size)
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
