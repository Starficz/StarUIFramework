package org.starficz.starui.compose

import org.starficz.starui.compose.layout.StackDirection

/**
 * Where a child sits in its container.
 *
 * - In a Box: `Modifier.align(Alignment.TopLeft)`, one of the nine positions.
 * - In a Column: `horizontalAlignment = Alignment.Left`, `CenterHorizontally` or `Right`.
 * - In a Row: `verticalAlignment = Alignment.Top`, `CenterVertically` or `Bottom`.
 */
class Alignment private constructor(private val name: String, internal val x: Float, internal val y: Float) {
    /** Where children sit across a Column or ScrollPanel: [Left], [CenterHorizontally] or [Right]. */
    class Horizontal internal constructor(private val name: String, internal val alignValue: Float) {
        override fun toString() = name
    }

    /** Where children sit across a Row: [Top], [CenterVertically] or [Bottom]. */
    class Vertical internal constructor(private val name: String, internal val alignValue: Float) {
        override fun toString() = name
    }

    override fun toString() = name

    companion object {
        // x: 0 left .. 1 right; y: 0 bottom .. 1 top (Starsector's y points up)
        val TopLeft = Alignment("TopLeft", 0f, 1f)
        val TopCenter = Alignment("TopCenter", 0.5f, 1f)
        val TopRight = Alignment("TopRight", 1f, 1f)
        val CenterLeft = Alignment("CenterLeft", 0f, 0.5f)
        val Center = Alignment("Center", 0.5f, 0.5f)
        val CenterRight = Alignment("CenterRight", 1f, 0.5f)
        val BottomLeft = Alignment("BottomLeft", 0f, 0f)
        val BottomCenter = Alignment("BottomCenter", 0.5f, 0f)
        val BottomRight = Alignment("BottomRight", 1f, 0f)

        /** The nine Box alignments, row by row from the top left. */
        val entries: List<Alignment> = listOf(
            TopLeft, TopCenter, TopRight, CenterLeft, Center, CenterRight, BottomLeft, BottomCenter, BottomRight
        )

        val Left = Horizontal("Left", 0f)
        val CenterHorizontally = Horizontal("CenterHorizontally", 0.5f)
        val Right = Horizontal("Right", 1f)

        val Top = Vertical("Top", 1f)
        val CenterVertically = Vertical("CenterVertically", 0.5f)
        val Bottom = Vertical("Bottom", 0f)
    }
}

/** Where text sits inside its component when the component is wider than the text. */
enum class TextAlign { Left, Center, Right }

/** Which way a Column stacks its children: [Downwards] (the default) or [Upwards]. */
enum class VGrow(internal val direction: StackDirection) {
    Downwards(StackDirection.DOWN),
    Upwards(StackDirection.UP)
}

/** Which way a Row stacks its children: [Rightwards] (the default) or [Leftwards]. */
enum class HGrow(internal val direction: StackDirection) {
    Rightwards(StackDirection.RIGHT),
    Leftwards(StackDirection.LEFT)
}

/** How an [org.starficz.starui.compose.components.Image]'s sprite fills its box. */
enum class ContentScale {
    /** Scales the sprite to fit inside the box, keeping its shape. */
    Fit,
    /** Stretches the sprite to the box's exact shape. */
    FillBounds,
    /** Draws the sprite at its own size, centred in the box. */
    None
}
