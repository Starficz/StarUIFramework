package org.starficz.starui.modifier

import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.BoxedUIElement

internal val UIComponentAPI.actual: UIComponentAPI
    get() = if (this is BoxedUIElement) this.vanillaComponent else this

internal inline fun UIComponentAPI.applyToPlugin(action: StarUIPanelPlugin.() -> Unit) {
    StarUIPanelPlugin.getOrCreate(this.actual).action()
}

/**
 * An immutable collection of modifier operations that decorate or add behavior to StarUI components. For example,
 * backgrounds, padding and click event listeners decorate or add behavior to rows, text or buttons.
 *
 * Start from [Modifier] and chain the factory extension functions:
 *
 * ```kotlin
 * Text("Hello", modifier = Modifier.padding(4f).background(VanillaColors.panel).onClick { greet() })
 * ```
 *
 * Unlike Compose, the order of a chain does not change the result: each modifier sets one thing on the component
 * (its padding, its background, a handler), so `Modifier.border().padding(4f)` and `Modifier.padding(4f).border()`
 * are the same.
 *
 * Modifiers attach to the native object, so they work on every component, vanilla ones included. Components that
 * accept a [Modifier] name the parameter `modifier` and give it a default value of [Modifier]. A component of your
 * own that adds modifiers of its own should add the caller's after them with [then], so the caller's modifiers still
 * apply:
 *
 * ```kotlin
 * fun UIPanelAPI.Card(modifier: Modifier = Modifier, builder: CustomPanelAPI.() -> Unit) =
 *     Column(modifier = Modifier.background(VanillaColors.panel).border() then modifier, builder = builder)
 * ```
 */
interface Modifier {
    /** The operations of this modifier, in order, each one applied to the component it modifies. */
    val operations: List<(UIComponentAPI) -> Unit>

    /**
     * Returns a [Modifier] representing this modifier followed by [action], which is applied to the component. For
     * writing modifier factories of your own.
     *
     * @param action the operation to add, called with the component the modifier is applied to
     */
    fun then(action: (UIComponentAPI) -> Unit): Modifier {
        return ModifierImpl(this.operations + action)
    }

    /**
     * Concatenates this modifier with another.
     *
     * Returns a [Modifier] representing this modifier followed by [other] in sequence.
     */
    infix fun then(other: Modifier): Modifier =
        if (other.operations.isEmpty()) this else ModifierImpl(this.operations + other.operations)

    /**
     * The empty modifier, which contains no operations. Use it as the start of a modifier chain, and as the default
     * value of a component's `modifier` parameter.
     */
    companion object : Modifier {
        override val operations: List<(UIComponentAPI) -> Unit> = emptyList()
    }
}

private class ModifierImpl(
    override val operations: List<(UIComponentAPI) -> Unit>
) : Modifier

// --- Lifecycle & Escape Hatches ---
/**
 * Invokes [action] every frame, with the time since the last frame in seconds, before the frame is drawn.
 *
 * [action] runs on the component's [StarUIPanelPlugin], so its current size and position are at hand (`width`,
 * `height`, `left`, `top`, ...). A component that is scrolled out of view in a lazy list pauses: its [action] does
 * not run until it shows again.
 *
 * @param action the work to do each frame
 */
fun Modifier.onAdvance(action: StarUIPanelPlugin.(amount: Float) -> Unit) =
    this.then { it.applyToPlugin { onAdvance { amount -> action(amount) } } }
/**
 * Invokes [action] each time the component is laid out: sized and positioned, the first time included. It is
 * called **after** the layout pass, when the size and position are final.
 *
 * [action] runs on the component's [StarUIPanelPlugin], so its laid-out size and position are at hand:
 *
 * ```kotlin
 * Column(modifier = Modifier.onLayout { columnWidth = width }) { ... }
 * ```
 *
 * @param action the work to do after each layout
 * @see onFirstLayout
 */
fun Modifier.onLayout(action: StarUIPanelPlugin.() -> Unit) = this.then { it.applyToPlugin { onLayout { action() } } }
/**
 * Invokes [action] once, after the first layout pass that sizes and positions the component. A component reused by
 * a builder that runs again does not invoke it again.
 *
 * @param action the one-time setup to do once the component has its size and position
 * @see onLayout
 */
fun Modifier.onFirstLayout(action: StarUIPanelPlugin.() -> Unit) = this.then { it.applyToPlugin { onFirstLayout { action() } } }
/**
 * Invokes [action] when the component moves or changes size, with its new [PositionAPI].
 *
 * [action] runs on the component's [StarUIPanelPlugin].
 *
 * @param action the work to do with the new position
 */
fun Modifier.onPositionChanged(action: StarUIPanelPlugin.(PositionAPI) -> Unit) =
    this.then { it.applyToPlugin { onPositionChanged { position -> action(position) } } }

/**
 * Draws with OpenGL in front of the component's content, every frame.
 *
 * [action] runs on the component's [StarUIPanelPlugin]: `left`, `right`, `top` and `bottom` are its edges in UI
 * coordinates (bottom-left based, as everywhere in Starsector). Multiply alpha by `alphaMult`, so the drawing fades
 * with the panel. StarUI sets up blending and turns textures off before [action] runs.
 *
 * @param action the drawing, called with the panel's alpha multiplier
 * @see onRenderBelow
 */
fun Modifier.onRender(action: StarUIPanelPlugin.(alphaMult: Float) -> Unit) = this.then {
    it.applyToPlugin { onRender { alpha -> action(alpha) } }
}
/**
 * Draws with OpenGL behind the component's content, every frame. Otherwise it is the same as [onRender].
 *
 * @param action the drawing, called with the panel's alpha multiplier
 * @see onRender
 */
fun Modifier.onRenderBelow(action: StarUIPanelPlugin.(alphaMult: Float) -> Unit) = this.then {
    it.applyToPlugin { onRenderBelow { alpha -> action(alpha) } }
}
/**
 * Gives direct access to the component's [StarUIPanelPlugin], for what no modifier covers:
 * `.plugin { consumeEvents = true }` stops mouse and key events from passing through to what is behind the
 * component.
 *
 * @param action the setup to do on the plugin
 */
fun Modifier.plugin(action: StarUIPanelPlugin.() -> Unit) = this.then { it.applyToPlugin { action() } }
