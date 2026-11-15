# Setup and getting UI on screen

Contents: [Dependencies](#dependencies) · [Compiling](#compiling) · [Imports](#imports) ·
[Where UI can go](#where-ui-can-go) · [StarUIPlugin screens](#staruiplugin-screens) ·
[A layer over a screen](#a-layer-over-a-screen) · [Panels vanilla hands you](#panels-vanilla-hands-you) ·
[inject into a vanilla panel](#inject-into-a-vanilla-panel)

## Dependencies

```json
"dependencies": [
    {"id": "staruiframework", "name": "StarUIFramework"},
    {"id": "lw_lazylib", "name": "LazyLib"},
    {"id": "patchlib", "name": "! PatchLib !"}
]
```

StarUI needs LazyLib (the Kotlin runtime) and PatchLib (its hooks); the player installs all three.

## Compiling

- Compile against `mods/StarUIFramework/jars/StarUIFramework.jar`, `starsector-core` jars (`starfarer.api.jar`;
  `lwjgl.jar`, `lwjgl_util.jar` for GL and `Keyboard`; `log4j-1.2.9.jar` for `Global.getLogger`; `json.jar`) and
  LazyLib's (runtime: `mods/LazyLib/jars/internal/Kotlin-Runtime.jar`; the folder may be `LazyLib-3.0.0` etc.: use
  the user's). `mods/PatchLib/jars/PatchLibAPI.jar` only for your own hooks (`inject`).
- **Kotlin**: LazyLib 3.1 runs 2.4.21. Compiler **2.3 or newer** (StarUI and LazyLib carry 2.4 metadata; older stops
  with "metadata version 2.4.0, can read up to 2.3.0"). `-api-version` at most 2.4.
- Never bundle StarUI's classes or a Kotlin stdlib in the mod's jar.

### With Gradle

Tested with Gradle 8.13, Kotlin plugin 2.4.21. `gradle.properties`:

```properties
# the game runs LazyLib's Kotlin stdlib: compile against it, never bundle one
kotlin.stdlib.default.dependency=false
```

`build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm") version "2.4.21"   // 2.3 or newer reads StarUI's and LazyLib's Kotlin 2.4 metadata
}

repositories { mavenCentral() }

val starsector = "C:/Program Files (x86)/Fractal Softworks/Starsector"   // your game folder
val core = "$starsector/starsector-core"
val mods = "$starsector/mods"

dependencies {
    // the game and the mods yours depends on provide all of these at runtime: compile-only, never bundled
    compileOnly(files(
        "$core/starfarer.api.jar", "$core/lwjgl.jar", "$core/lwjgl_util.jar", "$core/log4j-1.2.9.jar", "$core/json.jar",
        "$mods/LazyLib/jars/LazyLib.jar", "$mods/LazyLib/jars/LazyLib-Kotlin.jar",
        "$mods/LazyLib/jars/internal/Kotlin-Runtime.jar",   // the Kotlin stdlib the game runs (LazyLib 3.1: 2.4.21)
        "$mods/StarUIFramework/jars/StarUIFramework.jar",
        // "$mods/PatchLib/jars/PatchLibAPI.jar",           // only for your own PatchLib hooks (inject)
    ))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        apiVersion.set(KotlinVersion.KOTLIN_2_4)        // no stdlib calls newer than LazyLib's runtime
        languageVersion.set(KotlinVersion.KOTLIN_2_4)
    }
}
java { targetCompatibility = JavaVersion.VERSION_17; sourceCompatibility = JavaVersion.VERSION_17 }

tasks.jar {
    archiveFileName.set("MyMod.jar")                    // the name mod_info.json lists under "jars"
    destinationDirectory.set(layout.projectDirectory.dir("jars"))
}
```

`gradle jar` writes `jars/MyMod.jar`. Depend on `lw_lazylib` 3.1 in `mod_info.json`
(`"version": {"major": "3", "minor": "1", "patch": "0"}`) so older LazyLibs are told to update.

### A Java mod

StarUI is a Kotlin DSL: write the UI in Kotlin, keep the rest in Java; both go in one jar and call each other.

- Java 17 (the game's); a `--release 8` mod moves to 17. Kotlin `jvmTarget` = Java target.
- Gradle: add the Kotlin plugin, the properties line and `kotlin { }` block above; keep `mavenCentral()` (the
  plugin needs it). Sources outside `src/main/java`:
  `sourceSets.main { java.setSrcDirs(listOf("src")); kotlin.setSrcDirs(listOf("src")) }`.
- IntelliJ without Gradle: "Configure Kotlin" for the module, but use LazyLib's `Kotlin-Runtime.jar` (compile-only),
  not a bundled stdlib.
- From Java: `StarUIManager.INSTANCE.registerPlugin(new MyUIPlugin());`, `MyUIPlugin` in Kotlin.

## Imports

| Package | Holds |
|---|---|
| `org.starficz.starui` | `Size`, `BoundedSize`, `ImageSize`, `Axis`, `Alignment`, `TextAlign`, `VGrow`/`HGrow`, `ContentScale`, `Overflow`, `AxisOverflow`, `Anchor`, `UIState`, `mutableStateOf`, `TextOverflow`, `StarUITheme` and `*Theme`, `StarUIManager`, `StarUIPlugin`, `StarUIBuilder`, `inject`, `injection`, `removeInjection`, `InjectPlacement`, `showStarUIPanel`, `requestLayout`, `dumpLayout`, `findAll`, `find`, `FoundComponent`, `StarUITesting` |
| `.components` | every component, `Theme`, `key`, `FilterGroup`, `RadioGroup`, `TableSort`, `.scrollPanel`, `.sectorMap` |
| `.window` | `Layer`, `showLayer`, `StarUILayer`, `Dialog`, `showDialog`, `StarUIDialog`, `DialogStatic`, `showToast`, `StarUIToast` |
| `.modifier` | `Modifier` and extensions, `PaddingValues`, `VanillaFrame` |
| `.vanilla` | `Font`, `ButtonFont`, `VanillaColors`, `HoloBackground`, `Boxed*`, the toolkit |
| `.runtime` | `StarUIPanelPlugin` (receiver of `Modifier.plugin { }` and render/lifecycle modifiers) |

Everything else is internal; check packages in `api/`.

## Where UI can go

Every component is an extension on `UIPanelAPI` and adds a real vanilla object to the panel it is called on:

```kotlin
somePanel.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(20f, 20f))) {
    Text("Hello")
    Button("Close") { closed.value = true }
}
```

Host from the design ([layout.md](layout.md#hosts)), then:

| Host | Way in |
|---|---|
| A whole screen: title, campaign UI, a core tab, combat UI, combat command map, mission refit | `StarUIPlugin` |
| A window over any screen (config, editor, confirmation) | `Layer` in a builder, `showLayer` elsewhere |
| A panel the API gives you (custom dialog, interaction side panel, intel or tooltip panel) | call StarUI on it |
| A vanilla sub-panel no screen covers | `inject` from a PatchLib hook |

## StarUIPlugin screens

```kotlin
object MyScreens : StarUIPlugin {
    override val addPanelToRefitTab: StarUIBuilder = {
        Button("My tool", modifier = Modifier.anchor(Anchor.inside.topRight.ofParent(10f, 10f))) { toolOpen.value = true }
    }
}

// in the mod's ModPlugin.onApplicationLoad
StarUIManager.registerPlugin(MyScreens)
```

- Builders: `addPanelToTitleScreen`, `addPanelToCampaignUI`, `addPanelToCharacterTab`, `addPanelToFleetTab`,
  `addPanelToRefitTab`, `addPanelToCargoTab`, `addPanelToMapTab`, `addPanelToIntelTab`, `addPanelToOutpostsTab`,
  `addPanelAboveCombatShipInfo`, `addPanelBelowCombatShipInfo`, `addPanelToCombatScreen`, `addPanelToCombatWarroom`
  (exact moments: `api/org/starficz/starui/StarUIPlugin.kt`).
- Each runs in a full-screen `CustomPanelAPI`, added when vanilla builds the screen and again after it refills it; no
  polling; reactive. `null` adds nothing.
- **Register in `onApplicationLoad`**; screens built earlier get their panel only on their next build.
- Place with `Modifier.anchor`; no background on the screen panel.
- `StarUIManager.coreUI`, `currentCoreUITabID`, `currentCoreUITabPanel`, `combat*` read the screen showing now.

## A layer over a screen

A **layer** is a full-screen StarUI panel over whatever shows (title, campaign with its dialogs and tabs, battle).
Layers stack in open order; their tooltips show above them.

- **Modal** (default), as under vanilla's dialogs: dims (`dim`, theme `layer.dim` 0.6), takes every key and click
  (map, hotkeys, lower layers), pauses campaign or battle (the old pause returns with the last pausing layer); Escape
  dismisses the top one.
- **Non-modal** (`modal = false`): no dim or pause; keys pass; only clicks over its content are taken.
- The panel is a full-screen `Box`: place the window with `Modifier.align(Alignment.Center)`.

`Layer` in a builder shows while the builder calls it (Compose's `Dialog`):

```kotlin
val confirmDelete = UIState(false)
Column {
    Button("Delete") { confirmDelete.value = true }
    if (confirmDelete.value) Layer(onDismissRequest = { confirmDelete.value = false }) {
        Section("Delete plan", Size.exact(400f, 160f), Modifier.align(Alignment.Center)) {
            Text("The plan and its notes are deleted.")
            Button("Delete") { deletePlan(); confirmDelete.value = false }
        }
    }
}
```

- Closes (short fade) when a rebuild leaves it out or its UI goes away; Escape calls `onDismissRequest`.
- Reruns keep the layer and rerun its content; several are told apart by order or `key(...)`; nested ones close
  with their parent.

`showLayer` from a handler, script, ability or hotkey stays until closed:

```kotlin
Button("Doctrine") {
    showLayer(onClose = { saveDoctrine() }) { layer ->
        Section("Doctrine", Size.exact(620f, 700f), Modifier.align(Alignment.Center)) {
            // ... content ...
            Button("Close") { layer.close() }
        }
    }
}
```

`showLayer` in a builder throws, `Layer` outside one too. A layer belongs to its screen (leaving it closes it,
`isOpen` false). "Game paused" and combat's top-right panel stay above layers; other mods' input listeners still see
input first. Keep window state outside the UI so it reopens as it was.

### A dialog

`Dialog` is vanilla's custom dialog window on a modal layer, identical to `showCustomDialog` to the pixel. Content is
a `Box` 10 px in; the window is `(w + 20) x (h + 55)` inside its 7 px frame; `confirmText` / `cancelText` make the
150 x 25 Orbitron 20 buttons bottom right (confirm shows `[G]`).

```kotlin
val confirmDismiss = UIState(false)
Row {
    Button("Dismiss") { confirmDismiss.value = true }
    if (confirmDismiss.value) Dialog(onDismissRequest = { confirmDismiss.value = false },
        confirmText = "Dismiss", cancelText = "Cancel", onConfirm = { dismiss(team); confirmDismiss.value = false }) {
        Text("Dismiss ${team.name}?", font = Font.INSIGNIA_21, highlightedText = listOf(team.name to VanillaColors.highlight))
    }
}
```

`showDialog` from a handler or script closes itself on confirm or cancel:

```kotlin
Button("Survey office", Size.exact(140f, 20f), cutStyle = CutStyle.ALL) {
    // the window is the content plus 20 x 55 and its 7 px frame; it never comes closer than 50 px to the screen's edge
    showDialog(Size.exact(1040f, 640f), confirmText = "Return") { SurveyPanel() }
}
```

- `size` is the content's, per axis. Wrap (default) fits the window to the content, before the first frame and on
  every change. A wrapping width has nothing for a fill-width Text to fill: a paragraph popup is
  `showDialog(Size.mixed(Axis.Exact(400f), Axis.WrapContent)) { Text(body, Size.fillMaxWidth()) }`. Fill: as large as
  the screen allows (50 px margin; larger sizes shrink).
- Confirm button, Enter, Space, G: `onConfirm`; cancel button, Escape (even without the button): `onCancel`. In
  `Dialog` both default to `onDismissRequest`. A focused text field takes Space first.
- `{ dialog -> }`: `dialog.confirm()`, `dialog.cancel()` from the content.
- Dialogs stack: a `Dialog` in a dialog's content opens above it, closes with it; Escape closes the top one. Every
  screen; pauses (`pause`).
- `static`: `DialogStatic.Vanilla` (default), `CancelOnly`, `None`, or `DialogStatic(onOpen, onConfirm, onCancel)`.
  `contentKey` swaps content and buttons through vanilla's static (pages).
- F2 opens the codex (`codexEntryId`); a click outside flickers the window.
- `background`: `HoloBackground.Default` (vanilla custom dialog's, see-through, inner frame line showing). Over busy
  screens: `HoloBackground(200, 225)` (save dialog, nearly opaque) or `HoloBackground.Dialog` (market, opaque).

### A toast

`showToast("Doctrine saved")` or `showToast(duration = 5f) { Row { ... } }`. Vanilla has none: prefer the campaign's
`campaignUI.addMessage` and combat's message area; toasts are for the rest (title screen, rich content, over a dialog).

- A small panel on its own layer above all others. Default bottom right (in the campaign above the minimap, else
  10 px from the corner); `alignment`: any of the nine; top centre clears "Game paused".
- Moves like Windows notifications: slides and fades in from its side, newest nearest the edge, older ones glide
  aside; slides out when done or clicked. Three per place; the rest wait behind a "+N more" chip.
- `duration`: real seconds (runs while paused, stops under the mouse); 0 keeps it until clicked or closed.
  `StarUIToast`: `close()`, `isOpen`.
- More presence: a [toast content recipe](recipes.md#toast-content). Text wraps at 400 px; `color`
  (`VanillaColors.negative` for failure).
- Handlers and scripts only (throws in a builder).

## Panels vanilla hands you

Any API `UIPanelAPI` takes StarUI calls: a custom dialog's panel (`showCustomDialog`), an interaction dialog's side
panel (`getVisualPanel().showCustomPanel`), a `createCustom` panel. Build when the API hands it over. Hosts' sizes and
limits: [layout.md](layout.md#hosts).

```kotlin
dialog.visualPanel.showStarUIPanel(440f, 600f) { RequisitionsPanel() }   // black behind it by default
```

A vanilla dialog sized to StarUI content: `Modifier.fitVanillaDialog()` on the content (a wrapping container that is
the whole panel). After each layout the panel takes the content's size and the dialog grows or shrinks by the same,
centred, buttons in place; the size passed to vanilla only matters for the first frame.

```kotlin
dialog.showCustomDialog(300f, 100f, object : CustomDialogDelegate {
    override fun createCustomDialog(panel: CustomPanelAPI, callback: CustomDialogDelegate.CustomDialogCallback) {
        panel.Column(modifier = Modifier.fitVanillaDialog(), spacing = 6f) {
            Text("Recruit ${person.name.fullName}?", font = Font.INSIGNIA_21)
            if (showTerms.value) Text(terms, Size.mixed(Axis.Exact(420f), Axis.WrapContent))
            Button(if (showTerms.value) "Hide terms" else "Show terms") { showTerms.value = !showTerms.value }
        }
    }
    // hasCancelButton, getConfirmText, customDialogConfirm, ... as usual
})
```

Works on any vanilla dialog (nearest ancestor with readable `sizeToInner`), including content injected as a whole
dialog panel.

## inject into a vanilla panel

For one vanilla sub-panel no screen covers: adds one StarUI `AnchorPanel` (its own scope) and builds in it. Call it
when vanilla builds the panel: a PatchLib `@After` on the building method or constructor, `self` as receiver. The
hook is PatchLib's (`patchlib` skill); class and method from game sources, never memory.

`size` is against the vanilla panel: `inject(key, Size.fillMaxSize())` covers it and follows later resizes. Without
an anchor it sits top left (vanilla's default would be bottom left); anchor to a sibling or corner otherwise.

```kotlin
// 0.98.5a-RC4: the refit's hullmod list widget, matched by its readable class name; it never clears its children
@Patch(target = ClassMatch(typeName = "com.fs.starfarer.coreui.refit.ModWidget"))
object RefitButtonPatch {
    @JvmStatic
    @After(target = MethodMatch(methodType = MethodType.CONSTRUCTOR))
    fun afterCreate(context: AfterContext) {
        val widget = context.self as? UIPanelAPI ?: return
        widget.inject("mymod:refit-button") { Button("AI Config") { ConfigDialog.show() } }
    }
}
```

- **One injection per `key` per panel** (prefix with your mod id): a live one is returned and nothing happens, so
  double hooks, rerun build methods or an every-frame script can just call it. `panel.injection(key)` finds it.
- **Survives `clearChildren()`**: it returns at the panel's next render, above vanilla's children, builder rerun.
  Hook the first build only. `removeInjection(key)`, `dispose()` or removing it ends it.
- **Read the panel inside the builder** (`this.parent`, getters, siblings), not captured objects.
- `placement = InjectPlacement.Bottom`: under the panel's children; throws on StarUI reactive containers.
- Readable class names in matchers only; matching by shape: the `patchlib` skill.
