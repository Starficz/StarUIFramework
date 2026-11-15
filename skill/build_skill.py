"""Builds the agent skills that ship with StarUI from this checkout: starsector-starui and starsector-hotreload.

    python skill/build_skill.py [--install]

- copies the public API sources (src/org/starficz/starui without patches/, internal/ and layout/) into
  skill/starsector-starui/api/, and the samples into skill/starsector-starui/examples/, so the skill's signatures are
  always this checkout's;
- checks that SKILL.md names the version in mod_info.json;
- packs skill/dist/starsector-starui.skill and skill/dist/starsector-hotreload.skill (zips with the skill folder at
  their root);
- --install copies both into ~/.claude/skills, replacing older copies there.

api/, examples/ and dist/ are build output (git ignored).
"""
import argparse
import json
import os
import re
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKILL = os.path.join(ROOT, "skill", "starsector-starui")
HOT = os.path.join(ROOT, "skill", "starsector-hotreload")
DIST = os.path.join(ROOT, "skill", "dist")
SRC = os.path.join(ROOT, "src", "org", "starficz", "starui")
SAMPLES = os.path.join(ROOT, "src", "org", "starficz", "staruisamples")
INTERNAL_PACKAGES = {"patches", "internal", "layout"}
SKIP = {"__pycache__"}


def fail(msg):
    print("BUILD_FAILED: " + msg, file=sys.stderr)
    sys.exit(1)


def copy_api():
    dest = os.path.join(SKILL, "api", "org", "starficz", "starui")
    shutil.rmtree(os.path.join(SKILL, "api"), ignore_errors=True)
    count = 0
    for dirpath, dirnames, filenames in os.walk(SRC):
        rel = os.path.relpath(dirpath, SRC)
        top = rel.split(os.sep)[0]
        if top in INTERNAL_PACKAGES:
            dirnames[:] = []
            continue
        for name in filenames:
            if name.endswith(".kt"):
                out = os.path.join(dest, rel)
                os.makedirs(out, exist_ok=True)
                shutil.copy2(os.path.join(dirpath, name), out)
                count += 1
    examples = os.path.join(SKILL, "examples")
    shutil.rmtree(examples, ignore_errors=True)
    os.makedirs(examples)
    # the samples with their subfolders (the showcase is examples/showcase/)
    for dirpath, dirnames, filenames in os.walk(SAMPLES):
        dirnames[:] = [d for d in dirnames if d not in SKIP]
        rel = os.path.relpath(dirpath, SAMPLES)
        for name in filenames:
            if name.endswith(".kt"):
                out = os.path.join(examples, rel)
                os.makedirs(out, exist_ok=True)
                shutil.copy2(os.path.join(dirpath, name), out)
    return count


def check_version():
    with open(os.path.join(ROOT, "mod_info.json"), encoding="utf-8") as f:
        info = json.loads("\n".join(l for l in f.read().splitlines() if not l.lstrip().startswith("#")))
    version = info["version"]
    with open(os.path.join(SKILL, "SKILL.md"), encoding="utf-8") as f:
        text = f.read()
    if not re.search(r"\*\*StarUI " + re.escape(version) + r"\*\*", text):
        fail("SKILL.md does not name StarUI %s (mod_info.json); update its Version section" % version)
    return version


MAX_DESCRIPTION = 1024  # claude.ai's skill import refuses a longer frontmatter description


def check_description(folder):
    with open(os.path.join(folder, "SKILL.md"), encoding="utf-8") as f:
        text = f.read()
    m = re.match(r"---\n(.*?)\n---", text.replace("\r", ""), re.S)
    d = m and re.search(r"^description:\s*(.*?)(?=^\w+:|\Z)", m.group(1), re.S | re.M)
    if not d:
        fail("%s/SKILL.md has no description in its frontmatter" % os.path.basename(folder))
    n = len(d.group(1).strip())
    if n > MAX_DESCRIPTION:
        fail("%s/SKILL.md: the description is %d characters, the import allows %d"
             % (os.path.basename(folder), n, MAX_DESCRIPTION))


def pack(folder, out, skip=frozenset()):
    name = os.path.basename(folder.rstrip("/\\"))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with zipfile.ZipFile(out + ".tmp", "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, dirnames, filenames in os.walk(folder):
            dirnames[:] = sorted(d for d in dirnames if d not in skip)
            for f in sorted(filenames):
                full = os.path.join(dirpath, f)
                z.write(full, os.path.join(name, os.path.relpath(full, folder)))
    os.replace(out + ".tmp", out)
    return os.path.getsize(out)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--install", action="store_true")
    a = p.parse_args()
    version = check_version()
    check_description(SKILL)
    check_description(HOT)
    n = copy_api()
    out = os.path.join(DIST, "starsector-starui.skill")
    size = pack(SKILL, out, SKIP)
    print("starsector-starui %s: %d API sources, %s (%d KB)" % (version, n, out, size // 1024))
    hout = os.path.join(DIST, "starsector-hotreload.skill")
    print("starsector-hotreload: %s (%d KB)" % (hout, pack(HOT, hout, SKIP) // 1024))
    if a.install:
        for folder in (SKILL, HOT):
            dest = os.path.join(os.path.expanduser("~"), ".claude", "skills", os.path.basename(folder))
            shutil.rmtree(dest, ignore_errors=True)
            shutil.copytree(folder, dest, ignore=shutil.ignore_patterns(*SKIP))
            print("installed " + dest)


if __name__ == "__main__":
    main()
