#!/bin/bash
# Compares two golden-master output directories byte by byte.
#   ./compare.sh <dirA> <dirB>
# Excluded: runs/timing.jsonl (wall-clock times) and FORMAT.md (hand-written).
# Gzipped trajectories are compared both as files and decompressed.
set -uo pipefail
A="$1"; B="$2"; bad=0; n=0
while IFS= read -r rel; do
  case "$rel" in runs/timing.jsonl|FORMAT.md) continue ;; esac
  n=$((n+1))
  if [ ! -f "$B/$rel" ]; then echo "MISSING in B: $rel"; bad=$((bad+1)); continue; fi
  if ! cmp -s "$A/$rel" "$B/$rel"; then
    if [[ "$rel" == *.gz ]] && cmp -s <(gzip -dc "$A/$rel") <(gzip -dc "$B/$rel"); then
      echo "gzip container differs, content identical: $rel"
    else
      echo "DIFFERS: $rel"; bad=$((bad+1))
    fi
  fi
done < <(cd "$A" && find . -type f | sed 's|^\./||' | sort)
while IFS= read -r rel; do
  [ -f "$A/$rel" ] || { case "$rel" in runs/timing.jsonl|FORMAT.md) ;; *) echo "MISSING in A: $rel"; bad=$((bad+1));; esac; }
done < <(cd "$B" && find . -type f | sed 's|^\./||' | sort)
echo "compared $n files, $bad differences"
[ "$bad" -eq 0 ]
