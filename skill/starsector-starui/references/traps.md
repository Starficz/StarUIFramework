# Traps

By symptom. Read before writing UI code and before reviewing a capture.

Contents: [Reactive](#reactive) · [Layout and sizes](#layout-and-sizes) · [Text](#text) ·
[Controls and input](#controls-and-input) · [Screens and inject](#screens-and-inject) ·
[Vanilla objects](#vanilla-objects) · [Drawing](#drawing) · [Look](#look) · [Build and runtime](#build-and-runtime) ·
[Process](#process)

## Reactive

| Symptom | Cause | Fix |
|---|---|---|
| "a component was added to a reactive container from outside its builder block" | UI built in a handler | Write a `UIState` in the handler; build from it |
| UI does not update after a state change | State read outside the builder (handler, `onAdvance`, a `val` captured before, `peek()`) | Read `state.value` in the builder that shows it |
| A whole screen rebuilds when one label changes | Read in an outer builder | Move the read to the smallest container |
| A state snaps back to its first value | `UIState` created in a builder that re-runs | Create it once, outside the UI or in a non-reading outer builder |
| Log: builder stopped after 8 passes | Builder writes a state it reads | Derive a plain `val` |
| Focus or row state jumps rows after a removal | Unkeyed list | `key(item.id) { }`, `LazyColumn(key = { it.id })` |
| Hover or focus lost on every rebuild | A creation-time argument changes each run (new alpha colour, font) | Keep those stable |
| A long list's row forgets typed text | Far-off lazy rows are thrown away | Keep it in a `UIState` keyed by item |
| Game values stale | Game data is not a `UIState` | Mirror it into one from `onAdvance` when it differs |

## Layout and sizes

| Symptom | Cause | Fix |
|---|---|---|
| Exception: fill child in a wrapping container | Fill on the main axis of a wrapping Column or Row | Give the container a known size there |
| Exception about an anchor | `Modifier.anchor` in a Column, Row, Box or lazy list | Anchors only in `CustomPanel` / `AnchorPanel`; else `align`, `spacing`, `padding` |
| `Size.wrapContent()` does not compile | `BoundedSize` component (Spacer, ShipDisplay, lazy lists, sliders, Holo, SkillIcon) | `exact` or `fill` |
| `Overflow(Visible, Scroll)` throws | A scroll axis clips both | `Overflow.ScrollVertical` |
| Fatal "a child fills the height of a Box that has no bounded height" when a table shows | Fill-height content in a `custom { }` cell | Wrap or exact, placed with `Modifier.align` |
| A list grows past the screen instead of scrolling | Wrap height: only exact or fill overflow | Exact or fill height |
| A fill child in a scrolling list is 0 tall | Fill along a scroll axis gets the leftover, 0 once overflowing | Exact rows; fill only for a footer spacer |
| A gap is 4 px off | `margin` + `spacing` add up; a border adds to padding | Read `dumpLayout()` positions, fix one |
| `.border().padding()` differs from Compose | Order does not matter; a border takes space | `padding` is the gap inside the border |
| Raising a child reorders a Column | `bringComponentToTop` also raises its parents | Remove and re-add the child |
| Last visible row cut in half | Scroll height not whole rows | `n * rowHeight + (n - 1) * spacing` |
| Drawing upside down or off by the panel height | Render callbacks are screen coordinates, y up | Draw from the plugin's `x`, `y` (bottom), `width`, `height` |

## Text

| Symptom | Cause | Fix |
|---|---|---|
| A long name runs into the next column | A wrap-size `Text` is as wide as its text | `Text(name, Size.fillMaxWidth(), overflow = TextOverflow.Ellipsis)` |
| A paragraph is one long line | Wrap-size `Text` | Exact or fill width (wraps, grows in height) |
| UI shows faintly through a tooltip | Vanilla tooltips draw at 85% (`bgAlpha`) | `Modifier.tooltip { setBgAlpha(1f); ... }` for opaque |
| `%` vanishes, or shows as `%%` | Only `addPara` with highlight arguments formats (`String.format`) | `%%` there only; plain `%` elsewhere |
| A wrapped centred text's second line is flush left | Vanilla centres the block, not each line | Keep centred text to one line, or align left |

## Controls and input

| Symptom | Cause | Fix |
|---|---|---|
| A `buttonGroup` throws | Members need `bind` | `AreaCheckbox(text, bind = state, buttonGroup = group)` |
| Clicking the selected mode turns all toggles on | `FilterGroup` behaviour | Modes: `RadioGroup`; [tabs](recipes.md#tabs) are buttons on a `UIState<Int>` |
| One button in a line has other corners | Another default cut (`Button` `TL_BR`, cost button recipe `ALL`) | Pass the line's `cutStyle` to all |
| New button colour or font does not show | Fixed at creation | From state in the builder; a new button is expected |
| Clicks fall through an overlay | A hand-made overlay passes input | Modal `Layer` / `showLayer`; on your own panel `Modifier.plugin { consumeEvents = true }` |
| A window reopens on every rebuild, or never closes | `showLayer` in a builder (throws), or a `Layer` whose state nothing resets | `Layer` under a state; reset it in `onDismissRequest` and the close button |
| A vanilla button's own action stopped | Listener replaced | `button.onClick { }` from `vanilla` keeps it |
| A tooltip covers the next button | Default location is above | `Modifier.tooltip(location = TooltipLocation.LEFT)` in a right column |

## Screens and inject

| Symptom | Cause | Fix |
|---|---|---|
| `StarUIPlugin` panel shows only after reopening | Registered after the screen was built | `StarUIManager.registerPlugin` in `onApplicationLoad` |
| Injected panel shows an old screen's data | Builder captured objects a vanilla rebuild replaced | Read them from the panel inside the builder |
| Injection twice, or not back after a rebuild | A key per call, or a hook after the panel was cleared | One fixed mod-prefixed key per panel; hook the first build |
| `InjectPlacement.Bottom` throws | Target is a StarUI reactive container | Put components in its builder |
| Campaign map shows through a dialog side panel | Vanilla draws nothing behind `showCustomPanel` | `visualPanel.showStarUIPanel(w, h) { }` (black), or `Modifier.background(Color.BLACK)` |
| A dialog's root call change does not show after hot reload | `showDialog` / `createCustomDialog` / `init` ran once | Reopen, or use `Dialog` in a builder; content inside updates itself |

## Vanilla objects

| Symptom | Cause | Fix |
|---|---|---|
| A ship is small and sits high | Vanilla: 95%, 3% up | `ShipDisplay(..., exactScale = 1f)` |
| A port costs time every frame | Live-object ports remake on every call | Keep them in rarely re-running builders |
| `CostDisplay` / faction `RelationshipBar` throws on the title screen | Needs a campaign | Campaign screens only |
| A moved label lost its handlers | Its old panel was thrown away before it was re-added | Add it to the new parent first |
| A `Spacer` does not show | Draws nothing | `Modifier.background(color)` |
| `TooltipMakerPanel` text sits 5 px right of StarUI text | Vanilla's tooltip inset | Expected; build it in StarUI if it must line up |
| `SkillIcon` shows nothing on hover | Vanilla's skill tooltip is not in the API | `Modifier.tooltip { }` |
| `SkillIcon` does not follow a person learning the skill | The level overload shows the level passed | `SkillIcon(skillId, person)` |

## Drawing

For `Modifier.onRender` / `onRenderBelow`.

| Symptom | Cause | Fix |
|---|---|---|
| Empty hole for an icon | (engine) unloaded path gives a 0 x 0 sprite | Check `getWidth() > 0`; load it; fallback |
| Quad after a sprite is textured or white | (engine) `Misc.renderQuad` leaves textures on | Disable `GL_TEXTURE_2D` around quads |
| Stray 1 px line by an animated element | Zero or negative quads still draw | Guard `w > 0 && h > 0` |
| Everything after your panel renders wrong | GL state left changed | Restore; `glPushAttrib` / `glPopAttrib` |
| Panel pops in during a screen fade | `onRender`'s `alpha` ignored | Multiply every alpha by it |
| Blurry lines or icons | Fractional coordinates or scales | Round |
| No sprite for a weapon | Weapons are several files | The game's weapon icon widget, or text |
| Last rows look dim | Scroll panel edge shading | Expected; `StarUITheme.scroll.renderShadows` off; don't judge colour there |

## Look

| Symptom | Cause | Fix |
|---|---|---|
| "Looks like a web page" | Tinted tiles | Black interior, 1 px border, fill only the selected row |
| Bars look off | A home-made bar or eyeballed colours | `ProgressBar` with settings colours |
| A button reads as mod UI | Coloured fill | Faction base label on dark fill; story colours only for story points |
| Crowded panel | Explaining sentences | Tooltip rule; fewer values; roomy density |
| Unreadable bar label | Crosses the fill end or sits on bright fill | Short centred label, text colour |
| A heading looks like a button | Home-made heading | `SectionHeading`, centred |
| One wall of rows | No grouping | A section heading per group, 10 px above and below |
| Text glued to a border | Under 5 px gap | 10 px (`opad`) |
| A status icon looks like a sticker | Box or disc behind it | Outline of its own shape |
| The player hunts for a button | Stacked buttons under the card | Each action on its thing's strip; rare ones in the bottom row |
| The confirm looks home made | Inline Confirm/Cancel or a custom modal | A `Dialog` ([buttons.md](buttons.md#confirm)) |
| A gray faction's accent turns cyan | Unset bright is base mixed to pale cyan | `baseUIColor` |
| A grid's first column has no left border | The scroll area clips x 0 | Start 2 px in (`Modifier.padding(left = 2f)`) |

## Build and runtime

| Symptom | Cause | Fix |
|---|---|---|
| `NoSuchMethodError` from `kotlin.*` | Compiled against a newer stdlib than LazyLib's (3.1: 2.4.21) | `-api-version 2.4` or lower |
| "metadata version 2.4.0, can read up to 2.3.0" | Compiler older than 2.3 | Kotlin 2.3+ |
| "unresolved reference 'lwjgl'" / "cannot access class 'Logger'" | Game jars missing | `lwjgl.jar`, `lwjgl_util.jar`, `log4j-1.2.9.jar` ([setup.md](setup.md#compiling)) |
| `SecurityException` on reflection | The script loader refuses `java.lang.reflect` | API or StarUI's toolkit; else the allowed route (`starsector-knowledge` `docs/ui/attach-reflection.md`) |
| `.sortBy` as a lambda argument does not compile | It is a fluent call | `column("Cost") { "${it.cost}" }.sortBy { it.cost }` |
| A Compose name is missing (`Modifier.size`, `remember`) | StarUI differs | Look it up in `api/`; sizes are `size` |
| Hot reload did not change an open panel | No `StarUI: hot reload N, rebuilding M UI scopes` in the log: not a `starsector-hotreload` game, an old StarUI jar, or a `WARN` (new static field) | `hot.sh start` (`--dep` / `--dep-jar` for a dependency); restart after `WARN` |

## Process

- **Tuning a vanilla widget by eye fails**: use the widget, or port its draw code and compare at 2x.
- **Code review does not find alignment faults**: captures do, in every state.
- **Happy mockup data hides overflows**: worst cases in boards and test data.
- **A note readable two ways**: show both and ask.
- **"Vanilla does it" needs proof** from vanilla's live code ([vanilla-styles.md](vanilla-styles.md), sources).
- **Two rejections of one element**: the idea is wrong; run a round ([experiments.md](experiments.md)).
- **An unrequested indicator** (bar, badge, count) needs "what does the player do with this" first.
- **Invented text** (states, taglines, tooltips, popup questions) goes on a text board
  ([mockups.md](mockups.md#what-the-boards-must-show)).
- **One approved screen is not the style**: archetype from the content, vary the parts.
- **A capture can hide a layout fault as a style fault**: measure (or `dumpLayout()`) before changing padding.
