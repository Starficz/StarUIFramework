package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.Font
import org.starficz.starui.vanilla.VanillaColors
import org.starficz.starui.vanilla.measureTextSize
import org.starficz.starui.vanilla.withAlpha
import org.starficz.starui.window.StarUILayer
import org.starficz.starui.window.showLayer
import org.starficz.staruisamples.ExampleUIPlugin
import java.awt.Color

/**
 * Everything StarUI does, a page per feature, each demo live with the code that makes it beside it (read from this
 * folder's sources: [Snippets]). For modders learning StarUI, for checking features in game, screenshots and the agent
 * skill's examples. StarUI's own ModPlugin registers [Plugin]; Ctrl + Shift + U opens it.
 */
object Showcase {
    /**
     * Whether [event] is the showcase's shortcut, Ctrl + Shift + U, which opens and closes it on the title screen, in
     * the campaign and in combat. A chord, so a stray key press never opens it.
     */
    private fun isShortcut(event: InputEventAPI) =
        !event.isConsumed && event.eventValue == Keyboard.KEY_U && event.isCtrlDown && event.isShiftDown

    /**
     * Listens for the showcase's shortcut on the title screen, the campaign screen (for the pages whose parts need a
     * campaign) and in combat: a full-screen panel that draws nothing.
     */
    class Plugin : StarUIPlugin {
        private val shortcut: StarUIBuilder = {
            Box(Size.fillMaxSize(), Modifier.onKeyDown { event ->
                if (isShortcut(event)) { event.consume(); open() }
            })
        }
        override val addPanelToTitleScreen = shortcut
        override val addPanelToCampaignUI = shortcut
        override val addPanelToCombatScreen = shortcut
    }

    internal val pages: List<Page> = layoutPages + contentPages + controlsPages + listPages + statePages +
        inputPages + windowPages + vanillaPages + hostPages + recipePages

    /** Draws every component's bounds in the demos. */
    internal val outlines = UIState(false)
    private var layer: StarUILayer? = null

    /** Opens the showcase on a layer over whatever screen is showing (from a click or a script, not a builder). */
    fun open() {
        if (layer?.isOpen == true) return
        if (pages.none { it.selected.peek() }) pages.first().selected.value = true
        layer = showLayer(dim = 0.8f) { layer -> screen(layer) }
    }

    private fun CustomPanelAPI.screen(layer: StarUILayer) {
        // the shortcut closes it too: an open modal layer takes every key, so the screens' listeners do not see it
        val closeOnShortcut = Modifier.onKeyDown { event ->
            if (isShortcut(event)) { event.consume(); layer.close() }
        }
        Row(Size.fillMaxSize(), closeOnShortcut.margin(24f).background(Color.BLACK).border().padding(14f), spacing = 16f) {
            nav(layer)
            Column(Size.fillMaxSize()) { page() }
        }
    }

    private fun UIPanelAPI.nav(layer: StarUILayer) {
        Column(Size.fillMaxHeight(width = 190f), spacing = 2f) {
            Text("StarUI", font = Font.ORBITRON_24_BOLD, color = VanillaColors.base)
            Text("showcase, 1.0.0-alpha", color = VanillaColors.gray)
            Text("Ctrl + Shift + U", color = VanillaColors.gray)
            // the pages scroll when the screen is too short for them all; the toggles stay at the bottom
            Column(Size.fillMaxSize(), Modifier.margin(top = 4f), overflow = Overflow.ScrollVertical) {
                Column(Size.fillMaxWidth(), Modifier.padding(right = 12f), spacing = 1f) {
                    val group = RadioGroup()
                    for ((name, inGroup) in pages.groupBy { it.group }) {
                        Text(name.uppercase(), font = Font.ORBITRON_12, color = VanillaColors.gray,
                            modifier = Modifier.margin(top = 8f, bottom = 1f))
                        for (page in inGroup) {
                            AreaCheckbox(page.title, page.selected, Size.fillMaxWidth(height = 22f), leftAlign = true, buttonGroup = group)
                        }
                    }
                }
            }
            Checkbox("Outlines", bind = outlines, modifier = Modifier.tooltip("Draws the bounds of every component in the demos"))
            Button("Close", Size.fillMaxWidth(height = 24f), modifier = Modifier.margin(top = 6f)) { layer.close() }
        }
    }

    private fun UIPanelAPI.page() {
        val page = pages.firstOrNull { it.selected.value } ?: pages.first()
        // keyed by page: another page is new UI (its scroll starts at the top), not this one's components reused
        key(page.title) {
            Column(Size.fillMaxSize(), spacing = 6f) {
                Text(page.title, font = Font.INSIGNIA_25, color = VanillaColors.highlight)
                Text(page.blurb, Size.fillMaxWidth(), color = VanillaColors.gray)
                Divider(color = VanillaColors.dark, modifier = Modifier.margin(vertical = 4f))
                Column(Size.fillMaxSize(), overflow = Overflow.ScrollVertical) {
                    Column(Size.fillMaxWidth(), Modifier.padding(right = 14f), spacing = 12f) {
                        page.body(Cards(this, page.file))
                    }
                }
            }
        }
    }
}

/** The fill the demos put behind an area to show its bounds: the faction's dark colour, partly transparent. */
internal val INSET: Color get() = VanillaColors.dark.withAlpha(150)

/** A block inside a demo area ([INSET]): a card, a cell, a marker. Brighter than the area, so it stands apart. */
internal val TILE: Color get() = VanillaColors.dark.brighter()

/** One page of the showcase: a nav entry, and its cards, whose snippets are in [file] (in this folder). */
internal class Page(val group: String, val title: String, val blurb: String, val file: String, val body: Cards.() -> Unit) {
    val selected = UIState(false)
}

/** Where a page's cards go. */
internal class Cards(private val panel: UIPanelAPI, private val file: String) {
    /**
     * A card: [title], an optional [note] under it, the live [demo] on the left and the code of [snippet] (the lines
     * between its markers in the page's file) on the right; [wide] puts the demo above the code, for a demo that
     * needs the width (a table, a list). No demo: the code alone (a host that cannot run here).
     */
    fun card(title: String, snippet: String, note: String? = null, wide: Boolean = false,
             demo: (CustomPanelAPI.() -> Unit)? = null) {
        panel.Section(title, Size.fillMaxWidth(), spacing = 8f) {
            if (note != null) Text(note, Size.fillMaxWidth(), color = VanillaColors.gray)
            if (wide) {
                if (demo != null) demoColumn(demo)
                code(snippet)
            } else Row(Size.fillMaxWidth(), spacing = 12f) {
                if (demo != null) demoColumn(demo)
                code(snippet)
            }
        }
    }

    /** Text between cards: what a group of them has in common. */
    fun note(text: String) {
        panel.Text(text, Size.fillMaxWidth(), color = VanillaColors.gray)
    }

    private fun UIPanelAPI.demoColumn(demo: CustomPanelAPI.() -> Unit) {
        Column(Size.mixed(Axis.FillMax(1f), Axis.WrapContent),
            Modifier.onRender { if (Showcase.outlines.peek()) ExampleUIPlugin.drawDebugBorders(panel) },
            spacing = 6f) { demo() }
    }

    private fun UIPanelAPI.code(id: String) {
        // no wheel: it would scroll the code sideways while the reader scrolls the page (the scrollbar and a right
        // drag still move it)
        val scroll = StarUITheme.current.scroll.copy(useMouseWheel = false)
        Theme(StarUITheme.current.copy(scroll = scroll)) { codePane(id) }
    }

    private fun UIPanelAPI.codePane(id: String) {
        Box(Size.mixed(Axis.FillMax(1.3f), Axis.WrapContent),
            Modifier.background(CODE_BACKGROUND).border(VanillaColors.dark.withAlpha(160)).padding(8f),
            overflow = Overflow.ScrollHorizontal) {
            when (val found = Snippets.lookup(file, id)) {
                is Snippets.Lookup.Found -> {
                    // as wide as its longest line (and a little: vanilla wraps a few pixels early), so it never wraps: the
                    // pane scrolls sideways instead
                    val width = found.snippet.text.lines().maxOf { measureTextSize(it, CODE_FONT).first } + 16f
                    Text(found.snippet.text, Size.mixed(Axis.Exact(width), Axis.WrapContent), font = CODE_FONT,
                        color = Snippets.PLAIN, highlightedText = found.snippet.highlights)
                }
                Snippets.Lookup.NoSource -> Text("The code is read from src/: this build has none.", color = VanillaColors.gray)
                Snippets.Lookup.Missing -> Text("No snippet \"$id\" in $file", color = VanillaColors.negative)
            }
        }
    }

    private companion object {
        val CODE_BACKGROUND = Color(6, 9, 14)
        val CODE_FONT = Font.INSIGNIA_15
    }
}
