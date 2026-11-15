package org.starficz.starui.runtime

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.dumpLayout
import org.starficz.starui.findAll
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
 * The same way, [FIND_KEY] set to `<id>\t<kind>\t<mode>\t<text>` logs every component on screen, vanilla's included,
 * whose text or key is `text` (`mode` `exact`; `contains`: a case-insensitive part of it), of kind `kind` (empty: any),
 * with where a click on its shown part lands, in screen pixels from the top left (`hot.sh find` and `hot.sh click`):
 * ```
 * StarUI find <id>: 1 match (1 shown) for "Confirm"
 * Button "Confirm" 150x25 at (725, 600) shown click=(800, 612) path=Layer > Box > Holo > Box > Row > Button "Confirm"
 * StarUI find <id> end
 * ```
 *
 * The properties are absent in a normal game, so this costs a clock read per frame and does nothing else.
 */
internal object DebugDump {
    const val KEY = "starui.dump"
    const val FIND_KEY = "starui.find"
    private const val POLL_NANOS = 200_000_000L
    private var last: String? = System.getProperty(KEY)
    private var lastFind: String? = System.getProperty(FIND_KEY)
    private var nextPoll = 0L

    /** Called from StarUI nodes' render; checks the property a few times a second and dumps after a change. */
    fun poll() {
        val now = System.nanoTime()
        if (now < nextPoll) return
        nextPoll = now + POLL_NANOS
        val current = System.getProperty(KEY)
        if (current != null && current != last) {
            last = current
            val text = try {
                dump(current)
            } catch (t: Throwable) {
                "StarUI dump $current failed: $t\nStarUI dump $current end"
            }
            Global.getLogger(DebugDump::class.java).info(text)
        }
        val request = System.getProperty(FIND_KEY)
        if (request != null && request != lastFind) {
            lastFind = request
            val id = request.substringBefore('\t')
            val text = try {
                find(request)
            } catch (t: Throwable) {
                "StarUI find $id failed: $t\nStarUI find $id end"
            }
            Global.getLogger(DebugDump::class.java).info(text)
        }
    }

    /** The answer to a find request `<id>\t<kind>\t<mode>\t<text>`, searched under [screen]. */
    fun find(request: String, screen: UIPanelAPI? = Global.getSettings().screenPanel): String {
        val parts = request.split('\t', limit = 4)
        val id = parts[0]
        val kind = parts.getOrNull(1).orEmpty()
        val contains = parts.getOrNull(2) == "contains"
        val wanted = parts.getOrNull(3).orEmpty()
        fun matches(value: String?) = value != null &&
            (if (contains) value.contains(wanted, ignoreCase = true) else value == wanted)
        val found = screen?.findAll { f ->
            (kind.isEmpty() || f.kind == kind) && (matches(f.text) || matches(f.key?.toString()))
        }.orEmpty()
        val shown = found.count { it.isShown }
        val scale = Global.getSettings().screenScaleMult
        val out = StringBuilder()
        out.append("StarUI find ").append(id).append(": ").append(found.size)
            .append(if (found.size == 1) " match" else " matches")
            .append(" (").append(shown).append(" shown) for \"").append(wanted).append('"')
        if (kind.isNotEmpty()) out.append(" of kind ").append(kind)
        out.append('\n')
        if (screen == null) out.append("(no screen panel yet: the game is still loading)\n")
        for (f in found) {
            out.append(f)
            if (f.isShown) {
                out.append(" click=(").append(Math.round(f.visibleCenterX * scale)).append(", ")
                    .append(Math.round(f.visibleCenterY * scale)).append(')')
            }
            out.append(" path=").append(f.path).append('\n')
        }
        out.append("StarUI find ").append(id).append(" end")
        return out.toString()
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
