package org.starficz.staruisamples

import com.fs.starfarer.api.Global
import org.starficz.starui.compose.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import org.lazywizard.lazylib.opengl.ColorUtils.glColor
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.starficz.starui.compose.*
import org.starficz.starui.compose.components.*
import org.starficz.starui.compose.VGrow
import org.starficz.starui.compose.modifier.*
import org.starficz.starui.compose.runtime.*
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.ButtonFont
import org.starficz.starui.core.Font
import org.starficz.starui.core.VanillaColors
import org.starficz.starui.core.VanillaColors.withAlpha
import org.starficz.starui.core.bottom
import org.starficz.starui.core.left
import org.starficz.starui.core.right
import org.starficz.starui.core.top
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
    private val open = UIState(true)
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
            if (open.value) page()
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

            // ---------------------------------------- body: three columns ----------------------------------------
            Row(Size.fillMaxSize(), spacing = 14f) {
                Column(Size.fillMaxSize(), spacing = 10f) { layoutColumn() }
                Column(Size.fillMaxSize(), spacing = 10f) { stateColumn() }
                Column(Size.fillMaxSize(), spacing = 10f) { contentColumn() }
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
            ScrollPanel(Size.fillMaxWidth(height = 190f), Modifier.padding(4f), spacing = 2f) {
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
                    isSchematicMode = true, modifier = Modifier.tooltip("isSchematicMode = true"))
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
