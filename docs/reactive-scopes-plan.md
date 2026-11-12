# StarUIFramework: reactive scopes plan

Status (2026-11-11): Phases 0-5 done on branch `reactive-scopes` (609/609 checks), plus AGENTS.md. Remaining:
Phase 6 (Refit Filters port and live acceptance). Decisions taken: onLayout fires every pass (onFirstLayout is
once), every container is a scope, Step 1 kept simple and Step 2 built on it. Found while building Step 2: stack
children are anchored on their previous sibling, so removing one now re-pins the rest. Originally written
2026-11-11 so the work could start in a fresh context.

**Goal.** Make plain Kotlin `if` / `for` / `when` inside StarUI builders update automatically when state changes,
without a virtual-node layer (keep "zero wrapper": DSL calls create real Starsector objects), and make the library
easy for people who build UI with AI agents.

---

## 1. Workspace

| What | Where |
|---|---|
| Game root | `C:\Program Files (x86)\Fractal Softworks\Starsector` (0.98a-RC8, game JRE 17.0.10) |
| StarUIFramework | `...\Starsector\mods\StarUIFramework`, branch `patchlib-overhaul`, Gen 4 kept on `wip-gen4-backup` |
| Dependent mods (same author) | `mods\Combat AI Tweaks` (master), `mods\ScyNation` (main), `mods\RefitFilters` |
| Decompiled vanilla (Fernflower, renamed) | `C:\Program Files (x86)\Fractal Softworks\starsector-reference-master\src`. Names there (`UIComponent_0`, `class_2767`, `InputEvent`) are NOT runtime names (`OOOo`, `A.new`, `A.C`). Find classes by shape. |
| PatchLib | `mods\! PatchLib !-1.0.3` (API jar `jars\PatchLibAPI.jar`). Skill: `patchlib` (installed in `~\.claude\skills`). Never name obfuscated types in matchers. |
| Native smoke skill | `~\.claude\skills\starsector-native-smoke` (installed copy of `Downloads\starsector-native-smoke-2026-11-06.skill`, unmodified). Also `starsector-combat-smoke`. |
| JDK / Kotlin | `C:\Program Files\Java\jdk-17\bin`; kotlinc 2.3.10 at `%LOCALAPPDATA%\Programs\IntelliJ IDEA Ultimate\plugins\Kotlin\kotlinc`. Runtime Kotlin stdlib is LazyLib's **2.1.20**, so compile with `-api-version 2.1`. |
| Fast Rendering | 0.9.8 installed in `starsector-core` (backup of the old build: `starsector-core\fr-backup-2026-11-06-pre-0.9.8`). |

### Test suite (the main tool)

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Program Files (x86)\Fractal Softworks\Starsector\mods\StarUIFramework\native-smoke\run-test-suite.ps1" [-Filter input,stack] [-OutDir <fresh dir>]
```

- Run it from the PowerShell tool with `dangerouslyDisableSandbox: true` (it starts an isolated game JVM). About 45 s
  for the full suite. Last full run: **427/427 checks** (2026-11-06).
- It compiles `src/` plus `native-smoke/kotlin/` into a **test-only** jar (the committed `jars/StarUIFramework.jar`
  is never touched), then runs the skill's `run-native-smoke.ps1` with `native-smoke/StarUITestSuiteProbe.java` and
  `native-smoke/support/StarUITestSupport.java` (outside the security gate: real engine input events by shape,
  `beginInputFrame()` emulates the per-frame input poll).
- Tests are Kotlin in the **default package** (they must see default-package `NativeSmokeSupport` /
  `StarUITestSupport`) and compile in the same Kotlin module as the framework, so `internal` members are reachable.
- Groups: `StarUITestSuite.group("name") { ... }` in `native-smoke/kotlin/*.kt`, registered in
  `TestFramework.kt` `registerAll()`. Helpers: `TestContext` (`root`, `frame()`, `expect`, `near`, `expectRect`,
  `screenshot`, `thrown`), `Input.sendList/sendInternal/down/up/move/keyDown/keyUp`, `pixel`, `fraction`,
  `relLeft/relTop/cx/cy`.
- Output: `<OutDir>\launcher.log` (`NATIVE_SMOKE ...` lines, `SUITE SUMMARY`), one PNG per group. **Look at the PNGs.**
- **No Fast Rendering for this suite**: FR keeps OpenGL on its own render thread, so the harness's raw GL
  (frame setup, `glReadPixels`) has no context. `StarUITestSuite.start()` fails fast if not on `main`.
- Write the test first and watch it fail on the old code when practical.

### Live game (for things the startup probe cannot show)

Skill `starsector-native-smoke`, `scripts/live/` (Git Bash). Recipe used on 2026-11-06:
1. Build a release-style StarUI jar from `src/` only into a **staged** copy of the mod (scratchpad), never into the
   checkout: kotlinc args `-no-stdlib -no-reflect -jvm-target 1.8 -api-version 2.1 -language-version 2.2
   -module-name main` with game jars + LazyLib jars + PatchLibAPI on the classpath; `jar --create`; copy
   `mod_info.json` and `staruiframework.version`; junction `data`.
2. `make-modset.sh WORK LazyLib MagicLib-1.5.7 "! PatchLib !-1.0.3" "zz GraphicsLib" "Console Commands-4.0.9" <staged StarUI> "Combat AI Tweaks" ScyNation`
3. `SMOKE_HIDE=0 SMOKE_JAVAC_DIR='C:\Program Files\Java\jdk-17\bin' launch-game.sh GAME WORK > WORK/launch.log` (background; FR 0.9.8 is used automatically and loads fast).
4. `wait-log.sh WORK/launch.log "" 220`; capture with `GAMECTL_PID=$(cat WORK/game.pid) gamectl.exe bshot <png>`.
5. Stop only via `game-proc.sh stop WORK`. The game log goes to `launch.log` (not `logs/`).

### Environment gotchas

- Source files use **CRLF**. `sed -i` in Git Bash writes LF: restore with `sed -i 's/$/\r/'` or use the Edit tool.
- The Starsector root folder is not writable without admin; `starsector-core` and `mods` are.
- `Program Files` paths contain spaces and `(x86)`: never call `kotlinc.bat`, call the compiler through `java`.

---

## 2. Current state (all uncommitted)

StarUIFramework working tree has uncommitted changes (check `git status`) plus the untracked `native-smoke/` and
this `docs/` folder. **Ask the user whether to commit them before starting** (suggested: one commit per topic).
Done and covered by the suite:

1. Input: handlers run once per event (identity dedupe in `StarUIPanelPlugin.processInput`; vanilla
   `processInput(List)` re-dispatches the same event objects to the internal overload and PatchLib hooks both);
   consumed events are always skipped (engine getters throw on consumed events); `ignoreConsumedEvents` deprecated.
2. `StackLayoutEngine`: Rows measured children against their height (argument order); fixed.
3. Fonts: `getFontPath()` loads each `.fnt` once via `SettingsAPI.loadFont` (Combat AI Tweaks' `ORBITRON_20` button
   crashed the game in the refit screen).
4. `ButtonAPI.onClick` keeps the button's previous listener: `ButtonListenerChainPatch` (`@Before`
   `CustomPanel.actionPerformed(Object, Object)`) forwards to it.
5. `StarUIPanelPlugin.getOrCreate` wraps a CustomPanel's foreign plugin as `delegate` instead of replacing it.
6. `Column`/`Row` are canonical; `VerticalStackLayout`/`HorizontalStackLayout` are deprecated aliases that keep their
   centred default. `margin` side order is now `(left, top, right, bottom)` like `padding`.
7. `measureTextSize` LRU cache (119.5 us to 0.52 us per call). Injection polling reads live child lists and iterates
   a snapshot. Measured: lifecycle hooks have no overhead above noise on 300 vanilla labels; `StarUIManager.advance`
   with 20 injections costs 70.7 us/frame, so event-driven injection was dropped.
8. Title-screen demo panel (`ExampleUIPlugin`) registration is commented out in `internal/ModPlugin.kt`.

The user has rebuilt the jars and installed the patched Mikohime launch configs. Combat AI Tweaks still uses the
deprecated stack names (warnings only). Refit Filters is on the **old pre-Gen 3 API** (`org.starficz.staruiframework`)
and has its own uncommitted changes; do not touch it without asking.

---

## 3. Facts about vanilla that the design depends on

All on the main thread; Fast Rendering moves loading and GL execution off it but not UI logic.

- **Frame loop** (`BaseGameState.traverse`): `render` → poll input (`class_1846` poll; focus owner gets input first
  via `class_2664.method_26687`) → `processInput` down the UI tree → `advance`.
- **Campaign advance** (`CampaignState.advance`): `screenPanel.advance` (all UI, so StarUI advance hooks) first, then
  `engine.advance` (sector scripts, including StarUI's `CampaignUIAdderScript` → `StarUIManager.advance` →
  injections). Scripts with `runWhilePaused` run while the core UI pauses the game. With fast-forward,
  `engine.advance` runs several times in one frame.
- **Input dispatch**: `UIPanel.dispatchEventsToChildren` iterates a snapshot (`copy`, refreshed via
  `updateCopyIfNeeded`) top child first, then purges consumed events (`class_2767.method_27896`). Mutating children
  mid-dispatch does not crash, but removed children still get the current event and new ones miss it.
- **CustomPanel order**: `renderImpl` = plugin `renderBelow` → children → plugin `render`; `advanceImpl` = children
  first, then plugin `advance`.
- **`UIPanel.clearChildren()`** removes from the list and nulls each child's parent directly. It does **not** go
  through `removeComponent`, so StarUI's container hooks never see it. Vanilla uses it to rebuild panels
  (`WeaponPickerDialog.notifyFilterChanged()` → `clearChildren()` + synchronous rebuild of the same inner panel).
- **Focus**: `TextField.grabFocus()` registers a global focus owner (`class_2664`); `TextField.releaseFocus(event)`
  (readable name, obfuscated parameter; `null` is fine) releases it. A focused field removed from the tree keeps
  receiving keystrokes. `TextFieldAPI.hasFocus()` is public API.
- **Buttons**: a fired button sets a global "button fired this frame" flag (`class_1846.boolean_6`) that blocks other
  clicks until the next input poll.
- **`UIPanel.sendToBottom`** is a no-op when the component is currently the last child.

### How Refit Filters works today (a hand-made reactive loop)

Typing → `currentSearch` changes → its `onChange` sets `needsRefresh` → that panel's `advance` calls
`notifyFilterChanged()` → vanilla wipes and rebuilds the picker unfiltered → later in the same frame the
`DynamicInjection` (scripts) re-injects filters and re-filters → render shows the filtered list. No visible flicker,
but only because injection happens after UI advance and before render. Dead filter panels keep weak `onChange`
observers until GC.

---

## 4. Target model (user-facing)

```kotlin
Column {
    val ships = fleet.value                  // a UIState read inside a builder subscribes this Column
    for (ship in ships) ShipRow(ship)
    if (ships.isEmpty()) Text("No ships")
    Button("Add") { fleet.value = fleet.value + newShip() }   // handlers only change state
}
```

- No `Reactive {}`, no `invalidateLayout()`, no special `If`/`ForEach` blocks: agents write Compose-shaped code and
  it should just work. (Special control-flow blocks were rejected: an agent writes a plain `if`, it compiles, renders
  once, then silently goes stale.)
- Every container builder (`CustomPanel`, `Box`, `Column`, `Row`, `ScrollPanel` content) is a **scope**. Nested
  scopes rebuild independently.
- Optional `key(id) { ... }` for list rows (Step 2).
- Existing explicit bindings (`Checkbox(bind = ...)`, `TextField(bind = ...)`, `UIState.onChange`) keep working.

---

## 5. Design

### 5.1 Scope bookkeeping (new `internal class Scope`, owned by each container's `StarUIPanelPlugin`)

- `builder`: the container's builder lambda bound to its panel, plus the `StarUITheme` captured at build time
  (the theme stack is build-time only, so a rebuild must restore it).
- `reads`: states read during the last build. `UIState.value`'s getter records into
  `StarUIContext.currentScope` (thread-local, set by `withPlugin`). Reads in event handlers happen outside any build,
  so they are not tracked (correct).
- `dirty`, `disposed`, `depth`, `parent` scope.
- Subscription: `UIState` keeps **weak** references to subscribed scopes (no leaks if a scope is never disposed) and
  marks them dirty on change. Writing never rebuilds directly.

### 5.2 When dirty scopes rebuild (the race-safe part)

- **Only from the owning top-level StarUI node's own callbacks**: its plugin `renderBelow` (before its children
  render, so rewriting the subtree is safe) and its plugin `advance`. The flush handles all dirty scopes under that
  root, ancestors first; a rebuilt ancestor disposes its descendants' scopes, so they are skipped.
- Consequences: never rebuild during input dispatch; detached or hidden UI is never advanced or rendered, so it never
  rebuilds ("zombie" scopes cannot run). Hidden but attached UI rebuilds lazily when it is shown again.
- Never flush from scripts (`StarUIManager.advance`), because fast-forward runs those several times per frame.
- Writes made during a build only mark scopes dirty for the next flush. Loop guard: at most N (8) rebuild passes per
  flush; when exceeded, log an error naming the scope and the states it read, then stop until the next frame.

### 5.3 What a rebuild does (Step 1, brute force)

1. Release keyboard focus if a `TextFieldAPI` in the subtree `hasFocus()` (call `releaseFocus(null)` by name).
2. Dispose child scopes and the subtree's plugins (`StarUIPanelPlugin.dispose()`: clear handler lists, remove its
   `UIState` bindings explicitly, drop scope subscriptions).
3. `clearChildren()` on the container and reset the plugin's per-child maps (`childSizing`, `childPadding`,
   `childMargins`, `childAlignments`, `childAnchors`, `childMeasurables`, `childLayoutDelegates`, `childInnerOffset`).
4. Re-run the builder inside `StarUIContext.withPlugin(plugin)` and the captured theme, with read tracking on.
5. Request layout (5.4).

Known Step 1 limitation: vanilla state inside the rebuilt scope resets (scroll offset, caret, hover). Step 2 fixes it.

### 5.4 Automatic re-layout (Phase 1, needed by everything)

- Public `requestLayout()` on `StarUIPanelPlugin` (replaces callers of internal `invalidateLayout()`): marks the node
  and every StarUI ancestor up to the top-level node as needing layout; the top-level node's next `runLayout()`
  re-measures and re-lays out the whole tree (UIs are small; measured costs are tiny).
- Triggers: children added or removed under a StarUI container (existing `addComponent`/`removeComponent` hooks plus a
  new `clearChildren` hook), `Text` content changes (hook vanilla label `setText(String)`, readable name, when the
  label has an attached StarUI plugin or a StarUI parent), size modifiers applied after the first layout.
- Decide and test whether `onLayout` fires on every layout pass or only the first (today: once, then cleared).

### 5.5 Detach and dispose

- `@Before` hook on `UIPanel.clearChildren()` (match `UIPanelAPI` subtype, name `clearChildren`, 0 params): dispose
  StarUI subtrees under the removed children. Do **not** dispose on `removeComponent` (users move components by
  remove and add again); liveness gating in 5.2 already stops detached scopes from rebuilding, and weak references
  stop leaks.
- Optional public `dispose()` for users who tear UI down themselves.

### 5.6 Errors that name the fix (agents learn from them)

- A DSL component call while StarUI is dispatching an event (handler context) and no builder is active: throw
  "UI built inside an event handler; change a UIState instead and read it in the builder".
- Rebuild loop guard message (5.2).
- Keep the existing layout-guard style for anything new.

### 5.7 Step 2: in-place reuse (Incremental-DOM style, no virtual tree)

- During a rebuild, the container's old children become a reuse pool. Each DSL call asks a cursor for the next old
  child of the same component kind (tag stored on the plugin) at the same position, or the child with the same
  `key(...)`. Match: reuse the native object, update mutable properties (text, colours, size/margins/padding/
  alignment, bindings), reset and re-apply modifier callbacks (clear handler lists; `ButtonListener` has
  `clearOnClickFunctions`), recurse into nested scopes. No match, or a creation-time property changed (button font or
  cut style, area-checkbox colours): recreate. Unused old children are removed and disposed at the end.
- Same public API as Step 1; code written for Step 1 keeps working.
- Per-component "update in place" list is the bulk of the work (about ten component kinds in `Content.kt`,
  `Controls.kt`, `Containers.kt`, `Spacers.kt`).

---

## 6. Phases

Each phase: write the test group(s) first, run, see them fail, implement, run the full suite, look at the PNGs.

### Phase 0: prep
- Ask the user: commit the current uncommitted work first? New branch for this work (suggest `reactive-scopes`)?
- Re-run the full suite to confirm the 427/427 baseline.

### Phase 1: automatic re-layout
Tests (`native-smoke/kotlin/RelayoutTests.kt`):
- adding a child to a WrapContent Column after the first frame grows it and positions the new child (no manual call);
- removing a child shrinks it; `clearChildren()` then re-adding re-lays out;
- changing a `Text`'s string in a Row re-measures and moves the next sibling;
- a nested change propagates to the top-level node and re-applies its anchor (bottom-right anchored WrapContent root
  stays pinned);
- `onLayout` semantics (whatever is decided) and no layout work when nothing changed (count layout passes).
Update `dynamic-invalidate-relayout` to the new API.

### Phase 2: scopes, disposal, `clearChildren` hook
Tests: plugin `dispose()` clears handlers and removes bindings from `UIState` (observer count drops);
`clearChildren` on a panel holding StarUI UI disposes it; a moved component (remove + add) keeps working; weakly held
scopes are collectable (best effort: drop references, `System.gc()`, check a WeakReference).

### Phase 3: read tracking and Step 1 rebuild
Tests (`ReactiveTests.kt`):
- `if (flag.value) Text("A") else Text("B")` swaps after `flag.value = !flag.value` plus a frame;
- `for (i in 0 until count.value)` adds and removes rows; layout follows;
- nested scopes: changing an inner state rebuilds only the inner scope (outer builder run count unchanged);
- state written in a button click (real input through `Input.sendList`) updates UI on the next frame, not during
  dispatch (assert child list unchanged right after dispatch, changed after `frame()`);
- detached scope never rebuilds (remove its root, change state, advance, assert builder not run);
- hidden-then-shown scope rebuilds once when shown;
- rebuild-loop guard trips and logs instead of hanging;
- focused `TextField` inside a rebuilt scope releases focus (`hasFocus()` false, no stray keystrokes);
- builder that reads the same state twice subscribes once; unread states do not trigger rebuilds;
- error message when building UI inside an event handler.

### Phase 4: agent tooling
- `run-test-suite.ps1 -Preview <name>`: runs only a registered preview (`StarUITestSuite.preview("name") { ... }` in
  `native-smoke/kotlin/Previews.kt`), writes `<name>.png` and `<name>.layout.txt`.
- Layout dump (`dumpTree(panel)`): indented tree of component kind, text, rect relative to root, StarUI sizing, and
  attached modifiers; also usable from tests.
- `StarUIFramework/AGENTS.md` (ScyNation has one as a precedent): API cheat sheet, the reactive rules, where StarUI
  differs from Compose (`margin` exists, typed sizes, use `Button(onClick = ...)` on vanilla buttons, anchors only
  outside stacks), and the preview/test commands. Consider packaging it as a skill later.

### Phase 5: Step 2 in-place reuse and `key()`
Tests: a ScrollPanel's `yOffset` survives adding and reordering rows above it; a focused `TextField` keeps focus and
text through a rebuild; keyed rows keep identity across insert/remove/reorder (same native objects, `===`);
recreate happens when a creation-time property changes; handler lists are not duplicated on reuse (one click, one
call); Button listener chain not duplicated.

### Phase 6: live acceptance with Refit Filters (ask the user first)
- Refit Filters must first be ported from the old `org.starficz.staruiframework` API to the current one (separate,
  user-approved task; it has uncommitted changes of its own).
- Then port its search bar to a reactive scope and check in a live session (FR on): no unfiltered frame while typing
  (captures), no scopes left alive after picker refreshes (log a live-scope counter), focus survives a refresh.
- Also re-check Combat AI Tweaks' config dialog and Scy Nation's paperdolls in a live session.

---

## 7. Open questions for the user (ask before or during Phase 0)

1. Commit and branch strategy for the current uncommitted work.
2. `onLayout`: fire on every layout pass, or only the first?
3. Is every container being a scope acceptable, or should scopes be opt-in for very large builders?
4. Should Step 1 try to re-focus a rebuilt `TextField` (best effort), or just release focus until Step 2?
5. Refit Filters: may it be ported to the current API as part of Phase 6, and who owns its uncommitted changes?

## 8. Risks

- Rebuilds inside vanilla screens that rebuild themselves (picker, tabs, dialogs with fold animations that hold
  content via `setNext`): covered by liveness gating and the `clearChildren` hook, but only a live test proves it.
- Tooltips or hover pointing at components a Step 1 rebuild removed: expect brief visual quirks until Step 2.
- Performance with large builders: mitigated by nested scopes and Step 2; measure with the `perf-per-frame-costs`
  approach (add a rebuild-cost line).
- Binary compatibility for already compiled dependents (Combat AI Tweaks, Scy Nation): keep public signatures
  stable; additions only; deprecate rather than remove.
