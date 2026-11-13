package org.starficz.starui.patches

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.SliderAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.BoxedUIElement
import patchlib.api.context.AfterContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.match.MethodType
import patchlib.api.match.StaticType
import patchlib.api.patch.After
import patchlib.api.patch.Patch

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
internal object TextFieldValuePatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "setText", parameterTypes = [String::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun afterSetText(context: AfterContext) = notifyValueChanged(context)
}

@Patch(target = ClassMatch(subtype = ButtonAPI::class))
internal object ButtonCheckedPatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "setChecked", parameterTypes = [Boolean::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun afterSetChecked(context: AfterContext) = notifyValueChanged(context)
}

@Patch(target = ClassMatch(subtype = SliderAPI::class))
internal object SliderProgressPatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "setProgress", parameterTypes = [Float::class], returnTypeName = "void", methodType = MethodType.METHOD, staticType = StaticType.INSTANCE))
    fun afterSetProgress(context: AfterContext) = notifyValueChanged(context)
}

private fun notifyValueChanged(context: AfterContext) {
    if (!StarUIPanelPlugin.hasAttachedPlugins || !context.isMostDerivedCall) return
    val self = context.self as? UIComponentAPI ?: return
    // a box (BoxedUISlider) implements the API interface too: its delegating call reaches the vanilla object's own
    if (self is BoxedUIElement) return
    StarUIPanelPlugin.getAttached(self)?.nativeValueChanged()
}
