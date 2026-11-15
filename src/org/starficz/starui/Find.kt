package org.starficz.starui

import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import org.starficz.starui.modifier.actual
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.runtime.nodeOf
import org.starficz.starui.vanilla.ReflectionUtils.getMethodsMatching
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import org.starficz.starui.vanilla.fader
import org.starficz.starui.vanilla.getChildrenNonCopy
import java.util.IdentityHashMap

/**
 * One component [findAll] found: the vanilla object, what StarUI and vanilla know about it, where it is on screen and
 * whether any of it is shown.
 *
 * Positions are in screen units (UI units, before `screenScaleMult`) from the screen's top left corner, y downwards:
 * the way [dumpLayout] and layouts are written. Vanilla's `position` has y upwards from the bottom.
 */
class FoundComponent internal constructor(
    /** The component itself (the vanilla object; a box such as `BoxedUIImage` is unwrapped). */
    val component: UIComponentAPI,
    /** What made it: the StarUI call (`Button`, `Column`, `Text`), else its vanilla kind (`Button`, `Label`, `Panel`). */
    val kind: String,
    /** A label's, button's or text field's text; null for other components. */
    val text: String?,
    /**
     * The id it was built under (`key(id) { }`, a lazy list's `key`; every component a key block makes directly has
     * it), or null.
     */
    val key: Any?,
    /** What it was found under, up to the component the search started from (whose `parent` is null). */
    val parent: FoundComponent?,
    /** Its left edge. */
    val left: Float,
    /** Its top edge. */
    val top: Float,
    val width: Float,
    val height: Float,
    /**
     * Its opacity on screen: its own times every container's above it (a fading layer, a holo folding open). 0 when
     * it is not drawn.
     */
    val opacity: Float,
    private val clipLeft: Float,
    private val clipTop: Float,
    private val clipRight: Float,
    private val clipBottom: Float,
) {
    // what it hands its children: the rectangle it clips them to and its opacity (with a holo's fold)
    internal var childClipLeft = 0f
    internal var childClipTop = 0f
    internal var childClipRight = 0f
    internal var childClipBottom = 0f
    internal var childOpacity = 1f

    /** Whether StarUI made it or attached to it (a vanilla component with StarUI modifiers counts). */
    val isStarUI: Boolean get() = nodeOf(component) != null || StarUIPanelPlugin.getAttached(component) != null

    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    /**
     * Whether some of it is shown: part of it is inside the screen and every clipping or scrolling container above
     * it (a row scrolled out of view is not; a lazy list's rows far from the view do not exist at all), and it is not
     * fully transparent. It says nothing about what is drawn over it (a modal layer): a real click finds that out.
     */
    val isShown: Boolean get() = opacity > 0f && visibleRight > visibleLeft && visibleBottom > visibleTop

    /** The shown part's edges (meaningful while [isShown]). */
    val visibleLeft: Float get() = maxOf(left, clipLeft)
    val visibleTop: Float get() = maxOf(top, clipTop)
    val visibleRight: Float get() = minOf(right, clipRight)
    val visibleBottom: Float get() = minOf(bottom, clipBottom)
    /** The centre of the shown part: where a click on it lands on it. */
    val visibleCenterX: Float get() = (visibleLeft + visibleRight) / 2f
    val visibleCenterY: Float get() = (visibleTop + visibleBottom) / 2f

    /** The path from the search's start to it, as `Layer > Box > Holo > Column > Button "Confirm"`. */
    val path: String get() {
        val names = ArrayList<String>()
        var at: FoundComponent? = this
        while (at != null) { names += at.describeShort(); at = at.parent }
        return names.asReversed().joinToString(" > ")
    }

    /** Everything below it, found the same way (the search continues inside it, clips and opacity included). */
    fun findAll(predicate: (FoundComponent) -> Boolean = { true }): List<FoundComponent> {
        val out = ArrayList<FoundComponent>()
        val visited = IdentityHashMap<UIComponentAPI, Boolean>()
        visited[component] = true
        walkChildrenOf(component, StarUIPanelPlugin.getAttached(component)) { child, _ ->
            visit(child, this, predicate, out, visited, 1)
        }
        return out
    }

    /** The first of [findAll], or null. */
    fun find(predicate: (FoundComponent) -> Boolean): FoundComponent? = findAll(predicate).firstOrNull()

    private fun describeShort(): String = text?.let { "$kind \"${if (it.length > 30) it.take(27) + "..." else it}\"" } ?: kind

    override fun toString(): String = buildString {
        append(describeShort())
        if (key != null) append(" key=").append(key)
        append(' ').append(number(width)).append('x').append(number(height))
        append(" at (").append(number(left)).append(", ").append(number(top)).append(')')
        append(if (isShown) " shown" else " hidden")
    }
}

/**
 * Finds every component below this one that [predicate] accepts, in drawing order (a container before its children,
 * children in the order they draw), the way [dumpLayout] walks: through vanilla panels and scroll panels, StarUI's
 * containers, and content StarUI holds outside the tree (a Holo's, and so a Dialog's). Each result has its screen
 * position, whether it is shown (clipping, scrolling, the screen's edge, opacity) and its path.
 *
 * ```kotlin
 * val screen = Global.getSettings().screenPanel
 * val confirm = screen.find { it.kind == "Button" && it.text == "Confirm" && it.isShown }
 * val row = screen.find { it.key == ship.id }          // a keyed row ...
 * val learn = row?.find { it.text == "Learn" }          // ... and a button in it
 * screen.findAll { it.isStarUI && it.parent?.isStarUI != true }   // every StarUI panel on screen
 * ```
 *
 * It reads; it changes nothing. Positions are those of the last layout: search after a frame has rendered. A holo's
 * content, vanilla's included (the title menu, vanilla's dialogs), is found under its holo. Content vanilla keeps in
 * other fields of its own is not; nor is what a component draws itself (a ship display's ship, a map).
 */
fun UIComponentAPI.findAll(predicate: (FoundComponent) -> Boolean = { true }): List<FoundComponent> {
    val out = ArrayList<FoundComponent>()
    visit(this.actual, null, predicate, out, IdentityHashMap(), 0)
    return out
}

/** The first component [findAll] finds, or null. */
fun UIComponentAPI.find(predicate: (FoundComponent) -> Boolean): FoundComponent? = findAll(predicate).firstOrNull()

private fun visit(
    node: UIComponentAPI,
    parent: FoundComponent?,
    predicate: (FoundComponent) -> Boolean,
    out: MutableList<FoundComponent>,
    visited: IdentityHashMap<UIComponentAPI, Boolean>,
    depth: Int,
) {
    if (visited.put(node, true) != null || depth > MAX_DEPTH) return
    val found = describe(node, parent)
    if (predicate(found)) out += found
    walkChildrenOf(node, StarUIPanelPlugin.getAttached(node)) { child, _ -> visit(child, found, predicate, out, visited, depth + 1) }
}

private const val MAX_DEPTH = 256

private fun describe(node: UIComponentAPI, parent: FoundComponent?): FoundComponent {
    val plugin = StarUIPanelPlugin.getAttached(node)
    val position = node.position
    val screenHeight = Global.getSettings().screenHeight
    val left = position.x
    val top = screenHeight - (position.y + position.height)

    // what the parent clips its children to: a scroll panel always (vanilla's cuts on both axes), a StarUI container
    // on its clipping axes; the screen at the top
    var clipLeft = parent?.childClipLeft ?: 0f
    var clipTop = parent?.childClipTop ?: 0f
    var clipRight = parent?.childClipRight ?: Global.getSettings().screenWidth
    var clipBottom = parent?.childClipBottom ?: screenHeight
    val found = FoundComponent(
        component = node,
        kind = nodeOf(node)?.record?.kind ?: plugin?.kind ?: fallbackKind(node),
        text = textOf(node),
        // a key block records (its id, the position in the block): the id is what the caller gave
        key = nodeOf(node)?.record?.key?.let { if (it is Pair<*, *>) it.first else it },
        parent = parent,
        left = left, top = top, width = position.width, height = position.height,
        opacity = (parent?.childOpacity ?: 1f) * opacityOf(node),
        clipLeft = clipLeft, clipTop = clipTop, clipRight = clipRight, clipBottom = clipBottom,
    )
    val overflow = (plugin?.scrollContent ?: plugin)?.overflow
    val clipsX = node is ScrollPanelAPI || (overflow != null && overflow.horizontal != AxisOverflow.Visible)
    val clipsY = node is ScrollPanelAPI || (overflow != null && overflow.vertical != AxisOverflow.Visible)
    if (clipsX) { clipLeft = maxOf(clipLeft, left); clipRight = minOf(clipRight, left + position.width) }
    if (clipsY) { clipTop = maxOf(clipTop, top); clipBottom = minOf(clipBottom, top + position.height) }
    found.childClipLeft = clipLeft
    found.childClipTop = clipTop
    found.childClipRight = clipRight
    found.childClipBottom = clipBottom
    // content held outside the tree draws with its owner's fold: a holo's content is not drawn while it is shut
    found.childOpacity = found.opacity * (if (framesContent(node)) foldOf(node) else 1f)
    return found
}

// its own opacity times its fader's brightness (vanilla fades panels in and out with it: a slid-out panel's is 0)
private fun opacityOf(node: UIComponentAPI): Float {
    val opacity = try { node.opacity } catch (_: Throwable) { 1f }
    val fade = try { node.fader?.brightness ?: 1f } catch (_: Throwable) { 1f }
    return opacity * fade
}

private fun foldOf(node: UIComponentAPI): Float = try {
    ((node as Any).invoke("getFanOut") as? Fader)?.brightness ?: 1f
} catch (_: Throwable) { 1f }

/**
 * The children [findAll] and [dumpLayout] walk into below [node] (whose StarUI plugin is [plugin]), each with the
 * StarUI plugin of the container it is in: a panel's children; a scroll panel's content (a StarUI scrolling
 * container's children directly: its inner container is described on the scroll panel's line); content StarUI holds
 * outside the tree (a Holo's). A label's, button's or text field's own parts are engine details and not walked.
 */
internal fun walkChildrenOf(node: UIComponentAPI, plugin: StarUIPanelPlugin?, visit: (UIComponentAPI, StarUIPanelPlugin?) -> Unit) {
    when (node) {
        is LabelAPI, is TextFieldAPI -> return
        // a plain button's parts are engine details; a button drawn as a panel (vanilla's intel rows and filter chips,
        // area checkboxes) shows that panel's content, handed out by the readable getButtonPanel()
        is ButtonAPI -> {
            val content = try { (node as Any).invoke("getButtonPanel") as? UIComponentAPI } catch (_: Throwable) { null }
            if (content != null) visit(content, StarUIPanelPlugin.getAttached(content))
            return
        }
        is ScrollPanelAPI -> {
            val inner = plugin?.scrollContent
            if (inner != null) {
                for (child in inner.panel.getChildrenNonCopy().toList()) visit(child, inner)
                return
            }
            val content = node.contentContainer ?: return
            val contentPlugin = StarUIPanelPlugin.getAttached(content)
            for (child in content.getChildrenNonCopy().toList()) visit(child, contentPlugin)
        }
        is UIPanelAPI -> for (child in node.getChildrenNonCopy().toList()) visit(child, plugin)
    }
    val owned = plugin?.ownedOutsideTree?.toList().orEmpty()
    owned.forEach { visit(it, plugin) }
    // a holo's content is held outside its children (vanilla's own too: the title menu, its dialogs); a StarUI holo's
    // is the one it owns, listed above
    for (content in framedContent(node)) {
        if (owned.none { it === content }) visit(content, StarUIPanelPlugin.getAttached(content))
    }
}

/**
 * What a component frames outside its children: vanilla's holo panel takes its content with `setNext`, holds it there
 * until its transition swaps it in, then shows it as `getCurr()` (both readable). Empty for any other component.
 */
private fun framedContent(node: UIComponentAPI): List<UIComponentAPI> {
    if (!framesContent(node)) return emptyList()
    val shown = try { (node as Any).invoke("getCurr") as? UIComponentAPI } catch (_: Throwable) { null }
    val next = try { (node as Any).invoke("getNext") as? UIComponentAPI } catch (_: Throwable) { null }
    return listOfNotNull(shown, next.takeIf { it !== shown })
}

/** Whether [node]'s class has the readable `getCurr()` and `getNext()` of vanilla's holo panel (checked once per class). */
private fun framesContent(node: UIComponentAPI): Boolean = framesNext.getOrPut(node.javaClass) {
    try {
        node.javaClass.getMethodsMatching(name = "getNext", numOfParams = 0).isNotEmpty() &&
            node.javaClass.getMethodsMatching(name = "getCurr", numOfParams = 0).isNotEmpty()
    } catch (_: Throwable) { false }
}

private val framesNext = java.util.concurrent.ConcurrentHashMap<Class<*>, Boolean>()

internal fun fallbackKind(node: UIComponentAPI): String = when (node) {
    is LabelAPI -> "Label"
    is TextFieldAPI -> "TextField"
    is ButtonAPI -> "Button"
    is ScrollPanelAPI -> "ScrollPanel"
    is CustomPanelAPI -> "CustomPanel"
    is UIPanelAPI -> "Panel"
    else -> "Component"
}

internal fun textOf(node: UIComponentAPI): String? = when (node) {
    is LabelAPI -> node.text
    is TextFieldAPI -> node.text
    is ButtonAPI -> node.text
    else -> null
}

private fun number(value: Float): String =
    if (value == Math.round(value).toFloat()) Math.round(value).toString() else String.format(java.util.Locale.ROOT, "%.1f", value)
