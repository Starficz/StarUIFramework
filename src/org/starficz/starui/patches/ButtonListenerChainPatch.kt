package org.starficz.starui.patches

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.ButtonListener
import patchlib.api.context.BeforeContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.match.MethodType
import patchlib.api.match.StaticType
import patchlib.api.patch.Before
import patchlib.api.patch.Patch

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
internal object ButtonListenerChainPatch {

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
