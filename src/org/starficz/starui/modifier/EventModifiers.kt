package org.starficz.starui.modifier

import com.fs.starfarer.api.input.InputEventAPI
import org.starficz.starui.UIState

/**
 * Extends the area that takes mouse input by [all] pixels beyond the component, on every side, without changing its
 * size or how it is drawn.
 */
fun Modifier.hitBoxPadding(all: Float) = hitBoxPadding(left = all, top = all, right = all, bottom = all)
/**
 * Extends the area that takes mouse input by [horizontal] pixels on the left and right of the component, and by
 * [vertical] pixels on the top and bottom.
 */
fun Modifier.hitBoxPadding(horizontal: Float, vertical: Float) = hitBoxPadding(left = horizontal, top = vertical, right = horizontal, bottom = vertical)
/**
 * Extends the area that takes mouse input beyond the component, by side: [left], [top], [right] and [bottom], with
 * [horizontal] and [vertical] setting two sides at once (the same shapes as [margin] and [padding]).
 */
fun Modifier.hitBoxPadding(
    vararg namedArgumentsOnly: NamedArgumentsOnly,
    horizontal: Float = 0f, vertical: Float = 0f,
    left: Float = horizontal, top: Float = vertical, right: Float = horizontal, bottom: Float = vertical
) = this.then {
    it.applyToPlugin { setMouseCapturePad(leftPad = left, rightPad = right, topPad = top, bottomPad = bottom) }
}

// --- Events (Mouse) ---
// Mouse handlers get the event; one that does not need it can ignore it: `onClick { clicks++ }`.

/**
 * Configure the component to receive clicks: [action] will be called when a mouse button is pressed over the
 * component, with the event. A handler that does not need the event can ignore it: `onClick { clicks++ }`.
 *
 * For a vanilla [ButtonAPI][com.fs.starfarer.api.ui.ButtonAPI], use the `onClick` argument of
 * [Button][org.starficz.starui.components.Button] instead: it runs when the button is pressed, keyboard shortcut
 * included.
 *
 * The event is not consumed: what is behind the component sees it too. Call `event.consume()` in [action], or
 * set `consumeEvents` with [plugin], to stop it.
 *
 * @param action will be called when a mouse button is pressed over the component
 * @see onClickRelease
 */
fun Modifier.onClick(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onClick(action) } }
/**
 * [action] will be called when the mouse button is released over the component, after a press that started on it.
 *
 * The event is not consumed: what is behind the component sees it too. Call `event.consume()` in [action], or
 * set `consumeEvents` with [plugin], to stop it.
 *
 * @param action will be called when the press on the component ends over it
 * @see onClick
 */
fun Modifier.onClickRelease(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onClickRelease(action) } }
/**
 * [action] will be called when a mouse button is pressed outside the component's own bounds: to close a popup or a
 * menu, for example. Put it on the menu itself, not on a full-screen parent: a component that covers the screen has
 * no outside, so it never fires there. Like every input modifier it only sees events that reach this component
 * (a modal layer or a panel consuming events above it takes them first).
 *
 * @param action will be called when a mouse button is pressed outside the component
 */
fun Modifier.onClickOutside(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onClickOutside(action) } }
/**
 * [action] will be called on every mouse event, moves included, while the cursor is over the component.
 *
 * @param action will be called with each mouse event over the component
 * @see onHoverEnter
 * @see onHoverExit
 */
fun Modifier.onHover(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onHover(action) } }
/**
 * [action] will be called when the cursor moves onto the component.
 *
 * @param action will be called with the event that brought the cursor onto the component
 * @see onHoverExit
 */
fun Modifier.onHoverEnter(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onHoverEnter(action) } }
/**
 * [action] will be called when the cursor leaves the component.
 *
 * @param action will be called with the event that took the cursor off the component
 * @see onHoverEnter
 */
fun Modifier.onHoverExit(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onHoverExit(action) } }
/**
 * Keeps [hovered] true while the cursor is over the component and false once it leaves: the usual [onHoverEnter] /
 * [onHoverExit] pair. Read the state in a builder for a look that follows the hover; the component is reused when
 * that builder runs again, so it keeps its hover.
 *
 * Example usage:
 *
 * ```kotlin
 * val hovered = UIState(false)
 * Box(Size.exact(200f, 40f), Modifier.hoverState(hovered).background(if (hovered.value) VanillaColors.dark else Color.BLACK)) {
 *     Text("Hover me", modifier = Modifier.align(Alignment.Center))
 * }
 * ```
 *
 * The state changes with the mouse events over the component, so a component removed while the cursor is over it
 * leaves the state true: set it back yourself when that matters.
 *
 * @param hovered set to true when the cursor moves onto the component, to false when it leaves
 */
fun Modifier.hoverState(hovered: UIState<Boolean>) = onHoverEnter { hovered.value = true }.onHoverExit { hovered.value = false }
/**
 * [action] will be called on every mouse event over the component while the left mouse button is held down: for
 * dragging.
 *
 * @param action will be called with each mouse event over the component while the button is down
 */
fun Modifier.onMouseHeld(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onMouseHeld(action) } }

// --- Events (Keyboard) ---
// Key handlers see every key event that reaches the component, whether or not the cursor is over it.

/**
 * [action] will be called when a key is pressed. `event.eventValue` is the `Keyboard.KEY_*` code.
 *
 * Key handlers see every key event that reaches the component, whether or not the cursor is over it.
 *
 * @param action will be called with the key event
 * @see onKeyUp
 * @see onKeyHeld
 */
fun Modifier.onKeyDown(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onKeyDown(action) } }
/**
 * [action] will be called when a key is released. Key handlers see every key event that reaches the component,
 * whether or not the cursor is over it.
 *
 * @param action will be called with the key event
 * @see onKeyDown
 */
fun Modifier.onKeyUp(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onKeyUp(action) } }
/**
 * [action] will be called on each key repeat while a key is held down. Key handlers see every key event that
 * reaches the component, whether or not the cursor is over it.
 *
 * @param action will be called with each repeat event
 * @see onKeyDown
 */
fun Modifier.onKeyHeld(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onKeyHeld(action) } }
