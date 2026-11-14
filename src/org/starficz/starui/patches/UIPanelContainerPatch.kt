package org.starficz.starui.patches

import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.Injections
import org.starficz.starui.runtime.NodeStore
import org.starficz.starui.runtime.Scope
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.BoxedUIElement
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
internal object UIPanelContainerPatch {

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
            context.setArg(0, arg.obf)
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
            context.setArg(0, arg.obf)
        }
    }

    // 0.98a-RC8 Windows: UIPanel.add(UIComponent) and its varargs overload; both only mark the layout as stale.
    @JvmStatic
    @After(target = MethodMatch(methodName = "add", parameterCount = 1, methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun onChildAdded(context: AfterContext) {
        requestLayoutOf(context.self)
        // a label's StarUI node is held by its parent (labels have no store of their own): it moves with the label
        if (NodeStore.hasMovableNodes) {
            val panel = context.self as? UIPanelAPI
            val child = context.getArg(0) as? UIComponentAPI
            if (panel != null && child != null) NodeStore.childAdded(panel, child)
        }
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
        if (plugin.layoutEngine == null || plugin.swappingChild) return
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
            if (plugin.isDisposed) continue
            Scope.noteDetached(child)
            // an inject() container: built again at the panel's next render, after vanilla has refilled it
            if (plugin.injectionKey != null) Injections.childCleared(panel, child)
        }
    }

    private fun requestLayoutOf(self: Any?) {
        val panel = self as? UIPanelAPI ?: return
        val plugin = StarUIPanelPlugin.getAttached(panel) ?: return
        if (plugin.layoutEngine != null && !plugin.swappingChild) plugin.requestLayout()
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
            context.setArg(0, arg.obf)
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
            context.setArg(0, arg.obf)
        }
        requestLayoutOf(context.self)
    }
}
