package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.apache.log4j.Logger
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.getChildrenNonCopy
import org.starficz.starui.core.parent
import java.lang.ref.WeakReference
import java.util.IdentityHashMap

/**
 * One container's builder block, kept so it can run again.
 *
 * Every StarUI container (CustomPanel, Box, Column, Row, ScrollPanel content) owns a Scope. While the builder runs,
 * each [UIState.value] read is recorded; when one of those states changes the scope is marked dirty, and on the next
 * frame the builder runs again. That is what makes a plain `if` or `for` inside a builder follow its state.
 *
 * A rebuild does not throw the old children away. Each component call in the builder first asks for an old child of
 * the same kind (the next unused one, or the one with the same `key(...)`) and updates it in place; only calls with
 * no match create something new, and only old children nobody asked for are removed. Native objects therefore keep
 * their identity and their own state (scroll position, caret, keyboard focus, hover) across rebuilds.
 *
 * Timing (see docs/reactive-scopes-plan.md, 5.2): a write only sets flags. Rebuilds happen from the `renderBelow` or
 * `advance` callback of a StarUI node above the scope, so never while input is being dispatched, never from campaign
 * scripts, and never for UI that is not attached to anything that renders or advances.
 */
internal class Scope(
    val plugin: StarUIPanelPlugin,
    private var theme: StarUITheme,
    private var kind: String,
    private var declaredIn: String,
    private var builder: () -> Unit
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

    /** The enclosing builder ran again and declared this container anew: take its new block and theme. */
    fun adopt(theme: StarUITheme, kind: String, declaredIn: String, builder: () -> Unit) {
        this.theme = theme
        this.kind = kind
        this.declaredIn = declaredIn
        this.builder = builder
    }

    fun build() {
        unsubscribe()
        buildSerial = StarUIPanelPlugin.nextSerial()
        buildCount++
        StarUIContext.withTheme(theme) {
            StarUIContext.withScope(this) {
                StarUIContext.withKey(null) {   // a key(...) around this container does not apply to its children
                    StarUIContext.withPlugin(plugin) { builder() }
                }
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

    /** Runs the builder again, reusing the old children where the new calls match them. */
    fun rebuild() {
        if (disposed) return
        dirty = false
        val panel = plugin.panel
        val cursor = ReuseCursor(panel, plugin.childRecords)
        plugin.clearChildLayoutData()
        runCleanups()
        plugin.reuseCursor = cursor
        try {
            build()
        } finally {
            plugin.reuseCursor = null
        }
        cursor.removeUnused()
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

    internal fun runCleanups() {
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
         * Rebuilds every dirty scope at or below [top], outermost first. An outer rebuild re-runs the builders of the
         * containers it keeps, so those are no longer dirty when their turn comes. Builders that change state during
         * the flush get further passes, up to a limit.
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
            // still dirty until rebuilt; a scope disposed or already rebuilt by an outer one is skipped by the caller
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

/**
 * What a container remembers about one child a builder made: enough to recognise it when the builder runs again.
 * [props] are the arguments that cannot be changed on a live native object (a button's font, say); a call whose
 * props differ gets a new object. [handle] is what the component function returned (the native object or its
 * wrapper), handed out again on reuse so references held by user code stay valid.
 */
internal class ChildRecord(val kind: String, val key: Any?, val props: Any?, val handle: Any)

/** The old children of a container during one rebuild: handed out to matching calls, the rest removed at the end. */
internal class ReuseCursor(private val panel: UIPanelAPI, oldRecords: Map<UIComponentAPI, ChildRecord>) {
    private val records = IdentityHashMap<UIComponentAPI, ChildRecord>()
    private val unkeyed = HashMap<String, ArrayList<UIComponentAPI>>()
    private val keyed = HashMap<Pair<String, Any>, UIComponentAPI>()
    private val unused = LinkedHashSet<UIComponentAPI>()

    init {
        for (child in panel.getChildrenNonCopy()) {
            unused.add(child)
            val record = oldRecords[child] ?: continue   // not made by a component function: never reused
            records[child] = record
            if (record.key != null) keyed[Pair(record.kind, record.key)] = child
            else unkeyed.getOrPut(record.kind) { ArrayList() }.add(child)
        }
    }

    fun isFor(target: UIPanelAPI): Boolean = target === panel

    /**
     * The old child for a call of [kind] with [key] and [props], or null. With a key: the old child that had it.
     * Without: the first unused old child of that kind (so unkeyed children are matched in order). In both cases
     * only when the unchangeable props are equal. The child is moved to the end of the child list, which keeps the
     * list in call order and makes it "the last child", exactly like a freshly added one.
     */
    fun take(kind: String, key: Any?, props: Any?): ChildRecord? {
        val child: UIComponentAPI = if (key != null) {
            val candidate = keyed.remove(Pair(kind, key)) ?: return null
            if (records[candidate]?.props != props) return null
            candidate
        } else {
            val queue = unkeyed[kind] ?: return null
            val index = queue.indexOfFirst { records[it]?.props == props }
            if (index < 0) return null
            queue.removeAt(index)
        }
        unused.remove(child)
        StarUIPanelPlugin.getAttached(child)?.resetForReuse()
        panel.bringComponentToTop(child)
        return records[child]
    }

    /** Removes and disposes the old children no call asked for. */
    fun removeUnused() {
        for (child in unused) {
            if (child.parent === panel) panel.removeComponent(child)
            Scope.disposeTree(child)
        }
        unused.clear()
    }
}

/** One component call's place in its container: the old child to reuse (if any) and how to remember the result. */
internal class ChildSlot(
    private val container: StarUIPanelPlugin?,
    private val kind: String,
    private val key: Any?,
    private val props: Any?,
    private val record: ChildRecord?
) {
    /** The value the component function returned for the reused child last time, or null to create a new one. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> reused(): T? = record?.handle as T?

    val isReuse: Boolean get() = record != null

    /** Remembers [component] (the native object) and [handle] (what the function returns) for the next rebuild. */
    fun remember(component: UIComponentAPI, handle: Any) {
        if (container != null) container.childRecords[component] = ChildRecord(kind, key, props, handle)
        else StarUIPanelPlugin.getAttached(component)?.kind = kind
    }
}
