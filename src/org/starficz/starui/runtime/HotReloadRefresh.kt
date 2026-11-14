package org.starficz.starui.runtime

import com.fs.starfarer.api.Global
import java.lang.ref.WeakReference

/**
 * Rebuilds every live StarUI scope after a hot reload, so UI on screen shows the reloaded code at once.
 *
 * The starsector-hotreload agent sets the system property [KEY] to "0" before any mod loads and raises it by one on
 * every reload that changed code. When the property is absent (every normal game), [active] is false and nothing
 * here does anything: no scope is tracked and [poll] returns at once.
 *
 * The scopes are tracked beside their state reads, not as a read: a scope that read nothing stays non-reactive, so
 * StarUI behaves the same with and without the agent.
 */
internal object HotReloadRefresh {
    const val KEY = "starsector.hotreload.generation"
    private const val POLL_NANOS = 200_000_000L

    /** True in a game started with the hot-reload agent. A var so tests can switch it. */
    @Volatile
    var active: Boolean = System.getProperty(KEY) != null

    private val scopes = ArrayList<WeakReference<Scope>>()
    private var pruneAt = 256
    private var last: String? = System.getProperty(KEY)
    private var nextPoll = 0L

    /** Called by every new scope. */
    fun track(scope: Scope) {
        if (!active) return
        if (scopes.size >= pruneAt) {
            scopes.removeAll { it.get().let { s -> s == null || s.disposed } }
            pruneAt = maxOf(256, scopes.size * 2)
        }
        scopes.add(scope.selfRef)
    }

    /** Called from StarUI nodes' render; checks the property a few times a second and rebuilds after a change. */
    fun poll() {
        if (!active) return
        val now = System.nanoTime()
        if (now < nextPoll) return
        nextPoll = now + POLL_NANOS
        val current = System.getProperty(KEY)
        if (current == last) return
        last = current
        refreshAll()
    }

    /** Marks every live scope dirty: each rebuilds on the next frame, outermost first, with the code now loaded. */
    fun refreshAll() {
        val live = scopes.mapNotNull { it.get()?.takeUnless { s -> s.disposed } }
        scopes.retainAll { it.get().let { s -> s != null && !s.disposed } }
        Global.getLogger(HotReloadRefresh::class.java).info("StarUI: hot reload $last, rebuilding ${live.size} UI scopes")
        live.forEach { it.markDirty() }
    }

    /** For tests: forget the tracked scopes and the last value seen. */
    fun resetForTest(isActive: Boolean) {
        active = isActive
        scopes.clear()
        last = System.getProperty(KEY)
        nextPoll = 0L
    }

    /** For tests: let the next [poll] read the property at once. */
    fun pollNowForTest() {
        nextPoll = 0L
    }
}
