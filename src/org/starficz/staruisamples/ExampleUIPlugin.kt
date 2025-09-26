package org.starficz.staruisamples

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
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
        override val addPanelToTitleScreen: (CustomPanelAPI.() -> Unit) = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToTitleScreen' injection point.",
                font = Font.INSIGNIA_25
            )

            VerticalStackLayout(
                anchor = Anchor.inside.centerLeft.ofParent(),
                xMargin = 5f, yMargin = 5f, spacing = 5f,
                alignment = Alignment.TMID
            ) {
                // A stack layout itself is a CustomPanel, and thus you can put a StarUIPanelPlugin in it as well
                // render a yellow background
                Plugin {
                    renderBelow { alphaMult ->
                        glColor(Color.yellow, alphaMult*0.3f, false)
                        GL11.glRectf(left, bottom, right, top)
                    }
                    // consume events so things under it don't get clicked
                    consumeEvents = true
                }

                Text("This is a stack layout panel.", Font.INSIGNIA_25)
                Image(100f, 100f, "graphics/ships/eagle/eagle_base.png")
                Button(
                    width = 200f, height = 50f,
                    text = "Im A Button!"
                ) {
                    // add an onClick to the button
                    onClick {
                        // "this" being the ButtonAPI
                        this.text = "Button Clicked!"
                    }

                    // making a tooltip we have to use normal Starsector TooltipMakerAPI methods.
                    // you could also use DSL, within VerticalStackLayout as the main element of the Tooltip is wanted,
                    // but in that case you need to set the heightSoFar to be the height of the VerticalStackLayout.
                    Tooltip(300f, TooltipMakerAPI.TooltipLocation.RIGHT) {
                        addPara("This is a tooltip for the Button!", 0f)
                    }
                }
            }

        }

        override val addPanelToCampaignUI: (CustomPanelAPI.() -> Unit) = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.blue, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToCampaignUI' injection point.",
                font = Font.INSIGNIA_25
            )
        }

        override val addPanelToCharacterTab: (CustomPanelAPI.() -> Unit) = {
            Plugin {
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
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToCharacterTab' injection point.",
                font = Font.INSIGNIA_25
            )

            ScrollPanel(
                anchor = Anchor.below.previousComponent.matchingLeftEdge(5f),
                width = 300f, height = 150f
            ) {
                VerticalStackLayout(
                    anchor = Anchor.inside.topLeft.ofParent(),
                    xMargin = 5f, yMargin = 5f, spacing = 5f,
                    alignment = Alignment.TMID
                ) {
                    // A stack layout itself is a CustomPanel, and thus you can put a StarUIPanelPlugin in it as well
                    // render a yellow background
                    Plugin {
                        renderBelow { alphaMult ->
                            glColor(Color.yellow, alphaMult*0.3f, false)
                            GL11.glRectf(left, bottom, right, top)
                        }
                        // consume events so things under it don't get clicked
                        consumeEvents = true
                    }

                    Text("This is a scroll panel.", Font.INSIGNIA_25)
                    Image(100f, 100f, "graphics/ships/eagle/eagle_base.png")
                    Image(100f, 100f, "graphics/ships/eagle/eagle_base.png")
                }

                setContentSize(width, contentContainer.children.last().height)
            }

            // StarUIFramework exposes vanilla Ship Displays! Use "this." to let intellj help you see what's exposed.
            // These are used everywhere in vanilla whenever a ship is shown while not in the direct combat layer.
            // ie: refit, codex, production, tips, even in combat warroom!
            ShipDisplay(
                anchor = Anchor.below.previousComponent.matchingLeftEdge(),
                width = 200f, height = 200f,
                fleetMember = Global.getSector().playerFleet.flagship
            ) {
                // this. <- type this for intellisense!
            }

            Button(
                anchor = Anchor.inside.centerRight.ofParent(5f),
                width = 200f, height = 50f,
                text = "Right Middle Button"
            ) {
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
            VerticalStackLayout(
                anchor = Anchor.below.previousComponent.matchingRightEdge(),
                xMargin = 5f, yMargin = 5f, spacing = 5f,
                alignment = Alignment.TMID
            ) {
                // anchor to the Right Middle button, "anchorXPreviousMatchingX()" functions refer to the previously added component (in this case the button)

                // A stack layout itself is a CustomPanel, and thus it has its own StarUIPanelPlugin
                // render a blue background
                Plugin {
                    renderBelow { alphaMult ->
                        glColor(Color.blue, alphaMult*0.3f, false)
                        GL11.glRectf(left, bottom, right, top)
                    }
                    // consume events so things under it don't get clicked
                    consumeEvents = true
                }


                Text("First Element of VerticalStackLayout")
                // second element of VerticalStackLayout
                // This is a BoxedUIImage! as opposed to vanilla UI Images many more fields are exposed like border, sprite, ect
                // as mentioned above, use "this." to let intellj help you see what's exposed.
                Image(100f, 100f, "graphics/ships/eagle/eagle_base.png") {
                    // this. <- type this for intellisense!
                }
                AreaCheckbox(300f, 50f, "Third Element of VerticalStackLayout")

                HorizontalStackLayout(5f, 5f, 5f, Alignment.LMID) {
                    // A stack layout itself is a CustomPanel, and thus it has its own StarUIPanelPlugin
                    // render a magenta background
                    Plugin {
                        renderBelow { alphaMult ->
                            glColor(Color.magenta, alphaMult*0.3f, false)
                            GL11.glRectf(left, bottom, right, top)
                        }
                        // consume events so things under it don't get clicked
                        consumeEvents = true
                    }

                    applyAnchor(Anchor.below.previousComponent.matchingRightEdge())

                    Text("First Element of HorizontalStackLayout")
                    Image(100f, 100f, "graphics/ships/eagle/eagle_base.png")
                    Image(100f, 100f, "graphics/ships/eagle/eagle_base.png")
                    Checkbox(30f, 30f) {
                        Tooltip(200f, TooltipMakerAPI.TooltipLocation.ABOVE){
                            addPara("This is a Checkbox!", 0f)
                        }
                    }
                }

                LabelledValue(100f, "test", "hello")
                TextField(100f, 30f, Font.INSIGNIA_16)
            }
        }

        override val addPanelToFleetTab: (CustomPanelAPI.() -> Unit) = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToFleetTab' injection point.",
                font = Font.INSIGNIA_25
            )
        }

        override val addPanelToRefitTab: (CustomPanelAPI.() -> Unit) = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToRefitTab' injection point.",
                font = Font.INSIGNIA_25
            )
        }

        override val addPanelToCargoTab: (CustomPanelAPI.() -> Unit) = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToCargoTab' injection point.",
                font = Font.INSIGNIA_25
            )
        }

        override val addPanelToMapTab: (CustomPanelAPI.() -> Unit) = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToMapTab' injection point.",
                font = Font.INSIGNIA_25
            )
        }

        override val addPanelToIntelTab: (CustomPanelAPI.() -> Unit) = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToIntelTab' injection point.",
                font = Font.INSIGNIA_25
            )
        }

        override val addPanelToOutpostsTab: (CustomPanelAPI.() -> Unit) = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToOutpostsTab' injection point.",
                font = Font.INSIGNIA_25
            )
        }

        override val addPanelAboveCombatShipInfo: (CustomPanelAPI.() -> Unit)  = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelAboveCombatShipInfo' injection point.",
                font = Font.INSIGNIA_25
            )
        }

        override val addPanelToCombatScreen: (CustomPanelAPI.() -> Unit)  = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.pink, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToCombatScreen' injection point.",
                font = Font.INSIGNIA_25
            )
        }

        override val addPanelToCombatWarroom: (CustomPanelAPI.() -> Unit) = {
            Plugin {
                renderBelow { alphaMult ->
                    glColor(Color.red, alphaMult*0.3f, false)
                    GL11.glRectf(left, bottom, right, top)
                }
            }
            Text(
                anchor = Anchor.inside.topLeft.ofParent(5f, 5f),
                text = "This area is the 'addPanelToCombatWarroom' injection point.",
                font = Font.INSIGNIA_25
            )
        }
    }
}