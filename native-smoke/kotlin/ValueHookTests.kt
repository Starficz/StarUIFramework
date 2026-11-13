// Bound controls follow their native value through PatchLib hooks (TextFieldAPI.setText, ButtonAPI.setChecked,
// SliderAPI.setProgress), not by polling: a change shows in the state at once, whoever made it.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.SliderAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.components.AreaCheckbox
import org.starficz.starui.compose.components.Checkbox
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.components.ProgressBar
import org.starficz.starui.compose.components.Slider
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.components.TextField
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.onLayout
import org.starficz.starui.compose.runtime.ButtonGroup
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.asComponent
import java.awt.Color

private val HOOK_BG = Color(30, 40, 50)

private fun hookAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

private fun TestContext.press(c: UIComponentAPI, x: Float = c.cx, y: Float = c.cy) {
    Input.sendList(root, Input.move(x, y))
    Input.sendList(root, Input.down(x, y))
    Input.sendList(root, Input.up(x, y))
    NativeSmokeSupport.advance(root, 2)
}

/** The x a drag must reach for [value] on a vanilla slider (6 px inset each side, as in its input handler). */
private fun SliderAPI.xFor(value: Float): Float {
    val c = asComponent
    return c.position.x + 6f + (value - rangeMin) / (rangeMax - rangeMin) * (c.w - 6f)
}

fun registerValueHookTests() {
    StarUITestSuite.group("hook-textfield-follows-settext") {
        val text = UIState("start")
        val tick = UIState(0)
        lateinit var field: TextFieldAPI
        root.Column(Size.wrapContent(), hookAt(40f, 40f).background(HOOK_BG)) {
            Text("tick ${tick.value}")
            field = TextField(text, size = Size.exact(200f, 24f))
        }
        frame()
        field.text = "from code"
        expect(text.peek() == "from code", "a setText call reaches the state at once, no frame needed (${text.peek()})")

        field.grabFocus()
        Input.sendList(root, Input.keyDown(Keyboard.KEY_X, 'x'))
        Input.sendList(root, Input.keyUp(Keyboard.KEY_X, 'x'))
        expect(text.peek() == "from codex", "typing reaches the state (${text.peek()})")

        tick.value = 1
        frame()
        val reused = field
        reused.text = "after rebuild"
        expect(text.peek() == "after rebuild", "the reused field is still bound after a rebuild (${text.peek()})")
        expect(text.observers.size == 1, "with one binding, not one per build (${text.observers.size})")

        text.value = "from state"
        frame()
        expect(field.text == "from state", "writing the state still updates the field (${field.text})")
    }

    StarUITestSuite.group("hook-checkbox-follows-setchecked") {
        val plain = UIState(false)
        val area = UIState(false)
        val groupA = UIState(true); val groupB = UIState(false)
        val group = ButtonGroup()
        lateinit var box: ButtonAPI
        lateinit var areaBox: ButtonAPI
        lateinit var memberB: ButtonAPI
        root.Column(Size.wrapContent(), hookAt(40f, 40f).background(HOOK_BG), spacing = 4f) {
            box = Checkbox("plain", bind = plain)
            areaBox = AreaCheckbox("area", area, Size.exact(120f, 24f))
            AreaCheckbox("A", groupA, Size.exact(120f, 24f), buttonGroup = group)
            memberB = AreaCheckbox("B", groupB, Size.exact(120f, 24f), buttonGroup = group)
        }
        frame()

        box.isChecked = true
        expect(plain.peek(), "setChecked(true) from code reaches a Checkbox's state")
        areaBox.isChecked = true
        expect(area.peek(), "and an AreaCheckbox's")
        box.isChecked = false
        expect(!plain.peek(), "unchecking from code too")

        press(box)
        expect(plain.peek() && box.isChecked, "a click still toggles it (state ${plain.peek()}, box ${box.isChecked})")

        memberB.isChecked = true
        expect(groupB.peek(), "setChecked on a group member reaches its state")
        press(memberB)
        expect(groupB.peek() && !groupA.peek(), "and clicks keep the group's rules (a=${groupA.peek()} b=${groupB.peek()})")
    }

    StarUITestSuite.group("hook-slider-binding") {
        val value = UIState(50f)
        val stepped = UIState(0f)
        var layouts = 0
        lateinit var slider: org.starficz.starui.core.BoxedUISlider
        lateinit var snapping: SliderAPI
        root.Column(Size.wrapContent(), hookAt(40f, 40f).background(HOOK_BG).onLayout { layouts++ }, spacing = 10f) {
            Text("value")
            slider = Slider(value, valueRange = 0f..100f, text = "Value")
            snapping = Slider(stepped, valueRange = 0f..100f, step = 10f)
        }
        frame()
        near(slider.progress, 50f, "the slider starts at the state's value")
        near(slider.asComponent.w, 200f, "at its default width")
        expect(slider.position === slider.asComponent.position && slider.parent === slider.asComponent.parent,
            "Slider returns a box that is the component itself (same position and parent)")

        slider.progress = 40f
        near(value.peek(), 40f, "setProgress from code reaches the state at once")

        value.value = 70f
        frame()
        near(slider.progress, 70f, "writing the state moves the slider")

        // A real drag: press at 25 %, move to 80 %, release there.
        val y = slider.asComponent.cy
        Input.sendList(root, Input.move(slider.xFor(25f), y))
        Input.sendList(root, Input.down(slider.xFor(25f), y))
        near(value.peek(), 25f, "pressing on the bar sets the value there", tolerance = 1f)
        Input.sendList(root, Input.move(slider.xFor(80f), y))
        near(value.peek(), 80f, "dragging follows the mouse", tolerance = 1f)
        Input.sendList(root, Input.up(slider.xFor(80f), y))
        NativeSmokeSupport.advance(root, 2)
        near(value.peek(), 80f, "and the value stays where it was released", tolerance = 1f)

        val sy = snapping.asComponent.cy
        Input.sendList(root, Input.move(snapping.xFor(27f), sy))
        Input.sendList(root, Input.down(snapping.xFor(27f), sy))
        Input.sendList(root, Input.up(snapping.xFor(27f), sy))
        NativeSmokeSupport.advance(root, 2)
        near(stepped.peek(), 30f, "with step = 10 a drag to 27 snaps the state to 30")
        near(snapping.progress, 30f, "and the slider to it")

        value.value = 150f
        frame()
        near(slider.progress, 100f, "a state above the range shows the slider full")
        expect(value.peek() == 150f, "without the state being rewritten (${value.peek()})")

        // The slider rewrites its own value label every frame; that must not re-run the layout every frame.
        frame()
        val before = layouts
        NativeSmokeSupport.advance(root, 10)
        expect(layouts == before, "ten frames of an idle slider cause no layout passes (${layouts - before})")
        screenshot("final")
    }

    StarUITestSuite.group("hook-progressbar-follows-value") {
        val progress = UIState(20f)
        lateinit var bar: SliderAPI
        root.Column(Size.wrapContent(), hookAt(40f, 40f).background(HOOK_BG), spacing = 6f) {
            Text("progress ${progress.value.toInt()}")
            bar = ProgressBar(progress.value, animate = false)
        }
        frame()
        val first = bar
        near(bar.progress, 20f, "the bar starts at the value")
        near(bar.asComponent.h, 12f, "at its default height")

        progress.value = 60f
        frame()
        expect(bar === first, "a rebuild reuses the native bar")
        near(bar.progress, 60f, "which shows the new value")

        press(bar.asComponent, bar.xFor(90f))
        near(bar.progress, 60f, "the player can't drag a progress bar")
        screenshot("final")
    }
}
