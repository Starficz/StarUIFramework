package org.starficz.starui.compose

import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.layout.NodeSize

sealed interface StarUISizing

// ==========================================
// AXIS PRIMITIVES
// ==========================================
sealed interface Axis {
    /** Wraps the content's natural, intrinsic size. */
    data class Wrap(val padding: Float = 0f) : Axis

    /** Fills available space. Replaces flex. Shares space proportionally based on weight. */
    data class Fill(val weight: Float = 1f) : Axis

    /** Exactly locks the axis to the specified pixels. */
    data class Exact(val pixels: Float) : Axis
}

// ==========================================
// STRICT DOMAIN SIZES
// ==========================================

sealed interface Size : StarUISizing {
    companion object {
        fun wrap(padding: Float = 0f): Size = Mixed(Axis.Wrap(padding), Axis.Wrap(padding))
        fun wrap(xPadding: Float, yPadding: Float): Size = Mixed(Axis.Wrap(xPadding), Axis.Wrap(yPadding))
        fun fill(weight: Float = 1f): Size = Mixed(Axis.Fill(weight), Axis.Fill(weight))
        fun flex(weight: Float = 1f): Size = fill(weight)
        fun fillX(weight: Float = 1f, yPadding: Float = 0f): Size = Mixed(Axis.Fill(weight), Axis.Wrap(yPadding))
        fun fillY(weight: Float = 1f, xPadding: Float = 0f): Size = Mixed(Axis.Wrap(xPadding), Axis.Fill(weight))
        fun fillCross(crossSize: Float, weight: Float = 1f): Size = Mixed(Axis.Fill(weight), Axis.Exact(crossSize))
        fun exact(width: Float, height: Float): Size = ExactSize(width, height)
        fun mixed(xAxis: Axis, yAxis: Axis): Size = Mixed(xAxis, yAxis)
    }
}

sealed interface LayoutSize : StarUISizing {
    companion object {
        fun wrap(padding: Float = 0f): LayoutSize = Mixed(Axis.Wrap(padding), Axis.Wrap(padding))
        fun wrap(xPadding: Float, yPadding: Float): LayoutSize = Mixed(Axis.Wrap(xPadding), Axis.Wrap(yPadding))
        fun fill(weight: Float = 1f): LayoutSize = Mixed(Axis.Fill(weight), Axis.Fill(weight))
        fun flex(weight: Float = 1f): LayoutSize = fill(weight)
        fun fillX(weight: Float = 1f, yPadding: Float = 0f): LayoutSize = Mixed(Axis.Fill(weight), Axis.Wrap(yPadding))
        fun fillY(weight: Float = 1f, xPadding: Float = 0f): LayoutSize = Mixed(Axis.Wrap(xPadding), Axis.Fill(weight))
        fun exact(width: Float, height: Float): LayoutSize = ExactSize(width, height)
        fun mixed(xAxis: Axis, yAxis: Axis): LayoutSize = Mixed(xAxis, yAxis)
    }
}

sealed interface PanelSize : StarUISizing {
    companion object {
        fun wrap(padding: Float = 0f): PanelSize = Mixed(Axis.Wrap(padding), Axis.Wrap(padding))
        fun wrap(xPadding: Float, yPadding: Float): PanelSize = Mixed(Axis.Wrap(xPadding), Axis.Wrap(yPadding))
        fun fill(weight: Float = 1f): PanelSize = Mixed(Axis.Fill(weight), Axis.Fill(weight))
        fun flex(weight: Float = 1f): PanelSize = fill(weight)
        fun fillX(weight: Float = 1f, yPadding: Float = 0f): PanelSize = Mixed(Axis.Fill(weight), Axis.Wrap(yPadding))
        fun fillY(weight: Float = 1f, xPadding: Float = 0f): PanelSize = Mixed(Axis.Wrap(xPadding), Axis.Fill(weight))
        fun exact(width: Float, height: Float): PanelSize = ExactSize(width, height)
        fun mixed(xAxis: Axis, yAxis: Axis): PanelSize = Mixed(xAxis, yAxis)
    }
}

sealed interface ImageSize : StarUISizing {
    companion object {
        /** Uses intrinsic size, optionally scaled or clamped by max bounds. */
        fun native(scale: Float = 1f, maxWidth: Float? = null, maxHeight: Float? = null): ImageSize = NativeImage(scale, maxWidth, maxHeight)

        /** Exact physical box constraints. Rendering behavior depends on ContentScale. */
        fun exact(width: Float, height: Float): ImageSize = ExactSize(width, height)

        /** Fills physically on both axes. Rendering behavior depends on ContentScale. */
        fun fill(weight: Float = 1f): ImageSize = Mixed(Axis.Fill(weight), Axis.Fill(weight))
        fun fillX(weight: Float = 1f, yPadding: Float = 0f): ImageSize = Mixed(Axis.Fill(weight), Axis.Wrap(yPadding))
        fun fillY(weight: Float = 1f, xPadding: Float = 0f): ImageSize = Mixed(Axis.Wrap(xPadding), Axis.Fill(weight))
        fun mixed(xAxis: Axis, yAxis: Axis): ImageSize = Mixed(xAxis, yAxis)

        /** Derives an exact Box based on the Texture's Aspect Ratio. No Letterboxing! */
        fun aspectWrap(): ImageSize = AspectWrapImage()
    }
}

typealias ContentSize = Size
typealias StackSize = LayoutSize
typealias LabelSize = Size

// ==========================================
// INTERNAL IMPLEMENTATIONS
// ==========================================
internal data class Mixed(val x: Axis, val y: Axis) : Size, PanelSize, LayoutSize, ImageSize
internal data class ExactSize(val width: Float, val height: Float) : Size, PanelSize, LayoutSize, ImageSize
internal data class NativeImage(val scale: Float, val maxWidth: Float?, val maxHeight: Float?) : ImageSize
internal class AspectWrapImage : ImageSize

internal fun Axis.toDimension(): Dimension = when (this) {
    is Axis.Wrap -> Dimension.WrapContent
    is Axis.Fill -> Dimension.FillMax(weight)
    is Axis.Exact -> Dimension.Exact(pixels)
}

internal fun StarUISizing.toNodeSize(): NodeSize = when (this) {
    is ExactSize -> NodeSize(Dimension.Exact(width), Dimension.Exact(height))
    is Mixed -> NodeSize(x.toDimension(), y.toDimension())
    is NativeImage -> NodeSize(
        maxWidth?.let { Dimension.Exact(it) } ?: Dimension.WrapContent,
        maxHeight?.let { Dimension.Exact(it) } ?: Dimension.WrapContent
    )
    is AspectWrapImage -> NodeSize(Dimension.AspectRatio, Dimension.AspectRatio)
}

internal fun StarUISizing.wrapPadding(): Pair<Float, Float> = when (this) {
    is Mixed -> Pair(
        (x as? Axis.Wrap)?.padding ?: 0f,
        (y as? Axis.Wrap)?.padding ?: 0f
    )
    else -> Pair(0f, 0f)
}