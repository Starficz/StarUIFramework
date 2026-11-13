// Table and the vanilla TooltipMakerAPI content (stat grids, bars, icons, ship lists, codex links) as StarUI components.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.campaign.PersonImportance
import com.fs.starfarer.api.combat.MutableStat
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.codex.CodexDataV2
import com.fs.starfarer.api.ui.*
import org.starficz.starui.*
import org.starficz.starui.Alignment
import org.starficz.starui.components.*
import org.starficz.starui.layout.VanillaElementEngine
import org.starficz.starui.modifier.align
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import org.starficz.starui.vanilla.sectorMap
import org.starficz.starui.vanilla.VanillaColors

private data class TestShip(val id: Int, val name: String, val cost: Int)
private val testShips = listOf(TestShip(1, "Onslaught", 300), TestShip(2, "Eagle", 150), TestShip(3, "Wolf", 40))
private fun elementAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

/** The vanilla table inside a Table's StarUI panel. */
private fun vanillaTable(panel: CustomPanelAPI): UIComponentAPI =
    (StarUIPanelPlugin.getAttached(panel)!!.layoutEngine as VanillaElementEngine).current!!

private fun rowsOf(panel: CustomPanelAPI): List<Any> = (vanillaTable(panel).invoke("getRows") as List<*>).filterNotNull()
private fun cellOf(row: Any, col: Int): UIComponentAPI = row.invoke("getCol", col) as UIComponentAPI
private fun rowTexts(row: Any): List<String> = (row.invoke("getLabels") as List<*>).map { (it as LabelAPI).text }

fun registerVanillaElementTests() {
    StarUITestSuite.group("table-columns-and-size") {
        lateinit var weighted: CustomPanelAPI
        lateinit var exact: CustomPanelAPI
        lateinit var empty: CustomPanelAPI
        root.Column(Size.exact(500f, 600f), elementAt(40f, 40f), spacing = 10f) {
            weighted = Table(testShips, Size.fillMaxWidth()) {
                column("Ship", weight = 1f) { it.name }
                column("Cost", width = 80f, align = TextAlign.Right) { cell("${it.cost}", color = VanillaColors.highlight) }
            }
            exact = Table(testShips) {
                column("A", width = 100f) { it.name }
                column("B", width = 60f) { "${it.cost}" }
            }
            empty = Table(emptyList<TestShip>(), Size.fillMaxWidth(), emptyText = "No ships") { column("Ship") { it.name } }
        }
        frame()
        near(weighted.w, 500f, "a fill-width table takes its column's width")
        near(vanillaTable(weighted).w, 500f, "and so does the vanilla table in it")
        near(exact.w, 163f, "a table of exact columns is as wide as they are, plus vanilla's 3 px frame")
        val rows = rowsOf(weighted)
        expect(rows.size == 3, "one row per item (${rows.size})")
        expect(rowTexts(rows[0]) == listOf("Onslaught", "300"), "the first row shows the first item's cells (${rowTexts(rows[0])})")
        val shipCol = cellOf(rows[0], 0).w
        val costCol = cellOf(rows[0], 1).w
        expect(costCol in 72f..81f, "the exact column is 80 wide, less vanilla's border inset ($costCol)")
        expect(shipCol in 410f..421f, "the weighted column takes the rest ($shipCol)")
        expect(weighted.h in 75f..110f, "the table is as tall as its header and three 20 px rows (${weighted.h})")
        expect(empty.h >= weighted.h - 2f, "an empty table keeps the height of three rows (${empty.h})")
        screenshot("final")
    }

    StarUITestSuite.group("table-custom-cells") {
        val sold = ArrayList<String>()
        lateinit var table: CustomPanelAPI
        root.Column(Size.exact(400f, 300f), elementAt(40f, 40f)) {
            table = Table(testShips, Size.fillMaxWidth(), rowKey = { it.id }) {
                column("Ship") { it.name }
                column("", width = 80f) { ship -> custom { Button("Sell", modifier = Modifier.align(Alignment.Center)) { sold += ship.name } } }
            }
        }
        frame(); frame()
        val boxes = table.getChildrenCopy2().filterIsInstance<CustomPanelAPI>()
        expect(boxes.size == 3, "one Box per custom cell (${boxes.size})")
        val rows = rowsOf(table)
        boxes.forEachIndexed { i, box ->
            val cell = cellOf(rows[i], 1)
            near(box.position.x, cell.position.x, "custom cell $i sits on its cell's left edge", tolerance = 1f)
            near(box.position.y, cell.position.y, "and its bottom edge", tolerance = 1f)
            near(box.w, cell.w, "at the cell's width", tolerance = 1f)
            near(box.h, cell.h, "and the row's height", tolerance = 1f)
            val button = box.getChildrenCopy2().filterIsInstance<ButtonAPI>().firstOrNull()
            if (button != null) expect(button.position.height <= cell.h + 0.5f,
                "rows grow to fit their custom cells: the button (${button.position.height}) fits the row (${cell.h})")
        }
        val button = boxes[1].getChildrenCopy2().filterIsInstance<ButtonAPI>().firstOrNull()
        expect(button != null, "the second custom cell holds its button")
        if (button != null) {
            Input.sendList(root, Input.move(button.cx, button.cy))
            Input.sendList(root, Input.down(button.cx, button.cy))
            Input.sendList(root, Input.up(button.cx, button.cy))
            frame()
            expect(sold == listOf("Eagle"), "clicking a custom cell's button runs its handler for that row's item ($sold)")
        }
        screenshot("final")
    }

    StarUITestSuite.group("table-follows-state-and-width") {
        val ships = UIState(testShips)
        val width = UIState(300f)
        lateinit var table: CustomPanelAPI
        root.Column(Size.exact(600f, 400f), elementAt(40f, 40f)) {
            Column(Size.mixed(Axis.Exact(width.value), Axis.WrapContent)) {
                table = Table(ships.value, Size.fillMaxWidth()) {
                    column("Ship") { it.name }
                    column("Cost", width = 60f) { "${it.cost}" }
                }
            }
        }
        frame()
        expect(rowsOf(table).size == 3, "three rows to start")
        ships.value = ships.value + TestShip(4, "Hound", 20)
        frame(); frame()
        expect(rowsOf(table).size == 4, "adding an item adds a row on the next frame (${rowsOf(table).size})")
        width.value = 450f
        frame(); frame()
        near(table.w, 450f, "a wider column makes the table wider")
        near(vanillaTable(table).w, 450f, "the vanilla table is made again at the new width")
        screenshot("final")
    }

    StarUITestSuite.group("table-row-click") {
        var clicked: TestShip? = null
        lateinit var table: CustomPanelAPI
        root.Column(Size.exact(400f, 300f), elementAt(40f, 40f)) {
            table = Table(testShips, Size.fillMaxWidth(), onRowClick = { clicked = it }) { column("Ship") { it.name } }
        }
        frame(); frame()
        val cell = cellOf(rowsOf(table)[2], 0)
        Input.sendList(root, Input.move(cell.cx, cell.cy))
        Input.sendList(root, Input.down(cell.cx, cell.cy))
        Input.sendList(root, Input.up(cell.cx, cell.cy))
        frame()
        expect(clicked?.name == "Wolf", "clicking the third row reports the third item (got ${clicked?.name})")
    }

    StarUITestSuite.group("table-keeps-sibling-order") {
        // A Table (with custom cells) inside the middle Column of a Row must not move that Column.
        lateinit var a: CustomPanelAPI; lateinit var b: CustomPanelAPI; lateinit var c: CustomPanelAPI
        lateinit var row: CustomPanelAPI
        val fleet = UIState(testShips)
        row = root.Row(Size.mixed(Axis.Exact(900f), Axis.WrapContent), elementAt(40f, 40f), spacing = 10f) {
            a = Column(Size.fillMaxWidth()) { Text("first") }
            b = Column(Size.fillMaxWidth()) {
                Text("second")
                Table(fleet.value, Size.fillMaxWidth()) {
                    column("Ship") { it.name }
                    column("", width = 60f) { ship -> custom { Button("Sell") { fleet.value = fleet.value - ship } } }
                }
            }
            c = Column(Size.fillMaxWidth()) { Text("third") }
        }
        frame(); frame()
        expect(a.position.x < b.position.x && b.position.x < c.position.x,
            "the Row keeps its call order: first, second (with the table), third (x = ${a.position.x}, ${b.position.x}, ${c.position.x})")
        log(dumpLayout("tree", row))
        fleet.value = fleet.value.drop(1)
        frame(); frame()
        expect(a.position.x < b.position.x && b.position.x < c.position.x,
            "and after the table rebuilds (x = ${a.position.x}, ${b.position.x}, ${c.position.x})")
    }

    StarUITestSuite.group("table-order-variants") {
        fun check(name: String, middle: CustomPanelAPI.() -> Unit) {
            lateinit var a: CustomPanelAPI; lateinit var b: CustomPanelAPI; lateinit var c: CustomPanelAPI
            val host = root.Row(Size.mixed(Axis.Exact(900f), Axis.WrapContent), elementAt(40f, 40f), spacing = 10f) {
                a = Column(Size.fillMaxWidth()) { Text("first") }
                b = Column(Size.fillMaxWidth()) { middle() }
                c = Column(Size.fillMaxWidth()) { Text("third") }
            }
            frame(); frame()
            expect(a.position.x < b.position.x && b.position.x < c.position.x,
                "$name: order kept (x = ${a.position.x}, ${b.position.x}, ${c.position.x})")
            root.removeComponent(host)
        }
        val state = UIState(1)
        check("reactive column, no table") { Text("n = ${state.value}") }
        check("plain table") { Table(testShips, Size.fillMaxWidth()) { column("Ship") { it.name } } }
        check("table with a custom cell") {
            Table(testShips, Size.fillMaxWidth()) { column("Ship") { it.name }; column("", width = 60f) { custom { Text("x") } } }
        }
        check("StatGrid") { StatGrid { entry("a", "1") } }
    }

    StarUITestSuite.group("vanilla-elements") {
        val members = listOf("onslaught_Standard", "eagle_Assault", "wolf_Assault").map {
            Global.getSettings().createFleetMember(FleetMemberType.SHIP, it)
        }
        val stat = MutableStat(100f).apply { modifyFlat("hullmod", 20f, "Hullmod"); modifyMult("skill", 1.5f, "Skill") }
        val made = linkedMapOf<String, CustomPanelAPI>()
        root.Column(Size.exact(420f, 760f), elementAt(40f, 20f), spacing = 6f) {
            made["StatGrid"] = StatGrid(columns = 2) {
                entry("Speed", "100"); entry("Armor", "500", VanillaColors.negative); entry("Hull", "1000")
            }
            made["StatGrid flipped"] = StatGrid(valueFirst = true) { entry("Flux capacity", "5000"); entry("Dissipation", "300") }
            made["StatModGrid"] = StatModGrid(stat)
            made["LabelledValue"] = LabelledValue("Credits", "12,000")
            made["RelationshipBar"] = RelationshipBar(0.5f)
            made["ImportanceIndicator"] = ImportanceIndicator(PersonImportance.HIGH)
            made["CommodityIcons"] = CommodityIcons { icons("supplies", 3); icons("fuel", 2, IconRenderMode.DIM) }
            made["ShipList"] = ShipList(members, columns = 4)
            made["FleetSummary"] = FleetSummary(members)
        }
        // codex links need the codex, which may not be set up at application load: built apart, so a failure is clear
        val codexError = thrown {
            root.Column(Size.exact(300f, 120f), elementAt(500f, 20f)) {
                made["CodexEntries"] = CodexEntries("Related", setOf(CodexDataV2.getShipEntryId("onslaught")))
            }
        }
        frame(); frame()
        for ((name, panel) in made) expect(panel.w > 0f && panel.h > 0f, "$name has a size (${panel.w} x ${panel.h})")
        expect(made["StatGrid"]!!.w in 400f..420f, "StatGrid is built at its column's width, less vanilla's inset (${made["StatGrid"]!!.w})")
        near(made["RelationshipBar"]!!.h, 19f, "RelationshipBar is vanilla's 19 tall")
        near(made["LabelledValue"]!!.h, 42f, "LabelledValue is vanilla's 42 tall")
        expect(codexError == null, "CodexEntries builds at application load (got $codexError)")
        for ((name, panel) in made) log("$name: ${panel.w} x ${panel.h}")
        screenshot("final")
    }

    StarUITestSuite.group("vanilla-elements-keep-unchanged") {
        // A builder running again for something else keeps vanilla content whose arguments did not change.
        val members = listOf(Global.getSettings().createFleetMember(FleetMemberType.SHIP, "wolf_Assault"))
        val tick = UIState(0)
        val speed = UIState("100")
        val ships = UIState(testShips)
        var clicked: String? = null
        var tooltipFrom = -1
        lateinit var grid: CustomPanelAPI; lateinit var shipList: CustomPanelAPI; lateinit var table: CustomPanelAPI
        lateinit var icons: CustomPanelAPI
        root.Column(Size.exact(500f, 700f), elementAt(40f, 40f), spacing = 6f) {
            val t = tick.value
            Text("built for tick $t")
            grid = StatGrid { entry("Speed", speed.value); entry("Armor", "500") }
            icons = CommodityIcons { icons("supplies", 3) }
            shipList = ShipList(members)
            table = Table(ships.value.map { it.copy() }, Size.fillMaxWidth(),
                rowTooltip = { tooltipFrom = t; addPara("tooltip", 0f) },
                onRowClick = { clicked = "${it.name} at tick $t" }) {
                column("Ship") { it.name }
                column("Cost", width = 60f) { "${it.cost}" }
            }
        }
        frame(); frame()
        val grid0 = vanillaTable(grid); val icons0 = vanillaTable(icons); val ships0 = vanillaTable(shipList)
        val table0 = vanillaTable(table)

        tick.value = 1
        frame(); frame()
        expect(vanillaTable(grid) === grid0, "a rebuild with the same entries keeps the StatGrid")
        expect(vanillaTable(icons) === icons0, "and the CommodityIcons")
        expect(vanillaTable(table) === table0, "and the Table showing the same cells (from new, equal items)")
        expect(vanillaTable(shipList) !== ships0, "a ShipList (live fleet members) is made again")

        // the kept table calls the newest handlers, with the newest items
        val cell = cellOf(rowsOf(table)[2], 0)
        Input.sendList(root, Input.move(cell.cx, cell.cy))
        Input.sendList(root, Input.down(cell.cx, cell.cy))
        Input.sendList(root, Input.up(cell.cx, cell.cy))
        frame()
        expect(clicked == "Wolf at tick 1", "a kept table's row click runs the newest handler (got $clicked)")
        val state = (StarUIPanelPlugin.getAttached(table)!!.layoutEngine as VanillaElementEngine).callerState
        expect(state != null, "the table keeps its caller state on its engine")

        speed.value = "120"
        frame(); frame()
        expect(vanillaTable(grid) !== grid0, "a changed entry makes the StatGrid again")
        ships.value = ships.value.drop(1)
        frame(); frame()
        expect(vanillaTable(table) !== table0 && rowsOf(table).size == 2, "a changed item list makes the Table again (${rowsOf(table).size} rows)")
        log("tooltip last built for tick $tooltipFrom")
    }

    StarUITestSuite.group("table-rows-follow-custom-cells") {
        // Only the custom cell reads `tall`: the table's call does not change, but its rows must grow.
        val tall = UIState(false)
        lateinit var table: CustomPanelAPI
        root.Column(Size.exact(400f, 400f), elementAt(40f, 40f)) {
            table = Table(testShips.take(2), Size.fillMaxWidth()) {
                column("Ship") { it.name }
                column("", width = 80f) { custom { Spacer(Size.exact(10f, if (tall.value) 60f else 10f)) } }
            }
        }
        frame(); frame()
        val short = cellOf(rowsOf(table)[0], 1).h
        expect(short < 30f, "rows start at the item height ($short)")
        tall.value = true
        frame(); frame(); frame()
        val grown = cellOf(rowsOf(table)[0], 1).h
        expect(grown >= 59f, "a custom cell that grows makes the rows taller ($grown)")
        val box = table.getChildrenCopy2().filterIsInstance<CustomPanelAPI>().first()
        near(box.h, grown, "and its Box follows the new row height", tolerance = 1f)
        screenshot("final")
    }
}

fun registerVanillaPortTests() {
    // the ports that need campaign objects, with what the startup sector gives: a cargo, a planet in a new system, a
    // map. Survey requirements and the playthrough panel need a player fleet: campaign checks.
    StarUITestSuite.group("vanilla-ports-startup") {
        if (Global.getSector() == null) Global.setSector(com.fs.starfarer.campaign.CampaignEngine.getInstance())
        val cargo = Global.getFactory().createCargo(true).apply {
            addSupplies(120f); addFuel(300f); addCrew(50); addCommodity("metals", 40f)
        }
        lateinit var cargoList: CustomPanelAPI
        root.Column(Size.exact(420f, 300f), elementAt(40f, 20f)) {
            cargoList = CargoList(cargo)
        }
        frame(); frame()
        expect(cargoList.w > 0f && cargoList.h > 20f, "CargoList has a size (${cargoList.w} x ${cargoList.h})")

        var planetPanel: CustomPanelAPI? = null
        val planetError = thrown {
            val system = Global.getSector().createStarSystem("StarUI Test")
            val star = system.initStar("staruitest_star", "star_yellow", 500f, 0f)
            val planet = system.addPlanet("staruitest_planet", star, "Testworld", "terran", 0f, 150f, 2000f, 100f)
            root.Column(Size.exact(260f, 300f), elementAt(500f, 20f)) {
                planetPanel = PlanetInfo(planet, Size.exact(240f, 240f), withClass = true)
            }
            frame(); frame()
        }
        expect(planetError == null, "PlanetInfo builds with a planet made at startup (got $planetError)")
        planetPanel?.let { p ->
            near(p.w, 240f, "PlanetInfo takes its exact width")
            expect(p.getChildrenCopy2().isNotEmpty(), "PlanetInfo holds vanilla's planet display")
        }

        var map: CustomPanelAPI? = null
        val mapError = thrown {
            val params = com.fs.starfarer.api.ui.MapParams()
            root.Column(Size.exact(400f, 300f), elementAt(800f, 20f)) {
                map = SectorMap(params, Size.exact(380f, 280f), title = "Map")
            }
            frame(); frame()
        }
        log("SectorMap at startup: ${mapError ?: "built"}; map=${map?.sectorMap}")
        screenshot("final")
    }
}
