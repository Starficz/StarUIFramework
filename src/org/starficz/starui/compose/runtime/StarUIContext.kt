package org.starficz.starui.compose.runtime

internal object StarUIContext {
    private val pluginStack = ThreadLocal.withInitial { mutableListOf<StarUIPanelPlugin>() }
    private val themeStack = ThreadLocal.withInitial { mutableListOf(StarUITheme.Default) }

    val isTopLevel: Boolean
        get() = pluginStack.get().isEmpty()

    val currentPlugin: StarUIPanelPlugin
        get() = pluginStack.get().lastOrNull() ?: throw IllegalStateException(
            "DSL Called on non-DSL Context! " +
            "This component or state binding must be inside a CustomPanel or StackLayout builder block."
        )

    val currentTheme: StarUITheme
        get() = themeStack.get().last()

    inline fun <R> withPlugin(plugin: StarUIPanelPlugin, block: () -> R): R {
        val stack = pluginStack.get()
        stack.add(plugin)
        try {
            return block()
        } finally {
            stack.removeLast()
        }
    }

    inline fun <R> withTheme(theme: StarUITheme, block: () -> R): R {
        val stack = themeStack.get()
        stack.add(theme)
        try {
            return block()
        } finally {
            stack.removeLast()
        }
    }
}