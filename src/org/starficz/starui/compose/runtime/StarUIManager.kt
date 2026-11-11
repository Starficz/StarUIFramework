package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.combat.CombatState
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import patchlib.api.PatchLib
import patchlib.api.query.ClassQuery
import patchlib.api.query.MethodQuery
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.PanelSize
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.plugin
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.BoxedUIShipPreview
import org.starficz.starui.core.ReflectionUtils.getConstructorsMatching
import org.starficz.starui.core.ReflectionUtils.getFieldsWithMethodsMatching
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.children
import org.starficz.starui.core.findChildWithMethod
import org.starficz.starui.core.getChildrenCopy
import org.starficz.starui.core.getChildrenNonCopy
import org.starficz.starui.core.height
import org.starficz.starui.core.width
import java.lang.ref.WeakReference

object StarUIManager {
    private val registeredPlugins = mutableListOf<StarUIPlugin>()
    private val dynamicInjections = mutableListOf<DynamicInjection>()
    private val dynamicInjectionElements = mutableMapOf<String, MutableList<WeakReference<UIComponentAPI>>>()

    private val anchorTL = Anchor.inside.topLeft.ofParent()
    var inited = false

    var tabOverride: UIPanelAPI? = null
    var tabIDOverride: CoreUITabId? = null

    var coreUI: UIPanelAPI? = null
        internal set

    val currentCoreUITabID: CoreUITabId?
        get() = tabIDOverride ?: coreUI?.invoke("getCurrentTabId") as CoreUITabId?

    val currentCoreUITabPanel: UIPanelAPI?
        get() = tabOverride ?: coreUI?.invoke("getCurrentTab") as UIPanelAPI?

    var combatShipInfo: UIPanelAPI? = null
        internal set

    var combatWidgetPanel: UIPanelAPI? = null
        internal set

    var combatWarroom: UIPanelAPI? = null
        internal set


    fun registerPlugin(plugin: StarUIPlugin) {
        if (plugin !in registeredPlugins) registeredPlugins.add(plugin)
    }

    fun registerDynamicInjection(injection: DynamicInjection) {
        if (injection !in dynamicInjections) dynamicInjections.add(injection)
    }

    fun unregisterPlugin(plugin: StarUIPlugin) {
        registeredPlugins.remove(plugin)
    }

    // Runs every frame for every injection target, so it reads the live child list instead of a fresh copy.
    private fun hasFrameworkPanel(panel: UIPanelAPI): Boolean {
        return panel.getChildrenNonCopy().any {
            it is CustomPanelAPI &&
                    it.plugin is StarUIPanelPlugin &&
                    (it.plugin as StarUIPanelPlugin).customData == "StarUIFrameworkPanel"
        }
    }

    internal fun advance(){
        val state = AppDriver.getInstance().currentState
        tabOverride = null
        tabIDOverride = null

        if (state is TitleScreenState) {
            val title = state.invoke("getScreenPanel") as? UIPanelAPI ?: return
            cacheObfClassesIfNeeded(title)
            if(!inited) return
            injectPanelsIntoTitleScreenIfNeeded(title)
            coreUI = null
            injectPanelsIntoMissionUIIfNeeded(title)
            combatShipInfo = null
            combatWarroom = null
        }
        else if (state is CampaignState){
            val core = (state.invoke("getEncounterDialog")?.invoke("getCoreUI") ?: state.invoke("getCore")) as? CoreUIAPI
                ?: return
            coreUI = core as UIPanelAPI
            injectPanelsIntoCoreUIIfNeeded(core)
            combatShipInfo = null
            combatWarroom = null
        }
        else if (state is CombatState){
            coreUI = null
            combatShipInfo = state.invoke("getShipInfo") as UIPanelAPI? ?: return
            injectShipInfoPanelsIntoCombatIfNeeded(combatShipInfo!!)

            combatWidgetPanel = state.invoke("getWidgetPanel") as UIPanelAPI? ?: return
            injectWidgetPanelsIntoCombatIfNeeded(combatWidgetPanel!!)

            combatWarroom = state.getFieldsWithMethodsMatching("getMapDisplay").firstOrNull()!!.get(state) as UIPanelAPI? ?: return
            //injectWarroomPanelsIntoCombatIfNeeded(combatWarroom!!)
        }
        else{
            coreUI = null
            combatShipInfo = null
            combatWarroom = null
        }

        processDynamicInjections()
    }

    private fun processDynamicInjections() {
        // Snapshot: an injection may register further injections while it runs.
        dynamicInjections.toList().forEach { injection ->
            val targetPanel = injection.findTarget() ?: return@forEach

            val trackedList = dynamicInjectionElements.getOrPut(injection.id) { mutableListOf() }

            trackedList.removeAll { it.get() == null }

            // One read of the live child list per frame (this used to copy the list once per tracked element).
            val liveChildren = targetPanel.getChildrenNonCopy()
            val alreadyInjected = trackedList.any { ref -> ref.get()?.let { it in liveChildren } == true }

            if (!alreadyInjected) {
                val childrenBefore = targetPanel.children.toSet()

                with(injection) { targetPanel.injectUI() }

                val newlyAdded = targetPanel.children.toSet() - childrenBefore

                newlyAdded.forEach { trackedList.add(WeakReference(it)) }
            }
        }
    }

    internal fun initAtApplicationLoad() {
        if (BoxedUIShipPreview.SHIP_PREVIEW_CLASS != null) return

        // 0.98a-RC8 Windows: matches com.fs.starfarer.title.Object.do (Holo / ShipPreview)
        val shipPreviewQuery = ClassQuery.create()
            .subtype(UIPanelAPI::class.java)
            .hasMethod(MethodQuery.create().methodName("isSchematicMode").parameterCount(0))
            .hasMethod(MethodQuery.create().methodName("setScaleDownSmallerShipsMagnitude").parameterCount(1))
            .build()

        val foundClass = PatchLib.scan(shipPreviewQuery).firstOrNull()?.type ?: return
        populateShipPreviewReflection(foundClass)
    }

    private fun populateShipPreviewReflection(clazz: Class<*>) {
        BoxedUIShipPreview.SHIP_PREVIEW_CLASS = clazz

        val constructors = clazz.getConstructorsMatching()

        BoxedUIShipPreview.FLEETMEMBER_CONSTRUCTOR = constructors.find { constructor ->
            constructor.parameterTypes.firstOrNull()?.let { FleetMemberAPI::class.java.isAssignableFrom(it) } ?: false
        }
        BoxedUIShipPreview.ENUM_CONSTRUCTOR = constructors.find { it.parameterTypes.size == 2 }
        BoxedUIShipPreview.ENUM_ARRAY = BoxedUIShipPreview.ENUM_CONSTRUCTOR!!.parameterTypes.first().enumConstants

        inited = true
    }

    private fun cacheObfClassesIfNeeded(title: UIPanelAPI) {
        if (BoxedUIShipPreview.SHIP_PREVIEW_CLASS != null) return

        val missionWidget = title.findChildWithMethod("getMissionList") as? UIPanelAPI ?: return
        val holographicBG = missionWidget.getChildrenCopy()[1] // 2 of the same class in the tree here

        val missionDetail = holographicBG.invoke("getCurr") as? UIPanelAPI ?: return

        val missionShipPreview = missionDetail.findChildWithMethod("setVariant") as? UIPanelAPI ?: return

        val shipPreview = missionShipPreview.findChildWithMethod("isSchematicMode") ?: return

        populateShipPreviewReflection(shipPreview.javaClass)
    }

    private fun injectPanelsIntoTitleScreenIfNeeded(title: UIPanelAPI) {
        if (!hasFrameworkPanel(title)) {
            val newCustomPanel = title.CustomPanel(
                PanelSize.exact(title.width, title.height),
                Modifier.anchor(anchorTL).plugin { customData = "StarUIFrameworkPanel" }
            ) {
                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelToTitleScreen?.let { builder ->
                        CustomPanel(PanelSize.exact(title.width, title.height), Modifier.anchor(anchorTL)) { builder() }
                    }
                }
            }
            title.bringComponentToTop(newCustomPanel)
        }
    }

    private fun injectPanelsIntoMissionUIIfNeeded(title: UIPanelAPI) {
        val delegateChild = title.findChildWithMethod("dismiss") as? UIPanelAPI ?: return
        val missionUI = delegateChild.findChildWithMethod("getMissionInstance") as? UIPanelAPI ?: return
        val holographicBG = missionUI.findChildWithMethod("forceFoldIn") ?: return

        val currentTabPanel = holographicBG.invoke("getCurr") as? UIPanelAPI ?: return

        coreUI = missionUI
        tabOverride = currentTabPanel
        tabIDOverride = CoreUITabId.REFIT

        injectPanelsIntoMainMenuRefitIfNeeded(currentTabPanel)
    }


    private fun injectWarroomPanelsIntoCombatIfNeeded(warroomPanel: UIPanelAPI) {
        if (!hasFrameworkPanel(warroomPanel)) {
            val newCustomPanel = warroomPanel.CustomPanel(
                PanelSize.exact(warroomPanel.width, warroomPanel.height),
                Modifier.anchor(anchorTL).plugin { customData = "StarUIFrameworkPanel" }
            ) {
                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelToCombatWarroom?.let { builder ->
                        CustomPanel(PanelSize.exact(warroomPanel.width, warroomPanel.height), Modifier.anchor(anchorTL)) { builder() }
                    }
                }
            }
            warroomPanel.bringComponentToTop(newCustomPanel)
        }
    }

    private fun injectShipInfoPanelsIntoCombatIfNeeded(shipInfoPanel: UIPanelAPI) {

        if (!hasFrameworkPanel(shipInfoPanel)) {
            val abovePanel = shipInfoPanel.CustomPanel(
                PanelSize.exact(shipInfoPanel.width, shipInfoPanel.height),
                Modifier.anchor(anchorTL).plugin { customData = "StarUIFrameworkPanel" }
            ) {
                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelAboveCombatShipInfo?.let { builder ->
                        CustomPanel(PanelSize.exact(shipInfoPanel.width, shipInfoPanel.height), Modifier.anchor(anchorTL)) { builder() }
                    }
                }
            }
            shipInfoPanel.bringComponentToTop(abovePanel)

            val belowPanel = shipInfoPanel.CustomPanel(
                PanelSize.exact(shipInfoPanel.width, shipInfoPanel.height),
                Modifier.anchor(anchorTL).plugin { customData = "StarUIFrameworkPanel" }
            ) {
                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelBelowCombatShipInfo?.let { builder ->
                        CustomPanel(PanelSize.exact(shipInfoPanel.width, shipInfoPanel.height), Modifier.anchor(anchorTL)) { builder() }
                    }
                }
            }
            shipInfoPanel.sendToBottom(belowPanel)
        }
    }

    // warning! this will display over everything!
    private fun injectWidgetPanelsIntoCombatIfNeeded(widgetPanel: UIPanelAPI) {

        if (!hasFrameworkPanel(widgetPanel)) {
            val combatPanel = widgetPanel.CustomPanel(
                PanelSize.exact(widgetPanel.width, widgetPanel.height),
                Modifier.anchor(anchorTL).plugin { customData = "StarUIFrameworkPanel" }
            ) {
                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelToCombatScreen?.let { builder ->
                        CustomPanel(PanelSize.exact(widgetPanel.width, widgetPanel.height), Modifier.anchor(anchorTL)) { builder() }
                    }
                }
            }
            widgetPanel.sendToBottom(combatPanel)
        }
    }

    private fun injectPanelsIntoMainMenuRefitIfNeeded(tabPanel: UIPanelAPI){
        if (!hasFrameworkPanel(tabPanel)) {
            val newCustomPanel = tabPanel.CustomPanel(
                PanelSize.exact(tabPanel.width, tabPanel.height),
                Modifier.anchor(anchorTL).plugin { customData = "StarUIFrameworkPanel" }
            ) {
                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelToRefitTab?.let{ builder ->
                        CustomPanel(PanelSize.exact(tabPanel.width, tabPanel.height), Modifier.anchor(anchorTL)) { builder() }
                    }
                }
            }
            tabPanel.bringComponentToTop(newCustomPanel)
        }
    }

    private fun injectPanelsIntoCoreUIIfNeeded(coreUI: UIPanelAPI) {
        // inject coreUI if needed

        if (!hasFrameworkPanel(coreUI)) {
            val newCustomPanel = coreUI.CustomPanel(
                PanelSize.exact(coreUI.width, coreUI.height),
                Modifier.anchor(anchorTL).plugin { customData = "StarUIFrameworkPanel" }
            ) {
                registeredPlugins.forEach { starUIPlugin ->
                    starUIPlugin.addPanelToCampaignUI?.let { builder ->
                        CustomPanel(PanelSize.exact(coreUI.width, coreUI.height), Modifier.anchor(anchorTL)) { builder() }
                    }
                }
            }
            coreUI.bringComponentToTop(newCustomPanel)
        }

        // inject into current tab if needed
        currentCoreUITabPanel?.let { parent ->
            if (!hasFrameworkPanel(parent)) {
                val newCustomPanel = parent.CustomPanel(
                    PanelSize.exact(parent.width, parent.height),
                    Modifier.anchor(anchorTL).plugin { customData = "StarUIFrameworkPanel" }
                ) {
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
                            CustomPanel(PanelSize.exact(parent.width, parent.height), Modifier.anchor(anchorTL)) { builder() }
                        }
                    }
                }
                parent.bringComponentToTop(newCustomPanel)
            }
        }
    }
}