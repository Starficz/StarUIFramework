package org.starficz.starui

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.components.AnchorPanel
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.runtime.NodeStore
import org.starficz.starui.runtime.Scope
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.runtime.nodeFor
import org.starficz.starui.vanilla.applyAnchor
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Where [inject] puts its container among the panel's children. */
enum class InjectPlacement {
    /** Added last: drawn over the panel's other children, and the first to get input. */
    Top,
    /** Sent to the bottom: drawn under the panel's other children. */
    Bottom,
}

/**
 * Adds StarUI to a vanilla panel: one [AnchorPanel][org.starficz.starui.components.AnchorPanel] of [size] with
 * [modifier] (usually an anchor to a vanilla sibling), holding the content [builder] makes. It is reactive like any
 * StarUI container.
 *
 * Call it at the moment vanilla builds the panel, typically from a PatchLib `@After` hook on the method or
 * constructor that builds it, with the hook's `self` (or a panel reached from it) as the receiver:
 *
 * ```kotlin
 * @Patch(target = ClassMatch(typeName = "com.fs.starfarer.coreui.refit.ModWidget"))
 * object RefitButtonPatch {
 *     @JvmStatic @After(target = MethodMatch(methodType = MethodType.CONSTRUCTOR))
 *     fun afterCreate(context: AfterContext) {
 *         (context.self as? UIPanelAPI)?.inject("mymod:ai-config") { Button("AI Config") { openConfig() } }
 *     }
 * }
 * ```
 *
 * - **One injection per [key]**: while the panel holds a live injection under [key], another call returns it and
 *   does nothing else. So a hook that fires twice, a build method that runs again, or a mod's own every-frame script
 *   (the fallback where no hook point exists) can all just call it. To build it again, call [removeInjection] first.
 * - **It survives `clearChildren()`**: when the panel clears its children, as vanilla does to rebuild a panel in
 *   place, the injection is added again at the panel's next render, before its children draw, with [builder] run
 *   again. So a hook on the panel's first build is enough. [removeInjection], disposing the container, or removing
 *   it yourself ends it for good; only a clear brings it back.
 * - **Nothing is global**: the record sits on the panel, and goes when vanilla throws the panel away.
 *
 * Because [builder] can run again after a clear, read what it needs from the panel inside it (`this.parent`,
 * getters, its siblings), rather than capturing objects that a vanilla rebuild replaces.
 *
 * - **Size and place**: [size] is measured against the vanilla panel. `Size.fillMaxSize()` takes the panel's whole
 *   size, and follows it when vanilla sizes or resizes the panel later (a constructor hook runs before that). Without
 *   an anchor in [modifier], the container is put at the panel's top left once (vanilla's own default is the bottom
 *   left), and placing it yourself afterwards (`position.belowLeft(...)`) sticks. An anchor is applied again on every
 *   layout, so it also follows siblings that move; prefer it.
 *
 * Not for StarUI's own containers: there, put the components in the container's builder.
 *
 * @param key the injection's name, unique per panel. Prefix it with your mod's id (`"mymod:refit-button"`)
 * @param size the size of the container, against the vanilla panel: wrap (the default), exact, or fill
 * @param modifier the [Modifier] to be applied to the container, usually its anchor (the top left without one)
 * @param placement where the container goes among the panel's children
 * @param builder the content of the container. It runs again after a clear, and when a [UIState] it read changes
 * @return the container: the new one, or the live one already under [key]
 * @throws IllegalStateException if this panel is a StarUI container
 * @see injection
 * @see removeInjection
 */
fun UIPanelAPI.inject(
    key: String,
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,
    placement: InjectPlacement = InjectPlacement.Top,
    builder: StarUIBuilder,
): CustomPanelAPI {
    val host = Injections.hostOf(this)
    if (StarUIPanelPlugin.getAttached(host)?.scope?.isReactive == true) throw IllegalStateException(
        "StarUI: inject(\"$key\") on a reactive StarUI container. inject is for vanilla panels; inside a StarUI " +
        "container, put the components in its builder."
    )
    val records = Injections.recordsOf(host, create = true)!!
    records.entries[key]?.let { old ->
        Injections.liveContainer(host, old)?.let { return it }
        Injections.detach(host, old)
    }
    val entry = Injections.Entry(key, size, modifier, placement, builder)
    records.entries[key] = entry
    return Injections.build(host, entry)
}

/**
 * Returns the live container that [inject] made on this panel under [key], or `null` if there is none.
 *
 * @param key the injection's name
 */
fun UIPanelAPI.injection(key: String): CustomPanelAPI? {
    val host = Injections.hostOf(this)
    val entry = Injections.recordsOf(host, create = false)?.entries?.get(key) ?: return null
    return Injections.liveContainer(host, entry)
}

/**
 * Removes the injection under [key] from this panel and disposes it, for good: a later clear does not bring it back.
 * Does nothing if there is none.
 *
 * @param key the injection's name
 */
fun UIPanelAPI.removeInjection(key: String) {
    val host = Injections.hostOf(this)
    val records = Injections.recordsOf(host, create = false) ?: return
    val entry = records.entries.remove(key) ?: return
    Injections.detach(host, entry)
}

internal object Injections {
    /** One [inject] call's arguments, kept to build it again after a clear, and its live container (weakly). */
    internal class Entry(
        val key: String,
        val size: Size,
        val modifier: Modifier,
        val placement: InjectPlacement,
        val builder: StarUIBuilder,
    ) {
        var container: WeakReference<CustomPanelAPI>? = null
        /** Its live container was dropped by a `clearChildren()`: build it again at the host's next render. */
        var readdPending = false
    }

    /** A panel's injections by key: in the panel's own vanilla store (see [NodeStore]), so it lives as long as the panel. */
    internal class Records {
        val entries = LinkedHashMap<String, Entry>()
    }

    /** Hosts that cleared a live injection: built again at their next render. Weak: a host thrown away is dropped. */
    private val pendingHosts = WeakHashMap<UIPanelAPI, Unit>()

    /** True while [pendingHosts] has entries: the one check the render hook makes on every component. */
    @Volatile
    var hasPending = false
        private set

    /** The panel children actually go into: a scroll panel's content container. */
    fun hostOf(panel: UIPanelAPI): UIPanelAPI = (panel as? ScrollPanelAPI)?.contentContainer ?: panel

    fun recordsOf(host: UIPanelAPI, create: Boolean): Records? {
        NodeStore.findInStore(host, Records::class.java)?.let { return it }
        if (!create) return null
        val records = Records()
        check(NodeStore.putInStore(host, records)) { "StarUI: inject: this panel (${host.javaClass.name}) has no object store" }
        return records
    }

    /** [entry]'s container while it is still in [host] and not disposed. */
    fun liveContainer(host: UIPanelAPI, entry: Entry): CustomPanelAPI? {
        val container = entry.container?.get() ?: return null
        if (container.parent !== host) return null
        if ((container.plugin as? StarUIPanelPlugin)?.isDisposed != false) return null
        return container
    }

    fun build(host: UIPanelAPI, entry: Entry): CustomPanelAPI {
        // isolated from any builder that happens to be running: no outer key position, no outer reads
        val container = StarUIContext.withKey(null) {
            StarUIContext.untracked { host.AnchorPanel(entry.size, entry.modifier) { (entry.builder)() } }
        }
        (container.plugin as StarUIPanelPlugin).injectionKey = entry.key
        // no anchor of its own: placed at the panel's top left once, as a layout reads (vanilla puts a new component
        // bottom left). Not stored as its anchor, so the caller's own placement afterwards (position.belowLeft...)
        // stays: layouts re-apply only anchors that were asked for.
        if (nodeFor(container).anchor == null) container.applyAnchor(Anchor.inside.topLeft.ofParent())
        if (entry.placement == InjectPlacement.Bottom) host.sendToBottom(container)
        entry.container = WeakReference(container)
        return container
    }

    /**
     * Takes [entry]'s container (if any) out of [host]; it is disposed at the next safe point, not here: this can run
     * inside one of the container's own handlers (an onAdvance that makes vanilla rebuild the host, whose hook then
     * injects again), and disposing clears the handler list that is being run.
     */
    fun detach(host: UIPanelAPI, entry: Entry) {
        val container = entry.container?.get() ?: return
        entry.container = null
        if (container.parent === host) host.removeComponent(container)
        if (container.parent == null) Scope.noteDetached(container)
    }

    /** From the `clearChildren` hook, before the clear: [child] of [host] is an injection container about to go. */
    fun childCleared(host: UIPanelAPI, child: UIComponentAPI) {
        val plugin = StarUIPanelPlugin.getAttached(child) ?: return
        val key = plugin.injectionKey ?: return
        if (plugin.isDisposed) return
        val entry = recordsOf(host, create = false)?.entries?.get(key) ?: return
        if (entry.container?.get() !== child) return
        entry.readdPending = true
        synchronized(pendingHosts) {
            pendingHosts[host] = Unit
            hasPending = true
        }
    }

    /** From the render hook, before [component] renders: build again the injections its last clear dropped. */
    fun beforeRender(component: Any?) {
        val host = component as? UIPanelAPI ?: return
        synchronized(pendingHosts) {
            if (pendingHosts.remove(host) == null) return
            hasPending = pendingHosts.isNotEmpty()
        }
        val records = recordsOf(host, create = false) ?: return
        // only the entries the clear dropped: one ended by dispose or removeComponent stays ended
        for (entry in records.entries.values.toList()) {
            if (!entry.readdPending) continue
            entry.readdPending = false
            if (liveContainer(host, entry) != null) continue   // vanilla added it back itself (a clear and re-add)
            build(host, entry)
        }
    }
}
