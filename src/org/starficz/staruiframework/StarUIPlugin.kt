package org.starficz.staruiframework

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI

interface StarUIPlugin {
    val addPanelToTitleScreen: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelToCampaignUI: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelToCharacterTab: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelToFleetTab: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelToRefitTab: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelToCargoTab: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelToMapTab: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelToIntelTab: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelToOutpostsTab: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelAboveCombatShipInfo: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelBelowCombatShipInfo: (CustomPanelAPI.() -> Unit)?
        get() = null

    val addPanelToCombatWarroom: (CustomPanelAPI.() -> Unit)?
        get() = null
}
