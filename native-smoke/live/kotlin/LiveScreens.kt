package org.starficz.starui.livecheck

// TEST-ONLY. Compiled into the staged StarUI jar for the campaign checks and live sessions (build-jar.ps1), never
// shipped: a StarUIPlugin with a builder for every screen, each drawing a label "starui-screen <name>" and counting
// its builds, so a check (or a capture) can see which screen panels StarUIManager injected, and how often.

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.*
import org.starficz.starui.components.Button
import org.starficz.starui.components.Column
import org.starficz.starui.components.Text
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.background
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.getChildrenNonCopy
import java.awt.Color

object LiveScreens {
    const val PREFIX = "starui-screen "

    /** Builds so far, by screen name ("title", "campaign", "tab-CARGO", "shipinfo-above", ...). */
    private val builds = HashMap<String, Int>()

    @JvmStatic
    fun builds(name: String): Int = builds[name] ?: 0

    @JvmStatic
    fun register() = StarUIManager.registerPlugin(Plugin)

    private fun label(name: String): StarUIBuilder = {
        builds[name] = (builds[name] ?: 0) + 1
        Text(
            PREFIX + name,
            modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(8f, 8f)).background(Color(0, 0, 0, 200)),
            color = Color(80, 255, 120)
        )
    }

    private object Plugin : StarUIPlugin {
        override val addPanelToTitleScreen = label("title")
        override val addPanelToCampaignUI = label("campaign")
        override val addPanelToCharacterTab = label("tab-CHARACTER")
        override val addPanelToFleetTab = label("tab-FLEET")
        override val addPanelToRefitTab = label("tab-REFIT")
        override val addPanelToCargoTab = label("tab-CARGO")
        override val addPanelToMapTab = label("tab-MAP")
        override val addPanelToIntelTab = label("tab-INTEL")
        override val addPanelToOutpostsTab = label("tab-OUTPOSTS")
        override val addPanelAboveCombatShipInfo = label("shipinfo-above")
        override val addPanelBelowCombatShipInfo = label("shipinfo-below")
        override val addPanelToCombatScreen = label("combat")
        // the command map hands input to its children before its map: a button here shows whether both still work
        override val addPanelToCombatWarroom: StarUIBuilder = {
            builds["warroom"] = (builds["warroom"] ?: 0) + 1
            Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(8f, 120f)).background(Color(0, 0, 0, 200)), spacing = 4f) {
                Text(PREFIX + "warroom", color = Color(80, 255, 120))
                Text("button clicks: ${warroomClicks.value}")
                Button("StarUI test button") { warroomClicks.value++ }
            }
        }
    }

    /** Clicks on the warroom test button (its label follows it). */
    @JvmField val warroomClicks = UIState(0)

    /** The screen names labelled in [panel]'s screen panel ([below]: the one under it), comma separated; "" for none. */
    @JvmStatic
    fun screenNames(panel: UIPanelAPI?, below: Boolean): String {
        val root = panel?.injection(if (below) StarUIManager.SCREEN_BELOW_KEY else StarUIManager.SCREEN_KEY) ?: return ""
        val names = ArrayList<String>()
        collect(root, names)
        return names.joinToString(",")
    }

    /** How many screen panels (either key) [panel] holds as children: 1 per key at most, or something doubled. */
    @JvmStatic
    fun screenPanelCount(panel: UIPanelAPI?): Int = panel?.getChildrenNonCopy()?.count {
        val key = ((it as? CustomPanelAPI)?.plugin as? StarUIPanelPlugin)?.injectionKey
        key == StarUIManager.SCREEN_KEY || key == StarUIManager.SCREEN_BELOW_KEY
    } ?: 0

    /** Whether [panel]'s screen panel is its last child (drawn over everything else in it). */
    @JvmStatic
    fun screenPanelOnTop(panel: UIPanelAPI?): Boolean {
        val root = panel?.injection(StarUIManager.SCREEN_KEY) ?: return false
        return panel.getChildrenNonCopy().lastOrNull() === root
    }

    private fun collect(component: UIComponentAPI, out: MutableList<String>) {
        if (component is LabelAPI && component.text.startsWith(PREFIX)) out += component.text.removePrefix(PREFIX)
        if (component is UIPanelAPI) for (child in component.getChildrenNonCopy()) collect(child, out)
    }
}
