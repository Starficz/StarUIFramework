// LazyColumn / LazyRow: only rows near the view exist, positions over the whole list, keyed reuse, per-row scopes,
// the view kept in place when items above it change.

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.Anchor
import org.starficz.starui.components.CustomPanel
import org.starficz.starui.components.LazyColumn
import org.starficz.starui.components.LazyRow
import org.starficz.starui.components.Text
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.background
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.Size
import org.starficz.starui.UIState
import org.starficz.starui.vanilla.children
import org.starficz.starui.vanilla.scrollPanel
import java.awt.Color

private fun lazyAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))
private fun lazyStripe(i: Int) = Modifier.background(if (i % 2 == 0) Color(200, 60, 60) else Color(60, 60, 200))

private class LazyItem(val id: Int, val name: String) {
    val clicks = UIState(0)
}

/** The row (the item's Box) whose label reads [text], or null when that row does not exist. */
private fun CustomPanelAPI.rowShowing(text: String): UIComponentAPI? =
    children.firstOrNull { row -> (row as? CustomPanelAPI)?.children?.any { (it as? LabelAPI)?.text?.startsWith(text) == true } == true }

fun registerLazyListTests() {
    StarUITestSuite.group("lazy-column-basics") {
        val items = UIState((0 until 1000).map { LazyItem(it, "Item $it") })
        val builds = IntArray(1000)
        val list = root.LazyColumn(items.value, Size.exact(300f, 300f), lazyAt(40f, 40f).background(Color(30, 30, 40)),
            itemHeight = 30f, key = { it.id }) { item ->
            builds[item.id]++
            Text("${item.name} clicked ${item.clicks.value}", modifier = lazyStripe(item.id))
        }
        frame()
        frame()
        val sp = list.scrollPanel!!
        near(list.position.height, 1000 * 30f, "the content is as long as every item together")
        val made = list.children.size
        expect(made in 10..40, "only the rows near the view exist ($made of 1000)")
        val row5 = list.rowShowing("Item 5 ")
        expect(row5 != null, "item 5 has a row")
        if (row5 != null) near(row5.relTop(list), 5 * 30f, "item 5's row sits at its place in the whole list")
        screenshot("top")

        // a small scroll keeps the rows that stay near the view
        sp.yOffset = 60f
        frame()
        expect(list.rowShowing("Item 5 ") === row5, "a small scroll keeps item 5's row (keyed)")

        // far away: the rows there exist, the first ones are gone
        sp.yOffset = 15000f
        frame()
        frame()
        val row500 = list.rowShowing("Item 500 ")
        expect(row500 != null && list.rowShowing("Item 5 ") == null, "scrolled to item 500: its row exists, item 5's is gone")
        if (row500 != null) near(row500.relTop(list), 500 * 30f, "item 500's row at its place")
        expect(list.children.size <= 40, "still only a few rows (${list.children.size})")
        screenshot("item-500")

        // a state change in a visible row rebuilds that row only
        val before = builds.copyOf()
        items.value[502].clicks.value++
        frame()
        expect(builds[502] == before[502] + 1, "item 502's row rebuilt (${before[502]} -> ${builds[502]})")
        expect((0 until 1000).all { it == 502 || builds[it] == before[it] }, "no other row rebuilt")
        expect(list.rowShowing("Item 502 clicked 1") != null, "and shows the new state")

        screenshot("final")
    }

    StarUITestSuite.group("lazy-column-keeps-view") {
        val items = UIState((0 until 300).map { LazyItem(it, "Item $it") })
        lateinit var list: CustomPanelAPI
        root.CustomPanel(Size.exact(400f, 400f), lazyAt(40f, 40f)) {
            list = LazyColumn(items.value, Size.exact(300f, 300f), itemHeight = 30f, key = { it.id }) { item ->
                Text(item.name, modifier = lazyStripe(item.id))
            }
        }
        frame(); frame()
        val sp = list.scrollPanel!!
        sp.yOffset = 100 * 30f + 10f
        frame(); frame()
        val y100 = list.rowShowing("Item 100")!!.position.y
        items.value = items.value.filter { it.id != 10 && it.id != 20 }   // two items above the view
        frame(); frame()
        val row = list.rowShowing("Item 100")
        expect(row != null, "item 100 still has a row after removing items above it")
        if (row != null) near(row.position.y, y100, "and it stays where it was on screen")
        near(sp.yOffset, 98 * 30f + 10f, "the scroll position moved with it")

        items.value = items.value.take(5)
        frame(); frame()
        near(list.position.height, 300f, "a short list: the content is the viewport")
        near(sp.yOffset, 0f, "and the scroll position is back at the top")
        expect(list.children.size == 5, "five rows (${list.children.size})")
        screenshot("final")
    }

    StarUITestSuite.group("lazy-row-basics") {
        val list = root.LazyRow((0 until 500).toList(), Size.exact(400f, 60f), lazyAt(40f, 40f), itemWidth = 50f, spacing = 4f) { i ->
            Text("$i", modifier = lazyStripe(i))
        }
        frame(); frame()
        near(list.position.width, 500 * 50f + 499 * 4f, "the content is as wide as every item together")
        expect(list.children.size in 5..30, "only the items near the view exist (${list.children.size})")
        list.scrollPanel!!.xOffset = -(200 * 54f)
        frame(); frame()
        val row = list.children.firstOrNull { r -> (r as? CustomPanelAPI)?.children?.any { (it as? LabelAPI)?.text == "200" } == true }
        expect(row != null, "scrolled to item 200: it exists")
        if (row != null) near(row.relLeft(list), 200 * 54f, "at its place")
        screenshot("final")
    }
}
