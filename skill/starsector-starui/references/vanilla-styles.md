# Vanilla styles, from the code

What vanilla's own UI code uses, counted across the game's code (0.98.5a-RC4): the readable implementation
(`com.fs.starfarer.api.impl`) and the decompiled engine (`com.fs.starfarer`). Use it to settle a style question with
what the game does, not with taste.

Contents: [How this was counted](#how-this-was-counted) · [Buttons](#buttons) · [Cut styles](#cut-styles) ·
[Section headings](#section-headings) · [Tables](#tables) · [Spacing](#spacing) · [Fonts](#fonts) ·
[Colours](#colours) · [Tooltips](#tooltips) · [How the rules use this](#how-the-rules-use-this)

## How this was counted

- **Only code something still reaches.** The game keeps old code in its jar, so a raw count mixes live screens with
  abandoned ones. A class counts when it is reachable from the launcher or from a class the game's data files name
  (`settings.json`, the CSVs, rule commands named in `rules.csv`), following imports, same-package names and
  qualified names. 299 of 3,395 classes are unreachable and were left out, among them `LoadGameDialogOld`, the old
  `MarketInfoPanel` and `CommodityDetailDialog` (replaced by `CommodityDetailDialogV2`), the old
  `WeaponGroupDialog`, `ManageVariantsDialog`, and a codex `VariantStatsPanel`. About 6% of all UI calls sit in that
  dead code.
- **Reachable is not the same as used.** A screen can still be referenced and never shown. Counts below are evidence,
  and a single use is a weak one.
- API code calls `TooltipMakerAPI` (`addButton`, `addPara`, ...); the engine builds its own screens through an
  internal button factory, counted by its `CutStyle` argument, its font and its colours (fields resolved to what they
  are assigned).
- Counts are of call sites in code, not of buttons on screen. Re-count after a game update: the StarUIFramework
  repo keeps the scripts in `skill/vanilla-crawl/`.

## Buttons

| Fact | Count | Where |
|---|---|---|
| Every button whose colours are given uses the owning faction's **base UI colour for the label and dark UI colour for the fill** | all 62 engine calls with colours; nearly all API `addButton` calls | officer panel (Assign, Dismiss, Activate), level-up dialog (Level up!, Not now, Confirm), commodity dialog (Dismiss), captain picker, the comms panel, intel |
| Buttons with no colours given take `buttonText` (170,222,255) on `buttonBgDark` (31,94,112 alpha 175): the same pair for the player faction | the factory's other overloads, most engine buttons | everywhere |
| No vanilla button is filled green, red or amber, including destructive ones (Dismiss, Scuttle, Abort, Abandon, End the agreement) | 0 | |
| The one exception: an action that spends a story point is a button in the **story colours** (`getStoryOptionColor()` label on `getStoryDarkColor()` fill) | 3 | `ContactIntel` (Develop contact above the limit, Suspend contact), `HostileActivityEventIntel` (Escalate crisis) |
| Story colour also goes on dialog options (`setOptionColor`) and highlighted text | 6 option sites, 23 text sites | `SetStoryColor`, `ShipRecoverySpecial`, `CharacterStats` |
| The API's `addButton` without a cut gives `CutStyle.ALL`, `Alignment.MID` | | `StandardTooltipV2Expandable` |
| API buttons (intel, dialogs) are **20 px** high, the full width of their column or 120 px, `opad * 2` above | 15 of 21 calls at 20 px, 14 full width | intel `Abandon`, `End the agreement`, `Accept` |
| Engine button fonts | Orbitron 20 AA 33, Orbitron 12 condensed 26, Victor 10 8, Orbitron 24 AA bold 5 | Orbitron 20 AA for a dialog's commit row, Orbitron 12 condensed for banks of small buttons, Victor 10 for tiny ones |

## Cut styles

Engine factory calls in reachable code, by the labels they carry:

| Cut | Calls | Used for | Examples |
|---|---|---|---|
| `TL_BR` | 82 | The commit and cancel row of a dialog or panel; menus | Cancel, Save, Confirm, Reset, Finish, Dismiss, Load Game, Settings, Exit, Codex |
| `ALL` | 70 | A button standing alone; small `-` / `+` buttons | Confirm, Cancel, Abort, Assign, Dismiss, View blueprints, Take all, Confirm Transaction |
| `TOP` | 47 | Tabs | Character, Fleet, Refit, Cargo, Map, Officers, Colonies, Income, Custom production, Log, Weapons, Fighters |
| `BL_TR` | 26 | A bank of actions on the thing on screen | refit: Strip, Undo, Autofit..., Weapon groups..., Restore..., Repair d-mods..., Run simulation; survey: Perform Survey, Establish colony...; Leave, Go back, Sort |
| `BOTTOM` | 17 | A button hanging under a block | Show on map, Lay in course, Planet list, Expand all, Collapse all, Add |
| `C2_MENU` | 2 | The combat command menu | Reinforcements, Standing Orders |

## Section headings

| Fact | Count |
|---|---|
| `addSectionHeading` centred (`Alignment.MID`) | 278 of 284 reachable calls; none passes a left alignment literally |
| Colours: the owning faction's base colour on its dark colour (the player's for the player's things) | nearly all calls with colours |
| Pad above a heading: `opad` (10) | 118; `pad` (3) 52; 0 8 |

## Tables

| Fact | Count |
|---|---|
| Row height **20 px** | every `beginTable` call that gives one (36) |
| Colours: base, dark, bright of the player faction (`Misc.getBasePlayerColor()` ...) or of the owning faction | all |
| Column widths are fixed pixels for value columns (70 to 150) with one name column taking the rest | e.g. "Price / unit" 100, "Demand" 70, "Location" 230, "Dist (ly)" 80 |

## Spacing

| Fact | Count |
|---|---|
| `float opad = 10f` | 390 declarations |
| `float pad = 3f` | 184 declarations |
| `float small = 5f` | 28 declarations |
| `addPara` pad argument: `opad` | 1,031 calls; `0f` 427; `pad` 191 |
| `addSpacer(-10f)`: pull the next block up by one `opad` | 17 |

Vanilla's vertical rhythm is two steps: **3 between lines that belong together, 10 between blocks**, with 5 now and
then.

## Fonts

| Paragraph font setter | Calls |
|---|---|
| `setParaSmallInsignia` (Insignia 15, the body text) | 63 |
| `setParaFontDefault` | 49 |
| `setParaInsigniaLarge` (Insignia 21) | 18 |
| `setParaOrbitronLarge`, `setParaOrbitronVeryLarge`, `setTitleOrbitronLarge` | 2 each |

Body text is Insignia; a larger Insignia for one prominent value; Orbitron for titles and button labels.

## Colours

Accessor calls in reachable code:

| Accessor | Calls | Used for |
|---|---|---|
| `Misc.getHighlightColor()` | 950 | numbers that matter, highlighted words |
| a faction's `getBaseUIColor()` | 628 | headings, button labels, frames |
| `Misc.getGrayColor()` | 354 | second lines, notes |
| `Misc.getNegativeHighlightColor()` | 342 | what is wrong or missing |
| a faction's `getDarkUIColor()` | 194 | heading bars, button fills |
| `Misc.getBasePlayerColor()` | 189 | the player's own panels |
| `Misc.getTextColor()` | 170 | body text |
| a faction's `getBrightUIColor()` | 133 | tables (third colour), selection |
| `Misc.getPositiveHighlightColor()` | 119 | gains, met requirements |
| `Misc.getStoryOptionColor()` | 119 | story points (text and dialog options) |

Engine palette (`settings.json` keys behind the engine's colour table, most used first): `buttonShortcut`
(255,210,0, hotkey letters) 397, enemy text 222, `tooltipTitleAndLightHighlightColor` (203,245,255) 159, readable gray
150, gray 143, `buttonText` 89, friend text (155,255,0) 64, `buttonBg` 37, `widgetBorderColorDark` (100,200,255) 34.

## Tooltips

| Fact | Count |
|---|---|
| `getTooltipWidth()` returns 350 | 10 |
| returns 450 | 10 |
| returns 500, 400, 375, 412 | 3, 2, 1, 1 |

Vanilla tooltips are 350 or 450 wide: 350 for a short one, 450 for one with a table or several sections.

## How the rules use this

[design-rules.md](design-rules.md), [buttons.md](buttons.md), [parts.md](parts.md) and [layout.md](layout.md)
follow this file. Where the mockup kit or an older note says otherwise (coloured button fills, left aligned headings,
24 px buttons, a 4/8/16/20 spacing scale, 360 px tooltips), this file wins.

**Vanilla is the default, not a limit.** A first pass, and every part the user has not asked to change, follows
vanilla. The user can always ask for another design: coloured button fills, left aligned headings, other spacing,
a part vanilla does not have ([parts.md](parts.md#not-vanilla)). Then build what they asked, say in one line where it
departs from vanilla, and keep the rest of the panel vanilla. A request holds for the panel or project it was made
for; write it into the project's UI notes so later passes keep it.
