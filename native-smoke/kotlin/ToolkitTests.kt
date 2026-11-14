// The vanilla toolkit (org.starficz.starui.vanilla): every helper called once on a real vanilla component, so a game
// update that renames a method one of them calls shows up here as a failed check, not as a crash in someone's mod.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.ReflectionUtils.invoke
import org.starficz.starui.vanilla.VanillaColors
import org.starficz.starui.vanilla.addToOverlay
import org.starficz.starui.vanilla.addTooltip
import org.starficz.starui.vanilla.allChildrenWithMethod
import org.starficz.starui.vanilla.autoSize
import org.starficz.starui.vanilla.bottom
import org.starficz.starui.vanilla.centerX
import org.starficz.starui.vanilla.centerY
import org.starficz.starui.vanilla.children
import org.starficz.starui.vanilla.fader
import org.starficz.starui.vanilla.findChildWithMethod
import org.starficz.starui.vanilla.forceSlideIn
import org.starficz.starui.vanilla.forceSlideOut
import org.starficz.starui.vanilla.getChildrenNonCopy
import org.starficz.starui.vanilla.isEnabled
import org.starficz.starui.vanilla.isSlidIn
import org.starficz.starui.vanilla.isSlidOut
import org.starficz.starui.vanilla.isSliding
import org.starficz.starui.vanilla.isSlidingIn
import org.starficz.starui.vanilla.lastComponent
import org.starficz.starui.vanilla.left
import org.starficz.starui.vanilla.mouseoverHighlightFader
import org.starficz.starui.vanilla.removeFromOverlay
import org.starficz.starui.vanilla.renderShadows
import org.starficz.starui.vanilla.right
import org.starficz.starui.vanilla.setAbsoluteLocation
import org.starficz.starui.vanilla.setLocation
import org.starficz.starui.vanilla.setMouseOverPad
import org.starficz.starui.vanilla.setSize
import org.starficz.starui.vanilla.setSlideData
import org.starficz.starui.vanilla.setTooltipOffsetFromCenter
import org.starficz.starui.vanilla.setTooltipPositionRelativeToAnchor
import org.starficz.starui.vanilla.shadowColor
import org.starficz.starui.vanilla.slideIn
import org.starficz.starui.vanilla.slideOut
import org.starficz.starui.vanilla.top
import org.starficz.starui.vanilla.topAncestor
import org.starficz.starui.vanilla.withAlpha
import org.starficz.starui.vanilla.xAlignOffset
import org.starficz.starui.vanilla.yAlignOffset
import java.awt.Color

private fun toolkitAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

fun registerToolkitTests() {
    StarUITestSuite.group("toolkit-component") {
        lateinit var button: ButtonAPI
        lateinit var label: LabelAPI
        lateinit var holder: CustomPanelAPI
        holder = root.CustomPanel(Size.exact(300f, 200f), toolkitAt(40f, 40f)) {
            button = Button("Toolkit", modifier = toolkitAt(10f, 10f))
            label = Text("A label", modifier = toolkitAt(10f, 60f))
        }
        frame()

        near(button.left, button.x, "left is x")
        near(button.right, button.x + button.width, "right is x + width")
        near(button.bottom, button.y, "bottom is y")
        near(button.top, button.y + button.height, "top is y + height")
        near(button.centerX, button.x + button.width / 2f, "centerX is the middle")
        near(button.centerY, button.y + button.height / 2f, "centerY is the middle")

        val fader = button.fader
        expect(fader != null, "fader reads vanilla's fader")
        button.fader = fader
        expect(button.fader === fader, "and sets it")
        button.mouseoverHighlightFader   // reads (a button has one)
        expect(button.mouseoverHighlightFader != null, "mouseoverHighlightFader reads the hover fader")
        expect(button.topAncestor != null, "topAncestor finds the screen above a child")

        button.isEnabled = false
        expect(!button.isEnabled, "isEnabled set false reads false")
        button.isEnabled = true
        expect(button.isEnabled, "and back to true")
        // the extension on UIComponentAPI and ButtonAPI's own member read the same flag
        val asComponent: com.fs.starfarer.api.ui.UIComponentAPI = button
        button.isEnabled = false
        expect(!asComponent.isEnabled, "the UIComponentAPI extension reads the button's own enabled flag")
        button.isEnabled = true

        button.setTooltipOffsetFromCenter(5f, 5f)
        button.setTooltipPositionRelativeToAnchor(0f, 0f, holder)
        button.addTooltip(TooltipMakerAPI.TooltipLocation.ABOVE, 200f) { it.addPara("tip", 0f) }
        expect(button.invoke("getTooltipLogic") != null, "addTooltip sets vanilla's tooltip on the component")
        button.setMouseOverPad(2f, 2f, 2f, 2f)

        button.setSlideData(0f, -50f, 0.2f, 0.2f)
        button.forceSlideOut()
        expect(button.isSlidOut, "forceSlideOut: slid out at once")
        button.forceSlideIn()
        expect(button.isSlidIn, "forceSlideIn: back in at once")
        button.slideOut()
        button.isSliding   // reads
        frame(30)
        expect(button.isSlidOut, "slideOut slides it out over its duration")
        button.slideIn()
        expect(button.isSlidingIn, "slideIn: sliding in")
        frame(30)
        expect(button.isSlidIn, "and in place once done")

        val before = button.xAlignOffset
        button.xAlignOffset = before + 7f
        frame()
        near(button.xAlignOffset, before + 7f, "xAlignOffset round trips")
        button.xAlignOffset = before
        button.yAlignOffset = button.yAlignOffset
        button.setAbsoluteLocation(500f, 400f)
        frame()
        near(button.x, 500f, "setAbsoluteLocation moves the bottom left to x")
        near(button.y, 400f, "and y")

        val spacer = root.Spacer(Size.exact(10f, 10f), toolkitAt(400f, 40f))
        spacer.setSize(40f, 30f)
        near(spacer.width, 40f, "setSize sizes")
        spacer.setLocation(10f, 10f)

        label.autoSize()
        expect(label.width > 0f, "autoSize sizes a label to its text")
        expect(Color(10, 20, 30).withAlpha(40).alpha == 40, "withAlpha keeps the colour, changes the alpha")
        expect(VanillaColors.base.alpha == 255, "VanillaColors reads the faction colours")
        screenshot("final")
    }

    StarUITestSuite.group("toolkit-panel") {
        lateinit var first: ButtonAPI
        lateinit var last: LabelAPI
        val holder = root.CustomPanel(Size.exact(300f, 200f), toolkitAt(40f, 40f)) {
            first = Button("First", modifier = toolkitAt(10f, 10f))
            last = Text("Last", modifier = toolkitAt(10f, 60f))
        }
        frame()
        expect(holder.children == listOf(first, last), "children lists the children in order")
        expect(holder.getChildrenNonCopy() == listOf(first, last), "getChildrenNonCopy is vanilla's live list")
        expect(holder.lastComponent === last, "lastComponent is the one added last")
        expect(holder.findChildWithMethod("getText") != null, "findChildWithMethod finds a child by a method")
        expect(holder.allChildrenWithMethod("getText").size == 2, "allChildrenWithMethod finds both (button and label)")
        expect(holder.allChildrenWithMethod("noSuchMethodAnywhere").isEmpty(), "and none for a missing method")
    }

    StarUITestSuite.group("toolkit-scroll") {
        val column = root.Column(Size.exact(200f, 100f), toolkitAt(40f, 40f), overflow = Overflow.ScrollVertical) {
            repeat(20) { Text("Row $it") }
        }
        frame()
        val scroll = column.scrollPanel
        expect(scroll != null, "a scrolling Column has a scroll panel")
        scroll!!
        scroll.renderShadows = false
        expect(!scroll.renderShadows, "renderShadows set false reads false")
        scroll.renderShadows = true
        scroll.shadowColor = Color.RED
        expect(scroll.shadowColor == Color.RED, "shadowColor round trips")
        val overlay = root.Spacer(Size.exact(20f, 20f))
        root.removeComponent(overlay)
        // vanilla keeps overlays in a list of its own (they get no parent): the calls must reach it
        expect(thrown { scroll.addToOverlay(overlay) } == null, "addToOverlay reaches vanilla's overlay list")
        expect(thrown { scroll.removeFromOverlay(overlay) } == null, "and removeFromOverlay")
        frame()
    }

    StarUITestSuite.group("toolkit-plugin-geometry") {
        var seen: List<Float>? = null
        val panel = root.CustomPanel(Size.exact(120f, 80f), toolkitAt(40f, 40f).onLayout {
            // plugin members: no import of the vanilla package needed
            seen = listOf(x, y, width, height, left, right, top, bottom, centerX, centerY)
        })
        frame()
        val plugin = StarUIPanelPlugin.getAttached(panel)!!
        expect(seen != null, "onLayout ran")
        near(seen!![2], 120f, "width is the panel's")
        near(seen!![3], 80f, "height is the panel's")
        near(plugin.x, panel.x, "x is the panel's")
        near(plugin.right, panel.x + 120f, "right is x + width")
        near(plugin.top, panel.y + 80f, "top is y + height")
        near(plugin.centerY, panel.y + 40f, "centerY is the middle")
    }
}
