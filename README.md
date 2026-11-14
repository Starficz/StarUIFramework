# StarUIFramework

A Compose-style Kotlin DSL for building Starsector UI out of the game's own UI elements.

**Prerelease (0.1.0).** The API may still change before 1.0.

Requires LazyLib and PatchLib. Built for Starsector 0.98.5a-RC4.

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

Every public class and function has KDoc. Ping @Starficz on the USC Discord for help.

## License

LGPL-3.0-only. See [LICENSE](LICENSE).
