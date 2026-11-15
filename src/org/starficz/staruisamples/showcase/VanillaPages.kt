package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.PersonImportance
import com.fs.starfarer.api.combat.MutableStat
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HyperspaceTopographyEventIntel
import com.fs.starfarer.api.impl.codex.CodexDataV2
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.IconRenderMode
import com.fs.starfarer.api.ui.MapParams
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.VanillaColors

private const val FILE = "VanillaPages.kt"

// snippet: cargo
private val cargo = Global.getFactory().createCargo(true).apply {
    addSupplies(120f); addFuel(300f); addCrew(50); addCommodity("metals", 40f); addCommodity("heavy_machinery", 15f)
}
private val cargoVersion = UIState(0)
// end snippet

// snippet: tooltip-maker
private val hazard = UIState(2)
private val probes = UIState(0)
// end snippet

private fun ships(vararg variants: String) =
    variants.map { Global.getSettings().createFleetMember(FleetMemberType.SHIP, it) }

/** Whether a campaign is running: the ports that read the sector, the player fleet or intel need one. */
private fun inCampaign() = Global.getCurrentState() == GameState.CAMPAIGN && Global.getSector()?.playerFleet != null

/** The card's demo when there is a campaign, else a line saying so. */
private fun CustomPanelAPI.campaignOnly(demo: CustomPanelAPI.() -> Unit) {
    if (inCampaign()) demo()
    else Text("Needs a campaign: open the showcase from the campaign's \"StarUI showcase\" button.", Size.fillMaxWidth(),
        color = VanillaColors.gray)
}

private val stats = Page("Vanilla parts", "Stats and values",
    "Pieces of vanilla's tooltips and panels, as components: vanilla builds each one at the width it gets.", FILE) {

    card("StatGrid", "stat-grid", "Label and value pairs in columns, as in ship and fleet tooltips.") {
        // snippet: stat-grid
        StatGrid(columns = 2) {
            entry("Max speed", "120")
            entry("Armor", "1500", VanillaColors.negative)
            entry("Hull", "8000")
            entry("Flux capacity", "12000")
        }
        // end snippet
    }

    card("StatModGrid", "stat-mod", "A stat's modifiers, each with its source, as in the refit's tooltips.") {
        // snippet: stat-mod
        val stat = MutableStat(100f).apply {
            modifyFlat("hullmod", 20f, "Hardened Shields")
            modifyMult("skill", 1.25f, "Field Modulation")
        }
        StatModGrid(stat)
        // end snippet
    }

    card("LabelledValue", "labelled") {
        // snippet: labelled
        Row(Size.fillMaxWidth(), spacing = 8f) {
            LabelledValue("Credits", "12,000", Size.fillMaxWidth())
            LabelledValue("Story points", "3", Size.fillMaxWidth(), valueColor = VanillaColors.story)
        }
        // end snippet
    }

    card("RelationshipBar and ImportanceIndicator", "relations",
        "A relationship from -1 to 1; with a faction or a person it reads theirs (in a campaign).") {
        // snippet: relations
        RelationshipBar(0.35f, Size.fillMaxWidth())
        ImportanceIndicator(PersonImportance.HIGH, Size.fillMaxWidth())
        // end snippet
    }

    card("CommodityIcons", "commodity-icons", "Rows of commodity icons, as on vanilla's market and trade panels.") {
        // snippet: commodity-icons
        CommodityIcons {
            icons("supplies", 4)
            icons("fuel", 2)
            icons("heavy_machinery", 1, IconRenderMode.DIM)
        }
        // end snippet
    }

    card("CodexEntries", "codex", "Links into the codex, as at the bottom of vanilla's tooltips.") {
        // snippet: codex
        CodexEntries("Related entries", setOf(CodexDataV2.getShipEntryId("onslaught"), CodexDataV2.getShipEntryId("eagle")))
        // end snippet
    }

    card("TooltipMakerPanel", "tooltip-maker",
        "A last resort for TooltipMakerAPI content StarUI has no component for; most things are better built in StarUI.") {
        // snippet: tooltip-maker
        TooltipMakerPanel(Size.fillMaxWidth(), onButtonClick = { if (it == "probe") probes.value++ }) { width ->
            addTitle("Survey")
            addPara("Hazard rating: %s. Probes sent: %s.", 10f, VanillaColors.highlight,
                "${100 + 25 * hazard.value}%", "${probes.value}")
            addButton("Send probe", "probe", width / 2, 25f, 10f)
        }
        Button("Raise the hazard") { hazard.value = (hazard.value + 1) % 5 }
        // end snippet
    }

    card("SkillIcon", "skill-icon",
        "Vanilla's framed skill icon on its own: not learned, learned, elite, thin; any size.") {
        // snippet: skill-icon
        Row(spacing = 6f, verticalAlignment = Alignment.CenterVertically) {
            SkillIcon("helmsmanship", learned = false)
            SkillIcon("helmsmanship")
            SkillIcon("helmsmanship", elite = true)
            SkillIcon("target_analysis", Size.exact(28f, 28f), thinBorder = true)
            SkillIcon("point_defense", Size.exact(64f, 64f), elite = true,
                modifier = Modifier.tooltip("Point Defense, elite"))
        }
        // end snippet
    }
}

private val fleetAndCargo = Page("Vanilla parts", "Fleet and cargo",
    "Ships and cargo as vanilla lists them. They read live objects, so they are made again each time their builder runs.", FILE) {

    card("ShipList", "ship-list", "Ship icons in a grid, as in fleet tooltips.") {
        // snippet: ship-list
        ShipList(ships("onslaught_Standard", "eagle_Assault", "hammerhead_Balanced", "wolf_Assault"), columns = 4, iconSize = 40f)
        // end snippet
    }

    card("FleetSummary", "fleet-summary", "Counts by hull, as in vanilla's fleet tooltips.") {
        // snippet: fleet-summary
        FleetSummary(ships("onslaught_Standard", "eagle_Assault", "eagle_Assault", "wolf_Assault"), Size.fillMaxWidth())
        // end snippet
    }

    card("CargoList", "cargo",
        "Each stack's icon, name and quantity; two columns from 330 wide. Change the cargo, then a state, to show it.") {
        // snippet: cargo
        Row(spacing = 4f) {
            Button("+50 supplies") { cargo.addSupplies(50f); cargoVersion.value++ }
            Button("+5 marines") { cargo.addMarines(5); cargoVersion.value++ }
            Button("Empty") { cargo.clear(); cargoVersion.value++ }
        }
        cargoVersion.value // read, so this builder runs again when the cargo changes
        CargoList(cargo, Size.fillMaxWidth())
        CargoList(cargo, Size.mixed(Axis.Exact(200f), Axis.WrapContent), Modifier.background(INSET), max = 3)
        // end snippet
    }
}

private val campaign = Page("Vanilla parts", "In a campaign",
    "Parts that read the sector, the player's fleet or intel. On the title screen each says so.", FILE) {

    card("CostDisplay", "cost") {
        campaignOnly {
            // snippet: cost
            CostDisplay(title = "Survey costs", withAvailable = true) {
                cost("supplies", 40)
                cost("heavy_machinery", 10)
                cost("crew", 50, consumed = false)
            }
            // end snippet
        }
    }

    card("PlanetInfo and SurveyRequirements", "planet") {
        campaignOnly {
            // snippet: planet
            val planet = Global.getSector().playerFleet.containingLocation.planets.firstOrNull { !it.isStar }
            if (planet != null) Row(spacing = 8f) {
                PlanetInfo(planet, Size.exact(180f, 200f), withClass = true, showConditions = true)
                SurveyRequirements(planet, Size.mixed(Axis.Exact(300f), Axis.WrapContent))
            } else Text("No planet in this system.", color = VanillaColors.gray)
            // end snippet
        }
    }

    card("SkillPanel", "skills", "A person's skills by aptitude, as on the character screen.", wide = true) {
        campaignOnly {
            // snippet: skills
            SkillPanel(Global.getSector().playerPerson, Size.fillMaxWidth())
            // end snippet
        }
    }

    card("EventProgressBar", "event", "An event's bar, its stages and the progress marker (750 wide, as vanilla's).", wide = true) {
        campaignOnly {
            // snippet: event
            val event = HyperspaceTopographyEventIntel.get()
            if (event != null) EventProgressBar(event)
            else Text("No Hyperspace Topography event yet.", color = VanillaColors.gray)
            // end snippet
        }
    }

    card("SectorMap", "map", "Vanilla's map; .sectorMap gives the map itself (centre it, zoom, ping).", wide = true) {
        campaignOnly {
            // snippet: map
            val params = MapParams().apply {
                Global.getSector().playerFleet.starSystem?.let { showSystem(it) }
                positionToShowAllMarkersAndSystems(true, 300f)
            }
            SectorMap(params, Size.fillMaxWidth(height = 260f), title = "Here")
            // end snippet
        }
    }

    card("PlaythroughData", "playthrough", "The intel screen's record of the playthrough.", wide = true) {
        campaignOnly {
            // snippet: playthrough
            PlaythroughData(Size.fillMaxWidth(height = 220f))
            // end snippet
        }
    }
}

internal val vanillaPages = listOf(stats, fleetAndCargo, campaign)
