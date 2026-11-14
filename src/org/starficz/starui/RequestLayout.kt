package org.starficz.starui

import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.modifier.actual
import org.starficz.starui.runtime.StarUIPanelPlugin

/**
 * Lays out this component's container again on the next frame: its own layout when it is a StarUI container, else
 * its parent's.
 *
 * Rarely needed: StarUI already does it when children are added or removed and when a
 * [Text][org.starficz.starui.components.Text] changes its text. Call it after changing something StarUI cannot see,
 * such as a vanilla component's size set by hand. It only sets flags, so it is cheap, and safe to call from event
 * handlers.
 */
fun UIComponentAPI.requestLayout() {
    val target = this.actual
    val own = StarUIPanelPlugin.getAttached(target)
    if (own != null) own.requestLayout()
    else target.parent?.let { StarUIPanelPlugin.getAttached(it) }?.requestLayout()
}
