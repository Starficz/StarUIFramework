package org.starficz.starui.compose.modifier

import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.lazywizard.lazylib.opengl.ColorUtils
import org.lwjgl.opengl.GL11
import org.starficz.starui.compose.components.StarUIDsl
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.addTooltip
import org.starficz.starui.core.bottom
import org.starficz.starui.core.left
import org.starficz.starui.core.right
import org.starficz.starui.core.top
import java.awt.Color

// --- Tooltips ---
fun Modifier.tooltip(text: String, width: Float = 300f, location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE) = this.then {
    it.actual.addTooltip(location, width, null) { tm -> tm.addPara(text, 0f) }
    StarUIPanelPlugin.getOrCreate(it.actual).noteTooltipApplied()
}

/**
 * A tooltip on this component. In a builder that runs again, a reused component whose call no longer has a tooltip
 * loses the one it had.
 */
fun Modifier.tooltip(width: Float = 300f, location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE, margin: Float? = null, builder: (@StarUIDsl TooltipMakerAPI).() -> Unit) = this.then {
    it.actual.addTooltip(location, width, margin) { tm -> tm.builder() }
    StarUIPanelPlugin.getOrCreate(it.actual).noteTooltipApplied()
}

// --- Convenience Rendering ---
fun Modifier.background(color: Color) = onRenderBelow { alphaMult ->
    GL11.glDisable(GL11.GL_TEXTURE_2D)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    ColorUtils.glColor(color, alphaMult, false)
    GL11.glRectf(left, bottom, right, top)
    GL11.glEnable(GL11.GL_TEXTURE_2D)
}

fun Modifier.border(color: Color, thickness: Float = 1f) = onRender { alphaMult ->
    GL11.glDisable(GL11.GL_TEXTURE_2D)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    ColorUtils.glColor(color, alphaMult, false)
    GL11.glLineWidth(thickness)
    GL11.glBegin(GL11.GL_LINE_LOOP)
    GL11.glVertex2f(left, bottom)
    GL11.glVertex2f(right, bottom)
    GL11.glVertex2f(right, top)
    GL11.glVertex2f(left, top)
    GL11.glEnd()
    GL11.glEnable(GL11.GL_TEXTURE_2D)
}