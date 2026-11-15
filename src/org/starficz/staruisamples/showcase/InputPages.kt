package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.*
import java.awt.Color
import kotlin.math.sin

private const val FILE = "InputPages.kt"

// snippet: mouse
private val lastMouse = UIState("Click, release, or click elsewhere")
// end snippet
// snippet: keys-input
private val lastKey = UIState("Press and hold any key")
// end snippet
// snippet: callbacks
private val roomy = UIState(false)
// end snippet
// snippet: dump
private val dump = UIState("")
// end snippet

// snippet: gl-colour
/** Sets GL's colour to [color] at [alpha] (the render callbacks' alphaMult, which fades with the screen). */
private fun glColour(color: Color, alpha: Float) =
    GL11.glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f * alpha)
// end snippet

private val mouseAndKeys = Page("Input and drawing", "Mouse and keys",
    "Input modifiers work on any component, vanilla ones included. Handlers get vanilla's event and write states.", FILE) {

    card("Clicks", "mouse", "onClick on press, onClickRelease on release, onClickOutside for a click anywhere else.") {
        // snippet: mouse
        Box(Size.fillMaxWidth(height = 50f), Modifier.background(INSET)
            .onClick { event -> lastMouse.value = "onClick at ${event.x}, ${event.y}" }
            .onClickRelease { lastMouse.value = "onClickRelease" }
            .onClickOutside { lastMouse.value = "onClickOutside" }) {
            Text(lastMouse.value, modifier = Modifier.align(Alignment.Center))
        }
        // end snippet
    }

    card("Hover", "hover", "onHoverEnter and onHoverExit once each; onHover on every move over it.") {
        // snippet: hover
        lateinit var label: LabelAPI
        Box(Size.fillMaxWidth(height = 50f), Modifier.background(INSET)
            .onHoverEnter { label.text = "In" }
            .onHover { event -> label.text = "Over it at ${event.x}, ${event.y}" }
            .onHoverExit { label.text = "Out" }) {
            label = Text("Move the mouse here", modifier = Modifier.align(Alignment.Center))
        }
        // end snippet
    }

    card("Holding the mouse", "held", "onMouseHeld every frame the button is down over it; onAdvance lets the bar drain.") {
        // snippet: held
        var charge = 0f
        Box(Size.fillMaxWidth(height = 50f), Modifier.background(INSET)
            .onMouseHeld { charge = minOf(1f, charge + 0.02f) }
            .onAdvance { amount -> charge = maxOf(0f, charge - amount * 0.4f) }
            .onRender { alpha ->
                GL11.glDisable(GL11.GL_TEXTURE_2D)
                glColour(VanillaColors.bright, alpha)
                GL11.glRectf(left, bottom, left + width * charge, bottom + 4f)
                GL11.glEnable(GL11.GL_TEXTURE_2D)
            }) {
            Text("Hold the mouse button here", modifier = Modifier.align(Alignment.Center))
        }
        // end snippet
    }

    card("Keys", "keys-input", "onKeyDown, onKeyHeld (the key's repeats) and onKeyUp.") {
        // snippet: keys-input
        var repeats = 0
        Box(Size.fillMaxWidth(height = 50f), Modifier.background(INSET)
            .onKeyDown { event -> repeats = 0; lastKey.value = "Down: ${Keyboard.getKeyName(event.eventValue)}" }
            .onKeyHeld { repeats++ }
            .onKeyUp { event -> lastKey.value = "Up: ${Keyboard.getKeyName(event.eventValue)} after $repeats repeats" }) {
            Text(lastKey.value, modifier = Modifier.align(Alignment.Center))
        }
        // end snippet
    }
}

private val drawing = Page("Input and drawing", "Drawing and motion",
    "onRender draws over a component, onRenderBelow under its content; both with GL, in screen coordinates (y up). " +
        "onAdvance runs every frame.", FILE) {

    card("A colour helper", "gl-colour", "The callbacks get alphaMult: multiply by it so the drawing fades with the UI.")

    card("Under and over", "render",
        "onRenderBelow fills behind the text; onRender draws a frame of corner marks over it. left, bottom, right and top " +
            "are the component's edges.") {
        // snippet: render
        Box(Size.exact(220f, 50f), Modifier
            .onRenderBelow { alpha ->
                GL11.glDisable(GL11.GL_TEXTURE_2D)
                glColour(VanillaColors.dark, alpha)
                GL11.glRectf(left, bottom, right, top)
                GL11.glEnable(GL11.GL_TEXTURE_2D)
            }
            .onRender { alpha ->
                GL11.glDisable(GL11.GL_TEXTURE_2D)
                glColour(VanillaColors.bright, alpha)
                for ((x, y) in listOf(left to bottom, right - 8f to bottom, left to top - 2f, right - 8f to top - 2f)) {
                    GL11.glRectf(x, y, x + 8f, y + 2f)
                }
                GL11.glEnable(GL11.GL_TEXTURE_2D)
            }) {
            Text("Drawn under and over", modifier = Modifier.align(Alignment.Center))
        }
        // end snippet
    }

    card("A pulse", "pulse", "onAdvance moves a value every frame; onRender draws it. No state, no rebuild.") {
        // snippet: pulse
        var time = 0f
        Box(Size.exact(220f, 30f), Modifier
            .onAdvance { amount -> time += amount }
            .onRenderBelow { alpha ->
                GL11.glDisable(GL11.GL_TEXTURE_2D)
                glColour(VanillaColors.base, alpha * (0.35f + 0.25f * sin(time * 4f)))
                GL11.glRectf(left, bottom, right, top)
                GL11.glEnable(GL11.GL_TEXTURE_2D)
            }) {
            Text("Incoming transmission", modifier = Modifier.align(Alignment.Center))
        }
        // end snippet
    }

    card("Vanilla's slide", "slide",
        "Any component can slide: setSlideData gives the offset and times, slideIn and slideOut move it (and fade it).") {
        // snippet: slide
        lateinit var panel: CustomPanelAPI
        Row(spacing = 6f) {
            Button("Slide out") { panel.slideOut() }
            Button("Slide in") { panel.slideIn() }
        }
        Box(Size.fillMaxWidth(height = 40f), Modifier.background(INSET), overflow = Overflow.Clip) {
            panel = Box(Size.exact(160f, 30f), Modifier.align(Alignment.CenterLeft).background(TILE).padding(6f)) {
                Text("Sliding panel")
            }
            panel.setSlideData(-200f, 0f, 0.25f, 0.25f)
        }
        // end snippet
    }
}

private val callbacks = Page("Input and drawing", "Layout callbacks",
    "Code that runs when a component is laid out or moves, and the layout as text.", FILE) {

    card("Sizes as they change", "callbacks",
        "onLayout runs after every layout pass, onFirstLayout once; width and height are the component's size then.") {
        // snippet: callbacks
        Checkbox("Roomy", bind = roomy)
        lateinit var size: LabelAPI
        Box(Size.mixed(Axis.FillMax(), Axis.Exact(if (roomy.value) 60f else 30f)), Modifier.background(INSET).padding(6f)
            .onLayout { size.text = "${width.toInt()} x ${height.toInt()}" }) {
            size = Text("...")
        }
        // end snippet
    }

    card("The layout as text", "dump",
        "dumpLayout(): every component below one, with its kind, text, size, position, sizing and handlers.", wide = true) {
        // snippet: dump
        lateinit var sample: CustomPanelAPI
        sample = Row(spacing = 6f, verticalAlignment = Alignment.CenterVertically) {
            Text("Fuel")
            Button("Refuel") { }
            Button("Dump layout") { dump.value = sample.dumpLayout() }
        }
        if (dump.value.isNotEmpty()) Text(dump.value, Size.fillMaxWidth(), color = VanillaColors.gray)
        // end snippet
    }
}

internal val inputPages = listOf(mouseAndKeys, drawing, callbacks)
