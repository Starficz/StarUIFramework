package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import org.starficz.starui.compose.Alignment
import org.starficz.starui.compose.layout.NodeSize
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.compose.modifier.actual
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.ReflectionUtils
import org.starficz.starui.core.ReflectionUtils.getMethodsMatching
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * What StarUI knows about one UI component: how it is sized and spaced, where it sits, how to measure and lay it
 * out, how to recognise it on a rebuild, and its [StarUIPanelPlugin] when it has one (its handlers and, for a
 * container, its layout engine and scope). Set by the component's call and its modifiers; read by the engine of the
 * container it is in, or by itself when nothing lays it out (a top-level node).
 *
 * A node lives as long as its component does, without StarUI holding either: see [NodeStore].
 */
internal class UINode {
    /** Its size rule: from its component call's `size`. */
    var size: NodeSize = NodeSize()
    /**
     * Space inside it: a container's between its edge and its children, a leaf's around its content (its component
     * call's content padding plus `Modifier.padding`). A container's measure includes it; a leaf's does not.
     */
    var padding: PaddingValues = PaddingValues.Zero
    /** Space outside it (`Modifier.margin`). */
    var margin: PaddingValues = PaddingValues.Zero
    /** Where it sits in a Box (`Modifier.align`); top left when null. */
    var alignment: Alignment? = null
    /** Its anchor (`Modifier.anchor`), margins included: a top-level node applies it again after each layout. */
    var anchor: Anchor.AnchorData? = null
    /** A leaf's content size in a room of (width, height), padding excluded; its current size when null. */
    var measure: ((maxWidth: Float, maxHeight: Float) -> Pair<Float, Float>)? = null
    /** Runs after its container gave it its size. */
    var layout: ((width: Float, height: Float) -> Unit)? = null
    /** For an [Dimension.AspectRatio][org.starficz.starui.compose.layout.Dimension.AspectRatio] axis: width / height. */
    var aspectRatio: (() -> Float)? = null
    /** What the call that made it remembered for the next rebuild of its container; null when no call made it. */
    var record: ChildRecord? = null

    /** Its plugin: a CustomPanel's own, or the one a leaf got for its modifiers. */
    var plugin: StarUIPanelPlugin? = null
        internal set

    /** Back to a blank node before a rebuild's call configures the reused component again ([record] stays). */
    fun resetForBuild() {
        size = NodeSize()
        padding = PaddingValues.Zero
        margin = PaddingValues.Zero
        alignment = null
        anchor = null
        measure = null
        layout = null
        aspectRatio = null
    }

    // ---- where it is held (NodeStore) ----
    /** What a vanilla store holds for this node. */
    internal val holder = NodeStore.Holder(this)
    /** The component whose store holds [holder]: its own, or a label's parent; null for an orphan or a plugin's. */
    internal var heldBy: WeakReference<UIComponentAPI>? = null
}

/**
 * Where nodes live. A node must last exactly as long as its component, so StarUI never holds it in a map of its own
 * (a map value that refers to its key keeps the key alive forever, and with it the whole tree around it):
 * - a CustomPanel with a StarUI plugin: the plugin holds the node (the panel holds the plugin);
 * - any other component: vanilla's own per-component store, the object repository behind the public
 *   `addListener(Object)` / `getListeners(Class)` of the engine's base UI component class (readable names, not API:
 *   re-check after a game update). Vanilla only ever asks it for its own listener interfaces by class, so a
 *   [Holder] is never handed to vanilla code;
 * - a label (its class has no such store): its parent's store, moved to the new parent's when it is added elsewhere
 *   ([childAdded]); one not in any panel yet is held strongly here until it is added or disposed.
 * Lookups go through weak indexes whose values are weak too, so they keep nothing alive: one for every node, and a
 * small one for the leaves that have a plugin, which the lifecycle hooks look up on every vanilla component.
 */
internal object NodeStore {
    /** What sits in a vanilla component's store: the node, under a class only StarUI knows. */
    internal class Holder(val node: UINode)

    private val index = WeakHashMap<UIComponentAPI, WeakReference<UINode>>()
    /** The leaves that have a plugin (few): what the lifecycle hooks look up, for every vanilla component they see. */
    private val leafPlugins = WeakHashMap<UIComponentAPI, WeakReference<StarUIPanelPlugin>>()
    /** Nodes of components that are in no panel yet and have no store of their own. */
    private val orphans = HashSet<UINode>()

    private class StoreMethods(val add: ReflectionUtils.ReflectedMethod, val remove: ReflectionUtils.ReflectedMethod)
    private val storeMethods = HashMap<Class<*>, StoreMethods?>()

    /** The store methods of [component]'s class, or null when it has none (labels). */
    private fun storeOf(component: Any): StoreMethods? = storeMethods.getOrPut(component.javaClass) {
        val any = arrayOf<Class<*>?>(Any::class.java)
        val add = component.getMethodsMatching("addListener", parameterTypes = any).singleOrNull()
        val remove = component.getMethodsMatching("removeListener", parameterTypes = any).singleOrNull()
        val get = component.getMethodsMatching("getListeners", parameterTypes = arrayOf(Class::class.java)).singleOrNull()
        if (add != null && remove != null && get != null) StoreMethods(add, remove) else null
    }

    /** True once a node is held by something other than its own component (a label's): the add hook has work then. */
    @Volatile
    var hasMovableNodes = false
        private set

    /** The plugin of a leaf (not a CustomPanel), or null. */
    fun leafPlugin(component: UIComponentAPI): StarUIPanelPlugin? = synchronized(leafPlugins) { leafPlugins[component]?.get() }

    /** Makes [plugin] [component]'s (a leaf's), on its node. */
    fun attachLeafPlugin(component: UIComponentAPI, node: UINode, plugin: StarUIPanelPlugin) {
        plugin.adoptNode(node)
        synchronized(leafPlugins) { leafPlugins[component] = WeakReference(plugin) }
    }

    fun lookup(component: UIComponentAPI): UINode? {
        val target = component.actual
        if (target is CustomPanelAPI) (target.plugin as? StarUIPanelPlugin)?.let { return it.node }
        synchronized(index) { return index[target]?.get() }
    }

    /** [component]'s node, made (and held as described above) when it has none. */
    fun nodeFor(component: UIComponentAPI): UINode {
        val target = component.actual
        if (target is CustomPanelAPI) (target.plugin as? StarUIPanelPlugin)?.let { return it.node }
        synchronized(index) { index[target]?.get()?.let { return it } }
        val node = UINode()
        // its own store; a label has none: its parent's; in no panel yet: the orphans
        val owner = if (storeOf(target) != null) target else target.parent?.takeIf { storeOf(it) != null }
        if (owner != null) putIn(owner, node) else orphans.add(node)
        if (owner !== target) hasMovableNodes = true
        synchronized(index) { index[target] = WeakReference(node) }
        return node
    }

    private fun putIn(owner: UIComponentAPI, node: UINode) {
        storeOf(owner)!!.add.invoke(owner, node.holder)
        node.heldBy = WeakReference(owner)
    }

    private fun takeOut(node: UINode) {
        node.heldBy?.get()?.let { owner -> storeOf(owner)?.remove?.invoke(owner, node.holder) }
        node.heldBy = null
        orphans.remove(node)
    }

    /** [child] was added to [parent]: a node held by its old parent (a label's), or an orphan, moves to [parent]. */
    fun childAdded(parent: UIComponentAPI, child: UIComponentAPI) {
        val target = child.actual
        val node = synchronized(index) { index[target]?.get() } ?: return
        val owner = node.heldBy?.get()
        if (owner === target || owner === parent || storeOf(parent) == null) return
        takeOut(node)
        putIn(parent, node)
    }

    /** Forgets [component]'s node (it is disposed). A CustomPanel's stays with its plugin. */
    fun release(component: UIComponentAPI) {
        val target = component.actual
        synchronized(leafPlugins) { leafPlugins.remove(target) }
        val node = synchronized(index) { index.remove(target)?.get() } ?: return
        takeOut(node)
    }
}

/** [component]'s node, made when it has none. For recording, from component calls and modifiers. */
internal fun nodeFor(component: UIComponentAPI): UINode = NodeStore.nodeFor(component)

/** [component]'s node, or null when nothing was recorded for it. */
internal fun nodeOf(component: UIComponentAPI): UINode? = NodeStore.lookup(component)

/** The StarUI container [component] is a child of, or null (no parent, or one without a StarUI plugin). */
internal fun parentPluginOf(component: UIComponentAPI): StarUIPanelPlugin? =
    component.actual.parent?.let { StarUIPanelPlugin.getAttached(it) }

/** Read-only defaults for a child nothing was recorded for. Never change it. */
internal val NO_NODE = UINode()

/** [child]'s node, or the defaults: for the layout engines, which only read. */
internal fun childNode(child: UIComponentAPI): UINode = nodeOf(child) ?: NO_NODE
