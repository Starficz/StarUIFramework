package org.starficz.starui

import org.starficz.starui.layout.NodeSize

// ==========================================
// AXES
// ==========================================

/** How one axis of a component is sized. */
sealed interface Axis {
    /** As large as the content on this axis. */
    data object WrapContent : Axis

    /** Takes the space its container has left on this axis; several fill children share it by [weight]. */
    data class FillMax(val weight: Float = 1f) : BoundedAxis

    /** Exactly [pixels]. */
    data class Exact(val pixels: Float) : BoundedAxis
}

/** An axis that does not depend on content: [Axis.FillMax] or [Axis.Exact]. */
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

/** A size for [org.starficz.starui.components.Image]: any [Size], or one taken from the sprite. */
sealed interface ImageSize {
    companion object {
        /** The sprite's own size, times [scale], shrunk to fit [maxWidth] / [maxHeight] when given. */
        fun native(scale: Float = 1f, maxWidth: Float? = null, maxHeight: Float? = null): ImageSize = NativeImage(scale, maxWidth, maxHeight)

        /** As large as the space allows, keeping the sprite's shape. */
        fun aspectWrap(): ImageSize = AspectWrapImage()
    }
}

/** A size for any component that has content to wrap. */
sealed interface Size : ImageSize {
    companion object {
        /** As large as the content on both axes. */
        fun wrapContent(): Size = Mixed(Axis.WrapContent, Axis.WrapContent)

        /** Fills the width, as tall as the content. */
        fun fillMaxWidth(weight: Float = 1f): Size = Mixed(Axis.FillMax(weight), Axis.WrapContent)
        /** Fills the width at an exact [height]. */
        fun fillMaxWidth(height: Float, weight: Float = 1f): BoundedSize = Mixed(Axis.FillMax(weight), Axis.Exact(height))

        /** Fills the height, as wide as the content. */
        fun fillMaxHeight(weight: Float = 1f): Size = Mixed(Axis.WrapContent, Axis.FillMax(weight))
        /** Fills the height at an exact [width]. */
        fun fillMaxHeight(width: Float, weight: Float = 1f): BoundedSize = Mixed(Axis.Exact(width), Axis.FillMax(weight))

        /** Fills both axes. */
        fun fillMaxSize(weight: Float = 1f): BoundedSize = Mixed(Axis.FillMax(weight), Axis.FillMax(weight))

        /** Exactly [width] by [height]. */
        fun exact(width: Float, height: Float): BoundedSize = Mixed(Axis.Exact(width), Axis.Exact(height))

        /** Any [width] and [height]: `mixed(Axis.Exact(100f), Axis.FillMax(2f))`. */
        fun mixed(width: Axis, height: Axis): Size = Mixed(width, height)
        /** Any [width] and [height] that are each exact or fill. */
        fun mixed(width: BoundedAxis, height: BoundedAxis): BoundedSize = Mixed(width, height)
    }
}

/** A size that does not depend on content: exact or fill on both axes. */
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
