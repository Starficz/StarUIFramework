package org.starficz.starui.compose.modifier

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.starficz.starui.core.VanillaColors
import org.lazywizard.lazylib.opengl.ColorUtils
import org.lwjgl.opengl.GL11
import org.starficz.starui.compose.StarUIDsl
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.addTooltip
import org.starficz.starui.core.bottom
import org.starficz.starui.core.left
import org.starficz.starui.core.right
import org.starficz.starui.core.top
import java.awt.Color

// --- Tooltips ---
/** A tooltip with one paragraph of [text], shown while the cursor is over the component. */
fun Modifier.tooltip(text: String, width: Float = 300f, location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE, margin: Float? = null) = this.then {
    it.actual.addTooltip(location, width, margin) { tm -> tm.addPara(text, 0f) }
    StarUIPanelPlugin.getOrCreate(it.actual).noteTooltipApplied()
}

/**
 * A tooltip built with vanilla's [TooltipMakerAPI], shown while the cursor is over the component:
 * `tooltip { addPara("Cost: %s", 0f, Misc.getHighlightColor(), "100") }`.
 */
fun Modifier.tooltip(width: Float = 300f, location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE, margin: Float? = null, builder: (@StarUIDsl TooltipMakerAPI).() -> Unit) = this.then {
    it.actual.addTooltip(location, width, margin) { tm -> tm.builder() }
    StarUIPanelPlugin.getOrCreate(it.actual).noteTooltipApplied()
}

// --- Convenience Rendering ---
/** Fills the component's area with [color], under its content. */
fun Modifier.background(color: Color) = onRenderBelow { alphaMult ->
    GL11.glDisable(GL11.GL_TEXTURE_2D)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    ColorUtils.glColor(color, alphaMult, false)
    GL11.glRectf(left, bottom, right, top)
    GL11.glEnable(GL11.GL_TEXTURE_2D)
}

/**
 * A line [width] pixels thick along the inside of the component's edge, in [color], drawn over its content. The
 * default, a 1 px line in the player faction's dark colour, is what vanilla's campaign panels draw (intel, dialogs,
 * codex boxes). Drawn as four rectangles that meet without overlapping, so the corners are square and a translucent
 * colour is even all the way round.
 *
 * Like a CSS border, it takes its space: [width] is added to the component's padding on every side, so `padding`
 * is the gap inside the border and content never sits on it. On a wrap axis the component grows by twice [width];
 * on an exact or fill axis it keeps its size and the content gets that much less room.
 */
fun Modifier.border(color: Color = VanillaColors.dark, width: Float = 1f) = reserveBorder(width).onRender { alphaMult ->
    val t = minOf(width, (right - left) / 2f, (top - bottom) / 2f)
    if (t <= 0f) return@onRender
    GL11.glDisable(GL11.GL_TEXTURE_2D)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    ColorUtils.glColor(color, alphaMult, false)
    GL11.glRectf(left, top - t, right, top)               // top, full width
    GL11.glRectf(left, bottom, right, bottom + t)         // bottom, full width
    GL11.glRectf(left, bottom + t, left + t, top - t)     // left, between them
    GL11.glRectf(right - t, bottom + t, right, top - t)   // right, between them
    GL11.glEnable(GL11.GL_TEXTURE_2D)
}

/**
 * The sprite frames vanilla draws around its screens, each a set of nine-slice sprites in `settings.json`'s `ui`
 * graphics (corners, and edges that repeat along each side). Vanilla draws them untinted. Only the ones vanilla still
 * uses: the game ships older sets (`panel00`, `ui_border2` to `ui_border4`) that only dead or tutorial code loads.
 */
enum class VanillaFrame(internal val spriteSet: String) {
    /**
     * The holo frame of vanilla's dialogs and the title screen's menu: a light frame with a cyan inner line and
     * rounded corners (`ui_border1`). Bare here; `Holo` draws it with the holo's background, tint, noise and fold.
     */
    TitleMenu("ui_border1"),
    ;

    /** How deep the frame is: its corner sprite's size, the unit vanilla draws every piece in (8 for TitleMenu). */
    val depth: Float get() = Global.getSettings().getSprite("ui", "${spriteSet}_bot_left").width
}

/** Adds a border's [depth] to the component's padding, so content starts inside the border (CSS's box model). */
private fun Modifier.reserveBorder(depth: Float) = then { component -> component.addPadding(PaddingValues.all(depth)) }

/**
 * One of vanilla's sprite frames around the component, inside its edge and under its content, drawn as vanilla's
 * own frame class draws it: the corners at their size, each edge repeated between them. Like the line [border], it
 * takes its space: the frame's [VanillaFrame.depth] is added to the component's padding on every side.
 */
fun Modifier.border(frame: VanillaFrame): Modifier {
    fun sprite(part: String) = Global.getSettings().getSprite("ui", "${frame.spriteSet}_$part")
    val botLeft = sprite("bot_left"); val botRight = sprite("bot_right")
    val topLeft = sprite("top_left"); val topRight = sprite("top_right")
    val leftEdge = sprite("left"); val rightEdge = sprite("right")
    val topEdge = sprite("top"); val botEdge = sprite("bot")
    val all = listOf(botLeft, botRight, topLeft, topRight, leftEdge, rightEdge, topEdge, botEdge)
    return reserveBorder(botLeft.width).onRenderBelow { alphaMult ->
        val c = botLeft.width   // vanilla sizes every piece by the corner
        val w = right - left
        val h = top - bottom
        val across = (w - c * 2f) / c
        val up = (h - c * 2f) / c
        for (piece in all) piece.setAlphaMult(alphaMult)
        botLeft.render(left, bottom)
        botRight.render(left + w - c, bottom)
        topLeft.render(left, bottom + h - c)
        topRight.render(left + w - c, bottom + h - c)
        if (up > 0f) {
            leftEdge.renderRegion(left, bottom + c, 0f, 0f, 1f, up)
            rightEdge.renderRegion(left + w - c, bottom + c, 0f, 0f, 1f, up)
        }
        if (across > 0f) {
            topEdge.renderRegion(left + c, bottom + h - c, 0f, 0f, across, 1f)
            botEdge.renderRegion(left + c, bottom, 0f, 0f, across, 1f)
        }
    }
}
