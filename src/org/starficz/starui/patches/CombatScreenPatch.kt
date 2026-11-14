package org.starficz.starui.patches

import com.fs.starfarer.combat.CombatState
import org.starficz.starui.StarUIManager
import patchlib.api.context.AfterContext
import patchlib.api.match.ClassMatch
import patchlib.api.match.MethodMatch
import patchlib.api.match.MethodType
import patchlib.api.patch.After
import patchlib.api.patch.Patch

/**
 * A battle's UI is set up near the start of `CombatState.traverse()` (the combat state's whole main loop: it only
 * returns when the battle ends), in `resetSomeUIThings()`, which makes the widget panel and the ship info and sizes
 * the ship info. The combat screen panels are injected right after it. Combat plugins' `init` is too early: it runs
 * inside `CombatEngine.init()`, before those panels exist.
 *
 * The warroom (command map) is made later in `traverse`, and shown with `showWarroom()` each time the map opens
 * (Tab, the deployment screen, tutorials); `addPanelToCombatWarroom` is injected then (the same panel every later
 * time). The warroom hands input to its children before its map display, so whatever is injected sees clicks first.
 *
 * `CombatState`, `resetSomeUIThings` and `showWarroom` are readable engine names, not API (inferred): re-check them
 * after a game update. Additive: nothing replaced. Runs per battle (simulator and missions included).
 */
@Patch(target = ClassMatch(type = CombatState::class))
internal object CombatScreenPatch {
    @JvmStatic
    @After(target = MethodMatch(methodName = "resetSomeUIThings", parameterCount = 0, methodType = MethodType.METHOD))
    fun afterUISetUp(context: AfterContext) {
        val state = context.self ?: return
        StarUIManager.onCombatUICreated(state)
    }

    @JvmStatic
    @After(target = MethodMatch(methodName = "showWarroom", parameterCount = 0, methodType = MethodType.METHOD))
    fun afterShowWarroom(context: AfterContext) {
        val state = context.self ?: return
        StarUIManager.onWarroomShown(state)
    }
}
