package org.starficz.staruiframework.interfaces

import com.fs.starfarer.api.ui.CustomPanelAPI
import org.starficz.staruiframework.StarUIDsl

typealias StarUIBuilder = (@StarUIDsl CustomPanelAPI).() -> Unit

interface StarUIPlugin {
    val addPanelToTitleScreen: StarUIBuilder?
        get() = null

    val addPanelToCampaignUI: StarUIBuilder?
        get() = null

    val addPanelToCharacterTab: StarUIBuilder?
        get() = null

    val addPanelToFleetTab: StarUIBuilder?
        get() = null

    val addPanelToRefitTab: StarUIBuilder?
        get() = null

    val addPanelToCargoTab: StarUIBuilder?
        get() = null

    val addPanelToMapTab: StarUIBuilder?
        get() = null

    val addPanelToIntelTab: StarUIBuilder?
        get() = null

    val addPanelToOutpostsTab: StarUIBuilder?
        get() = null

    val addPanelAboveCombatShipInfo: StarUIBuilder?
        get() = null

    val addPanelBelowCombatShipInfo: StarUIBuilder?
        get() = null

    val addPanelToCombatScreen: StarUIBuilder?
        get() = null

    val addPanelToCombatWarroom: StarUIBuilder?
        get() = null
}
