package org.starficz.staruisamples.showcase

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.LabelAPI
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.vanilla.*

private const val FILE = "LayoutPages.kt"

// snippet: size-state
private val wide = UIState(false)
// end snippet
// snippet: overflow-lines
private val lines = UIState(3)
// end snippet
// snippet: holo-state
private val holoOpen = UIState(true)
private val holoPage = UIState(0)
// end snippet
// snippet: holo-background
private val seeThrough = UIState(false)
// end snippet

private val columnsAndRows = Page("Layout", "Column and Row",
    "The two stacks: children one after another, down or across, with a gap between them and an alignment across.", FILE) {

    card("Spacing and alignment across", "column-align",
        "spacing is the gap between children; horizontalAlignment places each child across the column's width.") {
        // snippet: column-align
        Row(Size.fillMaxWidth(), spacing = 6f) {
            for (align in listOf(Alignment.Left, Alignment.CenterHorizontally, Alignment.Right)) {
                Column(Size.fillMaxWidth(), Modifier.background(INSET).padding(4f), horizontalAlignment = align, spacing = 4f) {
                    Text(align.toString())
                    Spacer(Size.exact(40f, 10f), Modifier.background(VanillaColors.base))
                    Spacer(Size.exact(20f, 10f), Modifier.background(VanillaColors.base))
                }
            }
        }
        // end snippet
    }

    card("A Row's vertical alignment", "row-align", "verticalAlignment places each child up and down the row's height.") {
        // snippet: row-align
        for (align in listOf(Alignment.Top, Alignment.CenterVertically, Alignment.Bottom)) {
            Row(Size.fillMaxWidth(height = 34f), Modifier.background(INSET).padding(horizontal = 4f),
                verticalAlignment = align, spacing = 6f) {
                Spacer(Size.exact(12f, 30f), Modifier.background(VanillaColors.base))
                Spacer(Size.exact(12f, 12f), Modifier.background(VanillaColors.base))
                Text(align.toString())
            }
        }
        // end snippet
    }

    card("Growing the other way", "grow",
        "grow = Upwards stacks from the bottom, Leftwards from the right: the first child sits at that edge.") {
        // snippet: grow
        Row(Size.fillMaxWidth(height = 70f), spacing = 6f) {
            Column(Size.fillMaxSize(), Modifier.background(INSET).padding(4f), grow = VGrow.Upwards, spacing = 2f) {
                Text("1st, at the bottom")
                Text("2nd")
                Text("3rd")
            }
            Row(Size.fillMaxSize(), Modifier.background(INSET).padding(4f), grow = HGrow.Leftwards, spacing = 8f) {
                Text("1st")
                Text("2nd")
                Text("3rd")
            }
        }
        // end snippet
    }

    card("Pushing to the far end", "flex",
        "A FlexSpacer takes the room the others leave. Two share it by weight.") {
        // snippet: flex
        Row(Size.fillMaxWidth(), Modifier.background(INSET).padding(4f)) {
            Text("Left")
            FlexSpacer()
            Text("Right")
        }
        Row(Size.fillMaxWidth(), Modifier.background(INSET).padding(4f)) {
            Text("A")
            FlexSpacer(1f)
            Text("1 : 3")
            FlexSpacer(3f)
            Text("B")
        }
        // end snippet
    }
}

private val boxes = Page("Layout", "Box and anchors",
    "A Box puts its children on top of each other at nine places; a CustomPanel places each child by an anchor.", FILE) {

    card("Nine places", "box-align", "Modifier.align in a Box: a corner, an edge's middle or the centre.") {
        // snippet: box-align
        Box(Size.fillMaxWidth(height = 100f), Modifier.background(INSET)) {
            for (place in Alignment.entries) {
                Spacer(Size.exact(14f, 14f), Modifier.align(place).margin(5f).background(VanillaColors.base))
            }
            Text("align(Center)", modifier = Modifier.align(Alignment.Center).margin(top = 30f))
        }
        // end snippet
    }

    card("Layers of content", "box-layers", "Later children draw over earlier ones: a fill, a label, a badge.") {
        // snippet: box-layers
        Box(Size.exact(220f, 60f), Modifier.border()) {
            Spacer(Size.fillMaxSize(), Modifier.background(VanillaColors.dark.withAlpha(140)))
            Text("Onslaught", font = Font.INSIGNIA_21, modifier = Modifier.align(Alignment.CenterLeft).margin(left = 10f))
            Box(Size.wrapContent(), Modifier.align(Alignment.TopRight).margin(4f)
                .background(VanillaColors.negative.darker()).padding(horizontal = 4f, vertical = 1f)) {
                Text("D-mods 2", color = VanillaColors.text)
            }
        }
        // end snippet
    }

    card("Inside the parent", "anchor-inside",
        "In a CustomPanel, Modifier.anchor places a child; the spacing moves it inward from the named edges.") {
        // snippet: anchor-inside
        CustomPanel(Size.fillMaxWidth(height = 110f), Modifier.background(INSET)) {
            Text("inside.topLeft", modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(6f, 6f)))
            Text("inside.topRight", modifier = Modifier.anchor(Anchor.inside.topRight.ofParent(6f, 6f)))
            Text("inside.center", color = VanillaColors.highlight, modifier = Modifier.anchor(Anchor.inside.center.ofParent()))
            Text("inside.bottomLeft", modifier = Modifier.anchor(Anchor.inside.bottomLeft.ofParent(6f, 6f)))
            Text("inside.bottomRight", modifier = Modifier.anchor(Anchor.inside.bottomRight.ofParent(6f, 6f)))
        }
        // end snippet
    }

    card("Next to another component", "anchor-sibling",
        "above, below, leftOf and rightOf a sibling, lined up on an edge or the centre; previousComponent is the one before.") {
        // snippet: anchor-sibling
        CustomPanel(Size.fillMaxWidth(height = 120f), Modifier.background(INSET)) {
            val hub = Spacer(Size.exact(70f, 24f), Modifier.anchor(Anchor.inside.center.ofParent()).background(TILE))
            Text("above", modifier = Modifier.anchor(Anchor.above.sibling(hub).matchingCenter(4f)))
            Text("below, right edge", modifier = Modifier.anchor(Anchor.below.sibling(hub).matchingRightEdge(4f)))
            Text("leftOf", modifier = Modifier.anchor(Anchor.leftOf.sibling(hub).matchingCenter(6f)))
            Text("rightOf, bottom edge", modifier = Modifier.anchor(Anchor.rightOf.sibling(hub).matchingBottomEdge(6f)))
            Text("then below that", color = VanillaColors.gray, modifier = Modifier.anchor(Anchor.below.previousComponent.matchingLeftEdge(2f)))
        }
        // end snippet
    }

    card("My corner to yours", "anchor-my",
        "Anchor.my puts a point of this component on a point of the reference: here its bottom left on the hub's top right.") {
        // snippet: anchor-my
        CustomPanel(Size.fillMaxWidth(height = 80f), Modifier.background(INSET)) {
            val hub = Spacer(Size.exact(70f, 24f), Modifier.anchor(Anchor.inside.center.ofParent()).background(TILE))
            Spacer(Size.exact(10f, 10f), Modifier.anchor(Anchor.my.bottomLeft.toTopRight.ofSibling(hub)).background(VanillaColors.bright))
            Spacer(Size.exact(10f, 10f), Modifier.anchor(Anchor.my.topRight.toBottomLeft.ofSibling(hub)).background(VanillaColors.highlight))
        }
        // end snippet
    }
}

private val sizes = Page("Layout", "Sizes",
    "A component's size is its size argument: exact, fill or wrap on each axis. There are no size modifiers.", FILE) {

    card("Exact, fill and wrap", "size-kinds",
        "wrapContent fits the content, exact is pixels, fillMaxWidth takes the room the container has.") {
        // snippet: size-kinds
        Box(Size.wrapContent(), Modifier.background(INSET).padding(4f)) { Text("wrapContent()") }
        Box(Size.exact(200f, 30f), Modifier.background(INSET).padding(4f)) { Text("exact(200, 30)") }
        Box(Size.fillMaxWidth(), Modifier.background(INSET).padding(4f)) { Text("fillMaxWidth()") }
        Box(Size.fillMaxWidth(height = 30f), Modifier.background(INSET).padding(4f)) { Text("fillMaxWidth(height = 30)") }
        // end snippet
    }

    card("Sharing by weight", "size-weights",
        "Fill children share what is left by weight, along the container's main axis; exact ones take theirs first.") {
        // snippet: size-weights
        Row(Size.fillMaxWidth(height = 24f), spacing = 4f) {
            Spacer(Size.exact(30f, 24f), Modifier.background(VanillaColors.bright))
            Spacer(Size.fillMaxSize(1f), Modifier.background(VanillaColors.base.withAlpha(80)))
            Spacer(Size.fillMaxSize(2f), Modifier.background(VanillaColors.base.withAlpha(160)))
            Spacer(Size.fillMaxSize(3f), Modifier.background(VanillaColors.base))
        }
        // end snippet
    }

    card("Mixed axes", "size-mixed", "Any pair: Size.mixed(Axis..., Axis...).") {
        // snippet: size-mixed
        Row(Size.fillMaxWidth(height = 40f), Modifier.background(INSET), verticalAlignment = Alignment.CenterVertically, spacing = 4f) {
            Spacer(Size.mixed(Axis.Exact(60f), Axis.FillMax()), Modifier.background(VanillaColors.base))
            Spacer(Size.mixed(Axis.FillMax(), Axis.Exact(10f)), Modifier.background(TILE))
            Box(Size.mixed(Axis.WrapContent, Axis.FillMax()), Modifier.background(TILE).padding(horizontal = 6f)) {
                Text("wrap x fill", modifier = Modifier.align(Alignment.Center))
            }
        }
        // end snippet
    }

    card("A size from state", "size-state", "The size is an argument like any other: read a state and it follows.") {
        // snippet: size-state
        Checkbox("Wide", bind = wide)
        Box(Size.exact(if (wide.value) 300f else 120f, 30f), Modifier.background(INSET).padding(4f)) {
            Text(if (wide.value) "300 wide" else "120 wide")
        }
        // end snippet
    }
}

private val spacing = Page("Layout", "Spacing",
    "margin is outside a component, padding inside it, spacing between a container's children. Each takes all, " +
        "horizontal and vertical, or sides by name.", FILE) {

    card("Margin and padding", "margin-padding") {
        // snippet: margin-padding
        Row(Size.fillMaxWidth(), Modifier.background(INSET)) {
            Spacer(Size.exact(30f, 16f), Modifier.margin(10f).background(VanillaColors.base))
            Text("margin(10) around the block", modifier = Modifier.margin(vertical = 10f))
        }
        Box(Size.wrapContent(), Modifier.background(INSET).padding(12f)) {
            Text("padding(12) inside the box")
        }
        // end snippet
    }

    card("By side", "sides", "Sides by name only, so a side is never guessed from a position.") {
        // snippet: sides
        Box(Size.fillMaxWidth(), Modifier.background(INSET).padding(left = 40f, top = 4f, bottom = 16f)) {
            Text("padding(left = 40, top = 4, bottom = 16)")
        }
        Row(Size.fillMaxWidth(), Modifier.background(INSET)) {
            Spacer(Size.exact(30f, 16f), Modifier.margin(left = 60f, vertical = 6f).background(VanillaColors.base))
        }
        // end snippet
    }

    card("A larger hit box", "hitbox",
        "hitBoxPadding grows the area that takes the mouse, not the component: click to the right of the button.") {
        // snippet: hitbox
        Row(spacing = 8f, verticalAlignment = Alignment.CenterVertically) {
            lateinit var label: LabelAPI
            Button("Small", modifier = Modifier.hitBoxPadding(right = 120f)) { label.text = "Hit" }
            label = Text("not yet", color = VanillaColors.gray)
        }
        // end snippet
    }

    card("Borders take their space", "border-space",
        "A border's width is added to the padding, as in CSS: padding is the gap inside the border.") {
        // snippet: border-space
        Row(spacing = 8f) {
            Box(Size.wrapContent(), Modifier.background(INSET).padding(6f)) { Text("no border") }
            Box(Size.wrapContent(), Modifier.background(INSET).border(VanillaColors.base, 4f).padding(6f)) { Text("border 4") }
            Box(Size.wrapContent(), Modifier.border(VanillaFrame.TitleMenu).padding(6f)) { Text("TitleMenu frame") }
        }
        // end snippet
    }
}

private val overflow = Page("Layout", "Overflow",
    "What a container does with children that do not fit, per axis: Visible (spill out, the default), Clip, Scroll.", FILE) {

    card("Visible and clip", "overflow-clip", "The same child, bigger than its 110 x 40 box.") {
        // snippet: overflow-clip
        Row(Size.fillMaxWidth(height = 64f), spacing = 60f) {
            for ((name, overflow) in listOf("Clip" to Overflow.Clip, "Visible" to Overflow.Visible)) {
                Box(Size.exact(110f, 40f), Modifier.border(VanillaColors.bright), overflow = overflow) {
                    Box(Size.exact(160f, 56f), Modifier.background(INSET).padding(4f)) { Text("$name: too big for its box") }
                }
            }
        }
        // end snippet
    }

    card("A scrolling list", "overflow-lines",
        "Overflow.ScrollVertical. A fill spacer pins the buttons to the bottom until the lines overflow.") {
        // snippet: overflow-lines
        Column(Size.fillMaxWidth(height = 120f), Modifier.background(INSET).padding(4f), spacing = 2f, overflow = Overflow.ScrollVertical) {
            for (i in 1..lines.value) Text("Line $i")
            FlexSpacer()
            Row(spacing = 4f) {
                Button("Add") { lines.value++ }
                Button("Remove") { if (lines.value > 0) lines.value-- }
            }
        }
        // end snippet
    }

    card("Sideways", "overflow-sideways", "Overflow.ScrollHorizontal: the plain wheel scrolls it, as nothing overflows down.") {
        // snippet: overflow-sideways
        Row(Size.fillMaxWidth(height = 56f), Modifier.background(INSET), spacing = 6f,
            verticalAlignment = Alignment.CenterVertically, overflow = Overflow.ScrollHorizontal) {
            for (i in 1..12) Box(Size.exact(80f, 44f), Modifier.background(TILE).padding(4f)) { Text("Card $i") }
        }
        // end snippet
    }

    card("Both ways", "overflow-both", "Overflow.Scroll: the wheel scrolls down, Shift + wheel across, a right drag pans.") {
        // snippet: overflow-both
        Box(Size.fillMaxWidth(height = 120f), Modifier.background(INSET), overflow = Overflow.Scroll) {
            Column(spacing = 2f) {
                for (r in 1..12) Row(spacing = 2f) {
                    for (c in 1..12) Box(Size.exact(54f, 22f), Modifier.background(if ((r + c) % 2 == 0) TILE else INSET)) {
                        Text("$r, $c", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        // end snippet
    }

    card("The scroll panel's looks", "overflow-theme",
        "From the theme's scroll: the scrollbar on the left, its colour, no shadows. scrollPanel gives vanilla's panel.") {
        // snippet: overflow-theme
        val scroll = StarUITheme.current.scroll.copy(isLeftScrollbar = true, scrollWidgetColor = VanillaColors.bright, renderShadows = false)
        lateinit var list: CustomPanelAPI
        Theme(StarUITheme.current.copy(scroll = scroll)) {
            list = Column(Size.fillMaxWidth(height = 100f), Modifier.background(INSET).padding(left = 14f), spacing = 2f,
                overflow = Overflow.ScrollVertical) {
                for (i in 1..20) Text("Line $i")
            }
        }
        Row(spacing = 4f) {
            Button("To the top") { list.scrollPanel?.scrollToY(0f) }
            Button("To the bottom") { list.scrollPanel?.scrollToBottom() }
        }
        // end snippet
    }
}

private val sections = Page("Layout", "Sections and dividers",
    "The frames vanilla's campaign panels are made of: a section with a heading bar, a heading alone, a line.", FILE) {

    card("A section", "section", "Section(title): vanilla's heading bar over a bordered panel; its builder is a column.") {
        // snippet: section
        Section("Fleet") {
            Text("Ships: 12")
            Text("Supplies a month: 48", color = VanillaColors.gray)
        }
        // end snippet
    }

    card("Section options", "section-options") {
        // snippet: section-options
        Row(Size.fillMaxWidth(), spacing = 8f) {
            Section(null, Size.fillMaxWidth(), contentPadding = PaddingValues.all(10f)) {
                Text("No title")
                Text("contentPadding = all(10)", color = VanillaColors.gray)
            }
            Section("Own colours", Size.fillMaxWidth(), backgroundColor = VanillaColors.dark.withAlpha(120),
                borderColor = VanillaColors.bright, spacing = 1f) {
                Text("background, border")
                Text("spacing = 1", color = VanillaColors.gray)
            }
        }
        // end snippet
    }

    card("Section headings", "heading", "Vanilla's own heading bar; it fills the width by default.") {
        // snippet: heading
        SectionHeading("Centred, the default")
        SectionHeading("Left", textAlign = TextAlign.Left)
        SectionHeading("Own colours", color = VanillaColors.highlight, backgroundColor = VanillaColors.highlight.withAlpha(40),
            textAlign = TextAlign.Right)
        // end snippet
    }

    card("Dividers", "divider") {
        // snippet: divider
        Text("Above")
        Divider()
        Text("Between")
        Divider(thickness = 3f, color = VanillaColors.base)
        Text("Below")
        // end snippet
    }

    card("Borders", "borders", "A line in any colour and width, or vanilla's sprite frame (ui_border1).") {
        // snippet: borders
        Row(spacing = 8f, verticalAlignment = Alignment.CenterVertically) {
            Box(Size.wrapContent(), Modifier.border().padding(6f)) { Text("border()") }
            Box(Size.wrapContent(), Modifier.border(VanillaColors.highlight, 2f).padding(6f)) { Text("highlight, 2") }
            Box(Size.wrapContent(), Modifier.border(VanillaFrame.TitleMenu).padding(6f)) { Text("TitleMenu") }
        }
        // end snippet
    }
}

private val holos = Page("Layout", "Holo",
    "Vanilla's holo window: the frame of its dialogs and title menu, a scanline background, the fold open and shut " +
        "with a burst of static (shown when UI noise is on in the game's settings).", FILE) {

    card("Open and shut", "holo-state",
        "open folds it; contentKey swaps the content through the static, as vanilla's tabbed holos change tabs.") {
        // snippet: holo-state
        Row(spacing = 4f) {
            Button(if (holoOpen.value) "Close" else "Open") { holoOpen.value = !holoOpen.value }
            Button("Next page") { holoPage.value = (holoPage.value + 1) % 3 }
        }
        val page = holoPage.value
        Holo(Size.exact(280f, 120f), open = holoOpen.value, contentKey = page) {
            Column(Size.fillMaxSize(), Modifier.padding(8f), spacing = 4f) {
                Text("Page ${page + 1} of 3", font = Font.ORBITRON_12, color = VanillaColors.base)
                Text("The content is a StarUI Box inside the frame.", Size.fillMaxWidth())
            }
        }
        // end snippet
    }

    card("Backgrounds", "holo-background",
        "HoloBackground.Dialog is opaque, as most of vanilla's dialogs; TitleScreen lets the screen show through.") {
        // snippet: holo-background
        Checkbox("See-through", bind = seeThrough)
        Holo(Size.exact(280f, 80f), background = if (seeThrough.value) HoloBackground.TitleScreen else HoloBackground.Dialog) {
            Text("Over the page", modifier = Modifier.align(Alignment.Center))
        }
        // end snippet
    }

    card("The title menu, rebuilt", "holo-title",
        "Vanilla's title menu box: a holo with contentInset 3; Orbitron 24 bold buttons 30 less than its width, the first 15 px in.") {
        // snippet: holo-title
        Holo(Size.exact(250f, 150f), contentInset = 3f, background = HoloBackground.TitleScreen) {
            Column(Size.fillMaxSize(), Modifier.padding(top = 15f), horizontalAlignment = Alignment.CenterHorizontally, spacing = 4f) {
                for (label in listOf("Continue", "New Game", "Load Game")) {
                    Button(label, Size.exact(220f, 30f), font = ButtonFont.ORBITRON_24_BOLD, textAlign = TextAlign.Right,
                        cutStyle = CutStyle.TL_BR) { }
                }
            }
        }
        // end snippet
    }
}

internal val layoutPages = listOf(columnsAndRows, boxes, sizes, spacing, overflow, sections, holos)
