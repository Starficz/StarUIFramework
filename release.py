"""Packs the mod for release: release/StarUIFramework.zip, with a StarUIFramework/ folder at its root. The name has
no version: the version file's download link is releases/latest/download/StarUIFramework.zip, so every release
uploads an asset of that name (and is not marked as a GitHub prerelease, which "latest" skips).

    python release.py

What goes in is the mod a player installs, nothing else: mod_info.json, the version file, LICENSE, README.md,
CHANGELOG.md, icon.ico (the mod managers' icon), data/, jars/ (the IntelliJ-built jar and the sources jar),
licenses/ (the LGPL, GPL and CC0 texts) and src/. Left out: .idea, out/, docs/, skill/, native-smoke/, AGENTS.md
and the build scripts.

Before packing it rebuilds jars/StarUIFramework-sources.jar (build_sources_jar.py), and stops when a source file is
newer than jars/StarUIFramework.jar: rebuild the jar in IntelliJ first.
"""
import json
import os
import sys
import zipfile

import build_sources_jar

ROOT = os.path.dirname(os.path.abspath(__file__))
MOD_FOLDER = "StarUIFramework"
FILES = ["mod_info.json", "staruiframework.version", "LICENSE", "README.md", "CHANGELOG.md", "icon.ico"]
FOLDERS = ["data", "jars", "licenses", "src"]
SKIP_DIRS = {"__pycache__", ".idea"}
SKIP_SUFFIXES = (".tmp", ".pyc", ".iml")
JAR = os.path.join(ROOT, "jars", "StarUIFramework.jar")


def fail(msg):
    print("RELEASE_FAILED: " + msg, file=sys.stderr)
    sys.exit(1)


def version():
    with open(os.path.join(ROOT, "mod_info.json"), encoding="utf-8") as f:
        text = "\n".join(l for l in f.read().splitlines() if not l.lstrip().startswith("#"))
    return json.loads(text)["version"]


def walk(folder):
    for dirpath, dirnames, filenames in os.walk(os.path.join(ROOT, folder)):
        dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
        for name in sorted(filenames):
            if not name.endswith(SKIP_SUFFIXES):
                yield os.path.join(dirpath, name)


def check_jar_fresh():
    if not os.path.isfile(JAR):
        fail("jars/StarUIFramework.jar is missing: build it in IntelliJ")
    built = os.path.getmtime(JAR)
    newer = [p for p in walk("src") if p.endswith((".kt", ".java")) and os.path.getmtime(p) > built]
    if newer:
        fail("%d source file(s) changed after jars/StarUIFramework.jar was built, e.g. %s: rebuild it in IntelliJ"
             % (len(newer), os.path.relpath(newer[0], ROOT)))


def main():
    check_jar_fresh()
    build_sources_jar.main()
    entries = [os.path.join(ROOT, f) for f in FILES]
    for f in entries:
        if not os.path.isfile(f):
            fail("missing " + os.path.relpath(f, ROOT))
    for folder in FOLDERS:
        entries.extend(walk(folder))

    out_dir = os.path.join(ROOT, "release")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, MOD_FOLDER + ".zip")
    tmp = out + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        for path in entries:
            z.write(path, MOD_FOLDER + "/" + os.path.relpath(path, ROOT).replace(os.sep, "/"))
    os.replace(tmp, out)
    print("%s (%s): %d files (%d KB)" % (out, version(), len(entries), os.path.getsize(out) // 1024))


if __name__ == "__main__":
    main()
