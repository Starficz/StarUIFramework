package org.starficz.starui.compose.modifier

import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.BoxedUIElement

internal val UIComponentAPI.actual: UIComponentAPI
    get() = if (this is BoxedUIElement) this.boxedElement else this

internal inline fun UIComponentAPI.applyToPlugin(action: StarUIPanelPlugin.() -> Unit) {
    StarUIPanelPlugin.getOrCreate(this.actual).action()
}

// ==========================================
// CORE MODIFIER INTERFACE
// ==========================================
interface Modifier {
    val operations: List<(UIComponentAPI) -> Unit>

    fun then(action: (UIComponentAPI) -> Unit): Modifier {
        return ModifierImpl(this.operations + action)
    }

    companion object : Modifier {
        override val operations: List<(UIComponentAPI) -> Unit> = emptyList()
    }
}

private class ModifierImpl(
    override val operations: List<(UIComponentAPI) -> Unit>
) : Modifier

// --- Lifecycle & Escape Hatches ---
fun Modifier.onAdvance(action: (Float) -> Unit) = this.then { it.applyToPlugin { advance(action) } }
fun Modifier.onLayout(action: () -> Unit) = this.then { it.applyToPlugin { onLayout(action) } }
fun Modifier.onPositionChanged(action: (PositionAPI) -> Unit) = this.then { it.applyToPlugin { onPositionChanged(action) } }

fun Modifier.onRender(action: StarUIPanelPlugin.(alphaMult: Float) -> Unit) = this.then {
    it.applyToPlugin { render { alpha -> action(alpha) } }
}
fun Modifier.onRenderBelow(action: StarUIPanelPlugin.(alphaMult: Float) -> Unit) = this.then {
    it.applyToPlugin { renderBelow { alpha -> action(alpha) } }
}
fun Modifier.plugin(action: StarUIPanelPlugin.() -> Unit) = this.then { it.applyToPlugin { action() } }
