package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.apache.log4j.Logger
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.clearChildren
import org.starficz.starui.core.getChildrenNonCopy
import org.starficz.starui.core.parent
import java.lang.ref.WeakReference

/**
 * One container's builder block, kept so it can run again.
 *
 * Every StarUI container (CustomPanel, Box, Column, Row, ScrollPanel content) owns a Scope. While the builder runs,
 * each [UIState.value] read is recorded; when one of those states changes the scope is marked dirty, and on the next
 * frame the container's children are thrown away and the builder runs again. That is what makes a plain `if` or
 * `for` inside a builder follow its state.
 *
 * Timing (see docs/reactive-scopes-plan.md, 5.2): a write only sets flags. Rebuilds happen from the `renderBelow` or
 * `advance` callback of a StarUI node above the scope, so never while input is being dispatched, never from campaign
 * scripts, and never for UI that is not attached to anything that renders or advances.
 */
internal class Scope(
    val plugin: StarUIPanelPlugin,
    private val theme: StarUITheme,
    private val kind: String,
    private val declaredIn: String,
    private val builder: () -> Unit
) {
    var dirty = false
        private set
    var disposed = false
        private set

    /** How many times the builder has run (1 after the first build). */
    var buildCount = 0
        private set

    /** Plugins created before this value existed before the current build started. */
    var buildSerial = 0L
        private set

    internal val selfRef = WeakReference(this)
    private val reads = LinkedHashSet<UIState<*>>()
    private val cleanups = ArrayList<() -> Unit>()
    private var loopReported = false

    /** True once the builder has read at least one state, so the children may be replaced at any time. */
    val isReactive: Boolean get() = reads.isNotEmpty()

    fun recordRead(state: UIState<*>) {
        if (reads.add(state)) state.subscribe(selfRef)
    }

    /** Something the current build attached to an object that outlives it; undone before the next build. */
    fun addCleanup(cleanup: () -> Unit) {
        cleanups.add(cleanup)
    }

    fun build() {
        unsubscribe()
        buildSerial = StarUIPanelPlugin.nextSerial()
        buildCount++
        StarUIContext.withTheme(theme) {
            StarUIContext.withScope(this) {
                StarUIContext.withPlugin(plugin) { builder() }
            }
        }
    }

    fun markDirty() {
        if (disposed) return
        if (!dirty) {
            dirty = true
            dirtyScopes.add(selfRef)
        }
        plugin.markDirtyScopeBelow()
    }

    /** Step 1 (brute force): drop every child and run the builder again. */
    fun rebuild() {
        if (disposed) return
        dirty = false
        val panel = plugin.panel
        disposeChildrenOf(panel)
        panel.clearChildren()
        plugin.clearChildLayoutData()
        runCleanups()
        build()
        plugin.requestLayout()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        dirty = false
        unsubscribe()
        runCleanups()
    }

    private fun unsubscribe() {
        for (state in reads) state.unsubscribe(selfRef)
        reads.clear()
    }

    private fun runCleanups() {
        if (cleanups.isEmpty()) return
        val pending = cleanups.toList()
        cleanups.clear()
        pending.forEach { it() }
    }

    private fun describe(): String =
        "$kind { ... } declared in $declaredIn, reading ${reads.joinToString { "UIState(${it.peek()})" }}"

    companion object {
        private val log: Logger = Global.getLogger(Scope::class.java)

        /** A scope that keeps dirtying itself (its builder writes a state it reads) is stopped after this many passes. */
        const val MAX_PASSES_PER_FLUSH = 8

        private val dirtyScopes = ArrayList<WeakReference<Scope>>()
        private var flushing = false

        val hasDirty: Boolean get() = dirtyScopes.isNotEmpty()

        /**
         * Rebuilds every dirty scope at or below [top], outermost first. A rebuilt scope disposes the scopes inside
         * it, so those are skipped. Builders that change state during the flush get further passes, up to a limit.
         */
        fun flush(top: StarUIPanelPlugin) {
            top.dirtyScopeBelow = false
            if (flushing) return
            flushing = true
            try {
                var pass = 0
                while (true) {
                    val batch = collectUnder(top)
                    if (batch.isEmpty()) break
                    if (++pass > MAX_PASSES_PER_FLUSH) {
                        for (scope in batch) {
                            scope.dirty = false
                            if (!scope.loopReported) {
                                scope.loopReported = true
                                log.error("StarUI: rebuild loop stopped after $MAX_PASSES_PER_FLUSH passes in one frame. " +
                                    "A builder must not change a UIState that it (or an enclosing builder) reads; " +
                                    "change state in event handlers instead. Offending scope: ${scope.describe()}")
                            }
                        }
                        break
                    }
                    for (scope in batch) if (!scope.disposed && scope.dirty) scope.rebuild()
                }
            } finally {
                flushing = false
                top.dirtyScopeBelow = false
            }
        }

        /** Dirty, live scopes whose container is [top]'s component or below it, nearest to [top] first. */
        private fun collectUnder(top: StarUIPanelPlugin): List<Scope> {
            if (dirtyScopes.isEmpty()) return emptyList()
            val topComponent = top.component
            val found = ArrayList<Pair<Scope, Int>>()
            val iterator = dirtyScopes.iterator()
            while (iterator.hasNext()) {
                val scope = iterator.next().get()
                if (scope == null || scope.disposed || !scope.dirty) {
                    iterator.remove()
                    continue
                }
                var depth = 0
                var node: UIComponentAPI? = scope.plugin.component
                while (node != null && node !== topComponent) {
                    node = node.parent
                    depth++
                }
                if (node == null) continue   // detached, or under another root: its own root flushes it
                // clear the "dirty below" marks on the way up so intermediate nodes do not flush again
                var marked: UIComponentAPI? = scope.plugin.component
                while (marked != null && marked !== topComponent) {
                    StarUIPanelPlugin.getAttached(marked)?.dirtyScopeBelow = false
                    marked = marked.parent
                }
                iterator.remove()
                found.add(Pair(scope, depth))
            }
            found.sortBy { it.second }
            // still dirty until rebuilt; a scope disposed by an outer rebuild is skipped by the caller
            return found.map { it.first }
        }

        /** Disposes all StarUI state under [panel]'s children (not [panel]'s own plugin). */
        fun disposeChildrenOf(panel: UIPanelAPI) {
            for (child in panel.getChildrenNonCopy().toList()) disposeTree(child)
        }

        /** Disposes [component]'s StarUI plugin and everything below it, and releases keyboard focus held inside. */
        fun disposeTree(component: UIComponentAPI) {
            if (component is TextFieldAPI && component.hasFocus()) {
                // readable engine method; a null event means "no listener callback". A focused field that is removed
                // from the tree would otherwise keep receiving every keystroke.
                (component as Any).invoke("releaseFocus", null)
            }
            if (component is UIPanelAPI) disposeChildrenOf(component)
            StarUIPanelPlugin.getAttached(component)?.disposeSelf()
        }

        // ---- deferred disposal for UI removed by clearChildren() from outside StarUI ----
        private val pendingDetached = ArrayList<WeakReference<UIComponentAPI>>()

        /** [child] was dropped by a `clearChildren()`; dispose it later unless it has been added back by then. */
        fun noteDetached(child: UIComponentAPI) {
            pendingDetached.add(WeakReference(child))
        }

        val hasPendingDetached: Boolean get() = pendingDetached.isNotEmpty()

        fun disposeDetachedPending() {
            if (pendingDetached.isEmpty()) return
            val pending = pendingDetached.toList()
            pendingDetached.clear()
            for (ref in pending) {
                val component = ref.get() ?: continue
                if (component.parent == null) disposeTree(component)
            }
        }
    }
}
