package org.starficz.starui

import com.fs.starfarer.api.ui.UIPanelAPI
import org.starficz.starui.window.Layers

/**
 * Switches for tests that run StarUI windows from code (a startup probe, a script in a test game): no waiting for
 * animations, and windows on a panel of the test's choosing. Find what is on screen with [findAll]; drive it with real
 * input (the starsector-hotreload skill's `hot.sh click`, or input events sent to the panel).
 *
 * ```kotlin
 * StarUITesting.instantAnimations = true
 * StarUITesting.windowHost = root          // before any screen exists, or to keep windows inside the test's panel
 * try { /* open, find, check */ } finally { StarUITesting.reset() }
 * ```
 *
 * Both are global and stay set until changed: reset them when the test ends. Not for release code.
 */
object StarUITesting {
    /**
     * Every StarUI animation done at once: layers and dialogs fade in and out, dialogs fold open and shut, and toasts
     * slide and glide, all in no time. A toast's own time on screen still counts down. Default false.
     */
    var instantAnimations: Boolean
        get() = Layers.fadeScale <= 0f
        set(value) { Layers.fadeScale = if (value) 0f else 1f }

    /**
     * The panel StarUI's windows (`showLayer`, `Layer`, dialogs, toasts) open on instead of the screen panel, or null
     * for the screen panel (the default). While it is set, windows never pause the campaign or battle.
     */
    var windowHost: UIPanelAPI?
        get() = Layers.testHost
        set(value) { Layers.testHost = value }

    /** Both back to their defaults. */
    fun reset() {
        instantAnimations = false
        windowHost = null
    }
}
