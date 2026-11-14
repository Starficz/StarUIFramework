package org.starficz.starui.modifier

import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.Alignment
import org.starficz.starui.Anchor
import org.starficz.starui.runtime.nodeFor
import org.starficz.starui.runtime.nodeOf
import org.starficz.starui.runtime.parentPluginOf
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.applyAnchor
import org.starficz.starui.vanilla.getChildrenNonCopy

// ==========================================
// SPACING MODIFIERS
// ==========================================
// Two kinds of space, each a modifier with the same three shapes:
//   margin   outside the component: between it and its neighbours or its container's edge
//   padding  inside it: between a container's edge and its children, or around a leaf's content
// Shapes: (all), (horizontal, vertical), or sides by name: (left = , top = , right = , bottom = ), where
// horizontal = and vertical = set two sides at once. Sides are named only, so four bare numbers are never guessed at.

/**
 * Makes the parameters after it named-only: it has no instances, so nothing can be passed for it. The side-by-side
 * overloads of [padding] and [margin] use it, so four bare numbers are never mistaken for one another.
 */
class NamedArgumentsOnly private constructor()


/**
 * Describes a padding to be applied along the edges inside a box: [left], [top], [right] and [bottom], in pixels.
 * See the [PaddingValues] factories for convenient ways to build one.
 *
 * Starsector has no right-to-left layouts, so the sides are absolute: left is always left.
 *
 * @property left the padding to be applied along the left edge inside a box
 * @property top the padding to be applied along the top edge inside a box
 * @property right the padding to be applied along the right edge inside a box
 * @property bottom the padding to be applied along the bottom edge inside a box
 */
data class PaddingValues(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    /** The padding along the left and right edges together. */
    val horizontal get() = left + right
    /** The padding along the top and bottom edges together. */
    val vertical get() = top + bottom

    /** Adds two [PaddingValues] together. */
    operator fun plus(other: PaddingValues) =
        PaddingValues(left + other.left, top + other.top, right + other.right, bottom + other.bottom)

    companion object {
        /** [PaddingValues] with all values 0. */
        val Zero = PaddingValues()
        /** Creates a padding of [all] pixels along all 4 edges. */
        fun all(all: Float) = PaddingValues(all, all, all, all)
        /**
         * Creates a padding of [horizontal] pixels along the left and right edges, and of [vertical] pixels along the
         * top and bottom edges.
         */
        fun symmetric(horizontal: Float = 0f, vertical: Float = 0f) = PaddingValues(horizontal, vertical, horizontal, vertical)
    }
}

/**
 * Apply [all] pixels of space outside the component, along each edge: between it and its neighbours, or its
 * container's edge. A Column, Row or Box keeps the margin clear when it places the component, and an anchored
 * component is pushed away from what it is anchored to.
 *
 * Padding is space inside a component; margin is space outside it. Compose has only padding.
 *
 * @see padding
 */
fun Modifier.margin(all: Float) = margin(left = all, top = all, right = all, bottom = all)
/**
 * Apply [horizontal] pixels of space outside the component along the left and right edges, and [vertical] pixels
 * along the top and bottom edges. See the other [margin] overloads.
 */
fun Modifier.margin(horizontal: Float, vertical: Float) = margin(left = horizontal, top = vertical, right = horizontal, bottom = vertical)
/**
 * Apply space outside the component along each edge, by name: [left], [top], [right] and [bottom], with
 * [horizontal] and [vertical] setting two sides at once. See the other [margin] overloads.
 *
 * ```kotlin
 * Modifier.margin(top = 10f)
 * Modifier.margin(horizontal = 5f, bottom = 8f)
 * ```
 */
fun Modifier.margin(
    vararg namedArgumentsOnly: NamedArgumentsOnly,
    horizontal: Float = 0f, vertical: Float = 0f,
    left: Float = horizontal, top: Float = vertical, right: Float = horizontal, bottom: Float = vertical
) = this.then { component ->
    nodeFor(component.actual).margin = PaddingValues(left = left, top = top, right = right, bottom = bottom)
}

/**
 * Apply [all] pixels of additional space along each edge of the content, left, top, right and bottom: between a
 * container's edge and its children, or around a leaf's content. Padding is applied before content measurement and
 * takes precedence; content may only be as large as the remaining space.
 *
 * Padding modifiers add up, and add to a control's own `contentPadding`.
 *
 * @see margin
 */
fun Modifier.padding(all: Float) = padding(PaddingValues.all(all))
/**
 * Apply [horizontal] pixels of space along the left and right edges of the content, and [vertical] pixels along the
 * top and bottom edges. Padding is applied before content measurement and takes precedence; content may only be as
 * large as the remaining space.
 */
fun Modifier.padding(horizontal: Float, vertical: Float) = padding(PaddingValues.symmetric(horizontal, vertical))
/**
 * Apply additional space along each edge of the content, by name: [left], [top], [right] and [bottom], with
 * [horizontal] and [vertical] setting two sides at once. Padding is applied before content measurement and takes
 * precedence; content may only be as large as the remaining space.
 *
 * ```kotlin
 * Modifier.padding(top = 10f)
 * Modifier.padding(horizontal = 5f, bottom = 8f)
 * ```
 */
fun Modifier.padding(
    vararg namedArgumentsOnly: NamedArgumentsOnly,
    horizontal: Float = 0f, vertical: Float = 0f,
    left: Float = horizontal, top: Float = vertical, right: Float = horizontal, bottom: Float = vertical
) = padding(PaddingValues(left, top, right, bottom))

/**
 * Apply [PaddingValues] to the component as additional space along each edge of the content's left, top, right and
 * bottom. Padding is applied before content measurement and takes precedence; content may only be as large as the
 * remaining space. Padding modifiers add up.
 */
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

/**
 * Pull the component to a specific [Alignment] within the [Box][org.starficz.starui.components.Box] it is in. Only
 * a [Box][org.starficz.starui.components.Box]'s children use it; a Column or Row places its children by its own
 * alignment argument.
 *
 * @param alignment one of the nine positions, such as [Alignment.TopLeft] or [Alignment.Center]
 */
fun Modifier.align(alignment: Alignment) = this.then { component ->
    nodeFor(component.actual).alignment = alignment
}

/**
 * Places the component relative to its parent or to a sibling, as described by [anchor]. Only for children of a
 * [CustomPanel][org.starficz.starui.components.CustomPanel] or of a panel StarUI does not lay out: a Column, Row, Box
 * or lazy list places its children itself, so an anchor there throws.
 *
 * Margins are kept: the component's own margin moves it away from the edge it is anchored by, and a sibling's
 * margin moves it further from that sibling.
 *
 * ```kotlin
 * Text("Title", modifier = Modifier.anchor(Anchor.inside.topLeft.ofParent(10f, 10f)))
 * ```
 *
 * @param anchor where the component goes. See [Anchor]
 * @throws IllegalStateException if the component's container places its children itself
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
