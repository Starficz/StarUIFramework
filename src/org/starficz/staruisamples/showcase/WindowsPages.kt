package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.impl.codex.CodexDataV2
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.Font
import org.starficz.starui.vanilla.HoloBackground
import org.starficz.starui.vanilla.VanillaColors
import org.starficz.starui.window.*

private const val FILE = "WindowsPages.kt"

// snippet: toast-handle
private var held: StarUIToast? = null
// end snippet
// snippet: layer-state
private val pickerOpen = UIState(false)
// end snippet
// snippet: dialog-state
private val renameOpen = UIState(false)
private val shipName = UIState("ISS Black Wind")
// end snippet
// snippet: dialog-pages
private val wizardOpen = UIState(false)
private val wizardPage = UIState(0)
// end snippet

private val layers = Page("Windows", "Layers",
    "A full-screen panel over whatever screen is showing (title, campaign, its dialogs, combat). Modal ones dim the " +
        "screen, take every key and click and pause the game; Escape closes them.", FILE) {

    card("From a click: showLayer", "layer-show",
        "Open from a handler or a script; it stays until close() or Escape. Its content is a full-screen Box.") {
        // snippet: layer-show
        Button("Open a layer") {
            showLayer { layer ->
                Column(Size.wrapContent(), Modifier.align(Alignment.Center).background(VanillaColors.panel).border().padding(16f),
                    spacing = 8f) {
                    Text("A modal layer", font = Font.INSIGNIA_21, color = VanillaColors.base)
                    Text("Escape or the button closes it.", color = VanillaColors.gray)
                    Button("Close") { layer.close() }
                }
            }
        }
        // end snippet
    }

    card("From a state: Layer", "layer-state",
        "In a builder: shown while the builder calls it, so a state says whether it is open (Compose's Dialog).") {
        // snippet: layer-state
        Button("Pick a ship") { pickerOpen.value = true }
        if (pickerOpen.value) Layer(onDismissRequest = { pickerOpen.value = false }) {
            Column(Size.wrapContent(), Modifier.align(Alignment.Center).background(VanillaColors.panel).border().padding(16f),
                spacing = 4f) {
                Text("Pick a ship", font = Font.INSIGNIA_21, color = VanillaColors.base)
                for (hull in listOf("Onslaught", "Eagle", "Wolf")) {
                    Button(hull, Size.exact(200f, 24f)) { showToast("Picked the $hull"); pickerOpen.value = false }
                }
            }
        }
        // end snippet
    }

    card("Not modal", "layer-free",
        "modal = false: no dim, no pause, keys pass through; only clicks on its own content are taken.") {
        // snippet: layer-free
        Button("Open a panel in the corner") {
            showLayer(modal = false) { layer ->
                Column(Size.exact(240f, 90f), Modifier.align(Alignment.TopRight).margin(40f)
                    .background(VanillaColors.panel).border().padding(10f), spacing = 6f) {
                    Text("The showcase still works under this.", Size.fillMaxWidth())
                    Button("Close") { layer.close() }
                }
            }
        }
        // end snippet
    }
}

private val dialogs = Page("Windows", "Dialogs",
    "Vanilla's custom dialog window, measure for measure: the holo frame, Confirm on G, Enter and Space, Cancel on " +
        "Escape, F2 for the codex, a flicker when the player clicks outside.", FILE) {

    card("Confirm and cancel", "dialog-show", "showDialog from a click; onConfirm and onCancel run, then it closes.") {
        // snippet: dialog-show
        Button("Scuttle the ship") {
            showDialog(380f, 50f, confirmText = "Scuttle", cancelText = "Keep it",
                onConfirm = { showToast("Scuttled", color = VanillaColors.negative) }) {
                Text("The ship and its cargo will be lost. Scuttle it?", Size.fillMaxWidth())
            }
        }
        // end snippet
    }

    card("From a state: Dialog", "dialog-state",
        "Dialog in a builder, open while the state says so; the builder's dialog confirms from the content.") {
        // snippet: dialog-state
        Row(spacing = 8f, verticalAlignment = Alignment.CenterVertically) {
            Button("Rename") { renameOpen.value = true }
            Text(shipName.value)
        }
        if (renameOpen.value) Dialog(onDismissRequest = { renameOpen.value = false }, 320f, 60f,
            confirmText = "Rename", cancelText = "Cancel") { dialog ->
            Column(Size.fillMaxSize(), spacing = 6f) {
                Text("New name", color = VanillaColors.gray)
                TextField(shipName, Size.fillMaxWidth(height = 26f))
            }
        }
        // end snippet
    }

    card("Pages", "dialog-pages", "contentKey: when it changes, the content is built again behind vanilla's static.") {
        // snippet: dialog-pages
        Button("Start the survey") { wizardPage.value = 0; wizardOpen.value = true }
        if (wizardOpen.value) Dialog(onDismissRequest = { wizardOpen.value = false }, 360f, 70f,
            confirmText = if (wizardPage.value < 2) "Next" else "Done", cancelText = "Cancel",
            onConfirm = { if (wizardPage.value < 2) wizardPage.value++ else wizardOpen.value = false },
            contentKey = wizardPage.value) {
            Column(Size.fillMaxSize(), spacing = 4f) {
                Text("Step ${wizardPage.value + 1} of 3", font = Font.ORBITRON_12, color = VanillaColors.base)
                Text(listOf("Pick the planet.", "Pick the survey team.", "Confirm the cost.")[wizardPage.value])
            }
        }
        // end snippet
    }

    card("A dialog over a dialog", "dialog-nested") {
        // snippet: dialog-nested
        Button("Open two") {
            showDialog(380f, 60f, confirmText = "Close") {
                Column(Size.fillMaxSize(), spacing = 6f) {
                    Text("The first dialog.")
                    Button("Open another") {
                        showDialog(300f, 30f, confirmText = "Close", background = HoloBackground.Dialog) { Text("On top of it.") }
                    }
                }
            }
        }
        // end snippet
    }

    card("Static and the codex", "dialog-options",
        "static picks which opens and closes burst with static; codexEntryId is what F2 opens.") {
        // snippet: dialog-options
        Button("Quiet, with a codex entry") {
            showDialog(380f, 40f, confirmText = "Close", static = DialogStatic.None,
                codexEntryId = CodexDataV2.getShipEntryId("onslaught")) {
                Text("No static. F2 opens the Onslaught's codex entry.", Size.fillMaxWidth())
            }
        }
        // end snippet
    }
}

private val toastsPage = Page("Windows", "Toasts",
    "Notices that slide in and go away by themselves, above every layer. Hover one to stop its time, click it to close it.",
    FILE) {

    card("A toast", "toast-plain", "From a click handler or a script; in a builder it throws (it would show again on every rebuild).") {
        // snippet: toast-plain
        Button("Show") { showToast("Doctrine saved") }
        // end snippet
    }

    card("Colour and time", "toast-options",
        "duration is real seconds (it runs while the game is paused); 0 keeps it until it is clicked or closed.") {
        // snippet: toast-options
        Row(spacing = 6f) {
            Button("Failure") { showToast("Not enough supplies", color = VanillaColors.negative) }
            Button("Ten seconds") { showToast("This one stays ten seconds", duration = 10f) }
            Button("Until clicked") { showToast("Click to close", duration = 0f) }
        }
        // end snippet
    }

    card("Places", "toast-places",
        "Any of the nine alignments. Each place has its own stack; the newest sits nearest the edge.") {
        // snippet: toast-places
        val places = listOf(
            listOf("Top left" to Alignment.TopLeft, "Top" to Alignment.TopCenter, "Top right" to Alignment.TopRight),
            listOf("Left" to Alignment.CenterLeft, "Centre" to Alignment.Center, "Right" to Alignment.CenterRight),
            listOf("Bottom left" to Alignment.BottomLeft, "Bottom" to Alignment.BottomCenter, "Bottom right" to Alignment.BottomRight),
        )
        Column(spacing = 4f) {
            for (row in places) Row(spacing = 4f) {
                for ((name, place) in row) Button(name, Size.exact(110f, 24f)) { showToast(name, alignment = place) }
            }
        }
        // end snippet
    }

    card("A burst", "toast-burst", "At most three show at one place; the rest wait, counted by a \"+N more\" chip.") {
        // snippet: toast-burst
        Button("Five at once") {
            for (i in 1..5) showToast("Notice $i of 5", duration = 4f)
        }
        // end snippet
    }

    card("Your own content", "toast-content", "Any components: the toast keeps its panel, slide and click to close around them.") {
        // snippet: toast-content
        Button("Charter renewed") {
            showToast(duration = 5f) {
                Row(spacing = 8f, verticalAlignment = Alignment.CenterVertically) {
                    Image("graphics/icons/cargo/supplies.png", ImageSize.native(maxWidth = 32f, maxHeight = 32f))
                    Column(spacing = 2f) {
                        Text("Charter renewed", font = Font.INSIGNIA_21, color = VanillaColors.base)
                        Text("Halberd survey team: 30 more days.", color = VanillaColors.gray)
                    }
                }
            }
        }
        // end snippet
    }

    card("Closed from code", "toast-handle", "showToast returns the toast: close() takes it away early.") {
        // snippet: toast-handle
        Row(spacing = 6f) {
            Button("Show") { held = showToast("Waiting for the fleet", duration = 0f) }
            Button("Close it") { held?.close() }
        }
        // end snippet
    }
}
internal val windowPages = listOf(layers, dialogs, toastsPage)
