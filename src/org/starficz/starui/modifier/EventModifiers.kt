package org.starficz.starui.modifier

import com.fs.starfarer.api.input.InputEventAPI

/** Extends the area that takes mouse input beyond the component, on every side. */
fun Modifier.hitBoxPadding(all: Float) = hitBoxPadding(left = all, top = all, right = all, bottom = all)
/** Extends the area that takes mouse input: [horizontal] on the left and right, [vertical] on the top and bottom. */
fun Modifier.hitBoxPadding(horizontal: Float, vertical: Float) = hitBoxPadding(left = horizontal, top = vertical, right = horizontal, bottom = vertical)
/** Extends the area that takes mouse input, by side (the same shapes as margin and padding). */
fun Modifier.hitBoxPadding(
    vararg namedArgumentsOnly: NamedArgumentsOnly,
    horizontal: Float = 0f, vertical: Float = 0f,
    left: Float = horizontal, top: Float = vertical, right: Float = horizontal, bottom: Float = vertical
) = this.then {
    it.applyToPlugin { setMouseCapturePad(leftPad = left, rightPad = right, topPad = top, bottomPad = bottom) }
}

// --- Events (Mouse) ---
// Mouse handlers get the event; one that does not need it can ignore it: `onClick { clicks++ }`.

/** Runs when a mouse button is pressed over the component. */
fun Modifier.onClick(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onClick(action) } }
/** Runs when the mouse button is released after an [onClick] on this component. */
fun Modifier.onClickRelease(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onClickRelease(action) } }
/** Runs when a mouse button is pressed anywhere outside the component. */
fun Modifier.onClickOutside(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onClickOutside(action) } }
/** Runs on every mouse event (moves included) while the cursor is over the component. */
fun Modifier.onHover(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onHover(action) } }
/** Runs when the cursor moves onto the component. */
fun Modifier.onHoverEnter(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onHoverEnter(action) } }
/** Runs when the cursor leaves the component. */
fun Modifier.onHoverExit(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onHoverExit(action) } }
/** Runs on every mouse event over the component while the left button is held down. */
fun Modifier.onMouseHeld(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onMouseHeld(action) } }

// --- Events (Keyboard) ---
// Key handlers see every key event that reaches the component, whether or not the cursor is over it.

/** Runs when a key is pressed. `event.eventValue` is the `Keyboard.KEY_*` code. */
fun Modifier.onKeyDown(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onKeyDown(action) } }
/** Runs when a key is released. */
fun Modifier.onKeyUp(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onKeyUp(action) } }
/** Runs on each key repeat while a key is held down. */
fun Modifier.onKeyHeld(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onKeyHeld(action) } }
