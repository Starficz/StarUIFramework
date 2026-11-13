package org.starficz.starui.core


import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.ui.*
import org.starficz.starui.core.ReflectionUtils.getMethodsMatching
import org.starficz.starui.core.ReflectionUtils.invoke
import org.starficz.starui.core.ReflectionUtils.set
import org.starficz.starui.compose.runtime.StarUIPanelPlugin
import org.starficz.starui.core.Anchor.AnchorData
import org.starficz.starui.core.Anchor.AnchorReference

// UIComponentAPI extensions that expose UIComponent fields/methods
/**
 * The vanilla scroll panel of a scrolling container (a Row, Column, Box or CustomPanel whose `overflow` scrolls on
 * an axis), for `scrollToY`, `ensureVisible`, offsets and the rest of [ScrollPanelAPI]; null for any other panel.
 * The container is the scroll panel's content: its size is the scrolled content's size.
 */
val CustomPanelAPI.scrollPanel: ScrollPanelAPI?
    get() = (plugin as? StarUIPanelPlugin)?.scrollHost

/**
 * The vanilla map of a `SectorMap` (inside its title box when it has one), boxed: pings, centring, zoom, and the panel
 * it is. Null for any other panel, and before the map is first laid out (it is made at the size it gets). Vanilla
 * lays a map out once, so StarUI makes it again when its size changes: read this when you use it, do not keep it.
 */
val CustomPanelAPI.sectorMap: BoxedSectorMap?
    get() = findSectorMap(this)?.let { BoxedSectorMap(it) }

internal fun findSectorMap(component: UIComponentAPI): SectorMapAPI? {
    if (component is SectorMapAPI) return component
    if (component !is UIPanelAPI) return null
    return component.getChildrenNonCopy().firstNotNullOfOrNull { findSectorMap(it) }
}

var UIComponentAPI.fader: Fader?
    get() = invoke("getFader") as Fader?
    set(fader) { invoke("setFader", fader) }


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

// width, height, x and y are UIComponentAPI members since 0.98.5a (read-only; set them on `position`).

/** Sizes the component; does nothing when it already has that size (vanilla recomputes the position tree on each call). */
fun UIComponentAPI.setSize(width: Float, height: Float){
    val p = position
    if (p.width == width && p.height == height) return
    p.setSize(width, height)
}


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
    val base = when(anchor.reference){
        is AnchorReference.Parent -> null
        is AnchorReference.Previous -> {
            parent?.children?.dropLast(1)?.lastOrNull()
                ?: throw Error("Previous Component does not exist!")
        }
        is AnchorReference.Sibling -> anchor.reference.component
    }

    position.relativeToBase(base, anchor.refAnchorX, anchor.refAnchorY,
        anchor.targetAnchorX, anchor.targetAnchorY, anchor.offsetX, anchor.offsetY)
}

// Mod code may not touch java.lang.reflect directly (the game's script loader refuses it), hence ReflectedMethod.
private var relativeToParentClass: Class<*>? = null
private var relativeToParentMethod: ReflectionUtils.ReflectedMethod? = null

/**
 * Places this position relative to [base], or to its parent when [base] is null: the alignment values are those of
 * vanilla's `relativeTo` (`PositionAPI.relativeTo2`). With a base this is the API call. Without one it stays a
 * reflective call of `relativeTo(null, ...)`: vanilla keeps the null and re-resolves the parent on every recompute,
 * which `relativeTo2` (it reads `base.position`) cannot express. The method is looked up once.
 */
internal fun PositionAPI.relativeToBase(
    base: UIComponentAPI?,
    xAlignBase: Float, yAlignBase: Float, xAlignThis: Float, yAlignThis: Float, xOffset: Float, yOffset: Float
) {
    if (base != null) {
        relativeTo2(base, xAlignBase, yAlignBase, xAlignThis, yAlignThis, xOffset, yOffset)
        return
    }
    var method = relativeToParentMethod
    if (method == null || relativeToParentClass !== javaClass) {
        val floatType = java.lang.Float.TYPE
        val paramTypes = arrayOf<Class<*>?>(javaClass, floatType, floatType, floatType, floatType, floatType, floatType)
        method = getMethodsMatching("relativeTo", parameterTypes = paramTypes)[0]
        relativeToParentMethod = method
        relativeToParentClass = javaClass
    }
    method.invoke(this, null, xAlignBase, yAlignBase, xAlignThis, yAlignThis, xOffset, yOffset)
}

val UIPanelAPI.lastComponent
    get() = children.lastOrNull()


// UIPanelAPI extensions that expose UIPanel methods
/** A snapshot of the children (`UIPanelAPI.getChildrenCopy2`, a new list that later changes do not touch). */
fun UIPanelAPI.getChildrenCopy(): List<UIComponentAPI> = getChildrenCopy2()

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


// Measuring builds a throwaway panel, tooltip and label, and Text's measure lambda calls this on every layout pass.
// The result only depends on (text, font), so keep a small LRU of results.
private const val TEXT_MEASURE_CACHE_SIZE = 1024
private val textMeasureCache = object : LinkedHashMap<Pair<String, Font?>, Pair<Float, Float>>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Font?>, Pair<Float, Float>>?) =
        size > TEXT_MEASURE_CACHE_SIZE
}

// Returns Pair(Width, Height)
fun measureTextSize(text: String, font: Font? = null): Pair<Float, Float> {
    if (text.isEmpty()) return Pair(0f, 0f)
    val key = text to font
    textMeasureCache[key]?.let { return it }
    return measureTextSizeUncached(text, font).also { textMeasureCache[key] = it }
}

internal fun measureTextSizeUncached(text: String, font: Font?): Pair<Float, Float> {
    if (text.isEmpty()) return Pair(0f, 0f)

    val tempPanel = Global.getSettings().createCustom(10000f, 10000f, null)
    val tempTM = tempPanel.createUIElement(10000f, 10000f, false)

    if (font != null) {
        tempTM.setParaFont(getFontPath(font))
    }

    val dummyLabel = tempTM.addPara(text, 0f)

    return Pair(dummyLabel.computeTextWidth(text), dummyLabel.computeTextHeight(text))
}

// Custom CustomUIPanelPlugin extensions that map the plugin position to the panel or component
val StarUIPanelPlugin.width: Float
    get() = slotBounds?.width ?: component.width

val StarUIPanelPlugin.height: Float
    get() = slotBounds?.height ?: component.height

val StarUIPanelPlugin.x: Float
    get() = component.x + (slotBounds?.offsetX ?: 0f)

val StarUIPanelPlugin.y: Float
    get() = component.y + (slotBounds?.offsetY ?: 0f)

val StarUIPanelPlugin.left: Float
    get() = x

val StarUIPanelPlugin.bottom: Float
    get() = y

val StarUIPanelPlugin.top: Float
    get() = y + height

val StarUIPanelPlugin.right: Float
    get() = x + width

val StarUIPanelPlugin.centerX: Float
    get() = x + width / 2f

val StarUIPanelPlugin.centerY: Float
    get() = y + height / 2f

val StarUIPanelPlugin.xAlignOffset: Float
    get() = component.xAlignOffset

val StarUIPanelPlugin.yAlignOffset: Float
    get() = component.yAlignOffset

fun CustomPanelAPI.setPlugin(plugin: CustomUIPanelPlugin) {
    set(value=plugin)
}
