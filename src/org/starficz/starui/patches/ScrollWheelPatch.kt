package org.starficz.starui.patches

import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.getChildrenNonCopy
import patchlib.api.context.AfterContext
import patchlib.api.context.BeforeContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.match.MethodType
import patchlib.api.match.StaticType
import patchlib.api.patch.After
import patchlib.api.patch.Before
import patchlib.api.patch.Patch
import patchlib.api.query.MethodQuery
import kotlin.math.sign

/**
 * The mouse wheel for every scroll panel in the game (StarUI's and vanilla's): nested panels and horizontal scrolling.
 *
 * Vanilla's scroll panel handles the wheel before it passes input to its children, and only vertically, so a panel
 * inside another never gets the wheel and horizontal content cannot be wheeled at all. Before a panel handles its
 * input, this finds the scroll panels under the cursor from that panel down and picks the deepest one that can still
 * move in the wheel's direction (at the end of an inner list the wheel goes on to the next panel out):
 * - on a panel whose content is taller than it, the wheel scrolls vertically, with vanilla's own code and feel: when
 *   the target is a panel further in, the event is hidden from this one (moved off screen for the duration of the
 *   call) so the target's own vanilla handling takes it when input reaches it;
 * - on a panel whose content is only wider than it, the wheel scrolls horizontally, and Shift+wheel does on a panel
 *   that overflows both ways: done here through `setXOffset` (vanilla has no horizontal wheel), and the event is
 *   consumed.
 * Horizontal wheel scrolling eases like vanilla's vertical one (the content container's own easing, given a target).
 * A right-button press (right-drag pans) goes to the deepest panel under the cursor with something to pan, not the
 * outermost one, by hiding it from the panels outside.
 * A panel with nothing nested and nothing wider than it behaves exactly as before (same step, Shift = 5x).
 * StarUI panels whose theme turns the wheel off are skipped; other panels are assumed to take the wheel.
 *
 * `processScrollInputOnly` (two parameters) is a readable engine method on the class implementing ScrollPanelAPI,
 * not an API method: an inferred target, the first to re-check after a game update. It is the only place the wheel
 * is read (the panel's own input and the trade screen's cargo grids both call it). The events' `setX(int)` /
 * `setY(int)` are readable engine methods. Handlers leave at once when no wheel event is in the list.
 */
// 0.98.5a-RC4 Windows: com.fs.starfarer.ui.g.processScrollInputOnly(com.fs.starfarer.util.oOOO.new, boolean)
@Patch(
    target = ClassMatch(
        subtype = ScrollPanelAPI::class,
        methodMatches = [
            MethodMatch(methodName = "processScrollInputOnly", parameterCount = 2, returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE)
        ]
    )
)
internal object ScrollWheelPatch {
    private const val HIDDEN = "staruilib.wheelHidden"
    private const val OFF_SCREEN = Int.MAX_VALUE
    private val SET_X = MethodQuery.create().methodName("setX").parameterTypes(Int::class.javaPrimitiveType).build()
    private val SET_Y = MethodQuery.create().methodName("setY").parameterTypes(Int::class.javaPrimitiveType).build()
    // the content container's own offset methods (readable names on the scroll panel's inner content class)
    private val FLOAT = Float::class.javaPrimitiveType
    private val CONTENT_SET_OFFSET = MethodQuery.create().methodName("setOffset").parameterTypes(FLOAT, FLOAT).build()
    private val CONTENT_FORCE_OFFSET = MethodQuery.create().methodName("forceOffset").parameterTypes(FLOAT, FLOAT).build()
    private val CONTENT_X_OFFSET = MethodQuery.create().methodName("getXOffset").parameterCount(0).build()
    private val CONTENT_Y_OFFSET = MethodQuery.create().methodName("getYOffset").parameterCount(0).build()

    /**
     * Scrolls [panel] to [x] the way vanilla's wheel scrolls vertically: the panel's own offset (what its scrollbar and
     * clamping read) is set at once, and the content container is given [x] as its target, which its advance eases
     * towards (vanilla's `amount * (3 * distance + 200)` per frame, on both axes). The API setter also moves the
     * content at once, so the content is put back where it is drawn before it gets the target.
     */
    private fun easeX(context: BeforeContext, panel: ScrollPanelAPI, x: Float) {
        val content = panel.contentContainer
        val drawnX = context.getMethod(CONTENT_X_OFFSET, content).call<Float>()
        val drawnY = context.getMethod(CONTENT_Y_OFFSET, content).call<Float>()
        panel.setXOffset(x)
        context.getMethod(CONTENT_FORCE_OFFSET, content).call<Any?>(drawnX, drawnY)
        context.getMethod(CONTENT_SET_OFFSET, content).call<Any?>(x, panel.yOffset)
    }

    /** Vanilla's wheel step, capped at a third of the panel. */
    private fun step(size: Float): Float = minOf(100f, size * 0.33f)

    @JvmStatic
    @Before(target = MethodMatch(methodName = "processScrollInputOnly", parameterCount = 2, returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun routeWheel(context: BeforeContext) {
        val events = context.getArg(0) as? List<*> ?: return
        var hasWork = false
        for (e in events) {
            val event = e as? InputEventAPI ?: continue
            if (!event.isConsumed && (event.isMouseScrollEvent || event.isRMBDownEvent)) { hasWork = true; break }
        }
        if (!hasWork || !context.isMostDerivedCall) return
        val self = context.self as? ScrollPanelAPI ?: return

        var hidden: ArrayList<Any>? = null
        for (e in events) {
            val event = e as? InputEventAPI ?: continue
            if (event.isConsumed) continue
            if (event.isRMBDownEvent) {
                // Right-drag pans the panel that gets the press. Vanilla gives it to the outermost one: hand it to the
                // deepest panel under the cursor that has anything to pan instead.
                val x = event.x
                val y = event.y
                if (x == OFF_SCREEN || !self.contains(x.toFloat(), y.toFloat())) continue
                val chain = ArrayList<ScrollPanelAPI>()
                collectUnder(self.contentContainer, x.toFloat(), y.toFloat(), chain)
                if (chain.any { overflows(it) }) {
                    if (hidden == null) hidden = ArrayList()
                    hidden.add(event); hidden.add(x); hidden.add(y)
                    context.getMethod(SET_X, event).call<Any?>(OFF_SCREEN)
                    context.getMethod(SET_Y, event).call<Any?>(OFF_SCREEN)
                }
                continue
            }
            if (!event.isMouseScrollEvent) continue
            val x = event.x
            val y = event.y
            if (x == OFF_SCREEN || !self.contains(x.toFloat(), y.toFloat())) continue
            val notches = event.eventValue
            if (notches == 0) continue
            val shift = event.isShiftDown

            val chain = ArrayList<ScrollPanelAPI>()
            chain.add(self)
            collectUnder(self.contentContainer, x.toFloat(), y.toFloat(), chain)
            var target: ScrollPanelAPI? = null
            var horizontal = false
            for (i in chain.indices.reversed()) {
                val panel = chain[i]
                val axis = wheelAxis(panel, notches, shift) ?: continue
                target = panel
                horizontal = axis
                break
            }
            if (target == null) continue   // nothing can move: vanilla's (no-op) handling stays as it was

            if (horizontal) {
                val view = target.position
                val content = target.contentContainer.position
                // Shift chose the axis when the panel overflows both ways; otherwise it speeds up, as vanilla's does
                val boost = if (shift && content.height <= view.height + 0.5f) 5f else 1f
                val minX = minOf(0f, view.width - content.width)
                val newX = (target.xOffset + step(view.width) * boost * sign(notches.toFloat())).coerceIn(minX, 0f)
                easeX(context, target, newX)
                event.consume()
            } else if (target !== self) {
                if (hidden == null) hidden = ArrayList()
                hidden.add(event); hidden.add(x); hidden.add(y)
                context.getMethod(SET_X, event).call<Any?>(OFF_SCREEN)
                context.getMethod(SET_Y, event).call<Any?>(OFF_SCREEN)
            }
            // target === self, vertical: vanilla's own handling, unchanged
        }
        if (hidden != null) context.setLocal(HIDDEN, hidden)
    }

    @JvmStatic
    @After(target = MethodMatch(methodName = "processScrollInputOnly", parameterCount = 2, returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun restoreHidden(context: AfterContext) {
        val hidden = context.getLocal<ArrayList<Any>>(HIDDEN) ?: return
        var i = 0
        while (i < hidden.size) {
            val event = hidden[i] as InputEventAPI
            if (!event.isConsumed) {
                context.getMethod(SET_X, event).call<Any?>(hidden[i + 1])
                context.getMethod(SET_Y, event).call<Any?>(hidden[i + 2])
            }
            i += 3
        }
    }

    /**
     * The axis this wheel event would scroll [panel] on (true = horizontal), or null when the panel does not take the
     * wheel, has nothing to scroll, or is already at its end in the wheel's direction. Wheel up (notches > 0) moves
     * towards the top or the left.
     */
    private fun wheelAxis(panel: ScrollPanelAPI, notches: Int, shift: Boolean): Boolean? {
        if (StarUIPanelPlugin.getAttached(panel)?.wheelEnabled == false) return null
        val view = panel.position
        val content = panel.contentContainer.position
        val overY = content.height > view.height + 0.5f
        val overX = content.width > view.width + 0.5f
        val horizontal = when {
            shift && overX && overY -> true
            overY -> false
            overX -> true
            else -> return null
        }
        return if (horizontal) {
            val minX = view.width - content.width
            val canMove = if (notches < 0) panel.xOffset > minX + 0.5f else panel.xOffset < -0.5f
            if (canMove) true else null
        } else {
            val maxY = content.height - view.height
            val canMove = if (notches > 0) panel.yOffset > 0.5f else panel.yOffset < maxY - 0.5f
            if (canMove) false else null
        }
    }

    private fun overflows(panel: ScrollPanelAPI): Boolean {
        val view = panel.position
        val content = panel.contentContainer.position
        return content.height > view.height + 0.5f || content.width > view.width + 0.5f
    }

    /** Adds the scroll panels under (x, y) inside [panel], outermost first, following the topmost child each step. */
    private fun collectUnder(panel: UIPanelAPI, x: Float, y: Float, out: MutableList<ScrollPanelAPI>) {
        var current: UIPanelAPI = panel
        while (true) {
            val children = current.getChildrenNonCopy()
            var next: UIComponentAPI? = null
            for (i in children.indices.reversed()) {
                val child = children[i]
                if (child.contains(x, y)) { next = child; break }
            }
            when (next) {
                is ScrollPanelAPI -> { out.add(next); current = next.contentContainer }
                is UIPanelAPI -> current = next
                else -> return
            }
        }
    }

    private fun UIComponentAPI.contains(x: Float, y: Float): Boolean {
        val p = position
        return x >= p.x && x <= p.x + p.width && y >= p.y && y <= p.y + p.height
    }
}
