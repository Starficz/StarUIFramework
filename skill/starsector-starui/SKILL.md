---
name: starsector-starui
description: Design and build Starsector mod UI that looks like the game made it, in Kotlin with StarUI (StarUIFramework), a Compose-style DSL where every call makes a real vanilla UI object - Column, Row, Box, Text, Button, Table, LazyColumn, Section, Holo, sliders, ship displays - with reactive UIState, typed sizes, per-axis scrolling, StarUIPlugin screens and inject() into any vanilla panel. Holds the design guide (look, colour roles, parts, button placement and colour, density, the tooltip rule), a mockup generator for boards at game pixel size, every part built in StarUI, tested recipes, traps, and a verify loop with the bundled starsector-hotreload skill. Use it whenever a mod depends on staruiframework or imports org.starficz.starui, the user mentions StarUI, or designs, mocks up, builds, changes or reviews Starsector mod UI in Kotlin (panel, screen, dialog, overlay, list, table, button, tooltip, "make it look vanilla", screenshots with alignment, overlap, density or colour complaints).
---

# StarUI

Design, mock up, build and check Starsector mod UI that reads as part of the game. StarUI is a Kotlin DSL like
Jetpack Compose, but every call creates a **real Starsector UI object** in the panel you call it on and returns it:
no virtual tree. Layout, input, rendering and updates are added through PatchLib hooks.

## Version

**StarUI 1.0.0-alpha** (API may change) for **Starsector 0.98.5a-RC6**. Name both versions in your first reply.
Check `version` in `mods/StarUIFramework/mod_info.json` (ask for the game folder; never search the disk); if it
differs, the install wins: read signatures from its jar or sources. Numbers marked "(engine)" were measured on
0.98a-RC8: check with one capture.

## The bar

The user compares your panel with the vanilla screen beside it. Never, in any state or data: **overlapping text,
misaligned elements, cut off text or boxes.** Plain and clean passes; rich with one clipped label fails.

## Reading rules

1. Read this file to the end.
2. Before designing: [design-rules.md](references/design-rules.md) and
   [vanilla-styles.md](references/vanilla-styles.md), in full.
3. Before writing or reviewing code: [components.md](references/components.md), [reactive.md](references/reactive.md),
   [traps.md](references/traps.md), in full.
4. **`api/` defines what exists** (`api/org/starficz/starui/...`, KDoc with examples). Never write calls from memory
   or Compose habit.
5. Game code from the game's real sources: `starsector-knowledge` (`api/`, then `obf/`) when installed, else ask.
   Never `javap`.

## Tell the user what you can do

First UI message of a conversation ends with this list (drop lines the session cannot do). Wait for the answer to
the first line before boards or UI code (a recorded answer in project notes counts); the rest are defaults.

> What I can do on this UI. Say the word for any of it:
> - **Mock up first, or build directly?** Boards at game pixel size for your go before any code, or straight to
>   code with the result shown in game for your go. Which do you want?
> - **Before and after** (on): every change to existing UI as a pair, side by side.
> - **Text list** (on): every text I wrote for the player, for your approval.
> - **Experiment** (on when you are unhappy with something and name no fix): three different solutions for one
>   element, side by side, with my pick.
> - **In game check**: I run the panel in a hot-reload game, capture every state and fix what I see.

Once per conversation, or per project when it keeps UI notes (record what was switched off). Again when asked.
Subagents never send it.

## Workflow

1. **Read first**: the project's UI notes and component functions (reuse them), [design-rules.md](references/design-rules.md),
   two or three [gallery](references/gallery.md) images closest to the case.
2. **Content list** before layout: every value, every action and what it changes, every state (empty, one, full,
   selected, blocked, in progress, done), worst cases (40 char names, 9 digit credits, 300 day counters, 14 skills,
   200 entries, empty).
3. **Host and archetype** ([layout.md](references/layout.md)) by the vanilla screen with the same content; row heights
   by vanilla's lists ([density](references/design-rules.md#density)). Say which.
4. **Parts** from [parts.md](references/parts.md), buttons from [buttons.md](references/buttons.md). No invented widget
   where a listed part fits.
5. **Approval before code**: mockups ([mockups.md](references/mockups.md): no layout warnings, PNGs looked at, shown,
   wait for the go; changes as before and after pairs; invented text on a text board), or, if building directly,
   in-game captures. Skip only when the user says so or the layout is already approved (a label, colour, bug fix).
   **Boards or screenshots the user hands you** are approved: build from them, no boards of your own. Build their
   departures from vanilla (coloured fills, left headings with a count at the right, two-line table rows, a
   [not-vanilla](references/parts.md#not-vanilla) part) vanilla's way, and list each in one line in your first reply,
   offering their look (rule 2). Never copy or change them silently. A compact row of skill icons is vanilla
   (`SkillIcon`).
6. **Attach** ([setup.md](references/setup.md)): `StarUIPlugin` screen, layer (`Layer`, `showLayer`), a panel the API
   hands you, or `inject` from a PatchLib hook.
7. **State**: one `UIState` per thing the player selects or changes, outside the UI; builders read, handlers write
   ([reactive.md](references/reactive.md)).
8. **Build** per [parts.md](references/parts.md) with [components.md](references/components.md),
   [recipes.md](references/recipes.md), [traps](references/traps.md); repeated defaults in a `Theme`; motion:
   [motion.md](references/motion.md).
9. **Check**: `dumpLayout()`, then in game with `starsector-hotreload` (rebuilt jars reload; StarUI panels on screen
   rebuild themselves; capture every state) ([verify.md](references/verify.md)).
10. **Report**: review checklist on the captures, fix, say plainly what was not seen in game.

### Change requests on existing UI

1. Get the current state: a capture, or the layout rebuilt on a board.
2. Sort it ([experiments.md](references/experiments.md#when-to-run-one)): a fault is fixed; an instruction is done; a
   complaint without a fix, "ideas?", "experiment" or a second rejection starts a round; a two-way note gets both
   readings drawn.
3. Board (pair or experiment), wait for the go or pick.
4. Build, capture pairs, record the decision in the project's UI notes.

## Hard rules

Design (reasons in the references):

1. **Text never overlaps and is never cut.** Long one-line text: `Text(..., overflow = TextOverflow.Ellipsis)`
   (cut plus tooltip); wrapped text has an exact or fill width; number columns fit their widest value.
2. **Vanilla first.** Unsteered passes follow vanilla's own code ([vanilla-styles.md](references/vanilla-styles.md)),
   not taste, only code vanilla still uses. When the user asks for something else, build it, say in one line where
   it departs, keep vanilla elsewhere.
3. **Colours and fonts from the game**: `VanillaColors`, `Font`, settings colours, faction UI colours. No mod colours.
4. **Every button is the faction's base label on its dark fill**, destructive ones too; only story-point actions
   take story colours. Cuts: `TL_BR` commit/cancel row, `ALL` alone, `TOP` tabs, `BL_TR` action bank.
5. **Black interiors, no tinted tiles, no rounded corners.** Fill only selected row, buttons, heading bars, table
   header strips.
6. **Explanations go in hover tooltips** (350 px, 450 with a table); panel rules behind a `?` on the title line.
7. **Icons before words.** Costs in highlight, red when short.
8. **Actions next to what they change.** Commit/cancel row bottom right, pinned. Every button on screen at
   1600 x 900 and 1280 x 768.
9. **A blocked button stays**, grey, reason in red on a reserved line or its tooltip.
10. **Lists scroll, keep their offset, end on a whole row.** Empty: heading plus one gray line. Row heights as
    vanilla's lists of the kind; tables 20 px.
11. **Vanilla widgets are vanilla's** (StarUI wraps most); a copy is proven at 2x against vanilla.
12. **Spacing**: 3 within a group, 10 between blocks; section headings centred, 10 above and below.
13. **Confirmations are the game's popup** (a `Dialog`: [buttons.md](references/buttons.md#confirm)).

StarUI:

14. **Never from memory.** Signatures from `api/`, game facts from game sources. Say what you could not confirm.
15. **Builders build, handlers change state.** Adding a component from a handler throws. Read `state.value` in the
    builder of the container that shows it; a builder never writes a state it reads.
16. **Key lists whose items move**: `key(item.id) { }`, `LazyColumn(key = { it.id })`.
17. **Sizes are the `size` argument**; clipping and scrolling are `overflow`; anchors only in `AnchorPanel` /
    `CustomPanel`.
18. **No obfuscated names** in imports, casts, reflection strings or PatchLib matchers.
19. **Build it in StarUI.** `TooltipMakerPanel` only when truly simplest (a mod's helper that fills a tooltip); hover
    tooltips are `Modifier.tooltip { }`; never `createUIElement` / `addUIElement` by hand.
20. **Kotlin against LazyLib's runtime** (LazyLib 3.1: 2.4.21): compiler 2.3+, `-api-version` at most 2.4.
21. **Seen in game, or not done.** Never "tested" for code that only compiled.

## Where to look

| Question | File |
|---|---|
| Vanilla's look: colour roles, type, spacing, density, tooltip rule, wording, icons, numbers, what vanilla never does | [design-rules.md](references/design-rules.md) |
| What vanilla's code uses, counted (button colours and cuts, headings, rows, pads, fonts, tooltip widths) | [vanilla-styles.md](references/vanilla-styles.md) |
| Which part for this content, its look, how to build it | [parts.md](references/parts.md) |
| Button kind, colour, cut, size, place, label, state; confirm; tabs; toggles | [buttons.md](references/buttons.md) |
| Hosts, screen sizes, archetypes, layout contract, view state | [layout.md](references/layout.md) |
| Dependencies, compiling, `StarUIPlugin`, layers, dialogs, toasts, API panels, `inject` | [setup.md](references/setup.md) |
| Containers, controls, tables, sizes, overflow, lazy lists, spacing, modifiers, anchors, theme, windows, toolkit | [components.md](references/components.md) |
| `UIState`, rebuilds, keys, reuse, disposal, Compose differences | [reactive.md](references/reactive.md) |
| Tested recipes: change row, guarded button, cost button, tabs, toast content | [recipes.md](references/recipes.md) |
| Hover, unfold, highlight motion | [motion.md](references/motion.md) |
| Symptom, cause, fix | [traps.md](references/traps.md) |
| Mockup boards and approval | [mockups.md](references/mockups.md) |
| Unhappy user, "ideas?", "experiment" | [experiments.md](references/experiments.md) |
| Dump, find, in-game check, states, review checklist, 2x comparison, report | [verify.md](references/verify.md) |
| Vanilla reference images | [gallery.md](references/gallery.md) |
| Signatures and KDoc | `api/org/starficz/starui/` |
| Working code, a page per feature (`// snippet:` blocks are what the showcase shows) | `examples/showcase/*Pages.kt`, `examples/ExampleUIPlugin.kt` |

A small task reads this file, `components.md`, `reactive.md`, `traps.md`, the one section of `parts.md` or
`buttons.md` it needs, and the component's `api/` file. Images cost context: open two or three.

## Mockups in one command

```sh
python3 <skill>/scripts/mockup/build.py <your_boards.py> --out <output folder>
```

Start from a copy of `<skill>/scripts/mockup/example_board.py`. Writes `mock.html` and PNGs at 1x and 2x, prints
layout warnings (none allowed before showing). Needs Python 3; PNGs need `node` with Playwright (else open
`mock.html`). Details: [mockups.md](references/mockups.md).

## Related skills

- **`starsector-hotreload`** (ships with StarUI): the in-game loop ([verify.md](references/verify.md)).
- **`starsector-knowledge`**, if installed: game API, decompiled sources, sprites, data.
- **`patchlib`**, if installed: the hooks `inject` is called from.

If one is missing, say so once; never fill the gap from memory.

## Subagents

They do not inherit this file. Pass this folder's and `starsector-hotreload`'s absolute paths, the reference files
from the table that fit, and: signatures from `api/` only; vanilla's styles unless the user asked otherwise (pass
the request word for word); mockups (or captures) and the in-game check are not optional; no capabilities notice.
One writer per panel; reviews and capture checks can run in parallel.

Credits: design guide and mockup generator adapted from the starsector-ui-design skill ([NOTICE.md](NOTICE.md)).
