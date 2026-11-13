package org.starficz.starui

import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.modifier.actual
import org.starficz.starui.runtime.StarUIPanelPlugin

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
