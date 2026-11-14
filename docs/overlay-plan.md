# Overlay plan: layers over any screen, dialogs that stack, toasts

Status: steps 1 to 4 done (2026-11-14). The spike confirmed the design in game (results under step 1); the user
approved the open decisions (below, "Decided"); the layer primitive (step 2) and `Dialog` (step 3) are built and
tested ("Built" under each), and `showToast` (step 4). Next is step 5, the rest of the docs (the showcase).

## Why

StarUI can put UI on a screen (`StarUIPlugin`, `inject`) and into vanilla's hosts (`showStarUIDialog`,
`showStarUIPanel`), but it has no window of its own over a screen. What exists today:

- **`showStarUIDialog`** is vanilla's custom dialog: one per interaction dialog, campaign only, a fixed Confirm /
  Cancel row. Opened from inside another StarUI dialog it most likely shows nothing (a second custom dialog does not
  stack in one interaction dialog; not run yet). The confirmation popup recipe works around that with a second, empty
  interaction dialog (`showInteractionDialogFromCargo`): 50 lines, raw `TooltipMakerAPI` content, compiled but never
  run.
- **The overlay recipe** (setup.md "An overlay over a screen") is a full-screen `CustomPanel` on the core UI with
  `consumeEvents`. It sits inside the core UI, so vanilla's interaction dialogs, the message list and the "Game
  paused" line are above it, and it does not pause, handle Escape or stack.
- Nothing for combat or the title screen, and no toasts.

Mods need: a confirmation over a dialog over a panel; several windows; a short notice that goes away by itself; the
same on every screen.

## The design

### One primitive: a layer on the screen panel

A **layer** is a full-screen StarUI `CustomPanel` added as the last child of the current state's screen panel
(`Global.getSettings().getScreenPanel()`: the API's own getter, title screen, campaign and combat; no reflection).
Layers stack in the order they open. Each has its own reactive scope, like an injection.

- **Modal** (the default for dialogs): a dim quad over the whole screen, and the layer consumes every keyboard event
  and every mouse event, so nothing under it sees them. This is what vanilla's dialogs do (below).
- **Non-modal**: no dim; only mouse events over its content are consumed, keys pass through.
- **Pause** (campaign and combat, default on for modal): pauses on open, restores the previous pause state when the
  last pausing layer closes (vanilla keeps `wasPaused` the same way).
- **Keys**: Escape goes to the top modal layer (default: close it as a cancel); Enter / Space to its confirm when it
  has one. A layer never sees keys while a layer above it is modal.
- **Close**: fade out, then remove; `onClose` runs once with how it closed (confirm, cancel, code).
- **Lifetime**: a layer belongs to its screen. A state change (campaign to combat, back to the title) drops the
  screen panel and the layers with it; `isOpen` reads false. The campaign's rare core rebuild (it removes every child
  of the screen panel but the message widgets) puts open layers back on the next frame.
- Tooltips need nothing: vanilla adds a tooltip as the last child of the top panel when it shows, so a tooltip
  from inside a layer is above it.

### Two ways to open one

StarUI's rule is "builders build, handlers change state", so the main form is declarative, as Compose's `Dialog`:

```kotlin
val confirmDismiss = UIState(false)
Column {
    Button("Dismiss") { confirmDismiss.value = true }
    if (confirmDismiss.value) Dialog(onClose = { confirmDismiss.value = false }, confirmText = "Dismiss",
        cancelText = "Cancel", onConfirm = { teams.value -= team }) {
        Text("Dismiss ${team.name}?", font = Font.INSIGNIA_21)
    }
}
```

The `Dialog` call opens a layer on the screen panel tied to the calling scope: it stays while the builder keeps
calling it, closes when a rebuild leaves it out or the scope is disposed (the panel that asked for it closed).

For code outside any UI (a campaign script, an ability, a hotkey listener) there is the imperative form, a thin wrapper
holding its own state: `showDialog(...) { layer -> }`, returning a `StarUILayer` handle (`close()`, `isOpen`).

### What sits on a layer

- **`Dialog`**: vanilla's window. `Holo` (vanilla's own framed window, which its dialogs use: `impl.E.getHolo()`)
  around a Box of the content size, with vanilla's custom dialog geometry: 10 px margin, a bottom row with
  `confirmText` and optional `cancelText` buttons (150 x 25, `TL_BR`, Orbitron 20, bottom right), dim 0.85, the
  fold animation on open and close. Without `confirmText` there is no row (content closes it through the layer).
  - **Static** (the user asked for it as an option): the burst of noise over the window that vanilla's dialogs show.
    Vanilla: the Holo's `flickerNoise(0, 1.25)`; its dialog (`ui/impl/E.dismiss`) runs it on close, always on a
    cancel and on a confirm only with `setNoiseOnConfirmDismiss(true)`; `Holo.setNext` (new content) runs a short
    static transition. StarUI's `Holo` already has all of it (`BoxedUIHolo.open/close(flicker)`, `contentKey`).
    `Dialog` takes a `static` parameter: open, close (vanilla's rule: cancel only, or both), or none; the default is
    what vanilla's custom dialog does, read off side-by-side captures in step 3. `contentKey` passes through, so a
    dialog with pages changes page behind the static.
  - **F2** opens the codex as vanilla's dialogs do: `SettingsAPI.showCodex(entryId)`, with a `codexEntryId`
    parameter (`null`: F2 passes on unconsumed). The spike showed a modal layer swallowing F2 otherwise.
- **`Toast`**: a non-modal layer, no dim, no pause, at a screen position, gone after `duration` with a fade; toasts
  stack. Vanilla has no toasts: its notices are the campaign message list (`campaignUI.addMessage`) and combat's
  message area. The docs will say to prefer those, and the toast is for what they cannot show (another screen,
  rich content).
- **`Layer`**: the bare full-screen layer for anything else (a custom overlay, a picker), modal or not.

### Decided (2026-11-14, with the user)

- The declarative `Dialog` in builders is the main form; `showDialog` is for code outside any UI.
- `showStarUIDialog` / `StarUIDialog` go once `Dialog` matches vanilla's window in captures; `showStarUIPanel` stays.
- `Toast` is built, flagged as non-vanilla; the docs send mods to the vanilla message lists first. Default position
  top centre, clear of the campaign message list.
- F2 over a modal dialog opens the codex (as vanilla's dialogs), not blocked.
- `Dialog` has the static animation as an option (above).

### What changes

- `showStarUIDialog` / `StarUIDialog` are replaced by `Dialog` / `showDialog` once those match vanilla's window in
  side-by-side captures (no public release yet: no shim). `showStarUIPanel` stays: the side panel is a different host.
- The overlay recipe in setup.md and the confirmation popup recipe become `Layer` / `Dialog` examples.

### Not part of this

Moving windows, resizing, minimising; layers that survive a state change; rebuilding vanilla's own dialogs.

## What vanilla does (0.98.5a-RC4 decompile, `starsector-knowledge` `obf/`)

- **One screen panel per state.** `CampaignState.screenPanel` holds the core UI, the message list, the message
  display and a `belowTooltips` panel; `CombatState.getScreenPanel()` is its widget panel; `TitleScreenState` has its
  own. `SettingsAPI.getScreenPanel()` returns the current one (`StarfarerSettings`, by current state).
- **Input goes top child first.** The panel class (`ui/z`, `dispatchEventsToChildren`) walks its children from the
  last added to the first. `CampaignState.processInput`: `ListenerUtil.processCampaignInputPreCore` (other mods'
  input listeners) first, then `screenPanel.processInput`, then the campaign's own hotkeys (pause, hide UI, fast
  forward) only on events not consumed. So a layer added last that consumes an event hides it from the core UI and
  from those hotkeys. Combat: `widgetPanel.processInput` in its main loop; combat also reads `Keyboard.isKeyDown`
  directly in places (Alt for the view), which no consumed event stops.
- **A vanilla modal dialog** (`ui/oo0O`, `show`): adds an "interceptor" panel, the size of the parent, then the
  dialog above it. The interceptor consumes every keyboard event and every mouse event over it (the whole screen)
  and draws the dim (`backgroundDimAmount`; the custom dialog sets 0.85). The dialog maps Escape to its cancel
  option, Enter and Space to its confirm option, F2 to the codex, and optionally left click outside or right click
  to cancel. `dismiss` fades it out; its `advance` then removes the dialog and the interceptor.
- **Vanilla's dialogs are children of the screen panel** (`new E(w, h, screenPanel, ...)`, the interaction dialog
  `new N(..., screenPanel, ...)`). One opened while a layer is up goes above the layer, which is right for a picker a
  layer opens.
- **Pause.** Campaign dialogs keep `wasPaused`, set paused, restore on dismiss (`CampaignState.dialogDismissed`).
- **Tooltips** (`ui/R.showTooltip`): added as the last child of `findTopAncestor()`, the screen panel, when shown.
- **Re-raised every frame:** the campaign brings its message display (the "Game paused" line) to the top of the
  screen panel each render, above everything, layers included. Vanilla's dialogs have the same.
- **Core rebuild** (`CampaignState.advance`, when the core's fleet is no longer the player fleet): removes every
  child of the screen panel except the message widgets and adds a new core.

## Steps

### 1. Spike: confirm the decompile in game (half a day)

A throwaway layer (a full-screen StarUI `CustomPanel` with `consumeEvents` and a dim, added to
`Global.getSettings().getScreenPanel()`) in the hot game, run behind with background input only. Check and record:

1. Campaign map: Escape does not open the menu, Space does not unpause, a click does not move the fleet, a map
   hotkey (`R`, `C`) does nothing, while the layer is up; all work again after it is removed.
2. Over an interaction dialog (the Requisitions board) and over a vanilla custom dialog (a board): the layer is
   above both and they get no input.
3. A core tab (refit): above it, its hotkeys blocked.
4. A tooltip on a button inside the layer shows above the layer.
5. Combat (a mission): the layer shows over the battle; what still reaches the ship (keys read directly); pausing
   the combat engine while it is up.
6. Title screen: shows and blocks the menu.
7. F2 over a modal layer: decide pass through (vanilla opens the codex) or block.

Results go into this file. Anything that does not hold changes the design before step 2.

**Results (2026-11-14, hot game behind, background input; the spike is `Spike.kt` in the throwaway boards mod).**
The layer was a StarUI `Box` of the screen's size added to `Global.getSettings().getScreenPanel()`, with
`Modifier.background(Color(0, 0, 0, 150))`, `.plugin { consumeEvents = true }` and an `onKeyDown` closing it on
Escape. A `CustomPanel` gives events to its children first and its plugin last, so the content works and the plugin
swallows the rest, as vanilla's interceptor.

1. **Title screen**: shows (it is the last child); a click on New Game does nothing; the dim darkens the screen by
   about 60 %. Holds.
2. **Campaign map**: Space, C, Tab and a map click reach the top layer and are consumed: still paused, no core tab,
   the fleet's destination unchanged, nothing reaches the post-core listener stage. Escape closes the layer and opens
   no menu. Holds. `CampaignInputListener.processCampaignInputPreCore` sees every event first, as decompiled.
   The layers are not the last children: two message widgets (the "Game paused" line) are re-raised above them each
   frame, as decompiled; they draw over the dim, as over vanilla's dialogs.
3. **Over an interaction dialog** (Requisitions) **and a vanilla custom dialog** (Pets): the layer is above both, and
   Escape closes only the layer (twice: two layers, then the dialog still open). Holds.
4. **Core tab** (refit): above it; C and I do nothing. Holds.
5. **Tooltip** on a button in the layer: drawn above the layer. Holds.
6. **Stacking**: two layers; Escape reaches only the top one each time. Holds.
7. **Text field** in the layer, focused: typed letters, Space and Enter all go to the field, none to the layer's key
   handler; Escape still reaches the layer. So a focused field never confirms a dialog by Space. Holds.
8. **Combat** (refit simulator): the layer shows over the battle and pauses it; Space is consumed (stays paused);
   Escape closes the layer, no combat menu; the pause returns to its previous state. One combat widget (the
   Reinforcements / Full Assault panel at the top right) is above the layer and draws undimmed.
   Not settled: what reaches the player's ship under a layer that does not pause. The simulator's `playerShip` was a
   fixed placeholder, so held keys could not be checked. Modal layers pause by default, so it matters only for a
   non-pausing modal layer; check it in a mission in step 2.
9. **F2** over a modal layer: consumed, no codex. `SettingsAPI.showCodex(...)` exists, so `Dialog` opens it itself.
10. **Pause**: restored to the state before the first layer when the last one closes (campaign and combat).
11. No errors in the game log.

Design changes from the spike: none to the layer. `Dialog` gains F2 handling and the static option (above).

### 2. The layer primitive

`Overlays.kt` (root package): the layer stack per screen panel (weak), `Layer(modal, dim, pause, ...) { }` in
builders and `showLayer(...)` outside them, the `StarUILayer` handle, Escape routing to the top modal layer, pause and
restore, fade, re-adding after the core rebuild (StarUI already sees the core being created: `CoreUIScreenPatch`).
Suite tests on the title screen's panel (the suite has no campaign): stacking order, input consumed below a modal
layer and passed by a non-modal one, Escape closing only the top layer, a rebuild leaving `Layer` out closing it, a
disposed scope closing its layers, the handle's `close()`.

**Built (2026-11-14).** `Overlays.kt`: `StarUILayer` (`panel`, `isOpen`, `close()`), `showLayer(modal, dim, pause,
closeOnEscape, onClose) { layer -> }`, `Layer(onDismissRequest, modal, dim, pause, dismissOnEscape) { }`,
`DEFAULT_LAYER_DIM` (0.6). Differences from the plan above:

- Compose's semantics for the declarative form: Escape calls `onDismissRequest`; the layer stays until the builder
  stops calling it. `showLayer`'s Escape closes it and runs `onClose`.
- A scope keeps its `Layer` calls (`Scope.layerSlots`, by order or `key`); a build that leaves one out closes it, and
  disposing the scope closes them all. A `Layer` called inside a layer closes with that layer.
- The fade drives the panel's opacity on a wall clock (the campaign advances its screen with 0 while paused). A
  closing layer is taken off and disposed by its own next advance: never from `close()`, which can run inside one of
  its own handlers.
- The core rebuild re-add: `CoreUIScreenPatch` calls `Layers.afterCoreUICreated()`; when an open layer is off the
  screen panel, the render hook puts it back before the screen panel's next render.
- Tests: suite groups `layer-imperative`, `layer-declarative`, `layer-fade-and-readd` (on the group's root,
  `Layers.testHost`: the suite runs before any screen exists), 48 checks; live group `layers` (real keys in a
  campaign: pause and its restore, Space, C, Escape to the top layer only and no game menu, above an interaction
  dialog, the re-add through a dialog's core UI), 22 checks.
- Still open: what reaches the ship under a non-pausing modal layer in a battle (no live combat group yet).

### 3. `Dialog`

On `Layer`: the Holo window, the button row, Enter / Space / Escape, `onConfirm` / `onCancel` / `onClose`.
Side-by-side captures with vanilla's custom dialog at the same content size (frame, margins, buttons, dim, the fold)
before `showStarUIDialog` is removed. Then the confirmation popup recipe becomes the `Dialog` above, and the boards
wire it on Euthanize, Dismiss and Delete: a confirmation over a custom dialog, a dialog over a dialog, checked live.

**Built (2026-11-14).** `Dialogs.kt`: `Dialog(onDismissRequest, width, height, confirmText, cancelText, onConfirm,
onCancel, static, contentKey, codexEntryId, background, pause) { dialog -> }`, `showDialog(...)`, `StarUIDialog`
(`confirm()`, `cancel()`, `layer`, `isOpen`), `DialogStatic` (`Vanilla`, `CancelOnly`, `None`, or per open /
confirm / cancel). `showStarUIDialog`, the old `StarUIDialog` and the confirm popup recipe are gone; the boards use
`showDialog` and a `Dialog` confirmation on Euthanize, Dismiss and Delete.

- Vanilla's numbers (`ui/newui/O0oo`, `ui/impl/E`): window (w + 20) x (h + 55) in a 7 px holo frame, content 10 px
  in, 150 x 25 Orbitron 20 (`orbitron20aa`) TL_BR buttons 10 px from the bottom right, Cancel in the corner, the
  confirm button with the G shortcut ("Confirm [G]"); dim 0.85; the custom dialog shows with `show(0.3, 0.2)`, the
  holo folding in half of each; static on open and on both closes (`noiseOnConfirmDismiss` starts true); Space and
  Enter confirm, Escape cancels, F2 `CodexDialog.show()`, an outside click flickers (0, 0.5).
- The holo background: vanilla's custom dialog keeps the holo's default 125 / 175 (see-through); its market
  pickers set 255 / 255. New preset `HoloBackground.Default`; `Dialog(background = ...)`.
- Side by side with `showCustomDialog` at the same content size over the same interaction dialog: identical, max
  pixel difference 1 over the whole window (captures `cmp-vanilla`, `cmp-starui` in the boardtest hot work folder).
- Live in the hot game: Euthanize's confirmation over the Pets window; Escape closes the top window only; F2 opens
  the codex above both (`showCodex(null)` opens it as last left) and Escape closes the codex alone; the pause comes
  back after every path tried. Once, early on, the campaign ran after closing Pets; six replays of the paths did not
  reproduce it.
- Tests: suite groups `dialog-window` (geometry, keys, no-confirm, content confirming, a focused text field taking
  Space, the builder guards) and `dialog-declarative` (stacking, Enter and Escape to the top one, closing with the
  outer one; the holo's content is not in the tree, so `Layers.ownerOf` searches the layers' trees).
- Also fixed on the way: `hot.sh start` read the previous game's log and reported CRASHED after a crash; it now
  moves that log aside first.

**Moved (2026-11-14, with the user):** the family lives in its own package, `org.starficz.starui.window`
(`window/Layers.kt`, was `Overlays.kt`; `window/Dialogs.kt`), with room for Toast and the movable, resizable windows
planned later. `showStarUIPanel` stays in the root (`SidePanel.kt`): it fills a vanilla host. `DEFAULT_LAYER_DIM`
became the theme's `StarUITheme.layer.dim`; `StarUIDialog.layer` is internal (its `close()` skipped `onConfirm` /
`onCancel`).

### 4. `Toast`

Non-modal, timed, stacking, fade in and out; a position (default top centre under the message display, out of the
campaign message list's way). Checked on the campaign map, in a core tab, over a dialog.

**Built (2026-11-14).** `window/Toasts.kt`: `showToast(text, duration = 3f, alignment, color)`, `showToast(duration,
alignment) { }`, `StarUIToast` (`close()`, `isOpen`). One non-modal toast layer per screen with a Column per place,
raised above any layer opened later. The default place moved after seeing it in game: the top centre (planned) sat
on "Game paused" and on mods' button rows; the user picked above the minimap: bottom right, 220 px up in the
campaign, 10 px elsewhere. Seen in the hot game on the campaign map (stacked, wrapped, coloured, rich content) and
over a dialog opened before it (Pets, after Euthanize's confirmation).

Then, with the user: toasts move like Windows' notifications (they are not a Column any more). One full-screen
stage per toast layer; `ToastHost.tick` places every toast by hand: newest nearest the edge, the others gliding to
their slots, vanilla's own slide (`setSlideData` / `slideIn` / `slideOut`, with its fade) in from and out to the side
of the place, at most three per place with the rest waiting, hover stopping the time, clicks passing between toasts
(`StarUILayer.hitTest`). New toasts are parked off-screen until placed: a component that is not drawn (slid out,
transparent) is never laid out, so it has no size to place it by. The looks from the mockups (intel notice, accent
bar, heading) are recipes, not library styles. Suite groups `toasts` and `skill-recipes-toasts`.

Two layout bugs found on the way, both in StarUI's core, both fixed with suite checks:
- an exact-width `Text` in a wrapping container measured as one line (the container offered unbounded room);
  `measureChildPaddedIntrinsic` now measures an exact axis at its own size (group `text-exact-width-wraps`);
- a top-level container that wraps its content counted its padding twice (the engines' measures already include
  it); found by the same check.

### 5. Docs (part of the work)

AGENTS.md, the skill (components.md, setup.md "An overlay over a screen" and the hosts, layout.md hosts, recipes.md
confirm popup, traps.md), the showcase, and the hotreload skill's driving notes if the spike finds anything.

## Risks and open points

- **Other mods' input listeners** (`CampaignInputListener`, pre-core) see input before any layer, as they do before
  vanilla's own dialogs. A modal layer cannot stop them; the same holds for vanilla.
- **Keys read directly** (`Keyboard.isKeyDown`) in combat and in some campaign code are not stopped by consuming
  events. Pausing covers most of combat; a non-pausing modal layer in a battle is still unchecked (spike result 8).
- **Widgets above layers**: the campaign's message widgets and combat's top right panel stay above every layer and
  undimmed, as they do over vanilla's dialogs. Accepted.
- **The core rebuild** removes layers; they are put back on the next frame, so one frame without them (and a fade
  restarting) is possible. Rare (the player fleet object changing).
- **Declarative `Dialog` and scope lifetime**: a dialog asked for by a panel closes when that panel's scope is
  disposed. Usually right (the screen closed); a dialog that must outlive its panel uses `showDialog`.
- **Text fields in a layer**: keyboard focus and the modal key consumption must not fight (a typed Space must not
  confirm). Vanilla's dialog lets the focused field consume first; the layer must too (checked in step 2).
- **Hot reload**: layers are StarUI scopes, so `HotReloadRefresh` rebuilds them; a changed `Dialog` call shows
  after its builder runs again.
