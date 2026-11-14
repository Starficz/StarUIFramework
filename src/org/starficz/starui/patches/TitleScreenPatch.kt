package org.starficz.starui.patches

import com.fs.starfarer.title.TitleScreenState
import org.starficz.starui.StarUIManager
import patchlib.api.context.AfterContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.match.MethodType
import patchlib.api.patch.After
import patchlib.api.patch.Patch

/**
 * The title screen's panels are made in `TitleScreenState.createUI()` (from `prepare()`, each time the game returns
 * to the title screen, except when it goes on to the combat simulator). `addPanelToTitleScreen` is injected right
 * after it.
 *
 * `TitleScreenState` and `createUI` are readable engine names, not API (inferred): re-check them after a game update.
 * Additive: nothing replaced.
 */
@Patch(target = ClassMatch(type = TitleScreenState::class))
internal object TitleScreenPatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "createUI", parameterCount = 0, methodType = MethodType.METHOD))
    fun afterCreateUI(context: AfterContext) {
        val state = context.self ?: return
        StarUIManager.onTitleScreenCreated(state)
    }
}
