package org.starficz.starui.components

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.Axis
import org.starficz.starui.BoundedSize
import org.starficz.starui.layout.BoxLayoutEngine
import org.starficz.starui.layout.NodeSize
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.parentPluginOf
import org.starficz.starui.runtime.Scope
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.StarUIDsl
import org.starficz.starui.vanilla.BoxedUIHolo
import org.starficz.starui.vanilla.HoloBackground
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import org.starficz.starui.vanilla.VanillaClasses

/**
 * Vanilla's holo panel around [builder] content: the framed window that vanilla's dialogs and title screen menu sit
 * in, with its `ui_border1` frame, dark scanline background and static, all drawn by vanilla.
 *
 * [Holo] animates the appearance and disappearance of its content, as the [open] value changes, the way vanilla's
 * dialogs do: the frame grows from or shrinks to a line through its middle over [foldDuration] seconds, with a burst
 * of static, and the content shows only in the last quarter of the fold. It takes input once the holo is three
 * quarters open. Keep calling `Holo(open = false)` to close it with the animation; leaving the call out removes it
 * at once.
 *
 * [contentKey] switches between two contents with vanilla's static transition, as its tabbed holo switches tabs.
 * Every time the key changes the transition is triggered: a 0.2 second burst of static, with the content built for
 * the new key taking over at its peak. The old content is removed with its state, as any removed component is.
 *
 * The content is a [Box] [contentInset] in from the holo's edge on every side. The holo is a vanilla panel laid out
 * at the size it is given, not around its content, so its size is exact or fill.
 *
 * Example usage:
 *
 * ```kotlin
 * val showDialog = mutableStateOf(true)
 * Holo(Size.exact(400f, 300f), open = showDialog.value) {
 *     Text("Hello", modifier = Modifier.align(Alignment.Center))
 *     Button("Close", modifier = Modifier.align(Alignment.BottomRight)) { showDialog.value = false }
 * }
 * ```
 *
 * @param size the size of the [Holo]
 * @param modifier the [Modifier] to be applied to this holo
 * @param open whether the holo is open. A change folds it open or shut
 * @param background the holo's dark background: [HoloBackground.Dialog] (opaque, as most of vanilla's dialogs),
 *   [HoloBackground.TitleScreen] (the see-through title menu), or any pair of alphas
 * @param contentKey a key representing the content. Every time it changes, the content is built again behind the
 *   static transition
 * @param foldDuration how long folding open or shut takes, in seconds
 * @param animateFirstOpen whether the holo folds open when it is first made. Otherwise it starts open
 * @param contentInset the space between the holo's edge and the content, on every side. Vanilla's dialogs use 7 and
 *   the title menu 3; the frame itself is 8 deep
 * @param builder the content of the holo, a [Box]. It runs again when a [UIState] it read changes
 * @return the vanilla holo, boxed: open, close, the fold and the static
 * @see BoxedUIHolo
 * @see HoloBackground
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
    val holo = boxed.vanillaComponent

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

    fun sizeContent(w: Float, h: Float) {
        // The holo's own setSize also resizes its content and rebuilds its backgrounds at the new size, the noise
        // included: a fresh noise layer at rest. Called on every layout it wiped each burst of static as it started
        // (and a content swap waits for the static to peak), so only on a real size change.
        val p = holo.position
        if (p.width != w || p.height != h) holo.invoke("setSize", w, h)
        val inset = boxed.contentInset
        val contentPlugin = boxed.content.plugin as StarUIPanelPlugin
        val node = NodeSize(Axis.Exact(maxOf(0f, w - 2 * inset)), Axis.Exact(maxOf(0f, h - 2 * inset)))
        if (contentPlugin.node.size != node) {
            contentPlugin.node.size = node
            contentPlugin.requestLayout()
        }
    }
    // not sized here: the holo's own setSize rebuilds its backgrounds (see sizeContent)
    finishLeaf(slot, holo, size, modifier, handle = boxed, startSize = false,
        measure = { maxW, maxH -> Pair(if (maxW == Float.MAX_VALUE) initW else maxW, if (maxH == Float.MAX_VALUE) initH else maxH) },
        layout = { w, h -> sizeContent(w, h) }
    )
    if (parentPluginOf(holo)?.layoutEngine == null) sizeContent(initW, initH)

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
    plugin.node.size = NodeSize(Axis.Exact(content.position.width), Axis.Exact(content.position.height))
    return content
}

/** New content through vanilla's static transition; the old content's StarUI state is disposed. */
private fun swapContent(boxed: BoxedUIHolo) {
    val old = boxed.content
    val fresh = newContent(old.position.width, old.position.height)
    val owned = StarUIPanelPlugin.getOrCreate(boxed.vanillaComponent).ownedOutsideTree
    owned.remove(old)
    owned += fresh
    // the holo keeps drawing the old content, fading under the static, until the burst peaks and it switches
    boxed.transitionsEnabled = true
    boxed.vanillaComponent.invoke("setNext", fresh)
    boxed.content = fresh
    Scope.disposeTree(old)
}

private fun UIPanelAPI.createHolo(
    width: Float, height: Float, inset: Float, open: Boolean, animate: Boolean, duration: Float
): BoxedUIHolo {
    val constructor = VanillaClasses.holoConstructor ?: throw IllegalStateException(
        "StarUI: Holo needs vanilla's holo panel, which was not found at application load (see the starsector.log " +
            "warning from VanillaClasses). A game update may have changed it.")
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
