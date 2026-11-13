package org.starficz.staruisamples

import org.starficz.starui.core.scrollPanel
import org.starficz.starui.compose.Overflow
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.opengl.GL11
import org.starficz.starui.compose.*
import org.starficz.starui.compose.components.Button
import org.starficz.starui.compose.components.Checkbox
import org.starficz.starui.compose.components.Divider
import org.starficz.starui.compose.components.FlexSpacer
import org.starficz.starui.compose.components.Row
import org.starficz.starui.compose.components.Image
import org.starficz.starui.compose.components.ShipDisplay
import org.starficz.starui.compose.components.Spacer
import org.starficz.starui.compose.components.Text
import org.starficz.starui.compose.TextAlign
import org.starficz.starui.compose.components.Theme
import org.starficz.starui.compose.components.Column
import org.starficz.starui.compose.Alignment
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.modifier.anchor
import org.starficz.starui.compose.modifier.background
import org.starficz.starui.compose.modifier.border
import org.starficz.starui.compose.modifier.hitBoxPadding
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.compose.modifier.margin
import org.starficz.starui.compose.modifier.padding
import org.starficz.starui.compose.modifier.onHoverEnter
import org.starficz.starui.compose.modifier.onRender
import org.starficz.starui.compose.runtime.StarUIBuilder
import org.starficz.starui.compose.runtime.StarUIPlugin
import org.starficz.starui.compose.runtime.StarUITheme
import org.starficz.starui.compose.runtime.UIState
import org.starficz.starui.core.*
import org.starficz.starui.drawBorder
import java.awt.Color

object ExampleUIPlugin {

    // Helper: Recursively draws debug borders around all UI Components in the tree
    fun drawDebugBorders(panel: UIPanelAPI) {
        panel.getChildrenCopy().forEach { child ->
            when (child) {
                is CustomPanelAPI -> GL11.glColor4f(0.2f, 0.5f, 1f, 1f) // Blue for Layouts/Spacers
                is UIPanelAPI -> GL11.glColor4f(0f, 1f, 1f, 1f)         // Cyan for generic Panels
                is LabelAPI -> GL11.glColor4f(0.2f, 1f, 0.2f, 1f)       // Green for Text
                is ButtonAPI -> GL11.glColor4f(1f, 0.8f, 0f, 1f)        // Yellow/Orange for Buttons & Checkboxes
                is TextFieldAPI -> GL11.glColor4f(1f, 0f, 1f, 1f)       // Magenta for Text Inputs
                else -> GL11.glColor4f(1f, 0f, 0f, 1f)                  // Red for Images and unknown types
            }

            drawBorder(
                child.position.x,
                child.position.y,
                child.position.x + child.width,
                child.position.y + child.height
            )

            if (child is UIPanelAPI) {
                drawDebugBorders(child)
            }
        }
    }

    class ExampleStarUIPlugin : StarUIPlugin {
        override val addPanelToTitleScreen: StarUIBuilder = {

            val showDebug = UIState(false)

            // We will capture these references to test the ScrollPanelStateAPI later!
            var myScroller: ScrollPanelAPI? = null
            var targetListItem: UIComponentAPI? = null

            // --- THEME OVERRIDE ---
            // We create a custom theme to prove context-passing works!
            val myCyberTheme = StarUITheme.Default.copy(
                button = StarUITheme.Default.button.copy(
                    contentPadding = PaddingValues.symmetric(horizontal = 15f, vertical = 8f), // Fat buttons
                    font = ButtonFont.ORBITRON_12_CONDENSED
                ),
                scroll = StarUITheme.Default.scroll.copy(
                    shadowColor = Color(0, 50, 100, 200) // Cool blue gradient shadows!
                ),
                divider = StarUITheme.Default.divider.copy(
                    color = Color.CYAN,
                    thickness = 1f
                )
            )

            // ROOT CONTAINER
            Theme(myCyberTheme) {
                Column(
                    size = Size.exact(1200f, 800f),
                    modifier = Modifier
                        .anchor(Anchor.inside.center.ofParent())
                        .padding(20f)
                        .background(Color(10, 10, 15, 240))
                        .border(Color(80, 80, 100, 255), 2f)
                        .onRender { if (showDebug.value) drawDebugBorders(customPanel) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    spacing = 15f
                ) {

                    // ==========================================
                    // HEADER
                    // ==========================================
                    Row(size = Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("StarUI Unified Architecture Test", font = Font.ORBITRON_24_BOLD, color = Color.CYAN)
                        FlexSpacer(1f)
                        Checkbox("Draw Debug Outlines", bind = showDebug, color = Color.YELLOW)
                    }

                    Divider()

                    // ==========================================
                    // MAIN BODY (Two Columns)
                    // ==========================================
                    Row(size = Size.fillMaxSize(1f), spacing = 20f, verticalAlignment = Alignment.Top) {

                        // ---------------------------------------------------------
                        // COLUMN A: ADVANCED CONTROLLERS & SCROLL COMMANDS
                        // ---------------------------------------------------------
                        Column(
                            size = Size.fillMaxHeight(width = 350f),
                            spacing = 15f,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            // TEXT WRAPPER TEST
                            Text("1. Text Wrapper Features", font = Font.INSIGNIA_21, color = Color.WHITE)

                            // This text proves that the outer wrapper stretches, takes background colors,
                            // and the inner text centers perfectly within it!
                            Text(
                                "I am a Full-Width Centered Banner!",
                                size = Size.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = Color.WHITE,
                                modifier = Modifier
                                    .background(Color(100, 0, 0, 200))
                                    .border(Color.RED)
                                    .margin(bottom = 10f)
                            )

                            // IMAGE WRAPPER TEST
                            Text("2. Image API Features", font = Font.INSIGNIA_21, color = Color.WHITE)
                            Row(Size.fillMaxWidth(), spacing = 10f, verticalAlignment = Alignment.Bottom) {
                                // Outline Test
                                Column(Size.fillMaxWidth(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image("graphics/icons/cargo/supplies.png", withOutline = true)
                                    Text("Outline", font = Font.SMALL_FONTS_8, color = Color.GRAY)
                                }
                                // Schematic Test
                                Column(Size.fillMaxWidth(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image("graphics/icons/cargo/supplies.png", renderSchematic = true)
                                    Text("Schematic", font = Font.SMALL_FONTS_8, color = Color.GRAY)
                                }
                                // Border Test
                                Column(Size.fillMaxWidth(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image("graphics/icons/cargo/supplies.png", showBorder = true, borderColor = Color.GREEN)
                                    Text("Border", font = Font.SMALL_FONTS_8, color = Color.GRAY)
                                }
                            }

                            Spacer(Size.exact(1f, 10f))

                            // SHIP DISPLAY TEST
                            Text("3. Ship Display API", font = Font.INSIGNIA_21, color = Color.WHITE)
                            ShipDisplay(
                                size = Size.fillMaxSize(1f), // Fills the remaining vertical space of Column A
                                variant = Global.getSettings().getVariant("hammerhead_Balanced"),
                                showFighters = true,
                                scaleSmallerShips = true,
                                showBorder = true,
                                modifier = Modifier.background(Color(0, 0, 20, 100))
                            )

                            Spacer(Size.exact(1f, 10f))

                            // SCROLL API COMMANDS
                            Text("4. Scroll API Interaction", font = Font.INSIGNIA_21, color = Color.WHITE)
                            Row(Size.fillMaxWidth(), spacing = 5f, verticalAlignment = Alignment.CenterVertically) {
                                Button("Jump to Item #75", color = Color.GREEN) {
                                    // Notice how we don't need to cast or unwrap manually!
                                    // ensureVisible automatically passes the Kotlin Proxy into the vanilla method!
                                    if (myScroller != null && targetListItem != null) {
                                        myScroller!!.ensureVisible(targetListItem!!)
                                    }
                                }
                                Button("Scroll to Top", color = Color.YELLOW) {
                                    myScroller?.scrollToY(0f)
                                }
                            }
                            Row(Size.fillMaxWidth(), spacing = 5f, verticalAlignment = Alignment.CenterVertically) {
                                Button("Save State") { myScroller?.saveVerticalScrollState() }
                                Button("Restore State") { myScroller?.restoreVerticalScrollState() }
                            }
                        }


                        // ---------------------------------------------------------
                        // COLUMN B: THE SCROLL PANEL (LazyColumn)
                        // ---------------------------------------------------------

                        // We capture the returned StarUIScrollPanel controller directly!
                        myScroller = Column(
                            size = Size.fillMaxSize(1f), // Fills the rest of the HorizontalStack width
                            spacing = 10f,
                            overflow = Overflow.ScrollVertical,
                            // Notice we don't pass shadowColor here, it inherently gets the Blue color from our Theme!
                            modifier = Modifier.padding(10f).background(Color(5, 5, 5, 200)).border(Color.DARK_GRAY)
                        ) {

                            Text("Massive Scrollable List", font = Font.ORBITRON_20_BOLD, color = Color.WHITE)
                            Text("Try clicking 'Save State', scrolling, and clicking 'Restore'.", font = Font.INSIGNIA_15, color = Color.GRAY)
                            Spacer(Size.exact(1f, 10f))

                            for (i in 1..100) {
                                // Create the row
                                val row = Row(
                                    size = Size.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    spacing = 15f,
                                    modifier = Modifier
                                        .background(if (i == 75) Color(0, 50, 0, 200) else Color(20, 20, 20, 255))
                                        .border(if (i == 75) Color.GREEN else Color.DARK_GRAY)
                                        .margin(bottom = 5f)
                                        .hitBoxPadding(5f)
                                        .onHoverEnter { Global.getSoundPlayer().playUISound("ui_button_mouseover", 1f, 1f) }
                                ) {
                                    Text("#$i", font = Font.ORBITRON_12_CONDENSED, color = if (i == 75) Color.GREEN else Color.CYAN)
                                    Image("graphics/ships/hound/hound_base.png", size = Size.exact(40f, 40f))

                                    Column(Size.fillMaxSize(1f), horizontalAlignment = Alignment.Left) {
                                        Text(if (i == 75) "TARGET ACQUIRED" else "Standard Hound Variant", font = Font.INSIGNIA_15, color = Color.WHITE)
                                        Text("A cheap, readily available frigate.", font = Font.SMALL_FONTS_8, color = Color.GRAY)
                                    }

                                    Button("Examine") // Uses Fat Button Theme!
                                }

                                // If this is item 75, we capture its layout controller to use in the ensureVisible test!
                                if (i == 75) {
                                    targetListItem = row
                                }
                            }
                        }.scrollPanel // End the scrolling Column

                    } // End Columns
                }
            } // End Theme
        }
    }
}
