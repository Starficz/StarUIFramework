package org.starficz.starui.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.Axis
import org.starficz.starui.BoundedSize
import org.starficz.starui.modifier.background
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.Size
import org.starficz.starui.StarUITheme
import org.starficz.starui.vanilla.VanillaColors
import java.awt.Color

/**
 * Empty space of [size] (exact or fill). Fills the space it is given by default. Draws nothing; add
 * `Modifier.background(color)` for a coloured block.
 */
fun UIPanelAPI.Spacer(
    size: BoundedSize = Size.fillMaxSize(),
    modifier: Modifier = Modifier
): CustomPanelAPI {
    checkDslTarget()

    val (initW, initH) = getInitialBounds(size)

    val slot = childSlot("Spacer")
    val panel: CustomPanelAPI = slot.reused() ?: run {
        val plugin = StarUIPanelPlugin()
        val made = Global.getSettings().createCustom(initW, initH, plugin)
        plugin.customPanel = made
        this.addComponent(made)
        made
    }
    finishLeaf(slot, panel, size, modifier, measure = { _, _ -> Pair(initW, initH) })
    return panel
}

/** A horizontal line [thickness] pixels tall across the full width. */
fun UIPanelAPI.Divider(
    modifier: Modifier = Modifier,
    thickness: Float = StarUITheme.current.divider.thickness,
    color: Color = StarUITheme.current.divider.color ?: VanillaColors.dark
) = Spacer(
    size = Size.mixed(Axis.FillMax(), Axis.Exact(thickness)),
    modifier = Modifier.background(color) then modifier
)

/**
 * Takes all space left over in a Row or Column, pushing what comes after it to the far end. Several share the space
 * by [weight].
 */
fun UIPanelAPI.FlexSpacer(
    weight: Float = 1f,
    modifier: Modifier = Modifier
) = Spacer(size = Size.fillMaxSize(weight), modifier = modifier)
