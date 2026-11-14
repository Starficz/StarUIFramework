package org.starficz.starui

import org.starficz.starui.runtime.Scope
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.runtime.StarUIPanelPlugin
import java.lang.ref.WeakReference
import kotlin.reflect.KProperty

/**
 * A mutable value holder where reads to the [value] property during the execution of a builder (`Column { }`,
 * `CustomPanel { }`, ...) subscribe that builder to changes of that value. When the [value] property is written to
 * and changed, a rebuild of every subscribed builder will be scheduled for the next frame. If [value] is written to
 * with the same value, no rebuilds will be scheduled.
 *
 * So plain `if`, `for` and `when` in a builder stay in step with the state:
 *
 * ```kotlin
 * val ships = mutableStateOf(listOf<Ship>())
 * Column {
 *     for (ship in ships.value) key(ship.id) { Text(ship.name) }
 *     if (ships.value.isEmpty()) Text("No ships")
 * }
 * ```
 *
 * Reads anywhere else (event handlers, render callbacks) just return the value, and nothing is rebuilt during a
 * write: it only marks the subscribed builders.
 *
 * A [UIState] is also a property delegate: `var count by mutableStateOf(0)` reads and writes [value].
 *
 * @param initialValue the initial value
 * @see mutableStateOf
 */
class UIState<T>(initialValue: T) {
    private var current: T = initialValue

    /**
     * The current value. Reading it in a builder subscribes that builder; writing a different value schedules a
     * rebuild of every subscribed builder and calls the [onChange] and [observeForever] listeners.
     */
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

    /**
     * Returns the current value without subscribing the builder that is running: the builder will not rebuild when
     * this state changes.
     */
    fun peek(): T = current

    /**
     * Permits property delegation of `var`s using `by` for [UIState]: reads [value].
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    /**
     * Permits property delegation of `var`s using `by` for [UIState]: writes [value].
     */
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
     * Calls [action] with the new value after every change, for as long as the UI around this call lives. Call it in a
     * builder: it is dropped when the builder runs again (its next run adds it again), and when the UI is removed.
     *
     * For a listener outside any UI, use [observeForever].
     *
     * @param action called with the new value after each change
     * @throws IllegalStateException if called outside a builder
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
     * Calls [action] with the new value after every change, until [removeObserver] is called with it.
     *
     * The state holds [action] strongly: anything it refers to stays alive as long as the state does. Inside UI, use
     * [onChange] instead.
     *
     * @param action called with the new value after each change
     */
    fun observeForever(action: (T) -> Unit) {
        strongObservers.add(action)
    }

    /**
     * Stops a listener added with [observeForever].
     *
     * @param action the listener to remove
     */
    fun removeObserver(action: (T) -> Unit) {
        strongObservers.remove(action)
    }
}

/**
 * Return a new [UIState] initialized with the passed in [value], under Compose's name for it.
 *
 * ```kotlin
 * val count = mutableStateOf(0)
 * var name by mutableStateOf("")
 * ```
 *
 * @param value the initial value for the [UIState]
 * @see UIState
 */
fun <T> mutableStateOf(value: T): UIState<T> = UIState(value)
