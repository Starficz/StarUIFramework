package org.starficz.starui.internal

import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.BoxedUIElement
import org.starficz.starui.core.ButtonListener
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
    }
}
