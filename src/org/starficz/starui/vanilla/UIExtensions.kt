package org.starficz.starui.vanilla

import com.fs.graphics.util.Fader
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.ui.*
import org.starficz.starui.Anchor.AnchorData
import org.starficz.starui.Anchor.AnchorReference
import org.starficz.starui.vanilla.ReflectionUtils.getMethodsMatching
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import org.starficz.starui.vanilla.ReflectionUtils.set

// Kotlin access to vanilla UI: the readable methods of vanilla's base component and panel classes that the API
// interfaces leave out, and small conveniences on top of the API. Each is a thin call through to vanilla, for working
// on vanilla UI directly; StarUI's components do not need any of them.

// ---------------------------------------------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------------------------------------------

/**
 * Extends the area where vanilla sees the mouse as over the component (its hover highlight and its tooltip) beyond
 * the component's bounds.
 *
 * @param leftPad the extra space on the left, in pixels
 * @param rightPad the extra space on the right, in pixels
 * @param topPad the extra space on the top, in pixels
 * @param bottomPad the extra space on the bottom, in pixels
 */
fun UIComponentAPI.setMouseOverPad(leftPad: Float, rightPad: Float, topPad: Float, bottomPad: Float) {
    invoke("setMouseOverPad", leftPad, rightPad, topPad, bottomPad)
}

// width, height, x and y are UIComponentAPI members since 0.98.5a (read-only; set them on `position`).

/**
 * Sizes the component, through its position. Does nothing when it already has that size, since vanilla works out
 * the position of everything placed relative to it again on each call.
 *
 * @param width the new width
 * @param height the new height
 */
fun UIComponentAPI.setSize(width: Float, height: Float){
    val p = position
    if (p.width == width && p.height == height) return
    p.setSize(width, height)
}


/** The x of the component's left edge, in screen coordinates. */
val UIComponentAPI.left
    get() = x

/** The y of the component's bottom edge, in screen coordinates (Starsector's y points up). */
val UIComponentAPI.bottom
    get() = y

/** The y of the component's top edge, in screen coordinates. */
val UIComponentAPI.top
    get() = y + height

/** The x of the component's right edge, in screen coordinates. */
val UIComponentAPI.right
    get() = x + width

/**
 * Places the component's bottom left corner at ([x], [y]) in screen coordinates, through its position.
 *
 * @param x the x of the left edge
 * @param y the y of the bottom edge
 */
fun UIComponentAPI.setLocation(x: Float, y: Float){
    position.setLocation(x, y)
}

/** The x of the component's centre, in screen coordinates. */
val UIComponentAPI.centerX
    get() = position.centerX

/** The y of the component's centre, in screen coordinates. */
val UIComponentAPI.centerY
    get() = position.centerY

/**
 * The horizontal offset added to wherever the component is placed relative to its reference. Changing it moves the
 * component without changing what it is placed against.
 */
var UIComponentAPI.xAlignOffset: Float
    get() = position.invoke("getXAlignOffset") as Float
    set(xOffset) { position.setXAlignOffset(xOffset) }

/**
 * The vertical offset added to wherever the component is placed relative to its reference, positive upwards.
 * Changing it moves the component without changing what it is placed against.
 */
var UIComponentAPI.yAlignOffset: Float
    get() = position.invoke("getYAlignOffset") as Float
    set(yOffset) { position.setYAlignOffset(yOffset) }


/** The component's fader: its opacity over time as vanilla fades it in and out. */
var UIComponentAPI.fader: Fader?
    get() = invoke("getFader") as Fader?
    set(fader) { invoke("setFader", fader) }

/** The fader vanilla drives while the mouse is over the component (its hover highlight). */
val UIComponentAPI.mouseoverHighlightFader: Fader?
    get() = invoke("getMouseoverHighlightFader") as Fader?

/** The outermost panel above the component (the screen it is on). */
val UIComponentAPI.topAncestor: UIPanelAPI?
    get() = invoke("findTopAncestor") as UIPanelAPI?

/** Whether the component takes input; a disabled one is drawn greyed out where it has a disabled look. */
var UIComponentAPI.isEnabled: Boolean
    get() = invoke("isEnabled") as Boolean
    set(enabled) { invoke("setEnabled", enabled) }

/** Moves the component's tooltip away from its centre by these margins. */
fun UIComponentAPI.setTooltipOffsetFromCenter(xMargin: Float, yMargin: Float) {
    invoke("setTooltipOffsetFromCenter", xMargin, yMargin)
}

/** Places the component's tooltip relative to [anchor] instead of the component, by these margins. */
fun UIComponentAPI.setTooltipPositionRelativeToAnchor(xMargin: Float, yMargin: Float, anchor: UIComponentAPI) {
    invoke("setTooltipPositionRelativeToAnchor", xMargin, yMargin, anchor)
}

/**
 * Sets up vanilla's slide animation: [slideOut] moves the component by ([xOffset], [yOffset]) over [durationOut]
 * seconds and [slideIn] brings it back over [durationIn].
 */
fun UIComponentAPI.setSlideData(xOffset: Float, yOffset: Float, durationIn: Float, durationOut: Float) {
    invoke("setSlideData", xOffset, yOffset, durationIn, durationOut)
}

/** Slides the component back to its place (see [setSlideData]). */
fun UIComponentAPI.slideIn() { invoke("slideIn") }
/** Slides the component away by its slide offset (see [setSlideData]). */
fun UIComponentAPI.slideOut() { invoke("slideOut") }
/** In place at once, no animation. */
fun UIComponentAPI.forceSlideIn() { invoke("forceSlideIn") }
/** Slid away at once, no animation. */
fun UIComponentAPI.forceSlideOut() { invoke("forceSlideOut") }

/** Sliding in either direction. */
val UIComponentAPI.isSliding: Boolean get() = invoke("isSliding") as Boolean
/** Fully in place. */
val UIComponentAPI.isSlidIn: Boolean get() = invoke("isSlidIn") as Boolean
/** Fully slid away. */
val UIComponentAPI.isSlidOut: Boolean get() = invoke("isSlidOut") as Boolean
/** Sliding back into place. */
val UIComponentAPI.isSlidingIn: Boolean get() = invoke("isSlidingIn") as Boolean

/**
 * Moves the component so its bottom left is at ([x], [y]) on the screen ((0, 0) is the screen's bottom left), through
 * its align offsets: it keeps whatever it is placed relative to.
 */
fun UIComponentAPI.setAbsoluteLocation(x: Float, y: Float) {
    xAlignOffset += (x - position.x)
    yAlignOffset += (y - position.y)
}

/**
 * Places the component with vanilla's `relativeTo`, as [anchor] describes, without StarUI's layout: for vanilla
 * components that StarUI does not place. In StarUI, use `Modifier.anchor` instead, which also keeps margins.
 *
 * @param anchor where the component goes. See [Anchor][org.starficz.starui.Anchor]
 * @throws IllegalStateException if [anchor] is against the previous component and there is none
 */
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

// ---------------------------------------------------------------------------------------------------------------
// Panels
// ---------------------------------------------------------------------------------------------------------------

private val childrenNonCopyMethods = HashMap<Class<*>, ReflectionUtils.ReflectedMethod>()

/**
 * Returns the panel's live child list: vanilla's own, not a copy. Do not change it, and copy it before changing the
 * panel while going through it. For a snapshot, use [children].
 *
 * It runs often (layouts, input, injection checks), so the method is looked up once per class.
 */
@Suppress("UNCHECKED_CAST")
fun UIPanelAPI.getChildrenNonCopy(): List<UIComponentAPI> {
    val target: Any = if (this is BoxedUIElement) vanillaComponent else this
    val method = childrenNonCopyMethods.getOrPut(target.javaClass) {
        target.getMethodsMatching("getChildrenNonCopy", numOfParams = 0).single()
    }
    return method.invoke(target) as List<UIComponentAPI>
}

/** A snapshot of the children (the API's `getChildrenCopy2`: a new list that later changes do not touch). */
val UIPanelAPI.children: List<UIComponentAPI> get() = getChildrenCopy2()

/**
 * Returns the first child that has a method matching the given name, return type and parameters, or `null`. Each
 * argument left `null` matches anything. Vanilla's obfuscated panels are told apart by their readable methods.
 *
 * @param name the method's name
 * @param returnType the method's return type
 * @param numOfParams the method's number of parameters
 * @param parameterTypes the method's parameter types, `null` entries matching any type
 * @see allChildrenWithMethod
 */
fun UIPanelAPI.findChildWithMethod(
    name: String? = null,
    returnType: Class<*>? = null,
    numOfParams: Int? = null,
    parameterTypes: Array<Class<*>?>? = null
): UIComponentAPI? {
    return children.find { it.getMethodsMatching(name, returnType, numOfParams, parameterTypes).isNotEmpty() }
}

/** The children that have a method named [methodName] (vanilla's obfuscated panels are told apart by their methods). */
fun UIPanelAPI.allChildrenWithMethod(methodName: String): List<UIComponentAPI> =
    children.filter { it.getMethodsMatching(methodName).isNotEmpty() }

/** The child added last (drawn on top), or null. */
val UIPanelAPI.lastComponent: UIComponentAPI?
    get() = getChildrenNonCopy().lastOrNull()


// ---------------------------------------------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------------------------------------------

/**
 * Returns the size of [text] in [font] as (width, height): on one line, or more where it has line breaks. Cheap: it
 * measures with one kept label per font.
 *
 * @param text the text to measure
 * @param font the font, or `null` for vanilla's paragraph font
 */
fun measureTextSize(text: String, font: Font? = null): Pair<Float, Float> = VanillaFactory.measure(text, font)

internal fun CustomPanelAPI.setPlugin(plugin: CustomUIPanelPlugin) {
    set(value=plugin)
}
