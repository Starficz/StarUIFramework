package org.starficz.starui.compose.layout

internal sealed interface Dimension {
    data object WrapContent : Dimension
    data class FillMax(val weight: Float = 1f) : Dimension
    data class Exact(val pixels: Float) : Dimension
    data object AspectRatio : Dimension
}

internal data class NodeSize(
    val width: Dimension = Dimension.WrapContent,
    val height: Dimension = Dimension.WrapContent
)
