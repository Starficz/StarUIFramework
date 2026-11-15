package org.starficz.starui.patches

import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.Injections
import org.starficz.starui.runtime.ClipStack
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.window.Layers
import patchlib.api.context.AfterContext
import patchlib.api.context.BeforeContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.match.MethodType
import patchlib.api.match.StaticType
import patchlib.api.patch.After
import patchlib.api.patch.Before
import patchlib.api.patch.Patch

/**
 * Attaches StarUI lifecycle hooks (`renderBelow`, `render`, `advance`, `processInput`, `positionChanged`)
 * directly to Starsector's root UI component implementations so that every [UIComponentAPI] in the game
 * natively supports all StarUI modifiers without needing wrapper [CustomPanelAPI] containers.
 *
 * The render hook also culls: inside a StarUI container whose overflow clips or scrolls, a child of a StarUI
 * container that lies entirely outside the visible rectangle ([ClipStack]) is not rendered (its `render` body is
 * skipped, as vanilla's scroll panel skips its off-screen items). Advance and input still run for it.
 */
// 0.98a-RC8 Windows: matches com.fs.starfarer.ui.OOOo (UIComponent_0) and com.fs.starfarer.ui.d (Label)
@Patch(
    target = ClassMatch(
        subtype = UIComponentAPI::class,
        methodMatches = [
            MethodMatch(methodName = "render", parameterTypes = [Float::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE),
            MethodMatch(methodName = "advance", parameterTypes = [Float::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE),
            MethodMatch(methodName = "sizeChanged", parameterTypes = [Float::class, Float::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE),
            MethodMatch(methodName = "locationChanged", parameterCount = 0, returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE)
        ]
    )
)
internal object UIComponentLifecyclePatch {
    /** Set by the render @Before on a call it culled, so the @After skips the plugin's render too. */
    private const val CULLED = "staruilib.culled"

    @JvmStatic
    @Before(
        target = MethodMatch(
            methodName = "render",
            parameterTypes = [Float::class],
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun onRenderBefore(context: BeforeContext) {
        // a panel whose clearChildren() dropped an inject() container gets it back before its children draw
        if (Injections.hasPending) Injections.beforeRender(context.self)
        if (Layers.hasPending) Layers.beforeRender(context.self)
        if (ClipStack.depth == 0 && !StarUIPanelPlugin.hasAttachedPlugins) return
        val self = context.self as? UIComponentAPI ?: return
        if (self is CustomPanelAPI) {
            // a super call inside a body that runs was not culled either: no most-derived check needed here
            val plugin = self.plugin as? StarUIPanelPlugin
            if (ClipStack.depth > 0 && ClipStack.culls(self)) {
                plugin?.culled = true
                context.setLocal(CULLED, true)
                context.skipOriginal(null)
            } else {
                plugin?.culled = false
            }
            return
        }
        if (ClipStack.depth > 0 && ClipStack.culls(self)) {
            context.setLocal(CULLED, true)
            context.skipOriginal(null)
            return
        }
        if (!StarUIPanelPlugin.hasAttachedPlugins || !context.isMostDerivedCall) return
        val plugin = StarUIPanelPlugin.getAttached(self) ?: return
        val alphaMult = context.getArg(0) as? Float ?: return
        plugin.renderBelow(alphaMult)
    }

    @JvmStatic
    @After(
        target = MethodMatch(
            methodName = "render",
            parameterTypes = [Float::class],
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun onRenderAfter(context: AfterContext) {
        val scissorOwner = ClipStack.scissorOwner
        if (scissorOwner == null && !StarUIPanelPlugin.hasAttachedPlugins) return
        if (!context.isMostDerivedCall) return
        val self = context.self as? UIComponentAPI ?: return
        // a vanilla scissor inside this child (scroll panel, ship display) may have replaced a one-axis clip's
        if (scissorOwner != null && (self.parent as? CustomPanelAPI)?.plugin === scissorOwner) ClipStack.reapplyScissor()
        if (!StarUIPanelPlugin.hasAttachedPlugins || self is CustomPanelAPI) return
        if (ClipStack.depth > 0 && context.getLocal<Boolean>(CULLED) == true) return
        val plugin = StarUIPanelPlugin.getAttached(self) ?: return
        val alphaMult = context.getArg(0) as? Float ?: return
        plugin.render(alphaMult)
    }

    /** A StarUI panel culled in the last render skips its advance when its container pauses off-screen children. */
    @JvmStatic
    @Before(target = MethodMatch(methodName = "advance", parameterTypes = [Float::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun onAdvanceBefore(context: BeforeContext) {
        if (!ClipStack.cullPerFrameWork) return
        val self = context.self as? CustomPanelAPI ?: return
        val plugin = self.plugin as? StarUIPanelPlugin ?: return
        if (!plugin.culled) return
        val container = (self.parent as? CustomPanelAPI)?.plugin as? StarUIPanelPlugin ?: return
        if (container.pausesOffscreenAdvance) context.skipOriginal(null)
    }

    /** ... and its input, unless it still thinks it is hovered (so it gets the event that ends that). */
    @JvmStatic
    @Before(target = MethodMatch(methodName = "processInput", parameterCount = 1, returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun onProcessInputBefore(context: BeforeContext) {
        if (!ClipStack.cullPerFrameWork) return
        val self = context.self as? CustomPanelAPI ?: return
        val plugin = self.plugin as? StarUIPanelPlugin ?: return
        if (plugin.culled && !plugin.isHovering) context.skipOriginal(null)
    }

    @JvmStatic
    @After(
        target = MethodMatch(
            methodName = "advance",
            parameterTypes = [Float::class],
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun onAdvanceAfter(context: AfterContext) {
        // a leaf's plugin only has work in advance when it has onAdvance handlers; none anywhere: no lookup at all
        if (StarUIPanelPlugin.leafAdvanceHandlers <= 0 || !StarUIPanelPlugin.hasAttachedPlugins) return
        val self = context.self as? UIComponentAPI ?: return
        if (self is CustomPanelAPI) return
        // in a StarUI container none of whose leaves has onAdvance: nothing to look up
        val parentPlugin = (self.parent as? CustomPanelAPI)?.plugin as? StarUIPanelPlugin
        if (parentPlugin != null && !parentPlugin.hasLeafAdvancers) return
        if (!context.isMostDerivedCall) return
        val plugin = StarUIPanelPlugin.getAttached(self) ?: return
        val amount = context.getArg(0) as? Float ?: return
        plugin.advance(amount)
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    @After(
        target = MethodMatch(
            methodName = "processInput",
            parameterCount = 1,
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun onProcessInputAfter(context: AfterContext) {
        if (!StarUIPanelPlugin.hasAttachedPlugins || !context.isMostDerivedCall) return
        val self = context.self as? UIComponentAPI ?: return
        if (self is CustomPanelAPI) return
        val plugin = StarUIPanelPlugin.getAttached(self) ?: return
        val events = context.getArg(0) as? MutableList<InputEventAPI> ?: return
        plugin.processInput(events)
    }

    @JvmStatic
    @After(
        target = MethodMatch(
            methodName = "sizeChanged",
            parameterTypes = [Float::class, Float::class],
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun onSizeChangedAfter(context: AfterContext) {
        if (!StarUIPanelPlugin.hasAttachedPlugins || !context.isMostDerivedCall) return
        val self = context.self as? UIComponentAPI ?: return
        if (self is CustomPanelAPI) return
        val plugin = StarUIPanelPlugin.getAttached(self) ?: return
        plugin.positionChanged(self.position)
    }

    @JvmStatic
    @After(
        target = MethodMatch(
            methodName = "locationChanged",
            parameterCount = 0,
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun onLocationChangedAfter(context: AfterContext) {
        if (!StarUIPanelPlugin.hasAttachedPlugins || !context.isMostDerivedCall) return
        val self = context.self as? UIComponentAPI ?: return
        if (self is CustomPanelAPI) return
        val plugin = StarUIPanelPlugin.getAttached(self) ?: return
        plugin.positionChanged(self.position)
    }
}
