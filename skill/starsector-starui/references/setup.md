# Setup and getting UI on screen

Contents: [Dependencies](#dependencies) · [Compiling](#compiling) · [Imports](#imports) ·
[Where UI can go](#where-ui-can-go) · [StarUIPlugin screens](#staruiplugin-screens) ·
[A layer over a screen](#a-layer-over-a-screen) · [Panels vanilla hands you](#panels-vanilla-hands-you) ·
[inject into a vanilla panel](#inject-into-a-vanilla-panel)

## Dependencies

`mod_info.json` of the mod that uses StarUI:

```json
"dependencies": [
    {"id": "staruiframework", "name": "StarUIFramework"},
    {"id": "lw_lazylib", "name": "LazyLib"},
    {"id": "patchlib", "name": "! PatchLib !"}
]
```

StarUI itself needs LazyLib (which ships the Kotlin runtime) and PatchLib (its hooks). The player installs all
three mods.

## Compiling

- Compile against `mods/StarUIFramework/jars/StarUIFramework.jar`, the game's jars in `starsector-core`
  (`starfarer.api.jar`; `lwjgl.jar` and `lwjgl_util.jar` for GL calls in render code and `Keyboard`;
  `log4j-1.2.9.jar` for `Global.getLogger`; `json.jar` for `JSONObject`) and LazyLib's jars (the Kotlin runtime is
  `mods/LazyLib/jars/internal/Kotlin-Runtime.jar`; the folder name varies with how LazyLib was installed, such as
  `mods/LazyLib-3.0.0`: use the one in the user's mods folder). Add `mods/PatchLib/jars/PatchLibAPI.jar` only when the mod
  writes its own PatchLib hooks (for `inject`).
- **Kotlin**. The runtime Kotlin stdlib is LazyLib's: LazyLib 3.1 ships Kotlin 2.4.21. Compile with a Kotlin compiler
  **2.3 or newer** (2.4 matches LazyLib): StarUI's and LazyLib's jars carry Kotlin 2.4 metadata, and an older compiler
  stops with "metadata version 2.4.0, can read up to 2.3.0". Keep `-api-version` at 2.4 or lower (IntelliJ: Kotlin
  compiler settings, API version), so the compiler rejects stdlib calls the game cannot run.
- Do not bundle StarUI's classes or the Kotlin stdlib into the mod's jar.

### With Gradle

A working setup (Gradle 8.13, Kotlin plugin 2.4.21; it builds a StarUI screen into a jar holding only the mod's own
classes). `gradle.properties`:

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

`gradle jar` writes `jars/MyMod.jar`. Name `lw_lazylib` with version 3.1 in `mod_info.json`'s dependencies
(`"version": {"major": "3", "minor": "1", "patch": "0"}`), so a player on an older LazyLib, with an older Kotlin runtime, is told to
update.

## Imports

| Package | Holds |
|---|---|
| `org.starficz.starui` | `Size`, `BoundedSize`, `ImageSize`, `Axis`, `Alignment`, `TextAlign`, `VGrow`/`HGrow`, `ContentScale`, `Overflow`, `AxisOverflow`, `Anchor`, `UIState`, `mutableStateOf`, `TextOverflow`, `StarUITheme` and its `*Theme` classes, `StarUIManager`, `StarUIPlugin`, `StarUIBuilder`, `inject`, `injection`, `removeInjection`, `InjectPlacement`, `showStarUIPanel`, `requestLayout`, `dumpLayout` |
| `org.starficz.starui.components` | every component (`Column`, `Text`, `Button`, `Table`, ...), `Theme`, `key`, `FilterGroup`, `RadioGroup`, `TableSort`, `.scrollPanel`, `.sectorMap` |
| `org.starficz.starui.window` | windows over a screen: `Layer`, `showLayer`, `StarUILayer`, `Dialog`, `showDialog`, `StarUIDialog`, `DialogStatic`, `showToast`, `StarUIToast` |
| `org.starficz.starui.modifier` | `Modifier` and its extensions, `PaddingValues`, `VanillaFrame` |
| `org.starficz.starui.vanilla` | `Font`, `ButtonFont`, `VanillaColors`, `HoloBackground`, the `Boxed*` types, the vanilla toolkit (extensions on vanilla components) |
| `org.starficz.starui.runtime` | `StarUIPanelPlugin` (the receiver of `Modifier.plugin { }` and the render/lifecycle modifiers) |

Everything else is internal. Check the exact package of a name in `api/` before importing it.

## Where UI can go

Every component function is an extension on `UIPanelAPI`: it can be called on any panel, and it adds a real
vanilla object to that panel.

```kotlin
somePanel.Column(Size.wrapContent(), Modifier.anchor(Anchor.inside.topLeft.ofParent(20f, 20f))) {
    Text("Hello")
    Button("Close") { closed.value = true }
}
```

Pick the host from the design ([layout.md](layout.md#hosts)), then the way in:

| Host | Way in |
|---|---|
| A whole vanilla screen: title screen, campaign UI, a core tab (character, fleet, refit, cargo, map, intel, outposts), combat UI, the combat command map, the mission refit | `StarUIPlugin` |
| A window over a screen (a config dialog, an editor, a confirmation), on any screen | a layer: `Layer` in a builder, `showLayer` elsewhere |
| A custom dialog, an interaction dialog's side panel, an intel or tooltip panel, any `CustomPanelAPI` the API gives you | call StarUI on that panel |
| One specific vanilla sub-panel no screen covers (a widget inside the refit, a list in a tab) | `inject` from a PatchLib hook |

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

- The builders: `addPanelToTitleScreen`, `addPanelToCampaignUI`, `addPanelToCharacterTab`, `addPanelToFleetTab`,
  `addPanelToRefitTab`, `addPanelToCargoTab`, `addPanelToMapTab`, `addPanelToIntelTab`, `addPanelToOutpostsTab`,
  `addPanelAboveCombatShipInfo`, `addPanelBelowCombatShipInfo`, `addPanelToCombatScreen`, `addPanelToCombatWarroom`
  (check `api/org/starficz/starui/StarUIPlugin.kt` for each one's exact moment).
- Each builder runs in a full-size `CustomPanelAPI` over its screen, added when vanilla builds the screen, and again
  after vanilla clears and refills it. Nothing polls. A builder left `null` adds nothing. It is reactive: a
  `UIState` it reads rebuilds it.
- **Register in `onApplicationLoad`.** A screen built before the plugin registered gets its panel only the next time
  vanilla builds it.
- The screen panel covers the whole screen: place things with `Modifier.anchor`, and do not give it a background.
- `StarUIManager.coreUI`, `currentCoreUITabID`, `currentCoreUITabPanel` and the `combat*` getters read the screen
  that is showing now.

## A layer over a screen

A window, a picker or a notice over whatever is showing (the title screen, the campaign with its dialogs and core
tabs, a battle) is a **layer**: a full-screen StarUI panel on top of the screen, from `org.starficz.starui.window`. Layers stack in
the order they open; a tooltip from inside one shows above it.

- **Modal** (the default) is what vanilla's dialogs put under themselves: it dims the screen (`dim`, black at that
  alpha; the theme's `layer.dim`, 0.6, by default), takes every key and click so nothing under it sees them (not the map, not the
  campaign's hotkeys, not a layer below), and pauses the campaign or the battle while it is open; the pause from
  before comes back when the last pausing layer closes. Escape dismisses the top one.
- **Non-modal** (`modal = false`): no dim, no pause, keys pass through, and only the clicks and mouse moves over its
  content are taken. For a notice that stays out of the way.
- The layer's panel is a full-screen `Box`: place the window with `Modifier.align` (`Alignment.Center`).

In a builder, `Layer` is shown for as long as the builder calls it, like Compose's `Dialog`:

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

- It opens when the state turns true and closes (a short fade) when a rebuild leaves the call out, or when the UI
  that asked for it goes away. Escape calls `onDismissRequest`: that is where the state changes.
- Each run of the builder keeps the same layer and runs its content again with the new values. Several in one
  builder are told apart by order, or by `key(...)`. A `Layer` asked for from inside a layer closes with it.

From a click handler, a campaign script, an ability or a hotkey, `showLayer` opens one that stays until it is
closed; the builder gets the layer:

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

`showLayer` in a builder throws (it would open another layer each time the builder runs again), and so does `Layer`
outside one. A layer belongs to its screen: leaving it (the campaign for a battle, back to the title) takes the
layer with it, and `isOpen` reads false. The campaign's "Game paused" line and combat's top right panel stay above
every layer, as they do over vanilla's dialogs. Other mods' input listeners (`CampaignInputListener`) still see
input first, as they do with vanilla's dialogs.

Keep the window's state outside the UI (an object or the `UIState` the builder reads), so reopening shows it as it
was.

### A dialog

A window over the screen is usually vanilla's dialog window: `Dialog` puts it on a modal layer, measure for measure
the game's custom dialog (side by side with `showCustomDialog` on 0.98.5a-RC4 they are the same to the pixel). The
content is a `Box` of exactly the size you pass, 10 px in; the window is `(w + 20) x (h + 55)` inside its frame;
`confirmText` and `cancelText` make the 150 x 25 Orbitron 20 buttons at its bottom right (the confirm one shows
`[G]`, its key). In a builder:

```kotlin
val confirmDismiss = UIState(false)
Row {
    Button("Dismiss") { confirmDismiss.value = true }
    if (confirmDismiss.value) Dialog(onDismissRequest = { confirmDismiss.value = false }, 500f, 130f,
        confirmText = "Dismiss", cancelText = "Cancel", onConfirm = { dismiss(team); confirmDismiss.value = false }) {
        Text("Dismiss ${team.name}?", font = Font.INSIGNIA_21, highlightedText = listOf(team.name to VanillaColors.highlight))
    }
}
```

From a click handler or a campaign script, `showDialog` opens one that closes itself when confirmed or cancelled:

```kotlin
const val W = 1040f   // the window is (W + 20) x (H + 55) plus its 7 px frame: fits 1280 x 768
const val H = 640f

Button("Survey office", Size.exact(140f, 20f), cutStyle = CutStyle.ALL) {
    showDialog(W, H, confirmText = "Return") { SurveyPanel() }
}
```

- The confirm button, Enter, Space and G call `onConfirm`; the cancel button and Escape call `onCancel` (Escape
  even with no cancel button). In `Dialog` both default to `onDismissRequest`, where the state changes. A text
  field in the content that has the keyboard takes Space first.
- The builder's parameter (`{ dialog -> }`) confirms or cancels from the content: `dialog.confirm()`,
  `dialog.cancel()`.
- Dialogs stack: a confirmation `Dialog` inside a dialog's content opens above it, closes with it, and Escape closes
  the top one only. They work on every screen and pause the campaign or battle (`pause`).
- `static` picks which opens and closes show the window's burst of static: `DialogStatic.Vanilla` (all, the
  default), `CancelOnly`, `None`, or `DialogStatic(onOpen, onConfirm, onCancel)`. `contentKey` swaps the content
  (buttons too) behind vanilla's static transition, for pages.
- F2 opens the codex (at `codexEntryId`, or as last left), as vanilla's dialogs do. A click outside the window
  flickers it.
- `background`: `HoloBackground.Dialog` (the default) is the opaque one of vanilla's market pickers; a busy panel
  under the window (a core tab) stays hidden. `HoloBackground.Default` is the see-through one vanilla's custom
  dialog keeps, for a window over a calm screen that should match it exactly.

### A toast

A notice that goes away by itself: `showToast("Doctrine saved")`, or `showToast(duration = 5f) { Row { ... } }` for
content of your own. Vanilla has none: prefer the campaign's message list (`campaignUI.addMessage`) and combat's
message area for what they can show; a toast is for the rest (the title screen, rich content, a notice over a
dialog).

- A small panel (vanilla's panel background and border) over everything, on the toasts' own layer, which stays
  above every other layer. By default at the bottom right: in the campaign stacked up from just above the minimap,
  elsewhere 10 px from the corner. `alignment` picks any of the nine `Alignment` places; the top centre leaves room
  for "Game paused".
- It moves like Windows' notifications: it slides in from the side of the screen its place is on (vanilla's slide,
  which fades it in too), the newest nearest the edge, and the older ones glide aside; it slides out when its time
  is up or it is clicked, and the others glide into the gap. At most three show at one place; more wait for a slot,
  counted by a gray "+N more" chip nearest the edge, under the newest toast.
- `duration` is real time in seconds (it runs while the game is paused, and stops while the mouse is over the
  toast); 0 keeps it until it is clicked or closed. The returned `StarUIToast` has `close()` and `isOpen`.
- For a notice with more presence (an icon, a title in the faction colour, an accent bar), pass one of the
  [toast content recipes](recipes.md#toast-content) as the content.
- Text wraps at 400 px; `color` colours it (`VanillaColors.negative` for a failure).
- Called from a handler or a script, not a builder (it throws there: a builder running again would show it again).

## Panels vanilla hands you

Any `UIPanelAPI` from the API takes StarUI calls, for example the panel of a custom dialog
(`InteractionDialogAPI.showCustomDialog`), an interaction dialog's visual side panel
(`getVisualPanel().showCustomPanel`), or a `CustomPanelAPI` you created with `Global.getSettings().createCustom`.
StarUI wraps the interaction dialog's side panel (`VisualPanelAPI.showStarUIPanel`); its own windows over any screen
are `Dialog` and `showDialog` ([A dialog](#a-dialog)), not vanilla's custom dialog.
Build into it at the moment the API gives it to you. The hosts' sizes and limits (the custom dialog's window is
bigger than the size you pass, one custom dialog per interaction dialog, and so on) are in
[layout.md](layout.md#hosts); their API in the game's sources (`starsector-knowledge`, when installed).

A side panel in an interaction dialog you run (its plugin's `init`, or a dialog option):

```kotlin
dialog.visualPanel.showStarUIPanel(440f, 600f) { RequisitionsPanel() }   // black behind it by default
```

Seen in game on 0.98.5a-RC4 ([layout.md](layout.md#hosts) has the hosts' sizes and limits).

## inject into a vanilla panel

For one vanilla sub-panel that no screen covers. It adds one StarUI `CustomPanel` (its own reactive scope) to the
panel and builds your components in it. Call it at the moment vanilla builds the panel: a PatchLib `@After` on the
method or constructor that builds it, with the hook's `self` (or a panel reached from it) as the receiver. Writing
the hook is PatchLib's (its own `patchlib` skill, from the PatchLib wiki, when installed); the vanilla class and
method come from the game's sources (`starsector-knowledge`, when installed), never from memory.

Size and place: `size` is measured against the vanilla panel. `inject(key, Size.fillMaxSize()) { ... }` covers the
whole panel, and follows it when vanilla sizes or resizes it after the hook ran. Without an anchor in `modifier`, the
container sits at the panel's top left (vanilla's own default is the bottom left); anchor it to a vanilla sibling
or a corner (`Modifier.anchor(Anchor.inside.bottomRight.ofParent(10f, 10f))`) to place it elsewhere.

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

- **One injection per `key` per panel.** Prefix keys with your mod id. While it is live, another call returns it and
  does nothing, so a hook that fires twice, a build method that runs again, or your own every-frame script (the
  fallback where no hook point exists) can all just call it. `panel.injection(key)` finds it.
- **It survives `clearChildren()`.** When the panel clears its children (how vanilla rebuilds a panel in place), the
  injection comes back at the panel's next render, above what vanilla put back, with the builder run again. Hooking
  the panel's first build is enough. `removeInjection(key)`, `dispose()` on the container, or removing it yourself
  ends it for good.
- **Read the panel inside the builder.** Since the builder can run again, read what it needs from the panel
  (`this.parent`, getters, siblings) instead of capturing objects a vanilla rebuild replaces.
- `placement = InjectPlacement.Bottom` puts it under the panel's own children. Not on StarUI's own reactive
  containers (it throws): put the components in their builder.
- Class names in matchers must be readable ones (`com.fs.starfarer.coreui.refit.ModWidget` above), never obfuscated
  ones. The `patchlib` skill covers matching by shape when there is no readable name.
