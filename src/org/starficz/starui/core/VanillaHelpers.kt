package org.starficz.starui.core

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.SliderAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable
import org.starficz.starui.core.ReflectionUtils.invoke

fun UIComponentAPI.addTooltip(
    location: TooltipMakerAPI.TooltipLocation,
    width: Float,
    margin: Float? = null,
    lambda: (TooltipMakerAPI) -> Unit) {
    val tooltip = object: StandardTooltipV2Expandable(width, false, true) {
        override fun createImpl(p0: Boolean) {
            lambda(this)
        }
    }


    val tooltipClass = StandardTooltipV2Expandable::class.java
    if(margin == null){
        when(location){
            TooltipMakerAPI.TooltipLocation.LEFT -> tooltipClass.invoke("addTooltipLeft", this, tooltip)
            TooltipMakerAPI.TooltipLocation.RIGHT -> tooltipClass.invoke("addTooltipRight", this, tooltip)
            TooltipMakerAPI.TooltipLocation.ABOVE -> tooltipClass.invoke("addTooltipAbove", this, tooltip)
            TooltipMakerAPI.TooltipLocation.BELOW -> tooltipClass.invoke("addTooltipBelow", this, tooltip)
        }
    }
    else{
        when(location){
            TooltipMakerAPI.TooltipLocation.LEFT -> tooltipClass.invoke("addTooltipLeft", this, tooltip, margin)
            TooltipMakerAPI.TooltipLocation.RIGHT -> tooltipClass.invoke("addTooltipRight", this, tooltip, margin)
            TooltipMakerAPI.TooltipLocation.ABOVE -> tooltipClass.invoke("addTooltipAbove", this, tooltip, margin)
            TooltipMakerAPI.TooltipLocation.BELOW -> tooltipClass.invoke("addTooltipBelow", this, tooltip, margin)
        }
    }
}

/**
 * Removes a tooltip set with [addTooltip] (vanilla keeps one per component). A tooltip that is showing is faded out
 * first: vanilla's StandardTooltipV2 removes itself from the screen once it has faded.
 */
internal fun UIComponentAPI.removeTooltip() {
    if (invoke("getTooltipLogic") == null) return
    invoke("hideTooltip", null)
    invoke("setTooltip", 0f, null)
}

/**
 * Every vanilla [SliderAPI] is a UI component; this is that view, for a slider from vanilla or another mod. StarUI's
 * own (`Slider`, `ProgressBar`) are [BoxedUISlider]s, components already.
 */
val SliderAPI.asComponent: UIComponentAPI
    get() = if (this is BoxedUISlider) this.obf else this as UIComponentAPI

// CustomPanelAPI implements the same Listener that a ButtonAPI requires,
// A CustomPanel then happens to trigger its CustomUIPanelPlugin buttonPressed() method
// thus we can map our functions into a CustomUIPanelPlugin, and have them be triggered.
// A button has a single listener slot, so the listener this one replaces (vanilla's, or another mod's) is kept and
// ButtonListenerChainPatch forwards every actionPerformed(event, button) to it: the button keeps its original action.
internal class ButtonListener(button: ButtonAPI) : BaseCustomUIPanelPlugin() {
    private val onClickFunctions = mutableListOf<() -> Unit>()

    /** The listener that was on the button before this one, or null. */
    val previousListener: Any? = button.invoke("getListener")

    init {
        val buttonListener = Global.getSettings().createCustom(0f, 0f, this)
        button.invoke("setListener", buttonListener)
    }

    internal fun forwardToPrevious(event: Any?, source: Any?) {
        previousListener?.invoke("actionPerformed", event, source)
    }

    override fun buttonPressed(buttonId: Any?) { onClickFunctions.forEach { it() } }
    fun addOnClick(function: () -> Unit) { onClickFunctions.add(function) }
    fun clearOnClickFunctions() { onClickFunctions.clear() }
}

/** Drops the handlers added with [onClick]; the listener the button had before StarUI stays in place. */
internal fun ButtonAPI.clearOnClick() {
    val listener = invoke("getListener")
    if (listener is CustomPanelAPI) (listener.plugin as? ButtonListener)?.clearOnClickFunctions()
}

// Extension function for ButtonAPI
fun ButtonAPI.onClick(function: () -> Unit) {
    // Use reflection to check if this button already has a listener
    val existingListener = invoke("getListener")
    if (existingListener is CustomPanelAPI && existingListener.plugin is ButtonListener) {
        (existingListener.plugin as ButtonListener).addOnClick(function)
    } else {
        // if not, make one
        val listener = ButtonListener(this)
        listener.addOnClick(function)
    }
}
