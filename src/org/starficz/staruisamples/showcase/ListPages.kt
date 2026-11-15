package org.starficz.staruisamples.showcase

import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.VanillaColors

private const val FILE = "ListPages.kt"

// snippet: lazy-column
private class Contact(val id: Int) {
    val calls = UIState(0)
}
private val allContacts = (1..10_000).map { Contact(it) }
private val contacts = UIState(allContacts)
// end snippet

// snippet: table-data
private class Ship(val id: Int, val name: String, val hull: String, val cost: Int, val cr: Int)
private val allShips = listOf(
    Ship(1, "ISS Black Wind", "Onslaught", 320, 70), Ship(2, "ISS Kestrel", "Eagle", 180, 45),
    Ship(3, "ISS Tern", "Hammerhead", 90, 85), Ship(4, "ISS Runner", "Wolf", 40, 30),
    Ship(5, "ISS Ledger", "Buffalo", 25, 60))
private val fleet = UIState(allShips)
// end snippet
// snippet: table-select
private val selected = UIState<Int?>(null)
// end snippet
// snippet: table-sort
private val sort = UIState<TableSort?>(TableSort(column = 1, descending = true))
// end snippet

private val lazyLists = Page("Lists and tables", "Lazy lists",
    "For hundreds or thousands of rows: only the rows near the view exist. Every row is the same height (width).", FILE) {

    card("Ten thousand rows", "lazy-column", wide = true,
        note = "Each row's count is its own state. Scroll down, then remove rows above: the view stays on the same rows " +
            "(the key ties a row to its item).") {
        // snippet: lazy-column
        Row(spacing = 6f, verticalAlignment = Alignment.CenterVertically) {
            Button("Remove the first 5") { contacts.value = contacts.value.drop(5) }
            Button("Reset") { contacts.value = allContacts }
            Text("${contacts.value.size} rows", color = VanillaColors.gray)
        }
        LazyColumn(contacts.value, Size.fillMaxWidth(height = 160f), Modifier.background(INSET).padding(horizontal = 4f),
            itemHeight = 24f, key = { it.id }) { contact ->
            Row(Size.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, spacing = 6f) {
                Text("Contact ${contact.id}")
                FlexSpacer()
                Text("called ${contact.calls.value} times", color = VanillaColors.gray)
                Button("Call") { contact.calls.value++ }
            }
        }
        // end snippet
    }

    card("Sideways", "lazy-row", wide = true, note = "LazyRow: 2000 cards; the wheel scrolls it sideways.") {
        // snippet: lazy-row
        LazyRow((1..2000).toList(), Size.fillMaxWidth(height = 56f), Modifier.background(INSET), itemWidth = 84f, spacing = 6f) { i ->
            Box(Size.fillMaxSize(), Modifier.background(TILE).padding(4f)) { Text("Card $i") }
        }
        // end snippet
    }
}

private val tables = Page("Lists and tables", "Tables",
    "Vanilla's table: a header, rows that glow under the mouse, its frame and scrollbar. Columns are text, styled " +
        "text or StarUI components.", FILE) {

    card("Three kinds of cells", "table-data", wide = true,
        note = "A string; cell(...) with a colour or highlights; custom { } with any components, reactive as any builder.") {
        // snippet: table-data
        Table(fleet.value, Size.fillMaxWidth(), rowKey = { it.id }, emptyText = "No ships left") {
            column("Ship") { it.name }
            column("Hull", width = 110f) { it.hull }
            column("Cost", width = 70f, align = TextAlign.Right) { ship -> cell("${ship.cost}", color = VanillaColors.highlight) }
            column("Combat readiness", width = 140f) { ship ->
                cell("${ship.cr}%", color = if (ship.cr < 50) VanillaColors.negative else VanillaColors.positive)
            }
            column("", width = 70f) { ship ->
                custom { Button("Sell", modifier = Modifier.align(Alignment.Center)) { fleet.value = fleet.value - ship } }
            }
        }
        Button("Reset the fleet") { fleet.value = allShips }
        // end snippet
    }

    card("Sorting", "table-sort", wide = true,
        note = "sortBy makes a column sortable: a header click sorts by it, another reverses. The sort can live in a state.") {
        // snippet: table-sort
        Table(allShips, Size.fillMaxWidth(), rowKey = { it.id }, sort = sort) {
            column("Ship") { it.name }.sortBy { it.name }
            column("Cost", width = 80f) { "${it.cost}" }.sortBy(descendingFirst = true) { it.cost }
            column("CR", width = 80f) { "${it.cr}%" }.sortBy(descendingFirst = true) { it.cr }
        }
        Row(spacing = 6f, verticalAlignment = Alignment.CenterVertically) {
            Button("Unsorted") { sort.value = null }
            Text(sort.value?.let { "Column ${it.column}, ${if (it.descending) "descending" else "ascending"}" } ?: "In list order",
                color = VanillaColors.gray)
        }
        // end snippet
    }

    card("Row tooltips and clicks", "table-select", wide = true,
        note = "rowTooltip builds a tooltip per row; onRowClick here selects the row, shown in its first cell.") {
        // snippet: table-select
        Table(allShips, Size.fillMaxWidth(), rowKey = { it.id },
            rowTooltip = { ship -> addPara("The %s costs %s a month.", 0f, VanillaColors.highlight, ship.name, "${ship.cost / 10}") },
            onRowClick = { ship -> selected.value = ship.id }) {
            column("", width = 24f) { ship -> if (ship.id == selected.value) cell(">", color = VanillaColors.highlight) else cell("") }
            column("Ship") { it.name }
            column("Hull", width = 110f) { it.hull }
        }
        // end snippet
    }
}

internal val listPages = listOf(lazyLists, tables)
