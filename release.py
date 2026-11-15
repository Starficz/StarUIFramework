"""Packs the mod for release: release/StarUILib.zip, with a StarUILib/ folder at its root. The name has
no version: the version file's download link is releases/latest/download/StarUILib.zip, so every release
uploads an asset of that name (and is not marked as a GitHub prerelease, which "latest" skips).

    python release.py

What goes in is the mod a player installs, nothing else: mod_info.json, the version file, LICENSE, README.md,
CHANGELOG.md, icon.ico (the mod managers' icon), data/, jars/, licenses/ (the LGPL, GPL and CC0 texts) and src/.
Left out: .idea, out/, docs/, skill/, native-smoke/, AGENTS.md and the build scripts.

The zip's jars/StarUILib.jar is the IntelliJ-built jar with the library's sources (src/org/starficz/starui,
the KDoc) added next to its classes, so IDEs find the KDoc in the jar users already depend on. The committed jar is
left as IntelliJ built it. The samples' sources stay out of the jar (they are in src/).

Stops when a source file is newer than jars/StarUILib.jar: rebuild the jar in IntelliJ first.
"""
import io
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
MOD_FOLDER = "StarUILib"
FILES = ["mod_info.json", "staruilib.version", "LICENSE", "README.md", "CHANGELOG.md", "icon.ico"]
FOLDERS = ["data", "jars", "licenses", "src"]
SKIP_DIRS = {"__pycache__", ".idea"}
SKIP_SUFFIXES = (".tmp", ".pyc", ".iml")
JAR = os.path.join(ROOT, "jars", "StarUILib.jar")
SRC = os.path.join(ROOT, "src")
LIBRARY_PACKAGE = os.path.join("org", "starficz", "starui")


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
        fail("jars/StarUILib.jar is missing: build it in IntelliJ")
    built = os.path.getmtime(JAR)
    newer = [p for p in walk("src") if p.endswith((".kt", ".java")) and os.path.getmtime(p) > built]
    if newer:
        fail("%d source file(s) changed after jars/StarUILib.jar was built, e.g. %s: rebuild it in IntelliJ"
             % (len(newer), os.path.relpath(newer[0], ROOT)))


def jar_with_sources():
    """The IntelliJ jar's entries, then the library's sources at their package paths. Returns (bytes, count)."""
    buf = io.BytesIO()
    count = 0
    with zipfile.ZipFile(JAR) as src_jar, zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as out:
        names = set()
        for info in src_jar.infolist():
            out.writestr(info, src_jar.read(info.filename))
            names.add(info.filename)
        for dirpath, dirnames, filenames in os.walk(os.path.join(SRC, LIBRARY_PACKAGE)):
            dirnames.sort()
            for name in sorted(filenames):
                if not name.endswith((".kt", ".java")):
                    continue
                path = os.path.join(dirpath, name)
                entry = os.path.relpath(path, SRC).replace(os.sep, "/")
                if entry in names:
                    fail("jars/StarUILib.jar already has " + entry)
                out.write(path, entry)
                count += 1
    return buf.getvalue(), count


def main():
    check_jar_fresh()
    entries = [os.path.join(ROOT, f) for f in FILES]
    for f in entries:
        if not os.path.isfile(f):
            fail("missing " + os.path.relpath(f, ROOT))
    for folder in FOLDERS:
        entries.extend(walk(folder))

    jar_bytes, sources = jar_with_sources()
    out_dir = os.path.join(ROOT, "release")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, MOD_FOLDER + ".zip")
    tmp = out + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        for path in entries:
            name = MOD_FOLDER + "/" + os.path.relpath(path, ROOT).replace(os.sep, "/")
            if os.path.normcase(path) == os.path.normcase(JAR):
                z.writestr(name, jar_bytes)
            else:
                z.write(path, name)
    os.replace(tmp, out)
    print("%s (%s): %d files (%d KB), %d sources in the jar"
          % (out, version(), len(entries), os.path.getsize(out) // 1024, sources))


if __name__ == "__main__":
    main()
