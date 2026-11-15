package org.starficz.starui.runtime

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.dumpLayout
import org.starficz.starui.vanilla.getChildrenNonCopy

/**
 * Writes every StarUI panel on screen to the game log, as [dumpLayout] text, when the system property [KEY] changes:
 * an inspection channel for tools outside the game (the starsector-hotreload skill's `hot.sh dump` sets it through its
 * agent) and for tests running inside it (`System.setProperty(KEY, "some id")`).
 *
 * The dump is one log entry:
 * ```
 * StarUI dump <id>: 2 StarUI panels on screen
 * --- 1/2: Box (in the screen panel)
 * Box 1600x900 at (0, 0) ...
 * --- 2/2: Layer (in the screen panel)
 * ...
 * StarUI dump <id> end
 * ```
 * A dialog's or holo's content is included under its holo, though it is not one of the holo's children.
 *
 * The property is absent in a normal game, so this costs a clock read per frame and does nothing else.
 */
internal object DebugDump {
    const val KEY = "starui.dump"
    private const val POLL_NANOS = 200_000_000L
    private var last: String? = System.getProperty(KEY)
    private var nextPoll = 0L

    /** Called from StarUI nodes' render; checks the property a few times a second and dumps after a change. */
    fun poll() {
        val now = System.nanoTime()
        if (now < nextPoll) return
        nextPoll = now + POLL_NANOS
        val current = System.getProperty(KEY) ?: return
        if (current == last) return
        last = current
        val text = try {
            dump(current)
        } catch (t: Throwable) {
            "StarUI dump $current failed: $t\nStarUI dump $current end"
        }
        Global.getLogger(DebugDump::class.java).info(text)
    }

    /** The dump's text for request [id]: every StarUI panel under [screen] (the screen panel; tests pass their root). */
    fun dump(id: String, screen: UIPanelAPI? = Global.getSettings().screenPanel): String {
        val roots = ArrayList<UIComponentAPI>()
        if (screen != null) findRoots(screen, roots, 0)
        val out = StringBuilder()
        out.append("StarUI dump ").append(id).append(": ").append(roots.size).append(" StarUI panel")
            .append(if (roots.size == 1) "" else "s").append(" on screen\n")
        if (screen == null) out.append("(no screen panel yet: the game is still loading)\n")
        roots.forEachIndexed { i, root ->
            out.append("--- ").append(i + 1).append('/').append(roots.size).append(": ")
                .append(StarUIPanelPlugin.getAttached(root)?.kind ?: root.javaClass.simpleName)
                .append(" (in ").append(parentName(root, screen)).append(")\n")
            out.append(root.dumpLayout())
        }
        out.append("StarUI dump ").append(id).append(" end")
        return out.toString()
    }

    /** The first StarUI panel on each branch below [component]: [dumpLayout] covers everything under it. */
    private fun findRoots(component: UIComponentAPI, out: MutableList<UIComponentAPI>, depth: Int) {
        if (StarUIPanelPlugin.getAttached(component) != null) { out += component; return }
        if (depth > 64 || component !is UIPanelAPI) return
        for (child in component.getChildrenNonCopy()) findRoots(child, out, depth + 1)
    }

    private fun parentName(root: UIComponentAPI, screen: UIPanelAPI?): String {
        val parent = root.parent
        return when {
            parent == null -> "no parent"
            parent === screen -> "the screen panel"
            else -> StarUIPanelPlugin.getAttached(parent)?.kind ?: "a vanilla panel"
        }
    }
}
