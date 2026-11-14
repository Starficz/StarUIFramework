package org.starficz.starui.modifier

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.lazywizard.lazylib.opengl.ColorUtils
import org.lwjgl.opengl.GL11
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.StarUIDsl
import org.starficz.starui.vanilla.*
import java.awt.Color

// --- Tooltips ---
/**
 * Adds a plain tooltip to the component: one paragraph of [text], shown while the cursor is over it. Tooltips
 * provide a descriptive message for the component they are on.
 *
 * @param text the text of the tooltip
 * @param width the width of the tooltip
 * @param location where the tooltip shows, relative to the component
 * @param margin the gap between the component and the tooltip, or `null` for vanilla's
 */
fun Modifier.tooltip(text: String, width: Float = 300f, location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE, margin: Float? = null) = this.then {
    it.actual.addTooltip(location, width, margin) { tm -> tm.addPara(text, 0f) }
    StarUIPanelPlugin.getOrCreate(it.actual).noteTooltipApplied()
}

/**
 * Adds a rich tooltip to the component, built with vanilla's [TooltipMakerAPI] and shown while the cursor is over
 * it. Tooltips provide a descriptive message for the component they are on.
 *
 * ```kotlin
 * Modifier.tooltip { addPara("Costs %s supplies.", 0f, VanillaColors.highlight, "100") }
 * ```
 *
 * @param width the width of the tooltip
 * @param location where the tooltip shows, relative to the component
 * @param margin the gap between the component and the tooltip, or `null` for vanilla's
 * @param builder fills the tooltip
 */
fun Modifier.tooltip(width: Float = 300f, location: TooltipMakerAPI.TooltipLocation = TooltipMakerAPI.TooltipLocation.ABOVE, margin: Float? = null, builder: (@StarUIDsl TooltipMakerAPI).() -> Unit) = this.then {
    it.actual.addTooltip(location, width, margin) { tm -> tm.builder() }
    StarUIPanelPlugin.getOrCreate(it.actual).noteTooltipApplied()
}

// --- Convenience Rendering ---
/**
 * Draws a solid [color] behind the content, over the component's whole area.
 *
 * @param color color to paint background with
 */
fun Modifier.background(color: Color) = onRenderBelow { alphaMult ->
    GL11.glDisable(GL11.GL_TEXTURE_2D)
    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
    ColorUtils.glColor(color, alphaMult, false)
    GL11.glRectf(left, bottom, right, top)
    GL11.glEnable(GL11.GL_TEXTURE_2D)
}

/**
 * Modify the component to add a border with appearance specified with a [width] and a [color]: a line along the
 * inside of its edge, drawn over its content. The default, a 1 pixel line in the player faction's dark colour, is
 * what vanilla's campaign panels draw (intel, dialogs, codex boxes). Its corners are square, and a translucent
 * colour is even all the way round.
 *
 * Unlike Compose's border, and like a CSS border, it takes its space: [width] is added to the component's padding
 * on every side, so `padding` is the gap inside the border and content never sits on it. On a wrapping axis the
 * component grows by twice [width]; on an exact or fill axis it keeps its size and the content gets that much less
 * room.
 *
 * @param color color to paint the border with
 * @param width width of the border, in pixels
 * @see VanillaFrame
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
 * The sprite frames vanilla draws around its screens, for [Modifier.border]. Each is a set of nine-slice sprites in
 * the `ui` graphics of `settings.json`: corners, and edges that repeat along each side. Vanilla draws them untinted.
 *
 * Only the frames vanilla still uses on their own are here. The game ships older sets that only dead or tutorial code
 * loads: `panel00` and `ui_border2` (never used), `ui_border3` (the tutorial's billboards) and `ui_border4` (the old
 * codex). `ui_border4` lives on only as the old border style of a ship display, which vanilla shows on the Missions
 * screen's flagship; `ShipDisplay(showBorder = true, newBorderStyle = false)` draws it.
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
 * Modify the component to add one of vanilla's sprite frames, inside its edge and behind its content, drawn as
 * vanilla's own frame class draws it: the corners at their size, and each edge repeated between them.
 *
 * Like the line [border], it takes its space: the frame's [VanillaFrame.depth] is added to the component's padding
 * on every side.
 *
 * @param frame the frame to draw
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
