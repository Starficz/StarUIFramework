package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.ui.Alignment as VanillaAlignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.*

private const val FILE = "ContentPages.kt"

// snippet: image-load
/** The sprite's path, its texture loaded first: a sprite the game has not loaded yet draws as nothing. */
private fun loaded(path: String): String {
    if (Global.getSettings().getSprite(path).textureId == 0) Global.getSettings().loadTexture(path)
    return path
}
// end snippet

private val text = Page("Content", "Text",
    "Vanilla's labels: any of the game's fonts, a colour, highlighted words, wrapping at a width, cut to one line.", FILE) {

    card("Fonts", "text-fonts", "Every font the game ships (Font). Insignia 15 is the default.") {
        // snippet: text-fonts
        for (font in Font.entries) Text(font.name, font = font)
        // end snippet
    }

    card("Colours and highlights", "text-colours",
        "highlightedText colours words of the text, in order, each found after the last.") {
        // snippet: text-colours
        Text("Gray, for less important text", color = VanillaColors.gray)
        Text("Costs 1200 credits and 300 supplies",
            highlightedText = listOf("1200" to VanillaColors.highlight, "300" to VanillaColors.negative))
        // end snippet
    }

    card("Vanilla's colours", "text-vanilla-colours",
        "VanillaColors: the player faction's UI colours and vanilla's text colours, read live.") {
        // snippet: text-vanilla-colours
        val colours = listOf("base" to VanillaColors.base, "dark" to VanillaColors.dark, "bright" to VanillaColors.bright,
            "text" to VanillaColors.text, "gray" to VanillaColors.gray, "highlight" to VanillaColors.highlight,
            "positive" to VanillaColors.positive, "negative" to VanillaColors.negative, "story" to VanillaColors.story,
            "storyDark" to VanillaColors.storyDark, "storyBright" to VanillaColors.storyBright, "panel" to VanillaColors.panel)
        for (row in colours.chunked(3)) Row(Size.fillMaxWidth(), spacing = 6f) {
            for ((name, colour) in row) Row(Size.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
                Spacer(Size.exact(14f, 14f), Modifier.background(colour).border(VanillaColors.gray.withAlpha(80)))
                Text(name, color = VanillaColors.gray)
            }
        }
        // end snippet
    }

    card("Wrapping", "text-wrap",
        "A Text with a fill or exact width wraps onto as many lines as it needs; its height follows. Wrap width: one line.") {
        // snippet: text-wrap
        Text("This one fills the width of its column, so it wraps where the column ends and again when it changes.",
            Size.fillMaxWidth())
        Text("This one is exactly 180 wide, whatever its column does.",
            Size.mixed(Axis.Exact(180f), Axis.WrapContent), Modifier.background(INSET))
        // end snippet
    }

    card("Alignment in its box", "text-align", "textAlign places the text inside the Text's own width.") {
        // snippet: text-align
        for (align in TextAlign.entries) {
            Text(align.name, Size.fillMaxWidth(), Modifier.background(INSET), textAlign = align)
        }
        // end snippet
    }

    card("Cut to one line", "text-ellipsis",
        "TextOverflow.Ellipsis stays on one line and ends in \"...\"; the full text shows in a tooltip.") {
        // snippet: text-ellipsis
        Text("ISS Unreasonably Long Name of a Ship (Onslaught class)", Size.mixed(Axis.Exact(200f), Axis.WrapContent),
            Modifier.background(INSET), overflow = TextOverflow.Ellipsis)
        // end snippet
    }

    card("Measuring", "text-measure", "measureTextSize: a string's width and height in a font, before it is placed.") {
        // snippet: text-measure
        val (width, height) = measureTextSize("Starsector", Font.ORBITRON_20_AA)
        Text("Starsector", font = Font.ORBITRON_20_AA)
        Text("${width.toInt()} x ${height.toInt()} px", color = VanillaColors.gray)
        // end snippet
    }
}

private val images = Page("Content", "Images",
    "Sprites at their own size, at a size you give, scaled and placed in it as you choose.", FILE) {

    card("A sprite must be loaded", "image-load",
        "Sprites the game loads at start draw at once; others need loadTexture first, or they draw as nothing.")

    card("Native size", "image-native", "ImageSize.native: the sprite's own pixels, scaled, or capped at a size.") {
        // snippet: image-native
        val icon = loaded("graphics/icons/cargo/supplies.png")
        Row(spacing = 10f, verticalAlignment = Alignment.CenterVertically) {
            Image(icon, ImageSize.native())
            Image(icon, ImageSize.native(scale = 0.5f))
            Image(icon, ImageSize.native(maxWidth = 24f, maxHeight = 24f))
        }
        // end snippet
    }

    card("Scaling into a box", "image-scale", "An exact size, and ContentScale to fit the sprite into it.") {
        // snippet: image-scale
        val tall = loaded("graphics/ships/wolf/wolf_base.png")
        Row(spacing = 8f) {
            for (scale in ContentScale.entries) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, spacing = 2f) {
                    Box(Size.exact(70f, 50f), Modifier.background(INSET), overflow = Overflow.Clip) {
                        Image(tall, Size.exact(70f, 50f), contentScale = scale)
                    }
                    Text(scale.name, color = VanillaColors.gray)
                }
            }
        }
        // end snippet
    }

    card("Keeping the aspect", "image-aspect", "ImageSize.aspectWrap: as large as its room allows, keeping the sprite's shape (here the row's 60 px).") {
        // snippet: image-aspect
        val tall = loaded("graphics/ships/wolf/wolf_base.png")
        Row(Size.fillMaxWidth(height = 60f), Modifier.background(INSET), spacing = 8f) {
            Image(tall, ImageSize.aspectWrap())
            Image(loaded("graphics/icons/cargo/supplies.png"), ImageSize.aspectWrap())
        }
        // end snippet
    }

    card("Options", "image-options") {
        // snippet: image-options
        val icon = loaded("graphics/icons/cargo/fuel.png")
        Row(spacing = 10f) {
            Image(icon, Size.exact(40f, 40f), Modifier.tooltip("withOutline"), withOutline = true)
            Image(icon, Size.exact(40f, 40f), Modifier.tooltip("renderSchematic"), renderSchematic = true)
            Image(icon, Size.exact(40f, 40f), Modifier.tooltip("showBorder"), showBorder = true, borderColor = VanillaColors.base)
        }
        // end snippet
    }
}

private val ships = Page("Content", "Ship displays",
    "Vanilla's ship display: a hull or a fleet member, with its weapons, fighters and labels.", FILE) {

    card("A variant", "ship-variant", "Vanilla fits the ship to the size, then shrinks it to 95%.") {
        // snippet: ship-variant
        Row(spacing = 6f) {
            ShipDisplay(Size.exact(110f, 110f), variant = Global.getSettings().getVariant("eagle_Assault"))
            ShipDisplay(Size.exact(110f, 110f), variant = Global.getSettings().getVariant("hammerhead_Balanced"))
        }
        // end snippet
    }

    card("A fleet member, with its fighters", "ship-member") {
        // snippet: ship-member
        val heron = Global.getSettings().createFleetMember(FleetMemberType.SHIP, "heron_Strike")
        ShipDisplay(Size.exact(140f, 140f), fleetMember = heron, showFighters = true)
        // end snippet
    }

    card("Labels", "ship-labels", "A name needs a variant and room: labels are the display's width less 10.") {
        // snippet: ship-labels
        val lasher = Global.getSettings().getVariant("lasher_Assault")
        Row(spacing = 6f) {
            ShipDisplay(Size.exact(200f, 150f), variant = lasher, shipName = "ISS Example")
            ShipDisplay(Size.exact(200f, 150f), variant = lasher, shipName = "ISS Example",
                style = BoxedUIShipPreview.Style.MINI, labelColor = VanillaColors.highlight)
        }
        // end snippet
    }

    card("Options", "ship-options") {
        // snippet: ship-options
        val lasher = Global.getSettings().getVariant("lasher_Assault")
        Row(spacing = 6f) {
            ShipDisplay(Size.exact(90f, 90f), Modifier.tooltip("showBorder"), variant = lasher, showBorder = true,
                borderColor = VanillaColors.bright)
            ShipDisplay(Size.exact(90f, 90f), Modifier.tooltip("scanlines = 0.5"), variant = lasher, scanlines = 0.5f)
            ShipDisplay(Size.exact(90f, 90f), Modifier.tooltip("scaleSmallerShips: a frigate at half"), variant = lasher,
                scaleSmallerShips = true)
        }
        // end snippet
    }

    card("At its real size", "ship-exact",
        "exactScale = 1f: one sprite pixel per UI pixel, centred, on whole pixels. Left: vanilla's fit.") {
        // snippet: ship-exact
        val wolf = Global.getSettings().getVariant("wolf_Assault")
        val sprite = Global.getSettings().getSprite(wolf.hullSpec.spriteName)
        val box = Size.exact(sprite.width + 20f, sprite.height + 20f)
        Row(spacing = 6f) {
            ShipDisplay(box, Modifier.background(INSET), variant = wolf)
            ShipDisplay(box, Modifier.background(INSET), variant = wolf, exactScale = 1f)
        }
        // end snippet
    }
}

private val tooltips = Page("Content", "Tooltips",
    "Modifier.tooltip on any component: a line of text, or anything vanilla's TooltipMakerAPI can build.", FILE) {

    card("A line of text", "tooltip-text", "Hover the button.") {
        // snippet: tooltip-text
        Button("Hover me", modifier = Modifier.tooltip("Opens the refit screen for the selected ship.")) { }
        // end snippet
    }

    card("Width and place", "tooltip-place") {
        // snippet: tooltip-place
        Row(spacing = 6f) {
            Button("Below", modifier = Modifier.tooltip("Under the button", location = TooltipMakerAPI.TooltipLocation.BELOW)) { }
            Button("Right", modifier = Modifier.tooltip("To the right", location = TooltipMakerAPI.TooltipLocation.RIGHT)) { }
            Button("Narrow", modifier = Modifier.tooltip("A narrow tooltip wraps its text sooner than the default 300.",
                width = 150f)) { }
        }
        // end snippet
    }

    card("Built with TooltipMakerAPI", "tooltip-builder",
        "The builder form: headings, highlighted paragraphs, anything vanilla's tooltips have.") {
        // snippet: tooltip-builder
        Box(Size.exact(200f, 36f), Modifier.background(INSET).tooltip(width = 320f) {
            addSectionHeading("Hardened Shields", VanillaAlignment.MID, 0f)
            addPara("Shield damage taken reduced by %s.", 6f, VanillaColors.highlight, "20%")
            addPara("Costs %s ordnance points on a cruiser.", 4f, VanillaColors.highlight, "15")
        }) {
            Text("Hover here", modifier = Modifier.align(Alignment.Center))
        }
        // end snippet
    }

    card("On a disabled button", "tooltip-disabled", "It still shows: where vanilla says why an action is blocked.") {
        // snippet: tooltip-disabled
        Button("Repair", modifier = Modifier.tooltip("Needs 40 more supplies.")) { }.isEnabled = false
        // end snippet
    }
}

internal val contentPages = listOf(text, images, ships, tooltips)
