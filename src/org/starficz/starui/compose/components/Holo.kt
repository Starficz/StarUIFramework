package org.starficz.starui.compose.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.BoundedSize
import org.starficz.starui.compose.StarUIDsl
import org.starficz.starui.compose.layout.BoxLayoutEngine
import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.layout.NodeSize
import org.starficz.starui.compose.modifier.Modifier
import org.starficz.starui.compose.runtime.Scope
import org.starficz.starui.compose.runtime.StarUIContext
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.BoxedUIHolo
import org.starficz.starui.core.HoloBackground
import org.starficz.starui.core.ReflectionUtils.invoke

/**
 * Vanilla's holo panel around [builder]'s components: the framed window vanilla's dialogs and the title screen's
 * menu sit in, with its `ui_border1` frame, dark scanline background and static, all vanilla's own drawing.
 *
 * [open] folds it open and shut the way vanilla's dialogs do (0.25 s, [foldDuration], with a burst of static): the
 * frame grows from or shrinks to a line through its middle and the content shows only in the last quarter of the
 * fold, taking input once the holo is three quarters open. Keep calling `Holo(open = false)` to close it with the
 * animation; leaving the call out removes it at once. [animateFirstOpen] folds it open when it is first made
 * (otherwise it starts open).
 *
 * [background]: [HoloBackground.Dialog] (opaque, most of vanilla's dialogs) or [HoloBackground.TitleScreen] (the
 * see-through title menu), or any pair of alphas.
 *
 * [contentKey]: when it changes, the holo swaps to freshly built content through vanilla's static transition (a
 * 0.2 s burst, the new content taking over at its peak), the way vanilla's tabbed holo switches tabs. The old
 * content is thrown away with its state, as a removed component is.
 *
 * The content is a Box [contentInset] in from the holo's edge on every side (vanilla's dialogs use 7, the title
 * menu 3; the frame itself is 8 deep). Its size is exact or fill: the holo is a vanilla panel laid out at the size it
 * gets, not around its content. Returns a [BoxedUIHolo] (open, close, the fold, flickerNoise, ...).
 */
fun UIPanelAPI.Holo(
    size: BoundedSize,
    modifier: Modifier = Modifier,
    open: Boolean = true,
    background: HoloBackground = HoloBackground.Dialog,
    contentKey: Any? = null,
    foldDuration: Float = 0.25f,
    animateFirstOpen: Boolean = true,
    contentInset: Float = 7f,
    builder: (@StarUIDsl CustomPanelAPI).() -> Unit = {}
): BoxedUIHolo {
    checkDslTarget()
    val slot = childSlot("Holo", contentInset)
    val (initW, initH) = getInitialBounds(size, Pair(200f, 200f))
    val reused = slot.reused<BoxedUIHolo>()
    val boxed = reused ?: createHolo(initW, initH, contentInset, open, animateFirstOpen, foldDuration)
    val holo = boxed.holo

    if (boxed.lastBackground != background) {
        boxed.setBackgroundAlpha(background.lines, background.offsetLines)
        boxed.lastBackground = background
    }
    // open or close when the argument changes (not on every rebuild)
    val wasOpen = boxed.lastOpenArgument
    if (wasOpen != null && wasOpen != open) { if (open) boxed.open(foldDuration) else boxed.close(foldDuration) }
    boxed.lastOpenArgument = open
    if (reused != null && boxed.lastContentKey != contentKey) swapContent(boxed)
    boxed.lastContentKey = contentKey

    registerComponentSizing(holo, size, isContainer = false)
    slot.remember(holo, boxed)

    fun sizeContent(w: Float, h: Float) {
        // the holo's own setSize also resizes its content and rebuilds its backgrounds at the new size
        holo.invoke("setSize", w, h)
        val inset = boxed.contentInset
        val contentPlugin = boxed.content.plugin as StarUIPanelPlugin
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
    val contentPlugin = boxed.content.plugin as StarUIPanelPlugin
    contentPlugin.attachScope("Holo", builder) { boxed.content.builder() }
    return boxed
}

/** A StarUI Box for a holo's content: a layout root of its own (the holo, not StarUI's tree, holds it). */
private fun newContent(width: Float, height: Float): CustomPanelAPI {
    val plugin = StarUIPanelPlugin()
    val content = Global.getSettings().createCustom(maxOf(1f, width), maxOf(1f, height), plugin)
    plugin.customPanel = content
    plugin.layoutEngine = BoxLayoutEngine(plugin)
    plugin.isTopLevelNode = true
    plugin.myNodeSize = NodeSize(Dimension.Exact(content.position.width), Dimension.Exact(content.position.height))
    return content
}

/** New content through vanilla's static transition; the old content's StarUI state is disposed. */
private fun swapContent(boxed: BoxedUIHolo) {
    val old = boxed.content
    val fresh = newContent(old.position.width, old.position.height)
    val owned = StarUIPanelPlugin.getOrCreate(boxed.holo).ownedOutsideTree
    owned.remove(old)
    owned += fresh
    // the holo keeps drawing the old content, fading under the static, until the burst peaks and it switches
    boxed.transitionsEnabled = true
    boxed.holo.invoke("setNext", fresh)
    boxed.content = fresh
    Scope.disposeTree(old)
}

private fun UIPanelAPI.createHolo(
    width: Float, height: Float, inset: Float, open: Boolean, animate: Boolean, duration: Float
): BoxedUIHolo {
    val constructor = BoxedUIHolo.CONSTRUCTOR ?: throw IllegalStateException(
        "StarUI: Holo needs vanilla's holo panel, which was not found at application load (see the starsector.log " +
            "warning from StarUIManager). A game update may have changed it.")
    val holo = constructor.newInstance(width, height, "ui_border1", inset) as UIComponentAPI
    val content = newContent(width - 2 * inset, height - 2 * inset)

    this.addComponent(holo)
    val boxed = BoxedUIHolo(holo, content)
    // the first content shows at once, as in vanilla's dialogs; the static transition is for later swaps
    boxed.transitionsEnabled = false
    holo.invoke("setNext", content)
    StarUIPanelPlugin.getOrCreate(holo).ownedOutsideTree += content
    when {
        !open -> boxed.forceClose()
        animate -> { boxed.forceClose(); boxed.open(duration) }
        else -> boxed.forceOpen()
    }
    return boxed
}
