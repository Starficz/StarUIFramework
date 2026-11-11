package org.starficz.starui.compose.runtime

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import org.starficz.starui.compose.layout.Dimension
import org.starficz.starui.compose.layout.LayoutEngine
import org.starficz.starui.compose.layout.NodeSize
import org.starficz.starui.compose.modifier.Margin
import org.starficz.starui.compose.modifier.PaddingValues
import org.starficz.starui.core.Anchor
import org.starficz.starui.core.BoxedUIElement
import org.starficz.starui.core.applyAnchor
import org.starficz.starui.core.bottom
import org.starficz.starui.core.height
import org.starficz.starui.core.left
import org.starficz.starui.core.parent
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
    // CHILD LAYOUT TRACKING MAPS
    // ==========================================
    internal val childSizing = mutableMapOf<UIComponentAPI, NodeSize>()
    internal val childPadding = mutableMapOf<UIComponentAPI, PaddingValues>()
    internal val childMargins = mutableMapOf<UIComponentAPI, Margin>()
    internal val childInnerOffset = mutableMapOf<UIComponentAPI, Pair<Float, Float>>()

    // Positioning maps (Box uses Alignments, Absolute uses Anchors)
    internal val childAlignments = mutableMapOf<UIComponentAPI, Alignment>()
    internal val childAnchors = mutableMapOf<UIComponentAPI, Anchor.AnchorData>()

    // Measurement and Layout delegates
    internal val childMeasurables = mutableMapOf<UIComponentAPI, (maxWidth: Float, maxHeight: Float) -> Pair<Float, Float>>()
    internal val childLayoutDelegates = mutableMapOf<UIComponentAPI, (assignedWidth: Float, assignedHeight: Float) -> Unit>()

    // ==========================================
    // EVENTS & LIFECYCLE CALLBACKS
    // ==========================================
    private val activeBindings = mutableListOf<Any>()
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
        layoutEngine?.let { engine ->
            layoutPassCount++
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

    /** Called by the parent's engine once it has sized and laid out this node. */
    internal fun markLayoutResolved() {
        hasLayoutRun = true
        fireLayoutCallbacks()
    }

    private fun fireLayoutCallbacks() {
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
        var node = this
        while (true) {
            node.hasLayoutRun = false
            if (!node::component.isInitialized) return
            val parentPanel = node.component.parent ?: return
            val parentPlugin = getAttached(parentPanel) ?: return
            if (parentPlugin.layoutEngine == null) return
            node = parentPlugin
        }
    }

    @Deprecated("Use requestLayout().", ReplaceWith("requestLayout()"))
    internal fun invalidateLayout() = requestLayout()

    internal fun registerBinding(binding: Any) {
        activeBindings.add(binding)
    }

    fun onPositionChanged(function: (PositionAPI) -> Unit) { onPositionChangedFunctions.add(function) }
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

    fun renderBelow(function: (alphaMult: Float) -> Unit) { renderBelowFunctions.add(function) }
    override fun renderBelow(alphaMult: Float) {
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
    }

    /** Runs after every layout pass that sizes and positions this node. Use [onFirstLayout] for one-time setup. */
    fun onLayout(function: () -> Unit) { onLayoutFunctions.add(function) }

    /** Runs once, after the first layout pass that sizes and positions this node. */
    fun onFirstLayout(function: () -> Unit) { onFirstLayoutFunctions.add(function) }
    fun render(function: (alphaMult: Float) -> Unit) { renderFunctions.add(function) }
    override fun render(alphaMult: Float) {
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

    fun advance(function: (amount: Float) -> Unit) { advanceFunctions.add(function) }
    override fun advance(amount: Float) {
        delegate?.advance(amount)
        advanceFunctions.forEach { it(amount) }
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

    fun onClick(function: (InputEventAPI) -> Unit) { onClickFunctions.add(function) }
    fun onClickRelease(function: (InputEventAPI) -> Unit) { onClickReleaseFunctions.add(function) }
    fun onClickOutside(function: (InputEventAPI) -> Unit) { onClickOutsideFunctions.add(function) }
    fun onHover(function: (InputEventAPI) -> Unit) { onHoverFunctions.add(function) }
    fun onHoverEnter(function: (InputEventAPI) -> Unit) { onHoverEnterFunctions.add(function) }
    fun onHoverExit(function: (InputEventAPI) -> Unit) { onHoverExitFunctions.add(function) }
    fun onMouseHeld(function: (InputEventAPI) -> Unit) { onMouseButtonHeldFunctions.add(function) }
    fun onKeyDown(function: (InputEventAPI) -> Unit) { onKeyDownFunctions.add(function) }
    fun onKeyUp(function: (InputEventAPI) -> Unit) { onKeyUpFunctions.add(function) }
    fun onKeyHeld(function: (InputEventAPI) -> Unit) { onKeyHeldFunctions.add(function) }
}
