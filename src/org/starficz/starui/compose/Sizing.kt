package org.starficz.starui.compose


sealed interface StarUISizing

sealed interface CrossAxis {
    companion object {
        fun wrap(): CrossAxis = CrossWrapImpl
        fun fill(): CrossAxis = CrossFillImpl
        fun exact(size: Float): CrossAxis = CrossExactImpl(size)
    }
}

sealed interface ImageCrossAxis {
    companion object {
        fun fill(): ImageCrossAxis = CrossFillImpl
        fun exact(size: Float): ImageCrossAxis = CrossExactImpl(size)
        fun maintainAspectRatio(): ImageCrossAxis = CrossAspectRatioImpl
    }
}

internal sealed interface CrossAxisDirective

sealed interface ContentSize : StarUISizing {
    companion object {
        fun exact(width: Float, height: Float): ContentSize = ExactImpl(width, height)
        fun wrap(xPad: Float = 4f, yPad: Float = 4f): ContentSize = WrapContentImpl(xPad, yPad)
        fun flex(weight: Float = 1f, crossAxis: CrossAxis = CrossAxis.wrap()): ContentSize = FlexImpl(weight, crossAxis as CrossAxisDirective)
        fun fillCross(primarySize: Float): ContentSize = FillCrossImpl(primarySize)
    }
}

sealed interface ImageSize : StarUISizing {
    companion object {
        fun exact(width: Float, height: Float): ImageSize = ExactImpl(width, height)
        fun native(scale: Float = 1f): ImageSize = NativeImageImpl(scale)
        fun scaledToWidth(width: Float): ImageSize = ScaledWidthImpl(width)
        fun scaledToHeight(height: Float): ImageSize = ScaledHeightImpl(height)
        fun flex(weight: Float = 1f, crossAxis: ImageCrossAxis = ImageCrossAxis.maintainAspectRatio()): ImageSize = FlexImpl(weight, crossAxis as CrossAxisDirective)
    }
}

sealed interface LabelSize : StarUISizing {
    companion object {
        fun wrap(): LabelSize = WrapLabelImpl(null)
        fun wrapAt(maxWidth: Float): LabelSize = WrapLabelImpl(maxWidth)
    }
}

sealed interface PanelSize : StarUISizing {
    companion object {
        fun exact(width: Float, height: Float): PanelSize = ExactImpl(width, height)
        fun flex(weight: Float = 1f, crossAxis: CrossAxis = CrossAxis.fill()): PanelSize = FlexImpl(weight, crossAxis as CrossAxisDirective)
        fun fillCross(primarySize: Float): PanelSize = FillCrossImpl(primarySize)
    }
}

sealed interface StackSize : StarUISizing {
    companion object {
        fun exact(width: Float, height: Float): StackSize = ExactImpl(width, height)
        fun wrap(): StackSize = WrapStackImpl
        fun flex(weight: Float = 1f, crossAxis: CrossAxis = CrossAxis.fill()): StackSize = FlexImpl(weight, crossAxis as CrossAxisDirective)
        fun fillCross(primarySize: Float): StackSize = FillCrossImpl(primarySize)
    }
}

internal data object CrossWrapImpl : CrossAxis, ImageCrossAxis, CrossAxisDirective
internal data object CrossFillImpl : CrossAxis, ImageCrossAxis, CrossAxisDirective
internal data class CrossExactImpl(val size: Float) : CrossAxis, ImageCrossAxis, CrossAxisDirective
internal data object CrossAspectRatioImpl : ImageCrossAxis, CrossAxisDirective

internal data class ExactImpl(val width: Float, val height: Float) : ContentSize, ImageSize, PanelSize, StackSize
internal data class FlexImpl(val weight: Float, val crossAxis: CrossAxisDirective) : ContentSize, ImageSize, PanelSize, StackSize

internal data class FillCrossImpl(val primarySize: Float) : ContentSize, PanelSize, StackSize
internal data class WrapLabelImpl(val maxWidth: Float?) : LabelSize
internal data class WrapContentImpl(val xPad: Float, val yPad: Float) : ContentSize
internal data object WrapStackImpl : StackSize
internal data class NativeImageImpl(val scale: Float) : ImageSize
internal data class ScaledWidthImpl(val width: Float) : ImageSize
internal data class ScaledHeightImpl(val height: Float) : ImageSize