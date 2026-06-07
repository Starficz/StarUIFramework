package org.starficz.starui.core


import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.ui.*
import org.starficz.starui.core.ReflectionUtils.getMethodsMatching
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.ReflectionUtils.set
import org.starficz.starui.compose.StarUIPanelPlugin
import org.starficz.starui.core.Anchor.AnchorData
import org.starficz.starui.core.Anchor.AnchorReference

// UIComponentAPI extensions that expose UIComponent fields/methods
var UIComponentAPI.fader: Fader?
    get() = invoke("getFader") as Fader?
    set(fader) { invoke("setFader", fader) }

var UIComponentAPI.parent: UIPanelAPI?
    get() = invoke("getParent") as UIPanelAPI?
    set(parent) { invoke("setParent", parent) }

fun UIComponentAPI.setMouseOverPad(leftPad: Float, rightPad: Float, topPad: Float, bottomPad: Float) {
    invoke("setMouseOverPad", leftPad, rightPad, topPad, bottomPad)
}

val UIComponentAPI.mouseoverHighlightFader: Fader?
    get() = invoke("getMouseoverHighlightFader") as Fader?

val UIComponentAPI.topAncestor: UIPanelAPI?
    get() = invoke("findTopAncestor") as UIPanelAPI?

fun UIComponentAPI.setTooltipOffsetFromCenter(xMargin: Float, yMargin: Float){
    invoke("setTooltipOffsetFromCenter", xMargin, yMargin)
}

fun UIComponentAPI.setTooltipPositionRelativeToAnchor(xMargin: Float, yMargin: Float, anchor: UIComponentAPI){
    invoke("setTooltipPositionRelativeToAnchor", xMargin, yMargin, anchor)
}

fun UIComponentAPI.setSlideData(xOffset: Float, yOffset: Float, durationIn: Float, durationOut: Float){
    invoke("setSlideData", xOffset, yOffset, durationIn, durationOut)
}

fun UIComponentAPI.slideIn(){
    invoke("slideIn")
}

fun UIComponentAPI.slideOut(){
    invoke("slideOut")
}

fun UIComponentAPI.forceSlideIn(){
    invoke("forceSlideIn")
}

fun UIComponentAPI.forceSlideOut(){
    invoke("forceSlideOut")
}

val UIComponentAPI.sliding: Boolean
    get() = invoke("isSliding") as Boolean

val UIComponentAPI.slidIn: Boolean
    get() = invoke("isSlidIn") as Boolean

val UIComponentAPI.slidOut: Boolean
    get() = invoke("isSlidOut") as Boolean

val UIComponentAPI.slidingIn: Boolean
    get() = invoke("isSlidingIn") as Boolean

var UIComponentAPI.enabled: Boolean
    get() = invoke("isEnabled") as Boolean
    set(enabled) { invoke("setEnabled", enabled) }

var UIComponentAPI.width
    get() = position.width
    set(width) { position.setSize(width, position.height) }

var UIComponentAPI.height
    get() = position.height
    set(height) { position.setSize(position.width, height) }

fun UIComponentAPI.setSize(width: Float, height: Float){
    position.setSize(width, height)
}

val UIComponentAPI.x
    get() = position.x

val UIComponentAPI.y
    get() = position.y

val UIComponentAPI.left
    get() = x

val UIComponentAPI.bottom
    get() = y

val UIComponentAPI.top
    get() = y + height

val UIComponentAPI.right
    get() = x + width

fun UIComponentAPI.setLocation(x: Float, y: Float){
    position.setLocation(x, y)
}

/**
 * Uses x/yAlignOffset to position the bottom left of a UIComponent in absolute pixel coords,
 * where (0,0) is the bottom left of the screen.
 */
fun UIComponentAPI.setAbsoluteLocation(x: Float, y: Float){
    xAlignOffset += (x - position.x)
    yAlignOffset += (y - position.y)
}

val UIComponentAPI.centerX
    get() = position.centerX

val UIComponentAPI.centerY
    get() = position.centerY

var UIComponentAPI.xAlignOffset: Float
    get() = position.invoke("getXAlignOffset") as Float
    set(xOffset) { position.setXAlignOffset(xOffset) }

var UIComponentAPI.yAlignOffset: Float
    get() = position.invoke("getYAlignOffset") as Float
    set(yOffset) { position.setYAlignOffset(yOffset) }


fun UIComponentAPI.applyAnchor(anchor: AnchorData){
    val floatType = Float::class.javaPrimitiveType!!
    val paramTypes = listOf<Class<*>?>(this.position::class.java,
        floatType, floatType, floatType, floatType, floatType, floatType).toTypedArray()

    val anchorPosition = when(anchor.reference){
        is AnchorReference.Parent -> null
        is AnchorReference.Previous -> {
            parent?.children?.dropLast(1)?.lastOrNull()?.position
                ?: throw Error("Previous Component does not exist!")
        }
        is AnchorReference.Sibling -> anchor.reference.component.position
    }

    this.position.getMethodsMatching("relativeTo", parameterTypes = paramTypes)[0]
        .invoke(this.position, anchorPosition, anchor.refAnchorX, anchor.refAnchorY,
            anchor.targetAnchorX, anchor.targetAnchorY, anchor.offsetX, anchor.offsetY)
}

val UIPanelAPI.lastComponent
    get() = children.lastOrNull()


// UIPanelAPI extensions that expose UIPanel methods
fun UIPanelAPI.getChildrenCopy(): List<UIComponentAPI> {
    return invoke("getChildrenCopy") as List<UIComponentAPI>
}

fun UIPanelAPI.getChildrenNonCopy(): List<UIComponentAPI> {
    return invoke("getChildrenNonCopy") as List<UIComponentAPI>
}

val UIPanelAPI.children get() = this.getChildrenCopy()

fun UIPanelAPI.findChildWithMethod(
    name: String? = null,
    returnType: Class<*>? = null,
    numOfParams: Int? = null,
    parameterTypes: Array<Class<*>?>? = null
): UIComponentAPI? {
    return children.find { it.getMethodsMatching(name, returnType, numOfParams, parameterTypes).isNotEmpty() }
}

fun UIPanelAPI.allChildrenWithMethod(methodName: String): List<UIComponentAPI> {
    return children.filter { it.getMethodsMatching(methodName).isNotEmpty() }
}

fun UIPanelAPI.clearChildren() {
    invoke("clearChildren")
}

// Returns Pair(Width, Height)
fun measureTextSize(text: String, font: Font? = null): Pair<Float, Float> {
    if (text.isEmpty()) return Pair(0f, 0f)

    val tempPanel = Global.getSettings().createCustom(10000f, 10000f, null)
    val tempTM = tempPanel.createUIElement(10000f, 10000f, false)

    if (font != null) {
        tempTM.setParaFont(getFontPath(font))
    }

    val dummyLabel = tempTM.addPara(text, 0f)

    return Pair(dummyLabel.computeTextWidth(text), dummyLabel.computeTextHeight(text))
}

// Custom CustomUIPanelPlugin extensions that map the plugin position to the panel
val StarUIPanelPlugin.width
    get() = customPanel.width

val StarUIPanelPlugin.height
    get() = customPanel.height

val StarUIPanelPlugin.x
    get() = customPanel.x

val StarUIPanelPlugin.y
    get() = customPanel.y

val StarUIPanelPlugin.left
    get() = x

val StarUIPanelPlugin.bottom
    get() = y

val StarUIPanelPlugin.top
    get() = y + height

val StarUIPanelPlugin.right
    get() = x + width

val StarUIPanelPlugin.centerX
    get() = customPanel.centerX

val StarUIPanelPlugin.centerY
    get() = customPanel.centerY

val StarUIPanelPlugin.xAlignOffset
    get() = customPanel.xAlignOffset

val StarUIPanelPlugin.yAlignOffset
    get() = customPanel.yAlignOffset

fun CustomPanelAPI.setPlugin(plugin: CustomUIPanelPlugin) {
    set(value=plugin)
}
