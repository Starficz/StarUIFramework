package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lazywizard.lazylib.opengl.ColorUtils
import org.lwjgl.opengl.GL11
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.core.bottom
import org.starficz.starui.core.left
import org.starficz.starui.core.right
import org.starficz.starui.core.top
import java.awt.Color

/**
 * The fundamental layout blank-space node.
 * Can optionally render a background color to act as a physical divider.
 */
fun UIPanelAPI.Spacer(
    size: PanelSize = PanelSize.fill(1f),
    modifier: Modifier = Modifier,
    color: Color? = null
): CustomPanelAPI {
    checkDslTarget()

    val (initW, initH) = getInitialBounds(size)

    val plugin = StarUIPanelPlugin()
    val panel = Global.getSettings().createCustom(initW, initH, plugin)
    plugin.customPanel = panel
    this.addComponent(panel)

    registerComponentSizing(panel, size, isContainer = false)
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMeasurables[panel] = { _, _ -> Pair(initW, initH) }
    }

    modifier.operations.forEach { it(panel) }

    if (color != null) {
        plugin.renderBelow { alphaMult ->
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            ColorUtils.glColor(color, alphaMult, false)
            GL11.glRectf(plugin.left, plugin.bottom, plugin.right, plugin.top)

            GL11.glEnable(GL11.GL_TEXTURE_2D)
        }
    }

    return panel
}

/** A semantic horizontal line that stretches completely across the X axis. */
fun UIPanelAPI.Divider(
    thickness: Float = StarUITheme.current.divider.thickness,
    color: Color = StarUITheme.current.divider.color,
    modifier: Modifier = Modifier
) = Spacer(size = PanelSize.mixed(Axis.Fill(), Axis.Exact(thickness)), modifier = modifier, color = color)

/** A flexible invisible gap that pushes elements apart */
fun UIPanelAPI.FlexSpacer(
    weight: Float = 1f,
    modifier: Modifier = Modifier
) = Spacer(size = PanelSize.fill(weight), modifier = modifier)
