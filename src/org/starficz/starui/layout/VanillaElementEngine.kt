package org.starficz.starui.layout

import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.runtime.StarUIPanelPlugin

/**
 * Lays out a container that holds one vanilla component made for a given width: most TooltipMakerAPI content (tables,
 * grids, bars) is built at a width and cannot be resized afterwards. The component is made the first time the
 * container is measured, and made again whenever the width it is given changes, a call gives it new inputs
 * ([update]) or [invalidate] is called.
 *
 * [build] makes the component for a width; [afterBuild] and [afterLayout] let a caller (Table) add and place its own
 * children over it. Children other than the component are not touched.
 *
 * With [buildSized] set instead, the component is made at the whole size the container is given (vanilla content
 * laid out once at a fixed size: a sector map, a planet), and made again when either side changes. Such a container
 * has a bounded size, so measuring it makes nothing.
 */
internal class VanillaElementEngine(internal val plugin: StarUIPanelPlugin) : LayoutEngine {
    /** A fixed width to build at, or null to build at the width the container is given. */
    var fixedWidth: Float? = null
    /** The width to build at when the room is unbounded and there is no fixed width. */
    var fallbackWidth = 300f
    var build: (width: Float) -> UIComponentAPI = { throw IllegalStateException("StarUI: no vanilla element to build") }
    var afterBuild: ((UIComponentAPI) -> Unit)? = null
    var afterLayout: ((UIComponentAPI) -> Unit)? = null
    /** Makes the component for a width and height; replaces [build] when set. */
    var buildSized: ((width: Float, height: Float) -> UIComponentAPI)? = null
    /** Called when a new component replaces [current] (null the first time), before the old one is removed. */
    var onSwap: ((old: UIComponentAPI?, made: UIComponentAPI) -> Unit)? = null

    internal var current: UIComponentAPI? = null
        private set
    private var builtWidth = Float.NaN
    private var builtHeight = Float.NaN

    /**
     * Components made for widths a measure asked about, not attached. A container is often measured at a few widths
     * before its parent settles; making the component for each is unavoidable (vanilla builds at a width), but
     * swapping the attached one each time would ask for another layout every pass.
     */
    private val measured = LinkedHashMap<Float, UIComponentAPI>()

    /** What the last call was given (see [update]). */
    private var lastInputs: Any? = null

    /** State a caller keeps across its calls, for listeners on a component that outlives the call that made it. */
    var callerState: Any? = null

    /**
     * Called by every component call with what the component shows: it is made again on the next layout unless
     * [inputs] equal the last call's, so a builder running again for something else keeps it. Pass a fresh snapshot
     * (lists, copies), never a collection the caller may change in place. Null means "always make it again": content
     * read from live game objects (a stat, a fleet member, a person) can change without any argument changing.
     *
     * The inputs are what [build] holds anyway and replace the last call's, so comparing keeps nothing alive.
     */
    fun update(inputs: Any?) {
        val unchanged = inputs != null && current != null && inputs == lastInputs
        lastInputs = inputs
        if (!unchanged) invalidate()
    }

    /** Makes the component again on the next layout (its arguments changed). */
    fun invalidate() {
        builtWidth = Float.NaN
        builtHeight = Float.NaN
        measured.clear()
        plugin.requestLayout()
    }

    private fun widthFor(room: Float): Float =
        fixedWidth ?: if (room == Float.MAX_VALUE || room <= 0f) fallbackWidth else room

    /** The component for [width]: the attached one, a measured one, or a new one. Not attached here. */
    private fun forWidth(width: Float): UIComponentAPI {
        val existing = current
        if (existing != null && existing.parent === plugin.panel && width == builtWidth) return existing
        measured[width]?.let { return it }
        val made = build(width)
        measured[width] = made
        if (measured.size > 4) measured.remove(measured.keys.first())
        return made
    }

    private fun roomOr(room: Float, fallback: Float) = if (room == Float.MAX_VALUE || room <= 0f) fallback else room

    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        if (buildSized != null) return Pair(roomOr(maxWidth, fallbackWidth), roomOr(maxHeight, fallbackWidth))
        val made = forWidth(widthFor(maxWidth))
        return Pair(made.width, made.height)
    }

    override fun layout(assignedWidth: Float, assignedHeight: Float) {
        val sized = buildSized
        if (sized != null) {
            val w = roomOr(assignedWidth, fallbackWidth)
            val h = roomOr(assignedHeight, fallbackWidth)
            val existing = current
            if (existing == null || existing.parent !== plugin.panel || w != builtWidth || h != builtHeight) {
                attach(existing, sized(w, h))
                builtWidth = w
                builtHeight = h
            }
            current?.position?.inTL(0f, 0f)
            current?.let { afterLayout?.invoke(it) }
            return
        }
        val width = widthFor(assignedWidth)
        val made = forWidth(width)
        val existing = current
        if (made !== existing || existing?.parent !== plugin.panel) {
            measured.remove(width)
            attach(existing, made)
            builtWidth = width
        }
        made.position.inTL(0f, 0f)
        afterLayout?.invoke(made)
    }

    /** Puts [made] in the container in place of [existing]. */
    private fun attach(existing: UIComponentAPI?, made: UIComponentAPI) {
        onSwap?.invoke(existing, made)
        // swapped during this layout: the container's size already accounts for it, so no new layout is needed
        plugin.swappingChild = true
        try {
            if (existing != null && existing.parent === plugin.panel) plugin.panel.removeComponent(existing)
            plugin.panel.addComponent(made).inTL(0f, 0f)
        } finally {
            plugin.swappingChild = false
        }
        current = made
        afterBuild?.invoke(made)
    }
}
