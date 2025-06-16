package org.starficz.staruiframework

/**
 * An independent flag for tracking the state of checkbox buttons. Used for button groups / persisting button state across UI.
 */
class Flag() {
    var isChecked: Boolean = true
        internal set

    var isUnchecked: Boolean
        get() = !isChecked
        internal set(unchecked) { isChecked = !unchecked }
}