---
name: starsector-starui
description: Design and build Starsector mod UI that looks like the game made it, in Kotlin with StarUI (StarUIFramework), a Compose-style DSL where every call makes a real vanilla UI object - Column, Row, Box, Text, Button, Table, LazyColumn, Section, Holo, sliders, ship displays - with reactive UIState, typed sizes, per-axis scrolling, StarUIPlugin screens and inject() into any vanilla panel. Holds the design guide (look, colour roles, parts, button placement and colour, density, the tooltip rule), a mockup generator for boards at game pixel size, every part built in StarUI, tested recipes, traps, and a verify loop with the bundled starsector-hotreload skill. Use it whenever a mod depends on staruiframework or imports org.starficz.starui, the user mentions StarUI, or designs, mocks up, builds, changes or reviews Starsector mod UI in Kotlin (panel, screen, dialog, overlay, list, table, button, tooltip, "make it look vanilla", screenshots with alignment, overlap, density or colour complaints).
---

# StarUI

How to design, mock up, build and check Starsector mod UI with StarUI, so that it reads as part of the game:
nothing overlaps, nothing is cut off, everything lines up to the pixel, and every colour means what it means in
vanilla.

StarUI is a Kotlin UI library. Its DSL reads like Jetpack Compose, but every call creates a **real Starsector UI
object** (a label, a button, a `CustomPanelAPI`) directly in the panel you call it on, and returns it. There is no
virtual tree. Layout, input, rendering and reactive updates are added to the native objects through PatchLib hooks.

## Version

This skill is **StarUI 1.0.0-alpha** (an alpha; the API can still change) for **Starsector 0.98.5a-RC4**. In the first
reply of a conversation that uses this skill, say that you are working from it and name both versions. Check the
user's install: the `version` in `mods/StarUIFramework/mod_info.json` (ask for the game folder if you do not have it;
never search the disk). When it differs from 1.0.0-alpha, the install wins: say that this skill's `api/` copy may be stale
and read the signatures from the install's jar or sources instead. Numbers marked "(engine)" were measured on
0.98a-RC8; check them with one capture before relying on them.

## The bar

The user compares your panel with the vanilla screen next to it. Three faults are never acceptable, in any state
and for any data: **overlapping text, misaligned elements, cut off text or boxes.** A panel that is plain but clean
passes. A panel that is rich but has one clipped label does not.

## Reading rules

1. Read this file to the end.
2. Before designing, read [references/design-rules.md](references/design-rules.md) and
   [references/vanilla-styles.md](references/vanilla-styles.md) in full: the rules follow what vanilla's own code
   does, and the catalog holds the counts behind them.
3. Before writing or reviewing StarUI code, read [references/components.md](references/components.md),
   [references/reactive.md](references/reactive.md) and [references/traps.md](references/traps.md) completely.
4. **The sources in `api/` define what exists.** Every component, parameter, modifier and theme field you use must
   be in them (`api/org/starficz/starui/...`, with KDoc and examples). Never write StarUI calls from memory or from
   Compose habits: names and parameters differ.
5. Game code (the API, what a vanilla panel is, which method builds a screen, sprite names) comes from the game's
   real sources, never from memory. The `starsector-knowledge` skill holds them when it is installed (`api/` first,
   then `obf/` for the decompiled engine); otherwise ask the user. Do not assume the user has a decompile of their own,
   and never use `javap` on any jar.

## Tell the user what you can do

The first time you work on UI in a conversation, end your first message about it with this list. Leave out a line
the session cannot do. Do not wait for an answer: work with the defaults.

> What I can do on this UI. Say the word for any of it:
> - **Mockup first** (on): boards at game pixel size, for your go before any code.
> - **Before and after** (on): every change to existing UI as a pair, side by side.
> - **Text list** (on): every text I wrote for the player, for your approval.
> - **Experiment** (on when you are unhappy with something and name no fix): three different solutions for one
>   element, side by side, with my pick.
> - **In game check**: I run the panel in a hot-reload game, capture every state and fix what I see.

Once per conversation, or once per project when the project keeps UI notes (write down that the user has seen it
and what they switched off). Show it again when the user asks what you can do. A subagent does not send it.

## Workflow

1. **Read first.** The project's own UI notes and its component functions if it has them (reuse them, do not
   write a second kit). Then [design-rules.md](references/design-rules.md). Look at two or three images from
   [gallery.md](references/gallery.md): the vanilla screens closest to your case.
2. **Write the content list** before any layout: every value shown, every action with the thing it changes, every
   state (empty, one entry, full, selected, blocked, in progress, done) and the worst case of every value: 40
   character names, 9 digit credits, 300 day counters, 14 skills, 200 entries, an empty list.
3. **Pick host and archetype** ([layout.md](references/layout.md)) by the vanilla screen with the same kind of
   content, and row heights by vanilla's lists ([design-rules.md](references/design-rules.md#density)). Say which
   you picked.
4. **Pick the parts** from [parts.md](references/parts.md) and the buttons from
   [buttons.md](references/buttons.md), vanilla's unless the user asked otherwise. Do not invent a widget when a
   listed part fits.
5. **Mock up and get approval before writing code** ([mockups.md](references/mockups.md)): boards from the bundled
   generator, no layout warnings, PNGs looked at, then shown; wait for the go. A change to existing UI is shown as
   before and after pairs; every invented text goes on a text board. Skip only when the user says so, or when the
   change keeps an approved layout (a label, a colour, a bug fix).
   **Boards or screenshots the user hands you** are the approved layout and content: build from them, no boards of
   your own. Read them for where they depart from vanilla (coloured button fills, left aligned headings with a count
   at the right, two-line table rows, a compact row of skill icons, a part from [parts.md](references/parts.md#not-vanilla)).
   Build those parts vanilla's way, and in your first reply list each departure in one line with what you built
   instead, offering the boards' look for any of them (rule 2). Do not copy them silently, and do not change them
   silently.
6. **Pick where it attaches** ([setup.md](references/setup.md)): a `StarUIPlugin` screen, a layer (`Layer`,
   `showLayer`), a panel the API hands you, or `inject` from a PatchLib hook.
7. **Model the state**: one `UIState` per thing the player selects or changes, kept outside the UI. Builders read
   it; handlers write it ([reactive.md](references/reactive.md)).
8. **Build** each part as [parts.md](references/parts.md) says, with [components.md](references/components.md),
   [recipes.md](references/recipes.md) and the [traps](references/traps.md). Set repeated defaults once with a
   `Theme`. Motion: [motion.md](references/motion.md).
9. **Check the layout as text** with `dumpLayout()`, then **see it in the game** with the `starsector-hotreload`
   skill (start once, rebuild the jar: StarUI panels on screen rebuild with the new code by themselves; capture every state) ([verify.md](references/verify.md)).
10. **Check and report**: the review checklist on the captures, fix, and say plainly what was not seen in game.

### Change requests on existing UI

1. **Get the current state**: a capture, or the current layout rebuilt on a board.
2. **Sort the request** ([experiments.md](references/experiments.md#when-to-run-one)): a fault is fixed; a concrete
   instruction is done as asked; a complaint without a solution, "ideas?", "experiment", or a second rejection of the
   same element starts an experiment round (three variants that differ in kind, one recommended); a note that can be
   read two ways gets both readings drawn.
3. **Show it on a board** as a before and after pair, or the experiment board, and wait for the go or the pick.
4. Build, capture before and after as pairs, write the decision into the project's UI notes.

## Hard rules

Design (the reasons are in the references):

1. **Text never overlaps and is never cut.** One line text that can be long is cut with `...` plus a tooltip
   (`Text(..., overflow = TextOverflow.Ellipsis)`); wrapped text has an exact or fill width; number columns are
   sized from the widest value they can hold.
2. **Vanilla first.** A first pass, and any pass the user has not steered, follows what vanilla's own code does
   ([vanilla-styles.md](references/vanilla-styles.md)), never taste, and only code vanilla still uses. The user can
   always ask for another design (coloured buttons, another spacing, a part vanilla does not have): then build what
   they asked, say in one line where it departs from vanilla, and keep to vanilla everywhere they did not ask for a
   change.
3. **Colours and fonts come from the game**: `VanillaColors`, `Font`, the settings colours, a faction's UI colours.
   A mod defines no colours of its own.
4. **Every button is the faction's base label on its dark fill**, a destructive one included; only an action that
   spends a story point takes the story colours. Cuts as vanilla: `TL_BR` commit and cancel row, `ALL` alone,
   `TOP` tabs, `BL_TR` a bank of actions on the thing on screen.
5. **Black interiors, no tinted tiles, no rounded corners.** Fill only for the selected row, buttons, heading bars
   and table header strips.
6. **Explaining text goes into hover tooltips** (350 px wide, 450 with a table); panel rules behind a `?` help
   icon on the title line.
7. **Icons before words.** Costs in highlight, red when short.
8. **Actions sit next to the thing they change.** The commit and cancel row bottom right, pinned. Every button on
   screen at 1600 x 900 and 1280 x 768.
9. **A blocked button stays in place**, grey, with its reason in red on a reserved line or in its tooltip.
10. **Lists scroll, keep their offset and end on a whole row.** An empty list keeps its heading and one gray line.
    Row heights as vanilla's lists of the same kind; tables 20 px.
11. **Widgets that exist in vanilla are vanilla's** (StarUI wraps most); a copy is proven at 2x against vanilla.
12. **Vanilla's spacing**: 3 between lines that belong together, 10 between blocks; section headings centred with
    10 above and below.
13. **Every confirmation is the game's own popup** (a `Dialog`: [buttons.md](references/buttons.md#confirm)).

StarUI:

14. **Never from memory.** Signatures from `api/`, game facts from the game's sources. Say what you could not
    confirm.
15. **Builders build, handlers change state.** Adding a component from a click handler throws. Read `state.value`
    inside the builder of the container that shows it; a builder never writes a state it reads.
16. **Key every list whose items can move**: `key(item.id) { ... }`, `LazyColumn(key = { it.id })`.
17. **Sizes are the `size` argument**; clipping and scrolling the container's `overflow`; anchors only inside a
    `CustomPanel`.
18. **No obfuscated names** in mod code: not in imports, casts, reflection strings or PatchLib matchers.
19. **No TooltipMakerAPI content in the DSL.** StarUI wraps the vanilla pieces it supports as components;
    `TooltipMakerAPI` belongs inside `Modifier.tooltip { }` (and the confirm popup's text) only.
20. **Kotlin against LazyLib's Kotlin runtime** (LazyLib 3.1: Kotlin 2.4.21): a Kotlin compiler 2.3 or newer, and
    `-api-version` no higher than 2.4.
21. **Seen in game, or not done.** Never write "tested" for code that only compiled.

## Where to look

| Question | File |
|---|---|
| What vanilla looks like: colour roles, typography, spacing, density, the tooltip rule, wording, icons, numbers, what vanilla never does | [references/design-rules.md](references/design-rules.md) |
| What vanilla's own code uses, counted (button colours and cuts, heading alignment, table rows, pads, fonts, tooltip widths), and where the design rules differ | [references/vanilla-styles.md](references/vanilla-styles.md) |
| Which part for this content, its size and look, and how to build it in StarUI | [references/parts.md](references/parts.md) |
| Which button kind, colour, cut, size, place, label, state; confirm; tabs; toggles | [references/buttons.md](references/buttons.md) |
| Hosts, screen sizes, archetypes, the layout contract, view state | [references/layout.md](references/layout.md) |
| Dependencies, compiling, `StarUIPlugin`, layers, dialogs and toasts (`Layer`, `Dialog`, `showToast`), panels the API hands you, `inject` | [references/setup.md](references/setup.md) |
| Containers, controls, tables, sizes, overflow, lazy lists, spacing, modifiers, anchors, theme, the window calls, the vanilla toolkit | [references/components.md](references/components.md) |
| `UIState`, rebuilds, keys, reuse, disposal, where StarUI differs from Compose | [references/reactive.md](references/reactive.md) |
| Change row, guarded button, cost button, tabs, toast content (tested) | [references/recipes.md](references/recipes.md) |
| Hover, unfold, highlight motion | [references/motion.md](references/motion.md) |
| Something went wrong: symptom, cause, fix | [references/traps.md](references/traps.md) |
| Mockup boards, the approval loop, from board to code | [references/mockups.md](references/mockups.md) |
| The user is unhappy with an element, asks for ideas, or says "experiment" | [references/experiments.md](references/experiments.md) |
| Layout dump, in-game check, states, screenshot request, review checklist, 2x comparison, report | [references/verify.md](references/verify.md) |
| Reference images: vanilla screens and what to look at in each | [references/gallery.md](references/gallery.md) |
| Exact signatures and KDoc | `api/org/starficz/starui/` |
| Working code for every feature, a page per feature (each demo between `// snippet:` markers is the code the showcase shows beside it) | `examples/showcase/` (`LayoutPages.kt`, `ContentPages.kt`, `ControlsPages.kt`, `ListPages.kt`, `StatePages.kt`, `InputPages.kt`, `WindowsPages.kt`, `VanillaPages.kt`, `HostPages.kt`, `RecipePages.kt`), `examples/ExampleUIPlugin.kt` |

A small task (one component, one fix) reads this file, `components.md`, `reactive.md`, `traps.md`, the one section
of `parts.md` or `buttons.md` it needs, and the `api/` file of the component it touches. Images cost context: open
the two or three that match the task.

## Mockups in one command

```sh
python3 <skill>/scripts/mockup/build.py <your_boards.py> --out <output folder>
```

Copy `<skill>/scripts/mockup/example_board.py` as the start. The build writes `mock.html`, one PNG per board at 1x
and 2x, and prints layout warnings; "no layout warnings" is required before a board is shown. Needs Python 3 and,
for the PNGs, `node` with Playwright; without them open `mock.html` in a browser. Details:
[mockups.md](references/mockups.md).

## Related skills

- **`starsector-hotreload`** ships with StarUI: the in-game check and iteration loop ([verify.md](references/verify.md)).
- **`starsector-knowledge`**, when installed: the game's API and decompiled sources, sprites, data.
- **`patchlib`**, when installed (from the PatchLib wiki): writing the hooks `inject` is called from.

If one is missing, say so once and work from what is there; never fill the gap from memory.

## Subagents

Subagents do not inherit this file. Pass the absolute path of this skill folder (and of `starsector-hotreload`),
name the reference files from the table above that fit the task, and tell the subagent to take signatures from
`api/` only, to follow vanilla's styles unless the user asked for another design (pass that request on word for
word), that the mockup step and the in-game check are not optional, and that it does not send the
capabilities notice (you do). One writer per panel; reviews and capture checks can run side by side.

Credits: the design guide and the mockup generator are adapted from the starsector-ui-design skill
([NOTICE.md](NOTICE.md)).
