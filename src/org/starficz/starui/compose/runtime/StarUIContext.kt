package org.starficz.starui.compose.runtime

internal object StarUIContext {
    private val pluginStack = ThreadLocal.withInitial { mutableListOf<StarUIPanelPlugin>() }
    private val themeStack = ThreadLocal.withInitial { mutableListOf(StarUITheme.Default) }
    // null entries switch read tracking off for a stretch of code that runs inside a build but is not part of it
    private val scopeStack = ThreadLocal.withInitial { mutableListOf<Scope?>() }

    val isTopLevel: Boolean
        get() = pluginStack.get().isEmpty()

    val isManagedByParent: Boolean
        get() = pluginStack.get().lastOrNull()?.layoutEngine != null

    val currentPlugin: StarUIPanelPlugin
        get() = pluginStack.get().lastOrNull() ?: throw IllegalStateException(
            "DSL Called on non-DSL Context! " +
            "This component or state binding must be inside a CustomPanel or StackLayout builder block."
        )

    val currentPluginOrNull: StarUIPanelPlugin?
        get() = pluginStack.get().lastOrNull()

    val currentTheme: StarUITheme
        get() = themeStack.get().last()

    /** The scope whose builder is running right now, or null (event handlers, render callbacks, plain code). */
    val currentScope: Scope?
        get() = scopeStack.get().lastOrNull()

    inline fun <R> withScope(scope: Scope?, block: () -> R): R {
        val stack = scopeStack.get()
        stack.add(scope)
        try {
            return block()
        } finally {
            stack.removeAt(stack.lastIndex)
        }
    }

    // ---- key(...) blocks: the identity given to the components emitted directly inside one ----
    class KeyFrame(val id: Any) { var emitted = 0 }
    private val keyStack = ThreadLocal.withInitial { mutableListOf<KeyFrame?>() }

    /** [id] == null hides an outer key block (used while a nested container builds its own children). */
    inline fun <R> withKey(id: Any?, block: () -> R): R {
        val stack = keyStack.get()
        stack.add(if (id == null) null else KeyFrame(id))
        try {
            return block()
        } finally {
            stack.removeAt(stack.lastIndex)
        }
    }

    /** The key for the component being emitted now, or null outside a key block. Each call uses up one position. */
    fun nextKey(): Any? {
        val frame = keyStack.get().lastOrNull() ?: return null
        return Pair(frame.id, frame.emitted++)
    }

    /** Runs [block] with read tracking off: UIState reads inside it do not subscribe the scope being built. */
    inline fun <R> untracked(block: () -> R): R = withScope(null, block)

    inline fun <R> withPlugin(plugin: StarUIPanelPlugin, block: () -> R): R {
        val stack = pluginStack.get()
        stack.add(plugin)
        try {
            return block()
        } finally {
            stack.removeAt(stack.lastIndex)
        }
    }

    inline fun <R> withTheme(theme: StarUITheme, block: () -> R): R {
        val stack = themeStack.get()
        stack.add(theme)
        try {
            return block()
        } finally {
            stack.removeAt(stack.lastIndex)
        }
    }
}