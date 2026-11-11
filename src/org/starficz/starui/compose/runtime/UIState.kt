package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.ui.ButtonAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.core.onClick
import java.lang.ref.WeakReference

/**
 * A value the UI follows.
 *
 * Reading [value] inside a container's builder block (`Column { ... }`, `CustomPanel { ... }`, ...) subscribes that
 * container: when the value changes, its children are rebuilt on the next frame, so plain `if` / `for` / `when` in a
 * builder stay in step with the state. Reads anywhere else (event handlers, render callbacks) just return the value.
 * Writing only marks the subscribed containers; nothing is rebuilt during the write.
 */
class UIState<T>(initialValue: T) {
    private var current: T = initialValue

    var value: T
        get() {
            StarUIContext.currentScope?.recordRead(this)
            return current
        }
        set(newValue) {
            if (current != newValue) {
                current = newValue

                if (scopeSubscribers.isNotEmpty()) {
                    val subscribed = scopeSubscribers.toList()
                    scopeSubscribers.retainAll { it.get()?.disposed == false }
                    subscribed.forEach { it.get()?.markDirty() }
                }

                // Observers are not part of whatever builder happens to be running when the write is made.
                StarUIContext.untracked {
                    val observersSnapshot = observers.toList()
                    observersSnapshot.forEach { weakRef ->
                        weakRef.get()?.invoke(newValue)
                    }
                    observers.retainAll { it.get() != null }

                    strongObservers.toList().forEach { it.invoke(newValue) }
                }
            }
        }

    /** Reads the value without subscribing the builder that is running. */
    fun peek(): T = current

    internal val observers = mutableListOf<WeakReference<(T) -> Unit>>()
    internal val strongObservers = mutableListOf<(T) -> Unit>()
    internal val scopeSubscribers = mutableListOf<WeakReference<Scope>>()

    internal fun subscribe(scope: WeakReference<Scope>) {
        scopeSubscribers.add(scope)
    }

    internal fun unsubscribe(scope: WeakReference<Scope>) {
        scopeSubscribers.removeAll { it === scope || it.get() == null }
    }

    /** Removes a listener added with [onChange] right away (it would otherwise linger until garbage collection). */
    internal fun removeBinding(action: Any) {
        observers.removeAll { it.get().let { held -> held == null || held === action } }
    }

    /**
     * Binds a listener to the UI.
     */
    fun onChange(ownerPlugin: StarUIPanelPlugin? = null, action: (T) -> Unit) {
        observers.retainAll { it.get() != null }

        val activePlugin = ownerPlugin ?: if (!StarUIContext.isTopLevel) StarUIContext.currentPlugin else null

        if (activePlugin != null) {
            activePlugin.registerBinding(this, action)
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
    // A set: a rebuilt builder registers the same states again for its new buttons.
    private val allFlags: MutableCollection<UIState<Boolean>> = LinkedHashSet()

    internal fun addButtonToGroup(button: ButtonAPI, bind: UIState<Boolean>) {
        allFlags.add(bind)
        button.isChecked = bind.peek() == true
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
