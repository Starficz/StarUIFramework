"""Builds jars/StarUIFramework-sources.jar from src/org/starficz/starui, for IDEs to show StarUI's KDoc.

    python build_sources_jar.py

Run it after rebuilding jars/StarUIFramework.jar, so the two match. The sources jar is not in mod_info.json's jars:
the game never loads it. In IntelliJ, users attach it to the StarUIFramework library (Project Structure, Libraries,
the library, "+", the jar) and get the KDoc on hover and in Quick Documentation, and the sources on navigation.
The samples (org.starficz.staruisamples) are left out.
"""
import os
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "src")
PACKAGE = os.path.join("org", "starficz", "starui")
OUT = os.path.join(ROOT, "jars", "StarUIFramework-sources.jar")
FIXED_TIME = (2026, 1, 1, 0, 0, 0)   # the same sources give the same jar


def main():
    files = []
    for dirpath, dirnames, filenames in os.walk(os.path.join(SRC, PACKAGE)):
        dirnames.sort()
        for name in sorted(filenames):
            if name.endswith((".kt", ".java")):
                files.append(os.path.join(dirpath, name))
    tmp = OUT + ".tmp"
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(zipfile.ZipInfo("META-INF/MANIFEST.MF", FIXED_TIME), "Manifest-Version: 1.0\r\n\r\n")
        z.writestr(zipfile.ZipInfo("LICENSE", FIXED_TIME), open(os.path.join(ROOT, "LICENSE"), "rb").read())
        for path in files:
            info = zipfile.ZipInfo(os.path.relpath(path, SRC).replace(os.sep, "/"), FIXED_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            with open(path, "rb") as f:
                z.writestr(info, f.read())
    os.replace(tmp, OUT)
    print("%s: %d sources (%d KB)" % (OUT, len(files), os.path.getsize(OUT) // 1024))


if __name__ == "__main__":
    main()
