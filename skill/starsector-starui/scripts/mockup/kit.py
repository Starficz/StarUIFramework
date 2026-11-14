"""Starsector mockup kit: draws UI boards as absolutely placed HTML at game pixel size.

Every helper takes x, y (top left, y down, like PositionAPI.inTL) and sizes in game pixels and returns an
HTML string. Add the strings up, wrap them in frame() and board(), list the board functions in BOARDS and
run build.py on the file. example_board.py shows every helper in use.

Colours here are the vanilla defaults (player faction, data/config/settings.json). They are for mockups
only: mod code reads them through the API accessors, never as numbers.
"""
import hashlib
import html as _html
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SKILL = os.path.dirname(os.path.dirname(HERE))


def _find_game():
    cands = [os.environ.get('SS_GRAPHICS'),
             os.path.join(os.path.dirname(SKILL), 'starsector-knowledge', 'graphics')]
    for c in cands:
        if c and os.path.isdir(c):
            return c
    return None


GAME = _find_game()                      # the game's graphics folder; the starsector-knowledge copy has icons/, hullmods/, ui/
MOD = os.environ.get('MOD_GRAPHICS')     # the mod's own graphics folder
SAMPLE = os.path.join(HERE, 'sample')    # a few portraits, ships and chrome sprites for mockups
CACHE = os.path.join(os.environ.get('MOCK_OUT', os.getcwd()), '_tint')

# vanilla default palette
BASE = 'rgb(170,222,255)'          # FactionAPI.getBaseUIColor, player: labels on buttons, heading text
BRIGHT = 'rgb(203,245,255)'        # getBrightUIColor: selected, hover
DARK = 'rgba(31,94,112,.686)'      # getDarkUIColor: button and heading fill
DARKS = 'rgb(21,64,77)'            # the dark fill as it looks on black
EDGE = 'rgb(21,65,77)'             # 1 px border of rows, cards and cells
HL = 'rgb(255,210,0)'              # Misc.getHighlightColor: numbers, costs, key values
TEXT = 'rgb(220,220,220)'          # Misc.getTextColor
GRAY = 'rgba(175,175,175,.706)'    # Misc.getGrayColor: second lines, notes, empty states
POS = 'rgb(155,255,0)'             # Misc.getPositiveHighlightColor
NEG = 'rgb(255,100,0)'             # Misc.getNegativeHighlightColor: red reasons, shortfalls
STORY = 'rgb(163,239,128)'         # Misc.getStoryOptionColor
LINE = 'rgba(170,222,255,.3)'      # rules
FRAME = (100, 220, 255)            # widgetBorderColorBright: bar frame lines
GREEN = (50, 130, 0)               # state colours, the only raw colours a mod defines
RED = (160, 30, 20)
AMBER = (200, 160, 10)
BARS = {'standard': (0, 121, 216), 'cargo': (203, 203, 144), 'fuel': (255, 125, 25), 'crew': (0, 225, 160),
        'warning': (255, 210, 0), 'danger': (255, 50, 0), 'overflow': (255, 0, 0), 'ready': GREEN}

MISSING = []


def rgb(c, a=None):
    c = tuple(int(round(max(0, min(255, v)))) for v in c)
    return 'rgb(%d,%d,%d)' % c if a is None else 'rgba(%d,%d,%d,%.3g)' % (c + (a,))


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def scale(c, f):
    return tuple(v * f for v in c)


def _url(path):
    return 'file://' + path.replace(' ', '%20')


def game(rel):
    """Path of a file in the game's graphics folder, for example game('icons/cargo/supplies.png')."""
    return os.path.join(GAME, rel) if GAME else os.path.join('<SS_GRAPHICS not set>', rel)


def mod(rel):
    return os.path.join(MOD, rel) if MOD else os.path.join('<MOD_GRAPHICS not set>', rel)


def sample(rel):
    return os.path.join(SAMPLE, rel)


CREDITS = sample('ui/credits.png')
HELP = sample('ui/more_info.png')
QUEST = sample('ui/quest.png')     # the vanilla (!) marker: settings sprite "intel", "important"

SECTION = 10                       # vanilla's opad: between blocks
HEAD_GAP = 10                      # vanilla's opad: above a section heading, and from it to its content
GAP = 5                            # vanilla's small: between two parts of one block
ROW_GAP = 3                        # vanilla's pad: between lines and rows that belong together


def css():
    f = os.path.join(HERE, 'fonts')
    faces = ''.join('@font-face{font-family:%s;src:url(%s)}' % (n, _url(os.path.join(f, n + '.ttf')))
                    for n in ('ins15', 'ins21', 'orb12', 'orb20', 'vic10'))
    return faces + '''
body{margin:0;background:#14181c;color:#c9d4da}
.page{padding:28px;display:flex;flex-direction:column;gap:44px;width:max-content}
.board{display:flex;gap:28px;align-items:flex-start;padding:4px}
.cap{font:600 17px 'Segoe UI',Helvetica,Arial,sans-serif;color:#e6eef2;margin:0 0 12px}
.notes{font:14px/1.5 'Segoe UI',Helvetica,Arial,sans-serif;color:#c9d4da;width:400px}
.notes h4{font:600 14px 'Segoe UI',Helvetica,Arial,sans-serif;color:#e6eef2;margin:14px 0 4px}
.notes ul{margin:4px 0 0 18px;padding:0}.notes li{margin:4px 0}
.frame{position:relative;background:#000;box-shadow:0 0 0 1px rgb(150,170,178),0 0 0 2px rgba(0,0,0,.9),0 0 0 3px rgba(60,120,140,.6)}
.screen{position:relative;background:#05080b;overflow:hidden}
.a{position:absolute;box-sizing:border-box}
.t{white-space:nowrap;overflow-x:clip;overflow-y:visible}
.ins15{font:15px/17px ins15}.ins21{font:21px/22px ins21}.orb12{font:12px/15px orb12}.orb20{font:20px/22px orb20}.vic10{font:10px/11px vic10}
.hl{color:rgb(255,210,0)}.pos{color:rgb(155,255,0)}.neg{color:rgb(255,100,0)}.gr{color:rgba(175,175,175,.706)}
.bs{color:rgb(170,222,255)}.br{color:rgb(203,245,255)}.st{color:rgb(163,239,128)}.tx{color:rgb(220,220,220)}
'''



def box(x, y, w, h, style='', inner='', cls=''):
    return '<div class="a %s" style="left:%gpx;top:%gpx;width:%gpx;height:%gpx;%s">%s</div>' % (cls, x, y, w, h, style, inner)


def text(x, y, w, s, font='ins15', color=TEXT, align='left', wrap=False):
    """One line of text in a box w wide (the build warns when it does not fit). wrap=True wraps at w.
    s may hold spans: '<span class="hl">30</span> days'."""
    return '<div class="a %s %s" style="left:%gpx;top:%gpx;width:%gpx;color:%s;text-align:%s">%s</div>' % (
        '' if wrap else 't', font, x, y, w, color, align, s)


_SIZES = {'ins15': 15, 'ins21': 21, 'orb12': 12, 'orb20': 20, 'vic10': 10}
_FONTS = {}


def textwidth(s, font='ins15'):
    """Width of a plain string in px, measured with the bundled font (needs Pillow; else an estimate)."""
    s = _html.unescape(str(s))
    try:
        from PIL import ImageFont
        if font not in _FONTS:
            _FONTS[font] = ImageFont.truetype(os.path.join(HERE, 'fonts', font + '.ttf'), _SIZES[font])
        return _FONTS[font].getlength(s)
    except Exception:
        return len(s) * _SIZES.get(font, 15) * .5


def fit(s, w, font='ins15'):
    """s shortened with ... so that it fits w, as mod code must do for every one line text. Plain strings only."""
    s = _html.unescape(str(s))
    if textwidth(s, font) <= w - 2:
        return _html.escape(s)
    while s and textwidth(s.rstrip() + '...', font) > w - 2:
        s = s[:-1]
    return _html.escape(s.rstrip()) + '...'


def namelines(x, y, w, name, second=None, color=TEXT, pitch=19):
    """A name with a grey second line below it. A name too long for one line wraps to two lines and the
    second line drops; a name too long for two lines ends in an ellipsis."""
    if textwidth(name) <= w - 2:
        return text(x, y, w, name, 'ins15', color) + (text(x, y + pitch, w, second, 'ins15', GRAY) if second else '')
    return ('<div class="a ins15" style="left:%gpx;top:%gpx;width:%gpx;color:%s;display:-webkit-box;-webkit-line-clamp:2;'
            '-webkit-box-orient:vertical;overflow:hidden">%s</div>' % (x, y, w, color, name))


def rule(x, y, w, color=LINE):
    return box(x, y, w, 1, 'background:%s' % color)


def vrule(x, y, h, color=LINE):
    return box(x, y, 1, h, 'background:%s' % color)


def _inner(w, h, inner):
    """Children of a bordered box are placed from its outer edge, as inTL places them in the game."""
    return '<div class="a" style="left:-1px;top:-1px;width:%gpx;height:%gpx">%s</div>' % (w, h, inner) if inner else ''


def card(x, y, w, h, inner=''):
    """Black box with a 1 px border. Never give it a fill: vanilla does not tint tiles.
    inner is placed in card coordinates, measured from the outer edge. Keep text 8 px or more from the border."""
    return box(x, y, w, h, 'border:1px solid %s' % EDGE, _inner(w, h, inner))


def dashbox(x, y, w, h, inner=''):
    """Empty slot."""
    return box(x, y, w, h, 'border:1px dashed %s' % EDGE, _inner(w, h, inner))


def img(x, y, w, h, src, fit='contain', extra=''):
    if not os.path.isfile(src):
        MISSING.append(src)
        return box(x, y, w, h, 'border:1px dashed rgb(255,100,0)',
                   '<div class="vic10" style="color:rgb(255,100,0);overflow:hidden">%s</div>' % _html.escape(os.path.basename(src)))
    return '<img class="a" src="%s" style="left:%gpx;top:%gpx;width:%gpx;height:%gpx;object-fit:%s;%s" alt="">' % (_url(src), x, y, w, h, fit, extra)


def tint(x, y, w, h, src, color=(170, 222, 255)):
    """Sprite multiplied by a colour, as SpriteAPI.setColor does (help icon, white glyph sprites)."""
    if not os.path.isfile(src):
        return img(x, y, w, h, src)
    try:
        from PIL import Image
    except ImportError:
        return img(x, y, w, h, src)
    os.makedirs(CACHE, exist_ok=True)
    out = os.path.join(CACHE, hashlib.md5((src + str(color)).encode()).hexdigest()[:12] + '.png')
    if not os.path.exists(out):
        im = Image.open(src).convert('RGBA')
        r, g, b, a = im.split()
        r = r.point(lambda v: v * color[0] // 255)
        g = g.point(lambda v: v * color[1] // 255)
        b = b.point(lambda v: v * color[2] // 255)
        Image.merge('RGBA', (r, g, b, a)).save(out)
    return img(x, y, w, h, out)



def heading(x, y, w, s, align='center', note=None, h=18, bg=DARK, color=BASE):
    """Section heading bar, 18 high like addSectionHeading. align left: text 8 px in. note: grey text at the right end."""
    pad = '' if align == 'center' else 'padding:0 8px;'
    inner = '<div class="t orb12" style="color:%s;text-align:%s;line-height:%gpx;%s">%s</div>' % (color, align, h, pad, s)
    if note:
        inner += '<div class="a t orb12" style="right:8px;top:0;line-height:%gpx;color:%s">%s</div>' % (h, GRAY, note)
    return box(x, y, w, h, 'background:%s' % bg, inner)


def thead(x, y, w, cols, h=18):
    """Table header strip. cols: (label, x, w, align) with x relative to the strip."""
    inner = ''.join('<div class="a t orb12" style="left:%gpx;top:0;width:%gpx;line-height:%gpx;color:%s;text-align:%s">%s</div>'
                    % (cx, cw, h, BASE, al, lab) for lab, cx, cw, al in cols)
    return box(x, y, w, h, 'background:rgba(31,94,112,.35)', inner)


def title(w, s, sub=None, help=True):
    """Title strip at y 0 of the content. The ? help icon sits at the right end and holds rules and rates."""
    hw = w - 26 if help else w
    t = s if not sub else '%s <span style="color:%s">&nbsp;%s</span>' % (s, GRAY, sub)
    out = heading(0, 0, hw, t)
    if help:
        out += tint(w - 20, -1, 20, 20, HELP)
    return out


def _cut(h, cut):
    c = round(h / 4) if cut in ('all', 'none') else round(h / 3)
    p = {'all': '%dpx 0,calc(100%% - %dpx) 0,100%% %dpx,100%% calc(100%% - %dpx),calc(100%% - %dpx) 100%%,%dpx 100%%,0 calc(100%% - %dpx),0 %dpx' % ((c,) * 8),
         'top': '%dpx 0,calc(100%% - %dpx) 0,100%% %dpx,100%% 100%%,0 100%%,0 %dpx' % ((c,) * 4),
         'bottom': '0 0,100%% 0,100%% calc(100%% - %dpx),calc(100%% - %dpx) 100%%,%dpx 100%%,0 calc(100%% - %dpx)' % ((c,) * 4),
         'tlbr': '%dpx 0,100%% 0,100%% calc(100%% - %dpx),calc(100%% - %dpx) 100%%,0 100%%,0 %dpx' % ((c,) * 4),
         'bltr': '0 0,calc(100%% - %dpx) 0,100%% %dpx,100%% 100%%,%dpx 100%%,0 calc(100%% - %dpx)' % ((c,) * 4)}
    return 'clip-path:polygon(%s);' % p[cut]


def button(x, y, w, h, label, kind='std', cut='all', icon=None, sub=None, font='orb12', subcolor=None):
    """kind: std (every vanilla button: base label on the dark fill), story (costs a story point, vanilla's story
    colours), hot (the selected one of a set), off (disabled). green, red and amber exist for old boards only:
    vanilla never fills a button by what it does.
    cut: all (single button), tlbr (commit and cancel row), bltr (a bank of actions on the thing on screen),
    top (tab), bottom (a button under a block).
    icon and sub put a cost on the button: icon, label, amount ("[relic] Gene scan 10"). subcolor overrides the amount colour."""
    bg = {'std': DARKS, 'green': rgb(scale(GREEN, .6)), 'red': rgb(RED), 'amber': rgb(scale(AMBER, .6)),
          'story': 'rgb(45,66,35)', 'hot': 'rgb(31,94,112)', 'off': 'rgb(73,95,101)'}[kind]
    col = {'std': BASE, 'green': TEXT, 'red': TEXT, 'amber': TEXT, 'story': STORY, 'hot': BRIGHT,
           'off': 'rgba(164,185,190,.75)'}[kind]
    ic = ''
    if icon:
        ic = '<img src="%s" style="height:%gpx;vertical-align:middle;margin:-3px 5px 0 0%s" alt="">' % (
            _url(icon), min(h - 6, 22), ';opacity:.4' if kind == 'off' else '')
    s2 = ' <span style="color:%s">%s</span>' % (subcolor or (HL if kind != 'off' else col), sub) if sub else ''
    return box(x, y, w, h, 'background:%s;%s' % (bg, _cut(h, cut)),
               '<div class="t %s" style="color:%s;text-align:center;line-height:%gpx">%s%s%s</div>' % (font, col, h, ic, label, s2))


def costbutton(x, y, w, h, label, icon, amount, kind='std', ok=True, cut='all'):
    """A button that carries its cost: icon, label, amount. Short on stock: grey in place with the amount in red;
    the tooltip then shows the cost against the stock."""
    if ok:
        return button(x, y, w, h, label, kind, cut, icon, amount)
    return button(x, y, w, h, label, 'off', cut, icon, amount, subcolor=NEG)


def bank(x, y, buttons, h=24, gap=6, align='left', cut=None):
    """Buttons side by side. buttons: (label, kind, width) or (label, kind, width, icon, amount).
    align right: x is the right edge of the last button. One button is cut all, several are cut tlbr."""
    cut = cut or ('all' if len(buttons) == 1 else 'tlbr')
    total = sum(b[2] for b in buttons) + gap * (len(buttons) - 1)
    bx = x - total if align == 'right' else x
    out = ''
    for b in buttons:
        out += button(bx, y, b[2], h, b[0], b[1], cut, b[3] if len(b) > 3 else None, b[4] if len(b) > 4 else None)
        bx += b[2] + gap
    return out


def guarded(x, y, w, h, label, kind='std', reason=None):
    """A button that can be blocked: grey with one red reason line below. Reserve the line either way (h + 4 + 20)."""
    out = button(x, y, w, h, label, 'off' if reason else kind)
    if reason:
        out += text(x, y + h + 4, w, reason, 'ins15', NEG, wrap=True)
    return out


def tab(x, y, w, h, label, on=False, enabled=True):
    bg = 'rgb(31,94,112)' if on else ('rgba(31,94,112,.41)' if enabled else 'rgba(60,70,74,.4)')
    col = BRIGHT if on else (BASE if enabled else 'rgba(168,180,186,.5)')
    return box(x, y, w, h, 'background:%s;%s' % (bg, _cut(h, 'top')),
               '<div class="t orb12" style="color:%s;text-align:center;line-height:%gpx">%s</div>' % (col, h, label))


def tabs(x, y, w, labels, selected=0, tab_w=150, h=24):
    """Tab bar with the rule below it; returns (html, y under the rule)."""
    out = ''.join(tab(x + i * (tab_w + 4), y, tab_w, h, lab, i == selected) for i, lab in enumerate(labels))
    return out + rule(x, y + h, w), y + h + 1


def area(x, y, w, h, label='', on=False, enabled=True, inner='', strip=None, align='center', font='orb12'):
    """Area toggle (addAreaCheckbox look): black with a 1 px border, filled only when on."""
    if on:
        s = 'border:1px solid %s;background:%s;box-shadow:inset 0 0 0 1px #000' % (EDGE, EDGE)
        col = BRIGHT
    elif enabled:
        s = 'border:1px solid %s' % EDGE
        col = BASE
    else:
        s = 'border:1px solid rgba(21,65,77,.55)'
        col = 'rgba(135,170,186,.55)'
    sp = box(0, 0, 4, h - 2, 'background:%s;opacity:%s' % (strip, 1 if enabled else .35)) if strip else ''
    lab = ''
    if label:
        pad = '' if align == 'center' else 'padding:0 10px;'
        lab = '<div class="t %s" style="color:%s;text-align:%s;line-height:%gpx;%s">%s</div>' % (font, col, align, h - 2, pad, label)
    return box(x, y, w, h, s, sp + lab + _inner(w, h, inner))


def row(x, y, w, h, inner='', selected=False, enabled=True, strip=None):
    """Selectable list row. inner is placed in row coordinates, measured from the outer edge."""
    return area(x, y, w, h, '', selected, enabled, inner, strip)


def check(x, y, label, on, w=200, enabled=True):
    s = sample('ui/check_%s.png' % ('on' if on else 'off'))
    return img(x, y, 20, 20, s, 'contain', '' if enabled else 'opacity:.4') + text(x + 26, y + 2, w - 26, label, 'ins15', TEXT if enabled else GRAY)


def stepper(x, y, value, enabled=True):
    k = 'std' if enabled else 'off'
    return (button(x, y, 22, 22, '-', k) + text(x + 22, y + 3, 28, str(value), 'ins15', HL if value else GRAY, 'center')
            + button(x + 50, y, 22, 22, '+', k))


def scrollbar(x, y, h, frac=.5, off=0):
    """Vanilla scroller: 3 px at the right edge of the list. Keep 10 px of the list width free for it.
    frac is the thumb length and off the thumb start, both as a fraction of the track (visible / content, offset / content)."""
    return box(x, y, 3, h, 'background:rgba(170,222,255,.10)', box(0, h * off, 3, h * frac, 'background:rgba(170,222,255,.55)'))



def bar(x, y, w, h, frac, color='standard', label=''):
    """Progress bar drawn like the vanilla bar widget: black track, inset fill that is brightest in the
    vertical middle, dim empty part, doubled end lines, corner stubs. color: a BARS key or an (r, g, b)."""
    c = BARS.get(color, color)
    f = max(0, min(1, frac))
    fr = rgb(FRAME)
    soft = mix(c, (255, 255, 255), .15)
    edge = scale(soft, .75)
    mid = tuple(min(255, edge[i] + c[i] * .75) for i in range(3))
    usable = w - 8
    fw = round(usable * f) + 1
    o = box(3, 1, fw, h - 2, 'background:linear-gradient(to bottom,%s,%s,%s)' % (rgb(edge), rgb(mid), rgb(edge)))
    ew = usable - fw - 4
    if ew > 0 and h - 6 > 1:
        e0 = scale(soft, .65 * .75)
        e1 = scale(e0, .68)
        ex = 3 + fw + 5
        o += box(ex, 3, ew, h - 6, 'background:linear-gradient(to bottom,%s,%s,%s)' % (rgb(e0), rgb(e1), rgb(e0)))
        o += box(ex - 2, 1, w - 2 - (ex - 2), 1, 'background:%s;opacity:.8' % fr) + box(ex - 2, h - 2, w - 2 - (ex - 2), 1, 'background:%s;opacity:.8' % fr)
        o += box(ex - 2, 0, 1, h, 'background:%s' % fr)
    o += box(0, 0, 2, h, 'background:%s' % fr) + box(w - 2, 0, 2, h, 'background:%s' % fr)
    for sx, d in ((1, 90), (w - 15, 270)):
        g = 'linear-gradient(%ddeg,%s,transparent)' % (d, fr)
        o += box(sx, 0, 14, 1, 'background:' + g) + box(sx, h - 1, 14, 1, 'background:' + g)
    if label:
        o += '<div class="a t ins15" style="left:4px;top:0;width:%gpx;line-height:%gpx;text-align:center;color:%s;text-shadow:0 0 2px #000,0 1px 1px #000">%s</div>' % (w - 8, h, TEXT, label)
    return box(x, y, w, h, 'background:#000', o)


def thinbar(x, y, w, frac, color='standard', h=3):
    """3 px bar for a list row."""
    c = mix(BARS.get(color, color), (255, 255, 255), .15)
    f = max(0, min(1, frac))
    return box(x, y, w, h, 'background:%s' % rgb(scale(c, .2)), box(0, 0, round(w * f), h, 'background:%s' % rgb(scale(c, .75))))


def pips(x, y, total, filled, size=8, gap=3, color=HL):
    """Small squares for a level of a few steps."""
    return ''.join(box(x + i * (size + gap), y, size, size, 'background:%s' % (color if i < filled else 'rgba(170,222,255,.18)'))
                   for i in range(total))


def mark(x, y, ok, size=12):
    if ok:
        return '<svg class="a" style="left:%gpx;top:%gpx" width="%d" height="%d" viewBox="0 0 12 12"><path d="M1.5 6.5 L4.5 9.5 L10.5 2.5" stroke="%s" stroke-width="2" fill="none"/></svg>' % (x, y, size, size, POS)
    return '<svg class="a" style="left:%gpx;top:%gpx" width="%d" height="%d" viewBox="0 0 12 12"><path d="M2 2 L10 10 M10 2 L2 10" stroke="%s" stroke-width="2" fill="none"/></svg>' % (x, y, size, size, NEG)


def checkrow(x, y, w, ok, s):
    """Requirement line: tick or cross, text red while unmet."""
    return mark(x, y + 2, ok) + text(x + 18, y, w - 18, s, 'ins15', TEXT if ok else NEG)


def grid(x, y, w, rows, rowh=18, value_color=HL):
    """Label left, value right. rows: (label, value) or (label, value, colour)."""
    out = ''
    for i, r in enumerate(rows):
        out += text(x, y + i * rowh, w * .6, r[0], 'ins15', TEXT) + text(x + w * .4, y + i * rowh, w * .6, r[1], 'ins15', r[2] if len(r) > 2 else value_color, 'right')
    return out


def change(x, y, w, label, old, new):
    """The effect of an action on one number: old value struck through in grey, an arrow, the new value."""
    s = '<span style="color:%s;text-decoration:line-through;text-decoration-color:%s">&nbsp;%s&nbsp;</span> <span style="color:%s">-&gt;</span> <span style="color:%s">%s</span>' % (GRAY, TEXT, old, GRAY, HL, new)
    return text(x, y, w * .5, label) + text(x + w * .3, y, w * .7, s, 'ins15', HL, 'right')


def costrow(x, y, w, label, icon, amount, stock=None, ok=True, h=22, color=None):
    """Cost line: label left; right the icon, the amount in highlight (red when short) and the stock in grey brackets.
    icon None draws no icon; color overrides the amount colour (STORY for a story point cost)."""
    ic = '<img src="%s" style="height:%gpx;vertical-align:middle;margin:-3px 4px 0 0" alt="">' % (_url(icon), h) if icon else ''
    s = '%s<span style="color:%s">%s</span>' % (ic, NEG if not ok else (color or HL), amount)
    if stock is not None:
        s += ' <span class="gr">(%s)</span>' % stock
    return text(x, y + 3, w * .5, label) + text(x + w * .3, y + 3, w * .7, s, 'ins15', HL, 'right')


def counter(right, y, label, icon, value):
    """Header item that ends at x = right: grey label, 24 px icon (None for no icon), highlighted value.
    Returns (html, right edge for the next counter to the left)."""
    ic = '<img src="%s" style="width:24px;height:24px" alt="">' % _url(icon) if icon else ''
    o = ('<div class="a" style="left:0;top:%gpx;width:%gpx;height:24px;display:flex;justify-content:flex-end;align-items:center;gap:6px">'
         '<span class="ins15 gr">%s</span>%s<span class="ins15 hl">%s</span></div>' % (y, right, label, ic, value))
    used = textwidth(label) + textwidth(value) + (30 if icon else 0) + 6
    return o, right - round(used) - 24


def portrait(x, y, size, src):
    return img(x, y, size, size, src, 'cover')


def ship(x, y, size, src, framed=True, dim=False):
    f = box(x, y, size, size, 'border:1px solid %s' % EDGE) if framed else ''
    return f + img(x + 4, y + 4, size - 8, size - 8, src, 'contain', 'opacity:.4;' if dim else '')


def stack(x, y, size, src, count=None):
    """Cargo style cell: sprite in a bordered cell, count bottom right in highlight."""
    out = box(x, y, size, size, 'border:1px solid %s' % EDGE) + img(x + 6, y + 6, size - 12, size - 12, src)
    if count is not None:
        out += text(x, y + size - 18, size - 3, str(count), 'ins15', HL, 'right')
    return out


def morecell(x, y, w, h, n):
    return box(x, y, w, h, 'border:1px solid %s' % EDGE, '<div class="t ins15" style="color:%s;text-align:center;line-height:%gpx">+%d</div>' % (BASE, h - 2, n))


def iconrow(x, y, w, items, cw, ch, render, gap=4, rows=2):
    """Fixed cells, at most `rows` rows, then a +N cell. render(x, y, item) draws one cell. Returns (html, used height)."""
    per = int((w + gap) // (cw + gap))
    cap = per * rows
    show = items if len(items) <= cap else items[:cap - 1]
    o = ''.join(render(x + (i % per) * (cw + gap), y + (i // per) * (ch + gap), it) for i, it in enumerate(show))
    if len(items) > cap:
        i = cap - 1
        o += morecell(x + (i % per) * (cw + gap), y + (i // per) * (ch + gap), cw, ch, len(items) - len(show))
    used = min(rows, -(-min(len(items), cap) // per))
    return o, used * (ch + gap) - gap


def tooltip(x, y, w, h, inner):
    """Hover tooltip: dark fill and the bracket border (side rules with corner hooks, top and bottom open).
    Content starts 5 px in, title in highlight, paragraphs 10 px apart."""
    e = BASE
    br = (box(0, 0, 1, h, 'background:' + e) + box(w - 1, 0, 1, h, 'background:' + e) + box(0, 0, 7, 1, 'background:' + e)
          + box(0, h - 1, 7, 1, 'background:' + e) + box(w - 7, 0, 7, 1, 'background:' + e) + box(w - 7, h - 1, 7, 1, 'background:' + e))
    return box(x, y, w, h, 'background:rgba(0,0,0,.92);box-shadow:0 0 0 1px rgba(0,0,0,.6);z-index:5', br + inner, 'tip')



def frame(w, h, inner, confirm='Return', cancel=None):
    """A showCustomDialog window of w x h. The content panel is (w - 20) x (h - 55) at 10, 10; the game adds
    its own confirm button 150 x 25 at the bottom right; with a cancel button, Confirm is left and Cancel right."""
    c = box(10, 10, w - 20, h - 55, '', inner)
    if confirm and cancel:
        # the game puts Cancel in the corner and Confirm left of it
        c += button(w - 10 - 150, h - 10 - 25, 150, 25, cancel, 'std', 'tlbr', None, None, 'orb20')
        c += button(w - 10 - 150 - 10 - 150, h - 10 - 25, 150, 25, confirm, 'std', 'tlbr', None, None, 'orb20')
    elif confirm:
        c += button(w - 10 - 150, h - 10 - 25, 150, 25, confirm, 'std', 'tlbr', None, None, 'orb20')
    return '<div class="frame" style="width:%dpx;height:%dpx">%s</div>' % (w, h, c)


def popup(text_html, confirm='Confirm', cancel='Cancel', w=500, h=130):
    """The game's confirmation popup: text in Insignia 21, Confirm left, Cancel right. Every confirmation uses it;
    a panel never arms Confirm and Cancel in place and never draws its own popup."""
    return frame(w + 20, h + 55, text(0, 0, w, text_html, 'ins21', TEXT, 'left', True), confirm, cancel)


_STAR = '9.0,0.0 11.2,6.0 17.6,6.2 12.5,10.1 14.3,16.3 9.0,12.7 3.7,16.3 5.5,10.1 0.4,6.2 6.8,6.0'


_STARS = [0]


def star(x, y, on, enabled=True, size=18):
    """Favourite toggle for a name bar: gold when set, an outline when not, a grey outline when it cannot be set.
    The outline is clipped to the star, so it lies inside the shape and all states have the same silhouette.
    A placeholder drawing: the game has no such sprite, the mod ships one."""
    svg = '<svg class="a" style="left:%gpx;top:%gpx" width="%d" height="%d" viewBox="-1 -1 20 20">%%s</svg>' % (x, y, size, size)
    if on:
        return svg % ('<polygon points="%s" fill="%s"/>' % (_STAR, HL))
    _STARS[0] += 1
    cid = 'kitstar%d' % _STARS[0]
    return svg % ('<defs><clipPath id="%s"><polygon points="%s"/></clipPath></defs>'
                  '<polygon points="%s" fill="none" stroke="%s" stroke-width="2.6" clip-path="url(#%s)"/>'
                  % (cid, _STAR, _STAR, BASE if enabled else 'rgba(175,175,175,.5)', cid))


def namebar(x, y, w, name, fav=None, can_fav=True, h=26):
    """The selected entry's name as a text field across its column: the player renames in place, there is no
    Rename button. fav True or False adds the favourite star at the right end; can_fav False greys it."""
    o = '<div class="t orb20" style="color:%s;text-align:center;line-height:%gpx">%s</div>' % (BRIGHT, h - 2, name)
    out = box(x, y, w, h, 'border:1px solid rgba(170,222,255,.45)', o)
    if fav is not None:
        out += star(x + w - 23, y + (h - 18) // 2, fav, can_fav)
    return out


def tagline(x, y, w, s, color=GRAY):
    """One flavour line of the entry, centred under its name. Two lines at most; it changes with state, it never explains."""
    return '<div class="a ins15" style="left:%gpx;top:%gpx;width:%gpx;color:%s;text-align:center">%s</div>' % (x, y, w, color, s)


def shortrule(x, y, w, frac=.7):
    """A centred rule over part of the width: separates two parts of a tile without closing it off."""
    rw = round(w * frac)
    return rule(x + (w - rw) // 2, y, rw)


def strip(x, y, w, h, inner='', buttons=(), bh=24):
    """Action strip: one thing and the buttons that change it, in one bordered line (a ship with Move and
    Unassign, a request with its answer). inner is in strip coordinates; buttons (as in bank) sit right
    aligned 8 px from the border, centred. 40 high for one text line, 50 for two."""
    return card(x, y, w, h, inner + (bank(w - 8, (h - bh) // 2, list(buttons), bh, align='right') if buttons else ''))


def lumtint(src, color, cut=False):
    """A sprite reduced to its brightness and coloured: a placeholder status glyph made from any game icon.
    cut drops the faint glow around a glyph sprite so an outline follows the glyph. Needs Pillow."""
    try:
        from PIL import Image, ImageOps
    except ImportError:
        return src
    if not os.path.isfile(src):
        return src
    os.makedirs(CACHE, exist_ok=True)
    out = os.path.join(CACHE, 'm' + hashlib.md5((src + str(color) + str(cut)).encode()).hexdigest()[:12] + '.png')
    if not os.path.exists(out):
        im = Image.open(src).convert('RGBA')
        lum = ImageOps.autocontrast(im.convert('L'))
        alpha = im.split()[3]
        if cut:
            alpha = alpha.point(lambda v: 0 if v < 110 else min(255, (v - 110) * 3))
        Image.merge('RGBA', tuple(lum.point(lambda v, c=c: (100 + v * 155 // 255) * c // 255) for c in color) + (alpha,)).save(out)
    return out


def _outlined(src, s, r):
    import math
    from PIL import Image, ImageChops, ImageOps
    os.makedirs(CACHE, exist_ok=True)
    out = os.path.join(CACHE, 'o' + hashlib.md5((src + str(s) + str(r)).encode()).hexdigest()[:12] + '.png')
    if not os.path.exists(out):
        im = ImageOps.contain(Image.open(src).convert('RGBA'), (s * 2, s * 2), Image.LANCZOS)
        side = (s + 2 * r + 2) * 2
        ox, oy = (side - im.width) // 2, (side - im.height) // 2
        alpha = im.split()[3]
        stamp = alpha.point(lambda v: v * 3 // 4)
        mask = Image.new('L', (side, side), 0)
        for deg in range(0, 360, 30):
            one = Image.new('L', (side, side), 0)
            one.paste(stamp, (ox + round(2 * r * math.cos(math.radians(deg))), oy + round(2 * r * math.sin(math.radians(deg)))))
            mask = ImageChops.add(mask, one)
        under = Image.new('L', (side, side), 0)
        under.paste(alpha, (ox, oy))
        res = Image.new('RGBA', (side, side), (0, 0, 0, 0))
        res.putalpha(ImageChops.lighter(mask, under))
        res.alpha_composite(im, (ox, oy))
        res.save(out)
    return out


def marker(x, y, src, size=22, r=2, icon=None):
    """Status icon on a picture, drawn the way the game draws an installed item on an industry picture: the
    icon itself with a 2 px outline of its own shape in black, no cell or box behind it. x, y, size are the
    marker's box; the outline lies around it. icon: the drawn size of the icon inside that box, on the same
    centre, for an icon that fills its whole sprite. The quest (!) does and is drawn at 16 px, so it reads as
    large as an icon whose picture covers about 16 of its 22 px. Needs Pillow, else no outline."""
    if icon is None:
        icon = 16 if src == QUEST else size
    d = (size - icon) // 2
    x, y, size = x + d, y + d, icon
    if not os.path.isfile(src):
        return img(x, y, size, size, src)
    try:
        out = _outlined(src, size, r)
    except ImportError:
        return img(x, y, size, size, src)
    return img(x - r - 1, y - r - 1, size + 2 * r + 2, size + 2 * r + 2, out)


TILE_W, TILE_H = 86, 144



def tile(x, y, src, name, mark=None, level='', icon=None, selected=False, enabled=True):
    """Picture tile for a grid of things with a face (86 x 144, six in a 560 px list): a 64 px picture, one
    marker hanging over its top right corner, the name, one small level line (level: html in tile
    coordinates, drawn at y 91, 11 px high), a short rule and one 26 px icon under it. Everything else
    belongs to the card of the selected tile."""
    i = portrait(11, 5, 64, src)
    if mark:
        i += marker(61, 1, mark)
    i += text(4, 71, 78, fit(name, 78), align='center') + level + shortrule(13, 107, 60, 1)
    if icon:
        i += img(30, 114, 26, 26, icon)
    return row(x, y, TILE_W, TILE_H, i, selected, enabled)


APTITUDE = {'combat': (220, 94, 41), 'leadership': (107, 175, 0), 'technology': (0, 121, 216), 'industry': (175, 135, 0)}


def skill(x, y, size, src, aptitude='combat', elite=False, known=True):
    """Skill icon the way the game draws it in officer lists and skill panels: a black cell, the icon 4 px in,
    a frame of two 1 px rings with 3 px cut corners in the aptitude colour (mixed 35 % toward pale cyan),
    elite as the aptitude's overlay sprite with three stars on the lower edge. size is the cell:
    40 (32 px icon), 56 (48) or 72 (64). known False: a skill not learned, dim with one ring."""
    c = mix(APTITUDE.get(aptitude, aptitude), (203, 245, 255), .35)
    o = box(0, 0, size, size, 'background:#000')
    if os.path.isfile(src):
        dim = '' if known else 'filter:brightness(.45) saturate(.6);'
        o += '<img class="a" src="%s" style="left:4px;top:4px;width:%gpx;height:%gpx;%s" alt="">' % (_url(src), size - 8, size - 8, dim)
    else:
        o += img(4, 4, size - 8, size - 8, src)
    rings = ''
    for n in range(2 if known else 1):
        a, b, cut = n + .5, size - n - .5, 3 - n
        pts = '%g,%g %g,%g %g,%g %g,%g %g,%g %g,%g %g,%g %g,%g' % (a + cut, a, b - cut, a, b, a + cut, b, b - cut, b - cut, b, a + cut, b, a, b - cut, a, a + cut)
        rings += '<polygon points="%s" fill="none" stroke="%s" stroke-opacity="%g"/>' % (pts, rgb(c), .7 if known else .35)
    o += '<svg class="a" style="left:0;top:0" width="%d" height="%d">%s</svg>' % (size, size, rings)
    if known:
        g = 'linear-gradient(%s,transparent,' + rgb(c, .8) + ',transparent)'
        for bx, by, bw, bh, d in ((0, 0, 2, size, 'to bottom'), (size - 2, 0, 2, size, 'to bottom'), (0, 0, size, 2, 'to right'), (0, size - 2, size, 2, 'to right')):
            o += box(bx, by, bw, bh, 'background:%s;mix-blend-mode:screen' % (g % d))
    if elite:
        sfx = '' if size >= 72 else ('48' if size >= 56 else '32')
        ov = sample('skills/elite_%s%s.png' % (aptitude, sfx))
        if not os.path.isfile(ov):
            ov = game('icons/skills/elite_%s%s.png' % (aptitude, sfx))
        o += img(0, 0, size, size, ov)
    return box(x, y, size, size, '', o)


def cell(caption, html):
    """One captioned cut out for a board that shows several states or variants."""
    return '<div><p class="cap" style="font-size:15px;margin:0 0 6px">%s</p>%s</div>' % (caption, html)


def sidebyside(*cells, gap=24, width=None):
    """Cells next to each other; with width they wrap."""
    w = 'flex-wrap:wrap;width:%dpx;' % width if width else ''
    return '<div style="display:flex;%sgap:%dpx;align-items:flex-start">%s</div>' % (w, gap, ''.join(cells))


def compare(before, after, names=('Before', 'After')):
    """Head to head: the current state and the proposal of the same view next to each other. Use it for every
    change to existing UI, one pair per view, instead of all befores followed by all afters."""
    return sidebyside(cell(names[0], before), cell(names[1], after))


def experiment(problem, now, variants, recommend=None, reason=''):
    """One experiment round on one board: the complaint, the current state, then two to four variants that
    differ in kind, drawn with the same data. variants: (name, changed, html, cost). name: two to four words
    that say the idea; changed: the kind of change ('other owner: the bar'); cost: what the variant gives up.
    recommend: index from 0; reason: one sentence from what the element is, not from taste."""
    if not 2 <= len(variants) <= 4:
        raise ValueError('an experiment round shows two to four variants')
    foot = '<div class="notes" style="width:0;min-width:100%%;margin-top:8px">%s</div>'
    cells = [cell('Now', now + foot % ('<b>Problem:</b> %s' % problem))]
    for n, v in enumerate(variants):
        name, changed, html, cost = v
        if not cost:
            raise ValueError('variant %d has no cost line' % (n + 1))
        mark = ' <span style="color:%s;font-weight:400">recommended</span>' % HL if n == recommend else ''
        cells.append(cell('%d. %s%s' % (n + 1, name, mark), html + foot % ('<b>Changed:</b> %s<br><b>Costs:</b> %s' % (changed, cost))))
    return cells, ('<b>Recommended: %d. %s.</b> %s' % (recommend + 1, variants[recommend][0], reason) if recommend is not None else '')


def textlist(rows, title='Player-facing text'):
    """Every text the player will read that this mockup adds or changes, for approval: (where, text).
    Labels, tooltips, reason lines, popup texts, empty state lines. Put it on its own board."""
    body = ''.join('<tr><td style="padding:3px 16px 3px 0;color:#9fb0b8;vertical-align:top;white-space:nowrap">%s</td>'
                   '<td style="padding:3px 0;color:#e6eef2">%s</td></tr>' % r for r in rows)
    return '<div class="notes" style="width:900px"><h4>%s</h4><table style="border-collapse:collapse;font:14px/1.5 \'Segoe UI\',Helvetica,Arial,sans-serif">%s</table></div>' % (title, body)


def dialog(text_html, options, panel_inner, panel_w=600, panel_h=560, w=1240, h=640):
    """The stock interaction dialog with a custom side panel: prose and numbered options on the left with no
    chrome, the panel (VisualPanelAPI.showCustomPanel) on the right. options: (label, kind) with kind
    None, 'story' or 'off'."""
    tx = 20
    tw = w - panel_w - 60
    o = '<div class="a ins15" style="left:%gpx;top:20px;width:%gpx;color:%s">%s</div>' % (tx, tw, TEXT, text_html)
    oy = h - 30 - len(options) * 26
    for i, (lab, kind) in enumerate(options):
        col = {'story': STORY, 'off': GRAY}.get(kind, BASE)
        o += text(tx, oy + i * 26, tw, '%d. %s' % (i + 1, lab), 'orb20', col)
    o += box(w - panel_w - 20, 20, panel_w, panel_h, '', panel_inner)
    return '<div class="screen" style="width:%dpx;height:%dpx">%s</div>' % (w, h, o)



def board(bid, caption, frame_html, notes=''):
    """One mockup with its notes. bid becomes the PNG name."""
    return '<div class="board" id="%s"><div><p class="cap">%s</p>%s</div><div class="notes" style="margin-top:34px">%s</div></div>' % (bid, caption, frame_html, notes)


def notes(*items, title=None):
    t = '<h4>%s</h4>' % title if title else ''
    return t + '<ul>' + ''.join('<li>%s</li>' % i for i in items) + '</ul>'


def page(boards, title='UI mockups', intro=''):
    head = '<div class="notes" style="width:1000px"><p class="cap" style="font-size:22px">%s</p>%s</div>' % (title, intro)
    return '<!doctype html><html lang="en"><head><meta charset="utf-8"><title>%s</title><style>%s</style></head><body><div class="page">%s%s</div></body></html>' % (
        title, css(), head, ''.join(boards))
