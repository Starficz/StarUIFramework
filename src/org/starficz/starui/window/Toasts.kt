package org.starficz.starui.window

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.Alignment
import org.starficz.starui.Axis
import org.starficz.starui.Size
import org.starficz.starui.StarUIBuilder
import org.starficz.starui.UIState
import org.starficz.starui.components.Box
import org.starficz.starui.components.CustomPanel
import org.starficz.starui.components.Text
import org.starficz.starui.components.key
import org.starficz.starui.modifier.*
import org.starficz.starui.runtime.StarUIContext
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.VanillaColors
import org.starficz.starui.vanilla.forceSlideIn
import org.starficz.starui.vanilla.forceSlideOut
import org.starficz.starui.vanilla.isSlidOut
import org.starficz.starui.vanilla.measureTextSize
import org.starficz.starui.vanilla.setSlideData
import org.starficz.starui.vanilla.slideIn
import org.starficz.starui.vanilla.slideOut
import java.awt.Color

/**
 * A notice on screen that goes away by itself, from [showToast]. Clicking it dismisses it; while the mouse is over it
 * its time does not run.
 *
 * Vanilla has no toasts: its notices are the campaign's message list (`campaignUI.addMessage`, bottom left) and
 * combat's message area. Prefer those for what they can show; a toast is for the rest (the title screen, a notice
 * with rich content, one that must be seen over a dialog).
 */
class StarUIToast internal constructor(
    internal val host: ToastHost,
    internal val id: Long,
    internal val duration: Float,
    internal val alignment: Alignment,
    internal val content: CustomPanelAPI.() -> Unit
) {
    internal var age = 0f
    internal var closing = false
        private set
    /** Given one of its place's slots (at most [TOAST_MAX_SHOWN] show at once; the rest wait). */
    internal var shown = false
    /** Where it is in its place's stack, and its panel. */
    internal val slot = StackSlot()
    internal val panel: CustomPanelAPI? get() = slot.panel
    internal val placed: Boolean get() = slot.placed

    /** Whether the toast is showing: false from the moment it starts to leave, and once its screen is gone. */
    val isOpen: Boolean get() = !closing && host.layer.isOpen

    /** Slides the toast out now, before its time is up. */
    fun close() {
        closing = true
    }
}

/**
 * Shows [text] as a toast: a small panel (vanilla's panel background and border) at [alignment] on the screen, over
 * everything else, gone after [duration] seconds. It moves like Windows' notifications: it slides in from the side of
 * the screen its place is on (vanilla's slide, which fades it in), the newest nearest the edge, and the older ones
 * glide aside; it slides back out when its time is up or it is clicked, and the rest glide into its gap. While the
 * mouse is over it, its time stops. At most three show at one place; more wait for a slot, counted by a "+N more" chip
 * nearest the edge, under the newest toast. By default at the bottom
 * right: in the campaign just above its minimap, elsewhere 10 px from the corner. Long text wraps at 400 px.
 *
 * ```kotlin
 * showToast("Doctrine saved")
 * showToast("Not enough supplies", color = VanillaColors.negative)
 * ```
 *
 * Callable from anywhere but a builder (a click handler, a campaign script); on any screen. The time is real time:
 * a toast goes away while the game is paused too.
 *
 * @param text the notice
 * @param duration how long it shows, in seconds, before fading out; 0 or less keeps it until it is clicked or closed
 * @param alignment where on the screen it shows: a corner, an edge's middle or the centre. By default the bottom
 *   right, above the campaign's minimap. The top centre leaves room for the campaign's "Game paused" line
 * @param color the text's colour, or `null` for vanilla's text colour
 * @return the toast
 * @throws IllegalStateException when called while a builder runs, or before the game has a screen
 */
fun showToast(
    text: String,
    duration: Float = 3f,
    alignment: Alignment = Alignment.BottomRight,
    color: Color? = null
): StarUIToast = showToast(duration, alignment) {
    val wide = measureTextSize(text).first > TOAST_MAX_TEXT_WIDTH
    Text(text, if (wide) Size.mixed(Axis.Exact(TOAST_MAX_TEXT_WIDTH), Axis.WrapContent) else Size.wrapContent(), color = color)
}

/**
 * Shows a toast with content of your own (an icon and text, a few lines, a button), built by [builder] in the
 * toast's panel. Otherwise as the text form of [showToast].
 *
 * ```kotlin
 * showToast(duration = 5f) {
 *     Row(spacing = 5f) { Image(icon, ImageSize.native()); Text("Charter renewed: 30 days") }
 * }
 * ```
 *
 * @param duration how long it shows, in seconds, before fading out; 0 or less keeps it until it is clicked or closed
 * @param alignment where on the screen it shows; by default the bottom right, above the campaign's minimap
 * @param builder the content, in the toast's panel; reactive like any builder
 * @return the toast
 * @throws IllegalStateException when called while a builder runs, or before the game has a screen
 */
fun showToast(
    duration: Float = 3f,
    alignment: Alignment = Alignment.BottomRight,
    builder: StarUIBuilder
): StarUIToast {
    if (StarUIContext.currentScope != null) throw IllegalStateException(
        "StarUI: showToast(...) was called while a builder runs, so it would show another toast every time that " +
        "builder runs again. Show it from the handler or script where the thing it tells about happens."
    )
    val stack = Layers.stackOf(Layers.requireHost(), create = true)!!
    val host = stack.toasts?.takeIf { it.layer.isOpen } ?: ToastHost.open(stack)
    val toast = StarUIToast(host, host.nextId++, duration, alignment, builder)
    host.entries.value = host.entries.peek() + toast
    return toast
}

private const val TOAST_MAX_TEXT_WIDTH = 400f
/** The campaign's minimap and its location line take the bottom right 215 px up (0.98.5a-RC4, in UI units). */
private const val CAMPAIGN_MINIMAP_CLEARANCE = 220f

/** How many toasts one place shows at once; the rest wait for a slot. */
internal const val TOAST_MAX_SHOWN = 3
private const val TOAST_SLIDE_SECONDS = 0.25f
private const val TOAST_SPACING = 5f
private const val TOAST_EDGE_MARGIN = 10f
/** How fast toasts glide to a new slot: the share of the way covered per second (exponential). */
private const val TOAST_GLIDE_RATE = 12f

/** One panel in a place's stack (a toast, or the "+N more" chip): where it is now and how far in its slide. */
internal class StackSlot {
    var panel: CustomPanelAPI? = null
    /** Placed and sliding in or in: [edgeDistance] is where it is now. */
    var placed = false
    var leaving = false
    /** Its distance from its place's edge (the bottom for bottom places, else the top), gliding to its slot. */
    var edgeDistance = 0f

    /** Gone: it slid out after it was told to leave. */
    val isGone: Boolean get() = leaving && panel?.isSlidOut != false
}

/** The "+N more" chip nearest a place's edge (under the newest toast) while toasts wait for a slot there. */
internal class MoreChip(val alignment: Alignment) {
    val slot = StackSlot()
    val count = UIState(0)
    /** Nothing waits any more: it slides out, then goes. */
    var going = false
}

/**
 * The toasts of one screen: one non-modal layer, kept above every other layer. Its content is one full-screen stage
 * whose toasts are placed by hand every frame, the way Windows' notifications move: at each place the newest is
 * nearest the screen's edge, the others glide to their slots as toasts come and go, each arrives and leaves with
 * vanilla's slide (which fades it too), from and to the side its place is on. Toasts waiting for a slot show as a
 * "+N more" chip nearest the edge, under the newest toast.
 */
internal class ToastHost private constructor() {
    val entries = UIState(listOf<StarUIToast>())
    val chips = UIState(listOf<MoreChip>())
    lateinit var layer: StarUILayer
    var nextId = 0L
    private var lastTick = 0L

    /** The layer's content: the stage, and in it a panel per toast and chip (unanchored: [tick] places them). */
    fun build(panel: CustomPanelAPI) {
        val all = entries.value
        val allChips = chips.value
        panel.CustomPanel(Size.fillMaxSize()) {
            for (toast in all) key(toast.id) {
                val box = Box(Size.wrapContent(), Modifier.background(VanillaColors.panel).border()
                    .padding(horizontal = 10f, vertical = 6f).onClick { toast.close() }) { (toast.content)() }
                adopt(toast.slot, box)
            }
            for (chip in allChips) key(chip) {
                val box = Box(Size.wrapContent(), Modifier.background(VanillaColors.panel).border()
                    .padding(horizontal = 8f, vertical = 3f)) {
                    Text("+${chip.count.value} more", color = VanillaColors.gray)
                }
                adopt(chip.slot, box)
            }
        }
    }

    private fun adopt(slot: StackSlot, box: CustomPanelAPI) {
        if (slot.panel === box) return
        slot.panel = box
        // parked off the screen until it is placed: drawn there (a component that is not drawn, slid out or
        // transparent, is never laid out, so it would have no size to place it by), seen by nobody
        box.position.inTL(-10000f, -10000f)
    }

    /** Every frame, from the layer's advance: time, slots, the chips, glide, slides; drops what is gone. */
    fun tick(amount: Float) {
        val now = System.nanoTime()
        val wall = if (lastTick == 0L) 0f else (now - lastTick) / 1e9f
        lastTick = now
        val dt = maxOf(amount, wall).coerceAtMost(0.1f)
        val instant = Layers.fadeScale <= 0f
        val screen = layer.root ?: return
        val stage = Stage(screen.position.width, screen.position.height, dt, instant)
        var goneToasts: MutableList<StarUIToast>? = null
        var goneChips: MutableList<MoreChip>? = null

        for (alignment in Alignment.entries) {
            val here = entries.peek().filter { it.alignment === alignment }
            val chipsHere = chips.peek().filter { it.alignment === alignment }
            if (here.isEmpty() && chipsHere.isEmpty()) continue
            // slots: the waiting ones take the free ones in the order they came
            var free = TOAST_MAX_SHOWN - here.count { it.shown }
            for (toast in here) if (!toast.shown && !toast.closing && free > 0) { toast.shown = true; free-- }
            for (toast in here) if (!toast.shown && toast.closing) (goneToasts ?: ArrayList<StarUIToast>().also { goneToasts = it }).add(toast)

            // the chip: how many wait here, nearest the edge, the toasts past it. A toast that is leaving already gives
            // its slot to one that waits: the chip counts (and goes) as the toast starts to slide out, not once it is out
            val leaving = here.count { it.shown && it.closing }
            val waiting = (here.count { !it.shown && !it.closing } - leaving).coerceAtLeast(0)
            val live = chipsHere.firstOrNull { !it.going }
            if (waiting > 0) {
                if (live == null) chips.value = chips.peek() + MoreChip(alignment).also { it.count.value = waiting }
                else if (live.count.peek() != waiting) live.count.value = waiting
            } else live?.going = true
            var cursor = edgeMargin(alignment)
            for (chip in chipsHere) {
                cursor = stage.place(chip.slot, alignment, cursor, leave = chip.going) ?: continue
                if (chip.slot.isGone) (goneChips ?: ArrayList<MoreChip>().also { goneChips = it }).add(chip)
            }
            // newest nearest the edge
            for (toast in here.asReversed()) {
                if (!toast.shown) continue
                val box = toast.panel ?: continue
                if (toast.placed) {
                    val hovered = StarUIPanelPlugin.getAttached(box)?.isHovering == true
                    if (!toast.closing && !hovered) toast.age += dt
                    if (!toast.closing && toast.duration > 0f && toast.age >= toast.duration) toast.close()
                }
                cursor = stage.place(toast.slot, alignment, cursor, leave = toast.closing) ?: continue
                if (toast.slot.isGone) (goneToasts ?: ArrayList<StarUIToast>().also { goneToasts = it }).add(toast)
            }
        }

        goneChips?.let { gone -> chips.value = chips.peek() - gone.toSet() }
        goneToasts?.let { gone -> entries.value = entries.peek() - gone.toSet() }
        if (entries.peek().isEmpty() && chips.peek().isEmpty()) layer.close()
    }

    /** Whether (x, y) is over a toast or a chip: the layer takes the mouse there and lets it through elsewhere. */
    fun hit(x: Float, y: Float): Boolean =
        entries.peek().any { it.slot.contains(x, y) } || chips.peek().any { it.slot.contains(x, y) }

    private fun StackSlot.contains(x: Float, y: Float): Boolean {
        val p = panel?.position ?: return false
        return placed && x >= p.x && x <= p.x + p.width && y >= p.y && y <= p.y + p.height
    }

    /** One frame's placing: the screen's size, the frame's time, and whether moves are instant (tests). */
    private class Stage(val screenW: Float, val screenH: Float, val dt: Float, val instant: Boolean) {
        /**
         * Puts [slot]'s panel [target] from its place's edge (gliding there once placed), slides it in the first time,
         * out when told to [leave]. Returns the distance past it, where the next one goes, or null while the panel is
         * not measured yet (its first layout comes with its first render).
         */
        fun place(slot: StackSlot, alignment: Alignment, target: Float, leave: Boolean): Float? {
            val box = slot.panel ?: return null
            val w = box.position.width
            val h = box.position.height
            if (w <= 1f || h <= 1f) return null
            slot.edgeDistance = when {
                !slot.placed || instant -> target
                else -> slot.edgeDistance + (target - slot.edgeDistance) * minOf(1f, dt * TOAST_GLIDE_RATE)
            }
            val x = when (alignment.x) {
                0f -> TOAST_EDGE_MARGIN
                1f -> screenW - TOAST_EDGE_MARGIN - w
                else -> (screenW - w) / 2f
            }
            val y = when {
                alignment.y == 0f -> screenH - slot.edgeDistance - h
                alignment.y == 1f -> slot.edgeDistance
                else -> screenH / 2f - h / 2f + slot.edgeDistance - edgeMargin(alignment)
            }
            box.position.inTL(x, y)
            if (!slot.placed) {
                slot.placed = true
                // vanilla's slide: it moves the panel by this offset (scaled down as it comes in) and fades it
                val (dx, dy) = slideOffset(alignment, w, h)
                val seconds = if (instant) 0f else TOAST_SLIDE_SECONDS
                box.setSlideData(dx, dy, seconds, seconds)
                box.forceSlideOut()
                if (instant) box.forceSlideIn() else box.slideIn()
            }
            if (leave && !slot.leaving) {
                slot.leaving = true
                if (instant) box.forceSlideOut() else box.slideOut()
            }
            return target + h + TOAST_SPACING
        }
    }

    companion object {
        fun open(stack: Layers.Stack): ToastHost {
            val host = ToastHost()
            Layers.openLayer(modal = false, dim = 0f, pause = false, dismissOnEscape = false,
                onDismiss = {}, onClose = {},
                prepare = { layer ->
                    host.layer = layer   // before the layer is shown: showing it raises the toasts' layer
                    layer.fadeIn = 0f
                    layer.fadeOut = 0f
                    layer.onTick = { amount -> host.tick(amount) }
                    layer.hitTest = { x, y -> host.hit(x, y) }
                    stack.toasts = host
                }
            ) { host.build(this) }
            return host
        }

        /** The room between a place's edge and its nearest toast. */
        private fun edgeMargin(alignment: Alignment): Float = when {
            // the top centre leaves room for the campaign's "Game paused" line
            alignment === Alignment.TopCenter -> 40f
            // the bottom right stacks up from just above the campaign's minimap
            alignment === Alignment.BottomRight && Global.getCurrentState() == GameState.CAMPAIGN -> CAMPAIGN_MINIMAP_CLEARANCE
            else -> TOAST_EDGE_MARGIN
        }

        /** Where a toast slides in from and out to: off the side of the screen its place is on. */
        private fun slideOffset(alignment: Alignment, w: Float, h: Float): Pair<Float, Float> = when {
            alignment.x == 1f -> Pair(w + TOAST_EDGE_MARGIN, 0f)
            alignment.x == 0f -> Pair(-(w + TOAST_EDGE_MARGIN), 0f)
            alignment.y == 1f -> Pair(0f, h + TOAST_EDGE_MARGIN)
            alignment.y == 0f -> Pair(0f, -(h + TOAST_EDGE_MARGIN))
            else -> Pair(0f, 0f)   // the centre: vanilla's slide fades it in and out where it is
        }
    }
}
