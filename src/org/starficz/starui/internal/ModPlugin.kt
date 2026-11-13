package org.starficz.starui.internal

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import org.starficz.starui.StarUIManager
import org.starficz.staruisamples.ShowcaseUIPlugin


internal class ModPlugin : BaseModPlugin() {
    override fun onGameLoad(newGame: Boolean) {
        if (!Global.getSector().hasTransientScript(CampaignUIAdderScript::class.java))
            Global.getSector().addTransientScript(CampaignUIAdderScript())
    }

    override fun onApplicationLoad() {
        StarUIManager.initAtApplicationLoad()
        // The showcase: a "StarUI showcase" button on the title screen (collapsed until clicked)
        StarUIManager.registerPlugin(ShowcaseUIPlugin.Plugin())
        // Older title-screen demo panel, for manual testing only:
        // StarUIManager.registerPlugin(org.starficz.staruisamples.ExampleUIPlugin.ExampleStarUIPlugin())
    }
}