"""Build mockup boards: python3 build.py <boards.py> [--out DIR] [--no-shots]

<boards.py> defines BOARDS, a list of functions that each return kit.board(...), and optionally TITLE and INTRO.
Writes DIR/mock.html, then DIR/<board id>.png and DIR/<board id>@2x.png (needs node with playwright), and
prints layout warnings: text that does not fit its box, text that overlaps other text, text outside the frame.
"""
import importlib.util
import os
import subprocess
import sys


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if '--out' in sys.argv:
        out_arg = sys.argv[sys.argv.index('--out') + 1]
        args = [a for a in args if a != out_arg]
    else:
        out_arg = None
    if not args:
        sys.exit(__doc__)

    src = os.path.abspath(args[0])
    out = os.path.abspath(out_arg) if out_arg else os.path.join(os.path.dirname(src), 'mock_out')
    os.makedirs(out, exist_ok=True)
    os.environ['MOCK_OUT'] = out

    here = os.path.dirname(os.path.abspath(__file__))
    sys.path.insert(0, here)
    sys.path.insert(0, os.path.dirname(src))
    import kit

    spec = importlib.util.spec_from_file_location('boards', src)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    boards = [b() if callable(b) else b for b in mod.BOARDS]
    page = kit.page(boards, getattr(mod, 'TITLE', 'UI mockups'), getattr(mod, 'INTRO', ''))
    path = os.path.join(out, 'mock.html')
    with open(path, 'w', encoding='utf-8') as f:
        f.write(page)
    print('wrote', path, '(%d boards)' % len(boards))

    if kit.GAME is None:
        print('note: game graphics not found. Set SS_GRAPHICS to the graphics folder of the starsector-knowledge skill or of the game.')
    for m in sorted(set(kit.MISSING)):
        print('MISSING IMAGE', m)

    if '--no-shots' in sys.argv:
        return
    try:
        r = subprocess.run(['node', os.path.join(here, 'shot.js'), out], capture_output=True, text=True, timeout=300)
    except (OSError, subprocess.TimeoutExpired) as e:
        print('no screenshots (%s). Open mock.html in a browser instead.' % e)
        return
    print(r.stdout.strip())
    if r.returncode != 0:
        print(r.stderr.strip()[-800:])
        print('no screenshots. Open mock.html in a browser instead.')


if __name__ == '__main__':
    main()
