package org.starficz.starui.internal

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import org.starficz.starui.core.ShipDisplayScale
import patchlib.api.context.MethodCallContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.match.MethodType
import patchlib.api.match.StaticType
import patchlib.api.patch.Patch
import patchlib.api.patch.RedirectMethodCall
import patchlib.api.query.MethodQuery
import kotlin.math.roundToInt

/**
 * An exact scale for vanilla ship displays, pixel aligned. Vanilla fits the ship into the panel, never above 1, and then
 * always multiplies by 0.95, so a ship never draws 1:1; it also places the ship 3% of the panel height above centre,
 * and centres on the panel's middle, which is a half pixel whenever the panel's width or height is odd.
 *
 * For a display that has an exact scale ([ShipDisplayScale], set through `BoxedUIShipPreview.overwriteScale`) the three
 * redirects below use that scale, drop the 3% shift and put the sprite's edges on whole screen pixels. Every other
 * display, vanilla's own included, is passed through unchanged. All three redirects are narrow: one call site each in
 * this class's own methods.
 *
 * Targets are inferred, not API (re-check them after a game update): the class by its readable methods (the same shape
 * StarUIManager uses to find it), `render` with 7 parameters, and the ship set-up method by its (String, String, String)
 * shape.
 */
// 0.98.5a-RC4 Windows: matches com.fs.starfarer.title.ooOO.do; set-up method o00000(String, String, String)
@Patch(
    target = ClassMatch(
        subtype = UIPanelAPI::class,
        methodMatches = [
            MethodMatch(methodName = "isSchematicMode", parameterCount = 0),
            MethodMatch(methodName = "setScaleDownSmallerShipsMagnitude", parameterCount = 1)
        ]
    )
)
object ShipDisplayExactScalePatch {
    private val GET_SHIPS = MethodQuery.create().methodName("getShips").parameterCount(0).build()

    /** render(x, y, w, h, alpha, color, additive): `glScalef(scale, scale, 1)` is its only glScalef. */
    @JvmStatic
    @RedirectMethodCall(
        target = MethodMatch(methodName = "render", parameterCount = 7, methodType = MethodType.METHOD, staticType = StaticType.INSTANCE),
        call = MethodMatch(methodName = "glScalef", parameterCount = 3),
        owner = ClassMatch(type = GL11::class)
    )
    fun exactScale(context: MethodCallContext) {
        val scale = ShipDisplayScale.of(context.self)
        if (scale == null) { context.call(); return }
        context.call(scale, scale, 1f)
    }

    /**
     * render first moves to the panel centre, `glTranslatef(x + w / 2, y + h / 2, 0)`; the ship's sprite bounds are
     * centred there. Moving that centre so `centre - scale * spriteSize / 2` is a whole screen pixel puts every sprite
     * texel on a pixel at scale 1. The other glTranslatef in render (fighter formations) has other arguments and passes.
     */
    @JvmStatic
    @RedirectMethodCall(
        target = MethodMatch(methodName = "render", parameterCount = 7, methodType = MethodType.METHOD, staticType = StaticType.INSTANCE),
        call = MethodMatch(methodName = "glTranslatef", parameterCount = 3),
        owner = ClassMatch(type = GL11::class)
    )
    fun pixelAlignedCentre(context: MethodCallContext) {
        val scale = ShipDisplayScale.of(context.self)
        if (scale == null) { context.call(); return }
        val tx = context.getCallArg(0) as Float
        val ty = context.getCallArg(1) as Float
        val x = context.getArg(0) as Float; val y = context.getArg(1) as Float
        val w = context.getArg(2) as Float; val h = context.getArg(3) as Float
        if (tx != x + w / 2f || ty != y + h / 2f) { context.call(); return }
        val ship = (context.getMethod(GET_SHIPS).call() as? Array<*>)?.firstOrNull() as? ShipAPI
        val sprite = ship?.spriteAPI
        if (sprite == null) { context.call(); return }
        val pixels = Global.getSettings().screenScaleMult   // UI units to screen pixels
        context.call(snap(tx, scale * sprite.width / 2f, pixels), snap(ty, scale * sprite.height / 2f, pixels), 0f)
    }

    /** The centre nearest [centre] whose edge `centre - half` falls on a whole pixel of size `1 / pixelsPerUnit`. */
    private fun snap(centre: Float, half: Float, pixelsPerUnit: Float): Float =
        ((centre - half) * pixelsPerUnit).roundToInt() / pixelsPerUnit + half

    /**
     * The set-up method places each ship at `(-(spriteW / 2 - centreX), -(spriteH / 2 - centreY) + panelH * 0.03)`;
     * without the 0.03 term the sprite bounds are centred exactly. It is the method's only Vector2f.set call. The public
     * setVariant(String, String, String) has the same parameter shape and no such call, so nothing is woven there.
     */
    @JvmStatic
    @RedirectMethodCall(
        target = MethodMatch(
            parameterTypes = [String::class, String::class, String::class], returnTypeName = "void",
            methodType = MethodType.METHOD, staticType = StaticType.INSTANCE
        ),
        call = MethodMatch(methodName = "set", parameterCount = 2),
        owner = ClassMatch(type = Vector2f::class)
    )
    fun exactCentre(context: MethodCallContext) {
        val panel = context.self as? UIComponentAPI
        if (panel == null || ShipDisplayScale.of(panel) == null) { context.call(); return }
        val y = context.getCallArg(1) as Float
        context.call(context.getCallArg(0), y - panel.position.height * 0.03f)
    }
}
