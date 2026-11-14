"""Example boards for a made up feature, "Survey Office": copy the patterns, not the content.

python3 build.py example_board.py --out /tmp/mock_example
"""
import kit as k

TITLE = 'Survey Office, mockups'
INTRO = '<p>Drawn at game pixel size with the game fonts. Sample data is made up. Nothing is implemented.</p>'

W, H = 1000, 640
CW, CH = W - 20, H - 55
LIST_W = 560
ROW_W = LIST_W - 10
DX = LIST_W + 12
DW = CW - DX
TOP = 64

P = [k.sample('portraits/officer%d.png' % i) for i in (1, 2, 3, 4)]
SHIPS = [k.sample('ships/%s.png' % n) for n in ('wolf', 'hammerhead', 'eagle', 'buffalo', 'drover', 'onslaught')]
SUPPLIES = k.sample('ui/supplies.png')
LOW = k.lumtint(k.sample('ui/fuel.png'), (235, 125, 20))
STUCK = k.lumtint(k.sample('ui/glyph.png'), (90, 150, 255), True)
SKILLS = [('helmsmanship', 'combat', True), ('target_analysis', 'combat', False), ('gunnery_implants', 'technology', True),
          ('ordnance_expert', 'industry', False), ('field_modulation', 'combat', False)]

TEAMS = [
    ('Reka Yamada', 'Geologist, level 5', P[0], 'Surveying', k.POS, .4, 'Corvus IV', '18 days', 'standard'),
    ('Joss Arden', 'Prospector, level 3', P[1], 'Returning', k.BASE, .9, 'Thrinakia', '3 days', 'standard'),
]
IDLE = [
    ('Ilse Varga', 'Cartographer, level 4', P[2], '1,100'),
    ('Maximilian Wolkensteiner von Hohenfels', 'Geologist, level 2', P[3], '700'),
]


def header(tab=0):
    o = k.title(CW, 'Survey Office', 'Thrinakia')
    t, _ = k.tabs(0, 30, CW, ['Teams 4 / 5', 'Charter'], tab)
    c, _ = k.counter(CW, 30, 'Credits', k.CREDITS, '5,000,000&cent;')
    return o + t + c


def team_row(y, t, selected=False, w=ROW_W):
    name, role, por, state, col, frac, where, left, bar = t
    i = (k.portrait(3, 3, 44, por) + k.namelines(56, 7, 170, name, role)
         + k.text(236, 7, 110, state, color=col) + k.thinbar(236, 32, 110, frac, bar)
         + (k.text(356, 7, 90, where, color=k.GRAY) if w >= ROW_W else '')
         + k.text(w - 104, 7, 96, left, color=k.HL, align='right') + k.text(w - 104, 26, 96, 'left', color=k.GRAY, align='right'))
    return k.row(0, y, w, 50, i, selected)


def idle_row(y, t, selected=False, w=ROW_W):
    name, role, por, salary = t
    i = (k.portrait(3, 3, 44, por) + k.namelines(56, 7, 170, name, role)
         + k.text(236, 7, 110, 'Idle', color=k.GRAY)
         + k.text(w - 104, 7, 96, salary + '&cent;', color=k.HL, align='right') + k.text(w - 104, 26, 96, 'per month', color=k.GRAY, align='right'))
    return k.row(0, y, w, 50, i, selected)


def group(y, name, note, rows, first=False):
    """a list section: 20 px above its heading (not above the first), the heading 4 px on its first row, rows 4 px apart"""
    if not first:
        y += k.HEAD_GAP - 4
    o = k.heading(0, y, ROW_W, name, 'left', note)
    y += 18 + k.ROW_GAP
    for r in rows:
        o += r(y)
        y += 54
    return o, y


def team_list(selected=0):
    o, y = group(TOP, 'On assignment', '2 teams', [lambda yy, n=n, t=t: team_row(yy, t, n == selected) for n, t in enumerate(TEAMS)], True)
    g, y = group(y, 'Idle', '2 teams', [lambda yy, t=t: idle_row(yy, t) for t in IDLE])
    return o + g + k.scrollbar(LIST_W - 3, TOP, CH - TOP, .8)


def ident(y):
    return (k.card(DX, y, 72, 72) + k.portrait(DX + 4, y + 4, 64, P[0])
            + k.grid(DX + 84, y, DW - 84, [('Speciality', 'Geology', k.TEXT), ('Level', '5'), ('Salary', '1,200&cent;'), ('State', 'Surveying', k.POS)], 18))


def skills(y, size=40):
    return ''.join(k.skill(DX + n * (size + 4), y, size, k.sample('skills/%s.png' % s), apt, elite) for n, (s, apt, elite) in enumerate(SKILLS))


def ship_strip(y, reason=None):
    i = (k.ship(3, 3, 44, SHIPS[0]) + k.text(56, 7, 150, 'ISS Long Look')
         + k.text(56, 26, 150, reason or 'Corvus IV', color=k.NEG if reason else k.GRAY))
    return k.strip(DX, y, DW, 50, i, [('Change...', 'off' if reason else 'std', 96), ('Recall', 'std', 96)])


def ask_strip(y, can_pay=True, w=DW, x=DX):
    i = (k.img(10, 10, 20, 20, k.QUEST) + k.text(40, 12, 120, 'Asks for supplies')
         + k.text(166, 12, 96, '<span class="hl">12</span> days left', color=k.GRAY))
    return k.card(x, y, w, 40, i + k.costbutton(w - 8 - 116, 8, 116, 24, 'Send', SUPPLIES, '40', 'std', can_pay))


def bottom_row():
    """rare and destructive actions: pinned behind a rule, neutral left, red right"""
    y = CH - 24
    return k.rule(DX, y - 12, DW) + k.button(DX, y, 104, 24, 'Service log', 'std', 'tlbr') + k.button(DX + DW - 96, y, 96, 24, 'Dismiss', 'std', 'tlbr')


def detail(fav=None):
    o = k.vrule(LIST_W + 5, TOP, CH - TOP)
    y = TOP
    o += k.namebar(DX, y, DW, 'Reka Yamada', fav)
    y += 26 + k.GAP
    o += k.tagline(DX, y, DW, 'Reka is cataloguing ore veins and has opinions about the coffee.')
    y += 17 + k.SECTION
    o += ident(y)
    y += 72 + k.SECTION
    o += skills(y)
    y += 40 + k.HEAD_GAP
    o += k.heading(DX, y, DW, 'Assignment', 'left', 'Day 12 of 30')
    y += 18 + k.ROW_GAP
    o += ship_strip(y)
    y += 50 + k.GAP
    o += k.bar(DX, y, DW, 20, .4, 'standard', '18 days left')
    y += 20 + k.SECTION
    o += ask_strip(y)
    return o + bottom_row()


def list_detail():
    f = k.frame(W, H, header() + team_list() + detail())
    n = k.notes(
        'A list on the left, the card of the selected entry on the right. Lists and tiles are equal choices: rows when the player compares values, tiles when the things have a face (tiles board).',
        'Every action sits next to the thing it changes: the name is a text field (no Rename), the ship strip holds Change and Recall, the request strip holds Send. There is no button block.',
        'A heading sits 4 px on its own content and 20 px below whatever is above it, so it caps its block and never floats between two. Blocks without a heading are 16 px apart, rows of one list 4 px.',
        'Send carries its cost on the button: icon and amount. Coloured buttons are 96 to 160 px wide, never the full column.',
        'Rare and destructive actions sit in the bottom row behind a rule: neutral left, red right. Dismiss opens the game\'s confirmation popup (states board).',
        'Skill icons are drawn the vanilla way: black cell, aptitude coloured frame with cut corners, stars on the lower edge for elite.',
        'The one flavour line is the tagline under the name. What a control does is its tooltip (states board).',
        title='Teams tab, one team selected')
    return k.board('example_list_detail', 'Survey Office, Teams tab', f, n)


def level(n):
    return k.pips(17, 92, 5, n)


CREW = [('Reka Yamada', P[0], 5, SHIPS[0], k.QUEST), ('Joss Arden', P[1], 3, SHIPS[1], None), ('Ilse Varga', P[2], 4, SHIPS[2], LOW),
        ('Maximilian Wolkensteiner', P[3], 2, None, None), ('Dagny Holm', P[1], 1, SHIPS[4], STUCK), ('Bram Tolle', P[0], 3, SHIPS[3], None),
        ('Vera Kask', P[2], 2, SHIPS[5], None), ('Sun Achterberg', P[3], 4, SHIPS[1], None)]


def tile_group(y, name, note, crew, selected=None, first=False):
    if not first:
        y += k.HEAD_GAP - 6
    o = k.heading(0, y, ROW_W, name, 'left', note)
    y += 18 + k.ROW_GAP
    for n, (nm, por, lv, ship, mark) in enumerate(crew):
        # 2 px left margin: a tile at x 0 loses its left border to the scroll area's clip
        o += k.tile(2 + (n % 6) * (k.TILE_W + 6), y + (n // 6) * (k.TILE_H + 6), por, nm, mark, level(lv), ship, nm == selected)
    return o, y + -(-len(crew) // 6) * (k.TILE_H + 6)


def tiles():
    o, y = tile_group(TOP, 'Lead teams', '2 / 3', CREW[:2], 'Reka Yamada', True)
    g, y = tile_group(y, 'Teams', '6', CREW[2:])
    f = k.frame(W, H, header() + o + g + k.scrollbar(LIST_W - 3, TOP, CH - TOP, .9) + detail(True))
    n = k.notes(
        'The same panel with tiles. Use tiles when each entry is a person, a pet or a ship the player knows by face and the list holds few values. Keep rows when the player compares numbers down a column.',
        'A tile shows only what changes what the player does now: picture, name, one level line, where it is. Everything else is on the card.',
        'One marker per tile, the most urgent first. It is the icon itself with a black outline of its own shape, the way the game draws an installed item on an industry picture. No box behind it. It hangs over the picture\'s corner.',
        'A request uses the game\'s own (!) marker. The orange and blue glyphs are placeholders made from game icons; the mod needs its own.',
        'The star on the name bar marks a lead team. It is a toggle on the thing it belongs to, not a button in a block.',
        'The grid starts 2 px in, so the first column keeps its left border inside the scroll area.',
        title='Tiles instead of rows')
    return k.board('example_tiles', 'Survey Office with tiles', f, n)


def small(inner, w=460, h=230):
    return k.frame(w, h, inner, confirm=None)


def states():
    w = 440
    empty = k.cell('Empty list', small(k.heading(0, 0, w, 'Idle', 'left', '0') + k.text(0, 24, w, 'No idle team.', color=k.GRAY), h=70))

    global DX, DW
    keep = DX, DW
    DX, DW = 0, w
    try:
        blocked = k.cell('Blocked action in a strip', small(ship_strip(0, 'No other ship is free.'), h=70))
        short = k.cell('Cost button, short on stock', small(
            ask_strip(0, False, w, 0)
            + k.tooltip(w - 300, 46, 300, 96, k.text(6, 8, 288, 'Send supplies', color=k.HL)
                        + k.text(6, 35, 288, 'The team stays out for its full term.')
                        + k.costrow(6, 62, 288, 'Supplies', SUPPLIES, '40', '25', False)), h=162))
        stars = k.cell('Name bar: lead team, not set, no slot free', small(
            k.namebar(0, 0, w, 'Reka Yamada', True) + k.namebar(0, 34, w, 'Joss Arden', False) + k.namebar(0, 68, w, 'Ilse Varga', False, False), h=114))
    finally:
        DX, DW = keep

    confirm = k.cell('Dismiss: the game\'s confirmation popup',
                     k.popup('Dismiss <span class="hl">Ilse Varga</span>?<br><br>She leaves the office for good.'))

    tip = k.cell('What a button does is its tooltip', small(
        k.costbutton(0, 0, 150, 24, 'Accelerate', k.sample('ui/fuel.png'), '60', 'std')
        + k.tooltip(0, 30, 360, 74, k.text(6, 8, 348, 'Accelerate', color=k.HL)
                    + k.text(6, 35, 348, 'Halves the days left. The fuel is used up at once.', wrap=True)), h=130))

    marks = (k.marker(4, 4, k.QUEST) + k.text(36, 7, 180, 'Asks for something')
             + k.marker(4, 36, LOW) + k.text(36, 39, 180, 'Low on supplies')
             + k.marker(4, 68, STUCK) + k.text(36, 71, 180, 'Stranded')
             + k.skill(250, 0, 40, k.sample('skills/helmsmanship.png'), 'combat', True)
             + k.skill(294, 0, 40, k.sample('skills/gunnery_implants.png'), 'technology')
             + k.skill(338, 0, 40, k.sample('skills/ordnance_expert.png'), 'industry', False, False)
             + k.skill(250, 48, 56, k.sample('skills/target_analysis.png'), 'combat', True)
             + k.skill(312, 48, 56, k.sample('skills/energy_weapon_mastery.png'), 'technology'))
    legend = k.cell('Markers; skill icons at 40 and 56 px: elite, known, not learned', small(marks, h=130))

    f = k.sidebyside(empty, blocked, short, confirm, stars, tip, legend, width=980)
    n = k.notes(
        'Empty list: the heading stays, one grey line says so.',
        'Blocked action: the button greys out in place and the red reason takes the second line of its strip. Nothing moves.',
        'Short on stock: the cost button greys out, its amount turns red, and the tooltip shows the cost against the stock.',
        'Every confirmation is the game\'s popup: text, Confirm, Cancel. A panel never arms Confirm and Cancel in place.',
        'A star that cannot be set is a grey outline; its tooltip says why.',
        'Explaining text is the tooltip of the control it explains, never a label in the panel.',
        title='States every panel needs')
    return k.board('example_states', 'States: empty, blocked, short, confirm, tooltip, markers', f, n)


def req_card(y, name, note, note_col, line, frac, bar, label, ready):
    w = 590
    o = k.heading(0, y, w, name, 'left') + k.text(w - 128, y + 1, 120, note, 'orb12', note_col, 'right')
    o += k.text(8, y + 26, w - 16, line, color=k.TEXT)
    o += k.bar(8, y + 52, 300, 20, frac, bar, label)
    o += k.bank(w - 8, y + 50, [('Donate...', 'std', 96), ('Redeem', 'std' if ready else 'off', 96)], align='right')
    return k.card(0, y, w, 84) + o


def sidepanel():
    p = k.title(590, 'Requisitions')
    p += req_card(34, 'Fuel convoy', 'Ready', k.POS, 'A tanker run to the outer colonies.', 1, 'ready', '500 / 500', True)
    p += req_card(138, 'Survey charts', '84 %', k.BASE, 'Charts of three systems on the fringe.', .84, 'standard', '420 / 500', False)
    p += req_card(242, 'Relief shipment', '10 %', k.BASE, 'Food and medicine for a blockaded port.', .1, 'standard', '50 / 500', False)
    prose = ('The quartermaster looks up from a wall of manifests.<br><br>'
             '"Three requisitions are open. Deliver what you can spare."')
    d = k.dialog(prose, [('Leave', None)], p)
    n = k.notes(
        'The stock interaction dialog stays: prose and options on the left, the custom panel on the right.',
        'Use it when the feature is part of a conversation, or needs only a few controls. No window frame, no Return button: the dialog options close it.',
        'Each card starts with a heading strip, so the cards are 20 px apart: a left heading with the state at its right end, one line of text, a bar and the two actions.',
        title='Default dialog with a side panel')
    return k.board('example_sidepanel', 'Requisitions, dialog with side panel', d, n)


def column(above, below, block):
    """the same column with different gaps around its headings"""
    w = 420
    y = 0
    o = k.heading(0, y, w, 'On assignment', 'left', '1 team')
    y += 18 + below
    o += team_row(y, TEAMS[0], False, w)
    y += 50 + above
    o += k.heading(0, y, w, 'Assignment', 'left')
    y += 18 + below
    o += k.grid(0, y, w, [('Target', 'Corvus IV', k.TEXT), ('Distance', '6 light years')], 18)
    y += 36 + block
    o += k.bar(0, y, w, 20, .4, 'standard', '18 days left')
    y += 20 + above
    o += k.heading(0, y, w, 'Idle', 'left', '1 team')
    y += 18 + below
    o += idle_row(y, IDLE[0], False, w)
    return small(o, w + 20, y + 50 + 20)


def spacing():
    f = k.sidebyside(
        k.cell('Before, tight: 6 px above a heading, 4 below', column(6, 4, 6)),
        k.cell('After, grouped: %d px above, %d below' % (k.HEAD_GAP, k.ROW_GAP), column(k.HEAD_GAP, k.ROW_GAP, k.GAP)),
        k.cell('Wrong, floating: 14 px above and below', column(14, 14, k.GAP)))
    n = k.notes(
        'Head to head: the same content, only the gaps differ. A change to existing UI is shown like this, each before next to its after.',
        'A heading belongs to the block under it. It sits 4 px on its first row, like a table head, and the space goes above it.',
        'Equal space above and below turns the heading into a separator between two blocks: it labels neither. Never do this.',
        'The first heading of a column has nothing above it. Inside a block the gaps stay small: 4 px between rows, 3 px between lines, 8 px between a grid and its bar.',
        title='Spacing around headings')
    return k.board('example_spacing', 'Spacing around headings', f, n)


def density():
    dense = k.heading(0, 0, 550, 'Dense, 50 px rows (two text lines, one value per cell)', 'left')
    y = 22
    for t in IDLE:
        dense += idle_row(y, t)
        y += 54

    roomy = k.heading(0, 0, 550, 'Roomy, 88 px entries (one statement per line, 80 px portrait)', 'left')
    y = 24
    for name, role, por, salary in IDLE:
        i = (k.portrait(4, 4, 80, por) + k.text(96, 10, 300, k.fit(name, 300, 'ins21'), 'ins21', k.TEXT) + k.text(96, 38, 300, role, color=k.GRAY)
             + k.text(96, 60, 300, 'Idle', color=k.GRAY)
             + k.text(400, 12, 140, salary + '&cent;', color=k.HL, align='right') + k.text(400, 32, 140, 'per month', color=k.GRAY, align='right'))
        roomy += k.row(0, y, 550, 88, i)
        y += 98

    f = k.sidebyside(small(dense, 570, 190), small(roomy, 570, 272))
    n = k.notes(
        'Dense is the baseline of the reference panels: about ten rows per screen.',
        'Roomy follows the vanilla officer picker: fewer, larger entries, 10 px between them, bigger pictures.',
        'Pick one level per panel and say which in the mockup.',
        title='Density levels')
    return k.board('example_density', 'The same list at both density levels', f, n)


FLEET = [(3, 'ISS Long Look', 'Wolf class frigate', SHIPS[0], '120', '14', 0, '9,000'),
         (2, 'ISS Ember Road', 'Hammerhead class destroyer', SHIPS[1], '300', '18', 1, '16,000'),
         (4, 'ISS Korolev Dreaming of Distant Shores', 'Eagle class cruiser', SHIPS[2], '450', '21', 0, '38,000'),
         (1, 'ISS Pack Mule', 'Buffalo class freighter', SHIPS[3], '900', '24', 2, '15,000'),
         (2, 'ISS Open Hand', 'Drover class carrier', SHIPS[4], '250', '19', 0, '21,000'),
         (5, 'ISS Slow Thunder', 'Onslaught class battleship', SHIPS[5], '1,200', '30', 0, '120,000')]
TW = 620
COLS = [('Grade', 0, 48, 'center'), ('Ship', 56, 240, 'left'), ('Cargo', 318, 60, 'right'), ('Days', 388, 50, 'right'),
        ('Hire', 452, 72, 'center'), ('Cost each', 530, 72, 'right')]


def charter_row(y, f):
    grade, name, cls, src, cargo, days, n, cost = f
    c = COLS
    i = (k.text(c[0][1], 11, c[0][2], str(grade), color=k.HL, align='center')
         + k.ship(c[1][1], 3, 34, src, framed=False) + k.namelines(c[1][1] + 42, 3, c[1][2] - 42, name, cls, pitch=17)
         + k.text(c[2][1], 11, c[2][2], cargo, align='right') + k.text(c[3][1], 11, c[3][2], days, align='right')
         + k.stepper(c[4][1], 9, n) + k.text(c[5][1], 11, c[5][2], cost + '&cent;', color=k.HL, align='right'))
    return k.row(0, y, TW - 10, 40, i)


def table():
    sx, sw = TW + 12, CW - TW - 12
    o = header(1) + k.thead(0, TOP, TW - 10, COLS)
    y = TOP + 18 + k.ROW_GAP
    for f in FLEET:
        o += charter_row(y, f)
        y += 40 + k.ROW_GAP
    o += k.scrollbar(TW - 3, TOP, CH - TOP, .7) + k.vrule(TW + 5, TOP, CH - TOP)

    y = TOP
    o += k.heading(sx, y, sw, 'Charter', 'left', '3 ships')
    y += 18 + k.ROW_GAP
    o += k.grid(sx, y, sw, [('Travel time', '24 days'), ('Upkeep', '2,400&cent; per month')], 18)
    y += 36 + k.GAP
    o += k.bar(sx, y, sw, 20, .3, 'cargo', '630 / 2,100')
    y += 20 + k.HEAD_GAP
    o += k.heading(sx, y, sw, 'Cost', 'left')
    y += 18 + k.ROW_GAP
    o += k.costrow(sx, y, sw, 'Fee', k.CREDITS, '46,000&cent;', '5,000,000&cent;')
    o += k.costrow(sx, y + 22, sw, 'Supplies', SUPPLIES, '120', '310')
    by = CH - 24
    o += k.rule(sx, by - 12, sw) + k.button(sx + sw - 120, by, 120, 24, 'Charter', 'std', 'tlbr')
    n = k.notes(
        'A table with a summary: use it when the player sets an amount per entry and pays once. Rows stay rows here; tiles could not carry five values.',
        'The table head caps its rows (4 px). The key number, the grade, is the first column, left of the name.',
        'Two resources, so the cost is two cost rows with the stock in brackets, not a cost button.',
        'The one commit sits in the bottom row at the right end, 120 px wide. It is the only green on the panel.',
        'The long ship name wraps to two lines before it is shortened.',
        title='Table with a summary')
    return k.board('example_table', 'Charter tab: table with a summary', k.frame(W, H, o), n)


PLANS = [('Corvus sweep', 'Orbital scan, monthly'), ('Fringe run', 'Ground team'), ('Deep core, Thrinakia', 'Deep core')]


def pick_strip(x, y, w, label, name, pic):
    i = pic + k.text(46, 3, 150, label, color=k.GRAY) + k.text(46, 20, 150, name)
    return k.strip(x, y, w, 40, i, [('Change...', 'std', 88)])


def editor():
    lw = 320
    fx, fw = lw + 22, CW - lw - 22
    half = (fw - 8) // 2
    o = k.title(CW, 'Survey plans', 'Thrinakia')
    y = 30
    o += k.heading(0, y, lw, 'Saved plans', 'left', '3')
    y += 18 + k.ROW_GAP
    for n, (nm, second) in enumerate(PLANS):
        o += k.row(0, y, lw, 44, k.text(8, 5, lw - 16, nm) + k.text(8, 23, lw - 16, second, color=k.GRAY), n == 0)
        y += 44 + k.ROW_GAP
    o += k.button(0, y + k.GAP, 96, 24, 'New plan', 'std') + k.vrule(lw + 11, 30, CH - 30)

    y = 30
    o += k.namebar(fx, y, fw, 'Corvus sweep')
    y += 26 + k.SECTION
    o += (pick_strip(fx, y, half, 'Team', 'Reka Yamada', k.portrait(3, 3, 34, P[0]))
          + pick_strip(fx + half + 8, y, half, 'Ship', 'ISS Pack Mule', k.ship(3, 3, 34, SHIPS[3])))
    y += 40 + k.HEAD_GAP
    o += k.heading(fx, y, fw, 'Method', 'left')
    y += 18 + k.ROW_GAP
    third = (fw - 10) // 3
    for n, lab in enumerate(['Orbital scan', 'Ground team', 'Deep core']):
        o += k.area(fx + n * (third + 5), y, third, 22, lab, n == 0)
    y += 22 + k.HEAD_GAP
    o += k.heading(fx, y, fw, 'Options', 'left')
    y += 18 + k.ROW_GAP
    o += k.check(fx, y, 'Repeat monthly', True, 150) + k.check(fx + 170, y, 'Recall when attacked', False, 190)
    y += 20 + k.HEAD_GAP
    o += k.heading(fx, y, fw, 'Each run', 'left')
    y += 18 + k.ROW_GAP
    o += k.grid(fx, y, fw, [('Duration', '30 days'), ('Fee', '12,000&cent;'), ('Supplies', '40')], 18)
    by = CH - 24
    o += k.rule(fx, by - 12, fw) + k.button(fx, by, 88, 24, 'Delete', 'std', 'tlbr')
    o += k.bank(fx + fw, by, [('Cancel', 'std', 88), ('Save', 'std', 88)], align='right')
    n = k.notes(
        'An editor: saved things left, the form right. The plan\'s name is the text field on top.',
        'Each choice uses the part made for it: strips with Change for the team and the ship, a toggle bank for the one of three methods, checkboxes for options. Each toggle\'s tooltip says what the method does.',
        'Every form section has its heading on top of it and 20 px above the heading.',
        'The editor commits as a whole: Cancel and the green Save at the right end of the bottom row, the red Delete at the other end. Delete opens the confirmation popup.',
        title='Editor')
    return k.board('example_editor', 'Survey plans: editor', k.frame(W, H, o, confirm='Return'), n)


def cap(x, y, s):
    return k.text(x, y, 310, s, 'vic10', k.GRAY)


def parts():
    w = 310
    a, b, c = 0, 335, 670
    o = k.title(CW, 'Parts sheet', 'every kit helper')

    y = 30
    o += cap(a, y, 'heading: centred, left, left with note; thead')
    o += k.heading(a, y + 14, w, 'Section heading') + k.heading(a, y + 36, w, 'Left heading', 'left')
    o += k.heading(a, y + 58, w, 'With note', 'left', '3 of 5')
    o += k.thead(a, y + 80, w, [('Item', 8, 150, 'left'), ('Qty', 170, 50, 'right'), ('Cost', 230, 72, 'right')])
    y = 140
    o += cap(a, y, 'tabs (cut top)')
    t, _ = k.tabs(a, y + 14, w, ['Selected', 'Other'], 0, 120)
    o += t
    y = 190
    o += cap(a, y, 'button kinds: std, green, red, amber, story, hot, off')
    kinds = [('Standard', 'std'), ('Confirm', 'std'), ('Remove', 'std'), ('Recall', 'std'), ('Story point', 'story'), ('Selected', 'hot'), ('Disabled', 'off')]
    for i, (lab, kind) in enumerate(kinds):
        o += k.button(a + (i % 3) * 104, y + 14 + (i // 3) * 30, 98, 24, lab, kind)
    y = 300
    o += cap(a, y, 'bank: tlbr cuts, right aligned; single button: cut all')
    o += k.button(a, y + 14, 96, 24, 'Single', 'std') + k.bank(a + w, y + 14, [('Strip', 'std', 60), ('Save', 'std', 60), ('Load', 'std', 60)], align='right')
    y = 350
    o += cap(a, y, 'costbutton: icon and amount on the button; short on stock')
    o += k.costbutton(a, y + 14, 150, 24, 'Accelerate', k.sample('ui/fuel.png'), '60', 'std') + k.costbutton(a + 160, y + 14, 150, 24, 'Send', SUPPLIES, '40', 'std', False)
    y = 400
    o += cap(a, y, 'guarded: blocked button and its red reason')
    o += k.guarded(a, y + 14, 150, 24, 'Build', 'std', 'Needs a spaceport.')
    y = 470
    o += cap(a, y, 'stepper; check')
    o += k.stepper(a, y + 14, 3) + k.check(a + 90, y + 15, 'Auto-recall', True, 110) + k.check(a + 204, y + 15, 'Join fights', False, 106)
    y = 520
    o += cap(a, y, 'marker: outlined icon, no box; skill: vanilla frame, elite stars')
    o += k.marker(a + 2, y + 18, k.QUEST) + k.marker(a + 34, y + 18, LOW) + k.marker(a + 66, y + 18, STUCK)
    o += k.skill(a + 110, y + 14, 40, k.sample('skills/helmsmanship.png'), 'combat', True) + k.skill(a + 154, y + 14, 40, k.sample('skills/gunnery_implants.png'), 'technology')
    o += k.skill(a + 198, y + 14, 40, k.sample('skills/ordnance_expert.png'), 'industry') + k.skill(a + 242, y + 14, 40, k.sample('skills/field_modulation.png'), 'combat', False, False)

    y = 30
    o += cap(b, y, 'area toggle: off, on, disabled; strip; one of a set')
    o += k.area(b, y + 14, 100, 22, 'Stay') + k.area(b + 105, y + 14, 100, 22, 'Follow', True) + k.area(b + 210, y + 14, 100, 22, 'Hide', False, False)
    o += k.area(b, y + 42, 152, 22, 'Escort', strip=k.rgb(k.AMBER)) + k.area(b + 158, y + 42, 152, 22, 'Intercept', strip=k.rgb(k.RED))
    y = 110
    o += cap(b, y, 'row: selectable list row, selected is the only fill')
    o += k.row(b, y + 14, w, 50, k.portrait(3, 3, 44, P[0]) + k.text(56, 7, 150, 'Reka Yamada') + k.text(56, 26, 150, 'Level 5', color=k.GRAY)
               + k.text(210, 7, 92, '2,500&cent;', color=k.HL, align='right') + k.text(210, 26, 92, 'per month', color=k.GRAY, align='right'), True)
    o += k.row(b, y + 68, w, 50, k.portrait(3, 3, 44, P[1]) + k.text(56, 7, 150, 'Joss Arden') + k.text(56, 26, 150, 'Level 3', color=k.GRAY)
               + k.text(210, 7, 92, '1,700&cent;', color=k.HL, align='right') + k.text(210, 26, 92, 'per month', color=k.GRAY, align='right'))
    y = 250
    o += cap(b, y, 'bar: standard, ready, warning, overflow, cargo, fuel, crew')
    bars = [(.4, 'standard', 'Day 12 of 30'), (1, 'ready', 'Ready'), (.6, 'warning', 'Paused'), (1, 'overflow', '400 / 310'),
            (.7, 'cargo', '220 / 310'), (.5, 'fuel', '100 / 200'), (.9, 'crew', '320 / 360')]
    for i, (f, col, lab) in enumerate(bars):
        o += k.bar(b, y + 14 + i * 24, w, 20, f, col, lab)
    y = 440
    o += cap(b, y, 'thinbar (list rows); pips; marks; checkrow')
    o += k.thinbar(b, y + 18, 120, .45) + k.pips(b + 140, y + 15, 5, 3) + k.mark(b + 220, y + 13, True) + k.mark(b + 240, y + 13, False)
    o += k.checkrow(b, y + 34, w, True, 'At least one ship stays with you') + k.checkrow(b, y + 54, w, False, 'Cargo within capacity: over cargo')
    y = 520
    o += cap(b, y, 'strip: one thing and the buttons that change it')
    o += k.strip(b, y + 14, w, 40, k.img(10, 10, 20, 20, k.QUEST) + k.text(40, 12, 150, 'Asks for a new drill'), [('Buy', 'std', 80)])

    y = 30
    o += cap(c, y, 'namebar with star; tagline')
    o += k.namebar(c, y + 14, w, 'Reka Yamada', True) + k.tagline(c, y + 46, w, 'Has opinions about the coffee.')
    y = 104
    o += cap(c, y, 'grid: label left, value right; change')
    o += k.grid(c, y + 14, w, [('Level', '5'), ('Personality', 'Cautious', k.TEXT)], 18)
    o += k.change(c, y + 50, w, 'Days left', '48', '24')
    y = 180
    o += cap(c, y, 'costrow: icon, amount, stock in brackets; red when short')
    o += k.costrow(c, y + 14, w, 'Fee', k.CREDITS, '30,000&cent;', '5,000,000&cent;')
    o += k.costrow(c, y + 38, w, 'Supplies', SUPPLIES, '400', '310', ok=False)
    y = 250
    o += cap(c, y, 'portrait; ship; stack with count; +N cell; empty slot')
    o += k.portrait(c, y + 14, 48, P[2]) + k.ship(c + 54, y + 14, 48, SHIPS[2])
    o += k.stack(c + 108, y + 14, 48, k.sample('ui/crew.png'), 12) + k.morecell(c + 162, y + 14, 48, 48, 7) + k.dashbox(c + 216, y + 14, 48, 48)
    y = 324
    o += cap(c, y, 'iconrow: fixed cells, two rows, then +N')
    r, _ = k.iconrow(c, y + 14, w, SHIPS * 4, 36, 36, lambda x, yy, s: k.ship(x, yy, 36, s), 4, 2)
    o += r
    y = 424
    o += cap(c, y, 'card: 1 px border, no fill, text 8 px in; tooltip')
    o += k.card(c, y + 14, w, 50, k.text(8, 8, 290, 'A card holds one item.') + k.text(8, 26, 290, 'Never tint it.', color=k.GRAY))
    o += k.tooltip(c, y + 72, w, 66, k.text(6, 8, 298, 'Tooltip title', color=k.HL) + k.text(6, 35, 298, 'One or two short sentences.', wrap=True))

    f = k.frame(W, 640, o, confirm='Return')
    side = k.cell('tile: picture, marker, name, level, rule, icon', small(
        k.tile(0, 0, P[0], 'Reka Yamada', k.QUEST, level(5), SHIPS[0], True) + k.tile(92, 0, P[3], 'Maximilian Wolkensteiner', None, level(2))
        + k.tile(184, 0, P[2], 'Ilse Varga', LOW, level(4), SHIPS[2]), 290, 164))
    pop = k.cell('popup: the game\'s confirmation', k.popup('Scrap <span class="hl">ISS Long Look</span>?', h=60))
    n = k.notes('Every helper of <code>kit.py</code> once, with its name.',
                'Sizes are the dense defaults: heading 18, tab and button 24, row 50, strip 40 or 50, bar 20, text line 18.',
                title='Parts sheet')
    return k.board('example_parts', 'Parts sheet', '<div style="display:flex;flex-direction:column;gap:24px">%s%s</div>' % (f, k.sidebyside(side, pop)), n)


def assignment(w, home):
    """the Assignment block of the card with Accelerate in one of four homes"""
    def acc(x, y, cut='all'):
        return k.costbutton(x, y, 124, 24, 'Accelerate', k.sample('ui/fuel.png'), '60', 'std', True, cut)
    o = k.heading(0, 0, w, 'Assignment', 'left', 'Day 10 of 30')
    y = 18 + k.ROW_GAP
    o += ship_strip(y)
    y += 50 + k.GAP
    if home == 'bar':
        o += k.bar(0, y + 2, w - 124 - k.GAP, 20, 1 / 3, 'standard', '20 days left') + acc(w - 124, y)
    else:
        o += k.bar(0, y, w, 20, 1 / 3, 'standard', '20 days left')
    y += 20
    if home == 'now':
        o += acc(0, y + k.GAP)
    elif home == 'strip':
        o += k.card(0, y + k.GAP, w, 40, k.change(10, 12, w - 164, 'Days left', '20', '10') + acc(w - 132, 8))
    elif home == 'bottom':
        y += 12
        o += (k.rule(0, y, w) + k.button(0, y + 12, 104, 24, 'Service log', 'std', 'tlbr') + acc(110, y + 12, 'tlbr')
              + k.button(w - 96, y + 12, 96, 24, 'Dismiss', 'std', 'tlbr'))
    return small(o, w + 20, 172)


def experiment():
    w = 420
    global DX, DW
    keep = DX, DW
    DX, DW = 0, w
    try:
        cells, pick = k.experiment(
            '"Accelerate looks lost under the bar."',
            assignment(w, 'now'),
            [('On the bar', 'other owner: the days left, the one value it changes', assignment(w, 'bar'),
              'The bar is 132 px shorter.'),
             ('In the bottom row', 'other owner: the panel\'s rare actions', assignment(w, 'bottom'),
              'Far from the bar it changes. Right only if Accelerate is used rarely.'),
             ('Strip with the result', 'other picture: the result is drawn, the tooltip no longer has to say it', assignment(w, 'strip'),
              '48 px more height, and the days left are shown twice.')],
            recommend=0, reason='Accelerate changes the days left and nothing else, so it sits on the bar that shows them.')
    finally:
        DX, DW = keep
    f = k.sidebyside(*cells, width=2 * (w + 20) + 24)
    n = k.notes(
        'An experiment round: the user was unhappy with one element and gave no solution. Three variants that differ in kind, next to the current state, with the same data.',
        'Each variant has a name that says its idea, the kind of change and what it costs. Two variants that differ by a number or a side are one variant.',
        pick,
        'The message under the board ends with one question: "Which one, or what bothers you about all of them?"',
        'After the pick: move it into every board and state, delete this board, add the states the pick needs, write the decision down.',
        title='Experiment round')
    return k.board('example_experiment', 'Experiment: where does Accelerate live?', f, n)


def texts():
    rows = [
        ('Tabs', 'Teams 4 / 5 &middot; Charter'),
        ('List headings', 'On assignment &middot; Idle &middot; Lead teams &middot; Teams'),
        ('Empty list', 'No idle team.'),
        ('States', 'Surveying &middot; Returning &middot; Idle'),
        ('Tagline, sample', 'Reka is cataloguing ore veins and has opinions about the coffee.'),
        ('Ship strip', 'Change... &middot; Recall'),
        ('Blocked Change', 'No other ship is free.'),
        ('Request strip', 'Asks for supplies &middot; 12 days left &middot; Send'),
        ('Tooltip, Send', 'Send supplies. The team stays out for its full term.'),
        ('Bottom row', 'Service log &middot; Dismiss'),
        ('Charter tab', 'Grade &middot; Ship &middot; Cargo &middot; Days &middot; Hire &middot; Cost each &middot; Charter &middot; Travel time &middot; Upkeep &middot; Cost &middot; Fee'),
        ('Editor', 'Saved plans &middot; New plan &middot; Team &middot; Ship &middot; Method &middot; Orbital scan &middot; Ground team &middot; Deep core &middot; Options &middot; Repeat monthly &middot; Recall when attacked &middot; Each run &middot; Duration &middot; Delete &middot; Cancel &middot; Save'),
        ('Popup, Dismiss', 'Dismiss Ilse Varga? She leaves the office for good.'),
        ('Tooltip, Accelerate', 'Halves the days left. The fuel is used up at once.'),
        ('Marker tooltips', 'Asks for something &middot; Low on supplies &middot; Stranded'),
    ]
    n = k.notes('Every text the player reads that these boards add, in one place. Reword here; the boards follow.',
                'Offer this board when the user wants to approve the wording, and always when the mockup invents lore or flavour text.',
                title='Text for approval')
    return k.board('example_text', 'Player-facing text', k.textlist(rows), n)


BOARDS = [list_detail, table, editor, tiles, sidepanel, states, spacing, experiment, density, parts, texts]
