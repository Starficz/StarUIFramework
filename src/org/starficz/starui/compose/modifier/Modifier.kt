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

/**
 * Lays out this component's container again on the next frame. Rarely needed: StarUI already does it when children
 * are added or removed and when a Text changes. Call it after changing something StarUI can't see, such as a native
 * component's size.
 */
fun UIComponentAPI.requestLayout() {
    val target = this.actual
    val own = StarUIPanelPlugin.getAttached(target)
    if (own != null) own.requestLayout()
    else target.parent?.let { StarUIPanelPlugin.getAttached(it) }?.requestLayout()
}

/**
 * Extra behaviour for a component: spacing, events, drawing, tooltips. Start from `Modifier` and chain calls:
 * `Modifier.padding(4f).background(color).onClick { ... }`. Each component takes one as its `modifier` argument.
 */
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
/** Runs every frame with the seconds since the last one. */
fun Modifier.onAdvance(action: (Float) -> Unit) = this.then { it.applyToPlugin { advance(action) } }
/** Runs each time the component is laid out (sized and placed), the first time included. */
fun Modifier.onLayout(action: () -> Unit) = this.then { it.applyToPlugin { onLayout(action) } }
/** Runs once, the first time the component is laid out. */
fun Modifier.onFirstLayout(action: () -> Unit) = this.then { it.applyToPlugin { onFirstLayout(action) } }
/** Runs when the component moves or changes size. */
fun Modifier.onPositionChanged(action: (PositionAPI) -> Unit) = this.then { it.applyToPlugin { onPositionChanged(action) } }

/**
 * Draws with OpenGL over the component's own content, every frame. `left`, `right`, `top` and `bottom` give its
 * edges in UI coordinates; multiply alpha by [alphaMult] so it fades with the panel.
 */
fun Modifier.onRender(action: StarUIPanelPlugin.(alphaMult: Float) -> Unit) = this.then {
    it.applyToPlugin { render { alpha -> action(alpha) } }
}
/** Like [onRender], but draws under the component's content. */
fun Modifier.onRenderBelow(action: StarUIPanelPlugin.(alphaMult: Float) -> Unit) = this.then {
    it.applyToPlugin { renderBelow { alpha -> action(alpha) } }
}
/**
 * Direct access to the component's [StarUIPanelPlugin], for what no modifier covers:
 * `.plugin { consumeEvents = true }` stops mouse and key events from passing through to what is behind it.
 */
fun Modifier.plugin(action: StarUIPanelPlugin.() -> Unit) = this.then { it.applyToPlugin { action() } }
