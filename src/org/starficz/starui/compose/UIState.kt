package org.starficz.starui.compose

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
     * Must be called inside a UI Builder block (e.g., CustomPanel {}, HorizontalStackLayout {}).
     */
    fun onChange(action: (T) -> Unit) {
        observers.retainAll { it.get() != null }

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


data class Margin(val left: Float = 0f, val right: Float = 0f, val top: Float = 0f, val bottom: Float = 0f)

internal object StarUIContext {
    private val threadLocalStack = ThreadLocal.withInitial { mutableListOf<StarUIPanelPlugin>() }

    private val pluginStack: MutableList<StarUIPanelPlugin>
        get() = threadLocalStack.get()

    val isTopLevel: Boolean
        get() = pluginStack.isEmpty()

    val currentPlugin: StarUIPanelPlugin
        get() = pluginStack.lastOrNull() ?: throw IllegalStateException(
            "DSL Called on non-DSL Context! " +
            "This component or state binding must be inside a CustomPanel or StackLayout builder block."
        )

    inline fun <R> withPlugin(plugin: StarUIPanelPlugin, block: () -> R): R {
        val stack = pluginStack
        stack.add(plugin)
        try {
            return block()
        } finally {
            stack.removeLast()
        }
    }
}