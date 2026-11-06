package org.starficz.starui.internal

import com.fs.starfarer.api.EveryFrameScript
import org.starficz.starui.compose.runtime.StarUIManager


internal class CampaignUIAdderScript : EveryFrameScript{

    override fun isDone(): Boolean { return false }

    override fun runWhilePaused(): Boolean { return true }

    override fun advance(amount: Float) {
        StarUIManager.advance()
    }
}