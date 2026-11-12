package org.starficz.starui.compose

/**
 * What a container does, on one axis, with children that do not fit in it. Like CSS's `overflow-x` / `overflow-y`.
 *
 * - [Visible]: they spill out and are drawn outside the container. The default.
 * - [Clip]: they are cut at the container's edge, and the cut-off part takes no mouse input.
 * - [Scroll]: the container scrolls on this axis when they are bigger than it (a scrollbar appears only then).
 *
 * Only an exact or fill axis can overflow: on a wrapping axis the container grows to fit its children instead.
 */
enum class AxisOverflow { Visible, Clip, Scroll }

/**
 * What a container ([Row], [Column], [Box], [CustomPanel]) does with children that do not fit, per axis: its
 * `overflow` argument. Use the shorthands for the common cases:
 *
 * ```
 * Column(Size.fillMaxSize(), overflow = Overflow.ScrollVertical) { ... }   // a list
 * Row(Size.fillMaxWidth(height = 120f), overflow = Overflow.ScrollHorizontal) { ... }
 * Box(Size.exact(400f, 300f), overflow = Overflow.Scroll) { Image(map) }    // a 2D viewport
 * Box(Size.exact(200f, 60f), overflow = Overflow.Clip) { ... }
 * ```
 *
 * Rules:
 * - **A scroll axis clips both axes.** Vanilla's scroll panel cuts its content at its own rectangle on both axes, so
 *   the other axis is [AxisOverflow.Clip] or [AxisOverflow.Scroll]; `Overflow(Visible, Scroll)` throws. (CSS
 *   forbids the same pair and silently treats the `visible` as `auto`.)
 * - **On a scroll axis** the children get the container's visible size as their room (for fill, wrapping text and
 *   alignment), and the scrolled content is that size or bigger. A child that fills a scroll axis takes what is
 *   left of the visible size along a Column or Row: a fill spacer pushes a footer to the bottom of a short list, and
 *   is 0 once the list is longer than the container. In a Box, and across a Column or Row, a fill child spans the
 *   whole content (at least the visible size), so backgrounds and dividers reach the far edge.
 * - **On a clip axis** the children are bounded by the container, as without overflow; anything bigger is cut.
 * - **A wrapping axis never overflows**: the container grows with its children, so its overflow has no effect there.
 */
data class Overflow(val horizontal: AxisOverflow, val vertical: AxisOverflow) {
    init {
        require(!(horizontal == AxisOverflow.Visible && vertical == AxisOverflow.Scroll) &&
                !(vertical == AxisOverflow.Visible && horizontal == AxisOverflow.Scroll)) {
            "StarUI: Overflow($horizontal, $vertical): a scroll axis clips both axes (vanilla's scroll panel cuts its " +
            "content on both), so the other axis cannot be Visible. Use Clip or Scroll for it."
        }
    }

    /** True when either axis scrolls: the container is then a vanilla scroll panel holding the StarUI container. */
    val scrolls: Boolean get() = horizontal == AxisOverflow.Scroll || vertical == AxisOverflow.Scroll

    /** True when either axis cuts its children (clip or scroll). */
    val clips: Boolean get() = horizontal != AxisOverflow.Visible || vertical != AxisOverflow.Visible

    override fun toString(): String = when (this) {
        Visible -> "Visible"
        Clip -> "Clip"
        ScrollVertical -> "ScrollVertical"
        ScrollHorizontal -> "ScrollHorizontal"
        Scroll -> "Scroll"
        else -> "Overflow($horizontal, $vertical)"
    }

    companion object {
        /** Children spill out on both axes. The default. */
        val Visible = Overflow(AxisOverflow.Visible, AxisOverflow.Visible)
        /** Children are cut at the container's edges. */
        val Clip = Overflow(AxisOverflow.Clip, AxisOverflow.Clip)
        /** Scrolls up and down; anything too wide is cut. A list. */
        val ScrollVertical = Overflow(AxisOverflow.Clip, AxisOverflow.Scroll)
        /** Scrolls left and right; anything too tall is cut. A strip of cards. */
        val ScrollHorizontal = Overflow(AxisOverflow.Scroll, AxisOverflow.Clip)
        /** Scrolls on both axes. A 2D viewport (a map, a big grid). */
        val Scroll = Overflow(AxisOverflow.Scroll, AxisOverflow.Scroll)
    }
}
