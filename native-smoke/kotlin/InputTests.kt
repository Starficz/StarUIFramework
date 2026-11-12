// Input modifier contracts, driven with REAL engine events (StarUITestSupport), through the same entry points the
// engine uses. Every handler must run exactly once per dispatched event.

import com.fs.starfarer.api.ui.UIComponentAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.Button
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.onClick
import org.starficz.starui.compose.modifier.onClickOutside
import org.starficz.starui.compose.modifier.onClickRelease
import org.starficz.starui.compose.modifier.onHover
import org.starficz.starui.compose.modifier.onHoverEnter
import org.starficz.starui.compose.modifier.onHoverExit
import org.starficz.starui.compose.modifier.onKeyDown
import org.starficz.starui.compose.modifier.onKeyUp
import org.starficz.starui.core.Anchor

class Counts {
    var click = 0; var release = 0; var outside = 0
    var hover = 0; var enter = 0; var exit = 0
    var keyDown = 0; var keyUp = 0
    override fun toString() =
        "click=$click release=$release outside=$outside hover=$hover enter=$enter exit=$exit keyDown=$keyDown keyUp=$keyUp"
}

fun Modifier.counting(c: Counts): Modifier = this
    .onClick { c.click++ }.onClickRelease { c.release++ }.onClickOutside { c.outside++ }
    .onHover { c.hover++ }.onHoverEnter { c.enter++ }.onHoverExit { c.exit++ }
    .onKeyDown { c.keyDown++ }.onKeyUp { c.keyUp++ }

/** A 400x300 host panel at (40,40) from the top-left with one centred, instrumented Text. Returns (host, label). */
private fun TestContext.instrumentedLabel(counts: Counts, text: String = "Input target"): Pair<UIComponentAPI, UIComponentAPI> {
    var label: UIComponentAPI? = null
    val host = root.CustomPanel(Size.exact(400f, 300f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f))) {
        label = Text(text, modifier = Modifier.counting(counts).anchor(Anchor.inside.center.ofParent()))
    }
    frame()
    return host to label!!
}

fun registerInputTests() {
    StarUITestSuite.group("input-tree-dispatch") {
        // Engine path: root receives the List, panels hand the internal list to children.
        val c = Counts()
        val (_, label) = instrumentedLabel(c)
        val x = label.cx; val y = label.cy
        val outX = label.position.x - 60f

        Input.sendList(root, Input.down(x, y))
        expect(c.click == 1, "mouse down inside fires onClick once ($c)")
        expect(c.enter == 1, "first event inside fires onHoverEnter once ($c)")
        expect(c.hover == 1, "mouse down inside fires onHover once ($c)")

        Input.sendList(root, Input.up(x, y))
        expect(c.release == 1, "mouse up after down inside fires onClickRelease once ($c)")

        Input.sendList(root, Input.move(x + 1f, y))
        expect(c.hover == 3, "every inside mouse event fires onHover; 3 events so far ($c)")
        expect(c.enter == 1, "onHoverEnter does not repeat while still inside ($c)")

        Input.sendList(root, Input.move(outX, y))
        expect(c.exit == 1, "leaving fires onHoverExit once ($c)")
        expect(c.hover == 3, "outside event does not fire onHover ($c)")

        Input.sendList(root, Input.down(outX, y))
        expect(c.outside == 1, "mouse down outside fires onClickOutside once ($c)")
        expect(c.click == 1, "mouse down outside does not fire onClick ($c)")
        Input.sendList(root, Input.up(outX, y))
        expect(c.release == 1, "mouse up outside does not fire onClickRelease ($c)")

        Input.sendList(root, Input.up(x, y))
        expect(c.release == 1, "mouse up inside without a preceding down inside does not fire onClickRelease ($c)")

        Input.sendList(root, Input.keyDown(Keyboard.KEY_A, 'a'))
        expect(c.keyDown == 1, "key down fires onKeyDown once ($c)")
        Input.sendList(root, Input.keyUp(Keyboard.KEY_A, 'a'))
        expect(c.keyUp == 1, "key up fires onKeyUp once ($c)")
        log("final counts: $c")
    }

    StarUITestSuite.group("input-direct-list-to-leaf") {
        // A native leaf handed a raw List (top-level component, or a mod calling processInput itself).
        // UIComponentLifecyclePatch hooks processInput by parameterCount = 1, which also matches the internal
        // overload that processInput(List) delegates to on the same object.
        val c = Counts()
        val (_, label) = instrumentedLabel(c)
        Input.sendList(label, Input.down(label.cx, label.cy))
        expect(c.click == 1, "processInput(List) on a leaf fires onClick exactly once ($c)")
        expect(c.hover == 1, "processInput(List) on a leaf fires onHover exactly once ($c)")
        Input.sendList(label, Input.keyDown(Keyboard.KEY_B, 'b'))
        expect(c.keyDown == 1, "processInput(List) on a leaf fires onKeyDown exactly once ($c)")
    }

    StarUITestSuite.group("input-internal-to-leaf") {
        val c = Counts()
        val (_, label) = instrumentedLabel(c)
        Input.sendInternal(label, Input.down(label.cx, label.cy))
        expect(c.click == 1, "internal-list dispatch on a leaf fires onClick exactly once ($c)")
    }

    StarUITestSuite.group("input-consumed-events") {
        // Engine InputEvent getters throw "Attempt to access data of a consumed InputEvent". The parent only purges
        // consumed events after all children ran, so a leaf's after-hook can still see them.
        val c = Counts()
        val (_, label) = instrumentedLabel(c)
        val consumed = Input.down(label.cx, label.cy).also { it.consume() }
        val error = thrown { Input.sendList(root, consumed) }
        expect(error == null, "a consumed event in the list does not throw from a modified leaf (got $error)")
        expect(c.click == 0, "a consumed event does not fire onClick ($c)")

        // A native Button that consumes its own click, carrying StarUI input modifiers.
        val bc = Counts()
        var pressed = 0
        var button: UIComponentAPI? = null
        root.CustomPanel(Size.exact(300f, 120f), Modifier.anchor(Anchor.inside.topRight.ofParent(40f, 40f))) {
            button = Button("Press me", modifier = Modifier.counting(bc).anchor(Anchor.inside.center.ofParent())) { pressed++ }
        }
        frame()
        val b = button!!
        val err2 = thrown {
            Input.sendList(root, Input.move(b.cx, b.cy))
            Input.sendList(root, Input.down(b.cx, b.cy))
            Input.sendList(root, Input.up(b.cx, b.cy))
        }
        expect(err2 == null, "hover + click on a Button with input modifiers does not throw (got $err2)")
        log("button modifier counts: $bc, vanilla onClick callbacks: $pressed")
        frame()
        screenshot("final")
    }
}
