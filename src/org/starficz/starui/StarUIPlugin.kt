package org.starficz.starui

import com.fs.starfarer.api.ui.CustomPanelAPI

/**
 * A builder: a block that adds StarUI components to the panel it is called on. Every container's trailing block is
 * one, and so is each screen panel of a [StarUIPlugin].
 */
typealias StarUIBuilder = (@StarUIDsl CustomPanelAPI).() -> Unit

/**
 * Puts a mod's UI on vanilla's screens. Override the builder of each screen the mod adds to, and register the
 * plugin with [StarUIManager.registerPlugin] in `onApplicationLoad`:
 *
 * ```kotlin
 * object MyScreens : StarUIPlugin {
 *     override val addPanelToRefitTab: StarUIBuilder = {
 *         Button("My tool", modifier = Modifier.anchor(Anchor.inside.topRight.ofParent(10f, 10f))) { openTool() }
 *     }
 * }
 * ```
 *
 * Each builder runs in a full-size panel over its screen, added at the moment vanilla builds the screen, and again
 * after vanilla clears and refills it. Nothing polls. A builder left `null` (the default) adds nothing. A builder is
 * reactive like any other: a [UIState] it reads rebuilds the panel.
 *
 * For a vanilla panel that is not one of these screens, use [inject] from a PatchLib hook instead.
 */
interface StarUIPlugin {
    /** The title screen, built when it is made. */
    val addPanelToTitleScreen: StarUIBuilder?
        get() = null

    /**
     * The campaign's core UI: the campaign screen, and the core UI of each interaction dialog, built when it is made.
     */
    val addPanelToCampaignUI: StarUIBuilder?
        get() = null

    /** The character tab, built each time it opens. */
    val addPanelToCharacterTab: StarUIBuilder?
        get() = null

    /** The fleet tab, built each time it opens. */
    val addPanelToFleetTab: StarUIBuilder?
        get() = null

    /**
     * The refit tab, built each time it opens; also the mission screen's refit on the title screen, each time it is
     * shown.
     */
    val addPanelToRefitTab: StarUIBuilder?
        get() = null

    /** The cargo tab, built each time it opens. */
    val addPanelToCargoTab: StarUIBuilder?
        get() = null

    /** The map tab, built each time it opens. */
    val addPanelToMapTab: StarUIBuilder?
        get() = null

    /** The intel tab, built each time it opens. */
    val addPanelToIntelTab: StarUIBuilder?
        get() = null

    /** The outposts (colonies) tab, built each time it opens. */
    val addPanelToOutpostsTab: StarUIBuilder?
        get() = null

    /**
     * Combat: the ship info panel at the bottom left, drawn over vanilla's own content. Built when a battle sets up its
     * UI.
     */
    val addPanelAboveCombatShipInfo: StarUIBuilder?
        get() = null

    /**
     * Combat: the ship info panel at the bottom left, drawn under vanilla's own content. Built when a battle sets up
     * its UI.
     */
    val addPanelBelowCombatShipInfo: StarUIBuilder?
        get() = null

    /**
     * Combat: the whole screen, over the battle and under vanilla's own combat widgets. Built when a battle sets up its
     * UI.
     */
    val addPanelToCombatScreen: StarUIBuilder?
        get() = null

    /**
     * Combat: the warroom (the command map), built the first time it is shown. The warroom hands input to its children
     * before its map, so a control here takes the clicks on it before the map sees them.
     */
    val addPanelToCombatWarroom: StarUIBuilder?
        get() = null
}
