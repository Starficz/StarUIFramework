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
 * Component that represents an empty space layout, whose size is its [size]. By default it fills the space it is
 * given.
 *
 * A [Spacer] draws nothing; `Modifier.background(color)` makes it a block of colour.
 *
 * @param size the size of the [Spacer]
 * @param modifier the [Modifier] to be applied to this spacer
 * @return the spacer's panel
 * @see FlexSpacer
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

/**
 * A divider is a thin line that groups content in lists and layouts. It is a horizontal line across the full width.
 *
 * @param modifier the [Modifier] to be applied to this divider line
 * @param thickness thickness of this divider line, in pixels
 * @param color color of this divider line. By default the player faction's dark UI colour
 * @return the divider's panel
 */
fun UIPanelAPI.Divider(
    modifier: Modifier = Modifier,
    thickness: Float = StarUITheme.current.divider.thickness,
    color: Color = StarUITheme.current.divider.color ?: VanillaColors.dark
) = Spacer(
    size = Size.mixed(Axis.FillMax(), Axis.Exact(thickness)),
    modifier = Modifier.background(color) then modifier
)

/**
 * An empty space that takes the space left over in a [Row] or [Column], pushing what comes after it to the far end.
 *
 * Its size is proportional to its [weight] relative to other filling siblings: the parent divides the space
 * remaining after measuring the children that do not fill, and distributes it according to the weights.
 *
 * @param weight the proportional size to give to this spacer, as related to the total of all filling siblings
 * @param modifier the [Modifier] to be applied to this spacer
 * @return the spacer's panel
 */
fun UIPanelAPI.FlexSpacer(
    weight: Float = 1f,
    modifier: Modifier = Modifier
) = Spacer(size = Size.fillMaxSize(weight), modifier = modifier)
