package org.starficz.starui.compose.modifier

import com.fs.starfarer.api.input.InputEventAPI

fun Modifier.hitBoxPadding(pad: Float) = hitBoxPadding(pad, pad, pad, pad)
fun Modifier.hitBoxPadding(left: Float = 0f, right: Float = 0f, top: Float = 0f, bottom: Float = 0f) = this.then {
    it.applyToPlugin { setMouseCapturePad(left, right, top, bottom) }
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
