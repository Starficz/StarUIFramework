package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import org.starficz.starui.compose.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.ScrollPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import org.starficz.starui.compose.AxisOverflow
import org.starficz.starui.compose.Overflow
import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.layout.LayoutEngine
import org.starficz.starui.compose.layout.NodeSize
import org.starficz.starui.compose.layout.ViewportLayoutEngine
import org.starficz.starui.compose.modifier.Margin
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.BoxedUIElement
import org.starficz.starui.core.applyAnchor
import org.starficz.starui.core.removeTooltip
import org.starficz.starui.core.bottom
import org.starficz.starui.core.getChildrenNonCopy
import org.starficz.starui.core.height
import org.starficz.starui.core.left
import org.starficz.starui.core.right
import org.starficz.starui.core.setMouseOverPad
import org.starficz.starui.core.setPlugin
import org.starficz.starui.core.setSize
import org.starficz.starui.core.top
import org.starficz.starui.core.width
import java.util.WeakHashMap

internal data class SlotBounds(
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float
)

class StarUIPanelPlugin() : BaseCustomUIPanelPlugin() {

    companion object {
        private val attachedPlugins = WeakHashMap<UIComponentAPI, StarUIPanelPlugin>()
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

        /** Number of [requestLayout] calls so far. For tests. */
        @JvmField
        internal var layoutRequests = 0L

        /** A number larger than the serial of every plugin created so far. */
        internal fun nextSerial(): Long = ++serialCounter

        @Volatile
        internal var hasAttachedPlugins = false

        fun getAttached(component: UIComponentAPI): StarUIPanelPlugin? {
            val target = if (component is BoxedUIElement) component.boxedElement else component
            if (target is CustomPanelAPI) return target.plugin as? StarUIPanelPlugin
            if (!hasAttachedPlugins) return null
            synchronized(attachedPlugins) {
                return attachedPlugins[target]
            }
        }

        fun getOrCreate(component: UIComponentAPI): StarUIPanelPlugin {
            val target = if (component is BoxedUIElement) component.boxedElement else component
            if (target is CustomPanelAPI) {
                val existing = target.plugin
                if (existing is StarUIPanelPlugin) return existing
                val created = StarUIPanelPlugin()
                created.component = target
                // A CustomPanel has one plugin slot. Wrap a foreign plugin (vanilla's or another mod's) instead of
                // replacing it, so modifiers on someone else's panel never switch its own behaviour off.
                created.delegate = existing
                target.setPlugin(created)
                return created
            }
            synchronized(attachedPlugins) {
                return attachedPlugins.getOrPut(target) {
                    hasAttachedPlugins = true
                    StarUIPanelPlugin().also {
                        it.component = target
                        it.consumeEvents = false
                    }
                }
            }
        }
    }

    lateinit var component: UIComponentAPI

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

    /** True after [dispose]: the handlers are gone and the scope will never rebuild. */
    var isDisposed = false
        private set

    /**
     * The plugin that was on this CustomPanel before StarUI attached, if any. Every callback is forwarded to it:
     * underlays draw before it, overlays after it, and it sees input before StarUI's handlers.
     */
    var delegate: CustomUIPanelPlugin? = null
        internal set

    var customPanel: CustomPanelAPI
        get() = component as CustomPanelAPI
        set(value) {
            component = value
        }

    val panel: UIPanelAPI
        get() = component as UIPanelAPI

    // ==========================================
    // ENGINE & SELF STATE
    // ==========================================
    internal var isStackLayout: Boolean = false
    internal var layoutEngine: LayoutEngine? = null
    internal var isTopLevelNode: Boolean = false
    /** This panel was culled (not drawn) in the last render: it skips advance and input until drawn again. */
    @JvmField
    internal var culled = false

    /** True while a layout engine swaps one of this panel's children itself: the change needs no new layout. */
    internal var swappingChild = false
    private var hasLayoutRun = false

    /** Number of measure + layout passes this node's own engine has started (as a layout root). For tests and tuning. */
    internal var layoutPassCount = 0
    var customData: Any? = null

    // Used when a leaf component is placed inside a larger padded/aligned layout slot.
    internal var slotBounds: SlotBounds? = null

    // Used exclusively if this panel is the Absolute Root of the UI tree.
    internal var myNodeSize: NodeSize = NodeSize(Dimension.WrapContent, Dimension.WrapContent)
    internal var myPadding: PaddingValues = PaddingValues()
    internal var myAnchor: Anchor.AnchorData? = null

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
    // CHILD LAYOUT TRACKING MAPS
    // ==========================================
    internal val childSizing = mutableMapOf<UIComponentAPI, NodeSize>()
    internal val childPadding = mutableMapOf<UIComponentAPI, PaddingValues>()
    internal val childMargins = mutableMapOf<UIComponentAPI, Margin>()
    internal val childInnerOffset = mutableMapOf<UIComponentAPI, Pair<Float, Float>>()
    /** The children the last build made through component functions, for dumps and for reuse on the next build. */
    internal val childRecords = mutableMapOf<UIComponentAPI, ChildRecord>()

    /** Set while this container's builder runs again: the old children its calls may reuse. */
    internal var reuseCursor: ReuseCursor? = null

    // Positioning maps (Box uses Alignments, Absolute uses Anchors)
    internal val childAlignments = mutableMapOf<UIComponentAPI, Alignment>()
    internal val childAnchors = mutableMapOf<UIComponentAPI, Anchor.AnchorData>()

    // Measurement and Layout delegates
    internal val childMeasurables = mutableMapOf<UIComponentAPI, (maxWidth: Float, maxHeight: Float) -> Pair<Float, Float>>()
    internal val childLayoutDelegates = mutableMapOf<UIComponentAPI, (assignedWidth: Float, assignedHeight: Float) -> Unit>()

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

    private var advanceFunctions: MutableList<(Float) -> Unit> = mutableListOf()
    private var onLayoutFunctions: MutableList<() -> Unit> = mutableListOf()
    private var onFirstLayoutFunctions: MutableList<() -> Unit> = mutableListOf()
    // Run by the PatchLib hooks when a native control's value changes (text field text, checked state, slider
    // progress), whoever changed it: the player or any code.
    private var onValueChangedFunctions: MutableList<() -> Unit> = mutableListOf()

    var inputCaptureTopPad = 0f
        private set
    var inputCaptureBottomPad = 0f
        private set
    var inputCaptureLeftPad = 0f
        private set
    var inputCaptureRightPad = 0f
        private set

    var isHovering = false
        private set

    var hasClicked = false
        private set

    var consumeEvents = false

    /**
     * Kept for source compatibility. Consumed events are now always skipped: the engine's InputEvent throws
     * "Attempt to access data of a consumed InputEvent" from getX/getY/getEventType, and a patched leaf's
     * processInput can still see events it or an earlier sibling consumed (the parent purges them only after
     * dispatching to every child).
     */
    @Deprecated("Consumed events are always ignored now.")
    var ignoreConsumedEvents = true
    private var inProcessInput = false

    /**
     * Events handled by the previous processInput call, by identity. Vanilla processInput(List) copies the caller's
     * events into the engine's internal list and calls processInput(internal) on the same object; the lifecycle
     * patch sees both overloads, so the same event objects arrive twice. The engine creates new event objects on
     * every poll, so an event already seen here is the same dispatch, not a new one.
     */
    private var lastHandledEvents: Set<InputEventAPI> = emptySet()

    internal fun runLayout() {
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
        layoutEngine?.let { engine ->
            layoutPassCount++
            layoutPass++
            val p = component.parent
            val rootMaxWidth = if (p != null && p.width > 0f) p.width else Float.MAX_VALUE
            val rootMaxHeight = if (p != null && p.height > 0f) p.height else Float.MAX_VALUE

            // 1. Measure the children's intrinsic footprint
            val (contentW, contentH) = engine.measure(maxWidth = rootMaxWidth, maxHeight = rootMaxHeight)

            // 2. SELF SIZING: If this panel is a top-level root, size itself right now!
            if (isTopLevelNode) {
                val finalW = when (val wDim = myNodeSize.width) {
                    is Dimension.Exact -> wDim.pixels
                    is Dimension.WrapContent -> contentW + myPadding.horizontal
                    else -> if (component.width > 1f) component.width else rootMaxWidth
                }
                val finalH = when (val hDim = myNodeSize.height) {
                    is Dimension.Exact -> hDim.pixels
                    is Dimension.WrapContent -> contentH + myPadding.vertical
                    else -> if (component.height > 1f) component.height else rootMaxHeight
                }
                component.setSize(finalW, finalH)
                myAnchor?.let { component.applyAnchor(it) }
            }

            // 3. Layout the children within our finalized boundaries
            engine.layout(component.width, component.height)
        }
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
            measuredH == maxHeight && measuredSize == myNodeSize && measuredPadding == myPadding) return cached
        val result = engine.measure(maxWidth, maxHeight)
        measuredPass = layoutPass
        measuredW = maxWidth
        measuredH = maxHeight
        measuredSize = myNodeSize
        measuredPadding = myPadding
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
     * Asks for this node to be measured and laid out again on the next frame it renders. The request travels up
     * through every StarUI layout container to the top-level node, which re-measures its whole tree, resizes itself
     * and re-applies its anchor.
     *
     * StarUI calls this by itself when children are added to or removed from a layout container and when a [Text]
     * changes its string. Call it after changing something StarUI cannot see (a child's size set by hand, for
     * example). It only sets flags, so it is cheap and safe to call from event handlers.
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

    @Deprecated("Use requestLayout().", ReplaceWith("requestLayout()"))
    internal fun invalidateLayout() = requestLayout()

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

    /**
     * Back to a blank plugin before the component function that made this component configures it again: every
     * handler, binding and flag the previous build set through modifiers is dropped, then the function re-applies
     * the current ones. What the engine or the user's input owns (hover, pressed state) is kept.
     */
    /** A tooltip from Modifier.tooltip is on this component; [tooltipAppliedThisBuild]: the current build applied it. */
    private var hasStarUITooltip = false
    private var tooltipAppliedThisBuild = false

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
        myAnchor = null
        myPadding = PaddingValues()
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
     * Re-anchors every child to this panel at its current place. Stack layouts anchor each child on its previous
     * sibling; the engine rejects any resize while a child is anchored on a sibling that has been removed.
     */
    internal fun pinChildrenToPanel() {
        (layoutEngine as? org.starficz.starui.compose.layout.StackLayoutEngine)?.forgetPlacements()
        val panelPosition = panel.position
        val panelTop = panelPosition.y + panelPosition.height
        for (child in panel.getChildrenNonCopy()) {
            val p = child.position
            p.inTL(p.x - panelPosition.x, panelTop - (p.y + p.height))
        }
    }

    /** Forgets everything recorded about the children (they are about to be replaced). */
    internal fun clearChildLayoutData() {
        childSizing.clear(); childPadding.clear(); childMargins.clear(); childInnerOffset.clear()
        childAlignments.clear(); childAnchors.clear(); childMeasurables.clear(); childLayoutDelegates.clear()
        childRecords.clear()
    }

    /** Disposes this plugin only; [dispose] also handles everything below it. */
    internal fun disposeSelf() {
        if (isDisposed) return
        isDisposed = true
        scope?.dispose()
        scope = null
        dirtyScopeBelow = false
        activeBindings.forEach { (state, binding) -> state.removeBinding(binding) }
        activeBindings.clear()
        handlerLists().forEach { it.clear() }
        clearChildLayoutData()
    }

    /**
     * Tears down the StarUI side of this component and of everything below it: event handlers, render and advance
     * callbacks, UIState listeners and reactive scopes. Use it when you remove UI for good; StarUI does it by itself
     * for children it rebuilds and for children dropped by `clearChildren()`. A component removed with
     * `removeComponent` is left alone, so it can be added somewhere else.
     */
    fun dispose() {
        if (::component.isInitialized) Scope.disposeTree(component) else disposeSelf()
    }

    /** The non-empty handler lists by name, e.g. "onClick, renderBelow x2". */
    internal fun describeHandlers(): String {
        val names = listOf(
            "onPositionChanged", "onClick", "onClickOutside", "onClickRelease", "onHover", "onHoverEnter", "onHoverExit",
            "onMouseHeld", "onKeyDown", "onKeyUp", "onKeyHeld", "renderBelow", "render", "advance", "onLayout", "onFirstLayout",
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

    fun onPositionChanged(function: (PositionAPI) -> Unit) { register(onPositionChangedFunctions, function) }
    override fun positionChanged(position: PositionAPI) {
        super.positionChanged(position)
        delegate?.positionChanged(position)
        onPositionChangedFunctions.forEach { it(position) }
    }

    override fun buttonPressed(buttonId: Any?) {
        delegate?.buttonPressed(buttonId)
    }

    fun setMouseCapturePad(leftPad: Float, rightPad: Float, topPad: Float, bottomPad: Float) {
        inputCaptureTopPad = topPad
        inputCaptureBottomPad = bottomPad
        inputCaptureLeftPad = leftPad
        inputCaptureRightPad = rightPad
        if (::component.isInitialized && component !is LabelAPI) {
            component.setMouseOverPad(leftPad, rightPad, topPad, bottomPad)
        }
    }

    fun renderBelow(function: (alphaMult: Float) -> Unit) { register(renderBelowFunctions, function) }
    override fun renderBelow(alphaMult: Float) {
        // Before this node's children render and before any layout below runs: the safe point to replace children.
        if (Scope.hasPendingDetached) Scope.disposeDetachedPending()
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

    /** Runs after every layout pass that sizes and positions this node. Use [onFirstLayout] for one-time setup. */
    fun onLayout(function: () -> Unit) { register(onLayoutFunctions, function) }

    /** Runs once, after the first layout pass that sizes and positions this node. */
    fun onFirstLayout(function: () -> Unit) {
        if (hasEverLaidOut) return   // a reused component had its first layout long ago
        register(onFirstLayoutFunctions, function)
    }
    fun render(function: (alphaMult: Float) -> Unit) { register(renderFunctions, function) }
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

    fun advance(function: (amount: Float) -> Unit) { register(advanceFunctions, function) }
    override fun advance(amount: Float) {
        delegate?.advance(amount)
        advanceFunctions.forEach { it(amount) }
        // This node's children have advanced already (vanilla advances children before the plugin), so they can be
        // replaced now. When a StarUI parent is also marked it will flush everything below it in a moment.
        // Not when detached: a callback above may just have removed this node (a vanilla panel rebuilding itself).
        val parent = component.parent ?: return
        if (dirtyScopeBelow && getAttached(parent)?.dirtyScopeBelow != true) Scope.flush(this)
    }

    override fun processInput(events: MutableList<InputEventAPI>) {
        delegate?.processInput(events)
        if (inProcessInput) return
        inProcessInput = true
        try {
            val alreadyHandled = lastHandledEvents
            lastHandledEvents = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<InputEventAPI, Boolean>()).also { it.addAll(events) }
            val fresh = events.filter { it !in alreadyHandled }

            fresh.filter { it.isMouseEvent }.forEach { event ->
                if (event.isConsumed) return@forEach
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

            fresh.filter { it.isKeyboardEvent }.forEach { event ->
                if (event.isConsumed) return@forEach
                if (event.isKeyDownEvent) onKeyDownFunctions.forEach { it(event) }
                if (event.isKeyUpEvent) onKeyUpFunctions.forEach { it(event) }
                if (!event.isConsumed && event.isRepeat) onKeyHeldFunctions.forEach { it(event) }
                if (consumeEvents) event.consume()
            }
        } finally {
            inProcessInput = false
        }
    }

    fun onClick(function: (InputEventAPI) -> Unit) { register(onClickFunctions, function) }
    fun onClickRelease(function: (InputEventAPI) -> Unit) { register(onClickReleaseFunctions, function) }
    fun onClickOutside(function: (InputEventAPI) -> Unit) { register(onClickOutsideFunctions, function) }
    fun onHover(function: (InputEventAPI) -> Unit) { register(onHoverFunctions, function) }
    fun onHoverEnter(function: (InputEventAPI) -> Unit) { register(onHoverEnterFunctions, function) }
    fun onHoverExit(function: (InputEventAPI) -> Unit) { register(onHoverExitFunctions, function) }
    fun onMouseHeld(function: (InputEventAPI) -> Unit) { register(onMouseButtonHeldFunctions, function) }
    fun onKeyDown(function: (InputEventAPI) -> Unit) { register(onKeyDownFunctions, function) }
    fun onKeyUp(function: (InputEventAPI) -> Unit) { register(onKeyUpFunctions, function) }
    fun onKeyHeld(function: (InputEventAPI) -> Unit) { register(onKeyHeldFunctions, function) }
}
