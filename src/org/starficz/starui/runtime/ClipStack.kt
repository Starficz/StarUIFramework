package org.starficz.starui.runtime

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import kotlin.math.roundToInt

/**
 * The visible rectangle while StarUI renders inside clipping and scrolling containers, so their children that are
 * entirely outside it are not drawn at all (render culling). A container whose overflow clips or scrolls pushes the
 * intersection of its visible rectangle with the current one before its children render and restores the previous
 * one after them. Screen coordinates, the same as [com.fs.starfarer.api.ui.PositionAPI].
 *
 * Only direct children of StarUI containers are culled: StarUI sized and placed them, so their rectangles are what
 * they draw. Vanilla widgets inside them are left to vanilla.
 *
 * It also holds the GL scissors of containers that clip one axis only (vanilla's own clipping, used for both axes,
 * cuts both): see [pushScissor].
 */
internal object ClipStack {
    /** Number of rectangles pushed. 0 outside every clipping container: then nothing is culled. */
    @JvmField
    var depth = 0

    /** True once any container clips without scrolling, so the input hook can leave at once until then. */
    @JvmField
    @Volatile
    var anyClipContainers = false

    /** Off only to measure what culling saves (tests). */
    @JvmField
    var cullingEnabled = true

    /**
     * Whether a StarUI panel culled in the last render also skips its input until it is drawn again, as vanilla's
     * scroll panel does for its off-screen items. Its advance keeps running (onAdvance, vanilla animations) unless its
     * container pauses off-screen children ([StarUIPanelPlugin.pausesOffscreenAdvance], lazy lists).
     */
    @JvmField
    var cullPerFrameWork = true

    private var left = FloatArray(8)
    private var bottom = FloatArray(8)
    private var right = FloatArray(8)
    private var top = FloatArray(8)

    /** Pushes the rectangle intersected with the current one; returns the depth to restore afterwards. */
    fun push(l: Float, b: Float, r: Float, t: Float): Int {
        val before = depth
        if (depth == left.size) {
            left = left.copyOf(depth * 2); bottom = bottom.copyOf(depth * 2)
            right = right.copyOf(depth * 2); top = top.copyOf(depth * 2)
        }
        if (depth > 0) {
            val i = depth - 1
            left[depth] = maxOf(l, left[i]); bottom[depth] = maxOf(b, bottom[i])
            right[depth] = minOf(r, right[i]); top[depth] = minOf(t, top[i])
        } else {
            left[depth] = l; bottom[depth] = b; right[depth] = r; top[depth] = t
        }
        depth++
        return before
    }

    fun restore(toDepth: Int) {
        if (toDepth in 0..depth) depth = toDepth
    }

    /** True when [component] is a StarUI container's child lying entirely outside the visible rectangle. */
    fun culls(component: UIComponentAPI): Boolean {
        if (depth == 0 || !cullingEnabled) return false
        val parent = component.parent as? CustomPanelAPI ?: return false
        if (parent.plugin !is StarUIPanelPlugin) return false
        val p = component.position
        val i = depth - 1
        val x = p.x
        val y = p.y
        return x > right[i] || x + p.width < left[i] || y > top[i] || y + p.height < bottom[i]
    }

    // ---- GL scissors for one-axis clips ----

    private class Scissor(
        val owner: StarUIPanelPlugin,
        val wasEnabled: Boolean, val oldX: Int, val oldY: Int, val oldW: Int, val oldH: Int,
        val x: Int, val y: Int, val w: Int, val h: Int
    )

    private val scissors = ArrayList<Scissor>()
    private val box = BufferUtils.createIntBuffer(16)

    /** The container whose one-axis scissor is active, or null. Its children re-apply it after they render. */
    @JvmField
    var scissorOwner: StarUIPanelPlugin? = null

    /**
     * Cuts drawing to the rectangle (UI coordinates; an infinite side does not cut), intersected with the scissor
     * already active. Used for a container that clips one axis only. A vanilla scissor inside (a scroll panel, a
     * ship display) replaces it while that one draws and switches scissoring off when it ends, so the owner's children
     * call [reapplyScissor] after rendering.
     */
    fun pushScissor(owner: StarUIPanelPlugin, l: Float, b: Float, r: Float, t: Float) {
        val wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        box.clear()
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box)
        val ox = box.get(0); val oy = box.get(1); val ow = box.get(2); val oh = box.get(3)
        val scale = Global.getSettings().screenScaleMult
        fun px(v: Float, low: Boolean) = when {
            v == -Float.MAX_VALUE -> if (low) -1_000_000 else 1_000_000
            v == Float.MAX_VALUE -> 1_000_000
            else -> (v * scale).roundToInt()
        }
        var x0 = px(l, true); var y0 = px(b, true); var x1 = px(r, false); var y1 = px(t, false)
        if (wasEnabled) {
            x0 = maxOf(x0, ox); y0 = maxOf(y0, oy); x1 = minOf(x1, ox + ow); y1 = minOf(y1, oy + oh)
        } else {
            val (sw, sh) = screenPixels()
            x0 = maxOf(x0, 0); y0 = maxOf(y0, 0); x1 = minOf(x1, sw); y1 = minOf(y1, sh)
        }
        val s = Scissor(owner, wasEnabled, ox, oy, ow, oh, x0, y0, maxOf(0, x1 - x0), maxOf(0, y1 - y0))
        scissors.add(s)
        scissorOwner = owner
        apply(s)
    }

    fun popScissor() {
        if (scissors.isEmpty()) return
        val s = scissors.removeAt(scissors.size - 1)
        GL11.glScissor(s.oldX, s.oldY, s.oldW, s.oldH)
        if (s.wasEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST) else GL11.glDisable(GL11.GL_SCISSOR_TEST)
        scissorOwner = scissors.lastOrNull()?.owner
    }

    /** Puts the active one-axis scissor back (after a child whose own scissor replaced it). */
    fun reapplyScissor() {
        scissors.lastOrNull()?.let { apply(it) }
    }

    private fun apply(s: Scissor) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(s.x, s.y, s.w, s.h)
    }

    private fun screenPixels(): Pair<Int, Int> {
        val scale = Global.getSettings().screenScaleMult
        return Pair((Global.getSettings().screenWidth * scale).roundToInt(), (Global.getSettings().screenHeight * scale).roundToInt())
    }
}
