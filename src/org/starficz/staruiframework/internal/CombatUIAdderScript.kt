package org.starficz.staruiframework.internal

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.input.InputEventAPI
import org.starficz.staruiframework.StarUIManager


internal class CombatUIAdderScript : BaseEveryFrameCombatPlugin() {

    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        StarUIManager.advance()
    }
}