package org.starficz.starui.livecheck

// TEST-ONLY. Compiled into the staged StarUI jar for the campaign checks (build-jar.ps1), never shipped: the check
// mod is Java, and StarUI's DSL (extension functions, default arguments, builder lambdas) is easiest called from here.

import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.MapParams
import org.starficz.starui.Anchor
import org.starficz.starui.components.*
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.Size
import org.starficz.starui.UIState
import org.starficz.starui.vanilla.BoxedSectorMap
import org.starficz.starui.vanilla.sectorMap

object LivePorts {
    /** Height of the sector map; a change makes vanilla's map again (it is laid out once, at its size). */
    @JvmField val mapHeight = UIState(280f)

    /**
     * Builds the campaign-only ports into [panel] ([width] wide): the event progress bar across the top (vanilla's
     * bar is 750 wide), then skill panel and survey requirements on the left, sector map and playthrough record on
     * the right. Returns each port's StarUI panel.
     */
    @JvmStatic
    fun build(
        panel: CustomPanelAPI, width: Float, person: PersonAPI, intel: BaseEventIntel, planet: PlanetAPI, params: MapParams
    ): Map<String, CustomPanelAPI> {
        val made = LinkedHashMap<String, CustomPanelAPI>()
        val half = (width - 30f) / 2f
        panel.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(10f, 10f)), spacing = 10f) {
          Text("EventProgressBar")
          made["EventProgressBar"] = EventProgressBar(intel)
          Row(Size.wrapContent(), spacing = 10f) {
            Column(Size.mixed(org.starficz.starui.Axis.Exact(half), org.starficz.starui.Axis.WrapContent), spacing = 10f) {
                Text("SkillPanel")
                made["SkillPanel"] = SkillPanel(person)
                Text("SurveyRequirements")
                made["SurveyRequirements"] = SurveyRequirements(planet)
            }
            Column(Size.mixed(org.starficz.starui.Axis.Exact(half), org.starficz.starui.Axis.WrapContent), spacing = 10f) {
                Text("SectorMap")
                made["SectorMap"] = SectorMap(params, Size.fillMaxWidth(height = mapHeight.value), title = "StarUI sector map")
                Text("PlaythroughData")
                made["PlaythroughData"] = PlaythroughData(Size.fillMaxWidth(height = 200f))
            }
          }
        }
        return made
    }

    /** The boxed vanilla map of a SectorMap panel, or null before it is laid out. */
    @JvmStatic
    fun map(panel: CustomPanelAPI): BoxedSectorMap? = panel.sectorMap
}
