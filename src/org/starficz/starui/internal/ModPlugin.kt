package org.starficz.starui.internal

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import org.starficz.starui.compose.runtime.StarUIManager
import org.starficz.staruisamples.ExampleUIPlugin


internal class ModPlugin : BaseModPlugin() {
    override fun onGameLoad(newGame: Boolean) {
        if (!Global.getSector().hasTransientScript(CampaignUIAdderScript::class.java))
            Global.getSector().addTransientScript(CampaignUIAdderScript())
    }

    override fun onApplicationLoad() {
        StarUIManager.initAtApplicationLoad()
        // Title-screen demo panel, for manual testing only:
        // StarUIManager.registerPlugin(ExampleUIPlugin.ExampleStarUIPlugin())
    }
}