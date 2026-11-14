package org.starficz.starui.vanilla

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.ui.*
import org.starficz.starui.Anchor.AnchorData
import org.starficz.starui.Anchor.AnchorReference
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.ReflectionUtils.getMethodsMatching
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import org.starficz.starui.vanilla.ReflectionUtils.set

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

fun UIComponentAPI.setMouseOverPad(leftPad: Float, rightPad: Float, topPad: Float, bottomPad: Float) {
    invoke("setMouseOverPad", leftPad, rightPad, topPad, bottomPad)
}

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
            parent?.getChildrenNonCopy()?.let { it.getOrNull(it.size - 2) }
                ?: throw IllegalStateException("StarUI: Anchor ...previousComponent: there is no component before this one")
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

// UIPanelAPI extensions that expose UIPanel methods
private val childrenNonCopyMethods = HashMap<Class<*>, ReflectionUtils.ReflectedMethod>()

/**
 * The panel's live child list (vanilla's own, not a copy: do not change it, and copy it before changing the panel
 * while going through it). Runs often (layouts, input, injection checks), so the method is looked up once per class.
 */
@Suppress("UNCHECKED_CAST")
fun UIPanelAPI.getChildrenNonCopy(): List<UIComponentAPI> {
    val target: Any = if (this is BoxedUIElement) obf else this
    val method = childrenNonCopyMethods.getOrPut(target.javaClass) {
        target.getMethodsMatching("getChildrenNonCopy", numOfParams = 0).single()
    }
    return method.invoke(target) as List<UIComponentAPI>
}

/** A snapshot of the children (the API's `getChildrenCopy2`: a new list that later changes do not touch). */
val UIPanelAPI.children: List<UIComponentAPI> get() = getChildrenCopy2()

fun UIPanelAPI.findChildWithMethod(
    name: String? = null,
    returnType: Class<*>? = null,
    numOfParams: Int? = null,
    parameterTypes: Array<Class<*>?>? = null
): UIComponentAPI? {
    return children.find { it.getMethodsMatching(name, returnType, numOfParams, parameterTypes).isNotEmpty() }
}


/**
 * The size of [text] in [font] (vanilla's paragraph font for null) as (width, height): on one line, or more where it
 * has line breaks. Cheap: it measures with one kept label per font.
 */
fun measureTextSize(text: String, font: Font? = null): Pair<Float, Float> = VanillaFactory.measure(text, font)

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

internal fun CustomPanelAPI.setPlugin(plugin: CustomUIPanelPlugin) {
    set(value=plugin)
}
