package org.starficz.starui

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.campaign.VisualPanelAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import org.starficz.starui.components.Box
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.background
import java.awt.Color

/**
 * Shows a StarUI panel in the interaction dialog's picture area, the side panel of vanilla's market and bar screens,
 * in place of the picture. Vanilla does not draw anything behind a custom panel there, so the campaign map would show
 * through: the panel gets a [background], black by default.
 *
 * The content is a [Box] of exactly [width] x [height], built by [builder] and reactive like any builder.
 *
 * ```kotlin
 * dialog.visualPanel.showStarUIPanel(440f, 600f) { Column(Size.fillMaxSize()) { ... } }
 * ```
 *
 * @param width the width of the panel
 * @param height the height of the panel
 * @param background the colour drawn behind the content, or `null` for none (the map shows through)
 * @param builder builds the content
 * @return the vanilla panel holding it
 */
fun VisualPanelAPI.showStarUIPanel(
    width: Float,
    height: Float,
    background: Color? = Color.BLACK,
    builder: StarUIBuilder
): CustomPanelAPI {
    val panel = showCustomPanel(width, height, BaseCustomUIPanelPlugin())
    panel.Box(Size.exact(width, height), if (background != null) Modifier.background(background) else Modifier) { builder() }
    return panel
}
