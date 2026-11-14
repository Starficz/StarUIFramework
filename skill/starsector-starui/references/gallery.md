# Gallery

Vanilla screenshots in `assets/images/` (game 0.98a-RC8, 100% UI scale): what the player is used to. Open the two or
three that match your task; each costs context.

| Your panel is | Open |
|---|---|
| A list or roster with a detail | `vanilla_officer_picker.jpg`, `vanilla_colony_list.png` |
| Things known by face, in tiles | `vanilla_colony_screen.jpg` |
| A table, a picker, a shop | `vanilla_industry_picker.png`, `vanilla_custom_production.png`, `vanilla_hullmod_picker.png` |
| One flowing column (intel, a report) | `vanilla_intel_event.png` |
| Controls, steppers, banks of buttons | `vanilla_refit.png` |

| Image | Shows | Look at |
|---|---|---|
| [vanilla_industry_picker.png](../assets/images/vanilla_industry_picker.png) | The build menu: a table with picture, name, type, build time, cost, upkeep | 44 px rows; sortable table header; one value per cell; numbers in highlight yellow, right in their column; the selected row is the only filled row; unavailable entries in orange red; three buttons bottom right with hotkeys in yellow; credits and slot count in the footer |
| [vanilla_custom_production.png](../assets/images/vanilla_custom_production.png) | Custom production: order summary left, blueprint table right, filter banks below | 30 px rows, the densest vanilla list; filter buttons as banks of small outline toggles with counts; the pinned button stack bottom left; how much black there is |
| [vanilla_hullmod_picker.png](../assets/images/vanilla_hullmod_picker.png) | Hullmod list in the refit screen | 36 px rows with a 32 px icon; a tick column; cost column in highlight; filter banks below the list; the stat block on the right with label left, value right |
| [vanilla_officer_picker.jpg](../assets/images/vanilla_officer_picker.jpg) | Officer picker | 120 px entries, the roomy end of vanilla: portrait, name, one status sentence in gray, a bar with its label on it, skill icons in a row, buttons at the right end of the entry; title bar across the top; one button bottom right |
| [vanilla_colony_screen.jpg](../assets/images/vanilla_colony_screen.jpg) | Colony overview | Picture tiles with a heading strip each; label over value pairs in the header; commodity icons instead of words in the right column; one wide action button at the bottom |
| [vanilla_colony_list.png](../assets/images/vanilla_colony_list.png) | Command tab: colony table and a side summary | 68 px rows; tabs with `CutStyle.TOP`; icons for conditions; the summary block with label left, value right in highlight |
| [vanilla_intel_event.png](../assets/images/vanilla_intel_event.png) | Intel: an event with progress bar, factor table and tag buttons | A flowing single column; section headings as thin dark bars; a small table with header strip; one story-coloured button (Escalate crisis: a story-point action); tag buttons in faction colours at the bottom |
| [vanilla_refit.png](../assets/images/vanilla_refit.png) | Refit screen | Banks of small buttons with `BL_TR` cuts and hotkeys (vanilla's code: Strip, Undo, Autofit...); a column of ship tiles left with the selected one filled; stat rows with steppers (`-`, value, `+`) |

The mockup generator's example boards (`scripts/mockup/example_board.py`: list and detail, tiles, states, table,
editor, spacing, experiment, side panel, density, parts, text) show what a board for approval looks like; build them
once ([mockups.md](mockups.md#run-it)).

## Reading the vanilla shots

- Vanilla lists get their order from spacing, alignment and one filled row, not from boxes around everything.
- Almost every vanilla list has a table header and right aligned number columns.
- Buttons sit at the bottom or at the right end; they are few.
- Text on vanilla panels is names, states and numbers. Explanations are in tooltips and in the Codex.
- Yellow is numbers and hotkeys. Orange red is "cannot". Green is rare.
- A live capture of the vanilla screen next to your panel (`starsector-hotreload`: `hot.sh shot`) is better than
  any of these: it is the player's own build.
