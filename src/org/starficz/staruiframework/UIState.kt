package org.starficz.staruiframework

import java.lang.ref.WeakReference

class UIState<T>(initialValue: T) {
    var value: T = initialValue
        set(newValue) {
            if (field != newValue) {
                field = newValue

                observers.retainAll { weakRef ->
                    val observer = weakRef.get()
                    observer?.invoke(newValue)
                    observer != null
                }

                strongObservers.forEach { it.invoke(newValue) }
            }
        }

    internal val observers = mutableListOf<WeakReference<(T) -> Unit>>()
    internal val strongObservers = mutableListOf<(T) -> Unit>()

    /**
     * Binds a listener to the UI.
     * Must be called inside a UI Builder block (e.g., CustomPanel {}, HorizontalStackLayout {}).
     */
    fun onChange(action: (T) -> Unit) {
        val activePlugin = StarUIContext.currentPlugin

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