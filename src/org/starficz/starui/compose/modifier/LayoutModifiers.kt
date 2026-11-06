package org.starficz.starui.compose.modifier

import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.layout.NodeSize
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.applyAnchor
import org.starficz.starui.core.children
import org.starficz.starui.core.parent

private fun UIComponentAPI.applySizeConfig(action: (NodeSize) -> NodeSize) {
    val actualComponent = this.actual
    val attached = StarUIPanelPlugin.getAttached(actualComponent)
    if (attached != null) {
        attached.myNodeSize = action(attached.myNodeSize)
    }
    if (StarUIContext.isManagedByParent) {
        val plugin = StarUIContext.currentPlugin
        val currentSize = plugin.childSizing[actualComponent] ?: attached?.myNodeSize ?: NodeSize()
        plugin.childSizing[actualComponent] = action(currentSize)
    } else if (attached == null) {
        val plugin = StarUIPanelPlugin.getOrCreate(actualComponent)
        plugin.myNodeSize = action(plugin.myNodeSize)
    }
}

// ==========================================
// SIZING MODIFIERS
// ==========================================

fun Modifier.width(pixels: Float) = this.then { it.applySizeConfig { s -> s.copy(width = Dimension.Exact(pixels)) } }
fun Modifier.height(pixels: Float) = this.then { it.applySizeConfig { s -> s.copy(height = Dimension.Exact(pixels)) } }
fun Modifier.size(width: Float, height: Float) = this.width(width).height(height)
fun Modifier.size(exact: Float) = this.size(exact, exact)

fun Modifier.fillMaxWidth(weight: Float = 1f) = this.then { it.applySizeConfig { s -> s.copy(width = Dimension.FillMax(weight)) } }
fun Modifier.fillMaxHeight(weight: Float = 1f) = this.then { it.applySizeConfig { s -> s.copy(height = Dimension.FillMax(weight)) } }
fun Modifier.fillMaxSize(weight: Float = 1f) = this.fillMaxWidth(weight).fillMaxHeight(weight)

fun Modifier.wrapContentWidth() = this.then { it.applySizeConfig { s -> s.copy(width = Dimension.WrapContent) } }
fun Modifier.wrapContentHeight() = this.then { it.applySizeConfig { s -> s.copy(height = Dimension.WrapContent) } }
fun Modifier.wrapContentSize() = this.wrapContentWidth().wrapContentHeight()

// ==========================================
// SPACING MODIFIERS
// ==========================================

data class Margin(val left: Float = 0f, val right: Float = 0f, val top: Float = 0f, val bottom: Float = 0f) {
    val horizontal get() = left + right
    val vertical get() = top + bottom
}

fun Modifier.margin(all: Float) = margin(all, all, all, all)
fun Modifier.margin(horizontal: Float = 0f, vertical: Float = 0f) = margin(horizontal, horizontal, vertical, vertical)
fun Modifier.margin(left: Float = 0f, right: Float = 0f, top: Float = 0f, bottom: Float = 0f) = this.then { component ->
    if (!StarUIContext.isTopLevel) {
        StarUIContext.currentPlugin.childMargins[component.actual] = Margin(left, right, top, bottom)
    }
}

data class PaddingValues(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f) {
    val horizontal get() = left + right
    val vertical get() = top + bottom
}

fun Modifier.padding(all: Float) = padding(all, all, all, all)
fun Modifier.padding(horizontal: Float = 0f, vertical: Float = 0f) = padding(horizontal, vertical, horizontal, vertical)
fun Modifier.padding(left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f) = this.then { component ->
    val actualComponent = component.actual
    val pluginAsContainer = ((actualComponent as? CustomPanelAPI)?.plugin as? StarUIPanelPlugin)
        ?.takeIf { it.layoutEngine != null }
    val scrollContainerPlugin = (actualComponent as? ScrollPanelAPI)?.let {
        val contentContainer = it.invoke("getContentContainer") as? UIPanelAPI
        contentContainer?.let { cc -> StarUIPanelPlugin.getAttached(cc) }
    }
    val containerPlugin = pluginAsContainer ?: scrollContainerPlugin

    // Padding is space INSIDE.
    if (containerPlugin != null) {
        val existing = containerPlugin.myPadding
        containerPlugin.myPadding = PaddingValues(
            left = existing.left + left, top = existing.top + top, right = existing.right + right, bottom = existing.bottom + bottom
        )
    } else if (!StarUIContext.isTopLevel) {
        val plugin = StarUIContext.currentPlugin
        val existing = plugin.childPadding[actualComponent] ?: PaddingValues()
        plugin.childPadding[actualComponent] = PaddingValues(
            left = existing.left + left, top = existing.top + top, right = existing.right + right, bottom = existing.bottom + bottom
        )
    }
}

// ==========================================
// POSITIONING MODIFIERS
// ==========================================

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
