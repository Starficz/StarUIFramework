package org.starficz.staruiframework

import com.fs.starfarer.api.ui.UIPanelAPI

interface StarUIPlugin {
    val addPanelToTitleScreen: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelToCampaignUI: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelToCharacterTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelToFleetTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelToRefitTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelToCargoTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelToMapTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelToIntelTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelToOutpostsTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelAboveCombatShipInfo: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelBelowCombatShipInfo: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null

    val addPanelToCombatWarroom: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit)?
        get() = null
}
