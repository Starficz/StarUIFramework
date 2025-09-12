package org.starficz.staruiframework

import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.combat.CombatState
import com.fs.starfarer.combat.entities.Ship
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import org.starficz.staruiframework.internal.ReflectionUtils.get
import org.starficz.staruiframework.internal.ReflectionUtils.getConstructorsMatching
import org.starficz.staruiframework.internal.ReflectionUtils.getFieldsWithMethodsMatching
import org.starficz.staruiframework.internal.ReflectionUtils.getMethodsMatching
import org.starficz.staruiframework.internal.ReflectionUtils.invoke

object StarUIManager {
    private val anchorTL = Anchor.inside.topLeft.ofParent()
    var inited = false

    private val registeredPlugins = mutableListOf<StarUIPlugin>()

    var coreUI: UIPanelAPI? = null
        internal set

    val currentCoreUITabID: CoreUITabId?
        get() = coreUI?.invoke("getCurrentTabId") as CoreUITabId?

    val currentCoreUITabPanel: UIPanelAPI?
        get() = coreUI?.invoke("getCurrentTab") as UIPanelAPI?

    var combatShipInfo: UIPanelAPI? = null
        internal set

    var combatWarroom: UIPanelAPI? = null
        internal set


    fun registerPlugin(plugin: StarUIPlugin) {
        if (plugin !in registeredPlugins) registeredPlugins.add(plugin)
    }

    fun unregisterPlugin(plugin: StarUIPlugin) {
        registeredPlugins.remove(plugin)
    }

    private fun hasFrameworkPanel(panel: UIPanelAPI): Boolean {
        return panel.getChildrenCopy().any {
            it is CustomPanelAPI &&
            it.plugin is StarUIPanelPlugin &&
            (it.plugin as StarUIPanelPlugin).customData == "StarUIFrameworkPanel"
        }
    }

    internal fun advance(){
        val state = AppDriver.getInstance().currentState

        if (state is TitleScreenState) {
            val title = state.invoke("getScreenPanel") as? UIPanelAPI ?: return
            cacheObfClassesIfNeeded(title)
            if(!inited) return
            injectPanelsIntoTitleScreenIfNeeded(title)
            coreUI = null
            combatShipInfo = null
            combatWarroom = null
        }
        else if (state is CampaignState){
            val core = (state.invoke("getEncounterDialog")?.invoke("getCoreUI") ?: state.invoke("getCore")) as? CoreUIAPI ?: return
            coreUI = core as UIPanelAPI
            injectPanelsIntoCoreUIIfNeeded(core)
            combatShipInfo = null
            combatWarroom = null
        }
        else if (state is CombatState){
            coreUI = null
            combatShipInfo = state.invoke("getShipInfo") as UIPanelAPI? ?: return
            injectShipInfoPanelsIntoCombatIfNeeded(combatShipInfo!!)

            combatWarroom = state.getFieldsWithMethodsMatching("getMapDisplay").firstOrNull()!!.get(state) as UIPanelAPI? ?: return
            injectWarroomPanelsIntoCombatIfNeeded(combatWarroom!!)
        }
        else{
            coreUI = null
            combatShipInfo = null
            combatWarroom = null
        }
    }

    private fun cacheObfClassesIfNeeded(title: UIPanelAPI) {
        if (BoxedUIShipPreview.SHIP_PREVIEW_CLASS != null) return

        val missionWidget = title.findChildWithMethod("getMissionList") as? UIPanelAPI ?: return
        val holographicBG = missionWidget.getChildrenCopy()[1] // 2 of the same class in the tree here

        val missionDetail = holographicBG.invoke("getCurr") as? UIPanelAPI ?: return

        val missionShipPreview = missionDetail.findChildWithMethod("setVariant") as? UIPanelAPI ?: return

        val shipPreview = missionShipPreview.findChildWithMethod("isSchematicMode") ?: return

        BoxedUIShipPreview.SHIP_PREVIEW_CLASS = shipPreview.javaClass

        val constructors = BoxedUIShipPreview.SHIP_PREVIEW_CLASS!!.getConstructorsMatching()

        BoxedUIShipPreview.FLEETMEMBER_CONSTRUCTOR = constructors.find { constructor ->
            constructor.parameterTypes.firstOrNull()?.let { FleetMemberAPI::class.java.isAssignableFrom(it) } ?: false
        }
        BoxedUIShipPreview.ENUM_CONSTRUCTOR = constructors.find { it.parameterTypes.size == 2 }
        BoxedUIShipPreview.ENUM_ARRAY = BoxedUIShipPreview.ENUM_CONSTRUCTOR!!.parameterTypes.first().enumConstants

        inited = true
    }

    private fun injectPanelsIntoTitleScreenIfNeeded(title: UIPanelAPI) {
        if (!hasFrameworkPanel(title)) {
            val newCustomPanel = title.CustomPanel(title.width, title.height, anchorTL) {
                Plugin { customData = "StarUIFrameworkPanel" }

                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelToTitleScreen?.let { builder ->
                        CustomPanel(title.width, title.height, anchorTL) { builder() }
                    }
                }
            }
            title.bringComponentToTop(newCustomPanel)
        }
    }

    private fun injectWarroomPanelsIntoCombatIfNeeded(warroomPanel: UIPanelAPI) {
        if (!hasFrameworkPanel(warroomPanel)) {
            val newCustomPanel = warroomPanel.CustomPanel(warroomPanel.width, warroomPanel.height, anchorTL) {
                Plugin { customData = "StarUIFrameworkPanel" }

                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelToCombatWarroom?.let { builder ->
                        CustomPanel(warroomPanel.width, warroomPanel.height, anchorTL) { builder() }
                    }
                }
            }
            warroomPanel.bringComponentToTop(newCustomPanel)
        }
    }

    private fun injectShipInfoPanelsIntoCombatIfNeeded(shipInfoPanel: UIPanelAPI) {

        if (!hasFrameworkPanel(shipInfoPanel)) {
            val abovePanel = shipInfoPanel.CustomPanel(shipInfoPanel.width, shipInfoPanel.height, anchorTL) {
                Plugin { customData = "StarUIFrameworkPanel" }

                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelAboveCombatShipInfo?.let { builder ->
                        CustomPanel(shipInfoPanel.width, shipInfoPanel.height, anchorTL) { builder() }
                    }
                }
            }
            shipInfoPanel.bringComponentToTop(abovePanel)

            val belowPanel = shipInfoPanel.CustomPanel(shipInfoPanel.width, shipInfoPanel.height, anchorTL) {
                Plugin { customData = "StarUIFrameworkPanel" }

                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelBelowCombatShipInfo?.let { builder ->
                        CustomPanel(shipInfoPanel.width, shipInfoPanel.height, anchorTL) { builder() }
                    }
                }
            }
            shipInfoPanel.sendToBottom(belowPanel)
        }
    }

    private fun injectPanelsIntoCoreUIIfNeeded(coreUI: UIPanelAPI) {
        // inject coreUI if needed

        if (!hasFrameworkPanel(coreUI)) {
            val newCustomPanel = coreUI.CustomPanel(coreUI.width, coreUI.height, anchorTL) {
                Plugin { customData = "StarUIFrameworkPanel" }

                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelToCampaignUI?.let { builder ->
                        CustomPanel(coreUI.width, coreUI.height, anchorTL) { builder() }
                    }
                }
            }
            coreUI.bringComponentToTop(newCustomPanel)
        }

        // inject into current tab if needed
        currentCoreUITabPanel?.let { parent ->
            if (!hasFrameworkPanel(parent)) {
                val newCustomPanel = parent.CustomPanel(parent.width, parent.height, anchorTL) {
                    Plugin { customData = "StarUIFrameworkPanel" }

                    registeredPlugins.forEach { starUIPlugin ->
                        when(currentCoreUITabID){
                            CoreUITabId.CHARACTER -> starUIPlugin.addPanelToCharacterTab
                            CoreUITabId.FLEET -> starUIPlugin.addPanelToFleetTab
                            CoreUITabId.REFIT -> starUIPlugin.addPanelToRefitTab
                            CoreUITabId.CARGO -> starUIPlugin.addPanelToCargoTab
                            CoreUITabId.MAP -> starUIPlugin.addPanelToMapTab
                            CoreUITabId.INTEL -> starUIPlugin.addPanelToIntelTab
                            CoreUITabId.OUTPOSTS -> starUIPlugin.addPanelToOutpostsTab
                            null -> null
                        }?.let { builder ->
                            CustomPanel(parent.width, parent.height, anchorTL) { builder() }
                        }
                    }
                }
                parent.bringComponentToTop(newCustomPanel)
            }
        }
    }
}
