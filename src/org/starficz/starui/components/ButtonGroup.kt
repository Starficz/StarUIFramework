package org.starficz.starui.components

import com.fs.starfarer.api.ui.ButtonAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.UIState
import org.starficz.starui.vanilla.onClick

/**
 * Toggle buttons that select together: a [FilterGroup] (vanilla's filter bar) or a [RadioGroup] (one choice out of a
 * few). Each member is a [Checkbox] or [AreaCheckbox] with `bind` and `buttonGroup` set, bound to its own
 * `UIState<Boolean>`; the group writes those states.
 *
 * A button leaves its group when the builder that made it runs again without it, or when its UI is removed, so a
 * group kept outside the builder never holds a removed button.
 */
sealed class ButtonGroup {
    // How many live buttons use each state, in the order the states joined. A button made inside a builder leaves the
    // group when that builder runs again (its next pass adds the buttons still there) or its UI is thrown away, so a
    // group that outlives the builder never keeps a removed button's state. Counted, not a set, because two buttons
    // may share one state.
    private val members = LinkedHashMap<UIState<Boolean>, Int>()

    /** The members' states, in the order they joined. */
    protected val states: List<UIState<Boolean>> get() = members.keys.toList()

    internal fun addButtonToGroup(button: ButtonAPI, bind: UIState<Boolean>) {
        members[bind] = (members[bind] ?: 0) + 1
        StarUIContext.currentScope?.addCleanup {
            val left = (members[bind] ?: return@addCleanup) - 1
            if (left <= 0) members.remove(bind) else members[bind] = left
        }
        button.isChecked = bind.peek() == true
        joined(button, bind)
        button.onClick {
            clicked(bind)
            // The native click flipped the button before this runs. Show the state instead: when the click left the
            // state as it was (a radio button that was already on), no binding would put the button back.
            button.isChecked = bind.value
        }
    }

    /** A button joined, bound to [bind]. */
    internal open fun joined(button: ButtonAPI, bind: UIState<Boolean>) {}

    /** The button bound to [bind] was clicked: write the members' states. */
    internal abstract fun clicked(bind: UIState<Boolean>)
}

/**
 * A group of toggle buttons that filter together, as vanilla's filter bars do (the weapon picker's damage types). A
 * click:
 * - selects only the clicked button;
 * - with Shift or Ctrl held, toggles just the clicked button, so several can be on;
 * - on the only selected button, selects them all, so a second click shows everything again.
 *
 * Example usage:
 *
 * ```kotlin
 * val filters = FilterGroup()
 * Row(spacing = 4f) {
 *     AreaCheckbox("KINETIC", showKinetic, buttonGroup = filters)
 *     AreaCheckbox("ENERGY", showEnergy, buttonGroup = filters)
 * }
 * ```
 *
 * @see RadioGroup
 */
class FilterGroup : ButtonGroup() {
    override fun clicked(bind: UIState<Boolean>) {
        val all = states
        if (all.count { it.value } == 1 && bind.value) {
            all.forEach { it.value = true }
        } else if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ||
            Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            bind.value = !bind.value
        } else {
            all.forEach { it.value = (it === bind) }
        }
    }
}

/**
 * A group of toggle buttons of which exactly one is selected: one choice out of a few (a mode, an option, toggles
 * used as tabs).
 * - A click selects only the clicked button; a click on the selected one keeps it selected.
 * - A member's state set to `true` from code turns the others off too.
 *
 * Start it with exactly one state `true`.
 *
 * Example usage:
 *
 * ```kotlin
 * // one of three methods: the chosen one is the index of the state that is true
 * val methods = List(3) { mutableStateOf(it == 0) }
 * val methodGroup = RadioGroup()
 * Row(spacing = 5f) {
 *     listOf("Orbital", "Ground", "Deep core").forEachIndexed { i, name ->
 *         AreaCheckbox(name, methods[i], buttonGroup = methodGroup)
 *     }
 * }
 * ```
 *
 * @see FilterGroup
 */
class RadioGroup : ButtonGroup() {
    override fun joined(button: ButtonAPI, bind: UIState<Boolean>) {
        // a member turned on by code turns the others off, as a click on it would
        bind.bindTo(StarUIPanelPlugin.getOrCreate(button)) { on -> if (on) turnOffAllBut(bind) }
    }

    override fun clicked(bind: UIState<Boolean>) {
        bind.value = true
        turnOffAllBut(bind)
    }

    private fun turnOffAllBut(bind: UIState<Boolean>) {
        states.forEach { if (it !== bind) it.value = false }
    }
}
