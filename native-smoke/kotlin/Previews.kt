// Previews: pieces of UI to look at without running the test suite.
//
//   powershell -NoProfile -ExecutionPolicy Bypass -File native-smoke\run-test-suite.ps1 -Preview example -OutDir <fresh dir>
//
// builds the preview named "example" on a full-screen root, renders two frames and writes <OutDir>\example.png and
// <OutDir>\example.layout.txt (dumpLayout() text). Add your own with StarUITestSuite.preview("name") { ... } below, or
// keep this file untouched and pass your own file with -PreviewSource (it must define `fun registerPreviews()`).

import org.starficz.starui.Anchor
import org.starficz.starui.components.*
import org.starficz.starui.modifier.*
import org.starficz.starui.Size
import org.starficz.starui.UIState
import org.starficz.starui.vanilla.VanillaColors

fun registerPreviews() {
    // A small reactive panel: the list and the summary line follow the state.
    StarUITestSuite.preview("example") {
        val ships = UIState(listOf("Onslaught", "Eagle", "Wolf"))
        val showHidden = UIState(true)
        root.CustomPanel(Size.exact(320f, 220f), Modifier.anchor(Anchor.inside.center.ofParent()).background(VanillaColors.panel).border(VanillaColors.dark)) {
            Column(Size.fillMaxSize(), Modifier.padding(10f), spacing = 6f) {
                Text("Fleet (${ships.value.size} ships)")
                Divider()
                for (ship in ships.value) Row(Size.wrapContent(), spacing = 8f) {
                    Text(ship)
                    Button("Remove", size = Size.exact(70f, 18f)) { ships.value = ships.value - ship }
                }
                if (ships.value.isEmpty()) Text("No ships")
                Checkbox("Show hidden", bind = showHidden)
                if (showHidden.value) Text("(hidden ships are listed too)")
            }
        }
    }
}
