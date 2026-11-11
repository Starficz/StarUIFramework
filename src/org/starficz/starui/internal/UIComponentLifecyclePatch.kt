package org.starficz.starui.internal

import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.runtime.Scope
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.BoxedUIElement
import org.starficz.starui.core.ButtonListener
import org.starficz.starui.core.getChildrenNonCopy
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
object UIComponentLifecyclePatch {

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
        if (!StarUIPanelPlugin.hasAttachedPlugins || !context.isMostDerivedCall) return
        val self = context.self as? UIComponentAPI ?: return
        if (self is CustomPanelAPI) return
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
        if (!StarUIPanelPlugin.hasAttachedPlugins || !context.isMostDerivedCall) return
        val self = context.self as? UIComponentAPI ?: return
        if (self is CustomPanelAPI) return
        val plugin = StarUIPanelPlugin.getAttached(self) ?: return
        val alphaMult = context.getArg(0) as? Float ?: return
        plugin.render(alphaMult)
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
        if (!StarUIPanelPlugin.hasAttachedPlugins || !context.isMostDerivedCall) return
        val self = context.self as? UIComponentAPI ?: return
        if (self is CustomPanelAPI) return
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

/**
 * Keeps a button's original action when StarUI attaches `ButtonAPI.onClick`. Vanilla calls
 * `Listener.actionPerformed(event, button)` on the button's single listener; StarUI's listener is a CustomPanel, whose
 * implementation only passes the button's custom data to its plugin. This hook sees both arguments first and forwards
 * them to the listener StarUI replaced. Matched by API subtype, readable interface method name and `Object` parameter
 * types only, so no obfuscated symbol is named.
 */
// 0.98a-RC8 Windows: matches com.fs.starfarer.ui.newui.CustomPanel.actionPerformed(Object, Object)
@Patch(
    target = ClassMatch(
        subtype = CustomPanelAPI::class,
        methodMatches = [
            MethodMatch(methodName = "actionPerformed", parameterTypes = [Any::class, Any::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE)
        ]
    )
)
object ButtonListenerChainPatch {

    @JvmStatic
    @Before(
        target = MethodMatch(
            methodName = "actionPerformed",
            parameterTypes = [Any::class, Any::class],
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun forwardToReplacedListener(context: BeforeContext) {
        if (!context.isMostDerivedCall) return
        val self = context.self as? CustomPanelAPI ?: return
        val listener = self.plugin as? ButtonListener
            ?: (self.plugin as? StarUIPanelPlugin)?.delegate as? ButtonListener
            ?: return
        listener.forwardToPrevious(context.getArg(0), context.getArg(1))
    }
}

/**
 * Automatically unwraps any [BoxedUIElement] passed into vanilla [UIPanelAPI] container methods
 * (`addComponent`, `removeComponent`, `bringComponentToTop`, `sendToBottom`) so boxed StarUI elements
 * never trigger a `ClassCastException` inside obfuscated `UIPanel`.
 *
 * It also tells a StarUI layout container when its child list changes, so the layout re-runs without a manual call.
 * Vanilla's `addComponent` / `removeComponent` delegate to the base panel's own `add` / `remove` (one parameter of
 * an engine type, so matched by name and arity only), which is also what vanilla widgets call directly;
 * `clearChildren` empties the list without going through `remove`. All three are readable but not API names
 * (inferred): re-check them after a game update. The handlers only set flags on panels that carry a StarUI plugin.
 */
// 0.98a-RC8 Windows: matches com.fs.starfarer.ui.interfacenew (UIPanel)
@Patch(
    target = ClassMatch(
        subtype = UIPanelAPI::class,
        methodMatches = [
            MethodMatch(methodName = "addComponent", parameterTypes = [UIComponentAPI::class], returnType = PositionAPI::class, methodType = MethodType.METHOD, staticType = StaticType.INSTANCE),
            MethodMatch(methodName = "removeComponent", parameterTypes = [UIComponentAPI::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE),
            MethodMatch(methodName = "getChildrenNonCopy", parameterCount = 0, returnType = List::class, methodType = MethodType.METHOD, staticType = StaticType.INSTANCE)
        ]
    )
)
object UIPanelContainerPatch {

    @JvmStatic
    @Before(
        target = MethodMatch(
            methodName = "addComponent",
            parameterTypes = [UIComponentAPI::class],
            returnType = PositionAPI::class,
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun unwrapAddComponent(context: BeforeContext) {
        val arg = context.getArg(0)
        if (arg is BoxedUIElement) {
            context.setArg(0, arg.boxedElement)
        }
    }

    @JvmStatic
    @Before(
        target = MethodMatch(
            methodName = "removeComponent",
            parameterTypes = [UIComponentAPI::class],
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun unwrapRemoveComponent(context: BeforeContext) {
        val arg = context.getArg(0)
        if (arg is BoxedUIElement) {
            context.setArg(0, arg.boxedElement)
        }
    }

    // 0.98a-RC8 Windows: UIPanel.add(UIComponent) and its varargs overload; both only mark the layout as stale.
    @JvmStatic
    @After(target = MethodMatch(methodName = "add", parameterCount = 1, methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun onChildAdded(context: AfterContext) {
        requestLayoutOf(context.self)
        // A subtree that was detached while one of its scopes waited for a rebuild is being attached again: pass
        // the "dirty below" mark on to its new ancestors so the rebuild happens before they lay out.
        if (Scope.hasDirty) {
            val child = context.getArg(0) as? UIComponentAPI ?: return
            if (StarUIPanelPlugin.getAttached(child)?.dirtyScopeBelow == true) {
                (context.self as? UIPanelAPI)?.let { StarUIPanelPlugin.getAttached(it) }?.markDirtyScopeBelow()
            }
        }
    }

    @JvmStatic
    @After(target = MethodMatch(methodName = "remove", parameterCount = 1, methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun onChildRemoved(context: AfterContext) = requestLayoutOf(context.self)

    @JvmStatic
    @Before(target = MethodMatch(methodName = "clearChildren", parameterCount = 0, returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun onChildrenCleared(context: BeforeContext) {
        requestLayoutOf(context.self)
        // clearChildren() is how panels throw their content away. StarUI children are disposed a little later, and
        // only if nothing has added them back by then (some code clears and re-adds the same components to reorder).
        if (!StarUIPanelPlugin.hasAnyPlugins) return
        val panel = context.self as? UIPanelAPI ?: return
        for (child in panel.getChildrenNonCopy()) {
            val plugin = StarUIPanelPlugin.getAttached(child) ?: continue
            if (!plugin.isDisposed) Scope.noteDetached(child)
        }
    }

    private fun requestLayoutOf(self: Any?) {
        val panel = self as? UIPanelAPI ?: return
        val plugin = StarUIPanelPlugin.getAttached(panel) ?: return
        if (plugin.layoutEngine != null) plugin.requestLayout()
    }

    @JvmStatic
    @Before(
        target = MethodMatch(
            methodName = "bringComponentToTop",
            parameterTypes = [UIComponentAPI::class],
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun unwrapBringToTop(context: BeforeContext) {
        val arg = context.getArg(0)
        if (arg is BoxedUIElement) {
            context.setArg(0, arg.boxedElement)
        }
        requestLayoutOf(context.self)   // child order is flow order in a Column or Row
    }

    @JvmStatic
    @Before(
        target = MethodMatch(
            methodName = "sendToBottom",
            parameterTypes = [UIComponentAPI::class],
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun unwrapSendToBottom(context: BeforeContext) {
        val arg = context.getArg(0)
        if (arg is BoxedUIElement) {
            context.setArg(0, arg.boxedElement)
        }
        requestLayoutOf(context.self)
    }
}
