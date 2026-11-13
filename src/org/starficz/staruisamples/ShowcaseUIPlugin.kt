package org.starficz.staruisamples

import com.fs.starfarer.api.campaign.PersonImportance
import com.fs.starfarer.api.combat.MutableStat
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.codex.CodexDataV2
import com.fs.starfarer.api.ui.*
import org.lazywizard.lazylib.opengl.ColorUtils.glColor
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.runtime.*
import org.starficz.starui.vanilla.*
import org.starficz.starui.vanilla.VanillaColors.withAlpha
import java.awt.Color

/**
 * One full-screen page on the title screen that uses every part of StarUI: all containers and components, sizes,
 * every modifier, anchors, themes, and the reactive rules (if / for / key, ButtonGroup, onChange, peek).
 *
 * Styled the way vanilla screens are: VanillaColors (the player faction's UI colours and vanilla's text colours),
 * Orbitron for titles, headings and buttons, Insignia for text, and every card a Section (a vanilla section heading
 * over a bordered panel).
 *
 * Register it from a mod plugin's onApplicationLoad: `StarUIManager.registerPlugin(ShowcaseUIPlugin.Plugin())`.
 */
object ShowcaseUIPlugin {

    class Plugin : StarUIPlugin {
        override val addPanelToTitleScreen: StarUIBuilder = { showcase() }
    }

    private val BASE get() = VanillaColors.base
    private val DARK get() = VanillaColors.dark
    private val BRIGHT get() = VanillaColors.bright
    private val TEXT get() = VanillaColors.text
    private val GRAY get() = VanillaColors.gray
    private val HIGHLIGHT get() = VanillaColors.highlight
    private val POSITIVE get() = VanillaColors.positive
    private val NEGATIVE get() = VanillaColors.negative
    /** Vanilla's full-screen panels are black and opaque. */
    private val PAGE_BG = Color(0, 0, 0, 255)
    /** A demo area inside a section: the faction's dark colour, faint. */
    private val INSET_BG get() = DARK.withAlpha(70)

    private val log = Global.getLogger(ShowcaseUIPlugin::class.java)

    private class Task(val id: Int, val name: String) {
        val done = UIState(false)
    }

    // ------------------------------------------------------------------------------------------------------------
    // State. Everything a builder reads is a UIState; handlers change it and the builders that read it run again.
    // ------------------------------------------------------------------------------------------------------------
    /** Collapsed to a single button on the title screen until opened. */
    internal val open = UIState(false)
    private val outlines = UIState(false)
    private val tasks = UIState(listOf(Task(1, "Refit the Onslaught"), Task(2, "Buy 200 supplies"), Task(3, "Survey Askonia")))
    private var nextTaskId = 4
    private val newTask = UIState("")
    private val showOpen = UIState(true)
    private val showDone = UIState(true)
    private val details = UIState(false)
    private val lastChange = UIState("nothing yet")
    private val altTheme = UIState(false)
    private val volume = UIState(40f)

    /** The alternate theme: vanilla's story-point colours, as on its story-point options. */
    private fun altTheme(): StarUITheme {
        val story = VanillaColors.story
        val storyDark = VanillaColors.storyDark
        val storyBright = VanillaColors.storyBright
        return StarUITheme.Default.copy(
            button = StarUITheme.Default.button.copy(color = story, backgroundColor = storyDark, font = ButtonFont.ORBITRON_20),
            areaCheckbox = StarUITheme.Default.areaCheckbox.copy(baseColor = story, darkColor = storyDark, brightColor = storyBright)
        )
    }

    private fun CustomPanelAPI.showcase() {
        // A Box filling the screen: the page when open, one small button when hidden.
        Box(Size.fillMaxSize(), Modifier.anchor(Anchor.inside.topLeft.ofParent())) {
            if (open.value) {
                page()
                // A dialog over the page, as vanilla shows one: kept in the tree and folded shut when closed (it then
                // draws nothing and takes no input), so closing animates instead of vanishing
                Holo(Size.exact(520f, 300f), Modifier.align(Alignment.Center), open = dialogOpen.value,
                    foldDuration = foldTime(), animateFirstOpen = false) {
                    Column(Size.fillMaxSize(), Modifier.padding(14f), spacing = 8f) {
                        Text("A vanilla-style dialog", font = Font.ORBITRON_20_AA_BOLD, color = BASE)
                        Text("Holo(open = dialogOpen.value) over the page. Opening folds the frame out from a line " +
                            "through its middle with a burst of static; the content appears in the last quarter. " +
                            "Closing folds it back the same way.", Size.fillMaxWidth())
                        Text("Fold time: ${"%.2f".format(foldTime())} s" + if (slowFolds.value) " (slow motion)" else " (vanilla's)",
                            color = GRAY)
                        FlexSpacer()
                        Row(Size.fillMaxWidth(), spacing = 8f) {
                            Checkbox("Slow motion", bind = slowFolds)
                            FlexSpacer()
                            Button("Close", shortcut = Keyboard.KEY_ESCAPE) { dialogOpen.value = false }
                        }
                    }
                }
            }
            else Button("StarUI showcase", modifier = Modifier.align(Alignment.BottomLeft).margin(20f)) { open.value = true }
        }
    }

    private fun UIPanelAPI.page() {
        lateinit var status: LabelAPI
        var sinceStatus = 0f
        Column(
            Size.fillMaxSize(),
            Modifier.background(PAGE_BG)
                .padding(horizontal = 20f, vertical = 14f)
                // the page sits over the title menu: input none of its own controls used stops here
                .plugin { consumeEvents = true }
                .onRender { if (outlines.peek()) ExampleUIPlugin.drawDebugBorders(panel) }   // render code never subscribes
                .onAdvance { amount ->
                    sinceStatus += amount
                    if (sinceStatus >= 0.5f) {
                        sinceStatus = 0f
                        // a direct label update: no rebuild, and the header row re-lays out because the text changed
                        status.text = "Live scopes: ${StarUIManager.liveScopeCount}"
                    }
                },
            spacing = 10f
        ) {
            // ---------------------------------------- header ----------------------------------------
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 12f) {
                Text("StarUI Showcase", font = Font.ORBITRON_20_AA_BOLD, color = BASE)
                Text("Every component, modifier and reactive rule on one page", color = GRAY)
                FlexSpacer()
                status = Text("Live scopes: ...", color = GRAY,
                    modifier = Modifier.tooltip("StarUIManager.liveScopeCount, refreshed twice a second from onAdvance"))
                Checkbox("Outlines", bind = outlines,
                    modifier = Modifier.tooltip("Draws every component's bounds (Modifier.onRender on this page)"))
                Button("Hide", shortcut = Keyboard.KEY_H, modifier = Modifier.tooltip("open = false; the Box above shows a single button instead (shortcut H)")) {
                    open.value = false
                }
            }
            Divider(color = DARK)

            // ---------------------------------------- body: scrolls under the header ----------------------------------------
            // A scrolling Column fills what is left; the right padding keeps the scrollbar clear.
            Column(Size.fillMaxSize(), overflow = Overflow.ScrollVertical) {
                Column(Size.fillMaxWidth(), Modifier.padding(right = 14f), spacing = 10f) {
                    Row(Size.fillMaxWidth(), spacing = 14f) {
                        Column(Size.fillMaxWidth(), spacing = 10f) { layoutColumn() }
                        Column(Size.fillMaxWidth(), spacing = 10f) { stateColumn() }
                        Column(Size.fillMaxWidth(), spacing = 10f) { contentColumn() }
                    }
                    // the second band: every option and helper the cards above leave out
                    Row(Size.fillMaxWidth(), spacing = 14f) {
                        Column(Size.fillMaxWidth(), spacing = 10f) { moreLayoutColumn() }
                        Column(Size.fillMaxWidth(), spacing = 10f) { moreControlsColumn() }
                        Column(Size.fillMaxWidth(), spacing = 10f) { moreContentColumn() }
                    }
                    // the third band: borders, vanilla content that needs no campaign, boxed vanilla objects
                    Row(Size.fillMaxWidth(), spacing = 14f) {
                        Column(Size.fillMaxWidth(), spacing = 10f) { bordersColumn() }
                        Column(Size.fillMaxWidth(), spacing = 10f) { vanillaPortsColumn() }
                        Column(Size.fillMaxWidth(), spacing = 10f) { boxedColumn() }
                    }
                    fullSizeShips()
                }
            }
        }
    }

    // =================================================================================================================
    // Second band, column 1: more layout
    // =================================================================================================================
    private fun UIPanelAPI.moreLayoutColumn() {
        Section("Sizes: exact, fill at a fixed size, mixed, weights") {
            Row(Size.fillMaxWidth(height = 40f), Modifier.background(INSET_BG).padding(horizontal = 4f),
                verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                Spacer(Size.exact(40f, 20f), Modifier.background(BASE).tooltip("Size.exact(40, 20)"))
                Spacer(Size.fillMaxHeight(width = 20f), Modifier.background(BRIGHT).tooltip("Size.fillMaxHeight(width = 20)"))
                Spacer(Size.mixed(Axis.FillMax(1f), Axis.Exact(10f)), Modifier.background(DARK.brighter()).tooltip("Size.mixed(Axis.FillMax(1), Axis.Exact(10))"))
                Spacer(Size.mixed(Axis.FillMax(2f), Axis.Exact(26f)), Modifier.background(BASE.withAlpha(150)).tooltip("Size.mixed(Axis.FillMax(2), Axis.Exact(26)): twice the share"))
            }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("A")
                FlexSpacer(1f)
                Text("FlexSpacer(1) | FlexSpacer(3)", color = GRAY)
                FlexSpacer(3f)
                Text("B")
            }
        }

        Section("Row: grow Leftwards, verticalAlignment") {
            Row(Size.fillMaxWidth(height = 36f), Modifier.background(INSET_BG).padding(horizontal = 6f), grow = HGrow.Leftwards,
                verticalAlignment = Alignment.Bottom, spacing = 8f) {
                Text("1st (rightmost)")
                Text("2nd")
                Text("3rd: HGrow.Leftwards, Alignment.Bottom", color = GRAY)
            }
            Row(Size.fillMaxWidth(height = 36f), Modifier.background(INSET_BG).padding(horizontal = 6f),
                verticalAlignment = Alignment.CenterVertically, spacing = 8f) {
                Spacer(Size.exact(12f, 30f), Modifier.background(BASE))
                Spacer(Size.exact(12f, 10f), Modifier.background(BASE))
                Text("Alignment.CenterVertically", color = GRAY)
            }
        }

        Section("Anchors: above, below, leftOf, my corner to yours") {
            CustomPanel(Size.fillMaxWidth(height = 130f), Modifier.background(INSET_BG)) {
                val hub = Spacer(Size.exact(70f, 24f), Modifier.anchor(Anchor.inside.center.ofParent()).background(DARK.brighter()))
                Text("above.matchingCenter", modifier = Modifier.anchor(Anchor.above.sibling(hub).matchingCenter(4f)))
                Text("below.matchingRightEdge", color = GRAY, modifier = Modifier.anchor(Anchor.below.sibling(hub).matchingRightEdge(4f)))
                Text("leftOf", color = HIGHLIGHT, modifier = Modifier.anchor(Anchor.leftOf.sibling(hub).matchingCenter(6f)))
                Text("rightOf.matchingBottomEdge", modifier = Modifier.anchor(Anchor.rightOf.sibling(hub).matchingBottomEdge(6f)))
                Spacer(Size.exact(8f, 8f), Modifier.anchor(Anchor.my.bottomLeft.toTopRight.ofSibling(hub)).background(BRIGHT)
                    .tooltip("Anchor.my.bottomLeft.toTopRight.ofSibling(hub)"))
                Text("inside.topRight", modifier = Modifier.anchor(Anchor.inside.topRight.ofParent(6f, 6f)))
                Text("inside.bottomCenter", color = GRAY, modifier = Modifier.anchor(Anchor.inside.bottomCenter.ofParent(6f)))
            }
        }

        Section("Spacing by side, hitBoxPadding by side") {
            Row(Size.fillMaxWidth(), Modifier.background(INSET_BG), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Size.exact(30f, 16f), Modifier.margin(left = 40f, top = 4f, bottom = 12f).background(BASE))
                Text("margin(left = 40, top = 4, bottom = 12)", color = GRAY, modifier = Modifier.margin(horizontal = 8f, vertical = 0f))
            }
            Box(Size.fillMaxWidth(), Modifier.background(INSET_BG).padding(left = 30f, top = 4f, bottom = 14f)) {
                Text("padding(left = 30, top = 4, bottom = 14)")
            }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 8f) {
                lateinit var hitLabel: LabelAPI
                Button("Wide hit box", modifier = Modifier.hitBoxPadding(right = 120f)) { hitLabel.text = "Hit: hitBoxPadding(right = 120)" }
                hitLabel = Text("click up to 120 px right of the button", color = GRAY)
            }
        }

        Section("Section options: untitled, colours, padding") {
            Row(Size.fillMaxWidth(), spacing = 8f) {
                Section(null, Size.fillMaxWidth(), contentPadding = PaddingValues.all(10f)) {
                    Text("Section(null): no heading")
                    Text("contentPadding = all(10)", color = GRAY)
                }
                Section("Custom colours", Size.fillMaxWidth(), backgroundColor = DARK.withAlpha(120), borderColor = BRIGHT, spacing = 1f) {
                    Text("backgroundColor, borderColor")
                    Text("spacing = 1", color = GRAY)
                }
            }
        }

        Section("Overflow: clip and scroll, per axis") {
            Text("Clip cuts children at the edge; Visible (the default) lets them spill out.", color = GRAY)
            Row(Size.fillMaxWidth(height = 64f), spacing = 56f) {
                for ((name, overflow) in listOf("Clip" to Overflow.Clip, "Visible" to Overflow.Visible)) {
                    Box(Size.exact(110f, 40f), Modifier.border(BRIGHT), overflow = overflow) {
                        Box(Size.exact(150f, 56f), Modifier.background(INSET_BG).padding(4f)) { Text("$name: a child bigger than its box", color = HIGHLIGHT) }
                    }
                }
            }
            Text("ScrollHorizontal: the wheel scrolls it (no vertical overflow)", color = GRAY)
            Row(Size.fillMaxWidth(height = 64f), Modifier.background(INSET_BG), spacing = 6f,
                verticalAlignment = Alignment.CenterVertically, overflow = Overflow.ScrollHorizontal) {
                for (i in 1..12) Box(Size.exact(84f, 48f), Modifier.background(DARK).padding(4f)) { Text("Card $i", color = TEXT) }
            }
            Text("Scroll (2D): wheel for y, Shift+wheel for x, right-drag to pan", color = GRAY)
            Box(Size.fillMaxWidth(height = 130f), Modifier.background(INSET_BG), overflow = Overflow.Scroll) {
                Column(Size.wrapContent(), spacing = 2f) {
                    for (r in 1..14) Row(Size.wrapContent(), spacing = 2f) {
                        for (c in 1..12) Box(Size.exact(58f, 22f), Modifier.background(if ((r + c) % 2 == 0) DARK else INSET_BG)) {
                            Text("$r,$c", color = if (r == 1 || c == 1) HIGHLIGHT else TEXT, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
            Text("A fill spacer pins the footer to the bottom until the list overflows", color = GRAY)
            Row(Size.fillMaxWidth(height = 110f), spacing = 8f) {
                Column(Size.fillMaxSize(), Modifier.background(INSET_BG).padding(4f), spacing = 2f, overflow = Overflow.ScrollVertical) {
                    for (i in 1..overflowLines.value) Text("Line $i")
                    Spacer(Size.fillMaxHeight(width = 1f))
                    Row(Size.fillMaxWidth(), spacing = 4f) {
                        Button("Add") { overflowLines.value++ }
                        Button("Remove") { if (overflowLines.value > 0) overflowLines.value-- }
                    }
                }
                Theme(StarUITheme.current.copy(scroll = StarUITheme.current.scroll.copy(isLeftScrollbar = true, scrollWidgetColor = BRIGHT, renderShadows = false))) {
                    Column(Size.fillMaxSize(), Modifier.background(INSET_BG), horizontalAlignment = Alignment.CenterHorizontally,
                        spacing = 2f, overflow = Overflow.ScrollVertical) {
                        Text("theme: left scrollbar,", color = HIGHLIGHT)
                        Text("widget colour, no shadows", color = HIGHLIGHT)
                        for (i in 1..12) Text("Line $i")
                    }
                }
            }
            Text("LazyColumn: 10,000 items, only the rows near the view exist. Each row's count is its own state; " +
                "scroll down, then remove items above: the view stays on the same rows.", Size.fillMaxWidth(), color = GRAY)
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 6f) {
                Button("Remove the first 5") { lazyItems.value = lazyItems.value.drop(5) }
                Button("Reset") { lazyItems.value = startLazyItems }
                Text("${lazyItems.value.size} items", color = GRAY)
            }
            LazyColumn(lazyItems.value, Size.fillMaxWidth(height = 130f), Modifier.background(INSET_BG).padding(horizontal = 4f),
                itemHeight = 24f, key = { it.id }) { item ->
                Row(Size.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, spacing = 6f) {
                    Text("Item ${item.id}", color = if (item.id % 100 == 0) HIGHLIGHT else TEXT)
                    FlexSpacer()
                    Text("clicked ${item.clicks.value}", color = if (item.clicks.value > 0) POSITIVE else GRAY)
                    Button("+1", contentPadding = PaddingValues.symmetric(horizontal = 6f, vertical = 1f)) { item.clicks.value++ }
                }
            }
            Text("LazyRow: 2,000 cards (the wheel scrolls it sideways)", color = GRAY)
            LazyRow(lazyCards, Size.fillMaxWidth(height = 56f), Modifier.background(INSET_BG), itemWidth = 84f, spacing = 6f) { i ->
                Box(Size.fillMaxSize(), Modifier.background(DARK).padding(4f)) { Text("Card ${i + 1}", color = TEXT) }
            }
        }
    }

    // =================================================================================================================
    // Second band, column 2: every control option
    // =================================================================================================================
    private val overflowLines = UIState(2)
    private class LazyDemoItem(val id: Int) {
        val clicks = UIState(0)
    }
    private val startLazyItems = (1..10_000).map { LazyDemoItem(it) }
    private val lazyItems = UIState(startLazyItems)
    private val lazyCards = (0 until 2_000).toList()
    private val buttonsEnabled = UIState(false)
    private val hullSizes = listOf("Frigate", "Destroyer", "Cruiser", "Capital").map { it to UIState(it == "Frigate") }
    private val shipName = UIState("")
    private val crew = UIState(250f)
    private val percent = UIState(30f)
    private val barTarget = UIState(20f)

    private fun UIPanelAPI.moreControlsColumn() {
        Section("Button options") {
            Row(Size.fillMaxWidth(), spacing = 4f) {
                for (cut in listOf(CutStyle.TL_BR, CutStyle.BL_TR, CutStyle.TOP, CutStyle.BOTTOM, CutStyle.ALL, CutStyle.NONE)) {
                    Button(cut.name, cutStyle = cut, modifier = Modifier.tooltip("cutStyle = CutStyle.${cut.name}"))
                }
            }
            // every ButtonFont; the default is ORBITRON_12_CONDENSED
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                Button("Victor 10", font = ButtonFont.VICTOR_10)
                Button("Victor 14", font = ButtonFont.VICTOR_14)
                Button("Orbitron 20", font = ButtonFont.ORBITRON_20)
                Button("20 bold", font = ButtonFont.ORBITRON_20_BOLD)
            }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                Button("Orbitron 24", font = ButtonFont.ORBITRON_24)
                Button("24 bold", font = ButtonFont.ORBITRON_24_BOLD)
            }
            Button("textAlign = Left", Size.fillMaxWidth(), textAlign = TextAlign.Left)
            Button("textAlign = Right", Size.fillMaxWidth(), textAlign = TextAlign.Right)
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                Button("Positive", color = POSITIVE, backgroundColor = POSITIVE.darker().darker())
                Button("Negative", color = NEGATIVE, backgroundColor = NEGATIVE.darker().darker())
                FlexSpacer()
                Checkbox("Enabled", bind = buttonsEnabled)
                // a plain ButtonAPI setter, re-applied whenever the state changes and this row builds again
                Button("Only when enabled").isEnabled = buttonsEnabled.value
            }
        }

        Section("Checkbox and AreaCheckbox options") {
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 12f) {
                Checkbox("TINY", checkboxSize = ButtonAPI.UICheckboxSize.TINY)
                Checkbox("SMALL", checkboxSize = ButtonAPI.UICheckboxSize.SMALL)
                Checkbox("LARGE, Orbitron, highlight", checkboxSize = ButtonAPI.UICheckboxSize.LARGE, font = Font.ORBITRON_20_AA, color = HIGHLIGHT)
            }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                AreaCheckbox("leftAlign = true", size = Size.exact(150f, 24f), leftAlign = true)
                AreaCheckbox("Own colours", baseColor = POSITIVE, darkColor = POSITIVE.darker().darker(), brightColor = POSITIVE.brighter())
                AreaCheckbox("Victor 14", font = Font.VICTOR_14)
            }
            val sizeGroup = ButtonGroup()
            Text("One ButtonGroup: a click selects one, Shift-click adds, clicking the only one selects all", color = GRAY)
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                for ((name, state) in hullSizes) AreaCheckbox(name, state, Size.exact(90f, 22f), buttonGroup = sizeGroup)
            }
            val selected = hullSizes.filter { it.second.value }.joinToString { it.first }
            Text("Selected: $selected", highlightedText = listOf(selected to HIGHLIGHT))
        }

        Section("TextField options") {
            TextField(shipName, font = Font.ORBITRON_20_AA, placeholder = "Orbitron 20 field")
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 6f) {
                TextField(shipName, Size.fillMaxWidth(), font = Font.VICTOR_14, placeholder = "The same state, Victor 14",
                    contentPadding = PaddingValues.symmetric(horizontal = 8f, vertical = 2f))
                Text("${shipName.value.length} chars", color = GRAY)
            }
            Text("Both fields edit one UIState: type in either and the other follows", color = GRAY)
        }

        Section("Slider and ProgressBar options") {
            Slider(crew, Size.fillMaxWidth(height = 25f), valueRange = 0f..1000f, text = "Crew", step = 50f,
                configure = { setShowValueOnly(true) })
            Slider(percent, Size.fillMaxWidth(height = 25f), text = "Percent", barColor = POSITIVE.darker(),
                configure = { setShowPercent(true) })
            Text("valueRange = 0..1000, setShowValueOnly; setShowPercent (configure = { ... })", color = GRAY)
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                Button("Bars to 10") { barTarget.value = 10f }
                Button("Bars to 90") { barTarget.value = 90f }
            }
            ProgressBar(barTarget.value, Size.fillMaxWidth(height = 18f), text = "animate = true")
            ProgressBar(barTarget.value, Size.fillMaxWidth(height = 18f), text = "animate = false", animate = false,
                barColor = HIGHLIGHT.darker())
            ProgressBar(barTarget.value, Size.fillMaxWidth(height = 18f), text = "10 subdivisions",
                configure = { setNumSubivisions(10) })
        }

        Section("Reactive scopes: only the builder that reads a state runs again") {
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 6f) {
                Button("Left + 1") { leftCount.value++ }
                Button("Right + 1") { rightCount.value++ }
            }
            Row(Size.fillMaxWidth(), spacing = 6f) {
                Column(Size.fillMaxWidth(), Modifier.background(INSET_BG).padding(6f)) {
                    leftBuilds++
                    Text("left = ${leftCount.value}")
                    Text("this column built $leftBuilds times", color = GRAY)
                }
                Column(Size.fillMaxWidth(), Modifier.background(INSET_BG).padding(6f)) {
                    rightBuilds++
                    Text("right = ${rightCount.value}")
                    Text("this column built $rightBuilds times", color = GRAY)
                }
            }
            Text("Counters made with mutableStateOf; each column reads only its own", color = GRAY)
        }
        Section("Table: text, styled and custom cells") {
            lateinit var clickLabel: LabelAPI
            Table(fleet.value, Size.fillMaxWidth(), rowKey = { it.id }, emptyText = "Fleet empty",
                rowTooltip = { ship -> addPara("Row tooltip for the %s.", 0f, HIGHLIGHT, ship.name) },
                onRowClick = { ship -> clickLabel.text = "onRowClick: ${ship.name}" }) {
                column("Ship", tooltip = "A weighted column: it takes the width the others leave") { it.name }
                column("Cost", width = 70f, align = TextAlign.Right) { ship -> cell("${ship.cost}", color = HIGHLIGHT) }
                column("Status", width = 90f) { ship ->
                    cell("${ship.cr}% CR", highlightedText = listOf("${ship.cr}%" to if (ship.cr < 50) NEGATIVE else POSITIVE))
                }
                column("", width = 70f) { ship ->
                    custom { Button("Sell", modifier = Modifier.align(Alignment.Center)) { fleet.value = fleet.value - ship } }
                }
            }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 8f) {
                Button("Reset fleet") { fleet.value = startFleet }
                clickLabel = Text("Click a row (onRowClick), hover one (rowTooltip)", color = GRAY)
            }
        }
    }
    private class FleetShip(val id: Int, val name: String, val cost: Int, val cr: Int)
    private val startFleet = listOf(FleetShip(1, "Onslaught", 320, 70), FleetShip(2, "Eagle", 180, 45),
        FleetShip(3, "Hammerhead", 90, 85), FleetShip(4, "Wolf", 40, 30))
    private val fleet = UIState(startFleet)
    private val leftCount = mutableStateOf(0)
    private val rightCount = mutableStateOf(0)
    private var leftBuilds = 0
    private var rightBuilds = 0

    // =================================================================================================================
    // Second band, column 3: more content
    // =================================================================================================================
    private fun UIPanelAPI.moreContentColumn() {
        Section("Every Font", spacing = 2f) {
            for (font in Font.entries) Text(font.name, font = font)
        }

        Section("Text: wrapping, alignment, measuring") {
            Text("A Text with a fill width wraps onto as many lines as it needs, and its height follows. " +
                "Resize the window and it wraps again.", Size.fillMaxWidth())
            Text("An exact width of 220 wraps here, whatever the column does.", Size.mixed(Axis.Exact(220f), Axis.WrapContent),
                Modifier.background(INSET_BG), color = GRAY)
            Text("TextAlign.Right", Size.fillMaxWidth(), textAlign = TextAlign.Right)
            SectionHeading("SectionHeading, Center (the default)")
            SectionHeading("Right, own colours", color = HIGHLIGHT, backgroundColor = HIGHLIGHT.withAlpha(40), textAlign = TextAlign.Right)
            val (w, h) = measureTextSize("Starsector", Font.ORBITRON_20_AA)
            Text("measureTextSize(\"Starsector\", ORBITRON_20_AA) = ${w.toInt()} x ${h.toInt()}", color = GRAY,
                highlightedText = listOf("${w.toInt()} x ${h.toInt()}" to HIGHLIGHT))
        }

        Section("Image: sizes, ContentScale, options") {
            val icon = "graphics/icons/cargo/supplies.png"
            val tall = "graphics/ships/wolf/wolf_base.png"   // 52 x 96
            for (sprite in listOf(icon, tall)) if (Global.getSettings().getSprite(sprite).textureId == 0) runCatching { Global.getSettings().loadTexture(sprite) }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 10f) {
                Image(icon, ImageSize.native(), Modifier.tooltip("ImageSize.native()"))
                Image(icon, ImageSize.native(scale = 0.5f), Modifier.tooltip("ImageSize.native(scale = 0.5)"))
                Image(icon, Size.exact(32f, 32f), Modifier.tooltip("withOutline = true"), withOutline = true)
                Image(icon, Size.exact(32f, 32f), Modifier.tooltip("renderSchematic = true"), renderSchematic = true)
            }
            // vertical padding: ContentScale.None draws the 96 px tall sprite past its 60 px box
            Row(Size.fillMaxWidth(), Modifier.padding(horizontal = 0f, vertical = 18f), verticalAlignment = Alignment.CenterVertically, spacing = 8f) {
                for (scale in ContentScale.entries) {
                    Column(Size.wrapContent(), horizontalAlignment = Alignment.CenterHorizontally, spacing = 2f) {
                        Box(Size.exact(90f, 60f), Modifier.background(INSET_BG)) {
                            Image(tall, Size.exact(90f, 60f), contentScale = scale)
                        }
                        Text(scale.name, color = GRAY)
                    }
                }
                Column(Size.wrapContent(), horizontalAlignment = Alignment.CenterHorizontally, spacing = 2f) {
                    Box(Size.exact(90f, 60f), Modifier.background(INSET_BG)) {
                        Image(tall, ImageSize.aspectWrap(), Modifier.align(Alignment.Center))
                    }
                    Text("aspectWrap()", color = GRAY)
                }
            }
        }

        Section("ShipDisplay options") {
            val lasher = Global.getSettings().getVariant("lasher_Assault")
            val heron = Global.getSettings().createFleetMember(FleetMemberType.SHIP, "heron_Strike")
            Row(Size.fillMaxWidth(), spacing = 6f) {
                ShipDisplay(Size.exact(100f, 100f), Modifier.tooltip("fleetMember, showFighters = true"), fleetMember = heron, showFighters = true)
                ShipDisplay(Size.exact(100f, 100f), Modifier.tooltip("showBorder, borderColor"), variant = lasher, showBorder = true, borderColor = BRIGHT)
                ShipDisplay(Size.exact(100f, 100f), Modifier.tooltip("scanlines = 0.5"), variant = lasher, scanlines = 0.5f)
                ShipDisplay(Size.exact(100f, 100f), Modifier.tooltip("scaleSmallerShips = true: a frigate at 50%"), variant = lasher, scaleSmallerShips = true)
            }
            // labels need a variant and a shipName; they are the display's width minus 10, so give them room
            Row(Size.fillMaxWidth(), spacing = 6f) {
                ShipDisplay(Size.exact(220f, 160f), Modifier.tooltip("style = NORMAL (the default), shipName"), variant = lasher,
                    shipName = "ISS Example")
                ShipDisplay(Size.exact(220f, 160f), Modifier.tooltip("style = MINI, labelColor = highlight"), variant = lasher,
                    shipName = "ISS Example", style = BoxedUIShipPreview.Style.MINI, labelColor = HIGHLIGHT)
            }
        }

        Section("More modifiers: border, onHover, keys, tooltip builder") {
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 8f) {
                Box(Size.exact(90f, 36f), Modifier.border(BASE)) { Text("border()", modifier = Modifier.align(Alignment.Center)) }
                Box(Size.exact(90f, 36f), Modifier.border(HIGHLIGHT, 3f)) { Text("width = 3", modifier = Modifier.align(Alignment.Center)) }
                Box(Size.exact(90f, 36f), Modifier.border(VanillaFrame.TitleMenu)) { Text("TitleMenu", modifier = Modifier.align(Alignment.Center)) }
                Box(Size.fillMaxWidth(height = 36f), Modifier.background(INSET_BG).tooltip(width = 320f) {
                    addSectionHeading("Built with TooltipMakerAPI", com.fs.starfarer.api.ui.Alignment.MID, 0f)
                    addPara("Any vanilla tooltip content: %s, %s and more.", 6f, HIGHLIGHT, "highlights", "headings")
                }) { Text("tooltip { builder }", modifier = Modifier.align(Alignment.Center)) }
            }
            lateinit var hoverLabel: LabelAPI
            CustomPanel(Size.fillMaxWidth(height = 36f), Modifier.background(INSET_BG)
                .onHover { event -> hoverLabel.text = "onHover: mouse at ${event.x}, ${event.y}" }
                .onHoverExit { hoverLabel.text = "Move the mouse here (onHover)" }) {
                hoverLabel = Text("Move the mouse here (onHover)", modifier = Modifier.anchor(Anchor.inside.center.ofParent()))
            }
            lateinit var keyLabel: LabelAPI
            var repeats = 0
            CustomPanel(Size.fillMaxWidth(height = 36f), Modifier.background(INSET_BG)
                .onKeyHeld { repeats++; keyLabel.text = "onKeyHeld: $repeats repeats" }
                .onKeyUp { event -> keyLabel.text = "onKeyUp: ${Keyboard.getKeyName(event.eventValue)} after $repeats repeats"; repeats = 0 }) {
                keyLabel = Text("Hold any key, then let go (onKeyHeld, onKeyUp)", modifier = Modifier.anchor(Anchor.inside.center.ofParent()))
            }
        }

        Section("VanillaColors") {
            val colours = listOf("base" to VanillaColors.base, "dark" to VanillaColors.dark, "bright" to VanillaColors.bright,
                "text" to VanillaColors.text, "gray" to VanillaColors.gray, "highlight" to VanillaColors.highlight,
                "positive" to VanillaColors.positive, "negative" to VanillaColors.negative, "story" to VanillaColors.story,
                "storyDark" to VanillaColors.storyDark, "storyBright" to VanillaColors.storyBright, "panel" to VanillaColors.panel)
            for (row in colours.chunked(4)) Row(Size.fillMaxWidth(), spacing = 6f) {
                for ((name, colour) in row) Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                    Spacer(Size.exact(16f, 16f), Modifier.background(colour).border(GRAY.withAlpha(80)))
                    Text(name, color = GRAY)
                }
            }
        }
        Section("Vanilla tooltip content") {
            StatGrid(columns = 2) {
                entry("Max speed", "120"); entry("Armor", "1500", VanillaColors.negative)
                entry("Hull", "8000"); entry("Flux capacity", "12000")
            }
            StatModGrid(MutableStat(100f).apply { modifyFlat("hullmod", 20f, "Hardened Shields"); modifyMult("skill", 1.25f, "Field Modulation") })
            Row(Size.fillMaxWidth(), spacing = 8f) {
                LabelledValue("Credits", "12,000", Size.fillMaxWidth())
                LabelledValue("Story points", "3", Size.fillMaxWidth(), valueColor = VanillaColors.story)
            }
            RelationshipBar(0.35f, Size.fillMaxWidth())
            ImportanceIndicator(PersonImportance.HIGH, Size.fillMaxWidth())
            CommodityIcons { icons("supplies", 4); icons("fuel", 2); icons("heavy_machinery", 1, IconRenderMode.DIM) }
            val members = listOf("onslaught_Standard", "eagle_Assault", "hammerhead_Balanced", "wolf_Assault")
                .map { Global.getSettings().createFleetMember(FleetMemberType.SHIP, it) }
            ShipList(members, columns = 4, iconSize = 40f)
            FleetSummary(members, Size.fillMaxWidth())
            CodexEntries("Related codex entries", setOf(CodexDataV2.getShipEntryId("onslaught"), CodexDataV2.getShipEntryId("eagle")))
        }
    }

    // =================================================================================================================
    // Third band, column 1: borders
    // =================================================================================================================
    private fun UIPanelAPI.bordersColumn() {
        Section("Borders: the line") {
            Text("Modifier.border(color = VanillaColors.dark, width = 1f): what vanilla's campaign panels draw. " +
                "Four rectangles inside the edge, so the corners are square and a translucent colour is even.", Size.fillMaxWidth(), color = GRAY)
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 8f) {
                Box(Size.wrapContent(), Modifier.border().padding(6f)) { Text("border()") }
                Box(Size.wrapContent(), Modifier.border(BASE).padding(6f)) { Text("BASE") }
                Box(Size.wrapContent(), Modifier.border(HIGHLIGHT, 2f).padding(6f)) { Text("HIGHLIGHT, 2") }
                Box(Size.wrapContent(), Modifier.border(POSITIVE, 4f).padding(6f)) { Text("POSITIVE, 4") }
                Box(Size.wrapContent(), Modifier.background(INSET_BG).border(BRIGHT.withAlpha(110), 8f).padding(6f)) {
                    Text("translucent, 8")
                }
            }
        }

        Section("Borders: vanilla's sprite frame") {
            Text("Modifier.border(VanillaFrame.TitleMenu): ui_border1, the frame of vanilla's dialogs and title menu, " +
                "from its own sprites, untinted: corners at their size, each edge repeated between them. The game " +
                "ships older sets too, but only dead or tutorial code loads them.", Size.fillMaxWidth(), color = GRAY)
            Row(Size.fillMaxWidth(), spacing = 8f) {
                Column(Size.fillMaxWidth(), Modifier.border(VanillaFrame.TitleMenu).padding(6f), spacing = 2f) {
                    Text("TitleMenu, bare", font = Font.ORBITRON_12, color = BASE)
                    Text("depth ${VanillaFrame.TitleMenu.depth.toInt()}", color = HIGHLIGHT)
                }
                Column(Size.fillMaxWidth(), Modifier.background(INSET_BG).padding(6f), spacing = 2f) {
                    Text("Vanilla never shows it bare: its holo panel draws a dark background over the frame's inner " +
                        "edge, a tint and noise. For that look use Holo (below).", Size.fillMaxWidth(), color = GRAY)
                }
            }
        }

        Section("Borders take their space (CSS box model)") {
            Text("A border's depth is added to the padding on every side: padding is the gap inside the border, and " +
                "content never sits on it. A wrapping size grows by it; an exact size keeps its size and the content " +
                "gets less room.", Size.fillMaxWidth(), color = GRAY)
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.Top, spacing = 10f) {
                for ((label, framed) in listOf("no border" to false, "TitleMenu" to true)) {
                    lateinit var sizeLabel: LabelAPI
                    Column(Size.wrapContent(), horizontalAlignment = Alignment.CenterHorizontally, spacing = 3f) {
                        val mod = if (framed) Modifier.border(VanillaFrame.TitleMenu) else Modifier
                        // onLayout runs on the component's plugin: width and height are its laid-out size
                        Box(Size.wrapContent(), mod.onLayout { sizeLabel.text = "wrap: ${width.toInt()} x ${height.toInt()}" }) {
                            Spacer(Size.exact(80f, 30f), Modifier.background(BASE.withAlpha(140)))
                        }
                        Text(label, color = GRAY)
                        sizeLabel = Text("wrap: ...", color = HIGHLIGHT)
                    }
                }
                lateinit var exactLabel: LabelAPI
                Column(Size.wrapContent(), horizontalAlignment = Alignment.CenterHorizontally, spacing = 3f) {
                    Box(Size.exact(96f, 46f), Modifier.border(VanillaFrame.TitleMenu)) {
                        Spacer(Size.fillMaxSize(), Modifier.background(BASE.withAlpha(140))
                            .onLayout { exactLabel.text = "content ${width.toInt()} x ${height.toInt()}" })
                    }
                    Text("exact 96 x 46", color = GRAY)
                    exactLabel = Text("content ...", color = HIGHLIGHT)
                }
            }
        }

        Section("Holo: vanilla's framed window") {
            Text("Holo(size, open = ...) { content }: vanilla's own holo panel (getHolo() in its dialogs), drawn by " +
                "vanilla: the ui_border1 frame, a dark scanline background over its inner edge, a teal tint and " +
                "noise. open folds it open and shut over 0.25 s, as vanilla's dialogs do: the frame grows from a " +
                "line through its middle and the content shows in the last quarter.", Size.fillMaxWidth(), color = GRAY)
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 8f) {
                Button("Open a dialog", modifier = Modifier.tooltip("A large Holo over the page, folding open like vanilla's dialogs")) {
                    dialogOpen.value = true
                }
                Checkbox("Slow motion (2 s folds)", bind = slowFolds,
                    modifier = Modifier.tooltip("foldDuration = 2f instead of vanilla's 0.25 s, to watch the fold"))
                Button("Static", modifier = Modifier.tooltip("flickerNoise(0, 1.25): vanilla's opening and closing static")) {
                    showcaseHolo?.flickerNoise(0f, 1.25f)
                }
            }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                Button(if (holoOpen.value) "Close" else "Open",
                    modifier = Modifier.tooltip("open = ...: folds shut or open, with vanilla's burst of static")) { holoOpen.value = !holoOpen.value }
                Button("Page ${holoPage.value + 1} of 3",
                    modifier = Modifier.tooltip("contentKey = page: new content through vanilla's 0.2 s static transition")) {
                    holoPage.value = (holoPage.value + 1) % 3
                }
                Button(if (holoSeeThrough.value) "TitleScreen" else "Dialog",
                    modifier = Modifier.tooltip("background = HoloBackground.Dialog (opaque, most dialogs) or .TitleScreen (see-through)")) {
                    holoSeeThrough.value = !holoSeeThrough.value
                }
            }
            Row(Size.fillMaxWidth(), spacing = 12f) {
                val page = holoPage.value
                showcaseHolo = Holo(Size.exact(260f, 150f), open = holoOpen.value, contentKey = page, foldDuration = foldTime(),
                    background = if (holoSeeThrough.value) HoloBackground.TitleScreen else HoloBackground.Dialog) {
                    Column(Size.fillMaxSize(), Modifier.padding(8f), spacing = 4f) {
                        Text("Page ${page + 1}", font = Font.ORBITRON_12, color = BASE)
                        when (page) {
                            0 -> Text("A StarUI Box 7 px inside the frame (contentInset). Its builder is its own scope: " +
                                "count = ${holoCount.value}", Size.fillMaxWidth(), highlightedText = listOf("${holoCount.value}" to HIGHLIGHT))
                            1 -> Text("A contentKey change builds the content again and swaps it in at the peak of a " +
                                "burst of static, as vanilla's tabbed holo switches tabs.", Size.fillMaxWidth())
                            else -> Text("The background is two interlaced sets of black scanlines over the frame's " +
                                "inner edge: opaque in vanilla's dialogs, see-through on the title screen.", Size.fillMaxWidth())
                        }
                        Button("Takes input when open") { holoCount.value++ }
                    }
                }
                Column(Size.fillMaxWidth(), spacing = 3f) {
                    Text("Returns a BoxedUIHolo: open(), close(), forceOpen(), forceClose(), flickerNoise(), the " +
                        "fold (a Fader), noiseAlpha, setBackgroundAlpha, setRenderBackground.", Size.fillMaxWidth(), color = GRAY)
                    Text("The static only shows when UI noise is on in the game's settings.", Size.fillMaxWidth(), color = GRAY)
                    Text("Keep calling Holo(open = false) to close it with the animation; leaving the call out " +
                        "removes it at once.", Size.fillMaxWidth(), color = GRAY)
                }
            }
        }

        Section("Vanilla's title menu box, rebuilt") {
            Text("The title screen's menu is a holo with contentInset 3, its first button 15 px into the content: here " +
                "Holo(contentInset = 3f, background = HoloBackground.TitleScreen) with padding(top = 15f).", Size.fillMaxWidth(), color = GRAY)
            Holo(Size.exact(250f, 186f), contentInset = 3f, background = HoloBackground.TitleScreen) {
                Column(Size.fillMaxSize(), Modifier.padding(top = 15f), horizontalAlignment = Alignment.CenterHorizontally, spacing = 4f) {
                    for (label in listOf("Continue", "New Game", "Load Game", "Settings")) {
                        Button(label, Size.exact(200f, 30f), font = ButtonFont.ORBITRON_20, textAlign = TextAlign.Right,
                            cutStyle = CutStyle.TL_BR, modifier = Modifier.tooltip("Does nothing: a showcase copy")) { }
                    }
                }
            }
        }
    }
    private val holoOpen = UIState(true)
    /** The large dialog over the page (the Holo card's "Open a dialog"). */
    private val dialogOpen = UIState(false)
    /** Slow-motion folds, to watch them: 2 s instead of vanilla's 0.25 s. */
    private val slowFolds = UIState(false)
    private fun foldTime() = if (slowFolds.value) 2f else 0.25f
    private val holoPage = UIState(0)
    private val holoSeeThrough = UIState(false)
    private var showcaseHolo: BoxedUIHolo? = null
    private val holoCount = UIState(0)

    // =================================================================================================================
    // Third band, column 2: vanilla content that needs no campaign
    // =================================================================================================================
    private val cargo = Global.getFactory().createCargo(true).apply {
        addSupplies(120f); addFuel(300f); addCrew(50); addCommodity("metals", 40f); addCommodity("heavy_machinery", 15f)
    }
    private val cargoVersion = UIState(0)

    private fun UIPanelAPI.vanillaPortsColumn() {
        Section("CargoList: vanilla's cargo list") {
            Text("CargoList(cargo): each stack's icon, name and quantity, as in fleet and market tooltips; two " +
                "columns from 330 wide. The cargo is a plain CargoAPI: change it and bump a UIState to show it.",
                Size.fillMaxWidth(), color = GRAY)
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                Button("+50 supplies") { cargo.addSupplies(50f); cargoVersion.value++ }
                Button("+5 marines") { cargo.addMarines(5); cargoVersion.value++ }
                Button("+1 hand weapon") { cargo.addCommodity("hand_weapons", 1f); cargoVersion.value++ }
                Button("Empty") { cargo.clear(); cargoVersion.value++ }
            }
            cargoVersion.value   // the cards below follow the cargo
            Text("Full width (two columns):", color = GRAY)
            CargoList(cargo, Size.fillMaxWidth())
            Text("Narrow (one column), max = 3, sort = false:", color = GRAY)
            CargoList(cargo, Size.mixed(Axis.Exact(250f), Axis.WrapContent), Modifier.background(INSET_BG), max = 3, sort = false)
        }

        Section("PlanetInfo: vanilla's planet display") {
            // needs a planet of a loaded campaign: one exists on the title screen once a game was loaded this session
            val planet = runCatching {
                Global.getSector()?.starSystems?.asSequence()?.flatMap { it.planets.asSequence() }
                    ?.firstOrNull { !it.isStar && it.market != null }
            }.getOrNull()
            if (planet == null) {
                Text("PlanetInfo(planet) shows a campaign planet, lit by its star. None exists yet: load a game, " +
                    "quit to this menu and it shows here.", Size.fillMaxWidth(), color = GRAY)
            } else {
                Row(Size.fillMaxWidth(), spacing = 8f) {
                    PlanetInfo(planet, Size.exact(160f, 180f), Modifier.background(INSET_BG))
                    PlanetInfo(planet, Size.exact(160f, 180f), Modifier.background(INSET_BG), withClass = true, showConditions = true)
                    PlanetInfo(planet, Size.exact(120f, 120f), Modifier.background(INSET_BG), showName = false)
                }
                Text("showName (default) / withClass, showConditions / showName = false", color = GRAY)
            }
        }

        Section("Campaign-only ports") {
            Text("These need a running campaign (a player fleet, intel), so they are not on this page; " +
                "native-smoke/live's 'ports' check builds them in a real game.", Size.fillMaxWidth(), color = GRAY)
            for ((name, what) in listOf(
                "SkillPanel(person)" to "a person's skills by aptitude, as on the character screen",
                "EventProgressBar(intel)" to "an event's bar, stage icons and progress marker (750 wide, as vanilla's)",
                "SurveyRequirements(planet)" to "what the fleet needs to survey a planet",
                "SectorMap(params)" to "vanilla's map; .sectorMap gives a BoxedSectorMap (centerOn, zoom, pings)",
                "PlaythroughData()" to "the intel screen's playthrough record")) {
                Text("$name: $what", Size.fillMaxWidth(), highlightedText = listOf(name to HIGHLIGHT))
            }
        }
    }

    // =================================================================================================================
    // Third band, column 3: boxed vanilla objects
    // =================================================================================================================
    private val boxedSliderValue = UIState(30f)

    private fun UIPanelAPI.boxedColumn() {
        Section("Boxed vanilla objects") {
            Text("Some vanilla objects are components at run time, but their API interface does not say so " +
                "(SliderAPI, SectorMapAPI extend nothing), and images and ship displays have no API at all. StarUI " +
                "returns a box: one value that is both, so there is nothing to cast.", Size.fillMaxWidth(), color = GRAY)
            lateinit var sliderInfo: LabelAPI
            lateinit var slider: BoxedUISlider
            // both sides of one value: SliderAPI's progress and the component's position, no cast
            fun describe() { sliderInfo.text = "slider.progress = ${slider.progress.toInt()}, slider.position = ${slider.position.width.toInt()} x ${slider.position.height.toInt()}" }
            slider = Slider(boxedSliderValue, Size.fillMaxWidth(height = 24f), Modifier.onLayout { describe() }, text = "BoxedUISlider")
            sliderInfo = Text("...", color = HIGHLIGHT)
            boxedSliderValue.onChange { describe() }
            Text("slider.progress is SliderAPI; slider.position and adding it to a panel are UIComponentAPI.", Size.fillMaxWidth(), color = GRAY)
            Divider(color = DARK)
            Text("The boxes: BoxedUIImage (Image), BoxedUIShipPreview (ShipDisplay), BoxedUISlider (Slider, ProgressBar), " +
                "BoxedSectorMap (.sectorMap). Pass them to vanilla panel methods freely: StarUI unwraps them.",
                Size.fillMaxWidth(), color = GRAY)
            Text("Where the API interface already is a component (LabelAPI, ScrollPanelAPI), StarUI returns it as is and " +
                "adds the missing methods as extensions (ScrollPanelAPI.addToOverlay, LabelAPI.autoSize).",
                Size.fillMaxWidth(), color = GRAY)
        }
    }

    // =================================================================================================================
    // Full width, below the columns: ship displays at their real size
    // =================================================================================================================
    private val shipsPerScreenPixel = UIState(false)

    private fun UIPanelAPI.fullSizeShips() {
        Section("ShipDisplay: full size (exactScale)") {
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 10f) {
                Checkbox("One sprite pixel per screen pixel", bind = shipsPerScreenPixel,
                    modifier = Modifier.tooltip("exactScale = 1 / screenScaleMult instead of 1 (the same unless UI scaling is on)"))
                Text("Left of each pair: vanilla's fit (95%, 3% high). Right: exactScale, centred on whole pixels.", color = GRAY)
            }
            val scale = if (shipsPerScreenPixel.value) 1f / Global.getSettings().screenScaleMult else 1f
            // two pairs per row: four capital-sized pairs side by side are wider than a 1600 px screen
            for (variantIds in listOf(listOf("paragon_Elite", "onslaught_Standard"), listOf("conquest_Elite", "wolf_Assault")))
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.Bottom, spacing = 16f) {
                for (variantId in variantIds) {
                    val variant = Global.getSettings().getVariant(variantId) ?: continue
                    val sprite = Global.getSettings().getSprite(variant.hullSpec.spriteName)
                    // the box is the sprite plus a little room, so the exact display shows the whole hull
                    val box = Size.exact(sprite.width * scale + 20f, sprite.height * scale + 20f)
                    Column(Size.wrapContent(), horizontalAlignment = Alignment.CenterHorizontally, spacing = 4f) {
                        Row(Size.wrapContent(), spacing = 4f) {
                            ShipDisplay(box, Modifier.background(INSET_BG), variant = variant)
                            ShipDisplay(box, Modifier.background(INSET_BG), variant = variant, exactScale = scale)
                        }
                        Text("${variant.hullSpec.hullName}: ${sprite.width.toInt()} x ${sprite.height.toInt()} px", color = GRAY)
                    }
                }
            }
        }
    }

    // =================================================================================================================
    // Column 1: layout
    // =================================================================================================================
    private fun UIPanelAPI.layoutColumn() {
        Section("Box: nine alignments and margin") {
            Box(Size.fillMaxWidth(height = 92f), Modifier.background(INSET_BG)) {
                val corners = listOf(Alignment.TopLeft, Alignment.TopCenter, Alignment.TopRight, Alignment.CenterLeft, Alignment.CenterRight,
                    Alignment.BottomLeft, Alignment.BottomCenter, Alignment.BottomRight)
                for (alignment in corners) {
                    Spacer(Size.exact(14f, 14f), Modifier.align(alignment).margin(5f).background(BASE))
                }
                Text("Modifier.align(Center)", color = GRAY, modifier = Modifier.align(Alignment.Center))
            }
        }

        Section("Row: fill weights 1 : 2 : 3 and FlexSpacer") {
            Row(Size.fillMaxWidth(height = 20f), spacing = 4f) {
                Spacer(Size.fillMaxSize(1f), Modifier.background(BASE.withAlpha(70)))
                Spacer(Size.fillMaxSize(2f), Modifier.background(BASE.withAlpha(150)))
                Spacer(Size.fillMaxSize(3f), Modifier.background(BASE))
            }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Left")
                FlexSpacer()
                Text("FlexSpacer pushes this right", color = GRAY)
            }
        }

        Section("Column: grow upwards, cross alignment") {
            Row(Size.fillMaxWidth(height = 66f), spacing = 8f) {
                Column(Size.fillMaxSize(), Modifier.background(INSET_BG), grow = VGrow.Upwards,
                    horizontalAlignment = Alignment.CenterHorizontally, spacing = 2f) {
                    Text("1st (bottom)")
                    Text("2nd")
                    Text("3rd")
                }
                Column(Size.fillMaxSize(), Modifier.background(INSET_BG).padding(horizontal = 6f),
                    horizontalAlignment = Alignment.Right, spacing = 2f) {
                    Text("Right")
                    Text("aligned")
                    Text("column", color = GRAY)
                }
            }
        }

        Section("CustomPanel: anchors") {
            CustomPanel(Size.fillMaxWidth(height = 110f), Modifier.background(INSET_BG)) {
                val topLeft = Text("inside.topLeft", modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(6f, 6f)))
                Text("below.sibling(...)", color = GRAY,
                    modifier = Modifier.anchor(Anchor.below.sibling(topLeft).matchingLeftEdge(4f)))
                Text("rightOf.previousComponent", color = HIGHLIGHT,
                    modifier = Modifier.anchor(Anchor.rightOf.previousComponent.matchingTopEdge(10f)))
                Text("inside.center", modifier = Modifier.anchor(Anchor.inside.center.ofParent(0f, -10f)))
                Text("inside.bottomRight", modifier = Modifier.anchor(Anchor.inside.bottomRight.ofParent(6f, 6f)))
                Spacer(Size.exact(12f, 12f),
                    Modifier.anchor(Anchor.my.bottomLeft.toBottomLeft.ofParent(6f, 6f)).background(BRIGHT))
            }
        }

        Section("Margin (outside) and padding (inside)") {
            Row(Size.fillMaxWidth(), Modifier.background(INSET_BG), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Size.exact(30f, 16f), Modifier.margin(10f).background(BASE))
                Text("margin(10) around the block", color = GRAY)
            }
            Box(Size.wrapContent(), Modifier.background(INSET_BG).padding(12f)) {
                Text("A Box with padding(12)")
            }
        }
    }

    // =================================================================================================================
    // Column 2: reactive state
    // =================================================================================================================
    private fun UIPanelAPI.stateColumn() {
        val filterGroup = ButtonGroup()
        Section("Lists: UIState, for and key()") {
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 6f) {
                TextField(newTask, Size.fillMaxWidth(height = 26f), placeholder = "New task, then Add")
                Button("Add") {
                    val name = newTask.value.trim()
                    if (name.isNotEmpty()) {
                        tasks.value = tasks.value + Task(nextTaskId++, name)
                        newTask.value = ""
                    }
                }
            }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                AreaCheckbox("Open", showOpen, Size.exact(70f, 22f), buttonGroup = filterGroup,
                    modifier = Modifier.tooltip("ButtonGroup: click to show only this, click again for all, Shift-click to add"))
                AreaCheckbox("Done", showDone, Size.exact(70f, 22f), buttonGroup = filterGroup)
                FlexSpacer()
                val doneCount = tasks.value.count { it.done.value }
                val openCount = tasks.value.size - doneCount
                Text("$openCount open, $doneCount done", color = GRAY,
                    highlightedText = listOf("$openCount" to HIGHLIGHT, "$doneCount" to POSITIVE))
            }
            Column(Size.fillMaxWidth(height = 190f), Modifier.padding(4f), spacing = 2f, overflow = Overflow.ScrollVertical) {
                val visible = tasks.value.filter { (showOpen.value && !it.done.value) || (showDone.value && it.done.value) }
                if (visible.isEmpty()) Text("Nothing to show. Add a task or change the filter.", color = GRAY)
                for (task in visible) key(task.id) {
                    Row(Size.fillMaxWidth(height = 26f), Modifier.background(INSET_BG).padding(horizontal = 4f),
                        verticalAlignment = Alignment.CenterVertically, spacing = 6f) {
                        Checkbox(bind = task.done)
                        Text(task.name, color = if (task.done.value) GRAY else TEXT)
                        FlexSpacer()
                        Button("Remove", contentPadding = PaddingValues.symmetric(horizontal = 6f, vertical = 2f)) { tasks.value = tasks.value - task }
                    }
                }
            }
        }

        Section("Conditional UI: if") {
            Checkbox("Show details", bind = details)
            if (details.value) {
                Text("A plain if inside a builder: this paragraph exists only while the box is ticked, and the " +
                    "card around it re-sizes on the next frame.", color = GRAY)
            }
        }

        Section("onChange and peek") {
            // onChange inside a builder is removed automatically when this card builds again
            tasks.onChange { lastChange.value = "tasks now ${it.size}" }
            showOpen.onChange { lastChange.value = "Open filter ${if (it) "on" else "off"}" }
            Text("Last change: ${lastChange.value}", highlightedText = listOf(lastChange.value to HIGHLIGHT))
            Text("peek() does not subscribe: ${tasks.peek().size} tasks when this card was last built", color = GRAY)
        }

        Section("Slider and ProgressBar") {
            // Dragging writes `volume`; the bar and the text read it, so this card builds again and both follow.
            Slider(volume, Size.fillMaxWidth(height = 25f), valueRange = 0f..100f, text = "Volume",
                barColor = BASE, widgetColor = BRIGHT, step = 5f)
            ProgressBar(volume.value, Size.fillMaxWidth(height = 10f), barColor = DARK.brighter())
            Text("step = 5: the state snaps to ${volume.value.toInt()}", color = GRAY,
                highlightedText = listOf("${volume.value.toInt()}" to HIGHLIGHT))
        }
    }

    // =================================================================================================================
    // Column 3: content, events, theme, lifecycle
    // =================================================================================================================
    private fun UIPanelAPI.contentColumn() {
        Section("Text") {
            Text("Orbitron 20", font = Font.ORBITRON_20_AA, color = BASE)
            Text("Insignia 21", font = Font.INSIGNIA_21)
            Text("Highlights: 1200 credits, 300 supplies",
                highlightedText = listOf("1200" to HIGHLIGHT, "300" to POSITIVE))
            Text("Size.fillMaxWidth() and TextAlign.Center", Size.fillMaxWidth(), color = GRAY, textAlign = TextAlign.Center)
            SectionHeading("SectionHeading, textAlign = Left", textAlign = TextAlign.Left)
        }

        Section("Image and ShipDisplay") {
            // a sprite the game has not loaded yet draws as nothing
            val icons = listOf("graphics/icons/cargo/supplies.png", "graphics/icons/cargo/fuel.png", "graphics/icons/cargo/crew.png")
            for (icon in icons) if (Global.getSettings().getSprite(icon).textureId == 0) runCatching { Global.getSettings().loadTexture(icon) }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 10f) {
                Image("graphics/icons/cargo/supplies.png", Size.exact(40f, 40f), Modifier.tooltip("Image"))
                Image("graphics/icons/cargo/fuel.png", Size.exact(40f, 40f))
                Image("graphics/icons/cargo/crew.png", Size.exact(40f, 40f), showBorder = true, borderColor = BASE)
                FlexSpacer()
                ShipDisplay(Size.exact(90f, 90f), variant = Global.getSettings().getVariant("hammerhead_Balanced"))
                ShipDisplay(Size.exact(90f, 90f), variant = Global.getSettings().getVariant("eagle_Assault"),
                    showBorder = true, modifier = Modifier.tooltip("showBorder = true"))
            }
        }

        Section("Events") {
            lateinit var padLabel: LabelAPI
            var hovered = false
            var clicks = 0
            var held = 0f
            CustomPanel(
                Size.fillMaxWidth(height = 64f),
                Modifier
                    .onRenderBelow { alphaMult ->
                        glColor(if (hovered) DARK else INSET_BG, alphaMult, false)
                        GL11.glRectf(left, bottom, right, top)
                        // hold progress along the bottom (onMouseHeld)
                        glColor(BRIGHT, alphaMult, false)
                        GL11.glRectf(left, bottom, left + (right - left) * held, bottom + 3f)
                        GL11.glEnable(GL11.GL_TEXTURE_2D)
                    }
                    .onHoverEnter { hovered = true }
                    .onHoverExit { hovered = false }
                    .onClick { clicks++; padLabel.text = "onClick x $clicks" }
                    .onMouseHeld { held = minOf(1f, held + 0.02f) }
                    .onClickRelease { padLabel.text = "onClickRelease (held ${(held * 100).toInt()}%)" }
                    .onClickOutside { padLabel.text = "onClickOutside" }
                    .onKeyDown { event -> padLabel.text = "onKeyDown: ${Keyboard.getKeyName(event.eventValue)}" }
                    .onAdvance { amount -> held = maxOf(0f, held - amount * 0.5f) }
                    .tooltip("Hover, click, hold, click elsewhere, or press a key")
            ) {
                padLabel = Text("Hover, click, hold, click outside, press a key",
                    modifier = Modifier.anchor(Anchor.inside.center.ofParent()))
            }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 8f) {
                lateinit var hitLabel: LabelAPI
                Button("Tiny", modifier = Modifier.hitBoxPadding(10f), contentPadding = PaddingValues.symmetric(horizontal = 3f)) { hitLabel.text = "Hit, with 10 px of hitBoxPadding" }
                hitLabel = Text("hitBoxPadding(10): click near the tiny button", color = GRAY)
            }
        }

        Section("Theme") {
            Checkbox("Story point theme", bind = altTheme)
            Theme(if (altTheme.value) altTheme() else StarUITheme.Default) {
                Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 6f) {
                    Button("Themed")
                    Button("Buttons")
                    AreaCheckbox("and toggles")
                }
            }
        }

        Section("Lifecycle and tooling") {
            lateinit var lifecycle: LabelAPI
            var layoutPasses = 0
            var firstLayouts = 0
            var moves = 0
            var sinceUpdate = 0f
            Row(
                Size.fillMaxWidth(),
                Modifier.onLayout { layoutPasses++ }
                    .onFirstLayout { firstLayouts++ }
                    .onPositionChanged { moves++ }
                    .onAdvance { amount ->
                        // not from onLayout: a text change there would ask for another layout every pass
                        sinceUpdate += amount
                        if (sinceUpdate >= 0.5f) {
                            sinceUpdate = 0f
                            lifecycle.text = "onLayout x $layoutPasses, onFirstLayout x $firstLayouts, moved x $moves"
                        }
                    },
                verticalAlignment = Alignment.CenterVertically, spacing = 8f
            ) {
                lifecycle = Text("onLayout, onFirstLayout, onPositionChanged", color = GRAY)
            }
            Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 8f) {
                lateinit var dumpLabel: LabelAPI
                Button("dumpLayout() to log") {
                    // the page: the outermost StarUI container above this row
                    val page = generateSequence(dumpLabel.parent) { it.parent }
                        .lastOrNull { StarUIPanelPlugin.getAttached(it) != null } ?: return@Button
                    val dump = page.dumpLayout()
                    log.info("StarUI showcase layout:\n$dump")
                    dumpLabel.text = "Wrote ${dump.lines().size} lines to starsector.log"
                }
                dumpLabel = Text("The whole tree as text", color = GRAY)
            }
        }
    }
}
