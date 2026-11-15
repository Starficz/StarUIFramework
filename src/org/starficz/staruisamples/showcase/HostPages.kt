package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.VanillaColors
import org.starficz.starui.window.showToast

private const val FILE = "HostPages.kt"

// The code below is shown on the Hosts page and never runs here: it is what a mod writes to put StarUI on a screen.

// snippet: host-plugin
private class FleetScreens : StarUIPlugin {
    override val addPanelToFleetTab: StarUIBuilder = {
        Button("Sort by cost", modifier = Modifier.anchor(Anchor.inside.topRight.ofParent(20f, 60f))) { }
    }
}

private class MyModPlugin : BaseModPlugin() {
    override fun onApplicationLoad() {
        StarUIManager.registerPlugin(FleetScreens())
    }
}
// end snippet

// snippet: host-side-panel
private fun showSurvey(dialog: InteractionDialogAPI) {
    dialog.visualPanel.showStarUIPanel(400f, 300f) {
        Column(Size.fillMaxSize(), Modifier.padding(10f), spacing = 6f) {
            Text("Survey", color = VanillaColors.base)
            Button("Start") { dialog.dismiss() }
        }
    }
}
// end snippet

/*
 A PatchLib patch in a sample would be applied to the game, so this one is a comment. The same code, compiled and
 running, is Combat AI Tweaks' ui/RefitButton.kt.

// snippet: host-inject
// 0.98.5a-RC4: the refit's hullmod list; vanilla never clears its children
@Patch(target = ClassMatch(typeName = "com.fs.starfarer.coreui.refit.ModWidget"))
object RefitButtonPatch {
    @JvmStatic
    @After(target = MethodMatch(methodType = MethodType.CONSTRUCTOR))
    fun afterCreate(context: AfterContext) {
        val widget = context.self as? UIPanelAPI ?: return
        widget.inject("mymod:refit-button") { Button("AI Config") { } }
    }
}
// end snippet
*/

private val hosts = Page("Hosts", "Screens and hooks",
    "Where StarUI goes on screen. These run in a mod, not here, so the cards show only the code.", FILE) {

    card("A StarUIPlugin screen", "host-plugin",
        "A full-screen panel over a vanilla screen (title, campaign, each core tab, combat, the command map), added " +
            "whenever vanilla builds it. Register the plugin in onApplicationLoad.")

    card("inject from a PatchLib hook", "host-inject",
        "For a vanilla panel no screen covers: call inject when vanilla builds it. One injection per key per panel; it " +
            "comes back by itself when the panel clears its children.")

    card("An interaction dialog's side panel", "host-side-panel",
        "showStarUIPanel fills the dialog's visual panel, on black by default (vanilla draws nothing behind it).")

    card("Windows over any screen", "host-windows",
        "Layers, dialogs and toasts need no host: they go over whatever screen is showing (the Windows pages).")
}

// snippet: host-windows
private fun onFleetBought() {
    showToast("Fleet bought")
}
// end snippet

internal val hostPages = listOf(hosts)
