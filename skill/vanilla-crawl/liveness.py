"""Marks which vanilla classes are reachable, so a style count leaves out code nothing uses any more.

    python liveness.py <knowledge skill folder> <out.json>

Roots: the launcher, every class named in the game's data files (CSV, JSON, settings). Edges: explicit imports,
same-package simple names, wildcard imports, and fully qualified names in code or strings. Output: the live and
dead class lists, with each source file's path.
"""
import json
import os
import re
import sys
from collections import defaultdict, deque

root, out = sys.argv[1], sys.argv[2]
files = {}          # fqcn -> path
info = {}           # fqcn -> (pkg, imports, wild, tokens, fqns)
by_pkg = defaultdict(dict)

TOKEN = re.compile(r'\b[A-Za-z_$][\w$]*\b')
FQN = re.compile(r'\bcom\.fs\.[\w.$]+')

for sub, prefix in (('api', 'com.fs.starfarer.api'), ('obf', 'com.fs.starfarer')):
    for dp, _, fs in os.walk(os.path.join(root, sub)):
        for f in fs:
            if not f.endswith('.java'):
                continue
            p = os.path.join(dp, f)
            s = open(p, encoding='utf-8', errors='replace').read()
            pm = re.search(r'^package\s+([\w.]+);', s, re.M)
            pkg = pm.group(1) if pm else ''
            # top-level type names in this file (decompiled files hold one; inner types travel with it)
            names = re.findall(r'^(?:public\s+|final\s+|abstract\s+)*(?:class|interface|enum|@interface)\s+(\w+)', s, re.M)
            if not names:
                continue
            imports = {}
            wild = []
            for im in re.findall(r'^import\s+(?:static\s+)?([\w.$*]+);', s, re.M):
                if im.endswith('.*'):
                    wild.append(im[:-2])
                else:
                    imports[im.rsplit('.', 1)[-1]] = im
            body = re.sub(r'^import .*$', '', s, flags=re.M)
            tokens = set(TOKEN.findall(body))
            fqns = set(FQN.findall(body))
            for n in names:
                fq = (pkg + '.' if pkg else '') + n
                files[fq] = os.path.relpath(p, root).replace('\\', '/')
                info[fq] = (pkg, imports, wild, tokens, fqns)
                by_pkg[pkg][n] = fq


def resolve(fq_from):
    pkg, imports, wild, tokens, fqns = info[fq_from]
    out = set()
    for t in tokens:
        if t in imports and imports[t] in files:
            out.add(imports[t])
        elif t in by_pkg[pkg]:
            out.add(by_pkg[pkg][t])
        else:
            for w in wild:
                if t in by_pkg.get(w, {}):
                    out.add(by_pkg[w][t])
    for q in fqns:
        q = q.rstrip('.')
        while q and q not in files and '.' in q:
            q = q.rsplit('.', 1)[0]     # a nested class or a member: walk up to the file's class
        if q in files:
            out.add(q)
    out.discard(fq_from)
    return out


roots = {'com.fs.starfarer.StarfarerLauncher'}
data_names = set()
for dp, _, fs in os.walk(os.path.join(root, 'data')):
    for f in fs:
        if f.endswith(('.csv', '.json', '.skill', '.system', '.faction', '.ship', '.wpn', '.variant', '.txt', '.data')):
            try:
                data_names.update(FQN.findall(open(os.path.join(dp, f), encoding='utf-8', errors='replace').read()))
            except OSError:
                pass
for q in data_names:
    q = q.rstrip('.')
    while q and q not in files and '.' in q:
        q = q.rsplit('.', 1)[0]
    if q in files:
        roots.add(q)

# rules.csv calls rule commands by simple name (the game looks them up in the rulecmd package)
rules = os.path.join(root, 'data', 'campaign', 'rules.csv')
if os.path.exists(rules):
    words = set(TOKEN.findall(open(rules, encoding='utf-8', errors='replace').read()))
    for n, fq in by_pkg.get('com.fs.starfarer.api.impl.campaign.rulecmd', {}).items():
        if n in words:
            roots.add(fq)

live = set()
queue = deque(r for r in roots if r in files)
live.update(queue)
while queue:
    c = queue.popleft()
    for d in resolve(c):
        if d not in live:
            live.add(d); queue.append(d)

dead = sorted(set(files) - live)
json.dump({'roots': len(roots), 'live': sorted(live), 'dead': dead, 'files': files}, open(out, 'w'), indent=0)
print('classes', len(files), 'roots', len(roots), 'live', len(live), 'dead', len(dead))
for d in dead:
    if re.search(r'ui|codex|tutorial|panel|screen|dialog|button|intel', d, re.I):
        print('  dead:', d, files[d])
