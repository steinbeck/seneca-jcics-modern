#!/bin/bash
# Renders the 2001 client's dataset window (every tab and sub-tab) to PNG, offscreen, for comparison
# with the port's GUI renderings (docs/port/gui-design.md, "Comparing with the legacy GUI").
#
#   scripts/legacy-gui-shots.sh [outDir] [file.sml]
#
# Defaults: target/gui-shots-legacy, legacy/original/sml/pinene.sml. Needs legacy/runnable/classes
# (legacy/runnable/build.sh). user.home points at a scratch directory, so the legacy code cannot touch
# the real ~/seneca.* files. No window is shown (apple.awt.UIElement keeps the JVM out of the Dock).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}"
OUT="${1:-$HERE/target/gui-shots-legacy}"
SML="${2:-$HERE/legacy/original/sml/pinene.sml}"
[ -d "$HERE/legacy/runnable/classes/seneca" ] || "$HERE/legacy/runnable/build.sh"
CP="$HERE/legacy/runnable/classes"
for j in "$HERE"/legacy/original/jar/*.jar; do CP="$CP:$j"; done
SCRATCH="$(mktemp -d)"
trap 'rm -rf "$SCRATCH"' EXIT
mkdir -p "$OUT"
"$JAVA_HOME/bin/java" -Duser.home="$SCRATCH" -Dapple.awt.UIElement=true -cp "$CP" \
    "$HERE/scripts/gui/LegacyShots.java" "$OUT" "$SML" > "$SCRATCH/out.txt" 2>&1 || { cat "$SCRATCH/out.txt"; exit 1; }
grep '\.png$' "$SCRATCH/out.txt" || true
