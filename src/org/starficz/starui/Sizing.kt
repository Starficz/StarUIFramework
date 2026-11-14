package org.starficz.starui

import org.starficz.starui.layout.NodeSize

// ==========================================
// AXES
// ==========================================

/**
 * How one axis of a component is sized: [WrapContent], [FillMax] or [Exact]. A [Size] is one [Axis] for the width
 * and one for the height; the [Size] factories cover the common pairs, and [Size.mixed] takes any two.
 */
sealed interface Axis {
    /** The content's own size on this axis: the component is as large as what it shows or holds. */
    data object WrapContent : Axis

    /**
     * Fill the space the container has on this axis. On the main axis of a
     * [Column][org.starficz.starui.components.Column] or [Row][org.starficz.starui.components.Row], the space that
     * remains after measuring the children that do not fill is divided between the filling children in proportion to
     * their [weight]. On any other axis, the component takes the container's whole size.
     *
     * The container needs a known size on that axis (exact or fill): a filling child in a wrapping container throws.
     *
     * @property weight the proportional size to give to this component, as related to the total of all filling
     *   siblings. Should be positive
     */
    data class FillMax(val weight: Float = 1f) : BoundedAxis

    /**
     * Exactly [pixels] on this axis, whatever the content and the container.
     *
     * @property pixels the size, in pixels
     */
    data class Exact(val pixels: Float) : BoundedAxis
}

/** An axis whose size does not depend on the content: [Axis.FillMax] or [Axis.Exact]. */
sealed interface BoundedAxis : Axis

// ==========================================
// SIZES
// ==========================================
// One vocabulary (Compose's words) and three types, each a superset of the one before:
//   BoundedSize  exact or fill on both axes; for components with no content to wrap (Spacer, ShipDisplay, sliders,
//                lazy lists)
//   Size         adds wrap; for everything else
//   ImageSize    adds the sprite's own size and aspect ratio; for Image
// Every factory is on Size (image-only ones on ImageSize) and returns the narrowest type it can, so `Size.exact(..)`
// goes anywhere and `Size.wrapContent()` is a compile error for a ShipDisplay.

/**
 * The size of an [Image][org.starficz.starui.components.Image]: any [Size], or one taken from the sprite with
 * [native] or [aspectWrap].
 */
sealed interface ImageSize {
    companion object {
        /**
         * The sprite's own size, times [scale], shrunk to fit [maxWidth] and [maxHeight] when they are given, keeping
         * its shape.
         *
         * @param scale the factor applied to the sprite's size
         * @param maxWidth the largest width, or `null` for no limit
         * @param maxHeight the largest height, or `null` for no limit
         */
        fun native(scale: Float = 1f, maxWidth: Float? = null, maxHeight: Float? = null): ImageSize = NativeImage(scale, maxWidth, maxHeight)

        /** As large as the space allows, keeping the sprite's shape. */
        fun aspectWrap(): ImageSize = AspectWrapImage()
    }
}

/**
 * The size of a component: one [Axis] for the width and one for the height. It is the component's `size` argument,
 * the only way to size it, since StarUI has no sizing modifiers. The factories use Compose's words:
 *
 * ```kotlin
 * Size.wrapContent()                 // the content's size
 * Size.fillMaxWidth()                // the container's width, the content's height
 * Size.fillMaxHeight(width = 200f)   // 200 wide, the container's height
 * Size.exact(300f, 40f)
 * Size.mixed(Axis.FillMax(2f), Axis.Exact(26f))   // any other pair
 * ```
 *
 * A component that has no content to wrap takes a [BoundedSize] instead, so `Size.wrapContent()` there is a
 * compile error.
 *
 * @see Axis
 */
sealed interface Size : ImageSize {
    companion object {
        /** Have the component measure at the content's own size, on both axes. */
        fun wrapContent(): Size = Mixed(Axis.WrapContent, Axis.WrapContent)

        /**
         * Have the component fill the width of its container, at the content's height.
         *
         * @param weight the share of the leftover width, in a [Row][org.starficz.starui.components.Row]. See
         *   [Axis.FillMax]
         */
        fun fillMaxWidth(weight: Float = 1f): Size = Mixed(Axis.FillMax(weight), Axis.WrapContent)
        /**
         * Have the component fill the width of its container, at an exact [height].
         *
         * @param height the height, in pixels
         * @param weight the share of the leftover width, in a [Row][org.starficz.starui.components.Row]. See
         *   [Axis.FillMax]
         */
        fun fillMaxWidth(height: Float, weight: Float = 1f): BoundedSize = Mixed(Axis.FillMax(weight), Axis.Exact(height))

        /**
         * Have the component fill the height of its container, at the content's width.
         *
         * @param weight the share of the leftover height, in a [Column][org.starficz.starui.components.Column]. See
         *   [Axis.FillMax]
         */
        fun fillMaxHeight(weight: Float = 1f): Size = Mixed(Axis.WrapContent, Axis.FillMax(weight))
        /**
         * Have the component fill the height of its container, at an exact [width].
         *
         * @param width the width, in pixels
         * @param weight the share of the leftover height, in a [Column][org.starficz.starui.components.Column]. See
         *   [Axis.FillMax]
         */
        fun fillMaxHeight(width: Float, weight: Float = 1f): BoundedSize = Mixed(Axis.Exact(width), Axis.FillMax(weight))

        /**
         * Have the component fill the whole space of its container, on both axes.
         *
         * @param weight the share of the leftover space on a [Column][org.starficz.starui.components.Column]'s or
         *   [Row][org.starficz.starui.components.Row]'s main axis. See [Axis.FillMax]
         */
        fun fillMaxSize(weight: Float = 1f): BoundedSize = Mixed(Axis.FillMax(weight), Axis.FillMax(weight))

        /**
         * Declare the size of the component to be exactly [width] by [height] pixels, whatever its content and
         * container.
         */
        fun exact(width: Float, height: Float): BoundedSize = Mixed(Axis.Exact(width), Axis.Exact(height))

        /**
         * Any pair of axes: `Size.mixed(Axis.Exact(100f), Axis.FillMax(2f))`.
         *
         * @param width how the width is sized
         * @param height how the height is sized
         */
        fun mixed(width: Axis, height: Axis): Size = Mixed(width, height)
        /**
         * Any pair of axes that are each exact or fill, for components that take a [BoundedSize].
         *
         * @param width how the width is sized
         * @param height how the height is sized
         */
        fun mixed(width: BoundedAxis, height: BoundedAxis): BoundedSize = Mixed(width, height)
    }
}

/**
 * A size that does not depend on the content: exact or fill on both axes. Components with no content to wrap take
 * one: [Spacer][org.starficz.starui.components.Spacer], [ShipDisplay][org.starficz.starui.components.ShipDisplay],
 * [Slider][org.starficz.starui.components.Slider], [ProgressBar][org.starficz.starui.components.ProgressBar], the
 * lazy lists and the [Holo][org.starficz.starui.components.Holo].
 */
sealed interface BoundedSize : Size

// ==========================================
// INTERNAL IMPLEMENTATIONS
// ==========================================
internal data class Mixed(val x: Axis, val y: Axis) : BoundedSize
internal data class NativeImage(val scale: Float, val maxWidth: Float?, val maxHeight: Float?) : ImageSize
internal class AspectWrapImage : ImageSize

/**
 * An axis sized from the other one by the sprite's aspect ratio ([ImageSize.aspectWrap]). Internal: no public size
 * makes it, so code outside StarUI never meets it.
 */
internal data object AspectRatioAxis : Axis

internal fun ImageSize.toNodeSize(): NodeSize = when (this) {
    is Mixed -> NodeSize(x, y)
    is NativeImage -> NodeSize(
        maxWidth?.let { Axis.Exact(it) } ?: Axis.WrapContent,
        maxHeight?.let { Axis.Exact(it) } ?: Axis.WrapContent
    )
    is AspectWrapImage -> NodeSize(AspectRatioAxis, AspectRatioAxis)
}
