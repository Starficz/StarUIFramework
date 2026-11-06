package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.ui.ButtonAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.core.onClick
import java.lang.ref.WeakReference

class UIState<T>(initialValue: T) {
    var value: T = initialValue
        set(newValue) {
            if (field != newValue) {
                field = newValue

                val observersSnapshot = observers.toList()
                observersSnapshot.forEach { weakRef ->
                    weakRef.get()?.invoke(newValue)
                }
                observers.retainAll { it.get() != null }

                strongObservers.forEach { it.invoke(newValue) }
            }
        }

    internal val observers = mutableListOf<WeakReference<(T) -> Unit>>()
    internal val strongObservers = mutableListOf<(T) -> Unit>()

    /**
     * Binds a listener to the UI.
     */
    fun onChange(ownerPlugin: StarUIPanelPlugin? = null, action: (T) -> Unit) {
        observers.retainAll { it.get() != null }

        val activePlugin = ownerPlugin ?: if (!StarUIContext.isTopLevel) StarUIContext.currentPlugin else null

        if (activePlugin != null) {
            activePlugin.registerBinding(action)
            observers.add(WeakReference(action))
        } else {
            throw IllegalStateException(
                "UIState.onChange() called outside of a UI Builder context! " +
                "If you are building UI, make sure this is inside a CustomPanel{} or similar block. " +
                "If you want a permanent global listener, use observePermanently() instead."
            )
        }
    }

    /**
     * Creates a permanent, strong reference listener.
     * WARNING: If used inside a UI, this will probably cause memory leaks.
     */
    fun observePermanently(action: (T) -> Unit) {
        strongObservers.add(action)
    }

    fun removeObserver(action: (T) -> Unit) {
        strongObservers.remove(action)
    }
}

class ButtonGroup {
    private val allFlags: MutableCollection<UIState<Boolean>> = mutableListOf()

    internal fun addButtonToGroup(button: ButtonAPI, bind: UIState<Boolean>) {
        allFlags.add(bind)
        button.isChecked = bind.value == true
        button.onClick {
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
