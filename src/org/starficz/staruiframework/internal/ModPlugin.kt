package org.starficz.staruiframework.internal

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global


internal class ModPlugin : BaseModPlugin() {
    override fun onGameLoad(newGame: Boolean) {
        if (!Global.getSector().hasTransientScript(CampaignUIAdderScript::class.java))
            Global.getSector().addTransientScript(CampaignUIAdderScript())
    }
//
//    // for testing only
//    override fun onApplicationLoad() {
//        StarUIManager.registerPlugin(ExampleUIPlugin.ExampleStarUIPlugin())
//    }
}