package org.starficz.staruiframework.internal

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import org.starficz.staruiframework.StarUIManager
import org.starficz.staruisamples.ExampleUIPlugin


internal class ModPlugin : BaseModPlugin() {
    override fun onGameLoad(newGame: Boolean) {
        Global.getSector().addTransientScript(CampaignUIAdderScript())
    }

    // for testing only
    override fun onApplicationLoad() {
        StarUIManager.registerPlugin(ExampleUIPlugin.ExampleStarUIPlugin())
    }
}