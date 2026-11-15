# Gallery

Vanilla screenshots in `assets/images/`: what the player is used to. Two sets:

- `vanilla_*`: whole screens from game 0.98a-RC8 at 100% UI scale.
- `rc6_*`: one part each (a tooltip, a dialog, a list, a filter bank), cropped from 0.98.5a-RC6 at 1600x900 and
  100% UI scale, so one pixel in the image is one UI unit.

Open the two or three that match your task; each costs context.

| Your panel is | Open |
|---|---|
| A list or roster with a detail | `vanilla_officer_picker.jpg`, `vanilla_colony_list.png`, `rc6_intel_list.png` + `rc6_intel_detail.png`, `rc6_faction_list.png` |
| Things known by face, in tiles | `vanilla_colony_screen.jpg`, `rc6_fleet_ship_cards.png`, `rc6_comm_directory.png` |
| A table, a picker, a shop | `vanilla_industry_picker.png`, `vanilla_custom_production.png`, `vanilla_hullmod_picker.png`, `rc6_weapon_picker.png`, `rc6_market_trade_grid.png` |
| One flowing column (intel, a report) | `vanilla_intel_event.png`, `rc6_intel_detail.png`, `rc6_faction_detail.png` |
| Controls, steppers, banks of buttons | `vanilla_refit.png`, `rc6_refit_stat_block.png`, `rc6_intel_filter_banks.png` |
| A filter or search panel | `rc6_planet_filter.png`, `rc6_intel_filter_banks.png`, `rc6_codex_filter_chips.png` |
| A form of options (checkboxes, sliders, key bindings) | `rc6_settings_toggles_sliders.png`, `rc6_settings_controls.png`, `rc6_dialog_autofit.png` |
| A dialog window | `rc6_dialog_confirm.png`, `rc6_tutorial_popup.png`, `rc6_dialog_weapon_groups.png`, `rc6_save_dialog.png` |
| A tooltip | `rc6_tooltip_plain.png`, `rc6_tooltip_titled_f1.png`, `rc6_tooltip_weapon.png`, `rc6_tooltip_modifier_list.png` |
| A side panel in an interaction dialog | `rc6_interaction_dialog.png`, `rc6_contact_panel.png`, `rc6_dialog_fleet_panel.png` |
| A summary with a running total and confirm | `rc6_trade_transaction.png`, `rc6_income_table.png` |

## Whole screens (0.98a-RC8)

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

## Parts (0.98.5a-RC6)

### Tooltips

Vanilla's tooltip frame: thin cyan side edges with short ticks at the corners, black inside, no fill strip behind the
title. The key hints ("F1 more info  F2 open Codex") sit in a small box of their own hanging under the bottom left,
keys in yellow, words in gray.

| Image | Shows | Look at |
|---|---|---|
| [rc6_tooltip_one_line.png](../assets/images/rc6_tooltip_one_line.png) | The credits counter's tooltip | The smallest tooltip: one line, frame as wide as the text plus padding, placed over the thing it explains |
| [rc6_tooltip_plain.png](../assets/images/rc6_tooltip_plain.png) | Skill points explained | No title: two paragraphs; numbers in yellow, "story point" in story green |
| [rc6_tooltip_stat_explainer.png](../assets/images/rc6_tooltip_stat_explainer.png) | What armor does (hover on a refit stat) | A long explanation as five short paragraphs, no title, numbers in yellow; about 400 px wide |
| [rc6_tooltip_titled_f1.png](../assets/images/rc6_tooltip_titled_f1.png) | Combat readiness | Title line in plain text, one sentence per paragraph, values in yellow, the "Press F1 for more info" box under it |
| [rc6_tooltip_ability.png](../assets/images/rc6_tooltip_ability.png) | An ability (Transponder) over the ability bar | Title with a gray state suffix "(on)"; four paragraphs; gray hint lines last ("Expand tooltip...", "Right-click to..."); the tooltip sits on top of the bar and leaves it visible |
| [rc6_tooltip_commodity.png](../assets/images/rc6_tooltip_commodity.png) | A cargo stack | Name, description, "Base value: 100¢ per unit" with the number yellow, a gray "Expand to see remote price data" line, the F1/F2 box |
| [rc6_tooltip_hullmod.png](../assets/images/rc6_tooltip_hullmod.png) | A hullmod in the refit | A full-width dark orange heading strip ("S-mod penalty") splitting the effect from the penalty |
| [rc6_tooltip_condition.png](../assets/images/rc6_tooltip_condition.png) | A colony condition, with the condition icons above | Title in orange (a negative condition), story text, the effect line last ("-1 stability, -20% accessibility") with the numbers coloured |
| [rc6_tooltip_modifier_list.png](../assets/images/rc6_tooltip_modifier_list.png) | Stability with its sources | Explanation first, then "Stability: 10" and one row per source: a signed value column (+ yellow, - red) then the source's name, indented |
| [rc6_tooltip_requirement.png](../assets/images/rc6_tooltip_requirement.png) | A button's tooltip saying why it is off | "Buy [B]" title, "Unavailable for purchase.", then "Req:" lines in orange red |
| [rc6_tooltip_story_option.png](../assets/images/rc6_tooltip_story_option.png) | A story-point dialog option | "story point" and "100%" in story green, "No story points available" in orange red, the count last |
| [rc6_tooltip_officer.png](../assets/images/rc6_tooltip_officer.png) | An officer portrait | Name, a level bar with its text on it, a full-width teal heading strip "Combat-related skills", "None" under it |
| [rc6_tooltip_entity_hover.png](../assets/images/rc6_tooltip_entity_hover.png) | A planet hovered on the campaign map | Centred title in the large font, gray subtitle (faction, standing), then a justified paragraph |
| [rc6_tooltip_weapon.png](../assets/images/rc6_tooltip_weapon.png) | A weapon | Teal heading strips "Primary data" / "Ancillary data"; per block an icon on the left and label-left, value-right rows (values yellow); blank lines group the rows; the damage type icon beside its rows |
| [rc6_tooltip_ship_info.png](../assets/images/rc6_tooltip_ship_info.png) | A ship for sale (the "?" on a ship card) | Big title, design type and size with coloured values, description, two bars with percentages, then two stat tables under "Logistical data" / "Combat performance" strips, then System / Mounts lines with counts in yellow; ship sprite top right |
| [rc6_tooltip_fleet_member.png](../assets/images/rc6_tooltip_fleet_member.png) | A ship icon in a dialog's fleet panel | Name and class in the large font, status in green ("Ready for deployment"), CR and hull bars with the value right of the bar, label-value rows, portrait with LEVEL top right, sprite right |
| [rc6_tooltip_deployment.png](../assets/images/rc6_tooltip_deployment.png) | The same card in the deployment dialog | The same layout reused: one ship card for every place a fleet member is hovered |

### Dialogs and windows

| Image | Shows | Look at |
|---|---|---|
| [rc6_dialog_confirm.png](../assets/images/rc6_dialog_confirm.png) | Pay for storage? | Message in the large font, the price in yellow, two buttons bottom right (Pay, Never mind); the screen behind dimmed but readable |
| [rc6_tutorial_popup.png](../assets/images/rc6_tutorial_popup.png) | A first-time hint | Double frame; a dash list; "Don't show this again" checkbox bottom left; one wide Dismiss button bottom right with its text right-aligned |
| [rc6_dialog_weapon_groups.png](../assets/images/rc6_dialog_weapon_groups.png) | Manage weapon groups | Title strip; a checkbox grid (weapon rows by group columns) with rows tinted by weapon type (ballistic brown-yellow, energy blue, missile green); a small table on the right with header strip; label-value totals; Auto-assign bottom left, Confirm [W] / Cancel [T] bottom right |
| [rc6_dialog_autofit.png](../assets/images/rc6_dialog_autofit.png) | Autofit | Two framed sections with teal strips (variant choices, current variant); a column of checkboxes; a cost summary with values right in yellow; one action left, Confirm / Cancel right with hotkeys |
| [rc6_dialog_flagship_picker.png](../assets/images/rc6_dialog_flagship_picker.png) | Pick a flagship | The smallest list dialog: title, a row of ship icons, Ok [G] / Cancel |
| [rc6_save_dialog.png](../assets/images/rc6_save_dialog.png) | Save as | Title strip; rows with portrait, name, place; the current row outlined, not filled; three equal buttons along the bottom, the disabled ones dimmed |
| [rc6_escape_menu.png](../assets/images/rc6_escape_menu.png) | The escape menu | A stack of large right-aligned buttons with a cut corner, filling the right half; a ship picture on the left half |
| [rc6_comm_directory.png](../assets/images/rc6_comm_directory.png) | Comm directory | Title strip and Dismiss in the faction's colour; two columns of people: portrait, name in the faction colour, role in gray, a relationship bar for contacts; most of the window empty |
| [rc6_deployment_dialog.png](../assets/images/rc6_deployment_dialog.png) | Battle deployment | Big empty area with an orange-red empty state ("No ships deployed"); ship icons with "DEPLOY" in yellow; a cost line; a progress bar "Deployed: 30 / 160" left, Deploy / All / Cancel right |

### Lists, panels and controls

| Image | Shows | Look at |
|---|---|---|
| [rc6_intel_list.png](../assets/images/rc6_intel_list.png) | Intel entries | Icon left; title in cyan; dash lines with key values highlighted (credits and days yellow, faction in its colour); "New!" in yellow at the bottom right; the selected entry filled teal |
| [rc6_intel_detail.png](../assets/images/rc6_intel_detail.png) | The selected intel's detail column | Title strip in the faction's colour; faction flag; paragraphs and a dash list; hostile factions listed in their own colours; one full-width button at the very bottom in the faction colour |
| [rc6_intel_filter_banks.png](../assets/images/rc6_intel_filter_banks.png) | Intel filters | Three banks of outline toggles with counts; the selected one filled yellow; faction toggles with their names in faction colours |
| [rc6_planet_filter.png](../assets/images/rc6_planet_filter.png) | Planet filter | Teal heading strips per section; a 3-column toggle grid; label + segmented choice rows (Any / 100%- / 150%- / 200%-, one filled); per resource a tri-state pair and graded icons; matches count, Store / Load / Reset at the bottom |
| [rc6_table_header_sortable.png](../assets/images/rc6_table_header_sortable.png) | A table header | Each column a framed cell with a dropdown triangle at its right; a wide name column, narrow number columns |
| [rc6_faction_list.png](../assets/images/rc6_faction_list.png) | Factions | Cards: flag left, name in the faction colour, a relationship bar with "FAVORABLE (15 / 100)" in small caps above its right end |
| [rc6_faction_detail.png](../assets/images/rc6_faction_detail.png) | One faction | Framed boxes with strips in the faction colour (illegal commodities as icons with names, allies, enemies as small faction cards); attitude in the standing's colour; long text in a box of its own |
| [rc6_income_table.png](../assets/images/rc6_income_table.png) | Income | Sortable header; expandable rows filled teal, values right; a side panel with a small map under a strip and one big yellow total centred |
| [rc6_income_footer_buttons.png](../assets/images/rc6_income_footer_buttons.png) | Expand all / Expand top / Collapse all | Three equal buttons with hotkeys, left-aligned under the table |
| [rc6_doctrine_pips.png](../assets/images/rc6_doctrine_pips.png) | Doctrine | A ship icon grid under a strip, a tiny RE-GENERATE button; then rows of a two-line label and five square pips (filled teal up to the value); Reset / Confirm stacked at the bottom, disabled |
| [rc6_doctrine_text_and_button.png](../assets/images/rc6_doctrine_text_and_button.png) | An explanation in place of a list | Four short paragraphs with names in yellow, then one button right-aligned under them |
| [rc6_refit_stat_block.png](../assets/images/rc6_refit_stat_block.png) | Refit stats | Tiny small-caps labels over large digital numbers; colour per stat (hull orange, flux green, others yellow); steppers (-, value, +) for capacitors and vents; a full bar with "95 / 95" on it |
| [rc6_weapon_picker.png](../assets/images/rc6_weapon_picker.png) | Weapon picker | Rows: icon in a yellow frame, name, role in yellow, "N available, X credits each", small-caps source and "ORDNANCE POINTS" lines, OP cost large at the right; rows tinted by source (open market brown, black market red); the mounted weapon pinned on top; filter tabs in small caps |
| [rc6_fleet_sidebar.png](../assets/images/rc6_fleet_sidebar.png) | Fleet tab sidebar | Label left, value right in yellow; full-width buttons with hotkeys; a disabled button dimmed; a gray hint sentence at the bottom |
| [rc6_fleet_ship_cards.png](../assets/images/rc6_fleet_ship_cards.png) | Fleet ship cards | Ship sprite on black, a row of dim status icons, portrait (or silhouette) with CR and hull bars beside it; an orange mark top right for low CR |
| [rc6_shipyard_cards_locked.png](../assets/images/rc6_shipyard_cards_locked.png) | Ships for sale you cannot buy | The same card with name, "Req:" lines in orange red over the sprite, a "?" and price icon; no grayed-out sprite |
| [rc6_cargo_tab_top.png](../assets/images/rc6_cargo_tab_top.png) | Cargo grid | Tabs with hotkey numbers, Sort at the right end; square cells with the quantity in large gray digits top right; a description in gray under a picture on the left |
| [rc6_market_trade_grid.png](../assets/images/rc6_market_trade_grid.png) | A market's goods | The same grid tinted brown with orange lines for the market's side |
| [rc6_market_grid_locked.png](../assets/images/rc6_market_grid_locked.png) | A military market | Cells you cannot buy from tinted red, the rest as before |
| [rc6_market_header.png](../assets/images/rc6_market_header.png) | Market header | Faction name and "Colony Info [M]" left; stat widgets with tiny labels under them (Stability bar, Access, Fleets, Defenses); condition icons right under a tiny caption |
| [rc6_market_submarkets.png](../assets/images/rc6_market_submarkets.png) | Submarket buttons | Flag + two-line name; selected filled in the faction colour; black market red with a red X; Storage far right |
| [rc6_shipyard_submarkets.png](../assets/images/rc6_shipyard_submarkets.png) | The same row in the shipyard, with Sell / Buy tabs under it | Tabs in the faction colour once a market is open |
| [rc6_trade_transaction.png](../assets/images/rc6_trade_transaction.png) | A pending trade | "Current transaction" heading in gray, line items with values right (negative in yellow), tariff and "You pay" totals in a large font in orange red, Abort / Confirm [G] (Confirm dimmed here: the cargo would be over capacity) |
| [rc6_settings_controls.png](../assets/images/rc6_settings_controls.png) | Key bindings | Centred section headings; zebra rows (label left, two key buttons right, `<none>` when unbound); a scrollbar; Defaults / Save / Cancel bottom right |
| [rc6_settings_toggles_sliders.png](../assets/images/rc6_settings_toggles_sliders.png) | Settings | One checkbox per line with its whole sentence; sliders as full bars with the value centred on them and diamond handles; a checkbox with a slider on the same line |
| [rc6_codex_categories.png](../assets/images/rc6_codex_categories.png) | Codex categories | Icon and one name per row, nothing else |
| [rc6_codex_list.png](../assets/images/rc6_codex_list.png) | A Codex list | Two-line rows (name cyan, kind gray) with a small sprite; an up row ".."; a scrollbar; back / forward / up / random buttons and "Ctrl-F to search" at the bottom |
| [rc6_codex_filter_chips.png](../assets/images/rc6_codex_filter_chips.png) | Codex filters | Two groups of chip banks with counts, the "All" chip first in each row |
| [rc6_codex_ship_entry.png](../assets/images/rc6_codex_ship_entry.png) | A Codex ship page | Title strip with a close X; two stat tables under strips in three label-value columns; System / Mounts lines; the description in a box; related entries as icon + two-line rows |
| [rc6_character_skills.png](../assets/images/rc6_character_skills.png) | Skills | Portrait, name, two big counters; a level bar; one row per aptitude with its picture, a chevron, then skill icons in tier groups on a colour band; locked skills dimmed |
| [rc6_skill_detail_pane.png](../assets/images/rc6_skill_detail_pane.png) | A hovered skill, shown in a pane | Title in orange, a quote in italics with its source right-aligned gray, "Affects:", effects in yellow, "Elite" heading, hints at the bottom left and right |

### Interaction dialog

| Image | Shows | Look at |
|---|---|---|
| [rc6_interaction_dialog.png](../assets/images/rc6_interaction_dialog.png) | Arriving at a planet | Text column on the left, a picture on the right; "You decide to..." then numbered options (numbers yellow, hotkeys in yellow brackets), the unavailable option grayed |
| [rc6_contact_panel.png](../assets/images/rc6_contact_panel.png) | Talking to a person | Portrait and faction flag side by side, a relationship bar under each, Name / Rank lines with values in the faction colour |
| [rc6_dialog_fleet_panel.png](../assets/images/rc6_dialog_fleet_panel.png) | Fleets in a dialog | "Your forces" / the other side as headings in their colours, "Held in reserve" in small gray, ship icons with officer badges and CR marks |
| [rc6_encounter_dialog.png](../assets/images/rc6_encounter_dialog.png) | A hostile fleet | The same layout with the enemy's name in red; a story-point option grayed with its cost in brackets |

### Campaign and combat screens

| Image | Shows | Look at |
|---|---|---|
| [rc6_campaign_hud_bottom.png](../assets/images/rc6_campaign_hud_bottom.png) | Bottom left of the campaign | Credits and supply rows with icons; bars with "205 / 450" on them; the ability bar with numbered slots and PREV / NEXT; the tab bar with hotkeys in yellow brackets |
| [rc6_campaign_minimap.png](../assets/images/rc6_campaign_minimap.png) | Minimap | Circle radar with the system name in a strip under it |
| [rc6_campaign_notifications.png](../assets/images/rc6_campaign_notifications.png) | New intel messages | Icon left, title cyan, dash lines with values in yellow; older ones fade to gray |
| [rc6_map_tabs_and_layers.png](../assets/images/rc6_map_tabs_and_layers.png) | Map layer toggles | A bank of equal toggles with yellow hotkey numbers in brackets |
| [rc6_map_top_tabs.png](../assets/images/rc6_map_top_tabs.png) | Map tabs | Two tabs with hotkeys at the left, the place's name centred |
| [rc6_combat_command_hud.png](../assets/images/rc6_combat_command_hud.png) | Combat, top right | Small-caps captions over digital counters with icons; Full Assault! / Full Retreat! as outline buttons with icons |
| [rc6_combat_hud_ship.png](../assets/images/rc6_combat_hud_ship.png) | Combat, bottom left | The combat HUD's own look (green on black, small caps, thin lines): not the campaign look, use it only for UI shown in battle |

The mockup generator's example boards (`scripts/mockup/example_board.py`: list and detail, tiles, states, table,
editor, spacing, experiment, side panel, density, parts, text) show what a board for approval looks like; build them
once ([mockups.md](mockups.md#run-it)).

## Reading the vanilla shots

- Vanilla lists get their order from spacing, alignment and one filled row, not from boxes around everything.
- Almost every vanilla list has a table header and right aligned number columns.
- Buttons sit at the bottom or at the right end; they are few.
- Text on vanilla panels is names, states and numbers. Explanations are in tooltips and in the Codex.
- Yellow is numbers and hotkeys. Orange red is "cannot". Green is rare (story points, "ready").
- Teal strips head sections; inside a faction's screen (market, comm directory, intel detail) the strips and
  buttons take the faction's colour instead.
- One card per kind of thing, reused everywhere: the fleet member card is the same in a dialog's fleet panel and in
  deployment.
- A live capture of the vanilla screen next to your panel (`starsector-hotreload`: `hot.sh shot`) is better than
  any of these: it is the player's own build.
