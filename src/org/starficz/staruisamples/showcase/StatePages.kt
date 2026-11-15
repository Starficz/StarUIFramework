package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.ui.CutStyle
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.ButtonFont
import org.starficz.starui.vanilla.VanillaColors

private const val FILE = "StatePages.kt"

// snippet: rebuilds
private val left = UIState(0)
private val right = UIState(0)
private var leftBuilds = 0
private var rightBuilds = 0
// end snippet
// snippet: if
private val details = UIState(false)
// end snippet
// snippet: delegate
private var fuel by mutableStateOf(300)
// end snippet
// snippet: peek
private val supplies = UIState(100)
private val lastChange = UIState("nothing yet")
// end snippet
// snippet: keys
private val order = UIState(listOf("Eagle", "Falcon", "Hammerhead", "Wolf"))
// end snippet
// snippet: theme
private val storyTheme = StarUITheme.Default.copy(
    button = StarUITheme.Default.button.copy(color = VanillaColors.story, backgroundColor = VanillaColors.storyDark),
    areaCheckbox = StarUITheme.Default.areaCheckbox.copy(baseColor = VanillaColors.story,
        darkColor = VanillaColors.storyDark, brightColor = VanillaColors.storyBright))
private val themed = UIState(true)
// end snippet

private val rebuilds = Page("State", "State and rebuilds",
    "A UIState read in a builder makes that builder run again when it changes, on the next frame. Handlers write " +
        "states; they never build UI.", FILE) {

    card("Only the reader runs again", "rebuilds",
        "Each column reads one counter; a click rebuilds only the column that reads it.") {
        // snippet: rebuilds
        Row(spacing = 6f) {
            Button("Left + 1") { left.value++ }
            Button("Right + 1") { right.value++ }
        }
        Row(Size.fillMaxWidth(), spacing = 6f) {
            Column(Size.fillMaxWidth(), Modifier.background(INSET).padding(6f)) {
                leftBuilds++
                Text("left = ${left.value}")
                Text("built $leftBuilds times", color = VanillaColors.gray)
            }
            Column(Size.fillMaxWidth(), Modifier.background(INSET).padding(6f)) {
                rightBuilds++
                Text("right = ${right.value}")
                Text("built $rightBuilds times", color = VanillaColors.gray)
            }
        }
        // end snippet
    }

    card("Plain if and for", "if", "Ordinary Kotlin in a builder: the text exists only while the box is ticked.") {
        // snippet: if
        Checkbox("Show details", bind = details)
        if (details.value) {
            for (line in listOf("Hull 8000", "Armor 1500", "Flux 12000")) Text(line, color = VanillaColors.gray)
        }
        // end snippet
    }

    card("A delegated property, and values worked out in the builder", "delegate",
        "var x by mutableStateOf(...) reads and writes the state's value. Anything worked out from it follows too.") {
        // snippet: delegate
        Row(spacing = 6f) {
            Button("Burn 50") { fuel = maxOf(0, fuel - 50) }
            Button("Refuel") { fuel = 300 }
        }
        val jumps = fuel / 40
        Text("$fuel fuel: $jumps jumps", highlightedText = listOf("$jumps" to if (jumps < 3) VanillaColors.negative else VanillaColors.highlight))
        // end snippet
    }

    card("peek and onChange", "peek",
        "peek() reads without following; onChange in a builder runs on every change and goes when the builder runs again.") {
        // snippet: peek
        supplies.onChange { lastChange.value = "supplies now $it" }
        Button("Use 10 supplies") { supplies.value -= 10 }
        Text("Last change: ${lastChange.value}")
        Text("peek(): ${supplies.peek()} when this was last built", color = VanillaColors.gray)
        // end snippet
    }
}

private val keys = Page("State", "Keys",
    "A rebuild reuses components by their place among the calls, or by key(...) when it is given. Keys matter when " +
        "a list's items move.", FILE) {

    card("With and without keys", "keys",
        "Tick a box in each list, then rotate: with keys the tick moves with its ship; without, it stays in its place.") {
        // snippet: keys
        Button("Rotate") { order.value = order.value.drop(1) + order.value.first() }
        Row(Size.fillMaxWidth(), spacing = 6f) {
            Column(Size.fillMaxWidth(), Modifier.background(INSET).padding(4f), spacing = 2f) {
                Text("key(name)", color = VanillaColors.gray)
                for (name in order.value) key(name) {
                    Checkbox(name)
                }
            }
            Column(Size.fillMaxWidth(), Modifier.background(INSET).padding(4f), spacing = 2f) {
                Text("no key", color = VanillaColors.gray)
                for (name in order.value) {
                    Checkbox(name)
                }
            }
        }
        // end snippet
    }
}

private val themes = Page("State", "Themes",
    "A theme sets the defaults of every component under it: what a call gets for each argument it leaves out.", FILE) {

    card("A subtree in another theme", "theme",
        "Here vanilla's story point colours on the buttons and toggles. Arguments a call gives still win.") {
        // snippet: theme
        Checkbox("Story point theme", bind = themed)
        Theme(if (themed.value) storyTheme else StarUITheme.Default) {
            Row(spacing = 6f) {
                Button("Themed") { }
                AreaCheckbox("Toggle")
                Button("Its own colour", color = VanillaColors.base, backgroundColor = VanillaColors.dark) { }
            }
        }
        // end snippet
    }

    card("One kind of component", "theme-one", "Copy the current theme and change one part: here every button.") {
        // snippet: theme-one
        val current = StarUITheme.current
        Theme(current.copy(button = current.button.copy(font = ButtonFont.ORBITRON_20, cutStyle = CutStyle.ALL))) {
            Row(spacing = 6f) {
                Button("Larger") { }
                Button("Buttons") { }
            }
        }
        // end snippet
    }
}

internal val statePages = listOf(rebuilds, keys, themes)
