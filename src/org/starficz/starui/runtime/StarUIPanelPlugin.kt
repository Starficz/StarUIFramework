package org.starficz.starui.runtime

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.*
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import org.starficz.starui.*
import org.starficz.starui.layout.LayoutEngine
import org.starficz.starui.layout.NodeSize
import org.starficz.starui.layout.ViewportLayoutEngine
import org.starficz.starui.modifier.PaddingValues
import org.starficz.starui.vanilla.*

internal data class SlotBounds(
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float
)

/**
 * StarUI's side of one component: its event handlers, its render and advance callbacks, and for a container its
 * layout and reactive scope. A CustomPanel that StarUI made, or one a modifier was applied to, has one as its
 * plugin; a leaf (a label, a button) gets one when a modifier needs it.
 *
 * It is the receiver of the lifecycle and drawing modifiers (`Modifier.onRender { }`, `onLayout { }`, ...), so the
 * component's size and position are at hand there: [width], [height], [left], [top], and so on. Reach it directly
 * with `Modifier.plugin { }` or [getAttached].
 */
class StarUIPanelPlugin internal constructor() : BaseCustomUIPanelPlugin() {

    companion object {
        private var serialCounter = 0L

        /** False until the first StarUI plugin exists, so hooks on vanilla code can leave early. */
        @Volatile
        internal var hasAnyPlugins = false

        /**
         * Counts layout passes (one per [runLayout] that measures). A node's measure result is reused within one pass
         * when it is asked again with the same room ([measure]): every container level would otherwise measure its
         * whole subtree again (a Box measures its children once more when it lays them out, a Column or Row measures
         * cross-filling children until their size settles, a scrolling container when its content outgrows it),
         * which multiplies with nesting.
         */
        internal var layoutPass = 0L

        /** Debugging: the next this-many [requestLayout] calls log a stack trace. */
        @JvmField
        internal var traceLayoutRequests = 0

        /** onAdvance handlers registered on leaves (see AdvanceList). */
        @JvmField
        internal var leafAdvanceHandlers = 0

        /** Number of [requestLayout] calls so far. For tests. */
        @JvmField
        internal var layoutRequests = 0L

        /** A number larger than the serial of every plugin created so far. */
        internal fun nextSerial(): Long = ++serialCounter

        /** True once a component that is not a CustomPanel has a plugin, so hooks on leaves can leave early until then. */
        @Volatile
        internal var hasAttachedPlugins = false

        /**
         * Returns [component]'s plugin: a CustomPanel's own, or the one a leaf got from a modifier. `null` when it has
         * none.
         *
         * @param component the component, or its box
         */
        fun getAttached(component: UIComponentAPI): StarUIPanelPlugin? {
            val target = if (component is BoxedUIElement) component.vanillaComponent else component
            if (target is CustomPanelAPI) return target.plugin as? StarUIPanelPlugin
            if (!hasAttachedPlugins) return null
            return NodeStore.leafPlugin(target)
        }

        /**
         * Returns [component]'s plugin, made when it has none. It lives as long as the component. On a CustomPanel that
         * has a plugin of its own (vanilla's or another mod's), the new plugin wraps it as its [delegate].
         *
         * @param component the component, or its box
         */
        fun getOrCreate(component: UIComponentAPI): StarUIPanelPlugin {
            val target = if (component is BoxedUIElement) component.vanillaComponent else component
            if (target is CustomPanelAPI) {
                val existing = target.plugin
                if (existing is StarUIPanelPlugin) return existing
                val created = StarUIPanelPlugin()
                created.component = target
                // A CustomPanel has one plugin slot. Wrap a foreign plugin (vanilla's or another mod's) instead of
                // replacing it, so modifiers on someone else's panel never switch its own behaviour off.
                created.delegate = existing
                // a node recorded for the panel before it had a StarUI plugin stays its node
                NodeStore.lookup(target)?.let { created.adoptNode(it) }
                target.setPlugin(created)
                return created
            }
            val node = NodeStore.nodeFor(target)
            node.plugin?.let { return it }
            hasAttachedPlugins = true
            return StarUIPanelPlugin().also {
                it.component = target
                it.consumeEvents = false
                NodeStore.attachLeafPlugin(target, node, it)
            }
        }
    }

    /** The component this is the plugin of. */
    lateinit var component: UIComponentAPI
        internal set

    /** The width the component is drawn at: its own, or the width of the layout slot it is placed in. */
    val width: Float get() = slotBounds?.width ?: component.width
    /** The height the component is drawn at: its own, or the height of the layout slot it is placed in. */
    val height: Float get() = slotBounds?.height ?: component.height
    /** The x of the component's left edge, in screen coordinates. */
    val x: Float get() = component.x + (slotBounds?.offsetX ?: 0f)
    /** The y of the component's bottom edge, in screen coordinates (Starsector's y points up). */
    val y: Float get() = component.y + (slotBounds?.offsetY ?: 0f)
    /** The x of the component's left edge: [x]. */
    val left: Float get() = x
    /** The y of the component's bottom edge: [y]. */
    val bottom: Float get() = y
    /** The y of the component's top edge. */
    val top: Float get() = y + height
    /** The x of the component's right edge. */
    val right: Float get() = x + width
    /** The x of the component's centre. */
    val centerX: Float get() = x + width / 2f
    /** The y of the component's centre. */
    val centerY: Float get() = y + height / 2f

    /** Creation order, so a scope can tell plugins that existed before its build from ones the build created. */
    private val serial = nextSerial()

    init {
        hasAnyPlugins = true
    }

    /** The builder block that fills this container, kept so it can run again when a state it read changes. */
    internal var scope: Scope? = null

    /** The StarUI component this is ("Column", "Spacer", ...), for layout dumps and for matching on rebuild. */
    internal var kind: String? = null

    /** A scope at or below this node is waiting to be rebuilt. */
    internal var dirtyScopeBelow = false

    /** Whether [dispose] has run: the handlers are gone and the scope will never rebuild. */
    var isDisposed = false
        private set

    /**
     * The plugin that was on this CustomPanel before StarUI attached, if any. Every callback is forwarded to it:
     * drawing behind the content happens before it, drawing in front after it, and it sees input before StarUI's
     * handlers.
     */
    var delegate: CustomUIPanelPlugin? = null
        internal set

    /** This container's panel (a CustomPanel's plugin only). */
    internal var customPanel: CustomPanelAPI
        get() = component as CustomPanelAPI
        set(value) {
            component = value
        }

    /** This container's panel (a panel's plugin only). */
    internal val panel: UIPanelAPI
        get() = component as UIPanelAPI

    // ==========================================
    // ENGINE & SELF STATE
    // ==========================================
    internal var layoutEngine: LayoutEngine? = null
    internal var isTopLevelNode: Boolean = false
    /**
     * On a container: its children that were culled in the last render also skip their advance (their onAdvance and
     * vanilla animations pause while off screen). Off by default; lazy lists turn it on.
     */
    @JvmField
    internal var pausesOffscreenAdvance = false

    /** On a container: one of its leaf children has had onAdvance handlers (the advance hook looks only then). */
    @JvmField
    internal var hasLeafAdvancers = false

    /** This panel was culled (not drawn) in the last render: it skips input (and advance, see above) until drawn again. */
    @JvmField
    internal var culled = false

    /** True while a layout engine swaps one of this panel's children itself: the change needs no new layout. */
    internal var swappingChild = false
    private var hasLayoutRun = false

    /** Number of measure + layout passes this node's own engine has started (as a layout root). For tests and tuning. */
    internal var layoutPassCount = 0
    /** Free for your own use; StarUI never reads it. Cleared when a builder that runs again reuses the component. */
    var customData: Any? = null

    /** Set on the container an [org.starficz.starui.inject] call made: its key in the host panel's injections. */
    internal var injectionKey: String? = null
    /**
     * A root that measures its wrapping axes in the screen's room, not its parent's: content that sizes a vanilla
     * dialog around itself ([Modifier.fitVanillaDialog][org.starficz.starui.modifier.fitVanillaDialog]), whose panel
     * starts at whatever size vanilla made it.
     */
    internal var measuresInScreenRoom = false

    // Used when a leaf component is placed inside a larger padded/aligned layout slot.
    internal var slotBounds: SlotBounds? = null

    private var ownNode: UINode? = null

    /**
     * This component's node (its size rule, padding, margin, anchor, ...). A CustomPanel's plugin holds it; a leaf's
     * plugin sits on the node the component's store holds (see [NodeStore]).
     */
    internal val node: UINode
        get() = ownNode ?: UINode().also { adoptNode(it) }

    internal fun adoptNode(node: UINode) {
        ownNode = node
        node.plugin = this
    }

    // ==========================================
    // OVERFLOW
    // ==========================================
    /** This container's `overflow` argument. */
    internal var overflow: Overflow = Overflow.Visible
    /** On a scrolling container: the vanilla scroll panel it sits in (the node its parent lays out). */
    internal var scrollHost: ScrollPanelAPI? = null
    /** On a scroll host's plugin: the StarUI container inside it. */
    internal var scrollContent: StarUIPanelPlugin? = null
    /** On a scroll host's plugin: whether it takes the mouse wheel (the theme's `useMouseWheel`). */
    internal var wheelEnabled = true
    /** The [ClipStack] depth to restore after the children rendered, or -1 when nothing was pushed. */
    private var clipRestoreDepth = -1

    private fun pushClip() {
        if (!overflow.clips) return
        val rect = (scrollHost ?: component).position
        val x = rect.x
        val y = rect.y
        val clipsX = overflow.horizontal != AxisOverflow.Visible
        val clipsY = overflow.vertical != AxisOverflow.Visible
        val l = if (clipsX) x else -Float.MAX_VALUE
        val b = if (clipsY) y else -Float.MAX_VALUE
        val r = if (clipsX) x + rect.width else Float.MAX_VALUE
        val t = if (clipsY) y + rect.height else Float.MAX_VALUE
        clipRestoreDepth = ClipStack.push(l, b, r, t)
        // Clip on both axes is vanilla's own scissor (setClipping), scrolling is the scroll panel's; one axis is ours.
        if (scrollHost == null && clipsX != clipsY) {
            ClipStack.pushScissor(this, l, b, r, t)
            ownsScissor = true
        }
    }

    private var ownsScissor = false

    private fun popClip() {
        if (clipRestoreDepth < 0) return
        if (ownsScissor) {
            ClipStack.popScissor()
            ownsScissor = false
        }
        ClipStack.restore(clipRestoreDepth)
        clipRestoreDepth = -1
    }

    // ==========================================
    // CHILDREN
    // ==========================================
    /** Set while this container's builder runs again: the old children its calls may reuse. */
    internal var reuseCursor: ReuseCursor? = null

    // ==========================================
    // EVENTS & LIFECYCLE CALLBACKS
    // ==========================================
    private val activeBindings = mutableListOf<Pair<UIState<*>, Any>>()
    private var onPositionChangedFunctions: MutableList<(PositionAPI) -> Unit> = mutableListOf()

    private var onClickFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
    private var onClickOutsideFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
    private var onClickReleaseFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
    private var onHoverFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onHoverEnterFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onHoverExitFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onMouseButtonHeldFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onKeyDownFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onKeyUpFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onKeyHeldFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()

    private var renderBelowFunctions: MutableList<(Float) -> Unit> = mutableListOf()
    private var renderFunctions: MutableList<(Float) -> Unit> = mutableListOf()

    private var advanceFunctions: MutableList<(Float) -> Unit> = AdvanceList()

    /**
     * Counts onAdvance handlers on leaves (labels, buttons: components that are not CustomPanels), so the advance hook
     * on every vanilla component can leave without a plugin lookup while there are none.
     */
    private inner class AdvanceList : ArrayList<(Float) -> Unit>() {
        private fun isLeaf() = this@StarUIPanelPlugin::component.isInitialized && component !is CustomPanelAPI
        override fun add(element: (Float) -> Unit): Boolean = super.add(element).also {
            if (isLeaf()) {
                leafAdvanceHandlers++
                ((component.parent as? CustomPanelAPI)?.plugin as? StarUIPanelPlugin)?.hasLeafAdvancers = true
            }
        }
        override fun remove(element: (Float) -> Unit): Boolean = super.remove(element).also { if (it && isLeaf()) leafAdvanceHandlers-- }
        override fun clear() {
            if (isLeaf()) leafAdvanceHandlers -= size
            super.clear()
        }
    }
    private var onLayoutFunctions: MutableList<() -> Unit> = mutableListOf()
    private var onFirstLayoutFunctions: MutableList<() -> Unit> = mutableListOf()
    // Run by the PatchLib hooks when a native control's value changes (text field text, checked state, slider
    // progress), whoever changed it: the player or any code.
    private var onValueChangedFunctions: MutableList<() -> Unit> = mutableListOf()

    internal var inputCaptureTopPad = 0f
        private set
    internal var inputCaptureBottomPad = 0f
        private set
    internal var inputCaptureLeftPad = 0f
        private set
    internal var inputCaptureRightPad = 0f
        private set

    /** Whether the cursor is over the component, as of the last mouse event it saw. */
    var isHovering = false
        private set

    internal var hasClicked = false
        private set

    /**
     * Whether the component consumes the mouse events over it and the key events that reach it, so what is behind it
     * does not see them. Off by default: events pass through.
     */
    var consumeEvents = false

    private var inProcessInput = false

    /**
     * Events handled by the previous processInput call, by identity. Vanilla processInput(List) copies the caller's
     * events into the engine's internal list and calls processInput(internal) on the same object; the lifecycle
     * patch sees both overloads, so the same event objects arrive twice. The engine creates new event objects on
     * every poll, so an event already seen here is the same dispatch, not a new one.
     */
    private var lastHandledEvents: MutableSet<InputEventAPI> = identitySet()
    /** The set the next call fills, swapped with [lastHandledEvents] (two kept sets, nothing made per call). */
    private var spareHandledEvents: MutableSet<InputEventAPI> = identitySet()

    private fun identitySet(): MutableSet<InputEventAPI> =
        java.util.Collections.newSetFromMap(java.util.IdentityHashMap<InputEventAPI, Boolean>())

    internal fun runLayout() {
        // A node its parent lays out never lays itself out: it does not know the room its parent gives it (measuring
        // with the parent's whole size gave a Column in a Row the Row's width, not its share). A request reaches the
        // top-level node or a layout boundary, which lays this one out on the next frame.
        if (!isTopLevelNode && (layoutEngine as? ViewportLayoutEngine)?.isLayoutBoundary != true) return
        if (isTopLevelNode) {
            followParentSize()
            // an injection filling a vanilla panel that has no size yet (sized after the hook ran): nothing to fill, and
            // a layout now would measure the content in a 0 room (warnings, a throw). It waits for the size. Any other
            // root filling a sizeless parent is a mistake, and still throws
            if (waitsForParentSize()) return
        }
        if (!hasLayoutRun) {
            // Resolved before anything runs: a requestLayout() made by a layout callback (a label that changes its
            // own text in onLayout, say) must survive this pass and schedule the next one.
            hasLayoutRun = true
            try {
                runEngine()
            } catch (t: Throwable) {
                hasLayoutRun = false   // a broken layout keeps failing loudly instead of silently staying wrong
                throw t
            }
            fireLayoutCallbacks()
        }
    }

    private fun runEngine() {
        val viewport = layoutEngine as? ViewportLayoutEngine
        if (viewport != null && viewport.isLayoutBoundary && !isTopLevelNode) {
            // a layout boundary asked to lay out its content again: its own size is what its parent gave it
            layoutPassCount++
            layoutPass++
            viewport.layout(component.width, component.height)
            return
        }
        if (layoutEngine == null && isTopLevelNode && scope != null) {
            // a container that leaves its children alone (CustomPanel) as a root: it still sizes and places itself,
            // exact or filling its parent (its size never comes from its children); they lay themselves out against it
            layoutPassCount++
            layoutPass++
            val p = component.parent
            val w = when (val d = node.size.width) {
                is Axis.Exact -> d.pixels
                is Axis.FillMax -> if (p != null && p.width > 0f) p.width else component.width
                else -> component.width
            }
            val h = when (val d = node.size.height) {
                is Axis.Exact -> d.pixels
                is Axis.FillMax -> if (p != null && p.height > 0f) p.height else component.height
                else -> component.height
            }
            if (w != component.width || h != component.height) component.setSize(w, h)
            node.anchor?.let { component.applyAnchor(it) }
            return
        }
        layoutEngine?.let { engine ->
            layoutPassCount++
            layoutPass++
            // A top-level root's room is its own size where that is exact; otherwise its parent's (a Holo's content
            // is drawn by the holo and has no real parent to measure)
            val p = component.parent
            val screen = measuresInScreenRoom && isTopLevelNode
            val rootMaxWidth = (node.size.width as? Axis.Exact)?.pixels?.takeIf { isTopLevelNode }
                ?: if (screen) Global.getSettings().screenWidth
                else if (p != null && p.width > 0f) p.width else Float.MAX_VALUE
            val rootMaxHeight = (node.size.height as? Axis.Exact)?.pixels?.takeIf { isTopLevelNode }
                ?: if (screen) Global.getSettings().screenHeight
                else if (p != null && p.height > 0f) p.height else Float.MAX_VALUE

            // 1. Measure the children's intrinsic footprint
            val (contentW, contentH) = engine.measure(maxWidth = rootMaxWidth, maxHeight = rootMaxHeight)

            // 2. SELF SIZING: If this panel is a top-level root, size itself right now! The engine's measure already
            // includes this container's own padding, as it does for every container (measureChildPaddedIntrinsic)
            if (isTopLevelNode) {
                val finalW = when (val wDim = node.size.width) {
                    is Axis.Exact -> wDim.pixels
                    is Axis.WrapContent -> contentW
                    // fill: the parent's size where it has one (a vanilla panel sized after the UI was added)
                    else -> if (p != null && p.width > 0f) p.width else if (component.width > 1f) component.width else rootMaxWidth
                }
                val finalH = when (val hDim = node.size.height) {
                    is Axis.Exact -> hDim.pixels
                    is Axis.WrapContent -> contentH
                    else -> if (p != null && p.height > 0f) p.height else if (component.height > 1f) component.height else rootMaxHeight
                }
                component.setSize(finalW, finalH)
                node.anchor?.let { component.applyAnchor(it) }
            }

            // 3. Layout the children within our finalized boundaries
            engine.layout(component.width, component.height)
        }
    }

    private fun waitsForParentSize(): Boolean {
        if (injectionKey == null) return false
        val p = component.parent ?: return false
        return (node.size.width is Axis.FillMax && p.width <= 0f) || (node.size.height is Axis.FillMax && p.height <= 0f)
    }

    private var parentW = Float.NaN
    private var parentH = Float.NaN

    /**
     * A top-level node that fills its parent lays out again when the parent's size changes: a vanilla panel sized or
     * resized after StarUI's UI was added to it (an injection from a constructor hook). One size read per frame.
     */
    private fun followParentSize() {
        val p = component.parent ?: return
        val w = p.width
        val h = p.height
        if (w == parentW && h == parentH) return
        val first = parentW.isNaN()
        parentW = w
        parentH = h
        if (!first && (node.size.width is Axis.FillMax || node.size.height is Axis.FillMax)) hasLayoutRun = false
    }

    private var measuredPass = -1L
    private var measuredW = 0f
    private var measuredH = 0f
    private var measuredSize: NodeSize? = null
    private var measuredPadding: PaddingValues? = null
    private var measuredResult: Pair<Float, Float>? = null

    /** The size this node was last laid out at by its parent; NaN before that. */
    internal var laidOutW = Float.NaN
    internal var laidOutH = Float.NaN

    /** True when nothing at or below this node asked for a layout since it was last laid out. */
    internal val isLayoutClean: Boolean get() = hasLayoutRun

    /**
     * This node's engine's measure, reused when it is asked again with the same room, node size and padding, either
     * within one layout pass (see [layoutPass]) or, across passes, while nothing at or below it asked for a layout
     * ([isLayoutClean]): a change in one row of a long list then measures that row, not every row. The engine keeps
     * what its last measure found for its layout, and the result is only reused while that last measure matches.
     */
    internal fun measure(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        val engine = layoutEngine ?: return Pair(component.width, component.height)
        val cached = measuredResult
        if (cached != null && (measuredPass == layoutPass || hasLayoutRun) && measuredW == maxWidth &&
            measuredH == maxHeight && measuredSize == node.size && measuredPadding == node.padding) return cached
        val result = engine.measure(maxWidth, maxHeight)
        measuredPass = layoutPass
        measuredW = maxWidth
        measuredH = maxHeight
        measuredSize = node.size
        measuredPadding = node.padding
        measuredResult = result
        return result
    }

    /** Forgets the cached measure. */
    internal fun forgetMeasure() {
        measuredResult = null
    }

    /** Called by the parent's engine once it has sized and laid out this node. */
    internal fun markLayoutResolved() {
        hasLayoutRun = true
        fireLayoutCallbacks()
    }

    /**
     * Called by the parent's engine before it lays this node out: from here on a layout request (one made during this
     * node's own layout, say) is a new one and survives, as in [runLayout]. Follow with [finishLayout].
     */
    internal fun beginLayout() {
        hasLayoutRun = true
    }

    /** After the layout [beginLayout] started: the layout callbacks. */
    internal fun finishLayout() = fireLayoutCallbacks()

    private var hasEverLaidOut = false

    private fun fireLayoutCallbacks() {
        hasEverLaidOut = true
        if (onFirstLayoutFunctions.isNotEmpty()) {
            val first = onFirstLayoutFunctions.toList()
            onFirstLayoutFunctions.clear()
            first.forEach { it() }
        }
        if (onLayoutFunctions.isNotEmpty()) onLayoutFunctions.toList().forEach { it() }
    }

    /**
     * Asks for this component to be measured and laid out again on the next frame it renders. The request travels up
     * through every StarUI layout container to the top-level one, which measures its whole tree again, resizes itself
     * and places itself again.
     *
     * StarUI calls this by itself when children are added to or removed from a layout container and when a
     * [Text][org.starficz.starui.components.Text] changes its text. Call it after changing something StarUI cannot see,
     * such as a child's size set by hand. It only sets flags, so it is cheap, and safe to call from event handlers.
     */
    fun requestLayout() {
        layoutRequests++
        if (traceLayoutRequests > 0) {
            traceLayoutRequests--
            org.apache.log4j.Logger.getLogger(StarUIPanelPlugin::class.java)
                .info("StarUI layout request from ${kind ?: component.javaClass.simpleName}", Throwable("trace"))
        }
        var node = this
        while (true) {
            node.hasLayoutRun = false
            if (!node::component.isInitialized) return
            val parentPanel = node.component.parent ?: return
            val parentPlugin = getAttached(parentPanel) ?: return
            if (parentPlugin.layoutEngine == null) return
            node = parentPlugin
            // a scrolling container whose size does not follow its content lays its content out by itself
            if ((node.layoutEngine as? ViewportLayoutEngine)?.isLayoutBoundary == true && !node.isTopLevelNode) {
                node.hasLayoutRun = false
                return
            }
        }
    }

    internal fun registerBinding(state: UIState<*>, binding: Any) {
        val entry = Pair(state, binding)
        activeBindings.add(entry)
        owningScope()?.addCleanup {
            activeBindings.remove(entry)
            state.removeBinding(binding)
        }
    }

    // ==========================================
    // SCOPES & DISPOSAL
    // ==========================================

    /**
     * Makes [builder] this container's scope and runs it for the first time. [kind] and [source] (the caller's own
     * builder lambda) are only used to name the scope in error messages.
     */
    internal fun attachScope(kind: String, source: Any, builder: () -> Unit) {
        this.kind = kind
        val declaredIn = source.javaClass.name.substringBefore("$$")
        val existing = scope
        if (existing != null && !existing.disposed) {
            // A reused container: the enclosing builder ran again, so this block is a new lambda that may have
            // captured new values. Run it, reusing this container's own children in turn.
            existing.adopt(StarUIContext.currentTheme, kind, declaredIn, builder)
            existing.rebuild()
            return
        }
        val created = Scope(this, StarUIContext.currentTheme, kind, declaredIn, builder)
        scope = created
        created.build()
    }

    /** A tooltip from Modifier.tooltip is on this component; [tooltipAppliedThisBuild]: the current build applied it. */
    private var hasStarUITooltip = false
    internal var tooltipAppliedThisBuild = false
        private set

    internal fun noteTooltipApplied() {
        hasStarUITooltip = true
        tooltipAppliedThisBuild = true
    }

    /**
     * Called after a rebuild that reused this component: a tooltip its new call did not ask for again is removed.
     * Not done in [resetForReuse], before the build: most rebuilds apply the same tooltip again, and taking it off
     * first would close it under the cursor on every rebuild.
     */
    internal fun dropTooltipIfNotReapplied() {
        if (!hasStarUITooltip || tooltipAppliedThisBuild) return
        hasStarUITooltip = false
        component.removeTooltip()
    }

    /**
     * Back to a blank plugin before the component function that made this component configures it again: every
     * handler, binding and flag the previous build set through modifiers is dropped, then the function re-applies
     * the current ones. What the engine or the user's input owns (hover, pressed state) is kept.
     */
    internal fun resetForReuse() {
        tooltipAppliedThisBuild = false
        scope?.runCleanups()
        activeBindings.forEach { (state, binding) -> state.removeBinding(binding) }
        activeBindings.clear()
        handlerLists().forEach { it.clear() }
        if (inputCaptureTopPad != 0f || inputCaptureBottomPad != 0f || inputCaptureLeftPad != 0f || inputCaptureRightPad != 0f) {
            setMouseCapturePad(0f, 0f, 0f, 0f)
        }
        consumeEvents = false
        customData = null
        slotBounds = null
    }

    /**
     * The scope that must undo a registration made on this plugin right now, or null. That is the scope being built,
     * when this plugin outlives that build: the scope's own container, or any plugin that existed before the build
     * started. Plugins created by the build are thrown away with it, so they need no bookkeeping.
     */
    private fun owningScope(): Scope? {
        val building = StarUIContext.currentScope ?: return null
        return if (building.plugin === this || serial < building.buildSerial) building else null
    }

    private fun <F> register(list: MutableList<F>, function: F) {
        list.add(function)
        owningScope()?.addCleanup { list.remove(function) }
    }

    internal fun markDirtyScopeBelow() {
        var node: StarUIPanelPlugin? = this
        while (node != null) {
            node.dirtyScopeBelow = true
            if (!node::component.isInitialized) return
            node = node.component.parent?.let { getAttached(it) }
        }
    }


    /**
     * Components this one draws itself but that are not its children in the UI tree (a Holo's content): disposed with
     * it, since walking the tree never reaches them.
     */
    internal val ownedOutsideTree = ArrayList<UIComponentAPI>()

    /** Disposes this plugin only; [dispose] also handles everything below it. */
    internal fun disposeSelf() {
        if (isDisposed) return
        isDisposed = true
        for (owned in ownedOutsideTree) Scope.disposeTree(owned)
        ownedOutsideTree.clear()
        scope?.dispose()
        scope = null
        dirtyScopeBelow = false
        activeBindings.forEach { (state, binding) -> state.removeBinding(binding) }
        activeBindings.clear()
        handlerLists().forEach { it.clear() }
    }

    /**
     * Tears down the StarUI side of this component and of everything below it: the event handlers, the render and
     * advance callbacks, the [UIState][org.starficz.starui.UIState] listeners and the reactive scopes.
     *
     * Use it when you remove UI for good. StarUI does it by itself for children it rebuilds and for children dropped by
     * `clearChildren()`. A component removed with `removeComponent` is left alone, so it can be added somewhere else.
     */
    fun dispose() {
        if (::component.isInitialized) Scope.disposeTree(component) else disposeSelf()
    }

    /** The non-empty handler lists by name, e.g. "onClick, onRenderBelow x2". */
    internal fun describeHandlers(): String {
        val names = listOf(
            "onPositionChanged", "onClick", "onClickOutside", "onClickRelease", "onHover", "onHoverEnter", "onHoverExit",
            "onMouseHeld", "onKeyDown", "onKeyUp", "onKeyHeld", "onRenderBelow", "onRender", "onAdvance", "onLayout", "onFirstLayout",
            "onValueChanged"
        )
        return handlerLists().mapIndexedNotNull { i, list ->
            when (list.size) { 0 -> null; 1 -> names[i]; else -> names[i] + " x" + list.size }
        }.joinToString(", ")
    }

    private fun handlerLists(): List<MutableList<*>> = listOf(
        onPositionChangedFunctions, onClickFunctions, onClickOutsideFunctions, onClickReleaseFunctions,
        onHoverFunctions, onHoverEnterFunctions, onHoverExitFunctions, onMouseButtonHeldFunctions,
        onKeyDownFunctions, onKeyUpFunctions, onKeyHeldFunctions, renderBelowFunctions, renderFunctions,
        advanceFunctions, onLayoutFunctions, onFirstLayoutFunctions, onValueChangedFunctions
    )

    /** [function] runs whenever this native control's value changes (see [onValueChangedFunctions]). */
    internal fun onNativeValueChanged(function: () -> Unit) { register(onValueChangedFunctions, function) }
    internal fun nativeValueChanged() {
        if (onValueChangedFunctions.isEmpty()) return
        onValueChangedFunctions.toList().forEach { it() }
    }

    /**
     * Calls [function] when the component moves or changes size, with its new position. The modifier form is
     * [Modifier.onPositionChanged][org.starficz.starui.modifier.onPositionChanged].
     */
    fun onPositionChanged(function: (PositionAPI) -> Unit) { register(onPositionChangedFunctions, function) }
    override fun positionChanged(position: PositionAPI) {
        super.positionChanged(position)
        delegate?.positionChanged(position)
        onPositionChangedFunctions.forEach { it(position) }
    }

    override fun buttonPressed(buttonId: Any?) {
        delegate?.buttonPressed(buttonId)
    }

    internal fun setMouseCapturePad(leftPad: Float, rightPad: Float, topPad: Float, bottomPad: Float) {
        inputCaptureTopPad = topPad
        inputCaptureBottomPad = bottomPad
        inputCaptureLeftPad = leftPad
        inputCaptureRightPad = rightPad
        if (::component.isInitialized && component !is LabelAPI) {
            component.setMouseOverPad(leftPad, rightPad, topPad, bottomPad)
        }
    }

    /**
     * Calls [function] every frame, to draw behind the component's content. The modifier form is
     * [Modifier.onRenderBelow][org.starficz.starui.modifier.onRenderBelow].
     */
    fun onRenderBelow(function: (alphaMult: Float) -> Unit) { register(renderBelowFunctions, function) }
    override fun renderBelow(alphaMult: Float) {
        // Before this node's children render and before any layout below runs: the safe point to replace children.
        if (Scope.hasPendingDetached) Scope.disposeDetachedPending()
        HotReloadRefresh.poll()
        DebugDump.poll()
        if (dirtyScopeBelow) Scope.flush(this)
        runLayout()

        renderBelowFunctions.forEach {
            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_CULL_FACE)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            it(alphaMult)
            GL11.glPopMatrix()
        }
        delegate?.renderBelow(alphaMult)
        // last: the children render next, and only they are culled against this container's visible rectangle
        pushClip()
    }

    /**
     * Calls [function] after every layout pass that sizes and positions the component. The modifier form is
     * [Modifier.onLayout][org.starficz.starui.modifier.onLayout].
     */
    fun onLayout(function: () -> Unit) { register(onLayoutFunctions, function) }

    /**
     * Calls [function] once, after the first layout pass that sizes and positions the component. The modifier form is
     * [Modifier.onFirstLayout][org.starficz.starui.modifier.onFirstLayout].
     */
    fun onFirstLayout(function: () -> Unit) {
        if (hasEverLaidOut) return   // a reused component had its first layout long ago
        register(onFirstLayoutFunctions, function)
    }
    /**
     * Calls [function] every frame, to draw in front of the component's content. The modifier form is
     * [Modifier.onRender][org.starficz.starui.modifier.onRender].
     */
    fun onRender(function: (alphaMult: Float) -> Unit) { register(renderFunctions, function) }
    override fun render(alphaMult: Float) {
        popClip()
        runLayout()
        delegate?.render(alphaMult)

        renderFunctions.forEach {
            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_CULL_FACE)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            it(alphaMult)
            GL11.glPopMatrix()
        }
    }

    /**
     * Calls [function] every frame, with the seconds since the last one. The modifier form is
     * [Modifier.onAdvance][org.starficz.starui.modifier.onAdvance].
     */
    fun onAdvance(function: (amount: Float) -> Unit) { register(advanceFunctions, function) }
    override fun advance(amount: Float) {
        delegate?.advance(amount)
        // by index, and only while not disposed: a callback may dispose this UI (disposing clears the list)
        var i = 0
        while (i < advanceFunctions.size && !isDisposed) {
            advanceFunctions[i](amount)
            i++
        }
        // This node's children have advanced already (vanilla advances children before the plugin), so they can be
        // replaced now. When a StarUI parent is also marked it will flush everything below it in a moment.
        // Not when detached: a callback above may just have removed this node (a vanilla panel rebuilding itself).
        if (!dirtyScopeBelow) return
        val parent = component.parent ?: return
        if (getAttached(parent)?.dirtyScopeBelow != true) Scope.flush(this)
    }

    override fun processInput(events: MutableList<InputEventAPI>) {
        delegate?.processInput(events)
        if (inProcessInput) return
        inProcessInput = true
        try {
            val alreadyHandled = lastHandledEvents
            val handledNow = spareHandledEvents
            handledNow.clear()
            handledNow.addAll(events)
            lastHandledEvents = handledNow
            spareHandledEvents = alreadyHandled

            // Consumed events are skipped: the engine's InputEvent throws "Attempt to access data of a consumed
            // InputEvent" from getX/getY/getEventType, and a patched leaf can still see events it or an earlier
            // sibling consumed (the parent purges them only after dispatching to every child).

            for (event in events) {
                if (event in alreadyHandled || !event.isMouseEvent || event.isConsumed) continue
                val inElement = event.x.toFloat() in (left - inputCaptureLeftPad)..(right + inputCaptureRightPad) &&
                        event.y.toFloat() in (bottom - inputCaptureBottomPad)..(top + inputCaptureTopPad)
                if (inElement) {
                    onHoverFunctions.forEach { it(event) }
                    if (!isHovering) onHoverEnterFunctions.forEach { it(event) }
                    isHovering = true
                    if (event.isMouseDownEvent) {
                        hasClicked = true
                        onClickFunctions.forEach { it(event) }
                    }
                    if (event.isMouseUpEvent && hasClicked) {
                        hasClicked = false
                        onClickReleaseFunctions.forEach { it(event) }
                    }
                    if (Mouse.isButtonDown(0)) onMouseButtonHeldFunctions.forEach { it(event) }
                    if (consumeEvents) event.consume()
                } else {
                    if (isHovering) onHoverExitFunctions.forEach { it(event) }
                    isHovering = false
                    if (event.isMouseDownEvent) {
                        onClickOutsideFunctions.forEach { it(event) }
                    }
                    if (event.isMouseUpEvent) {
                        hasClicked = false
                    }
                }
            }

            for (event in events) {
                if (event in alreadyHandled || !event.isKeyboardEvent || event.isConsumed) continue
                if (event.isKeyDownEvent) onKeyDownFunctions.forEach { it(event) }
                if (event.isKeyUpEvent) onKeyUpFunctions.forEach { it(event) }
                if (!event.isConsumed && event.isRepeat) onKeyHeldFunctions.forEach { it(event) }
                if (consumeEvents) event.consume()
            }
        } finally {
            inProcessInput = false
        }
    }

    /**
     * Calls [function] when a mouse button is pressed over the component. The modifier form is
     * [Modifier.onClick][org.starficz.starui.modifier.onClick].
     */
    fun onClick(function: (InputEventAPI) -> Unit) { register(onClickFunctions, function) }
    /**
     * Calls [function] when the mouse button is released over the component after a press on it. The modifier form is
     * [Modifier.onClickRelease][org.starficz.starui.modifier.onClickRelease].
     */
    fun onClickRelease(function: (InputEventAPI) -> Unit) { register(onClickReleaseFunctions, function) }
    /**
     * Calls [function] when a mouse button is pressed outside the component. The modifier form is
     * [Modifier.onClickOutside][org.starficz.starui.modifier.onClickOutside].
     */
    fun onClickOutside(function: (InputEventAPI) -> Unit) { register(onClickOutsideFunctions, function) }
    /**
     * Calls [function] on every mouse event while the cursor is over the component. The modifier form is
     * [Modifier.onHover][org.starficz.starui.modifier.onHover].
     */
    fun onHover(function: (InputEventAPI) -> Unit) { register(onHoverFunctions, function) }
    /**
     * Calls [function] when the cursor moves onto the component. The modifier form is
     * [Modifier.onHoverEnter][org.starficz.starui.modifier.onHoverEnter].
     */
    fun onHoverEnter(function: (InputEventAPI) -> Unit) { register(onHoverEnterFunctions, function) }
    /**
     * Calls [function] when the cursor leaves the component. The modifier form is
     * [Modifier.onHoverExit][org.starficz.starui.modifier.onHoverExit].
     */
    fun onHoverExit(function: (InputEventAPI) -> Unit) { register(onHoverExitFunctions, function) }
    /**
     * Calls [function] on every mouse event over the component while the left button is held. The modifier form is
     * [Modifier.onMouseHeld][org.starficz.starui.modifier.onMouseHeld].
     */
    fun onMouseHeld(function: (InputEventAPI) -> Unit) { register(onMouseButtonHeldFunctions, function) }
    /**
     * Calls [function] when a key is pressed. The modifier form is
     * [Modifier.onKeyDown][org.starficz.starui.modifier.onKeyDown].
     */
    fun onKeyDown(function: (InputEventAPI) -> Unit) { register(onKeyDownFunctions, function) }
    /**
     * Calls [function] when a key is released. The modifier form is
     * [Modifier.onKeyUp][org.starficz.starui.modifier.onKeyUp].
     */
    fun onKeyUp(function: (InputEventAPI) -> Unit) { register(onKeyUpFunctions, function) }
    /**
     * Calls [function] on each key repeat while a key is held. The modifier form is
     * [Modifier.onKeyHeld][org.starficz.starui.modifier.onKeyHeld].
     */
    fun onKeyHeld(function: (InputEventAPI) -> Unit) { register(onKeyHeldFunctions, function) }
}
