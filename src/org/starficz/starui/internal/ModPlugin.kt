package org.starficz.starui.internal

import com.fs.starfarer.api.BaseModPlugin
import org.starficz.starui.StarUIManager
import org.starficz.staruisamples.ShowcaseUIPlugin


internal class ModPlugin : BaseModPlugin() {
    // the core tabs' panels come through the API's tab listener; the other screens through hooks (patches/)
    override fun onGameLoad(newGame: Boolean) = StarUIManager.initAtGameLoad()

    override fun onApplicationLoad() {
        StarUIManager.initAtApplicationLoad()
        // The showcase: a "StarUI showcase" button on the title screen (collapsed until clicked)
        //StarUIManager.registerPlugin(ShowcaseUIPlugin.Plugin())
        // Older title-screen demo panel, for manual testing only:
        // StarUIManager.registerPlugin(org.starficz.staruisamples.ExampleUIPlugin.ExampleStarUIPlugin())
    }
}