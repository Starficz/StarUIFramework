# Showcase plan: everything StarUI does, each with its code

**Done** (2026-11-14): built as below, checked page by page in game by the user; the old `ShowcaseUIPlugin` is
removed. What changed from the plan is under [As built](#as-built).

The current `ShowcaseUIPlugin` (1100 lines, title screen) grew as a debugging page: sections of options, no order,
no code shown, nothing for layers, dialogs or toasts. It is replaced by a systematic showcase: one page per feature,
each demo live with the code that makes it beside it, then the recipes.

## Decided (2026-11-14, with the user)

- **Internal only** (changed for 1.0.0-alpha: StarUI's `ModPlugin` registers it in releases, opened by Ctrl + Shift
  + U, and the README tells users): it stays in `org.starficz.staruisamples`. For the user, for screenshots and
  Discord posts, for checking features in game, and as the skill's `examples/`.
- **Code from its own source**: each demo sits between markers in the showcase's `.kt` files; the showcase reads
  the file at runtime (`Global.getSettings().loadText("src/...")`, a path inside the mod) and shows the lines
  between them. One copy, so the code shown is the code running. Without `src/` the code panes say so.
- **Nav plus demo and code cards** (layout below).

## The screen

A full-screen modal `showLayer`, opened by a "StarUI showcase" button on the title screen (the registered
`StarUIPlugin`; as built, Ctrl + Shift + U instead, see below). A layer rather than the title screen's own panel, so Escape closes it and the window demos
(dialogs over it, toasts, pause) behave as they do in a mod.

```
+--------------+---------------------------------------------------------------------+
| StarUI 0.1.0 | Buttons                                                  [outlines] |
|              | The game's own buttons: cut corners, fonts, colours, shortcuts.     |
| LAYOUT       | +-------------------------------+ +-------------------------------+ |
|   Column/Row | | Cut styles                    | | Row(spacing = 5f) {           | |
|   Box        | | [ALL] [TL_BR] [TOP] [NONE]    | |   for (cut in CutStyle...     | |
|   Sizes      | |                               | |     Button(cut.name, cut...   | |
|   ...        | +-------------------------------+ +-------------------------------+ |
| CONTENT      | +-------------------------------+ +-------------------------------+ |
|   Text       | | Shortcut and disabled         | | Button("Confirm", shortcut... | |
| > Buttons    | | [Confirm [G]]  [Locked]       | |                               | |
|   ...        | +-------------------------------+ +-------------------------------+ |
| RECIPES      |                                                                     |
|   Tabs       |  (the page scrolls)                                                 |
+--------------+---------------------------------------------------------------------+
```

- **Nav** (left, about 190 px): group headings in gray caps, one entry per page, the open page highlighted. A
  `ButtonGroup` of left-aligned plain buttons, so it is vanilla's list look. The page shown is one `UIState`, kept
  between openings.
- **Page**: title (Insignia 21), one line of what it covers, then the cards in a scrolling column.
- **Card**: a `Section`-style frame with the demo's title; the live demo on the left (its natural size, wrapping
  under when it is wide), the code on the right in a dark inset, Victor 12, keywords, strings and comments
  coloured. Long code scrolls inside its pane (height capped). A card whose subject cannot run here (a `StarUIPlugin`
  screen, an `inject` hook) shows only its code, with one line of why.
- **Outlines** toggle (top right): `Modifier.border` on every container in the demos, to show the layout, as the
  old showcase's checkbox did.
- Fits 1280 x 768 (the game's minimum) and uses the room on larger screens.

## Pages

Each bullet is a card. Live unless marked *code only*.

**Layout**
- Column and Row: spacing, `horizontalAlignment` / `verticalAlignment`, `grow` reversed, `FlexSpacer` pinning
  the last item to the far end.
- Box: the nine `Modifier.align` places; layers of content on top of each other.
- Sizes: exact, fill, wrap, mixed axes, fill with weights; `requestLayout` after changing one by hand.
- Spacing: `padding` by side, `margin`, `spacing`, `hitBoxPadding` (hover shows the hit box).
- Anchors: `CustomPanel` with `Modifier.anchor` (inside corners, above, below, left of, my corner to yours).
- Overflow: visible, clip, scroll, per axis; a scrolling column with a growing content.
- Section, Divider, SectionHeading: titled, untitled, colours, padding.
- Holo: vanilla's holo window, open and fold, `contentKey` swapping pages through the static, both backgrounds.

**Content**
- Text: every font (one line each), wrapping at an exact width, alignment, `highlightedText`, `TextOverflow`
  (ellipsis, clip), `measureTextSize`.
- Image: native, exact, `ContentScale` modes, max sizes; a sprite and an icon.
- ShipDisplay: a hull at sizes and options.
- Tooltips: `Modifier.tooltip("text")` and a built tooltip (`Modifier.tooltip { }` with TooltipMakerAPI).

**Controls**
- Button: cut styles, fonts, colours, `textAlign`, `enabled`, `shortcut`, the click sound.
- Checkbox and AreaCheckbox: `bind` to a state, sizes, colours.
- Button groups: `RadioGroup` (one of), `FilterGroup` (any of), with what is selected shown live.
- TextField: bound text, placeholder, font; the value shown live, and a list filtered by it.
- Slider and ProgressBar: bound value, ranges, a bar that follows a slider.

**Lists and tables**
- LazyColumn: 10 000 rows, keyed, each with its own state; add, remove, shuffle (the state follows the key).
- LazyRow: cards side by side.
- Table: text, styled and custom cells; sorting by a column (`sort` state); row tooltips; a row click selecting
  the row (`onRowClick` writing a state).

**State**
- `UIState` and rebuilds: two counters, each builder reading one, a rebuild counter per scope (only the builder
  that reads a state runs again).
- Keys: a list that reorders with and without `key(...)`: with keys each row keeps its checkbox, without it moves.
- `mutableStateOf` and the `by` delegate; derived values computed in the builder.
- Theme: one subtree with another `StarUITheme` (story colours), a button theme override.

**Input and drawing**
- Mouse: `onClick`, `onClickRelease`, `onClickOutside`, `onHover`, `onHoverEnter` / `Exit`, `onMouseHeld`.
- Keys: `onKeyDown`, `onKeyHeld`, `onKeyUp` (last key shown).
- Drawing: `onRender` and `onRenderBelow` with GL (a bar, a glow), `background`, `border`.
- Motion: `onAdvance` (a pulsing value), vanilla's slide (`setSlideData`, `slideIn`, `slideOut`).
- Layout callbacks: `onLayout`, `onFirstLayout`, `onPositionChanged` (sizes shown live); `dumpLayout()` of a demo.

**Windows**
- Layer: modal (dim, Escape) and non-modal; `Layer` from a builder under a state, `showLayer` from a click.
- Dialog: confirm and cancel, `DialogStatic` options side by side, a dialog over a dialog, `contentKey` pages, F2.
- Toasts: plain, coloured, each of the nine places, rich content, a burst of five (the "+N more" chip).

**Vanilla parts**
- One card per port: CargoList, CodexEntries, CommodityIcons, CostDisplay, EventProgressBar, FleetSummary,
  ImportanceIndicator, LabelledValue, PlanetInfo, PlaythroughData, RelationshipBar, SectorMap, ShipList,
  SkillPanel, StatGrid, StatModGrid, SurveyRequirements. The ones that need a campaign (a fleet, a market, the
  sector) say so on the title screen and show when opened in a campaign.

**Hosts** (*code only*, with what each is for)
- A `StarUIPlugin` screen (`addPanelToRefitTab` and the others); `registerPlugin` in `onApplicationLoad`.
- `inject` from a PatchLib `@After` hook; `injection`, `removeInjection`.
- `showStarUIPanel` in an interaction dialog's side panel.

**Recipes** (from the skill's `recipes.md`)
- Change row, Guarded button, Cost button, Tabs, Toast content (intel, accent, heading).

## Code

- `staruisamples/showcase/`: `Showcase.kt` (the layer, nav, page, card, outlines), `Snippets.kt` (reads a file
  once, cuts snippets between `// snippet: <id>` and `// end snippet`, removes the common indent, colours tokens),
  and one file per nav group (`LayoutPages.kt`, `ContentPages.kt`, ...). A card is
  `Card("Cut styles", snippet = "buttons-cuts") { /* the demo, between the markers */ }`.
- Snippet ids are checked: a card naming an id that its file does not have shows a red "missing snippet" line.
- `ShowcaseUIPlugin.kt` is removed; `ExampleUIPlugin.kt` stays (a small screen, the skill's second example).
  `build_skill.py` copies `staruisamples/` with its subfolder into the skill's `examples/`.
- Colouring: a small Kotlin tokenizer (keywords, strings, numbers, comments, `@` annotations, calls); one label
  per line with highlights in token order.

## Steps

1. **Staging** (native-smoke `stage-mod.sh`): link `src` into the staged mod as the other folders are, so
   `loadText("src/...")` works in hot games and the suite. A junction, nothing copied; the game does not read
   `src`.
2. **Shell**: layer, nav, page, card, snippet reading and colouring, with two pages (Buttons, Toasts) to judge the
   look. Seen in the hot game; the user approves the look before the rest.
3. **Pages**: the remaining groups, a page at a time, each seen in the hot game.
4. **Recipes** and **Hosts**.
5. Remove the old showcase; `build_skill.py` and the skill's "Where to look" point at the new one.

## Open points

- The recipes live in three places then: `recipes.md`, the suite's `SkillRecipes.kt`, the showcase. The showcase
  could become the copy the suite tests (`SkillRecipes.kt` calls its functions), leaving two.
- A copy button for a snippet needs the clipboard: LWJGL 2 only reads it, AWT's may be blocked. Left out.

## As built

- **Pages**: 32, in the ten groups above; one file per group in `staruisamples/showcase/`, each page naming its file
  for the snippets. The nav scrolls (it is taller than the screen).
- **Code font**: Insignia 15. Victor has no lowercase letters and no vanilla font is monospaced.
- **Colours**: IntelliJ's Darcula (the user's choice), its Kotlin named-argument blue included: keywords, strings,
  numbers, comments, annotations, properties after a dot, named arguments, declarations, and component calls in the
  declaration yellow (Darcula leaves calls plain; the yellow makes the tree read). Every word is a highlight, plain
  ones included: vanilla matches highlights in order from the end of the last, so a word left out could take a
  later word's colour.
- **Code panes**: the Text is exactly as wide as its longest line plus 16 px (vanilla wraps a few pixels before the
  measured width), in a `ScrollHorizontal` pane whose wheel is off (`ScrollTheme.useMouseWheel = false`): the wheel
  scrolls the page, the scrollbar and a right drag the code.
- **Snippets**: an id may mark several ranges, joined by a blank line, so a demo's state (kept outside its builder)
  shows with the demo. A card with no demo shows only code (Hosts, helper functions).
- **Fills**: `INSET` (the faction's dark at alpha 150) behind demo areas, `TILE` (dark, brighter) for blocks on it.
- **Wide cards** (`wide = true`): the demo above the code, for tables, lazy lists and the campaign parts.
- **Opening it**: Ctrl + Shift + U (a chord, so a stray key never opens it) on the title screen, the campaign screen and
  in combat, from a full-screen listener panel the plugin adds to each (no buttons); the same keys close it. The parts
  that need a campaign say so on the title screen.
- **Recipes**: `RecipePages.kt` is a third copy of `recipes.md` and the suite's `SkillRecipes.kt` (the open point
  above stands).
- **Suite**: `showcase-idle-layout` builds every page in one scrolling page and checks idle layout requests and the
  Text page's wrapped paragraph while scrolling.
