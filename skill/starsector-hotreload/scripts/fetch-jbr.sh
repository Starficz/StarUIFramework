#!/usr/bin/env bash
# Downloads the JetBrains Runtime this skill runs the game on into the machine folder (jbr-17), checks its SHA-512
# and trims what the game never uses (jmods, sources, debug symbols). Called by hot.sh setup.
# The game ships Java 17 (0.98.5a: Zulu 17.0.10), so the runtime stays on 17: Fast Rendering's --enable-preview and
# the game's own code are built for it.
set -euo pipefail
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
. "$HERE/lib.sh"
hot_root
NAME=jbrsdk-17.0.14-windows-x64-b1367.22
URL="https://cache-redirector.jetbrains.com/intellij-jbr/$NAME.zip"
SHA512=3b5101101a46778c5b1cc572adef03bffceb338284d4e03d2fe70a72fb52fb2e1ee1d2e1c0b548c86e9092f776d3ca910669326531d1a5f8ab4521ad1b6d868c
DEST="$HOT_ROOT_DIR/jbr-17"
if hot_jbr; then echo "Already there: $REPLY"; exit 0; fi
for tool in curl unzip sha512sum; do
    command -v "$tool" >/dev/null 2>&1 || { echo "NO_TOOL: $tool is missing (it comes with Git for Windows)" >&2; exit 2; }
done
TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT
echo "Downloading $URL (one time)"
curl -fsSL -o "$TMP/jbr.zip" "$URL" || { echo "DOWNLOAD_FAILED: $URL" >&2; exit 1; }
got=$(sha512sum "$TMP/jbr.zip"); got=${got%% *}
[[ $got == "$SHA512" ]] || { echo "CHECKSUM_FAILED: got $got" >&2; exit 1; }
unzip -q "$TMP/jbr.zip" -d "$TMP/x"
rm -rf -- "$TMP/x/$NAME/jmods" "$TMP/x/$NAME/lib/src.zip"
find "$TMP/x/$NAME" -name '*.pdb' -delete
rm -rf -- "$DEST.tmp"
mv -- "$TMP/x/$NAME" "$DEST.tmp" && mv -- "$DEST.tmp" "$DEST"
"$DEST/bin/java.exe" -XX:+AllowEnhancedClassRedefinition -version
echo "Installed $DEST"
