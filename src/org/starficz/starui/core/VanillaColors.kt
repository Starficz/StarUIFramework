package org.starficz.starui.core

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.util.Misc
import java.awt.Color

/**
 * The colours vanilla's own screens use, in one place. Each is read when asked for, never cached: the player faction's
 * colours change with the faction, and the game sets them up after mods are loaded.
 */
object VanillaColors {
    /** The player faction's UI colour: headings, button text, borders that matter. */
    val base: Color get() = Global.getSettings().basePlayerColor
    /** The player faction's dark UI colour: heading bars, button backgrounds, quiet borders. */
    val dark: Color get() = Global.getSettings().darkPlayerColor
    /** The player faction's bright UI colour: hover and selection. */
    val bright: Color get() = Global.getSettings().brightPlayerColor

    /** Body text. */
    val text: Color get() = Misc.getTextColor()
    /** Secondary text and notes. */
    val gray: Color get() = Misc.getGrayColor()
    /** Numbers and names picked out in text (yellow). */
    val highlight: Color get() = Misc.getHighlightColor()
    /** Good news in text (green). */
    val positive: Color get() = Misc.getPositiveHighlightColor()
    /** Bad news in text (red). */
    val negative: Color get() = Misc.getNegativeHighlightColor()

    /** Story-point options: text, background, hover. */
    val story: Color get() = Misc.getStoryOptionColor()
    val storyDark: Color get() = Misc.getStoryDarkColor()
    val storyBright: Color get() = Misc.getStoryBrightColor()

    /** The black of vanilla's panels: opaque, a touch lighter than the screen behind them. */
    val panel: Color get() = Color(4, 6, 8, 255)

    /** This colour with another alpha (0..255). */
    fun Color.withAlpha(alpha: Int): Color = Color(red, green, blue, alpha)
}
