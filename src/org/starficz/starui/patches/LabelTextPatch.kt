package org.starficz.starui.patches

import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.SliderAPI
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import patchlib.api.context.BeforeContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.match.MethodType
import patchlib.api.match.StaticType
import patchlib.api.patch.Before
import patchlib.api.patch.Patch

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
internal object LabelTextPatch {

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
