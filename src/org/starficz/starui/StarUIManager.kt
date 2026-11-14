package org.starficz.starui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.combat.CombatState
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import org.starficz.starui.components.CustomPanel
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.Scope
import org.starficz.starui.vanilla.*
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import java.lang.ref.WeakReference

/**
 * Puts the registered [StarUIPlugin]s' panels on vanilla's screens, and tells which of vanilla's screen panels are
 * showing.
 *
 * Each screen gets one full-size StarUI panel (an [inject]ion), holding a full-size panel for each plugin with a
 * builder for that screen, at the moment vanilla builds the screen: the campaign core UI when it is made, a core tab
 * when it opens (the API's [CoreUITabListener]), the combat UI when a battle sets it up, the warroom when it is first
 * shown, the title screen when it is made, and the mission screen's refit when it is shown. Nothing polls. A screen
 * that vanilla clears and refills gets its panel back.
 */
object StarUIManager {
    private val registeredPlugins = mutableListOf<StarUIPlugin>()

    private val anchorTL = Anchor.inside.topLeft.ofParent()

    /** The [inject] keys of the screen panels: over the screen, and (ship info only) under it. */
    internal const val SCREEN_KEY = "staruiframework:screen"
    internal const val SCREEN_BELOW_KEY = "staruiframework:screen-below"

    /**
     * The core UI on screen: in the campaign, the open interaction dialog's or else the campaign's
     * (`CampaignUIAPI.getCore2`); on the title screen, the mission screen's refit while it is open. `null` otherwise.
     */
    val coreUI: UIPanelAPI?
        get() = when (val state = AppDriver.getInstance().currentState) {
            is CampaignState -> Global.getSector()?.campaignUI?.core2 as? UIPanelAPI
            is TitleScreenState -> missionRefitOnScreen(state)?.first
            else -> null
        }

    /** The open core tab, [CoreUITabId.REFIT] for the mission screen's refit, or `null` when none is open. */
    val currentCoreUITabID: CoreUITabId?
        get() = when (val state = AppDriver.getInstance().currentState) {
            is CampaignState -> coreUI?.invoke("getCurrentTabId") as? CoreUITabId
            is TitleScreenState -> if (missionRefitOnScreen(state) != null) CoreUITabId.REFIT else null
            else -> null
        }

    /** The panel of the open core tab, or `null` when none is open. */
    val currentCoreUITabPanel: UIPanelAPI?
        get() = when (val state = AppDriver.getInstance().currentState) {
            is CampaignState -> coreUI?.invoke("getCurrentTab") as? UIPanelAPI
            is TitleScreenState -> missionRefitOnScreen(state)?.second
            else -> null
        }

    /** In combat, the ship info panel at the bottom left; `null` outside combat. */
    val combatShipInfo: UIPanelAPI?
        get() = (AppDriver.getInstance().currentState as? CombatState)?.invoke("getShipInfo") as? UIPanelAPI

    /** In combat, the widget panel: the whole screen, over the battle. `null` outside combat. */
    val combatWidgetPanel: UIPanelAPI?
        get() = (AppDriver.getInstance().currentState as? CombatState)?.invoke("getWidgetPanel") as? UIPanelAPI

    /**
     * In combat, the warroom (the command map), which covers the screen while it is shown; `null` outside combat and
     * before it is first shown.
     */
    val combatWarroom: UIPanelAPI?
        get() = (AppDriver.getInstance().currentState as? CombatState)?.invoke("getWarroom") as? UIPanelAPI

    /**
     * Diagnostics: the number of container scopes created and not yet disposed. UI that StarUI or a vanilla
     * `clearChildren()` removed is disposed, so on a steady screen this number stays flat. If it keeps growing while
     * the same UI is rebuilt, something is creating StarUI containers that are never torn down.
     */
    val liveScopeCount: Int
        get() = Scope.liveCount

    /**
     * Adds [plugin]'s panels to the screens vanilla builds from now on. Registering the same plugin twice does nothing.
     *
     * Call it in `onApplicationLoad`: a screen that is already built (the campaign core UI of a loaded game, an open
     * tab) gets the panels only when vanilla builds it again.
     *
     * @param plugin the plugin to add
     */
    fun registerPlugin(plugin: StarUIPlugin) {
        if (plugin !in registeredPlugins) registeredPlugins.add(plugin)
    }

    /**
     * Stops adding [plugin]'s panels to the screens vanilla builds from now on. Panels already on screen stay until
     * vanilla builds their screen again.
     *
     * @param plugin the plugin to remove
     */
    fun unregisterPlugin(plugin: StarUIPlugin) {
        registeredPlugins.remove(plugin)
    }

    internal fun initAtApplicationLoad() = VanillaClasses.findAtApplicationLoad()

    /** From `onGameLoad`: the core tabs come through the API's listener (transient: added again each load). */
    internal fun initAtGameLoad() {
        val listeners = Global.getSector().listenerManager
        if (!listeners.hasListener(CoreTabListener)) listeners.addListener(CoreTabListener, true)
    }

    private object CoreTabListener : CoreUITabListener {
        /** The tab panel showing when vanilla announced the new tab (weak: a closed tab's panel is not kept). */
        private var panelBefore: WeakReference<UIPanelAPI>? = null

        private fun currentTabPanel(): UIPanelAPI? =
            (Global.getSector()?.campaignUI?.core2 as? UIPanelAPI)?.invoke("getCurrentTab") as? UIPanelAPI

        override fun reportAboutToOpenCoreTab(tab: CoreUITabId, param: Any?) {
            panelBefore = currentTabPanel()?.let { WeakReference(it) }
        }

        // vanilla calls this right after CoreUI.showPanelAsDialog, which can return without showing the new panel
        // (a campaign dialog in the way); then the old tab's panel is still current and must not get this tab's UI
        override fun reportOpenedCoreTab(tab: CoreUITabId, param: Any?) {
            val panel = currentTabPanel() ?: return
            val before = panelBefore?.get()
            panelBefore = null
            if (panel === before) return
            injectScreen(panel) { plugin -> tabBuilder(plugin, tab) }
        }
    }

    private fun tabBuilder(plugin: StarUIPlugin, tab: CoreUITabId): StarUIBuilder? = when (tab) {
        CoreUITabId.CHARACTER -> plugin.addPanelToCharacterTab
        CoreUITabId.FLEET -> plugin.addPanelToFleetTab
        CoreUITabId.REFIT -> plugin.addPanelToRefitTab
        CoreUITabId.CARGO -> plugin.addPanelToCargoTab
        CoreUITabId.MAP -> plugin.addPanelToMapTab
        CoreUITabId.INTEL -> plugin.addPanelToIntelTab
        CoreUITabId.OUTPOSTS -> plugin.addPanelToOutpostsTab
    }

    // ---- from the screen hooks (patches/) ----

    /** A campaign core UI (the campaign's, or an interaction dialog's) was made. */
    internal fun onCoreUICreated(core: UIPanelAPI) = injectScreen(core) { it.addPanelToCampaignUI }

    /** A battle set up its UI (CombatState.resetSomeUIThings, early in traverse). */
    internal fun onCombatUICreated(state: Any) {
        (state.invoke("getShipInfo") as? UIPanelAPI)?.let { shipInfo ->
            injectScreen(shipInfo) { it.addPanelAboveCombatShipInfo }
            injectScreen(shipInfo, SCREEN_BELOW_KEY, InjectPlacement.Bottom) { it.addPanelBelowCombatShipInfo }
        }
        // shown over everything in combat, under vanilla's own widgets
        (state.invoke("getWidgetPanel") as? UIPanelAPI)?.let { widgets ->
            injectScreen(widgets, placement = InjectPlacement.Bottom) { it.addPanelToCombatScreen }
        }
    }

    /** The warroom (command map) was shown (CombatState.showWarroom): injected the first time, kept after. */
    internal fun onWarroomShown(state: Any) {
        val warroom = state.invoke("getWarroom") as? UIPanelAPI ?: return
        injectScreen(warroom) { it.addPanelToCombatWarroom }
    }

    /** The title screen was made (TitleScreenState.createUI). */
    internal fun onTitleScreenCreated(state: Any) {
        val title = state.invoke("getScreenPanel") as? UIPanelAPI ?: return
        VanillaClasses.findShipPreviewOnTitleScreen(title)
        injectScreen(title) { it.addPanelToTitleScreen }
    }

    /** The mission screen's core UI was shown (title screen, Missions, Refit; its one tab is the refit). */
    internal fun onMissionRefitShown(missionUI: UIPanelAPI) {
        val refit = missionRefitPanel(missionUI) ?: return
        injectScreen(refit) { it.addPanelToRefitTab }
    }

    /** The refit panel in the mission core UI's holo: the one being switched to, else the current one. */
    private fun missionRefitPanel(missionUI: UIPanelAPI): UIPanelAPI? {
        val holo = missionUI.findChildWithMethod("forceFoldIn") ?: return null
        return (holo.invoke("getNext") ?: holo.invoke("getCurr")) as? UIPanelAPI
    }

    /** The mission screen's core UI and its refit panel while they are on the title screen, or null. */
    private fun missionRefitOnScreen(state: TitleScreenState): Pair<UIPanelAPI, UIPanelAPI>? {
        val title = state.invoke("getScreenPanel") as? UIPanelAPI ?: return null
        val dialog = title.findChildWithMethod("dismiss") as? UIPanelAPI ?: return null
        val missionUI = dialog.findChildWithMethod("getMissionInstance") as? UIPanelAPI ?: return null
        val refit = missionRefitPanel(missionUI) ?: return null
        return Pair(missionUI, refit)
    }

    /**
     * Injects [target]'s screen panel under [key]: full size, holding a full-size panel for each registered plugin
     * with a builder for this screen ([builderOf]). Nothing when no plugin has one. Exact size when [target] already
     * has one (builders can read it while they build), else fill (the size comes at the first layout).
     */
    private fun injectScreen(
        target: UIPanelAPI, key: String = SCREEN_KEY, placement: InjectPlacement = InjectPlacement.Top,
        builderOf: (StarUIPlugin) -> StarUIBuilder?
    ) {
        if (registeredPlugins.none { builderOf(it) != null }) return
        val size = if (target.width > 1f && target.height > 1f) Size.exact(target.width, target.height) else Size.fillMaxSize()
        target.inject(key, size, Modifier.anchor(anchorTL), placement) {
            for (plugin in registeredPlugins.toList()) {
                val builder = builderOf(plugin) ?: continue
                CustomPanel(size, Modifier.anchor(anchorTL)) { builder() }
            }
        }
    }
}
