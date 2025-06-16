package org.starficz.staruisamples

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import org.lazywizard.lazylib.opengl.ColorUtils.glColor
import org.lwjgl.opengl.GL11
import org.starficz.staruiframework.*
import java.awt.Color

class ExampleUIPlugin : BaseModPlugin() {
    override fun onApplicationLoad() {
        // register your StarUIPlugin to the StarUIManager, all the UI injection is taken care of for you.
        StarUIManager.registerPlugin(ExampleStarUIPlugin())
    }

    // example StarUIPlugin with a simple opengl rectangle and text component
    class ExampleStarUIPlugin: StarUIPlugin {
        override val addPanelToTitleScreen: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit) = { plugin ->
            with(plugin){
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text("This area is the 'addPanelToTitleScreen' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }
        }

        override val addPanelToCampaignUI: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit) = { plugin ->
            with(plugin){
                renderBelow { alphaMult ->
                    glColor(Color.blue, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text("This area is the 'addPanelToCampaignUI' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }
        }

        override val addPanelToCharacterTab: (UIPanelAPI.(StarUIPanelPlugin) -> Unit) = { plugin ->
            with(plugin){
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
                /* ect
                render {
                    // render anything above the panel
                }
                onClick {
                    // run code when panel is clicked, see StarUIPanelPlugin for more event handling
                }
                advance {
                    // run code everyframe
                }
                */
            }
            // "this" is a CustomPanelAPI, so you can add any UI element as needed
            Text("This area is the 'addPanelToCharacterTab' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }

            // DSL functions are implicitly this.Button(), ect, adding a button to "this"
            Button(200f, 50f, "Right Middle Button", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor()){
                anchorInRightMiddleOfParent(5f)

                // add an onClick to the button
                onClick {
                    // "this" being the ButtonAPI
                    this.text = "Button Clicked!"
                }

                // making a tooltip we have to use normal Starsector TooltipMakerAPI methods.
                // you could also use DSL, within VerticalStackLayout as the main element of the Tooltip is wanted,
                // but in that case you need to set the heightSoFar to be the height of the VerticalStackLayout.
                Tooltip(300f, TooltipMakerAPI.TooltipLocation.LEFT) {
                    addPara("This is a tooltip for the Right Middle Button!", 0f)
                }
            }

            // a vertical stack layout where elements are anchored in the center, first element is at the top and grows downwards
            VerticalStackLayout(5f, 5f, 5f, Alignment.TMID) { stackPlugin ->
                // A stack layout itself is a CustomPanel, and thus it has its own StarUIPanelPlugin
                // render a blue background
                stackPlugin.renderBelow { alphaMult ->
                    glColor(Color.blue, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
                // consume events so things under it don't get clicked
                stackPlugin.consumeEvents = true

                // anchor to the Right Middle button, "anchorXPreviousMatchingX()" functions refer to the previously added component (in this case the button)
                anchorBelowPreviousMatchingRight()

                Text("First Element of VerticalStackLayout")
                // second element of VerticalStackLayout
                Image(100f, 100f, "graphics/ships/eagle/eagle_base.png")
                AreaCheckbox(300f, 50f, "Third Element of VerticalStackLayout", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor())
            }
        }

        override val addPanelToFleetTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit) = { plugin ->
            plugin.renderBelow { alphaMult ->
                glColor(Color.red, alphaMult*0.3f, false)
                GL11.glRectf(left, bottom, right, top)
            }
            Text("This area is the 'addPanelToFleetTab' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }
        }

        override val addPanelToRefitTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit) = { plugin ->
            plugin.renderBelow { alphaMult ->
                glColor(Color.red, alphaMult*0.3f, false)
                GL11.glRectf(left, bottom, right, top)
            }
            Text("This area is the 'addPanelToRefitTab' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }
        }

        override val addPanelToCargoTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit) = { plugin ->
            plugin.renderBelow { alphaMult ->
                glColor(Color.red, alphaMult*0.3f, false)
                GL11.glRectf(left, bottom, right, top)
            }
            Text("This area is the 'addPanelToCargoTab' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }
        }

        override val addPanelToMapTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit) = { plugin ->
            plugin.renderBelow { alphaMult ->
                glColor(Color.red, alphaMult*0.3f, false)
                GL11.glRectf(left, bottom, right, top)
            }
            Text("This area is the 'addPanelToMapTab' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }
        }

        override val addPanelToIntelTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit) = { plugin ->
            plugin.renderBelow { alphaMult ->
                glColor(Color.red, alphaMult*0.3f, false)
                GL11.glRectf(left, bottom, right, top)
            }
            Text("This area is the 'addPanelToIntelTab' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }
        }

        override val addPanelToOutpostsTab: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit) = { plugin ->
            plugin.renderBelow { alphaMult ->
                glColor(Color.red, alphaMult*0.3f, false)
                GL11.glRectf(left, bottom, right, top)
            }
            Text("This area is the 'addPanelToOutpostsTab' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }
        }

        override val addPanelAboveCombatShipInfo: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit) = { plugin ->
            plugin.renderBelow { alphaMult ->
                glColor(Color.red, alphaMult*0.3f, false)
                GL11.glRectf(left, bottom, right, top)
            }
            Text("This area is the 'addPanelAboveCombatShipInfo' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }
        }

        override val addPanelToCombatWarroom: (UIPanelAPI.(plugin: StarUIPanelPlugin) -> Unit) = { plugin ->
            plugin.renderBelow { alphaMult ->
                glColor(Color.blue, alphaMult*0.3f, false)
                GL11.glRectf(left, bottom, right, top)
            }
            Text("This area is the 'addPanelToCombatWarroom' injection point.", Font.INSIGNIA_25){
                anchorInTopLeftOfParent(5f,5f)
            }
        }
    }
}