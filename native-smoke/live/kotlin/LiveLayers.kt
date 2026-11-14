package org.starficz.starui.livecheck

// TEST-ONLY. Compiled into the staged StarUI jar for the campaign checks (build-jar.ps1), never shipped: layers for
// the "layers" group (LayerChecks.java), which cannot write Kotlin builders itself.

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.*
import org.starficz.starui.components.Text
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.align
import org.starficz.starui.modifier.background
import org.starficz.starui.vanilla.Font
import org.starficz.starui.vanilla.children
import org.starficz.starui.window.StarUILayer
import org.starficz.starui.window.showLayer
import java.awt.Color

object LiveLayers {
    /** A layer showing "starui-layer [name]" in the middle of the screen. */
    @JvmStatic
    fun open(name: String, modal: Boolean): StarUILayer = showLayer(modal = modal) {
        Text("starui-layer $name", font = Font.ORBITRON_20, color = Color(80, 255, 120),
            modifier = Modifier.align(Alignment.Center).background(Color(0, 0, 0, 200)))
    }

    /** [component]'s place among the screen panel's children, or -1. */
    @JvmStatic
    fun index(component: Any?): Int = Global.getSettings().screenPanel?.children?.indexOfFirst { it === component } ?: -1

    @JvmStatic
    fun index(layer: StarUILayer): Int = index(layer.panel)

    /** Takes the layer's panel off the screen panel, as the campaign's core rebuild does. */
    @JvmStatic
    fun detach(layer: StarUILayer) {
        Global.getSettings().screenPanel?.removeComponent(layer.panel as UIComponentAPI)
    }
}
