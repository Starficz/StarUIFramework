# Vanilla styles, from the code

What vanilla's UI code uses, counted across 0.98.5a-RC4 (readable `com.fs.starfarer.api.impl` and the decompiled
engine). Settles style questions with what the game does.

Contents: [How this was counted](#how-this-was-counted) · [Buttons](#buttons) · [Cut styles](#cut-styles) ·
[Section headings](#section-headings) · [Tables](#tables) · [Spacing](#spacing) · [Fonts](#fonts) ·
[Colours](#colours) · [Tooltips](#tooltips) · [How the rules use this](#how-the-rules-use-this)

## How this was counted

- **Only reachable code**: from the launcher or classes the data files name (`settings.json`, CSVs, `rules.csv`
  commands), via imports and names. 299 of 3,395 classes are dead and excluded (`LoadGameDialogOld`, old
  `MarketInfoPanel`, `CommodityDetailDialog`, old `WeaponGroupDialog`, `ManageVariantsDialog`, a codex
  `VariantStatsPanel`): about 6% of UI calls.
- Reachable is not used: counts are evidence; a single use is weak.
- API code calls `TooltipMakerAPI`; the engine uses an internal button factory, counted by `CutStyle`, font and
  colours.
- Call sites, not buttons on screen. Re-count after updates with `skill/vanilla-crawl/` in the StarUILib repo.

## Buttons

| Fact | Count | Where |
|---|---|---|
| Buttons with colours use the owning faction's **base label on dark fill** | all 62 engine calls with colours; nearly all API `addButton` | officer panel (Assign, Dismiss, Activate), level-up dialog, commodity dialog, captain picker, comms, intel |
| Without colours: `buttonText` (170,222,255) on `buttonBgDark` (31,94,112 a175), the player's pair | most engine buttons | everywhere |
| Green, red or amber fills, destructive ones included (Dismiss, Scuttle, Abort, Abandon) | 0 | |
| Story-point actions in **story colours** (`getStoryOptionColor()` on `getStoryDarkColor()`) | 3 | `ContactIntel` (Develop / Suspend contact), `HostileActivityEventIntel` (Escalate crisis) |
| Story colour on dialog options (`setOptionColor`) and text | 6 options, 23 text | `SetStoryColor`, `ShipRecoverySpecial`, `CharacterStats` |
| `addButton` without a cut: `CutStyle.ALL`, `Alignment.MID` | | `StandardTooltipV2Expandable` |
| API buttons **20 px**, column width or 120, `opad * 2` above | 15 of 21 at 20; 14 full width | intel Abandon, End the agreement, Accept |
| Engine button fonts | Orbitron 20 AA 33, Orbitron 12 condensed 26, Victor 10 8, Orbitron 24 AA bold 5 | 20 AA commit rows, 12 condensed banks, Victor tiny |

## Cut styles

Engine factory calls in reachable code:

| Cut | Calls | Used for | Examples |
|---|---|---|---|
| `TL_BR` | 82 | Commit and cancel rows; menus | Cancel, Save, Confirm, Reset, Finish, Dismiss, Load Game, Settings, Exit, Codex |
| `ALL` | 70 | Lone buttons; small `-` / `+` | Confirm, Cancel, Abort, Assign, Dismiss, View blueprints, Take all, Confirm Transaction |
| `TOP` | 47 | Tabs | Character, Fleet, Refit, Cargo, Map, Officers, Colonies, Income, Custom production, Log, Weapons, Fighters |
| `BL_TR` | 26 | Action banks on the thing on screen | refit: Strip, Undo, Autofit..., Weapon groups..., Restore..., Repair d-mods..., Run simulation; survey: Perform Survey, Establish colony...; Leave, Go back, Sort |
| `BOTTOM` | 17 | A button under a block | Show on map, Lay in course, Planet list, Expand all, Collapse all, Add |
| `C2_MENU` | 2 | Combat command menu | Reinforcements, Standing Orders |

## Section headings

| Fact | Count |
|---|---|
| Centred (`Alignment.MID`) | 278 of 284; none passes left literally |
| Owning faction's base on dark | nearly all with colours |
| Pad above: `opad` (10) | 118; `pad` (3) 52; 0 8 |

## Tables

| Fact | Count |
|---|---|
| Row height **20 px** | every `beginTable` giving one (36) |
| Colours: base, dark, bright of the player or owning faction | all |
| Value columns fixed 70 to 150 px, one name column takes the rest | "Price / unit" 100, "Demand" 70, "Location" 230, "Dist (ly)" 80 |

## Spacing

| Fact | Count |
|---|---|
| `float opad = 10f` | 390 |
| `float pad = 3f` | 184 |
| `float small = 5f` | 28 |
| `addPara` pad: `opad` | 1,031; `0f` 427; `pad` 191 |
| `addSpacer(-10f)` (pull the next block up one `opad`) | 17 |

Two steps: **3 within a group, 10 between blocks**, 5 now and then.

## Fonts

| Paragraph font setter | Calls |
|---|---|
| `setParaSmallInsignia` (Insignia 15, body) | 63 |
| `setParaFontDefault` | 49 |
| `setParaInsigniaLarge` (Insignia 21) | 18 |
| `setParaOrbitronLarge`, `setParaOrbitronVeryLarge`, `setTitleOrbitronLarge` | 2 each |

Body Insignia; larger Insignia for one prominent value; Orbitron for titles and buttons.

## Colours

| Accessor | Calls | Used for |
|---|---|---|
| `Misc.getHighlightColor()` | 950 | numbers that matter |
| faction `getBaseUIColor()` | 628 | headings, button labels, frames |
| `Misc.getGrayColor()` | 354 | second lines, notes |
| `Misc.getNegativeHighlightColor()` | 342 | what is wrong or missing |
| faction `getDarkUIColor()` | 194 | heading bars, button fills |
| `Misc.getBasePlayerColor()` | 189 | the player's panels |
| `Misc.getTextColor()` | 170 | body text |
| faction `getBrightUIColor()` | 133 | tables (third colour), selection |
| `Misc.getPositiveHighlightColor()` | 119 | gains, met requirements |
| `Misc.getStoryOptionColor()` | 119 | story points |

Engine palette (`settings.json` keys, most used first): `buttonShortcut` (255,210,0, hotkeys) 397, enemy text 222,
`tooltipTitleAndLightHighlightColor` (203,245,255) 159, readable gray 150, gray 143, `buttonText` 89, friend text
(155,255,0) 64, `buttonBg` 37, `widgetBorderColorDark` (100,200,255) 34.

## Tooltips

| `getTooltipWidth()` returns | Count |
|---|---|
| 350 | 10 |
| 450 | 10 |
| 500, 400, 375, 412 | 3, 2, 1, 1 |

350 for short tooltips, 450 for a table or several sections.

## How the rules use this

[design-rules.md](design-rules.md), [buttons.md](buttons.md), [parts.md](parts.md), [layout.md](layout.md) follow
this file; it wins over the mockup kit or older notes (coloured fills, left headings, 24 px buttons, 4/8/16/20 spacing,
360 px tooltips).

**Vanilla is the default, not a limit.** Unasked parts follow vanilla; when the user asks for something else
(coloured fills, left headings, other spacing, a [non-vanilla part](parts.md#not-vanilla)), build it, say in one line
where it departs, keep the rest vanilla, and record the request in the project's UI notes.
