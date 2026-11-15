# Mockups

Boards come before code (a wrong layout on a board costs minutes; in game, a build and a review round). The bundled
generator draws HTML boards at game pixel size (1 px = 1 game px at 100%) with the game's fonts, screenshots them and
checks the layout.

Contents: [Ask first](#ask-first) · [Run it](#run-it) · [Write a boards file](#write-a-boards-file) · [Helpers](#helpers) ·
[Images](#images) · [Layout warnings](#layout-warnings) · [What the boards must show](#what-the-boards-must-show) ·
[Look at your own boards](#look-at-your-own-boards) · [Approval loop](#approval-loop) ·
[From board to code](#from-board-to-code)

## Ask first

These are lines of the capabilities notice ([SKILL.md](../SKILL.md#tell-the-user-what-you-can-do)); if it was skipped,
send it with the first boards. Don't wait: use the defaults and adjust. Record answers in the project's notes.

| Preference | Default | What it means |
|---|---|---|
| Before and after | On for changes to existing UI | Each changed view as one pair, before left, after right, on one board (`k.compare`); never all befores then all afters |
| Text approval | On when a board holds text you wrote | One board listing every player-facing text added: labels, states, headings, tooltips, popup questions, taglines, empty lines (`k.textlist`) |
| Experiment rounds | On complaints, "ideas?", "experiment", second rejection | Three variants differing in kind beside the current state, one recommended ([experiments.md](experiments.md)); or on every layout change, or only when asked |

## Run it

```sh
cp <skill>/scripts/mockup/example_board.py <work folder>/boards.py
python3 <skill>/scripts/mockup/build.py <work folder>/boards.py --out <work folder>/mock
```

Writes `mock.html` (all boards), `<board id>.png` and `@2x.png`; prints `MISSING IMAGE`, layout warnings, or
`no layout warnings`.

Needs Python 3; PNGs and the layout check need `node` with Playwright (`npm i playwright`). `shot.js` uses
Playwright's Chromium if downloaded, else the installed Edge or Chrome; with none, open `mock.html` (`--no-shots`
skips). The tinted `?` icon needs Pillow. Build `example_board.py` once to see every helper (eleven boards).

## Write a boards file

Plain Python. Helpers take `x, y` from the top left (y down, like StarUI and `dumpLayout()`), sizes in game pixels,
and return HTML strings to add up.

```python
import kit as k

TITLE = 'Survey Office, mockups'
INTRO = '<p>Sample data is made up. Nothing is implemented.</p>'

W, H = 1000, 640            # the custom dialog window
CW, CH = W - 20, H - 55     # the content panel inside it


def main():
    o = k.title(CW, 'Survey Office', 'Thrinakia')
    t, y = k.tabs(0, 30, CW, ['Teams 4 / 5', 'Archive'], 0)
    o += t
    o += k.heading(0, 64, 550, 'On assignment', 'left', '2 teams')    # rows start 4 px below: 64 + 18 + 4
    inner = (k.portrait(3, 3, 44, k.sample('portraits/officer1.png'))
             + k.text(56, 7, 170, 'Reka Yamada') + k.text(56, 26, 170, 'Geologist, level 5', color=k.GRAY)
             + k.text(446, 7, 96, '18 days', color=k.HL, align='right'))
    o += k.row(0, 86, 550, 50, inner, selected=True)
    ship = (k.ship(3, 3, 44, k.sample('ships/wolf.png')) + k.text(56, 7, 150, 'ISS Long Look')
            + k.text(56, 26, 150, 'Corvus IV', color=k.GRAY))
    o += k.strip(572, 64, CW - 572, 50, ship, [('Change...', 'std', 96), ('Recall', 'std', 96)])
    y = CH - 24
    o += k.button(CW - 96, y, 96, 20, 'Dismiss', 'std', 'tlbr')
    f = k.frame(W, H, o)
    return k.board('office_main', 'Survey Office: list and card', f,
                   k.notes('Rows are 54 px, as vanilla\'s planet list.', 'Recall sits on the ship it recalls.'))


BOARDS = [main]
```

- `frame(w, h, ...)` takes the **window** size; content is `(w - 20) x (h - 55)` (what code passes to
  `showCustomDialog`). Lay out in content coordinates; `frame` draws the host's Return.
- `BOARDS`: functions returning one `k.board(...)` each; the id names the PNG.
- Positions from variables (`y += 54`). Gap constants are vanilla's: `k.ROW_GAP` 3, `k.GAP` 5, `k.SECTION` /
  `k.HEAD_GAP` 10.
- Buttons: `kind='std'` or `'story'`; green, red, amber kinds are not vanilla.
- `row`, `card`, `strip`, `tile`, `area` content is placed from the box's outer top left; the 1 px border is inside.
- Small per-block functions (`header()`, `team_row(y, t, selected)`), reused so every state has the same layout.

## Helpers

All in `scripts/mockup/kit.py` with docstrings; `example_board.py` uses each.

| Group | Helpers |
|---|---|
| Hosts | `frame(w, h, inner, confirm='Return', cancel=None)` custom dialog; `dialog(text_html, options, panel_inner, panel_w, panel_h, w, h)` interaction dialog with side panel; `popup(text_html, confirm, cancel)` the confirm popup |
| Review | `compare(before, after, names)`; `sidebyside(*cells)`; `cell(caption, html)`; `experiment(problem, now, variants, recommend, reason)` returns `(cells, recommendation line)`, refuses <2 or >4 variants or one without a cost line; `textlist(rows, title)` |
| Page | `board(id, caption, frame_html, notes)`, `notes(*items, title=None)` |
| Structure | `title(w, s, sub, help)`, `tabs(x, y, w, labels, selected)` returns `(html, y below the rule)`, `heading(x, y, w, s, align, note)`, `thead(x, y, w, cols)`, `rule`, `vrule`, `shortrule`, `counter(right, y, label, icon, value)` returns `(html, next right edge)` |
| Containers | `row(x, y, w, h, inner, selected, enabled, strip)`, `card(x, y, w, h, inner)`, `strip(x, y, w, h, inner, buttons)`, `tile(x, y, src, name, mark, level, icon, selected)`, `dashbox`, `box` |
| Card top | `namebar(x, y, w, name, fav, can_fav)`, `star`, `tagline(x, y, w, s)` |
| Text | `text(x, y, w, s, font, color, align, wrap)`; fonts `ins15`, `ins21`, `orb12`, `orb20`, `vic10`; spans `<span class="hl">30</span>` (`hl`, `pos`, `neg`, `gr`, `bs`, `br`, `st`, `tx`); `fit(s, w, font)` cuts with `...` like mod code must; `namelines(x, y, w, name, second)` name plus gray second line, wrapping to two first; `textwidth(s, font)` |
| Buttons | `button(x, y, w, h, label, kind, cut, icon, sub)` kinds `std green red amber story hot off`, cuts `all` (lone), `tlbr` (commit row), `bltr` (action bank), `top`, `bottom`; `costbutton(x, y, w, h, label, icon, amount, kind, ok)`; `bank(x, y, buttons, align=...)` items `(label, kind, width)` or with `icon, amount`; `guarded(x, y, w, h, label, kind, reason)`; `tab`; `area(x, y, w, h, label, on, enabled, strip=...)`; `check(x, y, label, on)`; `stepper(x, y, value)` |
| Data | `grid(x, y, w, rows)`, `change(x, y, w, label, old, new)`, `costrow(x, y, w, label, icon, amount, stock, ok)`, `bar(x, y, w, h, frac, color, label)`, `thinbar`, `pips`, `mark`, `checkrow` |
| Pictures | `img`, `tint`, `portrait(x, y, size, src)`, `ship(x, y, size, src)`, `stack(x, y, size, src, count)`, `morecell`, `iconrow(x, y, w, items, cw, ch, render)` returns `(html, used height)`; `marker(x, y, src)`; `lumtint(src, color)` placeholder glyph; `skill(x, y, size, src, aptitude, elite, known)` (`SkillIcon` in StarUI) |
| Overlays | `tooltip(x, y, w, h, inner)`, `scrollbar(x, y, h, frac, off)` |
| Colours and sizes | `BASE BRIGHT DARK EDGE HL TEXT GRAY POS NEG STORY LINE`, `GREEN RED AMBER`, `BARS[...]`, `APTITUDE[...]`; `ROW_GAP GAP SECTION HEAD_GAP`, `TILE_W TILE_H`; `QUEST` |

- `counter`, `costrow`: `icon=None` for no icon; story cost: `k.costrow(x, y, w, 'Story points', None, '1', '3', color=k.STORY)`.
- `iconrow`: left to right in `w`, two rows, then `+N`. 14 skill icons of 32 px, 4 px gaps: 500 px in one row, 248
  in two.
- `scrollbar`: `frac` = visible / content height, `off` = offset / content height; draw it when a list overflows, and
  make at least one board's list overflow.
- Long one-line text: `namelines` for a row's name, `text(..., k.fit(name, w))` otherwise; unfitted text triggers
  "does not fit its box" (where the code must fit too).
- `text(..., wrap=True)` wraps at `w`; place what follows by hand, as code will after measuring.
- `strip`: left content in strip coordinates, buttons at the right end; blocked button kind `'off'`, red reason in
  the content's second line.
- `marker` on a 64 px picture at `px, py`: at `px + 50, py - 4` (quest drawn 16 px in a 22 px box).
- `skill`: `elite=True` adds stars, `known=False` dims.
- `popup` is a cut-out for the states board; never Confirm/Cancel inside the panel.

Kit colours are vanilla defaults (mockups only). Fonts are converted from the game's bitmap fonts and sit on the
engine's pixel rows (a heading 5 px above its capitals, 4 below; a 24 px button 8 above): don't nudge with CSS. No
CSS effects the game cannot draw; if the kit can't draw it, the game likely can't either (or needs a custom widget):
say so on the board.

## Images

- `k.sample(...)`: bundled sample pictures: `portraits/officer1..4.png`; `ships/` wolf, hammerhead, eagle, onslaught,
  buffalo, drover; `ui/` supplies, fuel, crew, credits, more_info, check_on, check_off, quest, glyph; `skills/`
  helmsmanship, target_analysis, damage_control, field_modulation, gunnery_implants, energy_weapon_mastery,
  ordnance_expert, polarized_armor.
- `k.game('icons/cargo/supplies.png')`: the game's graphics, found via an unpacked `starsector-knowledge` next to
  this skill or `SS_GRAPHICS=<folder>`. It ships `icons/` (`cargo/`, `skills/`, `industry/`, `markets/`, `intel/`,
  `abilities/`), `hullmods/`, `ui/`, `fonts/`; no ships or portraits. List folders; never guess names.
- No story point icon: write story costs as story-coloured text.
- `k.mod('...')`: the mod's graphics, with `MOD_GRAPHICS` set.
- Missing files draw a dashed red box and print `MISSING IMAGE`: fix, or note missing art.
- Never search the disk for the game; ask.

## Layout warnings

Every text is measured at 1x:

- `text does not fit its box`: wider than its width (in game: cut or overlapping). Widen, shorten or wrap.
- `text overlaps`: two texts share pixels.
- `text outside the frame`.

All must be gone before showing. Boxes are not checked: look at the PNG.

## What the boards must show

1. **Main view with worst-case data**: longest name, largest numbers, a list that scrolls.
2. **Every state**: empty, one entry, selected, blocked with red reason, cost button short, in progress, done, and
   the confirm popup with real text for each confirming action; small states as cut-outs (`k.sidebyside`).
3. **One open tooltip**.
4. **A legend** of every marker and status icon with its tooltip text.
5. **The small screen** (1280 x 768 content) for large panels.
6. **Before and after pairs** for changes (`k.compare`, same data; before = current capture or rebuilt layout).
7. **The text board** (`k.textlist`), always when you invented flavour, lore or names.
8. **Notes per board**, 3 to 6 short lines: what is new, which rule decides, what is open, placeholders. Not what the
   picture shows.
9. **Variants** for open choices; an **experiment round** on complaints or a second rejection (`k.experiment`,
   [experiments.md](experiments.md)).
10. **Density or spacing** in doubt: both levels side by side.

Plausible made-up data in the game's style; say nothing is implemented.

## Look at your own boards

Open every PNG (`@2x` for 1 px alignment) and compare with the closest [gallery](gallery.md) screen:

- shared edges, equal gaps and row heights; 3 within groups, 10 between blocks and around headings; headings centred;
- no text within 8 px of a card border;
- no stacked buttons, coloured fills, or inline Confirm/Cancel; colours and cuts by the rules;
- number columns right aligned; no explaining sentences on the panel;
- actions on their subject's strip; bottom row pinned and inside the frame;
- nothing [vanilla never does](design-rules.md#what-vanilla-never-does).

## Approval loop

1. Show the PNGs (or HTML; publish `mock.html` where pages can be published) with one line per open decision and
   your recommendation.
2. Wait for the go; no implementing meanwhile.
3. Restate each note as a concrete change, rebuild, show only what changed.
4. Two-way notes: draw both, ask.
5. Complaints without a fix, or a second return of one element: stop adjusting, run a round
   ([experiments.md](experiments.md)).
6. Keep approved boards and text: the reference for the in-game check; code uses the approved wording unchanged.

## From board to code

Board numbers are the layout: carry them as named constants (`ROW_H = 50`, `LIST_W = 560`) into StarUI sizes and
spacing; keep the blocks as component functions (`fun UIPanelAPI.TeamRow(t: Team)`); build parts per
[parts.md](parts.md). Expect:

- fonts a pixel wider or narrower in places: fit one-line text in code even if it fit on the board;
- scroll areas fade their edges; stock buttons glow on hover;
- the board's bar is a close CSS copy, not pixel exact;
- marker outline, skill frame and star are copies: check them in game at 2x;
- a scroll area clips a border at x 0: keep the board's 2 px grid margin.
