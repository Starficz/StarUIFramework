package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Axis
import org.starficz.starui.compose.BoundedSize
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.StarUITheme
import java.awt.Color

/**
 * Empty space. It has no content, so its size is exact or fill. To draw something there, give it
 * `Modifier.background(color)`.
 */
fun UIPanelAPI.Spacer(
    size: BoundedSize = Size.fillMaxSize(),
    modifier: Modifier = Modifier
): CustomPanelAPI {
    checkDslTarget()

    val (initW, initH) = getInitialBounds(size)

    val slot = childSlot("Spacer")
    val reused: CustomPanelAPI? = slot.reused()
    val panel: CustomPanelAPI
    if (reused != null) {
        panel = reused
        applyReuseBounds(panel, size)
    } else {
        val plugin = StarUIPanelPlugin()
        panel = Global.getSettings().createCustom(initW, initH, plugin)
        plugin.customPanel = panel
        this.addComponent(panel)
    }

    registerComponentSizing(panel, size, isContainer = false)
    slot.remember(panel, panel)
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMeasurables[panel] = { _, _ -> Pair(initW, initH) }
    }

    modifier.operations.forEach { it(panel) }
    return panel
}

/** A horizontal line across the full width. */
fun UIPanelAPI.Divider(
    modifier: Modifier = Modifier,
    thickness: Float = StarUITheme.current.divider.thickness,
    color: Color = StarUITheme.current.divider.color
) = Spacer(
    size = Size.mixed(Axis.Fill(), Axis.Exact(thickness)),
    modifier = Modifier.background(color).then { c -> modifier.operations.forEach { it(c) } }
)

/** A gap that takes the space left over on its container's main axis, pushing its neighbours apart. */
fun UIPanelAPI.FlexSpacer(
    weight: Float = 1f,
    modifier: Modifier = Modifier
) = Spacer(size = Size.fillMaxSize(weight), modifier = modifier)
