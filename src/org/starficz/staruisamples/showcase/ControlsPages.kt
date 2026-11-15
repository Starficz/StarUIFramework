package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CutStyle
import org.lwjgl.input.Keyboard
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.ButtonFont
import org.starficz.starui.vanilla.Font
import org.starficz.starui.vanilla.VanillaColors

private const val FILE = "ControlsPages.kt"

// snippet: button-clicks
private val clicks = UIState(0)
// end snippet
// snippet: button-enabled
private val armed = UIState(false)
// end snippet
// snippet: checkbox-bind
private val autoRepair = UIState(true)
// end snippet
// snippet: radio
private val doctrines = listOf("Aggressive", "Steady", "Cautious").map { it to UIState(it == "Steady") }
// end snippet
// snippet: filter
private val hullSizes = listOf("Frigate", "Destroyer", "Cruiser", "Capital").map { it to UIState(true) }
// end snippet
// snippet: field-bind
private val fleetName = UIState("")
// end snippet
// snippet: field-filter
private val search = UIState("")
private val systems = listOf("Askonia", "Corvus", "Galatia", "Hybrasil", "Magec", "Samarra", "Thule", "Valhalla")
// end snippet
// snippet: slider-bind
private val crew = UIState(250f)
// end snippet
// snippet: slider-percent
private val burn = UIState(60f)
// end snippet
// snippet: bar-target
private val target = UIState(20f)
// end snippet

private val buttonsPage = Page("Controls", "Buttons",
    "The game's own buttons: cut corners, the seven button fonts, colours, sizes, a click and a key.",
    FILE) {

    card("Cut styles", "button-cuts", "Which corners are cut, as on vanilla's buttons. TL_BR is the default.") {
        // snippet: button-cuts
        Row(spacing = 6f) {
            for (cut in CutStyle.entries) Button(cut.name, cutStyle = cut) { }
        }
        // end snippet
    }

    card("Fonts", "button-fonts", "A button is made with one of these seven fonts (ButtonFont); any other is a compile error.") {
        // snippet: button-fonts
        Column(spacing = 6f) {
            for (font in ButtonFont.entries) Button(font.name, font = font) { }
        }
        // end snippet
    }

    card("Colours", "button-colours", "color is the text and frame, backgroundColor the fill. Leave them out for the faction's.") {
        // snippet: button-colours
        Row(spacing = 6f) {
            Button("Faction") { }
            Button("Story point", color = VanillaColors.story, backgroundColor = VanillaColors.storyDark) { }
            Button("Scuttle", color = VanillaColors.negative, backgroundColor = VanillaColors.negative.darker().darker()) { }
        }
        // end snippet
    }

    card("Sizes and text alignment", "button-sizes") {
        // snippet: button-sizes
        Column(Size.fillMaxWidth(), spacing = 6f) {
            Button("Wraps its text") { }
            Button("Exact 200 x 30", Size.exact(200f, 30f)) { }
            Button("Text on the left", Size.exact(200f, 30f), textAlign = TextAlign.Left) { }
            Button("Fills the width", Size.fillMaxWidth(height = 24f)) { }
        }
        // end snippet
    }

    card("A click and a key", "button-clicks",
        "The handler changes a state; the Text that reads it follows. The shortcut presses the button from the keyboard.") {
        // snippet: button-clicks
        Row(spacing = 10f, verticalAlignment = Alignment.CenterVertically) {
            Button("Count", shortcut = Keyboard.KEY_C) { clicks.value++ }
            Text("Clicked ${clicks.value} times")
        }
        // end snippet
    }

    card("Enabled, and a tooltip", "button-enabled",
        "A disabled button greys out and takes no clicks; its tooltip still shows.") {
        // snippet: button-enabled
        Row(spacing = 10f, verticalAlignment = Alignment.CenterVertically) {
            Checkbox("Armed", bind = armed)
            Button("Launch", modifier = Modifier.tooltip("Arm it first")) { }.isEnabled = armed.value
        }
        // end snippet
    }
}

private val checkboxes = Page("Controls", "Checkboxes",
    "A tick box with a label, and vanilla's area checkbox: a button that stays pressed. Both bind to a UIState<Boolean>.",
    FILE) {

    card("Bound to a state", "checkbox-bind", "Clicking writes the state; anything that reads it follows.") {
        // snippet: checkbox-bind
        Checkbox("Repair after battle", bind = autoRepair)
        Text(if (autoRepair.value) "Repairs: on" else "Repairs: off", color = VanillaColors.gray)
        // end snippet
    }

    card("Box sizes, fonts and colours", "checkbox-sizes") {
        // snippet: checkbox-sizes
        Row(spacing = 12f, verticalAlignment = Alignment.CenterVertically) {
            Checkbox("Tiny", checkboxSize = ButtonAPI.UICheckboxSize.TINY)
            Checkbox("Small", checkboxSize = ButtonAPI.UICheckboxSize.SMALL)
            Checkbox("Large, Orbitron", checkboxSize = ButtonAPI.UICheckboxSize.LARGE, font = Font.ORBITRON_20_AA,
                color = VanillaColors.highlight)
        }
        // end snippet
    }

    card("Area checkboxes", "area", "A toggle that looks like a button: the entries of vanilla's pickers, and this nav.") {
        // snippet: area
        Row(spacing = 4f) {
            AreaCheckbox("Wraps its text")
            AreaCheckbox("Left aligned", size = Size.exact(140f, 24f), leftAlign = true)
            AreaCheckbox("Own colours", baseColor = VanillaColors.positive,
                darkColor = VanillaColors.positive.darker().darker(), brightColor = VanillaColors.positive.brighter())
        }
        // end snippet
    }
}

private val groups = Page("Controls", "Button groups",
    "Checkboxes and area checkboxes that act together, each bound to its own state.", FILE) {

    card("One of them: RadioGroup", "radio",
        "Exactly one is on: a click selects it and turns the others off, a second click keeps it.") {
        // snippet: radio
        val group = RadioGroup()
        Row(spacing = 4f) {
            for ((name, state) in doctrines) AreaCheckbox(name, state, Size.exact(100f, 24f), buttonGroup = group)
        }
        Text("Doctrine: ${doctrines.first { it.second.value }.first}", color = VanillaColors.gray)
        // end snippet
    }

    card("Any of them: FilterGroup", "filter",
        "Vanilla's filter bar: a click shows only that one, Shift + click adds or removes one, clicking the only one shows all.") {
        // snippet: filter
        val group = FilterGroup()
        Row(spacing = 4f) {
            for ((name, state) in hullSizes) AreaCheckbox(name, state, Size.exact(90f, 24f), buttonGroup = group)
        }
        val shown = hullSizes.filter { it.second.value }.joinToString { it.first }
        Text("Shown: $shown", color = VanillaColors.gray)
        // end snippet
    }
}

private val fields = Page("Controls", "Text fields",
    "Vanilla's text field, bound to a UIState<String>: typing writes it, writing it changes the field.", FILE) {

    card("Bound text", "field-bind", "Two fields on one state: type in either and the other follows.") {
        // snippet: field-bind
        TextField(fleetName, Size.fillMaxWidth(height = 26f), placeholder = "Fleet name")
        TextField(fleetName, Size.fillMaxWidth(height = 30f), font = Font.ORBITRON_20_AA, placeholder = "The same, in Orbitron")
        Row(spacing = 6f, verticalAlignment = Alignment.CenterVertically) {
            Text("${fleetName.value.length} characters", color = VanillaColors.gray)
            Button("Clear") { fleetName.value = "" }
        }
        // end snippet
    }

    card("Filtering a list", "field-filter", "The list reads the state, so it filters as you type.") {
        // snippet: field-filter
        TextField(search, Size.fillMaxWidth(height = 26f), placeholder = "Search systems")
        val matches = systems.filter { it.contains(search.value, ignoreCase = true) }
        Column(spacing = 2f) {
            for (system in matches) Text(system)
            if (matches.isEmpty()) Text("No system matches", color = VanillaColors.gray)
        }
        // end snippet
    }
}

private val sliders = Page("Controls", "Sliders and bars",
    "Vanilla's slider, bound to a UIState<Float>, and the same bar as a progress bar that shows a value.", FILE) {

    card("A slider", "slider-bind", "valueRange and step; the state snaps to the step.") {
        // snippet: slider-bind
        Slider(crew, Size.fillMaxWidth(height = 25f), valueRange = 0f..1000f, text = "Crew", step = 50f)
        Text("Crew: ${crew.value.toInt()}", color = VanillaColors.gray)
        // end snippet
    }

    card("Vanilla's options", "slider-percent", "configure takes any SliderAPI setter, here a percentage label.") {
        // snippet: slider-percent
        Slider(burn, Size.fillMaxWidth(height = 25f), text = "Burn", barColor = VanillaColors.positive.darker(),
            configure = { setShowPercent(true) })
        // end snippet
    }

    card("Progress bars", "bar-target", "A value, not a state: it eases to a new one unless animate = false.") {
        // snippet: bar-target
        Row(spacing = 4f) {
            Button("To 10") { target.value = 10f }
            Button("To 90") { target.value = 90f }
        }
        ProgressBar(target.value, Size.fillMaxWidth(height = 18f), text = "Eases")
        ProgressBar(target.value, Size.fillMaxWidth(height = 18f), text = "Jumps", animate = false,
            barColor = VanillaColors.highlight.darker())
        ProgressBar(target.value, Size.fillMaxWidth(height = 18f), text = "Ten steps", configure = { setNumSubivisions(10) })
        // end snippet
    }
}

internal val controlsPages = listOf(buttonsPage, checkboxes, groups, fields, sliders)
