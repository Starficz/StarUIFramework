package org.starficz.starui

import org.starficz.starui.runtime.Scope
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.runtime.StarUIPanelPlugin
import java.lang.ref.WeakReference
import kotlin.reflect.KProperty

/**
 * A value the UI follows.
 *
 * Reading [value] inside a container's builder block (`Column { ... }`, `CustomPanel { ... }`, ...) subscribes that
 * container: when the value changes, its children are rebuilt on the next frame, so plain `if` / `for` / `when` in a
 * builder stay in step with the state. Reads anywhere else (event handlers, render callbacks) just return the value.
 * Writing only marks the subscribed containers; nothing is rebuilt during the write.
 *
 * Use it directly (`state.value`) or as a delegate: `var count by mutableStateOf(0)` reads and writes [value].
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

    /** `var x by state`: reads [value]. */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    /** `var x by state`: writes [value]. */
    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        value = newValue
    }

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
     * Runs [action] with the new value after every change, for as long as the UI around this call lives: call it in a
     * builder. It is dropped when the builder runs again (its next pass adds it again) or the UI is thrown away. For a
     * listener outside any UI, use [observeForever].
     */
    fun onChange(action: (T) -> Unit) {
        val plugin = StarUIContext.currentPluginOrNull ?: throw IllegalStateException(
            "StarUI: UIState.onChange() was called outside a builder block. It lives as long as the UI around it, so " +
            "call it inside a Column { }, CustomPanel { } or other builder; for a listener outside any UI, use " +
            "observeForever()."
        )
        bindTo(plugin, action)
    }

    /** [onChange] for [owner]'s component: kept while it lives, dropped when it is reset or disposed. */
    internal fun bindTo(owner: StarUIPanelPlugin, action: (T) -> Unit) {
        observers.retainAll { it.get() != null }
        owner.registerBinding(this, action)
        observers.add(WeakReference(action))
    }

    /**
     * Runs [action] with the new value after every change until [removeObserver]. The state holds [action] strongly:
     * anything it refers to stays alive as long as the state does, so inside UI use [onChange] instead.
     */
    fun observeForever(action: (T) -> Unit) {
        strongObservers.add(action)
    }

    /** Stops an [observeForever] listener. */
    fun removeObserver(action: (T) -> Unit) {
        strongObservers.remove(action)
    }
}

/** Compose's name for a new [UIState]: `val count = mutableStateOf(0)`. */
fun <T> mutableStateOf(value: T): UIState<T> = UIState(value)
