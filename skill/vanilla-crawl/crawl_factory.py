"""Calls of the engine's button factory (ui.Q in 0.98.5a-RC4, found by its CutStyle overloads) in live code:
how often each overload is used, the colours passed to the ones that take colours, fonts, cuts, and labels.

    python crawl_factory.py <knowledge skill> <live.json>
"""
import collections
import json
import os
import re
import sys


def split_args(s, start):
    depth, i, cur, args, in_str = 0, start, [], [], None
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
                args.append(''.join(cur).strip()); return args
            depth -= 1; cur.append(c)
        elif c == ',' and depth == 0:
            args.append(''.join(cur).strip()); cur = []
        else:
            cur.append(c)
        i += 1
    return None


root = sys.argv[1]
live = json.load(open(sys.argv[2]))
paths = [live['files'][c] for c in live['live'] if live['files'][c].startswith('obf/')]
by_n = collections.Counter()
colour_pairs = collections.Counter()
colour_ex = collections.defaultdict(list)
fonts = collections.Counter()
cuts = collections.Counter()
for rel in sorted(set(paths)):
    s = open(os.path.join(root, rel), encoding='utf-8', errors='replace').read()
    if not re.search(r'import com\.fs\.starfarer\.ui\.Q;', s) and not rel.startswith('obf/ui/'):
        continue
    for m in re.finditer(r'\bQ\.o00000\(', s):
        args = split_args(s, m.end())
        if not args:
            continue
        n = len(args)
        by_n[n] += 1
        for a in args:
            if a.startswith('"graphics/fonts/'):
                fonts[a] += 1
            cm = re.match(r'CutStyle\.(\w+)', a)
            if cm:
                cuts[cm.group(1)] += 1
        if n == 7:   # text, font, bg, fg, alignment, cut, listener
            key = (re.sub(r'\s+', ' ', args[2])[:70], re.sub(r'\s+', ' ', args[3])[:70])
            colour_pairs[key] += 1
            if len(colour_ex[key]) < 4:
                colour_ex[key].append('%s %s' % (rel, args[0][:40]))
print('overloads by argument count:', by_n.most_common())
print('fonts:', fonts.most_common())
print('cuts:', cuts.most_common())
print('explicit colours (bg, text):')
for k, v in colour_pairs.most_common(30):
    print('  %3d  bg=%s  text=%s   e.g. %s' % (v, k[0], k[1], colour_ex[k][:3]))


# resolve the colour arguments that are fields or locals of the same file to what they are assigned
def resolve_in(rel, expr):
    s = open(os.path.join(root, rel), encoding='utf-8', errors='replace').read()
    name = expr.split('.')[-1]
    name = re.sub(r'^this\.', '', name)
    hits = re.findall(r'(?:Color\s+|\.)' + re.escape(name) + r'\s*=\s*([^;]{1,120});', s)
    return sorted(set(re.sub(r'\s+', ' ', h) for h in hits))[:3]


print('\nresolved:')
seen = set()
for k, v in colour_pairs.most_common(40):
    rel = colour_ex[k][0].split(' ')[0]
    for e in k:
        if (rel, e) in seen:
            continue
        seen.add((rel, e))
        print('  %-48s %-40s -> %s' % (rel, e[:40], resolve_in(rel, e)))
