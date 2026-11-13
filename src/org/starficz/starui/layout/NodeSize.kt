package org.starficz.starui.layout

import org.starficz.starui.Axis

/** A node's size rule: how each axis is sized (its component call's `size`). */
internal data class NodeSize(
    val width: Axis = Axis.WrapContent,
    val height: Axis = Axis.WrapContent
)
