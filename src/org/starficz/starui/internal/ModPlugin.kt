package org.starficz.starui.internal

import com.fs.starfarer.api.BaseModPlugin
import org.starficz.starui.StarUIManager


internal class ModPlugin : BaseModPlugin() {
    // the core tabs' panels come through the API's tab listener; the other screens through hooks (patches/)
    override fun onGameLoad(newGame: Boolean) = StarUIManager.initAtGameLoad()

    override fun onApplicationLoad() {
        StarUIManager.initAtApplicationLoad()
        // The showcase: Ctrl + Shift + U opens it on the title screen, in the campaign and in combat (a chord, so a
        // player never opens it by accident)
        StarUIManager.registerPlugin(org.starficz.staruisamples.showcase.Showcase.Plugin())
        // Older title-screen demo panel, for manual testing only:
        // StarUIManager.registerPlugin(org.starficz.staruisamples.ExampleUIPlugin.ExampleStarUIPlugin())
    }
}