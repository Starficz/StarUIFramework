# Traps

Things that go wrong, by the symptom you see. Read this before writing UI code and again before reviewing a
capture.

Contents: [Reactive](#reactive) · [Layout and sizes](#layout-and-sizes) · [Text](#text) ·
[Controls and input](#controls-and-input) · [Screens and inject](#screens-and-inject) ·
[Vanilla objects](#vanilla-objects) · [Drawing](#drawing) · [Look](#look) · [Build and runtime](#build-and-runtime) ·
[Process](#process)

## Reactive

| Symptom | Cause | Fix |
|---|---|---|
| "a component was added to a reactive container from outside its builder block" | UI built in a click handler (or another callback) | Write a `UIState` in the handler; build in the builder from it |
| The UI does not update after a state change | The state is read outside the builder (in a handler, `onAdvance`, a `val` captured before the builder, `peek()`), so nothing subscribed | Read `state.value` inside the builder of the container that shows it |
| A whole screen rebuilds when one label changes | The state is read in an outer builder | Move the read into the smallest container that shows it |
| A state keeps going back to its first value | The `UIState` is created inside a builder that re-runs | Create it once, outside the UI or in an outer builder that does not read it |
| The error log names a builder stopped after 8 passes | The builder writes a state it reads | Compute the value as a plain `val` in the builder |
| A text field's focus or a row's state jumps to another row after a removal | List items not keyed | `key(item.id) { ... }`, `LazyColumn(key = { it.id })` |
| A button loses hover or a field loses focus on every rebuild | An argument fixed at creation changes every time (a colour computed with a new alpha, a font), so a new object is made | Keep creation-time arguments stable; change only what updates in place (text, value) |
| A row of a long list forgets what was typed in it | Lazy rows far off screen are thrown away with their native state | Keep it in a `UIState` keyed by the item |
| Game values on screen are stale | Game data is not a `UIState`; nothing re-ran the builder | Mirror the value into a `UIState` from `onAdvance` when it differs |

## Layout and sizes

| Symptom | Cause | Fix |
|---|---|---|
| An exception about a fill child in a wrapping container | A fill child (`fillMaxWidth`, `FlexSpacer`, `Axis.FillMax`) on the main axis of a Column or Row that wraps | Give the container a known size on that axis (`Size.fillMaxWidth()`, `exact`) |
| An exception about an anchor | `Modifier.anchor` on a child of a Column, Row, Box or lazy list | Anchors only in a `CustomPanel`; elsewhere `align`, `spacing`, `padding` |
| `Size.wrapContent()` does not compile | The component takes a `BoundedSize` (Spacer, ShipDisplay, lazy lists, sliders, Holo) | `exact` or `fill` |
| `Overflow(Visible, Scroll)` throws | A scroll axis clips both axes | `Overflow.ScrollVertical` (Clip, Scroll) |
| The game stops with "Fatal: StarUI Layout Exception: a child fills the height of a Box that has no bounded height" when a table shows | A table's `custom { }` cell holds content that fills its height (`Size.fillMaxSize()`); the table measures cells to size its rows | Wrap or exact sizes in custom cells, placed with `Modifier.align(Alignment.CenterLeft)` |
| A list does not scroll; it grows past the screen | Its height wraps: only an exact or fill axis overflows | An exact or fill height |
| A fill child inside a scrolling list is 0 tall | A fill along a scroll axis gets what is left of the visible size, 0 once the list overflows | Exact sizes for rows; fill only for a footer spacer |
| A gap is 4 px off | `margin` and `spacing` add up; a border adds its width to the padding | Read the positions in `dumpLayout()` and adjust one of them |
| `.border().padding()` does not do what it does in Compose | Modifiers are not order-sensitive, and a border takes its own space | `padding` is the gap inside the border |
| Raising a child above its siblings reorders a Column | Vanilla's `bringComponentToTop` also raises the panel in its parent | Remove the child and add it again |
| The last visible row is cut in half | The scroll height is not a whole number of rows | `n * rowHeight + (n - 1) * spacing` |
| A drawn line or quad is upside down or off by the panel height | Render callbacks use screen coordinates, y up; StarUI layout is top-left, y down | Draw from the plugin's `x`, `y` (bottom), `width`, `height` |

## Text

| Symptom | Cause | Fix |
|---|---|---|
| A long name runs into the next column | A `Text` wraps by default, and a wrap-size one is as wide as its text | `Text(name, Size.fillMaxWidth(), overflow = TextOverflow.Ellipsis)` (or an exact width): one line cut with `...`, the full name in a tooltip |
| A paragraph is one very long line | A wrap-size `Text` is as wide as its text | Give it an exact or fill width; it then wraps and grows in height |
| A `%` vanishes in a tooltip | `Modifier.tooltip(text)` and `addPara` format their text | `%%` |
| A centred text that wraps has its second line flush left | Vanilla's label centres the block of lines, not each line | Keep centred text (a tagline) to one line: shorten the words or `overflow = TextOverflow.Ellipsis`; or align it left |

## Controls and input

| Symptom | Cause | Fix |
|---|---|---|
| A `buttonGroup` throws | A group needs each button's `bind` | `AreaCheckbox(text, bind = state, buttonGroup = group)` |
| A click on the selected mode or tab turns every toggle on | A `FilterGroup` is a filter bar: a click on its only selected button selects them all | Modes in a `RadioGroup`; [tabs](recipes.md#tabs) are buttons on one `UIState<Int>` |
| One button of a line has other corners | A recipe or default with another cut (StarUI's `Button` defaults to `TL_BR`, the cost button recipe to `ALL`) | Pass the line's `cutStyle` to every button on it |
| A button's new colour or font does not show | Colours, font, cut and shortcut are fixed when the button is made | They come from state read in the builder; a change makes a new button (expected) |
| Clicks fall through an overlay onto the screen under it | A hand-made overlay panel passes input on | Use a modal layer (`Layer` / `showLayer`): it takes every key and click. On a panel of your own, `Modifier.plugin { consumeEvents = true }` |
| A window opens again every time its screen rebuilds, or never closes | `showLayer` in a builder (it throws), or a `Layer` whose state nothing sets back | `Layer` in the builder under a state; set the state false in `onDismissRequest` and in the window's close button |
| A vanilla button's own action stopped | A listener was replaced | `button.onClick { }` from `vanilla` keeps the previous listener |
| A tooltip covers the next button | Default location is above | `Modifier.tooltip(location = TooltipLocation.LEFT)` in a right hand column |

## Screens and inject

| Symptom | Cause | Fix |
|---|---|---|
| A `StarUIPlugin` panel shows only after reopening the screen | The plugin registered after the screen was built | `StarUIManager.registerPlugin` in `onApplicationLoad` |
| An injected panel shows the data of an old screen | The builder captured vanilla objects that a vanilla rebuild replaced | Read them from the panel inside the builder (`this.parent`, getters) |
| The injection appears twice, or not again after a rebuild | A key per call, or a hook on a method that runs after the panel was cleared | One fixed key per panel, prefixed with the mod id; hook the panel's first build |
| `InjectPlacement.Bottom` throws | The target is a StarUI reactive container | Put the components in its builder |
| The campaign map shows through an interaction dialog's side panel | Vanilla draws nothing behind the panel its `getVisualPanel().showCustomPanel` returns | `visualPanel.showStarUIPanel(w, h) { }` (black by default), or `Modifier.background(Color.BLACK)` on the root you build in it |
| A change to the root call of a dialog (its size, a modifier) does not show after a hot reload | The `showDialog` call (or `createCustomDialog`, a plugin's `init`) is not a builder: it ran once when the dialog opened | Close and reopen the dialog, or open it with `Dialog` from a builder (it follows reloads); everything inside the root's builder updates by itself |

## Vanilla objects

| Symptom | Cause | Fix |
|---|---|---|
| A ship is smaller than its box and sits high | Vanilla shrinks ship displays to 95% and lifts them 3% | `ShipDisplay(..., exactScale = 1f)` |
| A port (`StatModGrid`, `ShipList`, ...) costs time every frame | Ports that read live game objects are made again on every call | Keep them in a builder that re-runs rarely |
| `CostDisplay` or a faction `RelationshipBar` throws on the title screen | They need a campaign | Only in campaign screens |
| A label moved to another panel lost its handlers | It was taken out of a panel that was thrown away before it was added elsewhere | Add it to its new parent first |
| A `Spacer` does not show | Spacers draw nothing | `Modifier.background(color)` |

## Drawing

For `Modifier.onRender` / `onRenderBelow` code.

| Symptom | Cause | Fix |
|---|---|---|
| Empty hole where an icon should be | (engine) `getSprite` on a path that was never loaded returns a 0 x 0 sprite | Check `getWidth() > 0`; load the texture; pick a fallback |
| A quad after a sprite comes out textured or white | (engine) `Misc.renderQuad` does not disable textures | Disable `GL_TEXTURE_2D` around your quads and enable it again after |
| A stray 1 px line next to an animated element | A quad with zero or negative width still draws | Guard `w > 0 && h > 0` |
| Everything after your panel renders wrong | GL state left changed | Restore it; wrap raw GL in `glPushAttrib` / `glPopAttrib` |
| The panel pops in while the screen fades | The `alpha` passed to `onRender` ignored | Multiply every alpha by it |
| Blurry lines or icons | Fractional coordinates, or a sprite scaled to a fraction | Round positions and sizes |
| No sprite for a weapon | Weapon pictures are several files (mount, barrel) | Use the game's weapon icon widget or text |
| The last rows of a list look dim | Vanilla's scroll panel shades its edges | Expected; `StarUITheme.scroll.renderShadows` turns it off. Do not judge colours on the edge rows |

## Look

| Symptom | Cause | Fix |
|---|---|---|
| "Looks like a web page" | Tinted tiles behind rows and cards | Black interior, 1 px border, fill only for the selected row |
| Bars look too bright, flat or "off" | A bar of your own or colours tuned by eye | `ProgressBar` (vanilla's widget) with the settings colours |
| A button reads as mod UI | A coloured fill (green, red, amber) | The faction's base label on its dark fill, as every vanilla button; story colours only for a story point |
| Panel feels crowded | Explaining sentences on the panel | Tooltip rule; then fewer values; then roomy density |
| Bar label unreadable | Label crosses the fill end, or sits on a bright fill | Short centred label in the text colour |
| Heading looks like a button | A heading of your own, higher or with cut corners | `SectionHeading` (vanilla's own bar), centred |
| The panel reads as one wall of rows | No grouping | A section heading over each group, 10 px above and below it, as vanilla |
| Text in a framed block looks glued to the border | Less than 5 px between the border and the text | 10 px (`opad`) |
| A status icon looks like a sticker | Drawn in a black box or disc | The icon with an outline of its own shape |
| The player hunts for the button | A block of stacked buttons under the card | Each action on the strip of the thing it changes; rare ones in the bottom row |
| The confirm looks home made | Confirm and Cancel in place of the button, or a custom modal | The game's popup: a `Dialog` with Confirm and Cancel ([buttons.md](buttons.md#confirm)) |
| A gray faction's accent turns cyan | The bright UI colour; unset bright is base mixed toward pale cyan | `baseUIColor` |
| The first column of a grid has no left border | A tile at x 0 of a scroll area: the area clips its left border | Start the grid 2 px in (`Modifier.padding(left = 2f)` on the scroll container's content) |

## Build and runtime

| Symptom | Cause | Fix |
|---|---|---|
| `NoSuchMethodError` from `kotlin.*` at runtime | Compiled against a newer Kotlin stdlib than LazyLib's 2.1.x | `-api-version 2.1` |
| "unresolved reference 'lwjgl'" or "cannot access class 'Logger'" when compiling | The game's other jars are not on the classpath | Add `starsector-core/lwjgl.jar`, `lwjgl_util.jar` (GL11 in render code) and `log4j-1.2.9.jar` (`Global.getLogger`) ([setup.md](setup.md#compiling)) |
| `SecurityException` on reflection | The game's script class loader refuses `java.lang.reflect` | Use the API or StarUI's toolkit; for anything else, the reflection route the game allows (`starsector-knowledge`, `docs/ui/attach-reflection.md`, when installed) |
| `column(...) { }.sortBy { it.x }` does not compile with `sortBy` as a lambda argument | `sortBy` is a fluent call on the returned column, not a parameter | `column("Cost") { "${it.cost}" }.sortBy { it.cost }` |
| A name from Compose does not exist (`Modifier.size`, `fillMaxWidth()` as a modifier, `remember`) | StarUI's API differs | Look it up in `api/`; sizes are the `size` argument |
| A hot reload did not change an open StarUI panel | No `StarUI: hot reload N, rebuilding M UI scopes` line in the game log: the game was not started by `starsector-hotreload`, or runs a StarUI jar older than 1.0.0-alpha; or the reload printed `WARN` (a new static field: restart) | Start the game with `hot.sh start`; with a dependency jar, pass the current one (`--dep`, `--dep-jar`); restart after a `WARN` |

## Process

- **Tuning a vanilla widget by eye fails.** Use the vanilla widget (StarUI wraps most), or translate its draw code
  and compare pixels at 2x.
- **Reading code does not find alignment faults.** Only a capture does. Check every state, not only the default.
- **A mockup with happy data hides every overflow.** Put the worst case values into the boards and the test data.
- **A style note that can be read two ways** ("make the headings smaller") costs a round when guessed wrong. Show
  both readings and ask.
- **"Vanilla does it" needs proof from vanilla's code.** Look it up ([vanilla-styles.md](vanilla-styles.md), the
  game's sources) before adding or removing something on that ground; dead code in the jar does not count.
- **Two rejected tries at the same element** mean the idea is wrong, not the pixels: run an experiment round
  ([experiments.md](experiments.md)).
- **An indicator nobody asked for** (a second bar, a badge, a count) needs an answer to "what does the player do
  with this" before it goes on a board.
- **Invented text slips through.** Names of states, taglines, tooltips and popup questions are written by you and
  read by the player. List them on a text board for approval ([mockups.md](mockups.md#what-the-boards-must-show)).
- **One approved screen is not the style.** Pick the archetype from the content and vary the parts.
- **A capture can hide a layout fault as a style fault.** Measure the pixels (or read `dumpLayout()`) before
  changing a padding value.
