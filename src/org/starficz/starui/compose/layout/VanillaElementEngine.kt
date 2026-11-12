package org.starficz.starui.compose.layout

import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.height
import org.starficz.starui.core.width

/**
 * Lays out a container that holds one vanilla component made for a given width: most TooltipMakerAPI content (tables,
 * grids, bars) is built at a width and cannot be resized afterwards. The component is made the first time the
 * container is measured, and made again whenever the width it is given changes or [invalidate] is called.
 *
 * [build] makes the component for a width; [afterBuild] and [afterLayout] let a caller (Table) add and place its own
 * children over it. Children other than the component are not touched.
 */
internal class VanillaElementEngine(private val plugin: StarUIPanelPlugin) : LayoutEngine {
    /** A fixed width to build at, or null to build at the width the container is given. */
    var fixedWidth: Float? = null
    /** The width to build at when the room is unbounded and there is no fixed width. */
    var fallbackWidth = 300f
    var build: (width: Float) -> UIComponentAPI = { throw IllegalStateException("StarUI: no vanilla element to build") }
    var afterBuild: ((UIComponentAPI) -> Unit)? = null
    var afterLayout: ((UIComponentAPI) -> Unit)? = null

    internal var current: UIComponentAPI? = null
        private set
    private var builtWidth = Float.NaN

    /** Makes the component again on the next layout (its arguments changed). */
    fun invalidate() {
        builtWidth = Float.NaN
        plugin.requestLayout()
    }

    private fun widthFor(room: Float): Float =
        fixedWidth ?: if (room == Float.MAX_VALUE || room <= 0f) fallbackWidth else room

    private fun ensure(width: Float): UIComponentAPI {
        val existing = current
        if (existing != null && existing.parent === plugin.panel && width == builtWidth) return existing
        if (existing != null && existing.parent === plugin.panel) plugin.panel.removeComponent(existing)
        val made = build(width)
        plugin.panel.addComponent(made).inTL(0f, 0f)
        current = made
        builtWidth = width
        afterBuild?.invoke(made)
        return made
    }

    override fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        val made = ensure(widthFor(maxWidth))
        return Pair(made.width, made.height)
    }

    override fun layout(assignedWidth: Float, assignedHeight: Float) {
        val made = ensure(widthFor(assignedWidth))
        made.position.inTL(0f, 0f)
        afterLayout?.invoke(made)
    }
}
