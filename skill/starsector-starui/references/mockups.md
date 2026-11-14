# Mockups

Mockups come before code. The user approves boards, then you build what was approved. A wrong layout found on a
board costs minutes; found in the game it costs a build, a test run and a review round.

The bundled generator draws boards as HTML at game pixel size with the game's fonts, takes screenshots and
checks the layout. One pixel on a board is one pixel in the game at 100% UI scale.

Contents: [Ask first](#ask-first) · [Run it](#run-it) · [Write a boards file](#write-a-boards-file) · [Helpers](#helpers) ·
[Images](#images) · [Layout warnings](#layout-warnings) · [What the boards must show](#what-the-boards-must-show) ·
[Look at your own boards](#look-at-your-own-boards) · [Approval loop](#approval-loop) ·
[From board to code](#from-board-to-code)

## Ask first

Three things differ from user to user. They are lines of the capabilities notice in
[SKILL.md](../SKILL.md#tell-the-user-what-you-can-do), which goes out with your first message of a project.
If that notice was skipped, send it with the first boards. Do not wait for the answer: build with the
defaults and adjust.

| Preference | Default | What it means |
|---|---|---|
| Before and after | On for every change to existing UI | Each changed view is one pair, before left and after right, on the same board (`k.compare`). Never all the before boards and then all the after boards. |
| Text approval | On whenever a board holds text you wrote | One extra board that lists every player facing text the boards add: labels, states, headings, tooltips, popup questions, taglines, empty lines (`k.textlist`). The user approves or rewords there. |
| Experiment rounds | On complaints, on "ideas?" or "experiment", and on a second rejection | Three variants that differ in kind next to the current state, one recommended ([experiments.md](experiments.md)). Other settings: on every layout change, or only when asked. |

Write the answer into the project's notes (its UI doc or memory) so the next session does not ask again.

## Run it

```sh
cp <skill>/scripts/mockup/example_board.py <work folder>/boards.py
python3 <skill>/scripts/mockup/build.py <work folder>/boards.py --out <work folder>/mock
```

Output in `--out`:

- `mock.html`: all boards on one page.
- `<board id>.png` and `<board id>@2x.png` per board.
- On the console: `MISSING IMAGE` lines, layout warnings, and `no layout warnings` when clean.

Needs Python 3. The PNGs and the layout check need `node` with Playwright and Chromium; without them the build
says so and you open `mock.html` in a browser (`--no-shots` skips the attempt). The tinted `?` icon needs
Pillow and is drawn untinted without it.

Build the bundled example once to see what the helpers draw: `python3 <skill>/scripts/mockup/build.py
<skill>/scripts/mockup/example_board.py --out <work folder>/example`. Its eleven boards show every helper in use.

## Write a boards file

A boards file is plain Python. Every helper takes `x, y` from the top left (y down, like StarUI's layout and
`dumpLayout()`) and sizes in game pixels, and returns an HTML string. Add the strings up.

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

- `frame(w, h, ...)` takes the **window** size. The content panel inside it is `(w - 20) x (h - 55)`, and that
  content size is what the code later passes to `showCustomDialog`. Lay out in content coordinates (`CW`,
  `CH`); the host's Return button is drawn by `frame` below the content.
- `BOARDS` lists functions that each return one `k.board(...)`. The board id becomes the PNG name.
- Compute positions with variables (`y += 54`). Hard coded coordinates drift.
  Use the kit's gap constants, which are vanilla's: `k.ROW_GAP` (3, lines and rows that belong together),
  `k.GAP` (5, small gaps), `k.SECTION` and `k.HEAD_GAP` (10, between blocks and around a heading).
- Buttons are `kind='std'` (vanilla's base on dark) or `'story'` for a story-point action; the kit's green, red and
  amber kinds are not vanilla.
- The content of `row`, `card`, `strip`, `tile` and `area` is placed from the box's outer top left corner,
  like an anchor inside a `CustomPanel`. The 1 px border lies inside the box.
- Write small functions per block (`header()`, `team_row(y, t, selected)`, `detail()`), and reuse them across
  boards so every state shows the same layout.

## Helpers

All in `scripts/mockup/kit.py`; each has a docstring. `example_board.py` uses every one.

| Group | Helpers |
|---|---|
| Hosts | `frame(w, h, inner, confirm='Return', cancel=None)` custom dialog window; `dialog(text_html, options, panel_inner, panel_w, panel_h, w, h)` stock interaction dialog with a side panel; `popup(text_html, confirm, cancel)` the game's confirmation popup |
| Review | `compare(before, after, names)` one before and after pair; `sidebyside(*cells)` variants or state cut outs in one row; `cell(caption, html)`; `experiment(problem, now, variants, recommend, reason)` the cells of an experiment round, returns `(cells, recommendation line)`; refuses fewer than two or more than four variants and a variant without a cost line; `textlist(rows, title)` the text approval board |
| Page | `board(id, caption, frame_html, notes)`, `notes(*items, title=None)` |
| Structure | `title(w, s, sub, help)`, `tabs(x, y, w, labels, selected)` returns `(html, y below the rule)`, `heading(x, y, w, s, align, note)`, `thead(x, y, w, cols)`, `rule`, `vrule`, `shortrule`, `counter(right, y, label, icon, value)` returns `(html, next right edge)` |
| Containers | `row(x, y, w, h, inner, selected, enabled, strip)`, `card(x, y, w, h, inner)`, `strip(x, y, w, h, inner, buttons)` a thing and its buttons, `tile(x, y, src, name, mark, level, icon, selected)` picture tile, `dashbox`, `box` (raw) |
| Card top | `namebar(x, y, w, name, fav, can_fav)` name field with the star, `star`, `tagline(x, y, w, s)` |
| Text | `text(x, y, w, s, font, color, align, wrap)`; fonts `ins15`, `ins21`, `orb12`, `orb20`, `vic10`; spans `<span class="hl">30</span>` with classes `hl`, `pos`, `neg`, `gr`, `bs`, `br`, `st`, `tx`; `fit(s, w, font)` shortens a plain string with `...` the way mod code must; `namelines(x, y, w, name, second)` draws a name with its gray second line, wrapping a long name to two lines first; `textwidth(s, font)` |
| Buttons | `button(x, y, w, h, label, kind, cut, icon, sub)` kinds `std green red amber story hot off`, cuts `all tlbr top bottom`; `costbutton(x, y, w, h, label, icon, amount, kind, ok)` price on the face; `bank(x, y, buttons, align=...)` a line of buttons, each `(label, kind, width)` or with `icon, amount`; `guarded(x, y, w, h, label, kind, reason)`; `tab`; `area(x, y, w, h, label, on, enabled, strip=...)`; `check(x, y, label, on)`; `stepper(x, y, value)` |
| Data | `grid(x, y, w, rows)`, `change(x, y, w, label, old, new)`, `costrow(x, y, w, label, icon, amount, stock, ok)`, `bar(x, y, w, h, frac, color, label)`, `thinbar`, `pips`, `mark`, `checkrow` |
| Pictures | `img`, `tint`, `portrait(x, y, size, src)`, `ship(x, y, size, src)`, `stack(x, y, size, src, count)`, `morecell`, `iconrow(x, y, w, items, cw, ch, render)` returns `(html, used height)`; `marker(x, y, src)` outlined status icon; `lumtint(src, color)` placeholder glyph from a game icon; `skill(x, y, size, src, aptitude, elite, known)` vanilla skill cell |
| Overlays | `tooltip(x, y, w, h, inner)`, `scrollbar(x, y, h, frac, off)` |
| Colours and sizes | `BASE BRIGHT DARK EDGE HL TEXT GRAY POS NEG STORY LINE`, tuples `GREEN RED AMBER`, `BARS[...]`, `APTITUDE[...]`; `ROW_GAP GAP SECTION HEAD_GAP`, `TILE_W TILE_H`; `QUEST` the quest (!) icon |

Notes on single helpers:

- `counter` and `costrow` take `icon=None` for a value without an icon. `costrow(..., color=k.STORY)` draws a
  story point cost: `k.costrow(x, y, w, 'Story points', None, '1', '3', color=k.STORY)`.
- `iconrow` fills cells left to right into the width `w`, at most two rows, then `+N`. Size the column from
  the icons when a full set must show: 14 skill icons of 32 px with 4 px gaps need 500 px in one row or 248 px
  in two.
- `scrollbar(x, y, h, frac, off)`: `frac` is the thumb length (visible height / content height), `off` the
  thumb start (scroll offset / content height). Draw it whenever the list on the board is longer than its
  viewport, and make at least one board's list really overflow.
- Names: use `namelines` for a row's name, or `text(x, y, w, k.fit(name, w))` for other one line texts that
  can be long. An unfitted long text raises the "does not fit its box" warning; that is the check telling you
  where the code needs fitting.
- `text(..., wrap=True)` wraps at `w`; place what follows by hand below it, as the code will after measuring.
- `strip` takes the left content in strip coordinates and a list of buttons for the right end; a blocked
  button is kind `'off'` and the red reason goes into the second text line of the content.
- `marker` hangs over a picture: for a 64 px picture at `px, py` place it at `px + 50, py - 4`. The quest (!)
  is drawn at 16 px inside the 22 px box.
- `skill` takes the icon and the aptitude name; `elite=True` adds the stars, `known=False` dims it.
- `popup` is a cut out for the states board, not part of the panel frame. Never draw Confirm and Cancel
  inside the panel.

The colours in the kit are the vanilla defaults. They are for mockups only; mod code reads them through
accessors. The fonts are converted from the game's own bitmap fonts: Orbitron is the wide technical face,
Insignia the plain narrow sans face of the body text. Text sits on the pixel rows where the engine draws it
(a heading has 5 px above its capitals and 4 below; a 24 px button 8 above), so a board shows real top and
bottom padding. Do not nudge text with CSS.

Do not add CSS effects the game cannot draw (rounded corners, shadows, tinted tiles, gradients on buttons). If
the kit cannot draw something, that is a hint the game cannot either, or that it needs a custom drawn widget:
say so on the board.

## Images

- `k.sample('portraits/officer1.png')` (1 to 4), `k.sample('ships/wolf.png')` (wolf, hammerhead, eagle,
  onslaught, buffalo, drover), `k.sample('ui/supplies.png')` (supplies, fuel, crew, credits, more_info,
  check_on, check_off, quest, glyph), `k.sample('skills/helmsmanship.png')` (helmsmanship, target_analysis,
  damage_control, field_modulation, gunnery_implants, energy_weapon_mastery, ordnance_expert,
  polarized_armor): a few bundled pictures for sample data.
- `k.game('icons/cargo/supplies.png')`: a file from the game's graphics folder. It is found when the
  `starsector-knowledge` skill (if installed) sits next to this skill and is unpacked, or when the environment variable
  `SS_GRAPHICS` points to a graphics folder (`SS_GRAPHICS=<knowledge skill>/graphics python3 build.py ...`).
  The knowledge skill ships `icons/`, `hullmods/`, `ui/` and `fonts/`, not ships or portraits. Useful folders:
  `icons/cargo/` (commodities: `supplies.png`, `fuel.png`, `heavymachinery.png`), `icons/skills/` (skill
  icons, `advanced_countermeasures.png`), `icons/industry/`, `icons/markets/` (conditions), `icons/intel/`,
  `icons/abilities/`, `hullmods/`. List the folder to find a name; do not guess file names.
- There is no story point icon in those folders: write a story point cost as text in the story colour, as
  the game's own story options do.
- `k.mod('...')`: a file from the mod's graphics folder, with `MOD_GRAPHICS` set.
- A missing file is drawn as a dashed red box with the file name and listed as `MISSING IMAGE`. Fix the path or
  use a sample; do not show a board with missing images unless the art does not exist yet, and then say so in
  the notes.
- Never search the user's disk for the game. Ask for the path.

## Layout warnings

The build measures every text on every board at 1x:

- `text does not fit its box`: a one line text is wider than the width you gave it. In the game it would be
  shortened or would overlap its neighbour. Widen the box, shorten the label, or wrap.
- `text overlaps`: two texts share pixels. Move one.
- `text outside the frame`: a text leaves the window.

All three must be gone before a board is shown. They are the same faults the user rejects in the game. The
check knows only text: boxes that overlap or misalign are found by looking at the PNG.

## What the boards must show

A set of boards for one panel has:

1. **The main view with worst case data**: the longest name, the largest numbers, a full list that scrolls.
2. **Every state**: empty list, one entry, selected entry, blocked action with its red reason, a cost button
   short on stock, in progress, done, and the confirmation popup with its real text for every action that
   confirms. Small states share one board as cut outs (`k.sidebyside`).
3. **One tooltip open**, so the user sees where the explaining text went.
4. **A legend** of every marker and status icon the panel uses, with the tooltip text of each.
5. **The small screen** (content for 1280 x 768) when the panel is large.
6. **Before and after pairs** when the work changes existing UI: one pair per changed view, side by side on
   one board (`k.compare`), the same data in both. The "before" is the current screenshot, or the current
   layout rebuilt with the kit.
7. **The text board**: every player facing text the boards add, in one list (`k.textlist`). Always when a
   board holds flavour, lore or a name you invented.
8. **Notes beside each board**: what is new, which rule decides a choice ("Recall sits on the ship it
   recalls"), what is not decided, which art is a placeholder. Three to six short lines. No restating
   of what the picture shows.
9. **Variants** when a real choice is open, and an **experiment round** when the user is unhappy with an
   element or rejects it a second time: the current state and three variants that differ in kind, the same
   data in all, each with its cost, one recommended (`k.experiment`, [experiments.md](experiments.md)).
10. **Density or spacing**: when in doubt, the same block at both levels side by side.

Sample data is made up but plausible, in the game's style of names and numbers. Say on the page that nothing
is implemented.

## Look at your own boards

Open each PNG before showing it. Check:

- shared left and right edges, equal gaps, equal row heights;
- vanilla's spacing: 3 between lines that belong together, 10 between blocks and around every section heading;
  headings centred;
- no text closer than 8 px to a card's border, on any side;
- no block of stacked buttons, no coloured button fill, no Confirm and Cancel in place of the game's popup;
- every number column right aligned;
- button colours and cuts by the rules;
- no explaining sentence left on the panel;
- every action on the strip of its subject, the bottom row pinned and inside the frame;
- nothing that vanilla never does ([design-rules.md](design-rules.md#what-vanilla-never-does)).

Compare with the closest vanilla screen in [gallery.md](gallery.md). Use the `@2x` PNG to judge
1 px alignment.

## Approval loop

1. Show the boards (the PNGs, or the HTML page) with one line per open decision and your recommendation.
   On a platform that can publish a page, publish `mock.html` with its PNGs so the user can open it.
2. Wait for the go. Implementation starts after approval, not while waiting.
3. On change notes: restate each note as a concrete change, rebuild the boards, show only what changed.
4. When a note can be read two ways, draw both and ask. Do not guess.
5. When a note is a complaint without a solution ("looks lost", "not legible"), or the same element comes
   back a second time, stop adjusting it and run an experiment round ([experiments.md](experiments.md)).
6. Keep the approved boards and the approved text list. They are the reference for the screenshot check
   after the build; the code uses the approved wording unchanged.

## From board to code

The board's numbers are the layout. Carry them over as named constants (`ROW_H = 50`, `LIST_W = 560`) into the
StarUI sizes and spacings, keep the same blocks as small component functions (`fun UIPanelAPI.TeamRow(t: Team)`),
and build each part as [parts.md](parts.md) says. Differences between the board and the game that you must
expect:

- Fonts render a pixel wider or narrower in places; fit every one line text in code even if it fits on the
  board.
- The game's scroll area fades its edges.
- Stock buttons glow on hover; the board shows the rest state.
- The bar on the board is a CSS copy of the vanilla bar, close but not pixel exact.
- The marker outline, the skill frame and the star are drawn copies; check them in game at 2x.
- A scroll area clips a border at x 0: keep the 2 px grid margin from the board.
