package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.layout.NodeSize
import org.starficz.starui.compose.modifier.actual
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.getChildrenNonCopy

/**
 * The UI tree below this component as indented text, one line per component: what it is, its text, its size, its
 * position relative to this component's top-left corner (x to the right, y downwards, the way layouts are
 * written), how StarUI sizes it, and which modifiers and reactive scope are attached.
 *
 * Meant for debugging layouts without a screenshot, for tests, and for AI agents that cannot see the screen:
 *
 * ```
 * Column 150x80 at (40, 40) size=(wrap, wrap) [renderBelow] scope(builds=2, reads=1)
 *   Text "state B" 52x15 at (0, 0) size=(wrap, wrap)
 *   Spacer 100x20 at (0, 20) size=(exact 100, exact 20) [renderBelow]
 * ```
 *
 * Sizes and positions are the current ones: call it after a frame has rendered, or the layout has not run yet.
 */
fun UIComponentAPI.dumpLayout(): String {
    val root = this.actual
    val out = StringBuilder()
    dumpNode(root, root, null, 0, out)
    return out.toString()
}

private fun dumpNode(node: UIComponentAPI, root: UIComponentAPI, parentPlugin: StarUIPanelPlugin?, depth: Int, out: StringBuilder) {
    val plugin = StarUIPanelPlugin.getAttached(node)
    val position = node.position
    val rootPosition = root.position
    val left = position.x - rootPosition.x
    val top = (rootPosition.y + rootPosition.height) - (position.y + position.height)

    repeat(depth) { out.append("  ") }
    out.append(parentPlugin?.childRecords?.get(node)?.kind ?: plugin?.kind ?: fallbackKind(node))
    textOf(node)?.let { text ->
        out.append(" \"").append(if (text.length > 40) text.take(37) + "..." else text).append('"')
    }
    out.append(' ').append(number(position.width)).append('x').append(number(position.height))
    out.append(" at (").append(number(left)).append(", ").append(number(top)).append(')')

    val sizing = parentPlugin?.childSizing?.get(node) ?: plugin?.takeIf { it.isTopLevelNode }?.myNodeSize
    if (sizing != null) out.append(" size=").append(describe(sizing))
    // a container's padding is its own (space inside it); a leaf's padding is recorded by its parent
    val padding = parentPlugin?.childPadding?.get(node)
        ?: plugin?.takeIf { it.layoutEngine != null }?.myPadding?.takeIf { it.horizontal != 0f || it.vertical != 0f }
    padding?.let { p ->
        out.append(" padding=(").append(number(p.left)).append(", ").append(number(p.top)).append(", ")
            .append(number(p.right)).append(", ").append(number(p.bottom)).append(')')
    }
    parentPlugin?.childMargins?.get(node)?.let { m ->
        out.append(" margin=(").append(number(m.left)).append(", ").append(number(m.top)).append(", ")
            .append(number(m.right)).append(", ").append(number(m.bottom)).append(')')
    }
    if (plugin != null) {
        val handlers = plugin.describeHandlers()
        if (handlers.isNotEmpty()) out.append(" [").append(handlers).append(']')
        plugin.scope?.let { scope ->
            out.append(" scope(builds=").append(scope.buildCount)
            if (scope.isReactive) out.append(", reactive")
            if (scope.dirty) out.append(", dirty")
            out.append(')')
        }
        if (plugin.isDisposed) out.append(" DISPOSED")
    }
    out.append('\n')

    when (node) {
        // native widgets: their inner parts are engine details, not layout
        is LabelAPI, is ButtonAPI, is TextFieldAPI -> return
        is ScrollPanelAPI -> {
            val content = (node as Any).invoke("getContentContainer") as? UIPanelAPI ?: return
            val contentPlugin = StarUIPanelPlugin.getAttached(content)
            for (child in content.getChildrenNonCopy()) dumpNode(child, root, contentPlugin, depth + 1, out)
        }
        is UIPanelAPI -> for (child in node.getChildrenNonCopy()) dumpNode(child, root, plugin, depth + 1, out)
    }
}

private fun fallbackKind(node: UIComponentAPI): String = when (node) {
    is LabelAPI -> "Label"
    is TextFieldAPI -> "TextField"
    is ButtonAPI -> "Button"
    is ScrollPanelAPI -> "ScrollPanel"
    is CustomPanelAPI -> "CustomPanel"
    is UIPanelAPI -> "Panel"
    else -> "Component"
}

private fun textOf(node: UIComponentAPI): String? = when (node) {
    is LabelAPI -> node.text
    is TextFieldAPI -> node.text
    is ButtonAPI -> node.text
    else -> null
}

private fun describe(size: NodeSize): String = "(" + describe(size.width) + ", " + describe(size.height) + ")"

private fun describe(dimension: Dimension): String = when (dimension) {
    is Dimension.Exact -> "exact " + number(dimension.pixels)
    is Dimension.FillMax -> if (dimension.weight == 1f) "fill" else "fill " + number(dimension.weight)
    Dimension.WrapContent -> "wrap"
    Dimension.AspectRatio -> "aspect"
}

private fun number(value: Float): String =
    if (value == Math.round(value).toFloat()) Math.round(value).toString() else String.format(java.util.Locale.ROOT, "%.1f", value)
