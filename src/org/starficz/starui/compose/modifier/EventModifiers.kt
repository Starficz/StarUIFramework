package org.starficz.starui.compose.modifier

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
fun Modifier.onClick(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onClick(action) } }
fun Modifier.onClickRelease(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onClickRelease(action) } }
fun Modifier.onClickOutside(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onClickOutside(action) } }
fun Modifier.onHover(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onHover(action) } }
fun Modifier.onHoverEnter(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onHoverEnter(action) } }
fun Modifier.onHoverExit(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onHoverExit(action) } }
fun Modifier.onMouseHeld(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onMouseHeld(action) } }

// --- Events (Keyboard) ---
fun Modifier.onKeyDown(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onKeyDown(action) } }
fun Modifier.onKeyUp(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onKeyUp(action) } }
fun Modifier.onKeyHeld(action: (InputEventAPI) -> Unit) = this.then { it.applyToPlugin { onKeyHeld(action) } }
