// Layers (Overlays.kt): showLayer and Layer over a screen. The suite runs before any screen exists, so the layers
// open on the group's root (Layers.testHost) and the test drives it with frame() and Input; pause, real keys, the
// campaign's dialogs and its core rebuild are checked in the live "layers" group.
// Compiled in the same Kotlin module as the framework (run-test-suite.ps1), so `internal` members are reachable.

import com.fs.starfarer.api.ui.CustomPanelAPI
import org.lwjgl.input.Keyboard
import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.runtime.StarUIPanelPlugin
import org.starficz.starui.vanilla.children
import org.starficz.starui.window.*

/** Runs [body] with layers opening on this group's root, without fades; puts the defaults back after. */
private fun TestContext.onRoot(fades: Boolean = false, body: () -> Unit) {
    val oldScale = Layers.fadeScale
    Layers.testHost = root
    Layers.fadeScale = if (fades) 1f else 0f
    try {
        body()
    } finally {
        Layers.testHost = null
        Layers.fadeScale = oldScale
    }
}

private fun TestContext.escape() = Input.sendList(root, Input.keyDown(Keyboard.KEY_ESCAPE))
private fun TestContext.clickAt(x: Float, y: Float) = Input.sendList(root, Input.down(x, y), Input.up(x, y))
private fun CustomPanelAPI.isLast(child: CustomPanelAPI) = children.lastOrNull() === child
private fun disposed(layer: StarUILayer) = StarUIPanelPlugin.getAttached(layer.panel)?.isDisposed != false

fun registerLayerTests() {
    StarUITestSuite.group("layer-imperative") {
        onRoot {
            var under = 0
            var inside = 0
            var closed = 0
            // a clickable area under where the layer will be, made first
            root.Box(Size.exact(200f, 100f), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f)).onClick { under++ })
            frame()
            clickAt(140f, screenH - 90f)
            expect(under == 1, "before any layer, the area takes its click ($under)")

            val layer = showLayer(onClose = { closed++ }) {
                Box(Size.exact(200f, 100f), Modifier.align(Alignment.Center).onClick { inside++ })
            }
            frame()
            expect(layer.isOpen && root.isLast(layer.panel), "showLayer puts its panel on top of the screen")
            near(layer.panel.position.width, screenW, "the layer covers the screen's width")
            near(layer.panel.position.height, screenH, "and its height")
            clickAt(140f, screenH - 90f)
            expect(under == 1, "a modal layer takes the click meant for what is under it ($under)")
            clickAt(screenW / 2, screenH / 2)
            expect(inside == 1, "its content takes its own clicks ($inside)")
            val key = Input.keyDown(Keyboard.KEY_C, 'c')
            Input.sendList(root, key)
            expect(layer.isOpen, "another key does not close it")
            screenshot("modal")

            escape()
            frame()
            expect(!layer.isOpen && closed == 1, "Escape closes it, onClose runs once (open ${layer.isOpen}, onClose $closed)")
            expect(layer.panel.parent == null, "and its panel is gone")
            clickAt(140f, screenH - 90f)
            expect(under == 2, "what was under it takes clicks again ($under)")
            layer.close()
            expect(closed == 1, "a second close does nothing")

            // stacking: Escape reaches only the top one
            val first = showLayer { Text("first", modifier = Modifier.align(Alignment.TopCenter)) }
            val second = showLayer { Text("second", modifier = Modifier.align(Alignment.Center)) }
            frame()
            expect(root.isLast(second.panel), "the second layer is above the first")
            escape()
            frame()
            expect(!second.isOpen && first.isOpen, "Escape closes only the top layer (first ${first.isOpen}, second ${second.isOpen})")
            escape()
            frame()
            expect(!first.isOpen, "a second Escape the one below")

            // closed from its own content
            val selfClosing = showLayer { l -> Box(Size.exact(100f, 50f), Modifier.align(Alignment.Center).onClick { l.close() }) }
            frame()
            clickAt(screenW / 2, screenH / 2)
            frame()
            expect(!selfClosing.isOpen && selfClosing.panel.parent == null, "content closes its layer through the handle")

            val noEscape = showLayer(closeOnEscape = false) { Text("stays") }
            frame()
            escape()
            expect(noEscape.isOpen, "closeOnEscape = false keeps it on Escape")
            noEscape.close()
            frame()

            // non-modal: clicks pass except over its content, keys pass
            val toast = showLayer(modal = false) {
                Box(Size.exact(200f, 60f), Modifier.align(Alignment.Center).onClick { inside++ })
            }
            frame()
            clickAt(140f, screenH - 90f)
            expect(under == 3, "a non-modal layer lets clicks outside its content through ($under)")
            val insideBefore = inside
            clickAt(screenW / 2, screenH / 2)
            expect(inside == insideBefore + 1 && under == 3, "and takes the ones on its content ($inside, under $under)")
            val passing = Input.keyDown(Keyboard.KEY_C, 'c')
            Input.sendList(root, passing)
            expect(!passing.isConsumed, "keys pass a non-modal layer")
            escape()
            expect(toast.isOpen, "Escape does not close a non-modal layer by default")
            toast.close()
            frame()

            val inBuilder = thrown { root.Column { showLayer { } } }
            expect(inBuilder is IllegalStateException && inBuilder.message!!.contains("Layer("),
                "showLayer in a builder throws and points to Layer (${inBuilder?.message?.take(60)})")
            val outside = thrown { root.Layer(onDismissRequest = {}) { } }
            expect(outside is IllegalStateException && outside.message!!.contains("showLayer"),
                "Layer outside a builder throws and points to showLayer (${outside?.message?.take(60)})")
            frame()
        }
    }

    StarUITestSuite.group("layer-declarative") {
        onRoot {
            val show = UIState(false)
            val title = UIState("one")
            var dismissed = 0
            var layer: StarUILayer? = null
            var label: com.fs.starfarer.api.ui.LabelAPI? = null
            val column = root.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(40f, 40f))) {
                Text("caller")
                val t = title.value
                if (show.value) layer = Layer(onDismissRequest = { dismissed++; show.value = false }) {
                    label = Text("Layer $t", modifier = Modifier.align(Alignment.Center))
                }
            }
            frame()
            expect(layer == null, "no Layer while the state is false")
            show.value = true
            frame()
            val opened = layer!!
            expect(opened.isOpen && root.isLast(opened.panel), "the state turning true opens the layer on top")
            expect(label?.text == "Layer one", "with its content (${label?.text})")

            title.value = "two"
            frame()
            expect(layer === opened && opened.isOpen, "a rebuild that calls it again keeps the same layer")
            expect(label?.text == "Layer two", "and runs its content with the new values (${label?.text})")

            escape()
            expect(dismissed == 1, "Escape calls onDismissRequest ($dismissed)")
            frame()
            expect(!opened.isOpen, "the rebuild that leaves it out closes it")
            frame()
            expect(opened.panel.parent == null && disposed(opened), "and its panel goes on the next frame, disposed")

            show.value = true
            frame()
            val again = layer!!
            expect(again !== opened && again.isOpen, "true again opens a new layer")
            again.close()
            frame()
            expect(!again.isOpen, "a close from code closes it")
            title.value = "three"
            frame()
            expect(layer !== again && layer!!.isOpen, "until the builder runs again and calls it: then it is back")

            StarUIPanelPlugin.getAttached(column)!!.dispose()
            expect(!layer!!.isOpen, "disposing the UI that asked for it closes it")
            frame()
            expect(layer!!.panel.parent == null && disposed(layer!!), "and the layer's panel goes, disposed")

            // several in one builder, keyed: removing the first keeps the second
            val names = UIState(listOf("a", "b"))
            val byName = HashMap<String, StarUILayer>()
            root.Box(Size.exact(10f, 10f), Modifier.anchor(Anchor.inside.bottomLeft.ofParent())) {
                for (n in names.value) key(n) { byName[n] = Layer(onDismissRequest = {}, modal = false) { Text(n) } }
            }
            frame()
            val b = byName["b"]!!
            names.value = listOf("b")
            frame()
            expect(!byName["a"]!!.isOpen && b.isOpen && byName["b"] === b, "keyed layers: dropping one keeps the other")
            names.value = emptyList()
            frame()

            // a Layer asked for from inside a layer closes with it
            val outer = UIState(true)
            var inner: StarUILayer? = null
            var outerLayer: StarUILayer? = null
            root.Box(Size.exact(10f, 10f), Modifier.anchor(Anchor.inside.bottomRight.ofParent())) {
                if (outer.value) outerLayer = Layer(onDismissRequest = { outer.value = false }) {
                    Column(modifier = Modifier.align(Alignment.Center)) {
                        Text("outer")
                        inner = Layer(onDismissRequest = {}) { Text("inner", modifier = Modifier.align(Alignment.Center)) }
                    }
                }
            }
            frame()
            expect(inner!!.isOpen && root.isLast(inner!!.panel), "a Layer in a layer opens above it")
            escape()
            expect(outer.value, "Escape reaches the inner layer first (it has nothing to dismiss to)")
            outer.value = false
            frame()
            expect(!outerLayer!!.isOpen && !inner!!.isOpen, "closing the outer layer closes the inner one with it")
            frame()
        }
    }

    StarUITestSuite.group("layer-fade-and-readd") {
        onRoot(fades = true) {
            val layer = showLayer { Text("fading", modifier = Modifier.align(Alignment.Center)) }
            expect(layer.panel.opacity < 0.5f, "a layer starts faded out (${layer.panel.opacity})")
            frame(30)
            near(layer.panel.opacity, 1f, "and fades in", tolerance = 0.01f)
            layer.close()
            expect(!layer.isOpen && layer.panel.parent === root, "closing starts a fade out: not open, still there")
            frame(30)
            expect(layer.panel.parent == null, "then it is taken off")
            expect(disposed(layer), "and disposed")
        }
        onRoot {
            val low = showLayer { Text("low") }
            val high = showLayer(modal = false) { Text("high") }
            frame()
            // what the campaign's core rebuild does: every child off the screen panel, then a new core UI
            root.removeComponent(low.panel)
            root.removeComponent(high.panel)
            Layers.afterCoreUICreated()
            expect(Layers.hasPending, "a new core UI with layers off the screen marks them to go back")
            frame()
            val kids = root.children
            expect(low.panel.parent === root && high.panel.parent === root, "they are back on the next render")
            expect(kids.indexOf(low.panel) < kids.indexOf(high.panel) && kids.last() === high.panel, "in their order, on top")
            Layers.afterCoreUICreated()
            expect(!Layers.hasPending, "a core UI made while they are in place (an interaction dialog's) changes nothing")
            low.close(); high.close()
            frame()
        }
    }
}
