package org.starficz.starui.components

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.*
import org.starficz.starui.layout.commitChildLayout
import org.starficz.starui.layout.LayoutEngine
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.getChildrenNonCopy
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The vertically scrolling list that only creates and lays out the items near the visible part: the visible rows,
 * and a screen's worth above and below them. Use it for lists of hundreds or thousands of items; a
 * `Column(overflow = Overflow.ScrollVertical)` makes every row.
 *
 * Every row is [itemHeight] tall and fills the list's width, so the scrollbar always covers the whole list. Each
 * row's [itemContent] is its own reactive builder: a [UIState] it reads rebuilds that row only.
 *
 * A row that leaves the view and its buffer is removed, with its native state (a text field's focus, an inner
 * scroll position): keep what must survive in a [UIState]. Rows in the buffer exist but do not advance; their
 * `onAdvance` handlers and vanilla animations pause until they show.
 *
 * Example usage:
 *
 * ```kotlin
 * LazyColumn(ships.value, Size.fillMaxSize(), itemHeight = 40f, key = { it.id }) { ship ->
 *     Row(verticalAlignment = Alignment.CenterVertically, spacing = 8f) {
 *         Text(ship.name)
 *         Button("Sell") { sell(ship) }
 *     }
 * }
 * ```
 *
 * @param items the data list
 * @param size the size of the [LazyColumn]
 * @param modifier the [Modifier] to be applied to this list. `Modifier.padding` is space inside the scrolled
 *   content
 * @param itemHeight the height of every row
 * @param key a factory of stable and unique keys representing the item. Using the same key for multiple items in
 *   the list is not allowed. If `null` is passed the position in the list will represent the key. When you specify
 *   the key, a row keeps its components while it stays near the view, and adding or removing items above the view
 *   keeps the visible rows in place
 * @param spacing the gap between two neighbouring rows
 * @param itemContent the content displayed by a single item
 * @return the list's panel
 * @see LazyRow
 * @see Column
 */
fun <T> UIPanelAPI.LazyColumn(
    items: List<T>,
    size: BoundedSize = Size.fillMaxSize(),
    modifier: Modifier = Modifier,
    itemHeight: Float,
    key: ((T) -> Any)? = null,
    spacing: Float = 0f,
    itemContent: (@StarUIDsl CustomPanelAPI).(item: T) -> Unit
): CustomPanelAPI = LazyList("LazyColumn", vertical = true, items, size, modifier, itemHeight, key, spacing, itemContent)

/**
 * The horizontally scrolling list that only creates and lays out the items near the visible part: the visible
 * items, and a screen's worth to their left and right. Use it for strips of hundreds of items; a
 * `Row(overflow = Overflow.ScrollHorizontal)` makes every item.
 *
 * Every item is [itemWidth] wide and fills the list's height. Everything else works as in [LazyColumn].
 *
 * @param items the data list
 * @param size the size of the [LazyRow]
 * @param modifier the [Modifier] to be applied to this list. `Modifier.padding` is space inside the scrolled
 *   content
 * @param itemWidth the width of every item
 * @param key a factory of stable and unique keys representing the item. Using the same key for multiple items in
 *   the list is not allowed. If `null` is passed the position in the list will represent the key. When you specify
 *   the key, an item keeps its components while it stays near the view, and adding or removing items before the
 *   view keeps the visible items in place
 * @param spacing the gap between two neighbouring items
 * @param itemContent the content displayed by a single item
 * @return the list's panel
 * @see LazyColumn
 * @see Row
 */
fun <T> UIPanelAPI.LazyRow(
    items: List<T>,
    size: BoundedSize = Size.fillMaxWidth(height = 100f),
    modifier: Modifier = Modifier,
    itemWidth: Float,
    key: ((T) -> Any)? = null,
    spacing: Float = 0f,
    itemContent: (@StarUIDsl CustomPanelAPI).(item: T) -> Unit
): CustomPanelAPI = LazyList("LazyRow", vertical = false, items, size, modifier, itemWidth, key, spacing, itemContent)

private fun <T> UIPanelAPI.LazyList(
    kind: String,
    vertical: Boolean,
    items: List<T>,
    size: BoundedSize,
    modifier: Modifier,
    itemSize: Float,
    keyOf: ((T) -> Any)?,
    spacing: Float,
    itemContent: CustomPanelAPI.(T) -> Unit
): CustomPanelAPI {
    require(itemSize > 0f) { "StarUI: $kind needs a positive item ${if (vertical) "height" else "width"} (got $itemSize)" }
    checkDslTarget()
    val overflow = if (vertical) Overflow.ScrollVertical else Overflow.ScrollHorizontal
    val slot = childSlot(kind, overflow)
    val parts = containerParts(slot, size, overflow)
    val plugin = parts.plugin
    @Suppress("UNCHECKED_CAST")
    val engine = (plugin.layoutEngine as? LazyLayoutEngine<T>)?.takeIf { it.vertical == vertical }
        ?: LazyLayoutEngine<T>(plugin, vertical).also { plugin.layoutEngine = it }
    plugin.pausesOffscreenAdvance = true
    engine.update(items, itemSize, spacing, keyOf)
    parts.register(slot, size, modifier)

    plugin.attachScope(kind, itemContent) {
        engine.rangeVersion.value   // read: the list rebuilds when the visible range moves
        engine.indices.clear()
        val range = engine.range()
        engine.builtRange = range
        val panel = parts.panel
        for (i in range) {
            val item = items[i]
            val row = key(keyOf?.invoke(item) ?: i) {
                panel.Box(if (vertical) Size.fillMaxWidth(height = itemSize) else Size.fillMaxHeight(width = itemSize)) {
                    itemContent(item)
                }
            }
            engine.indices[row] = i
        }
        // once per frame: has the visible range moved past what was built?
        plugin.onAdvance { engine.checkRange() }
    }
    return parts.panel
}

/**
 * Lays out a lazy list's content: the content is as long as every item together; each existing row sits at its
 * item's place. Also tracks the scroll position: the range of rows to make, and the first visible item by key, to
 * keep the view on it when the items change.
 */
internal class LazyLayoutEngine<T>(private val plugin: StarUIPanelPlugin, val vertical: Boolean) : LayoutEngine {
    private var items: List<T> = emptyList()
    private var itemSize = 1f
    private var spacing = 0f
    private var keyOf: ((T) -> Any)? = null

    /** Bumped when the range to build changes; the list's builder reads it. */
    val rangeVersion = UIState(0)
    var builtRange: IntRange = IntRange.EMPTY
    /** Which item each existing row shows. */
    val indices = IdentityHashMap<UIComponentAPI, Int>()

    /** The first item at least partly in view, by key, and how far the view is scrolled into it. */
    private var anchorKey: Any? = null
    private var anchorDelta = 0f
    /** A scroll position to set at the next layout (the anchor item moved). */
    private var pendingOffset: Float? = null

    private val stride get() = itemSize + spacing

    fun update(newItems: List<T>, newItemSize: Float, newSpacing: Float, newKey: ((T) -> Any)?) {
        val itemsChanged = newItems !== items
        val geometryChanged = newItemSize != itemSize || newSpacing != spacing
        if (itemsChanged && newKey != null && anchorKey != null) {
            val index = newItems.indexOfFirst { newKey(it) == anchorKey }
            if (index >= 0) pendingOffset = index * (newItemSize + newSpacing) + anchorDelta
        }
        items = newItems
        itemSize = newItemSize
        spacing = newSpacing
        keyOf = newKey
        if (itemsChanged || geometryChanged) plugin.requestLayout()
    }

    private fun viewport(): Float {
        val host = plugin.scrollHost ?: return 0f
        return if (vertical) host.position.height else host.position.width
    }

    /** Where the scroll position is heading along the list (0 at the start): the scroll panel's own offset. */
    private fun offset(): Float {
        pendingOffset?.let { return it }
        val host = plugin.scrollHost ?: return 0f
        return if (vertical) host.yOffset else -host.xOffset
    }

    /** Where the content is drawn now: vanilla eases the content towards the offset over a few frames. */
    private fun drawnOffset(): Float {
        pendingOffset?.let { return it }
        val host = plugin.scrollHost ?: return 0f
        val h = host.position
        val c = host.contentContainer.position
        return if (vertical) (c.y + c.height) - (h.y + h.height) else h.x - c.x
    }

    /** The stretch of the list in or about to be in view: from the drawn position to the target, plus the view. */
    private fun span(view: Float): Pair<Float, Float> {
        val target = offset()
        val drawn = drawnOffset()
        return Pair(minOf(target, drawn) - leadPadding, maxOf(target, drawn) + view - leadPadding)
    }

    private val leadPadding get() = if (vertical) plugin.node.padding.top else plugin.node.padding.left

    /** The items to make: the visible ones and a viewport's worth on each side. */
    fun range(): IntRange {
        if (items.isEmpty()) return IntRange.EMPTY
        val view = viewport()
        if (view <= 1f) return 0 until minOf(items.size, 16)   // not laid out yet
        val buffer = ceil(view / stride).toInt()
        val (start, end) = span(view)
        val first = maxOf(0, floor(start / stride).toInt() - buffer)
        val last = minOf(items.size - 1, ceil(end / stride).toInt() + buffer)
        return if (first > last) IntRange.EMPTY else first..last
    }

    /** Called every frame: rebuild when the needed range is no longer inside the built one; note the anchor. */
    fun checkRange() {
        val view = viewport()
        if (items.isNotEmpty() && view > 1f) {
            val target = offset()
            val anchorIndex = floor((target - leadPadding) / stride).toInt().coerceIn(0, items.size - 1)
            anchorKey = keyOf?.invoke(items[anchorIndex])
            anchorDelta = target - anchorIndex * stride
            // rebuild when anything between the drawn position and the target is not built
            val (start, end) = span(view)
            val firstNeeded = floor(start / stride).toInt().coerceIn(0, items.size - 1)
            val lastNeeded = minOf(items.size - 1, ceil(end / stride).toInt())
            val built = builtRange
            if (built.isEmpty() || firstNeeded < built.first || lastNeeded > built.last) rangeVersion.value++
        }
    }

    private fun contentLength(): Float {
        val n = items.size
        val padding = plugin.node.padding
        val pad = if (vertical) padding.vertical else padding.horizontal
        return pad + n * itemSize + maxOf(0, n - 1) * spacing
    }

    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> =
        if (vertical) Pair(maxWidth, contentLength()) else Pair(contentLength(), maxHeight)

    override fun layout(assignedWidth: Float, assignedHeight: Float) {
        val padding = plugin.node.padding
        val cross = if (vertical) assignedWidth - padding.horizontal else assignedHeight - padding.vertical
        for (child in plugin.panel.getChildrenNonCopy()) {
            val index = indices[child] ?: continue
            val along = index * stride
            if (vertical) {
                commitChildLayout(child, plugin, maxOf(0f, cross), itemSize)
                child.position.inTL(padding.left, padding.top + along)
            } else {
                commitChildLayout(child, plugin, itemSize, maxOf(0f, cross))
                child.position.inTL(padding.left + along, padding.top)
            }
        }
        val pending = pendingOffset ?: return
        pendingOffset = null
        val host = plugin.scrollHost ?: return
        if (vertical) host.yOffset = pending else host.xOffset = -pending
        rangeVersion.value++   // the range follows the new offset on the next frame
    }
}
