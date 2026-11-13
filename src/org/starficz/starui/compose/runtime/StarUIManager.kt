package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.combat.CombatState
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import org.starficz.starui.compose.components.CustomPanel
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.Size
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.plugin
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.ReflectionUtils.getFieldsWithMethodsMatching
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.VanillaClasses
import org.starficz.starui.core.children
import org.starficz.starui.core.findChildWithMethod
import org.starficz.starui.core.getChildrenNonCopy
import org.starficz.starui.core.height
import org.starficz.starui.core.width
import java.lang.ref.WeakReference

object StarUIManager {
    private val registeredPlugins = mutableListOf<StarUIPlugin>()
    private val dynamicInjections = mutableListOf<DynamicInjection>()
    private val dynamicInjectionElements = mutableMapOf<String, MutableList<WeakReference<UIComponentAPI>>>()

    private val anchorTL = Anchor.inside.topLeft.ofParent()

    internal var tabOverride: UIPanelAPI? = null
    internal var tabIDOverride: CoreUITabId? = null

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


    /**
     * Diagnostics: container scopes created and not yet disposed. UI that StarUI or a vanilla `clearChildren()`
     * removed is disposed, so in a steady screen this number stays flat; if it keeps growing while the same UI is
     * rebuilt, something is creating StarUI containers that are never torn down.
     */
    val liveScopeCount: Int
        get() = Scope.liveCount

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
            it is CustomPanelAPI && (it.plugin as? StarUIPanelPlugin)?.isInjectedRoot == true
        }
    }

    internal fun initAtApplicationLoad() = VanillaClasses.findAtApplicationLoad()

    internal fun advance() {
        val state = AppDriver.getInstance().currentState
        tabOverride = null
        tabIDOverride = null
        Scope.disposeDetachedPending()

        if (state !is CampaignState && state !is TitleScreenState) coreUI = null
        if (state !is CombatState) {
            combatShipInfo = null
            combatWidgetPanel = null
            combatWarroom = null
        }

        when (state) {
            is TitleScreenState -> {
                val title = state.invoke("getScreenPanel") as? UIPanelAPI
                if (title != null) {
                    VanillaClasses.findShipPreviewOnTitleScreen(title)
                    if (VanillaClasses.hasShipPreview) {
                        injectIfNeeded(title) { it.addPanelToTitleScreen }
                        coreUI = null
                        injectIntoMissionRefit(title)
                    }
                }
            }
            is CampaignState -> {
                val core = (state.invoke("getEncounterDialog")?.invoke("getCoreUI") ?: state.invoke("getCore")) as? CoreUIAPI
                if (core != null) {
                    coreUI = core as UIPanelAPI
                    injectIntoCoreUI(core)
                }
            }
            is CombatState -> {
                combatShipInfo = (state.invoke("getShipInfo") as UIPanelAPI?)?.also { shipInfo ->
                    if (!hasFrameworkPanel(shipInfo)) {
                        addInjectedRoot(shipInfo, onTop = true) { it.addPanelAboveCombatShipInfo }
                        addInjectedRoot(shipInfo, onTop = false) { it.addPanelBelowCombatShipInfo }
                    }
                }
                // shown over everything in combat
                combatWidgetPanel = (state.invoke("getWidgetPanel") as UIPanelAPI?)?.also { widgets ->
                    injectIfNeeded(widgets, onTop = false) { it.addPanelToCombatScreen }
                }
                combatWarroom = state.getFieldsWithMethodsMatching("getMapDisplay").firstOrNull()?.get(state) as UIPanelAPI?
                // not yet injected: addPanelToCombatWarroom needs a look first (it had a bug)
                // combatWarroom?.let { injectIfNeeded(it) { plugin -> plugin.addPanelToCombatWarroom } }
            }
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

    /** The mission screen's refit (title screen, Missions): injected as a REFIT tab. */
    private fun injectIntoMissionRefit(title: UIPanelAPI) {
        val delegateChild = title.findChildWithMethod("dismiss") as? UIPanelAPI ?: return
        val missionUI = delegateChild.findChildWithMethod("getMissionInstance") as? UIPanelAPI ?: return
        val holographicBG = missionUI.findChildWithMethod("forceFoldIn") ?: return
        val currentTabPanel = holographicBG.invoke("getCurr") as? UIPanelAPI ?: return

        coreUI = missionUI
        tabOverride = currentTabPanel
        tabIDOverride = CoreUITabId.REFIT
        injectIfNeeded(currentTabPanel) { it.addPanelToRefitTab }
    }

    private fun injectIntoCoreUI(coreUI: UIPanelAPI) {
        injectIfNeeded(coreUI) { it.addPanelToCampaignUI }
        val tab = currentCoreUITabPanel ?: return
        val tabId = currentCoreUITabID ?: return
        injectIfNeeded(tab) { plugin ->
            when (tabId) {
                CoreUITabId.CHARACTER -> plugin.addPanelToCharacterTab
                CoreUITabId.FLEET -> plugin.addPanelToFleetTab
                CoreUITabId.REFIT -> plugin.addPanelToRefitTab
                CoreUITabId.CARGO -> plugin.addPanelToCargoTab
                CoreUITabId.MAP -> plugin.addPanelToMapTab
                CoreUITabId.INTEL -> plugin.addPanelToIntelTab
                CoreUITabId.OUTPOSTS -> plugin.addPanelToOutpostsTab
            }
        }
    }

    /** [addInjectedRoot] unless [target] already has one (vanilla made the screen again otherwise). */
    private fun injectIfNeeded(target: UIPanelAPI, onTop: Boolean = true, builderOf: (StarUIPlugin) -> StarUIBuilder?) {
        if (!hasFrameworkPanel(target)) addInjectedRoot(target, onTop, builderOf)
    }

    /**
     * Adds a full-size panel over [target] ([onTop]) or under its other children, holding a full-size panel for each
     * registered plugin that has a builder for this screen ([builderOf]).
     */
    private fun addInjectedRoot(target: UIPanelAPI, onTop: Boolean, builderOf: (StarUIPlugin) -> StarUIBuilder?) {
        val size = Size.exact(target.width, target.height)
        val root = target.CustomPanel(size, Modifier.anchor(anchorTL).plugin { isInjectedRoot = true }) {
            registeredPlugins.forEach { plugin ->
                builderOf(plugin)?.let { builder -> CustomPanel(size, Modifier.anchor(anchorTL)) { builder() } }
            }
        }
        if (onTop) target.bringComponentToTop(root) else target.sendToBottom(root)
    }
}
