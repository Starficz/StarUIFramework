package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.BoundedSize
import org.starficz.starui.compose.StarUIDsl
import org.starficz.starui.compose.layout.BoxLayoutEngine
import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.layout.NodeSize
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.BoxedUIHolo
import org.starficz.starui.core.ReflectionUtils.invoke

/**
 * Vanilla's holo panel around [builder]'s components: the framed window vanilla's dialogs and the title screen's
 * menu sit in, with its `ui_border1` frame, dark scanline background, teal tint and noise, all vanilla's own drawing.
 *
 * [open] folds it open and shut the way vanilla does (0.25 s, [foldDuration]): the frame grows from or shrinks to a
 * line through its middle and the content shows only in the last quarter of the fold, taking input once the holo is
 * three quarters open. Keep calling `Holo(open = false)` to close it with the animation; leaving the call out removes
 * it at once. [animateFirstOpen] folds it open when it is first made (otherwise it starts open).
 *
 * The content is a Box [contentInset] in from the holo's edge on every side (vanilla's dialogs use 7, the title
 * menu 3; the frame itself is 8 deep). Its size is exact or fill: the holo is a vanilla panel laid out at the size it
 * gets, not around its content. Returns a [BoxedUIHolo] (the holo's own methods: open, close, the fold, noise).
 */
fun UIPanelAPI.Holo(
    size: BoundedSize,
    modifier: Modifier = Modifier,
    open: Boolean = true,
    foldDuration: Float = 0.25f,
    animateFirstOpen: Boolean = true,
    contentInset: Float = 7f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): BoxedUIHolo {
    checkDslTarget()
    val slot = childSlot("Holo", contentInset)
    val (initW, initH) = getInitialBounds(size, Pair(200f, 200f))
    val boxed = slot.reused<BoxedUIHolo>() ?: createHolo(initW, initH, contentInset, open, animateFirstOpen, foldDuration)
    val holo = boxed.holo

    // open or close when the argument changes (not on every rebuild)
    val wasOpen = boxed.lastOpenArgument
    if (wasOpen != null && wasOpen != open) { if (open) boxed.open(foldDuration) else boxed.close(foldDuration) }
    boxed.lastOpenArgument = open

    registerComponentSizing(holo, size, isContainer = false)
    slot.remember(holo, boxed)

    val contentPlugin = boxed.content.plugin as StarUIPanelPlugin
    fun sizeContent(w: Float, h: Float) {
        // the holo's own setSize also resizes its content and rebuilds its backgrounds at the new size
        holo.invoke("setSize", w, h)
        val inset = boxed.contentInset
        val node = NodeSize(Dimension.Exact(maxOf(0f, w - 2 * inset)), Dimension.Exact(maxOf(0f, h - 2 * inset)))
        if (contentPlugin.myNodeSize != node) {
            contentPlugin.myNodeSize = node
            contentPlugin.requestLayout()
        }
    }
    if (!StarUIContext.isTopLevel) {
        val parentPlugin = StarUIContext.currentPlugin
        parentPlugin.childMeasurables[holo] = { maxW, maxH ->
            Pair(if (maxW == Float.MAX_VALUE) initW else maxW, if (maxH == Float.MAX_VALUE) initH else maxH)
        }
        parentPlugin.childLayoutDelegates[holo] = { w, h -> sizeContent(w, h) }
    } else {
        sizeContent(initW, initH)
    }
    modifier.operations.forEach { it(holo) }

    // The content's builder is its own scope, attached here so a rebuild of this builder runs it again and reuses
    // the content's children. The holo draws, advances and feeds input to the content itself.
    contentPlugin.attachScope("Holo", builder) { boxed.content.builder() }
    return boxed
}

private fun UIPanelAPI.createHolo(
    width: Float, height: Float, inset: Float, open: Boolean, animate: Boolean, duration: Float
): BoxedUIHolo {
    val constructor = BoxedUIHolo.CONSTRUCTOR ?: throw IllegalStateException(
        "StarUI: Holo needs vanilla's holo panel, which was not found at application load (see the starsector.log " +
            "warning from StarUIManager). A game update may have changed it.")
    val holo = constructor.newInstance(width, height, "ui_border1", inset) as com.fs.starfarer.api.ui.UIComponentAPI

    // The content: a StarUI Box, a layout root of its own (its parent is the holo, outside StarUI's tree)
    val contentPlugin = StarUIPanelPlugin()
    val content = Global.getSettings().createCustom(maxOf(1f, width - 2 * inset), maxOf(1f, height - 2 * inset), contentPlugin)
    contentPlugin.customPanel = content
    contentPlugin.layoutEngine = BoxLayoutEngine(contentPlugin)
    contentPlugin.isTopLevelNode = true
    contentPlugin.myNodeSize = NodeSize(Dimension.Exact(content.position.width), Dimension.Exact(content.position.height))

    this.addComponent(holo)
    val boxed = BoxedUIHolo(holo, content)
    // without transitions the content shows at once; vanilla's noise transition is for swapping content later
    boxed.transitionsEnabled = false
    holo.invoke("setNext", content)
    boxed.transitionsEnabled = true
    StarUIPanelPlugin.getOrCreate(holo).ownedOutsideTree += content
    when {
        !open -> boxed.forceClose()
        animate -> { boxed.forceClose(); boxed.open(duration) }
        else -> boxed.forceOpen()
    }
    return boxed
}
