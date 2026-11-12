package org.starficz.starui.internal

import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.SliderAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.runtime.ClipStack
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
import patchlib.api.query.MethodQuery
import org.starficz.starui.compose.AxisOverflow
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
object UIComponentLifecyclePatch {
    /** Set by the render @Before on a call it culled, so the @After skips the plugin's render too. */
    private const val CULLED = "staruiframework.culled"

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
        if (ClipStack.depth == 0 && !StarUIPanelPlugin.hasAttachedPlugins) return
        if (!context.isMostDerivedCall) return
        val self = context.self as? UIComponentAPI ?: return
        if (ClipStack.depth > 0 && ClipStack.culls(self)) {
            context.setLocal(CULLED, true)
            context.skipOriginal(null)
            return
        }
        if (!StarUIPanelPlugin.hasAttachedPlugins || self is CustomPanelAPI) return
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
 * A label's text decides its measured size, so a label inside a StarUI layout that gets a different string asks that
 * layout to run again (on the next frame). This covers `Text(...)` labels and vanilla labels placed in StarUI
 * containers alike. `setText(String)` is the `LabelAPI` method, so nothing obfuscated is named.
 */
// 0.98.5a-RC4 Windows: matches com.fs.starfarer.ui.dosuper (Label)
@Patch(
    target = ClassMatch(
        subtype = LabelAPI::class,
        methodMatches = [
            MethodMatch(methodName = "setText", parameterTypes = [String::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE)
        ]
    )
)
object LabelTextPatch {

    @JvmStatic
    @Before(
        target = MethodMatch(
            methodName = "setText",
            parameterTypes = [String::class],
            returnTypeName = "void",
            methodType = MethodType.METHOD,
            staticType = StaticType.INSTANCE
        )
    )
    fun onSetText(context: BeforeContext) {
        if (!StarUIPanelPlugin.hasAnyPlugins || !context.isMostDerivedCall) return
        val self = context.self as? LabelAPI ?: return
        val parent = self.parent ?: return
        // A slider rewrites its own value label every frame while it moves; the slider's size never depends on it.
        if (parent is SliderAPI) return
        val plugin = StarUIPanelPlugin.getAttached(parent) ?: return
        if (self.text == context.getArg(0)) return
        plugin.requestLayout()
    }
}

/**
 * Tells a native control's StarUI plugin that its value changed, so `bind` states follow it without polling. Every
 * change goes through these API methods, the player's included (0.98.5a-RC4 decompile):
 * - a text field's typing, backspace, paste and clearing all end in `setText(String)`;
 * - a slider's drag ends in `setProgress(float)` (followed by `forceSync()`).
 * A button's click flips its checked field directly, not through `setChecked`; clicks are already seen by the button
 * listener, so the `setChecked(boolean)` hook covers the other case: code (vanilla, another mod, the caller holding the
 * ButtonAPI) checking or unchecking it. All three are API interface methods matched by API subtype.
 */
// 0.98.5a-RC4 Windows: matches com.fs.starfarer.ui.B (text field), com.fs.starfarer.ui.o0O0 (button),
// com.fs.starfarer.campaign.ui.N (slider)
@Patch(target = ClassMatch(subtype = TextFieldAPI::class))
object TextFieldValuePatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "setText", parameterTypes = [String::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun afterSetText(context: AfterContext) = notifyValueChanged(context)
}

@Patch(target = ClassMatch(subtype = ButtonAPI::class))
object ButtonCheckedPatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "setChecked", parameterTypes = [Boolean::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun afterSetChecked(context: AfterContext) = notifyValueChanged(context)
}

@Patch(target = ClassMatch(subtype = SliderAPI::class))
object SliderProgressPatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "setProgress", parameterTypes = [Float::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun afterSetProgress(context: AfterContext) = notifyValueChanged(context)
}

private fun notifyValueChanged(context: AfterContext) {
    if (!StarUIPanelPlugin.hasAttachedPlugins || !context.isMostDerivedCall) return
    val self = context.self as? UIComponentAPI ?: return
    StarUIPanelPlugin.getAttached(self)?.nativeValueChanged()
}

/**
 * Automatically unwraps any [BoxedUIElement] (images, ship previews) passed into vanilla [UIPanelAPI] container methods
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
    fun onChildRemoved(context: AfterContext) {
        val panel = context.self as? UIPanelAPI ?: return
        val plugin = StarUIPanelPlugin.getAttached(panel) ?: return
        if (plugin.layoutEngine == null) return
        // A Column or Row places each child relative to the one before it. If that one is gone, the engine refuses
        // the next resize ("May only anchor on siblings"), so pin the rest where they are until the next layout.
        if (plugin.isStackLayout) plugin.pinChildrenToPanel()
        plugin.requestLayout()
    }

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
object ClipInputPatch {
    private const val MOVED = "staruiframework.clipMovedEvents"
    private const val OFF_SCREEN = Int.MAX_VALUE
    private val SET_X = MethodQuery.create().methodName("setX").parameterTypes(Int::class.javaPrimitiveType).build()
    private val SET_Y = MethodQuery.create().methodName("setY").parameterTypes(Int::class.javaPrimitiveType).build()

    @JvmStatic
    @Before(target = MethodMatch(methodName = "processInput", parameterCount = 1, returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun hideClippedInput(context: BeforeContext) {
        if (!ClipStack.anyClipContainers || !context.isMostDerivedCall) return
        val self = context.self as? CustomPanelAPI ?: return
        val plugin = self.plugin as? StarUIPanelPlugin ?: return
        val overflow = plugin.overflow
        if (!overflow.clips || plugin.scrollHost != null) return
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
