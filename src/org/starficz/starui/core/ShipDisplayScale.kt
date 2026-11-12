package org.starficz.starui.core

import java.util.WeakHashMap

/**
 * Exact scales for vanilla ship displays, read by ShipDisplayExactScalePatch while the display renders.
 *
 * A static map rather than PatchLib's per-instance `getData()`: the scale is set from outside any patch (when a
 * StarUI caller sets `overwriteScale`), and `getData()` is reachable only inside a handler. Weak keys, so a display
 * that is thrown away is not kept alive. Only the render thread reads and writes it.
 */
internal object ShipDisplayScale {
    private val scales = WeakHashMap<Any, Float>()

    fun of(display: Any?): Float? = if (display == null || scales.isEmpty()) null else scales[display]

    fun set(display: Any, scale: Float?) {
        if (scale == null) scales.remove(display) else scales[display] = scale
    }
}
