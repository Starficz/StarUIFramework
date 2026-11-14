package org.starficz.starui

import org.starficz.starui.layout.StackDirection

/**
 * The position of a component inside the space its container gives it. [Alignment] is used to define the alignment of a
 * component inside a parent layout:
 *
 * - in a [Box][org.starficz.starui.components.Box], `Modifier.align(Alignment.TopLeft)`: one of the nine positions;
 * - in a [Column][org.starficz.starui.components.Column], `horizontalAlignment = Alignment.Left`, `CenterHorizontally`
 *   or `Right`;
 * - in a [Row][org.starficz.starui.components.Row], `verticalAlignment = Alignment.Top`, `CenterVertically` or
 *   `Bottom`.
 *
 * Starsector has no right-to-left layouts, so the alignments are absolute: left is always left.
 */
class Alignment private constructor(private val name: String, internal val x: Float, internal val y: Float) {
    /**
     * The horizontal position of a component inside the width it is given. [Alignment.Horizontal] is used to define the
     * alignment of the children across a [Column][org.starficz.starui.components.Column]: [Left], [CenterHorizontally]
     * or [Right].
     */
    class Horizontal internal constructor(private val name: String, internal val alignValue: Float) {
        override fun toString() = name
    }

    /**
     * The vertical position of a component inside the height it is given. [Alignment.Vertical] is used to define the
     * alignment of the children across a [Row][org.starficz.starui.components.Row]: [Top], [CenterVertically] or
     * [Bottom].
     */
    class Vertical internal constructor(private val name: String, internal val alignValue: Float) {
        override fun toString() = name
    }

    override fun toString() = name

    /** A collection of common [Alignment]s. */
    companion object {
        // x: 0 left .. 1 right; y: 0 bottom .. 1 top (Starsector's y points up)
        /** At the top left corner. */
        val TopLeft = Alignment("TopLeft", 0f, 1f)
        /** At the top, centred horizontally. */
        val TopCenter = Alignment("TopCenter", 0.5f, 1f)
        /** At the top right corner. */
        val TopRight = Alignment("TopRight", 1f, 1f)
        /** At the left, centred vertically. */
        val CenterLeft = Alignment("CenterLeft", 0f, 0.5f)
        /** Centred on both axes. */
        val Center = Alignment("Center", 0.5f, 0.5f)
        /** At the right, centred vertically. */
        val CenterRight = Alignment("CenterRight", 1f, 0.5f)
        /** At the bottom left corner. */
        val BottomLeft = Alignment("BottomLeft", 0f, 0f)
        /** At the bottom, centred horizontally. */
        val BottomCenter = Alignment("BottomCenter", 0.5f, 0f)
        /** At the bottom right corner. */
        val BottomRight = Alignment("BottomRight", 1f, 0f)

        /** The nine [Alignment]s, row by row from the top left. */
        val entries: List<Alignment> = listOf(
            TopLeft, TopCenter, TopRight, CenterLeft, Center, CenterRight, BottomLeft, BottomCenter, BottomRight
        )

        /** Aligns to the left edge. */
        val Left = Horizontal("Left", 0f)
        /** Aligns to the centre, horizontally. */
        val CenterHorizontally = Horizontal("CenterHorizontally", 0.5f)
        /** Aligns to the right edge. */
        val Right = Horizontal("Right", 1f)

        /** Aligns to the top edge. */
        val Top = Vertical("Top", 1f)
        /** Aligns to the centre, vertically. */
        val CenterVertically = Vertical("CenterVertically", 0.5f)
        /** Aligns to the bottom edge. */
        val Bottom = Vertical("Bottom", 0f)
    }
}

/**
 * Aligns text horizontally within its component, when the component is wider than the text: [Left], [Center] or
 * [Right].
 */
enum class TextAlign { Left, Center, Right }

/**
 * The direction a [Column][org.starficz.starui.components.Column] stacks its children in: [Downwards], top to bottom
 * (the default), or [Upwards], bottom to top.
 */
enum class VGrow(internal val direction: StackDirection) {
    /** Top to bottom: the first child at the top. */
    Downwards(StackDirection.DOWN),
    /** Bottom to top: the first child at the bottom. */
    Upwards(StackDirection.UP)
}

/**
 * The direction a [Row][org.starficz.starui.components.Row] places its children in: [Rightwards], left to right (the
 * default), or [Leftwards], right to left.
 */
enum class HGrow(internal val direction: StackDirection) {
    /** Left to right: the first child at the left. */
    Rightwards(StackDirection.RIGHT),
    /** Right to left: the first child at the right. */
    Leftwards(StackDirection.LEFT)
}

/**
 * Represents a rule to apply to scale a sprite to be inscribed into the bounds of an
 * [Image][org.starficz.starui.components.Image], when they have a different shape from the sprite.
 */
enum class ContentScale {
    /**
     * Scale the sprite uniformly (maintaining its aspect ratio) so that both dimensions of the sprite will be equal to
     * or less than the corresponding dimension of the bounds, centred in them.
     */
    Fit,
    /** Scale horizontally and vertically non-uniformly to fill the bounds. */
    FillBounds,
    /** Do not apply any scaling to the sprite: draw it at its own size, centred in the bounds. */
    None
}
