package org.starficz.starui.window

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.starficz.starui.Anchor
import org.starficz.starui.Size
import org.starficz.starui.StarUIBuilder
import org.starficz.starui.StarUIDsl
import org.starficz.starui.StarUITheme
import org.starficz.starui.components.Box
import org.starficz.starui.modifier.Modifier
import org.starficz.starui.modifier.anchor
import org.starficz.starui.modifier.plugin
import org.starficz.starui.runtime.NodeStore
import org.starficz.starui.runtime.Scope
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.getChildrenNonCopy
import java.lang.ref.WeakReference

/**
 * A layer over the whole screen: a full-screen StarUI panel on top of whatever is showing (the title screen, the
 * campaign with its dialogs and core tabs, a battle), opened with [Layer] in a builder or [showLayer] from anywhere
 * else. Layers stack in the order they open; tooltips from inside one show above it.
 *
 * A **modal** layer is what vanilla's dialogs put under themselves: it dims the screen and takes every key and every
 * click, so nothing under it (the screen, the map hotkeys, a layer below) sees them; Escape dismisses it, and it
 * pauses the campaign or the battle while it is open. A **non-modal** layer (a notice, a toast) dims nothing, lets
 * keys through, and takes only the clicks and mouse moves over its content.
 *
 * Belongs to the screen it opened on: when the game moves to another screen (the campaign to a battle, back to the
 * title), the layer goes with the old screen and [isOpen] reads false.
 */
class StarUILayer internal constructor(
    host: UIPanelAPI,
    internal var modal: Boolean,
    internal var dim: Float,
    internal var pause: Boolean,
    internal var dismissOnEscape: Boolean,
    internal var onDismiss: () -> Unit,
    private val onClose: () -> Unit,
) {
    private val hostRef = WeakReference(host)
    internal val host: UIPanelAPI? get() = hostRef.get()
    internal var content: CustomPanelAPI.() -> Unit = {}
    internal var root: CustomPanelAPI? = null
    internal var closing = false
        private set
    internal var fade = 0f
    internal var lastTick = 0L
    /** How long it fades in and out, in seconds. */
    internal var fadeIn = DEFAULT_FADE
    internal var fadeOut = DEFAULT_FADE
    /** Runs every frame from the layer's advance (the toast layer ages its toasts). */
    internal var onTick: ((Float) -> Unit)? = null
    /** Runs as the layer starts closing (a [Dialog] folds its window shut). */
    internal var onClosing: (() -> Unit)? = null
    /** Sees each key press nothing in the layer took, before Escape is handled (a [Dialog]'s Enter, Space and F2). */
    internal var onKey: ((InputEventAPI) -> Unit)? = null
    /**
     * Whether a mouse event at (x, y) is over the layer's content: a non-modal layer takes those. By default its
     * panel's children; the toasts' layer, whose one child covers the screen, tests its toasts.
     */
    internal var hitTest: ((Float, Float) -> Boolean)? = null
    /** Sees each click nothing in the layer took (a [Dialog] flickers when it lands outside its window). */
    internal var onClickOutsideContent: ((InputEventAPI) -> Unit)? = null
    /** The [Dialog] whose window sits on this layer, if any. */
    internal var dialog: StarUIDialog? = null
    /** Layers asked for (with [Layer]) from inside this one: closed with it. */
    internal val nested = ArrayList<WeakReference<StarUILayer>>()

    /**
     * The layer's full-screen panel, a [Box]: its builder's receiver. Content placed with `Modifier.align` in it
     * (`Alignment.Center` for a window).
     */
    val panel: CustomPanelAPI get() = root ?: throw IllegalStateException("StarUI: this layer is not built yet")

    /** Whether the layer is showing: false from the moment it closes, and once its screen is gone. */
    val isOpen: Boolean get() = !closing && root != null && host != null && host === Layers.currentHost()

    /**
     * Closes the layer: it fades out, then goes. Its `onClose` runs now, once; a second call does nothing.
     *
     * A [Layer] from a builder is shown for as long as the builder calls it, so a close from code lasts until that
     * builder runs again and calls it: to close one for good, change the state its builder reads.
     */
    fun close() = Layers.close(this)

    internal fun markClosing(): Boolean {
        if (closing) return false
        closing = true
        return true
    }

    internal fun runOnClose() = onClose()

    /** Wires the layer's behaviour into its panel's plugin, once, when the panel is made. */
    internal fun attach(plugin: StarUIPanelPlugin) {
        plugin.consumeEvents = modal
        plugin.onRenderBelow { alphaMult ->
            if (dim <= 0f) return@onRenderBelow
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glColor4f(0f, 0f, 0f, dim * alphaMult)
            GL11.glRectf(plugin.left, plugin.bottom, plugin.right, plugin.top)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
        }
        plugin.onAdvance { amount -> Layers.tick(this@StarUILayer, amount) }
        // the panel's children get input before its plugin: a control in the layer that takes Escape keeps it
        plugin.onKeyDown { event ->
            if (closing) return@onKeyDown
            onKey?.invoke(event)
            if (event.isConsumed || event.eventValue != Keyboard.KEY_ESCAPE || event.isRepeat || !dismissOnEscape) return@onKeyDown
            event.consume()
            onDismiss()
        }
        plugin.onHover { event -> if (!modal && overContent(event)) event.consume() }
        plugin.onClick { event -> if (!closing) onClickOutsideContent?.invoke(event) }
    }

    private fun overContent(event: InputEventAPI): Boolean {
        val panel = root ?: return false
        val x = event.x.toFloat()
        val y = event.y.toFloat()
        hitTest?.let { return it(x, y) }
        return panel.getChildrenNonCopy().any {
            val p = it.position
            x >= p.x && x <= p.x + p.width && y >= p.y && y <= p.y + p.height
        }
    }
}

/**
 * Opens a [StarUILayer] over the current screen and builds [builder] in it: for code outside the UI (a click
 * handler, a campaign script, an ability, a hotkey). It stays until it is closed: by [StarUILayer.close] (the builder
 * gets the layer), or by Escape when [closeOnEscape].
 *
 * ```kotlin
 * Button("Fleet doctrine") {
 *     showLayer { layer ->
 *         Column(modifier = Modifier.align(Alignment.Center)) {
 *             Text("Doctrine")
 *             Button("Done") { layer.close() }
 *         }
 *     }
 * }
 * ```
 *
 * Inside a builder use [Layer] instead, which is shown while the builder calls it.
 *
 * @param modal whether the layer dims the screen and takes every key and click (see [StarUILayer])
 * @param dim how dark the dim is: black at this alpha, 0 for none. A modal layer's default is the theme's
 *   ([LayerTheme.dim][org.starficz.starui.LayerTheme.dim], 0.6)
 * @param pause whether the campaign or battle pauses while the layer is open. The pause state from before the first
 *   pausing layer comes back when the last one closes
 * @param closeOnEscape whether Escape closes the layer (when nothing in it took the key first)
 * @param onClose runs once when the layer closes, however it closes
 * @param builder the layer's content, in its full-screen [Box]; reactive like any builder
 * @return the layer
 * @throws IllegalStateException when called while a builder runs (use [Layer]), or before the game has a screen
 */
fun showLayer(
    modal: Boolean = true,
    dim: Float = if (modal) StarUITheme.current.layer.dim else 0f,
    pause: Boolean = modal,
    closeOnEscape: Boolean = modal,
    onClose: () -> Unit = {},
    builder: (@StarUIDsl CustomPanelAPI).(layer: StarUILayer) -> Unit
): StarUILayer {
    if (StarUIContext.currentScope != null) throw IllegalStateException(
        "StarUI: showLayer(...) was called while a builder runs, so it would open another layer every time that " +
        "builder runs again. In a builder use Layer(...), which is shown for as long as the builder calls it: " +
        "`if (showPicker.value) Layer(onDismissRequest = { showPicker.value = false }) { ... }`."
    )
    lateinit var layer: StarUILayer
    layer = Layers.openLayer(modal, dim, pause, closeOnEscape, onDismiss = { layer.close() }, onClose = onClose,
        prepare = {}, builder = builder)
    return layer
}

/**
 * Shows a [StarUILayer] over the current screen for as long as this builder calls it, as Compose's `Dialog`: put the
 * call under the state that says it is open, and it opens when the state turns true and closes (fading out) when a
 * rebuild leaves it out, or when the UI that asked for it goes away.
 *
 * ```kotlin
 * val confirmDelete = UIState(false)
 * Column {
 *     Button("Delete") { confirmDelete.value = true }
 *     if (confirmDelete.value) Layer(onDismissRequest = { confirmDelete.value = false }) {
 *         Column(modifier = Modifier.align(Alignment.Center)) {
 *             Text("Delete the plan?")
 *             Button("Delete") { deletePlan(); confirmDelete.value = false }
 *         }
 *     }
 * }
 * ```
 *
 * Escape (when [dismissOnEscape]) calls [onDismissRequest]; the layer stays until the state it reads changes, so
 * [onDismissRequest] is where that state changes. Each run of the builder keeps the same layer and runs its content
 * again with the new values. Several in one builder are told apart by their order, or by `key(...)` around them.
 * Layers asked for from inside a layer close with it.
 *
 * @param onDismissRequest called when the player dismisses the layer (Escape): change the state that shows it
 * @param modal whether the layer dims the screen and takes every key and click (see [StarUILayer])
 * @param dim how dark the dim is: black at this alpha, 0 for none. A modal layer's default is the theme's
 *   ([LayerTheme.dim][org.starficz.starui.LayerTheme.dim], 0.6)
 * @param pause whether the campaign or battle pauses while the layer is open
 * @param dismissOnEscape whether Escape calls [onDismissRequest] (when nothing in the layer took the key first)
 * @param builder the layer's content, in its full-screen [Box]; reactive like any builder
 * @return the layer
 * @throws IllegalStateException when called outside a builder (use [showLayer]), or before the game has a screen
 */
fun UIPanelAPI.Layer(
    onDismissRequest: () -> Unit,
    modal: Boolean = true,
    dim: Float = if (modal) StarUITheme.current.layer.dim else 0f,
    pause: Boolean = modal,
    dismissOnEscape: Boolean = modal,
    builder: StarUIBuilder
): StarUILayer {
    return Layers.declareLayer("Layer", onDismissRequest, modal, dim, pause, dismissOnEscape, prepare = {}) { builder() }
}

/** How long a layer fades in and out unless its kind says otherwise, in seconds. */
internal const val DEFAULT_FADE = 0.15f

/** One scope's [Layer] calls: which ran in its last build, closed when a build leaves them out. */
internal class LayerSlots {
    val layers = LinkedHashMap<Any, StarUILayer>()
    val seen = HashSet<Any>()
    var calls = 0

    fun beforeBuild() {
        seen.clear()
        calls = 0
    }

    fun afterBuild() {
        val iterator = layers.entries.iterator()
        while (iterator.hasNext()) {
            val (key, layer) = iterator.next()
            if (key in seen && !layer.closing) continue
            Layers.close(layer)
            iterator.remove()
        }
    }

    fun closeAll() {
        for (layer in layers.values) Layers.close(layer)
        layers.clear()
    }
}

internal object Layers {
    /** Every fade's length is multiplied by this. Tests set 0: layers open and close at once. */
    internal var fadeScale = 1f

    /** Tests: the panel layers open on instead of the screen panel (the suite runs before any screen exists). */
    internal var testHost: UIPanelAPI? = null

    /** The layers open on one screen panel, in the order they opened; kept in the panel's own store. */
    internal class Stack {
        val layers = ArrayList<StarUILayer>()
        var wasPaused = false
        /** The toasts' layer while toasts show: kept above every other layer. */
        var toasts: ToastHost? = null
    }

    /** Puts the toasts' layer back on top of [host]'s children, above a layer that just opened. */
    private fun raiseToasts(host: UIPanelAPI, stack: Stack) {
        val panel = stack.toasts?.layer?.root ?: return
        if (panel.parent === host && host.getChildrenNonCopy().lastOrNull() !== panel) host.bringComponentToTop(panel)
    }

    fun currentHost(): UIPanelAPI? = testHost ?: Global.getSettings().screenPanel

    fun requireHost(): UIPanelAPI = currentHost()
        ?: throw IllegalStateException("StarUI: there is no screen to show a layer on yet (the game is still loading)")

    fun stackOf(host: UIPanelAPI, create: Boolean): Stack? {
        NodeStore.findInStore(host, Stack::class.java)?.let { return it }
        if (!create) return null
        val stack = Stack()
        check(NodeStore.putInStore(host, stack)) { "StarUI: the screen panel (${host.javaClass.name}) has no object store" }
        return stack
    }

    /**
     * The open layer whose panel holds [component], or null. Up through the parents; content a component draws
     * itself (a Holo's, a [Dialog]'s window) has no parent, so the layers' trees are searched for it then.
     */
    fun ownerOf(component: UIComponentAPI, host: UIPanelAPI): StarUILayer? {
        val stack = stackOf(host, create = false) ?: return null
        var node: UIComponentAPI = component
        while (true) {
            stack.layers.firstOrNull { it.root === node }?.let { return it }
            val parent = node.parent ?: break
            if (parent === host) return null
            node = parent
        }
        return stack.layers.firstOrNull { layer -> layer.root?.let { holds(it, node) } == true }
    }

    /** Whether [target] is in [root]'s tree, counting what its components draw themselves (`ownedOutsideTree`). */
    private fun holds(root: UIComponentAPI, target: UIComponentAPI): Boolean {
        if (root === target) return true
        StarUIPanelPlugin.getAttached(root)?.ownedOutsideTree?.forEach { if (holds(it, target)) return true }
        if (root is UIPanelAPI) for (child in root.getChildrenNonCopy()) if (holds(child, target)) return true
        return false
    }

    /** A layer from code ([showLayer], [showDialog]): [prepare] sets the layer up before its content builds. */
    fun openLayer(
        modal: Boolean, dim: Float, pause: Boolean, dismissOnEscape: Boolean, onDismiss: () -> Unit,
        onClose: () -> Unit, prepare: (StarUILayer) -> Unit, builder: CustomPanelAPI.(StarUILayer) -> Unit
    ): StarUILayer {
        val layer = StarUILayer(requireHost(), modal, dim, pause, dismissOnEscape, onDismiss, onClose)
        layer.content = { builder(layer) }
        prepare(layer)
        show(layer)
        return layer
    }

    /**
     * A layer from a builder ([Layer], [Dialog]): the one this call made last time when it is still open, with the new
     * arguments, or a new one. [prepare] runs on every call, before the content builds or runs again.
     */
    fun declareLayer(
        kind: String, onDismiss: () -> Unit, modal: Boolean, dim: Float, pause: Boolean, dismissOnEscape: Boolean,
        prepare: (StarUILayer) -> Unit, builder: CustomPanelAPI.(StarUILayer) -> Unit
    ): StarUILayer {
        val scope = StarUIContext.currentScope ?: throw IllegalStateException(
            "StarUI: $kind(...) is shown for as long as a builder calls it, so it must be called inside one (a " +
            "container's block, a StarUIPlugin screen, an inject builder). From a click handler or other code, use " +
            "show$kind(...)."
        )
        val slots = scope.layerSlots ?: LayerSlots().also { scope.layerSlots = it }
        val key = StarUIContext.nextKey() ?: "Layer#${slots.calls}"
        slots.calls++
        check(slots.seen.add(key)) { "StarUI: two $kind calls with the same key ($key) in one builder" }
        val existing = slots.layers[key]
        if (existing != null && !existing.closing && existing.root != null) {
            prepare(existing)
            adopt(existing, modal, dim, pause, dismissOnEscape, onDismiss) { builder(existing) }
            return existing
        }
        val host = requireHost()
        val layer = StarUILayer(host, modal, dim, pause, dismissOnEscape, onDismiss, onClose = {})
        layer.content = { builder(layer) }
        prepare(layer)
        ownerOf(scope.plugin.panel, host)?.nested?.add(WeakReference(layer))
        show(layer)
        slots.layers[key] = layer
        return layer
    }

    /** Builds [layer]'s panel on its host, on top. */
    fun show(layer: StarUILayer) {
        val host = layer.host ?: return
        val stack = stackOf(host, create = true)!!
        if (layer.pause) startPause(stack)
        stack.layers += layer
        layer.fade = if (layer.fadeIn * fadeScale <= 0f) 1f else 0f
        val width = host.position.width
        val height = host.position.height
        // isolated from any builder that is running: no outer key position, no outer reads
        val panel = StarUIContext.withKey(null) {
            StarUIContext.untracked {
                host.Box(Size.exact(width, height),
                    Modifier.anchor(Anchor.inside.topLeft.ofParent(0f, 0f)).plugin { layer.attach(this) }) {
                    layer.content(this)
                }
            }
        }
        layer.root = panel
        panel.opacity = layer.fade.coerceAtLeast(MIN_OPEN_OPACITY)
        raiseToasts(host, stack)
    }

    /** A [Layer] call ran again: take its new arguments and run its content again with them. */
    fun adopt(
        layer: StarUILayer, modal: Boolean, dim: Float, pause: Boolean, dismissOnEscape: Boolean,
        onDismiss: () -> Unit, content: CustomPanelAPI.() -> Unit
    ) {
        layer.onDismiss = onDismiss
        layer.dim = dim
        layer.dismissOnEscape = dismissOnEscape
        val plugin = layer.root?.let { StarUIPanelPlugin.getAttached(it) }
        if (layer.modal != modal) {
            layer.modal = modal
            plugin?.consumeEvents = modal
        }
        if (layer.pause != pause) {
            val stack = layer.host?.let { stackOf(it, create = false) }
            if (pause) {
                if (stack != null) startPause(stack)
                layer.pause = true
            } else {
                layer.pause = false
                if (stack != null) endPause(stack)
            }
        }
        layer.content = content
        plugin?.scope?.markDirty()
    }

    fun close(layer: StarUILayer) {
        if (!layer.markClosing()) return
        for (ref in layer.nested) ref.get()?.let { close(it) }
        layer.nested.clear()
        val stack = layer.host?.let { stackOf(it, create = false) }
        if (layer.pause && stack != null) endPause(stack)
        layer.onClosing?.invoke()
        val panel = layer.root
        when {
            // not drawn or advanced any more: nothing else would take it off
            panel == null || panel.parent == null -> finish(layer)
            // gone this frame; its next advance takes it off (not here: this can run in one of its own handlers)
            layer.fadeOut * fadeScale <= 0f -> panel.opacity = 0f
        }
        layer.runOnClose()
    }

    /** Every frame, from the layer's own advance: the fade in, and the fade out that ends in [finish]. */
    fun tick(layer: StarUILayer, amount: Float) {
        layer.onTick?.invoke(amount)
        val now = System.nanoTime()
        // the screen panels advance with the real frame time (paused or not), capped by the game; a wall clock covers
        // a frame that advances with less (a test driving frames by hand)
        val wall = if (layer.lastTick == 0L) 0f else (now - layer.lastTick) / 1e9f
        layer.lastTick = now
        val dt = maxOf(amount, wall).coerceAtMost(0.1f)
        val duration = (if (layer.closing) layer.fadeOut else layer.fadeIn) * fadeScale
        val step = if (duration <= 0f) 1f else dt / duration
        val panel = layer.root ?: return
        if (layer.closing) {
            layer.fade -= step
            if (layer.fade <= 0f) {
                finish(layer)
                return
            }
            panel.opacity = layer.fade
        } else if (layer.fade < 1f) {
            layer.fade = minOf(1f, layer.fade + step)
            panel.opacity = layer.fade.coerceAtLeast(MIN_OPEN_OPACITY)
        }
    }

    /**
     * Takes [layer]'s panel off its host and disposes it (a text field focused in it lets go of the keyboard). Runs
     * from its own advance, where disposing is safe (the advance stops once its plugin is disposed), or for a panel
     * that is not in the tree: never from its input handlers.
     */
    private fun finish(layer: StarUILayer) {
        val host = layer.host
        host?.let { stackOf(it, create = false) }?.layers?.remove(layer)
        val panel = layer.root ?: return
        if (host != null && panel.parent === host) host.removeComponent(panel)
        if (panel.parent == null) Scope.disposeTree(panel)
    }

    // ---- pause: per screen, from the first pausing layer to the last ----

    private fun pausingOpen(stack: Stack) = stack.layers.any { it.pause && !it.closing }

    private fun startPause(stack: Stack) {
        if (pausingOpen(stack)) return
        stack.wasPaused = isPaused()
        setPaused(true)
    }

    private fun endPause(stack: Stack) {
        if (pausingOpen(stack)) return
        setPaused(stack.wasPaused)
    }

    private fun isPaused(): Boolean = when (Global.getCurrentState()) {
        GameState.CAMPAIGN -> Global.getSector()?.isPaused ?: false
        GameState.COMBAT -> Global.getCombatEngine()?.isPaused ?: false
        else -> false
    }

    private fun setPaused(paused: Boolean) {
        if (testHost != null) return
        when (Global.getCurrentState()) {
            GameState.CAMPAIGN -> Global.getSector()?.isPaused = paused
            GameState.COMBAT -> Global.getCombatEngine()?.isPaused = paused
            else -> {}
        }
    }

    // ---- the campaign's core rebuild takes every child off its screen panel: put open layers back ----

    @Volatile
    var hasPending = false
        private set
    private var pendingHost: WeakReference<UIPanelAPI>? = null

    /** From the core UI hook: when that core replaced the campaign's, open layers were taken off the screen panel. */
    fun afterCoreUICreated() {
        val host = currentHost() ?: return
        val stack = stackOf(host, create = false) ?: return
        if (stack.layers.none { it.root?.parent == null }) return
        pendingHost = WeakReference(host)
        hasPending = true
    }

    /** From the render hook: before the screen panel draws, its layers go back on top, in their order. */
    fun beforeRender(component: Any?) {
        val host = pendingHost?.get()
        if (host == null) {
            hasPending = false
            return
        }
        if (component !== host) return
        pendingHost = null
        hasPending = false
        val stack = stackOf(host, create = false) ?: return
        for (layer in stack.layers.toList()) {
            val panel = layer.root ?: continue
            if (panel.parent != null) continue
            if (layer.closing) {
                finish(layer)
                continue
            }
            host.addComponent(panel).inTL(0f, 0f)
        }
        raiseToasts(host, stack)
    }

    /** A layer's panel keeps a little opacity while it opens: vanilla gives no input to a fully transparent panel. */
    private const val MIN_OPEN_OPACITY = 0.01f
}
