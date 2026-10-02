#!/bin/bash
# Drives the 2001 client's "Structure Generation" tab for a seeded run and records renderings, the
# Logbook, stdout of "Log Annealing Data", the "Summarize Result" tiles and the three log files, for
# comparison with the port's run UI (docs/port/gui-design.md sections 6, 8, 9).
#
#   scripts/legacy-run-shots.sh [outDir] [file.sml] [seed] [slots]
#
# Defaults: target/gui-shots-legacy-run, legacy/original/sml/pinene.sml, seed 1, 1 slot. Needs
# legacy/runnable/classes (legacy/runnable/build.sh) and legacy/harness/classes (legacy/harness/build.sh,
# for the golden "gui" recipe). user.home points at a scratch directory, so the legacy code cannot touch
# the real ~/seneca.* files. No window is shown (hidden frames; apple.awt.UIElement keeps the JVM out of
# the Dock).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}"
OUT="${1:-$HERE/target/gui-shots-legacy-run}"
SML="${2:-$HERE/legacy/original/sml/pinene.sml}"
SEED="${3:-1}"
SLOTS="${4:-1}"
[ -d "$HERE/legacy/runnable/classes/seneca" ] || "$HERE/legacy/runnable/build.sh"
[ -f "$HERE/legacy/harness/classes/seneca/client/GuiRecipe.class" ] || { echo "run legacy/harness/build.sh first" >&2; exit 1; }
CP="$HERE/legacy/runnable/classes:$HERE/legacy/harness/classes"
for j in "$HERE"/legacy/original/jar/*.jar; do CP="$CP:$j"; done
SCRATCH="$(mktemp -d)"
trap 'rm -rf "$SCRATCH"' EXIT
mkdir -p "$OUT"
"$JAVA_HOME/bin/java" -Duser.home="$SCRATCH" -Dapple.awt.UIElement=true -cp "$CP" \
    "$HERE/scripts/gui/LegacyRunShots.java" "$OUT" "$SML" "$SEED" "$SLOTS" > "$SCRATCH/out.txt" 2>&1 || { cat "$SCRATCH/out.txt"; exit 1; }
grep -E '\.png$|^done' "$SCRATCH/out.txt" || true
