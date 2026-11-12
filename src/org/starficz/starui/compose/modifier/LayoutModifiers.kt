package org.starficz.starui.compose.modifier

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.Alignment
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.applyAnchor
import org.starficz.starui.core.children

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

data class Margin(val left: Float = 0f, val right: Float = 0f, val top: Float = 0f, val bottom: Float = 0f) {
    val horizontal get() = left + right
    val vertical get() = top + bottom
}

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
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMargins[component.actual] = Margin(left = left, right = right, top = top, bottom = bottom)
    }
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

/** Adds [values] to the padding of a container (its own) or of a leaf (kept by its container). */
internal fun UIComponentAPI.addPadding(values: PaddingValues) {
    if (values == PaddingValues.Zero) return
    val actualComponent = this.actual
    val pluginAsContainer = ((actualComponent as? CustomPanelAPI)?.plugin as? StarUIPanelPlugin)
        ?.takeIf { it.layoutEngine != null }
    // a scrolling container's padding is inside the StarUI container it holds (it scrolls with the content)
    val scrollContainerPlugin = (actualComponent as? ScrollPanelAPI)
        ?.let { StarUIPanelPlugin.getAttached(it)?.scrollContent }
    val containerPlugin = pluginAsContainer ?: scrollContainerPlugin

    if (containerPlugin != null) {
        containerPlugin.myPadding = containerPlugin.myPadding + values
    } else if (!StarUIContext.isTopLevel) {
        val plugin = StarUIContext.currentPlugin
        plugin.childPadding[actualComponent] = (plugin.childPadding[actualComponent] ?: PaddingValues.Zero) + values
    }
}

// ==========================================
// POSITIONING MODIFIERS
// ==========================================

/** Where this child sits in a Box: one of the nine [Alignment] values. */
fun Modifier.align(alignment: Alignment) = this.then { component ->
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childAlignments[component.actual] = alignment
    }
}

fun Modifier.anchor(anchor: Anchor.AnchorData) = this.then { component ->
    if (!StarUIContext.isTopLevel && StarUIContext.currentPlugin.isStackLayout) {
        throw IllegalStateException(
            "Modifier.anchor() cannot be used inside a StackLayout! " +
            "StackLayouts automatically position their children based on flow. Use Margin/Padding/Sizing instead."
        )
    }

    val targetComponent = when (anchor.reference) {
        is Anchor.AnchorReference.Parent -> component.actual.parent
        is Anchor.AnchorReference.Previous -> {
            component.actual.parent?.children?.dropLast(1)?.lastOrNull()
                ?: throw Error("Previous Component does not exist!")
        }
        is Anchor.AnchorReference.Sibling -> anchor.reference.component.actual
    }

    var targetPadX = 0f
    var targetPadY = 0f
    var myPadX = 0f
    var myPadY = 0f

    if (!StarUIContext.isTopLevel) {
        val parentPlugin = StarUIContext.currentPlugin
        parentPlugin.childAnchors[component.actual] = anchor

        targetComponent?.let { parentPlugin.childMargins[it.actual] }?.let { targetMargin ->
            if (anchor.reference !is Anchor.AnchorReference.Parent) {
                if (anchor.refAnchorX == 0f && anchor.targetAnchorX == 1f) targetPadX += targetMargin.right
                if (anchor.refAnchorX == 1f && anchor.targetAnchorX == 0f) targetPadX -= targetMargin.left
                if (anchor.refAnchorY == 0f && anchor.targetAnchorY == 1f) targetPadY += targetMargin.top
                if (anchor.refAnchorY == 1f && anchor.targetAnchorY == 0f) targetPadY -= targetMargin.bottom
            }
        }

        parentPlugin.childMargins[component.actual]?.let { myMargin ->
            if (anchor.refAnchorX == 0f) myPadX += myMargin.left
            if (anchor.refAnchorX == 1f) myPadX -= myMargin.right
            if (anchor.refAnchorY == 0f) myPadY += myMargin.bottom
            if (anchor.refAnchorY == 1f) myPadY -= myMargin.top
        }
    }

    val bakedAnchor = anchor.copy(
        offsetX = anchor.offsetX + myPadX + targetPadX,
        offsetY = anchor.offsetY + myPadY + targetPadY
    )
    StarUIPanelPlugin.getAttached(component.actual)?.myAnchor = bakedAnchor
    component.actual.applyAnchor(bakedAnchor)
}
