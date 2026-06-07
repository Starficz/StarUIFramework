package org.starficz.staruisamples

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.opengl.GL11
import org.starficz.starui.compose.*
import org.starficz.starui.core.*
import org.starficz.starui.drawBorder
import java.awt.Color



object ExampleUIPlugin {

    // Helper: Recursively draws debug borders around all UI Components in the tree
    fun drawDebugBorders(panel: UIPanelAPI) {
        panel.getChildrenCopy().forEach { child ->

            // Determine border color based on the vanilla UI component type
            when (child) {
                is com.fs.starfarer.api.ui.CustomPanelAPI -> GL11.glColor4f(0.2f, 0.5f, 1f, 1f) // Blue for Layouts/Spacers
                is com.fs.starfarer.api.ui.UIPanelAPI -> GL11.glColor4f(0f, 1f, 1f, 1f)         // Cyan for generic Panels
                is com.fs.starfarer.api.ui.LabelAPI -> GL11.glColor4f(0.2f, 1f, 0.2f, 1f)       // Green for Text
                is com.fs.starfarer.api.ui.ButtonAPI -> GL11.glColor4f(1f, 0.8f, 0f, 1f)        // Yellow/Orange for Buttons & Checkboxes
                is com.fs.starfarer.api.ui.TextFieldAPI -> GL11.glColor4f(1f, 0f, 1f, 1f)       // Magenta for Text Inputs
                else -> GL11.glColor4f(1f, 0f, 0f, 1f)                                          // Red for Images and unknown types
            }

            drawBorder(child.position.x, child.position.y, child.position.x + child.width, child.position.y + child.height)

            if (child is UIPanelAPI) {
                drawDebugBorders(child)
            }
        }
    }

    class ExampleStarUIPlugin : StarUIPlugin {
        override val addPanelToTitleScreen: StarUIBuilder = {

            // Global Debug Renderer
            Plugin { render { drawDebugBorders(customPanel) } }

            // ROOT WINDOW
            VerticalStackLayout(
                size = StackSize.exact(1000f, 700f),
                modifier = Modifier.anchor(Anchor.inside.center.ofParent()),
                align = XAlign.Center,
                xPadding = 15f,
                yPadding = 15f,
                spacing = 10f
            ) {
                Plugin {
                    renderBelow { alphaMult ->
                        GL11.glColor4f(0.05f, 0.05f, 0.05f, 0.95f * alphaMult)
                        GL11.glRectf(left, bottom, right, top)
                    }
                }

                // --- HEADER ---
                Text("StarUI Framework Exhaustive Test Matrix", font = Font.ORBITRON_24_BOLD, color = Color.CYAN)
                Divider(thickness = 2f, color = Color.CYAN)

                // --- BODY MULTI-COLUMN ---
                HorizontalStackLayout(size = StackSize.flex(1f), align = YAlign.Top, spacing = 20f) {

                    // ==========================================
                    // COLUMN 1: LAYOUT & FLEX MATRIX
                    // ==========================================
                    VerticalStackLayout(size = StackSize.flex(1f), align = XAlign.Left, spacing = 15f) {
                        Text("1. Layout Distributions", font = Font.INSIGNIA_21, color = Color.YELLOW)
                        Divider()

                        // Test: Proportional Flex Math
                        Text("Horizontal Flex (1:2:1 Ratio)", color = Color.GRAY)
                        HorizontalStackLayout(size = StackSize.fillCross(40f), spacing = 5f) {
                            Button("W: 1x", size = ContentSize.flex(1f))
                            Button("W: 2x", size = ContentSize.flex(2f))
                            Button("W: 1x", size = ContentSize.flex(1f))
                        }

                        // Test: Cross-Axis Alignment inside a Wrap Layout
                        Text("YAlign Test (Top/Center/Bottom)", color = Color.GRAY)
                        HorizontalStackLayout(size = StackSize.wrap(), spacing = 5f, align = YAlign.Center) {
                            Button("Top", size = ContentSize.exact(60f, 30f), modifier = Modifier.margin(bottom = 30f))
                            Button("Center", size = ContentSize.exact(60f, 60f))
                            Button("Bottom", size = ContentSize.exact(60f, 30f), modifier = Modifier.margin(top = 30f))
                        }

                        // Test: XAlign Test
                        Text("XAlign Test (Left/Center/Right)", color = Color.GRAY)
                        VerticalStackLayout(size = StackSize.flex(1f), spacing = 5f, align = XAlign.Center) {
                            Button("Left", modifier = Modifier.margin(right = 100f))
                            Button("Center")
                            Button("Right", modifier = Modifier.margin(left = 100f))
                        }
                    }

                    Divider(color = Color.DARK_GRAY)

                    // ==========================================
                    // COLUMN 2: SIZING & ELEMENTS
                    // ==========================================
                    VerticalStackLayout(size = StackSize.flex(1.2f), align = XAlign.Left, spacing = 15f) {
                        Text("2. Sizing & Data Bindings", font = Font.INSIGNIA_21, color = Color.YELLOW)
                        Divider()

                        // Test: TextField & FillCross Directive
                        Text("FillCross TextField (H:30, W:Fill)", color = Color.GRAY)
                        TextField(bind = UIState("Editable Text Box"), size = ContentSize.fillCross(30f))

                        // Test: Exact vs Wrap
                        Text("Exact vs Wrap Size", color = Color.GRAY)
                        HorizontalStackLayout(size = StackSize.wrap(), spacing = 10f) {
                            Button("Exact 100x40", size = ContentSize.exact(100f, 40f))
                            Button("Wrapped Content", size = ContentSize.wrap(xPad = 15f, yPad = 10f))
                        }

                        // Test: State Bindings & Groups
                        Text("State Bindings & Radio Groups", color = Color.GRAY)
                        val rGroup = ButtonGroup()
                        HorizontalStackLayout(size = StackSize.wrap(), spacing = 10f) {
                            Checkbox("Radio A", bind = UIState(true), buttonGroup = rGroup)
                            Checkbox("Radio B", bind = UIState(false), buttonGroup = rGroup)
                        }

                        AreaCheckbox("Standalone Area Checkbox", bind = UIState(false), size = ContentSize.fillCross(30f))

                        // Test: Image Aspect Ratio Dynamic Flex Scaling
                        Text("Dynamic Flex Image (Pass-3 Scaling)", color = Color.GRAY)
                        CustomPanel(size = PanelSize.flex(1f)) {
                            val img = Image("graphics/ships/eagle/eagle_base.png", size = ImageSize.exact(1f, 1f))
                            Plugin {
                                onLayout {
                                    img.sizeToOriginalAspectRatioWithWidth(width)
                                    img.setAbsoluteLocation(centerX - (img.width / 2f), centerY - (img.height / 2f))
                                }
                                renderBelow { alphaMult ->
                                    GL11.glColor4f(0.1f, 0.1f, 0.1f, 0.8f * alphaMult)
                                    GL11.glRectf(left, bottom, right, top)
                                }
                            }
                        }
                    }

                    Divider(color = Color.DARK_GRAY)

                    // ==========================================
                    // COLUMN 3: MODIFIERS & ANCHORING
                    // ==========================================
                    VerticalStackLayout(size = StackSize.flex(1f), align = XAlign.Left, spacing = 15f) {
                        Text("3. Modifiers & CustomPanel", font = Font.INSIGNIA_21, color = Color.YELLOW)
                        Divider()

                        // Test: Tooltips & Hitbox Padding
                        Button(
                            text = "Hover For Large Tooltip",
                            size = ContentSize.fillCross(40f),
                            modifier = Modifier
                                .tooltip("This tooltip is chained with modifiers!")
                                .hitBoxPadding(10f)
                        )

                        Text("Margin Test", color = Color.GRAY, modifier = Modifier.margin(top = 10f, bottom = 10f, left = 20f))

                        // Test: Complex CustomPanel Anchoring
                        Text("Absolute Anchor Directives", color = Color.GRAY)
                        CustomPanel(size = PanelSize.flex(1f)) {
                            Plugin {
                                renderBelow { alphaMult ->
                                    GL11.glColor4f(0.1f, 0.3f, 0.1f, 0.3f * alphaMult)
                                    GL11.glRectf(left, bottom, right, top)
                                }
                            }

                            // Using exhaustive anchor corners and margins
                            Text("TL", modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(5f, 5f)))
                            Text("TR", modifier = Modifier.anchor(Anchor.inside.topRight.ofParent(5f, 5f)))

                            Button("Center", size = ContentSize.exact(80f, 30f), modifier = Modifier.anchor(Anchor.inside.center.ofParent()))

                            Text("BL", modifier = Modifier.anchor(Anchor.inside.bottomLeft.ofParent(5f, 5f)))
                            Text("BR", modifier = Modifier.anchor(Anchor.inside.bottomRight.ofParent(5f, 5f)))
                        }
                    }
                }
            }
        }
    }
}