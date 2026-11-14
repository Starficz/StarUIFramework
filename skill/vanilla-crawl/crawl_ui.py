"""Crawls vanilla's UI code in the starsector-knowledge skill and tallies how vanilla styles its UI.

    python crawl_ui.py <knowledge skill folder> <out.json>

Reads api/impl (readable vanilla) and obf (decompiled engine). For each UI call it splits the top-level arguments
and counts each argument position's value, keeping up to 3 example files per value.
"""
import collections
import json
import os
import re
import sys

root, out = sys.argv[1], sys.argv[2]
LIVE = json.load(open(sys.argv[3])) if len(sys.argv) > 3 else None
MODE = sys.argv[4] if len(sys.argv) > 4 else 'live'
LIVE_PATHS = {LIVE['files'][c] for c in LIVE['live']} if LIVE else None


def java_files(sub):
    for dp, _, fs in os.walk(os.path.join(root, sub)):
        for f in fs:
            if f.endswith('.java'):
                yield os.path.join(dp, f)


def split_args(s, start):
    """s[start] is just after '('. Returns (list of args, end index) at top level."""
    depth, i, cur, args = 0, start, [], []
    in_str = None
    while i < len(s):
        c = s[i]
        if in_str:
            cur.append(c)
            if c == '\\':
                cur.append(s[i + 1]); i += 2; continue
            if c == in_str:
                in_str = None
        elif c in '"\'':
            in_str = c; cur.append(c)
        elif c in '([{':
            depth += 1; cur.append(c)
        elif c in ')]}':
            if depth == 0:
                args.append(''.join(cur).strip())
                return args, i
            depth -= 1; cur.append(c)
        elif c == ',' and depth == 0:
            args.append(''.join(cur).strip()); cur = []
        else:
            cur.append(c)
        i += 1
    return None, i


def norm(a):
    a = re.sub(r'\s+', ' ', a)
    return a[:80]


CALLS = ['addButton', 'addAreaCheckbox', 'addCheckbox', 'addSectionHeading', 'addPara', 'addTitle', 'beginTable',
         'addTable', 'addSpacer', 'addImage', 'addImages', 'addTextField', 'createUIElement', 'addCustom',
         'addStatModGrid', 'addShipList', 'addSkillPanel', 'addRelationshipBar', 'addProgressBar',
         'setParaFont', 'setTitleFont', 'setButtonFontDefault', 'setButtonFontOrbitron20',
         'setButtonFontOrbitron20Bold', 'setButtonFontOrbitron24', 'setButtonFontOrbitron24Bold',
         'setParaFontDefault', 'setParaFontOrbitron', 'setParaFontVictor14', 'setParaInsigniaLarge',
         'setParaInsigniaVeryLarge', 'setParaSmallInsignia', 'setParaOrbitronLarge', 'setParaOrbitronVeryLarge',
         'setTitleSmallOrbitron', 'setTitleOrbitronLarge', 'setTitleFontColor', 'setBulletedListMode',
         'addSectionHeading', 'showCustomDialog', 'setShowTooltipWhileInactive', 'addTooltipToPrevious',
         'addIconGroup', 'beginIconGroup', 'addPlaythroughDataPanel', 'addEventProgressBar', 'addSkillPanelOneColumn',
         'beginGridFlipped', 'beginGrid', 'addToGrid', 'addGrid', 'createSlider', 'addSlider']
COLOR_RE = re.compile(r'Misc\.get(\w*Color\w*)\(\)|Global\.getSettings\(\)\.getColor\("(\w+)"\)|'
                      r'get(Base|Dark|Bright)UIColor\(\)|Global\.getSettings\(\)\.get(Base|Dark|Bright)PlayerColor\(\)')
CUT_RE = re.compile(r'CutStyle\.(\w+)')
TIP_W_RE = re.compile(r'getTooltipWidth\([^)]*\)\s*\{\s*return\s+([^;]+);')
PAD_DECL_RE = re.compile(r'float\s+(pad|opad|small|smallPad|spad|gap)\s*=\s*([0-9.]+)f?\s*;')

stats = {}
examples = {}
colors = collections.Counter()
color_ex = collections.defaultdict(list)
cuts = collections.Counter()
cut_ex = collections.defaultdict(list)
tipw = collections.Counter()
tipw_ex = collections.defaultdict(list)
pads = collections.Counter()
calls_total = collections.Counter()

for sub in ('api/impl', 'obf'):
    for path in java_files(sub):
        rel = os.path.relpath(path, root).replace('\\', '/')
        if LIVE_PATHS is not None and ((rel in LIVE_PATHS) != (MODE == 'live')):
            continue
        try:
            s = open(path, encoding='utf-8', errors='replace').read()
        except OSError:
            continue
        # the real class name for citing: package + class from the file
        pkg = re.search(r'^package\s+([\w.]+);', s, re.M)
        cls = re.search(r'(?:class|interface|enum)\s+(\w+)', s)
        name = (pkg.group(1) + '.' if pkg else '') + (cls.group(1) if cls else os.path.basename(path))
        for m in COLOR_RE.finditer(s):
            key = m.group(1) or ('settings:' + m.group(2) if m.group(2) else None) or (m.group(3) or m.group(4)) + 'UIColor'
            colors[key] += 1
            if len(color_ex[key]) < 3 and name not in color_ex[key]:
                color_ex[key].append(name)
        for m in CUT_RE.finditer(s):
            cuts[m.group(1)] += 1
            if len(cut_ex[m.group(1)]) < 3 and name not in cut_ex[m.group(1)]:
                cut_ex[m.group(1)].append(name)
        for m in TIP_W_RE.finditer(s):
            v = norm(m.group(1)); tipw[v] += 1
            if len(tipw_ex[v]) < 3: tipw_ex[v].append(name)
        for m in PAD_DECL_RE.finditer(s):
            pads[m.group(1) + '=' + m.group(2)] += 1
        for call in CALLS:
            for m in re.finditer(r'[.\s]' + call + r'\s*\(', s):
                args, _ = split_args(s, m.end())
                if args is None:
                    continue
                calls_total[call] += 1
                st = stats.setdefault(call, collections.defaultdict(collections.Counter))
                ex = examples.setdefault(call, collections.defaultdict(lambda: collections.defaultdict(list)))
                key_n = '%d args' % len(args)
                for i, a in enumerate(args):
                    v = norm(a)
                    st[key_n + ' #' + str(i)][v] += 1
                    if len(ex[key_n + ' #' + str(i)][v]) < 3 and name not in ex[key_n + ' #' + str(i)][v]:
                        ex[key_n + ' #' + str(i)][v].append(name)

result = {
    'calls_total': calls_total.most_common(),
    'colors': [(k, v, color_ex[k]) for k, v in colors.most_common()],
    'cutstyles': [(k, v, cut_ex[k]) for k, v in cuts.most_common()],
    'tooltip_widths': [(k, v, tipw_ex[k]) for k, v in tipw.most_common(30)],
    'pad_declarations': pads.most_common(40),
    'calls': {c: {pos: [(v, n, examples[c][pos][v]) for v, n in cnt.most_common(25)] for pos, cnt in sorted(st.items())}
              for c, st in stats.items()},
}
json.dump(result, open(out, 'w', encoding='utf-8'), indent=1)
print('files done; calls:', dict(calls_total.most_common(15)))
