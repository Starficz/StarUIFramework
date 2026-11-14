# Injection plan: hooks say when, `inject` says what; no more per-frame polling

Status: done (2026-11-13). Part A: `Injection.kt`, the `injection` test group, `DynamicInjection` removed, Combat AI
Tweaks ported and checked live (title, Missions, Refit: the button under "Add", one after removing a hullmod and
after switching ships, the dialog opens). RefitFilters' cargo filters are work in progress: converted to
`injectCargoFilters()` but not hooked up (no patch yet). Part B: the `StarUIPlugin` screens on hooks, the adder
scripts and the per-frame `StarUIManager.advance` removed, getters computed on read. Checked by the campaign checks
(`native-smoke/live`, group `screens`: campaign root, the seven tabs, a tab opened again, a market dialog's own core
UI, 62 checks) and live (title, mission refit opened twice, a mission battle's widget panel and ship info, the
warroom with a working button and the map still usable). The warroom (`addPanelToCombatWarroom`, never injected
before) is now on `@After CombatState.showWarroom()`.

Where B differs from the plan below: the core tabs come through the API's `CoreUITabListener.reportOpenedCoreTab`
(rung 1, no patch), guarded because vanilla can put a tab switch off and still announce it in some paths; the
combat hook is `CombatState.resetSomeUIThings()`, not `traverse()` (that is the combat state's whole main loop: an
`@After` on it runs when the battle ends); the mission refit panel is read as the holo's `getNext() ?: getCurr()`.
A screen panel is only injected when some plugin has a builder for that screen, and it takes the target's exact
size when the target already has one, else fill.

Found on the way: an anchor to a vanilla sibling does not follow the sibling when only the sibling moves (it is
vanilla's `relativeTo`; vanilla's own `pack()` does re-place it). Old placement used `bringComponentToTop`, which
also raised the target panel in its own parents (the campaign core UI in the campaign screen); `inject` only adds.

## Why

`DynamicInjection` predates PatchLib in StarUI. Every frame `StarUIManager.processDynamicInjections` calls each
injection's `findTarget()` (a chain of reflective getters), checks whether any element it added earlier is still a
child of the target, and if not runs `injectUI()` again, finding "what it added" by diffing the child list before
and after. That design:

- **cannot know when the target was built, so it guesses.** RefitFilters checks `children.size <= 4` to mean
  "vanilla finished building the cargo grid". A rebuild is repaired on the next poll, not where it happens.
- **tracks by diffing, which is fragile.** If `injectUI` adds to a nested panel instead of `this`, the diff sees
  nothing and the next frame injects again, every frame, piling up duplicates. If vanilla drops one of two injected
  elements, the survivor counts as "injected" and the lost one never returns.
- **always costs**: every injection pays its reflection chain every frame in every state, its screen open or not.
- **only runs where `StarUIManager.advance` runs** (the campaign transient script and the combat plugin).
- **gives the injected UI no scope**: loose leaves in a vanilla panel, so `UIState` reads in `injectUI` subscribe
  nothing.

The `StarUIPlugin` screens (`addPanelToRefitTab` and the rest) are the same polling pattern in
`StarUIManager.advance`, with `hasFrameworkPanel` scanning children every frame.

PatchLib gives the moment directly: an `@After` on the method that builds the panel runs in the same call, never a
frame late, and costs nothing while the screen is closed. What a hook does not give is the *what*: idempotency
(a `subtype` hook fires once per level of a super call; build methods run again without clearing), one trackable
element, a scope, and survival when vanilla clears the panel. That is the part StarUI keeps.

## The design

### One primitive: `inject`

```kotlin
// org.starficz.starui, Injection.kt (replaces DynamicInjection.kt)
fun UIPanelAPI.inject(
    key: String,                       // "<modid>:<name>"; one injection per key per panel
    size: Size = Size.wrapContent(),
    modifier: Modifier = Modifier,     // usually an anchor relative to a vanilla sibling
    placement: InjectPlacement = InjectPlacement.Top,   // Top: added last; Bottom: sendToBottom
    builder: StarUIBuilder,
): CustomPanelAPI

fun UIPanelAPI.injection(key: String): CustomPanelAPI?   // the live injection, or null
fun UIPanelAPI.removeInjection(key: String)              // removes and disposes; it does not come back
```

- **One container per injection.** `inject` adds one StarUI `CustomPanel` (the given size, modifier and placement)
  to the vanilla panel and runs `builder` in it. It is a normal top-level StarUI node: its own reactive scope, lazy
  layout, an anchor to a vanilla sibling re-applied after every layout (as today for top-level wrap nodes).
- **Idempotent.** If the panel already holds a live injection under `key`, `inject` returns it and does nothing
  else. That makes it safe to call from a hook that fires twice, from a build method that runs again without
  clearing, or every frame from a mod's own script (the fallback when no hook point exists; no separate polling API).
  Explicit replacement is `removeInjection(key)` then `inject(...)`.
- **Survives `clearChildren()`.** When the panel clears its children (how vanilla rebuilds a panel in place, e.g.
  the cargo grid's `recreate`), the injection is re-added with its builder run again, at the panel's next render,
  before its children draw. Re-adding then, not at the clear, puts it after vanilla's re-added children (`Top`)
  and never in the middle of vanilla's rebuild. Hook points therefore only need the *first* build of a panel;
  finding every rebuild method is no longer required. `removeInjection`, disposing the container, or the code
  removing the container itself end it for good: only a clear brings it back.
- **No global registry.** The record (key, size, modifier, placement, builder, the live container weakly) lives on
  the host panel in vanilla's per-component store, like `UINode`s (`NodeStore`). A panel vanilla throws away is
  collected with its injections and their builders.

Builder captures: a re-added injection runs the same builder again, so it must not depend on objects captured at
the first call that a rebuild replaces. Read them from the host (`this.parent`, getters) inside the builder, as
Combat AI Tweaks already does with `children.lastOrNull()`.

### How a mod uses it

The mod writes its own PatchLib patch for *when*, and calls `inject` for *what*. Every StarUI consumer already
ships with PatchLib (it is a dependency of StarUI); it now also compiles against `PatchLibAPI.jar`.

```kotlin
// Combat AI Tweaks: the refit's hullmod widget never clears its children (it resizes its inner list and packs),
// so one injection per widget, at construction, is enough.
// 0.98.5a-RC4: refitPanel.getModDisplay() is coreui.refit.C, its getMods() returns this class.
@Patch(target = ClassMatch(typeName = "com.fs.starfarer.coreui.refit.ModWidget"))
object RefitButtonPatch {
    @JvmStatic
    @After(target = MethodMatch(methodType = MethodType.CONSTRUCTOR))
    fun afterCreate(context: AfterContext) {
        val widget = context.self as? UIPanelAPI ?: return
        widget.inject("combataitweaks:ai-config") { /* the button, anchored below the widget's last child */ }
    }
}
```

`ModWidget` is a readable but non-API name: an inferred target, first thing to re-check after a game update.

## What vanilla does (0.98.5a-RC4 decompile, `C:\Users\starf\starsector-reference-0.98.5a\obf`)

Names in brackets are this build's obfuscated names, for repair after an update; no code may depend on them.

| Screen | Built by | Hook | Notes |
|---|---|---|---|
| Refit hullmod widget (CAT) | `coreui.refit.ModWidget` constructor `(Objectsuper, C)` | `@After` constructor, `typeName` | never clears children; `syncWithCurrentVariant` / `addMod` resize the inner list and `pack()` |
| Cargo grid (RefitFilters) | `recreate(6 args)` on the cargo grid [`campaign.ui.void`], which `clearChildren()`s first | `@After recreate`, shape: `subtype = UIPanelAPI`, `methodMatches` `recreate` (6) + `getSpecial` (0) | every cargo grid in the game has this class (trade, storage, pickers): filter in the handler on the trade panel's `getPlayerCargoDisplay` / `getEntityCargoDisplay`, or accept all grids (product decision for RefitFilters) |
| Campaign tabs (all seven), **done through the API's `CoreUITabListener` instead** | `showPanelAsDialog(button, panel, b, b, b, b)` on the campaign CoreUI [`ui.newui.oOOo`, file `_c169.java`], 6 args; the 4-arg overload delegates to it | `@After`, shape: `subtype = CoreUIAPI`, `methodName = "showPanelAsDialog"`, `parameterCount = 6` | arg 1 is the tab panel, already sized (vanilla reads its width). Early returns (a campaign dialog showing; a deferred tab id): inject only if `self.getCurrentTab() === arg 1`. Tab id: `getCurrentTabId()` (highlighted button) |
| Campaign core UI root (`addPanelToCampaignUI`) | the same CoreUI, constructors `(dosuper, SectorEntityToken, boolean, z)` and `(dosuper)` | `@After` constructor, `subtype = CoreUIAPI` | needs `isMostDerivedCall` if both CoreUIAPI implementors chain; the mission CoreUI [`coreui.p`] also implements `CoreUIAPI`: distinguish it |
| Title screen | `TitleScreenState.createUI()` (private, readable), called from `prepare()` | `@After`, `type` by name | builds `screenPanel`, the title widget and the mission widget; `VanillaClasses.findShipPreviewOnTitleScreen` belongs here too |
| Combat (ship info above/below, widget panel, warroom) | `CombatState.traverse()`: `resetSomeUIThings()` makes `widgetPanel` and `shipInfo`; `warroom` is made later in `traverse` | **`@After resetSomeUIThings`** (`traverse` is the main loop and returns at the battle's end) | the widget panel and ship info exist once it returns; the warroom gets `@After showWarroom` (each map open; the panel is added once) |
| Mission refit (title, Missions) | not found yet: today `title → child with dismiss → child with getMissionInstance (the mission CoreUI [coreui.p]) → child with forceFoldIn (a holo) → getCurr` | research in step B4 | the holo's current panel is probably set through `setNext` (StarUI's `Holo` uses the same method); a hook there needs a filter on the mission CoreUI |

## What StarUI does now (to change)

- `DynamicInjection.kt`, `StarUIManager.registerDynamicInjection`, `processDynamicInjections`,
  `dynamicInjectionElements`.
- `StarUIManager.advance()`, run every frame by `internal/CampaignUIAdderScript` (transient script added in
  `onGameLoad`) and `internal/CombatUIAdderScript` (combat plugin, also on the title screen's background combat):
  injects the `StarUIPlugin` roots with `injectIfNeeded` / `hasFrameworkPanel` / `addInjectedRoot`, keeps
  `coreUI`, `combatShipInfo`, `combatWidgetPanel`, `combatWarroom` and the mission-refit `tabOverride` /
  `tabIDOverride`, and calls `Scope.disposeDetachedPending()`.
- `StarUIPanelPlugin.isInjectedRoot`, only used by the above.
- Consumers: Combat AI Tweaks (`ui/RefitButton.kt`, registered in its `ModPlugin`; `ui/ConfigDialog.kt` reads
  `StarUIManager.currentCoreUITabPanel` and `coreUI`), RefitFilters (`CargoInjections.kt`, `ModPlugin.kt`).
  ScyNation does not use injection.
- Tests: `dynamic-injection` (`StateAndDynamicTests.kt`), the injection-polling part of the perf group
  (`InteropAndPerfTests.kt`), the `buildInVanillaAndDrop` comment (`LeakTests.kt`).

## Steps

### A1. `inject` and its tests

- `Injection.kt`: `inject`, `injection`, `removeInjection`, `InjectPlacement`. The record on the host in vanilla's
  per-component store (next to where `NodeStore` keeps non-CustomPanel nodes); the container weakly.
- Re-add after a clear: `UIPanelContainerPatch.onChildrenCleared` already walks the cleared children; when one is
  an injection container, put its host on a pending-reinject set (weak). The render hook in
  `UIComponentLifecyclePatch` checks that set (one `isEmpty()` per component render while nothing is pending),
  and on the host's render runs the re-adds before its children draw. The cleared container itself is disposed
  as any cleared StarUI child is (`Scope.noteDetached`); the re-add builds a new one.
- Tests (new group `injection`, replacing `dynamic-injection`):
  - one call adds one container; a second call with the same key returns it and adds nothing; a different key adds
    a second one;
  - `Top` lands above vanilla children added before it, `Bottom` below them;
  - `clearChildren()`, then vanilla-style re-adding of children, then a frame: the injection is back, once, above
    the re-added children, its builder ran twice;
  - `removeInjection` and `dispose()`: gone, and a later clear does not bring it back;
  - a `UIState` read in the builder rebuilds the injection (it has a scope);
  - an anchor to a vanilla sibling holds after the sibling moves and a frame passes;
  - leaks: a host with injections, dropped, is collected with its containers and builder captures (`leaks-*`);
  - perf: 20 settled `inject` calls per frame (the polling fallback) stay cheap; log the number.

### A2. Remove `DynamicInjection`

- Delete `DynamicInjection.kt`, `registerDynamicInjection`, `processDynamicInjections`,
  `dynamicInjectionElements`; drop the injection-polling perf test.
- AGENTS.md: replace the `DynamicInjection` bullet under "Getting UI on screen" with `inject` and the hook pattern
  (the `ModWidget` example), the root-package import list, the source layout line.

### A3. Port the consumers

- Combat AI Tweaks: `RefitButtonPatch` as above, `RefitButton.kt` becomes the builder; remove the registration in
  its `ModPlugin`; add `PatchLibAPI.jar` to its compile libraries. Confirm in game: button present on opening the
  refit, after adding and removing hullmods, after switching ships, after leaving and reopening the tab; one
  button, never two. CTRL + P: the patch listed with sites once the refit tab has opened.
- RefitFilters: `@After recreate` patch on the cargo grid, the filter for which grids get buttons (ask), the
  `children.size <= 4` check removed; same compile setup and checks (trade with a market, storage, a cargo picker).
- Rebuild ScyNation too (it is compiled against StarUI) even though it does not inject.

### B1. Campaign: tabs and core UI root on hooks

- StarUI's own patch: `@After showPanelAsDialog` (6 args) on `CoreUIAPI` implementors, guarded by
  `getCurrentTab() === arg 1`, injects the tab root (`key = "staruiframework:screen"`, exact size of the tab panel,
  anchor top left) with the registered plugins' builders for that tab id, as `addInjectedRoot` does now.
- Core UI root: `@After` on the CoreUI constructor, or on the first `showPanelAsDialog` if the constructor runs
  before the core UI is sized; check which in game.
- Encounter dialogs: today the core UI is also found through `getEncounterDialog().getCoreUI()`; confirm that
  core UI comes through the same class and hooks.

### B2. Combat on a hook

- `@After CombatState.traverse()`: the ship info roots (above and below, two keys) and the widget panel root.
  Title screen background combat: check whether it runs `traverse` and whether a combat root there is wanted.

### B3. Title screen on a hook

- `@After TitleScreenState.createUI()`: `findShipPreviewOnTitleScreen`, then the title root once `hasShipPreview`.

### B4. Mission refit (research first)

- Find where the mission CoreUI sets its holo's current panel; hook that with a filter on the mission CoreUI.
  If no clean hook exists, keep this one screen on a cheap poll from a StarUI root that is on screen anyway (the
  title root's `onAdvance`), calling the idempotent `inject`; say so in the code.

### B5. Remove the adder scripts and the polling `advance`

- Delete `CampaignUIAdderScript`, `CombatUIAdderScript` (and their registration), `StarUIManager.advance`,
  `injectIfNeeded`, `hasFrameworkPanel`, `addInjectedRoot`, `isInjectedRoot`.
- Public getters `coreUI`, `currentCoreUITabID`, `currentCoreUITabPanel`, `combatShipInfo`, `combatWidgetPanel`,
  `combatWarroom`: compute on read from `AppDriver.getInstance().currentState`, as `advance` did, instead of
  caching per frame. The mission refit's tab override is recorded (weakly) by its B4 hook. Combat AI Tweaks'
  `ConfigDialog` uses these; recheck it.
- `Scope.disposeDetachedPending()`: every StarUI root already runs it in `renderBelow`, so the manager's call goes.
  Consequence: a detached scope waits for the next StarUI render to be disposed. It is only weakly held and
  disposal only matters for `UIState` observers, so this is acceptable; note it in the code.
- Tests: the screen roots in a live campaign (`starsector-native-smoke` scripted checks): each tab gets its root
  once, survives leaving and reopening the tab, and a plugin's `UIState` still rebuilds it.

### B6. Docs

- AGENTS.md: `StarUIPlugin` re-injection is now hook-driven ("whenever vanilla rebuilds the screen" stays true);
  the patches list gains the screen patch file(s); `internal/` loses the adder scripts.

## Risks and open points

- **Builder re-runs on re-add.** A builder that captures an object a vanilla rebuild replaces shows stale data after
  a clear. Documented; the injection tests cover the read-from-host pattern.
- **Inferred targets.** `ModWidget`, `showPanelAsDialog`, `recreate`, `getSpecial`, `createUI`, `traverse` are
  readable but not API: re-check all of them after a game update. The cargo grid and the CoreUI are matched by
  shape only.
- **Consumers now need a hook point.** Finding it means a decompile, harder than naming getters. The AGENTS.md
  example and the two ported mods are the reference; the idempotent `inject` from a mod's own every-frame script
  stays available as a fallback.
- **Re-add timing.** Re-adding on the host's next render assumes vanilla finishes its rebuild before that render.
  A panel that rebuilds across frames (adds children over several frames) would get the injection in the middle
  of its children; none seen so far.
- **Panels cleared on purpose.** A vanilla panel cleared to be repurposed gets the injection back. The builder can
  check the host's state and add nothing.
