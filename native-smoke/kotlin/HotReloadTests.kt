// StarUI's hot-reload refresh (runtime/HotReloadRefresh.kt): with the starsector-hotreload agent's counter present,
// a reload rebuilds every live UI scope; without it nothing is tracked and nothing changes.

import org.starficz.starui.*
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.runtime.HotReloadRefresh

private fun hotAt(x: Float, y: Float) = Modifier.anchor(Anchor.inside.topLeft.ofParent(x, y))

fun registerHotReloadTests() {
    StarUITestSuite.group("hot-reload-refresh") {
        val key = HotReloadRefresh.KEY
        val before = System.getProperty(key)
        fun setCounter(v: String) {
            System.setProperty(key, v)
            HotReloadRefresh.pollNowForTest()
        }
        try {
            // a normal game: no agent, no property
            System.clearProperty(key)
            HotReloadRefresh.resetForTest(false)
            var plainOuter = 0
            var plainInner = 0
            root.Column(Size.wrapContent(), hotAt(40f, 40f)) {
                plainOuter++
                Column { plainInner++; Text("normal game") }
            }
            frame()
            setCounter("1")
            frame()
            expect(plainOuter == 1 && plainInner == 1, "without the agent a counter change rebuilds nothing ($plainOuter, $plainInner)")

            // a hot game: the agent set the property before mods loaded
            setCounter("0")
            HotReloadRefresh.resetForTest(true)
            var outer = 0
            var inner = 0
            root.Column(Size.wrapContent(), hotAt(40f, 120f)) {
                outer++
                Column { inner++; Text("hot game") }
            }
            frame()
            expect(outer == 1 && inner == 1, "built once ($outer, $inner)")
            frame()
            expect(outer == 1, "and not again while the counter stays ($outer)")
            setCounter("1")
            frame()
            expect(outer == 2, "a reload re-runs a builder that reads no state ($outer)")
            expect(inner == 2, "and its inner builder once, with its parent ($inner)")

            // the refresh does not make containers reactive: adding from outside a builder still works
            val quiet = root.Column(Size.wrapContent(), hotAt(40f, 200f)) { Text("no state read") }
            frame()
            var threw = false
            try { quiet.Text("added from outside") } catch (e: IllegalStateException) { threw = true }
            expect(!threw, "a container that read no state stays non-reactive in a hot game")
        } finally {
            if (before == null) System.clearProperty(key) else System.setProperty(key, before)
            HotReloadRefresh.resetForTest(before != null)
        }
    }
}
