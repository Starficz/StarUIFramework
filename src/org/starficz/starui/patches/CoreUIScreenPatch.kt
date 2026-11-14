package org.starficz.starui.patches

import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.StarUIManager
import org.starficz.starui.window.Layers
import patchlib.api.context.AfterContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.match.MethodType
import patchlib.api.patch.After
import patchlib.api.patch.Patch

/**
 * The campaign core UI (the campaign's own, and an interaction dialog's) carries the campaign HUD and hosts the core
 * tabs. Its four-argument constructor builds all of it at screen size, so `addPanelToCampaignUI` is injected right
 * after it. The tabs themselves come through the API's `CoreUITabListener` (StarUIManager).
 *
 * `subtype = CoreUIAPI` (API) with four constructor parameters. The mission screen's core UI also implements
 * CoreUIAPI but its constructors take three or one, so it is not matched. Additive: nothing replaced.
 */
// 0.98.5a-RC4 Windows: com.fs.starfarer.ui.newui.oOOo(CampaignUIAPI impl, SectorEntityToken, boolean, panel), made by
// CampaignState (its core) and the encounter dialog; the class name is obfuscated, the shape is what is matched
@Patch(target = ClassMatch(subtype = CoreUIAPI::class))
internal object CoreUIScreenPatch {
    @JvmStatic
    @After(target = MethodMatch(methodType = MethodType.CONSTRUCTOR, parameterCount = 4))
    fun afterCreate(context: AfterContext) {
        val core = context.self as? UIPanelAPI ?: return
        StarUIManager.onCoreUICreated(core)
        Layers.afterCoreUICreated()
    }
}
