package org.starficz.starui.internal

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.input.InputEventAPI
import org.starficz.starui.StarUIManager


internal class CombatUIAdderScript : BaseEveryFrameCombatPlugin() {

    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        StarUIManager.advance()
    }
}