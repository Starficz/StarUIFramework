package org.starficz.starui.patches

import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.StarUIManager
import patchlib.api.context.AfterContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.match.MethodType
import patchlib.api.patch.After
import patchlib.api.patch.Patch

/**
 * The mission screen's refit (title screen, Missions, Refit) is its own core UI, a singleton
 * (`getMissionInstance()`) whose one tab, the refit, is made with it. `showAsDialog(parent, listener, dim)` puts it
 * on screen and switches its holo to the refit; `addPanelToRefitTab` is injected into that refit panel right after.
 * The campaign's `showNewInstanceAsDialog` refits are the same class and get it too.
 *
 * `subtype = CoreUIAPI` (API) plus the readable `showAsDialog` with three parameters (inferred: re-check after a
 * game update); the campaign core UI has no such method. Additive: nothing replaced.
 */
// 0.98.5a-RC4 Windows: com.fs.starfarer.coreui.p (extends com.fs.starfarer.ui.oooO, whose holo switches tabs)
@Patch(target = ClassMatch(subtype = CoreUIAPI::class))
internal object MissionRefitPatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "showAsDialog", parameterCount = 3, methodType = MethodType.METHOD))
    fun afterShow(context: AfterContext) {
        val missionUI = context.self as? UIPanelAPI ?: return
        StarUIManager.onMissionRefitShown(missionUI)
    }
}
