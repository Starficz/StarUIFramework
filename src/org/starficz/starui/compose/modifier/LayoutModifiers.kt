package org.starficz.starui.compose.modifier

import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.compose.Alignment
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.compose.runtime.nodeFor
import org.starficz.starui.compose.runtime.nodeOf
import org.starficz.starui.compose.runtime.parentPluginOf
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.applyAnchor
import org.starficz.starui.core.getChildrenNonCopy

// ==========================================
// SPACING MODIFIERS
// ==========================================
// Two kinds of space, each a modifier with the same three shapes:
//   margin   outside the component: between it and its neighbours or its container's edge
//   padding  inside it: between a container's edge and its children, or around a leaf's content
// Shapes: (all), (horizontal, vertical), or sides by name: (left = , top = , right = , bottom = ), where
// horizontal = and vertical = set two sides at once. Sides are named only, so four bare numbers are never guessed at.

/** Makes the parameters after it named-only: nothing can be passed for it. */
class NamedArgumentsOnly private constructor()


data class PaddingValues(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    val horizontal get() = left + right
    val vertical get() = top + bottom

    operator fun plus(other: PaddingValues) =
        PaddingValues(left + other.left, top + other.top, right + other.right, bottom + other.bottom)

    companion object {
        val Zero = PaddingValues()
        fun all(all: Float) = PaddingValues(all, all, all, all)
        fun symmetric(horizontal: Float = 0f, vertical: Float = 0f) = PaddingValues(horizontal, vertical, horizontal, vertical)
    }
}

/** Space outside the component on every side. */
fun Modifier.margin(all: Float) = margin(left = all, top = all, right = all, bottom = all)
/** Space outside the component: [horizontal] on the left and right, [vertical] on the top and bottom. */
fun Modifier.margin(horizontal: Float, vertical: Float) = margin(left = horizontal, top = vertical, right = horizontal, bottom = vertical)
/** Space outside the component, by side. */
fun Modifier.margin(
    vararg namedArgumentsOnly: NamedArgumentsOnly,
    horizontal: Float = 0f, vertical: Float = 0f,
    left: Float = horizontal, top: Float = vertical, right: Float = horizontal, bottom: Float = vertical
) = this.then { component ->
    nodeFor(component.actual).margin = PaddingValues(left = left, top = top, right = right, bottom = bottom)
}

/** Space inside the component on every side. */
fun Modifier.padding(all: Float) = padding(PaddingValues.all(all))
/** Space inside the component: [horizontal] on the left and right, [vertical] on the top and bottom. */
fun Modifier.padding(horizontal: Float, vertical: Float) = padding(PaddingValues.symmetric(horizontal, vertical))
/** Space inside the component, by side. */
fun Modifier.padding(
    vararg namedArgumentsOnly: NamedArgumentsOnly,
    horizontal: Float = 0f, vertical: Float = 0f,
    left: Float = horizontal, top: Float = vertical, right: Float = horizontal, bottom: Float = vertical
) = padding(PaddingValues(left, top, right, bottom))

/** Space inside the component: between a container's edge and its children, or around a leaf's content. Adds up. */
fun Modifier.padding(values: PaddingValues) = this.then { component -> component.addPadding(values) }

/** Adds [values] to the component's padding (a container's between its edge and its children). */
internal fun UIComponentAPI.addPadding(values: PaddingValues) {
    if (values == PaddingValues.Zero) return
    val actual = this.actual
    // a scrolling container's padding is inside the StarUI container it holds (it scrolls with the content)
    val node = (actual as? ScrollPanelAPI)?.let { StarUIPanelPlugin.getAttached(it)?.scrollContent?.node } ?: nodeFor(actual)
    node.padding = node.padding + values
}

// ==========================================
// POSITIONING MODIFIERS
// ==========================================

/** Where this child sits in a Box: one of the nine [Alignment] values. */
fun Modifier.align(alignment: Alignment) = this.then { component ->
    nodeFor(component.actual).alignment = alignment
}

/**
 * Places the component relative to its parent or a sibling (see [Anchor]). Only inside a `CustomPanel` (or a panel
 * StarUI does not lay out): a Column, Row, Box or list places its children itself, so an anchor there is an error.
 */
fun Modifier.anchor(anchor: Anchor.AnchorData) = this.then { component ->
    val actual = component.actual
    val parentPlugin = parentPluginOf(actual)
    if (parentPlugin?.layoutEngine != null) {
        throw IllegalStateException(
            "StarUI: Modifier.anchor() is only for children of a CustomPanel. This component's container " +
            "(${parentPlugin.kind ?: "a StarUI layout"}) places its children itself, so the anchor would be overwritten. " +
            "Use Modifier.align in a Box, the container's alignment and spacing in a Column or Row, or margin."
        )
    }

    val targetComponent = when (anchor.reference) {
        is Anchor.AnchorReference.Parent -> actual.parent
        is Anchor.AnchorReference.Previous -> {
            actual.parent?.getChildrenNonCopy()?.let { it.getOrNull(it.size - 2) }
                ?: throw IllegalStateException("StarUI: Anchor ...previousComponent: there is no component before this one")
        }
        is Anchor.AnchorReference.Sibling -> anchor.reference.component.actual
    }

    // margins: this component's push it away from the edge it is anchored by, the target's push it further off
    var offsetX = anchor.offsetX
    var offsetY = anchor.offsetY
    if (anchor.reference !is Anchor.AnchorReference.Parent && targetComponent != null) {
        nodeOf(targetComponent.actual)?.margin?.let { targetMargin ->
            if (anchor.refAnchorX == 0f && anchor.targetAnchorX == 1f) offsetX += targetMargin.right
            if (anchor.refAnchorX == 1f && anchor.targetAnchorX == 0f) offsetX -= targetMargin.left
            if (anchor.refAnchorY == 0f && anchor.targetAnchorY == 1f) offsetY += targetMargin.top
            if (anchor.refAnchorY == 1f && anchor.targetAnchorY == 0f) offsetY -= targetMargin.bottom
        }
    }
    nodeOf(actual)?.margin?.let { myMargin ->
        if (anchor.refAnchorX == 0f) offsetX += myMargin.left
        if (anchor.refAnchorX == 1f) offsetX -= myMargin.right
        if (anchor.refAnchorY == 0f) offsetY += myMargin.bottom
        if (anchor.refAnchorY == 1f) offsetY -= myMargin.top
    }

    val bakedAnchor = anchor.copy(offsetX = offsetX, offsetY = offsetY)
    nodeFor(actual).anchor = bakedAnchor
    actual.applyAnchor(bakedAnchor)
}
