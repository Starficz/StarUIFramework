package org.starficz.starui.components

import com.fs.starfarer.api.ui.ButtonAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.UIState
import org.starficz.starui.vanilla.onClick

/**
 * A group of toggle buttons that select together, as in vanilla's filter bars. Each member is a [Checkbox] or
 * [AreaCheckbox] with `bind` and `buttonGroup` set, bound to its own `UIState<Boolean>`. A click:
 * - selects only the clicked button, as a radio group does;
 * - with Shift or Ctrl held, toggles just the clicked button, so several can be on;
 * - on the only selected button, selects them all, so a second click shows everything again.
 *
 * A button leaves the group when the builder that made it runs again without it, or when its UI is removed, so a
 * group kept outside the builder never holds a removed button.
 *
 * Example usage:
 *
 * ```kotlin
 * val group = ButtonGroup()
 * Row(spacing = 4f) {
 *     AreaCheckbox("KINETIC", showKinetic, buttonGroup = group)
 *     AreaCheckbox("ENERGY", showEnergy, buttonGroup = group)
 * }
 * ```
 */
class ButtonGroup {
    // How many live buttons use each state, in the order the states joined. A button made inside a builder leaves the
    // group when that builder runs again (its next pass adds the buttons still there) or its UI is thrown away, so a
    // group that outlives the builder never keeps a removed button's state. Counted, not a set, because two buttons
    // may share one state.
    private val members = LinkedHashMap<UIState<Boolean>, Int>()

    internal fun addButtonToGroup(button: ButtonAPI, bind: UIState<Boolean>) {
        members[bind] = (members[bind] ?: 0) + 1
        StarUIContext.currentScope?.addCleanup {
            val left = (members[bind] ?: return@addCleanup) - 1
            if (left <= 0) members.remove(bind) else members[bind] = left
        }
        button.isChecked = bind.peek() == true
        button.onClick {
            val allFlags = members.keys.toList()
            if (allFlags.count { it.value } == 1 && bind.value) {
                allFlags.forEach { it.value = true }
            } else {
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
                    Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ||
                    Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ||
                    Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                    bind.value = !bind.value
                } else {
                    allFlags.forEach { it.value = (it === bind) }
                }
            }
            button.isChecked = bind.value
        }
    }
}
