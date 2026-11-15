# StarUILib

A Compose-style Kotlin DSL for building Starsector UI out of the game's own UI elements.

**Alpha (1.0.0-alpha.2).** The API may still change before 1.0.0.

Requires LazyLib and PatchLib. Built for Starsector 0.98.5a-RC6.

## Example

```kotlin
object MyScreens : StarUIPlugin {
    override val addPanelToRefitTab: StarUIBuilder = {
        val clicks = mutableStateOf(0)
        Column(modifier = Modifier.anchor(Anchor.inside.topRight.ofParent(10f, 10f)), spacing = 5f) {
            Text("Clicked ${clicks.value} times")
            Button("Click me") { clicks.value++ }
        }
    }
}

// in your ModPlugin
StarUIManager.registerPlugin(MyScreens)
```

To add UI to a vanilla panel that isn't one of the `StarUIPlugin` screens, call `inject` on it from a PatchLib hook.

## Showcase

Press **Ctrl + Shift + U** on the title screen, in the campaign or in combat to open the showcase: a page for every
feature, each with a live demo next to the code that makes it, ready to copy. The pages for campaign parts (fleets,
cargo, the sector map) need a game loaded. Press the same keys again, or Close, to leave it.

Every public class and function has KDoc, and `jars/StarUILib.jar` carries the sources next to its classes.
If IntelliJ shows no KDoc, add the same jar as the library's sources (Project Structure > Libraries > the library >
`+` > the jar): hover and Quick Documentation then show it, and navigation opens the sources.

Forum thread: https://fractalsoftworks.com/forum/index.php?topic=36412. Ping @Starficz on the USC Discord for help.

## License

The library (`org.starficz.starui`) is LGPL-3.0-only. The samples and the showcase (`org.starficz.staruisamples`)
are CC0 1.0: copy them into your own mod freely. See [LICENSE](LICENSE); the full texts are in
[licenses/](licenses).
