package org.starficz.starui.patches

import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.AxisOverflow
import org.starficz.starui.runtime.ClipStack
import org.starficz.starui.runtime.StarUIPanelPlugin
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

/**
 * Mouse input on the cut-off part of a clipping container does not reach its children: while they process input,
 * mouse events outside the container's visible rectangle are moved far off screen, and put back afterwards. This is
 * what vanilla's scroll panel does for its content (so scrolling containers need nothing here). Nested clips
 * intersect by themselves: an event the outer one moved is outside the inner one too.
 *
 * `processInput` with one parameter is matched as the lifecycle patch matches it (readable engine name, inferred).
 * The events' `setX(int)` / `setY(int)` are readable engine methods (InputEventAPI has getters only), found with
 * PatchLib's reflection. Handlers leave at once until a clipping container exists, and act only on StarUI panels
 * whose overflow clips without scrolling.
 */
// 0.98.5a-RC4 Windows: processInput is declared on com.fs.starfarer.ui.R; events are com.fs.starfarer.util.oOOO.C
@Patch(
    target = ClassMatch(
        subtype = UIComponentAPI::class,
        methodMatches = [
            MethodMatch(methodName = "processInput", parameterCount = 1, returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE)
        ]
    )
)
internal object ClipInputPatch {
    private const val MOVED = "staruilib.clipMovedEvents"
    private const val OFF_SCREEN = Int.MAX_VALUE
    private val SET_X = MethodQuery.create().methodName("setX").parameterTypes(Int::class.javaPrimitiveType).build()
    private val SET_Y = MethodQuery.create().methodName("setY").parameterTypes(Int::class.javaPrimitiveType).build()

    @JvmStatic
    @Before(target = MethodMatch(methodName = "processInput", parameterCount = 1, returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun hideClippedInput(context: BeforeContext) {
        if (!ClipStack.anyClipContainers) return
        val self = context.self as? CustomPanelAPI ?: return
        val plugin = self.plugin as? StarUIPanelPlugin ?: return
        val overflow = plugin.overflow
        if (!overflow.clips || plugin.scrollHost != null || !context.isMostDerivedCall) return
        val events = context.getArg(0) as? List<*> ?: return
        val p = self.position
        val clipsX = overflow.horizontal != AxisOverflow.Visible
        val clipsY = overflow.vertical != AxisOverflow.Visible
        var moved: ArrayList<Any>? = null
        for (e in events) {
            val event = e as? InputEventAPI ?: continue
            if (event.isConsumed || !event.isMouseEvent) continue
            val x = event.x
            val y = event.y
            if (x == OFF_SCREEN) continue
            val outside = (clipsX && (x < p.x || x > p.x + p.width)) || (clipsY && (y < p.y || y > p.y + p.height))
            if (!outside) continue
            if (moved == null) moved = ArrayList()
            moved.add(event); moved.add(x); moved.add(y)
            context.getMethod(SET_X, event).call<Any?>(OFF_SCREEN)
            context.getMethod(SET_Y, event).call<Any?>(OFF_SCREEN)
        }
        if (moved != null) context.setLocal(MOVED, moved)
    }

    @JvmStatic
    @After(target = MethodMatch(methodName = "processInput", parameterCount = 1, returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun restoreClippedInput(context: AfterContext) {
        if (!ClipStack.anyClipContainers) return
        val moved = context.getLocal<ArrayList<Any>>(MOVED) ?: return
        var i = 0
        while (i < moved.size) {
            val event = moved[i] as InputEventAPI
            if (!event.isConsumed) {   // a consumed event's data may no longer be touched (vanilla skips them too)
                context.getMethod(SET_X, event).call<Any?>(moved[i + 1])
                context.getMethod(SET_Y, event).call<Any?>(moved[i + 2])
            }
            i += 3
        }
    }
}
