#!/bin/bash
# Compares a port recording (`seneca record <smlDir> <dir> --manifest legacy/harness/summarize.py`)
# with the legacy golden master, as legacy/harness/compare.sh does, plus the two documented differences
# of the port (docs/port/DEVIATIONS.md DV-30 and the last section):
#   - PROVENANCE.json describes the legacy inputs and is not produced by the port: skipped;
#   - crash.at names the top stack frame (legacy: compchem.SetOfRingsFinder.breakBond:302): files that
#     differ are compared again with the "at" field of their crash objects removed.
# Also excluded, as in compare.sh: runs/timing.jsonl (wall-clock times) and FORMAT.md.
#   ./scripts/compare-golden.sh [goldenDir] <portDir>     (goldenDir defaults to golden/)
# Exit 0 iff every file matches.
set -uo pipefail
if [ $# -eq 1 ]; then A="$(cd "$(dirname "$0")/.." && pwd)/golden"; B="$1"; else A="$1"; B="$2"; fi
skip() { case "$1" in runs/timing.jsonl|FORMAT.md|PROVENANCE.json) return 0 ;; *) return 1 ;; esac; }
content() { if [[ "$1" == *.gz ]]; then gzip -dc "$1"; else cat "$1"; fi; }
noat() { content "$1" | sed -E 's/,"at":("[^"]*"|null)\}/}/g'; }
bad=0; n=0; crashat=0
while IFS= read -r rel; do
  skip "$rel" && continue
  n=$((n+1))
  if [ ! -f "$B/$rel" ]; then echo "MISSING in port: $rel"; bad=$((bad+1)); continue; fi
  cmp -s "$A/$rel" "$B/$rel" && continue
  if [[ "$rel" == *.gz ]] && cmp -s <(gzip -dc "$A/$rel") <(gzip -dc "$B/$rel"); then
    echo "gzip container differs, content identical: $rel"; continue
  fi
  if cmp -s <(noat "$A/$rel") <(noat "$B/$rel"); then crashat=$((crashat+1)); continue; fi
  echo "DIFFERS: $rel"; bad=$((bad+1))
done < <(cd "$A" && find . -type f | sed 's|^\./||' | sort)
while IFS= read -r rel; do
  skip "$rel" && continue
  [ -f "$A/$rel" ] || { echo "MISSING in golden: $rel"; bad=$((bad+1)); }
done < <(cd "$B" && find . -type f | sed 's|^\./||' | sort)
echo "compared $n files: $bad differences ($crashat files identical except crash.at)"
[ "$bad" -eq 0 ]
