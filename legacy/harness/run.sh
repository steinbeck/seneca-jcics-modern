#!/bin/bash
# Records the golden master of the legacy SENECA code (see golden/FORMAT.md).
#
#   ./run.sh [OUT]          default OUT = <repo>/golden
#
# Environment (defaults in brackets):
#   STAGES          subset of "echo scores ref traj full"                               [all five]
#   DATASETS        dataset keys                          [every legacy/original/sml/*.sml + $VARIANTS]
#   VARIANTS        extra dataset keys "<name>@<variant>" (see GuiRecipe)               ["polycarpol@hblock"]
#   SEEDS           seeds for trajectories and complete runs                            ["1 2 3"]
#   STEPS           trajectory length                                                   [5000]
#   TRAJ_PROFILES   judge profiles of the trajectories                                  ["gui paper"]
#   EXTRA_TRAJ      datasets that get trajectories for EXTRA_SEEDS as well              ["pinene eurabidiol"]
#   EXTRA_SEEDS                                                                        ["4 5 6 7 8 9 10"]
#   FULL_PROFILES   judge profiles of the complete runs                                 ["gui paper"]
#   VARIANT_PROFILES  complete-run profiles for the VARIANTS keys                       ["paper"]
#   MANUAL_FULL     datasets that also get complete runs in profile "manual"            ["pinene preconen eurabidiol"]
#   ITER_CAP        iteration cap of a complete run (deterministic; status iteration_cap)  [5000000]
#   WALL_CAP        wall-clock safety net per complete run in s; reaching it is a harness
#                   failure (status timeout, exit code 5), not golden data             [3600]
#   JOBS            parallel JVMs                          [number of performance cores]
#   JAVA_HOME       [/opt/homebrew/opt/openjdk, JDK 25]
#   GM_NO_BUILD     if set, skip build.sh (only for a second recording started while another
#                   one is running: build.sh deletes and recreates the class directories)
#
# Every (dataset, stage, profile, seed) runs in its own JVM (SpecMLReader keeps static state, and
# the random source seneca.structgen.Rng is JVM-global). Console chatter of the legacy code goes
# to legacy/harness/work/logs/<basename of OUT>/ (not part of the golden data).
# build.sh always recompiles legacy/runnable/src (into legacy-classes/), and provenance.py writes
# the SHA-256 of every input into OUT/PROVENANCE.json.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
OUT="${1:-$REPO/golden}"
SML="$REPO/legacy/original/sml"
REFS="$HERE/references.tsv"
STAGES="${STAGES:-echo scores ref traj full}"
VARIANTS="${VARIANTS-polycarpol@hblock}"
SEEDS="${SEEDS:-1 2 3}"
STEPS="${STEPS:-5000}"
TRAJ_PROFILES="${TRAJ_PROFILES:-gui paper}"
EXTRA_TRAJ="${EXTRA_TRAJ-pinene eurabidiol}"
EXTRA_SEEDS="${EXTRA_SEEDS-4 5 6 7 8 9 10}"
FULL_PROFILES="${FULL_PROFILES:-gui paper}"
VARIANT_PROFILES="${VARIANT_PROFILES-paper}"
MANUAL_FULL="${MANUAL_FULL-pinene preconen eurabidiol}"
ITER_CAP="${ITER_CAP:-5000000}"
WALL_CAP="${WALL_CAP:-3600}"
JOBS="${JOBS:-$(sysctl -n hw.perflevel0.physicalcpu 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || nproc)}"
if [ -z "${DATASETS:-}" ]; then
  DATASETS=""
  for f in "$SML"/*.sml; do DATASETS="$DATASETS $(basename "$f" .sml)"; done
  DATASETS="$DATASETS $VARIANTS"
fi
LOGS="$HERE/work/logs/$(basename "$OUT")"
export GM_HOME="$HERE/work/home"

[ -n "${GM_NO_BUILD:-}" ] || "$HERE/build.sh" >/dev/null
# a stage that is re-run replaces its whole output directory (no stale files)
for st in $STAGES; do
  case "$st" in
    echo) rm -rf "$OUT/datasets" ;; scores) rm -rf "$OUT/scores" ;; ref) rm -rf "$OUT/references" ;;
    traj) rm -rf "$OUT/trajectories" ;; full) rm -rf "$OUT/runs" ;;
  esac
done
mkdir -p "$OUT/datasets" "$OUT/scores" "$OUT/references" "$OUT/trajectories" "$OUT/runs" "$LOGS" "$GM_HOME"

is_variant() { [[ "$1" == *@* ]]; }
contains() { local x; for x in $2; do [ "$x" = "$1" ] && return 0; done; return 1; }

# one job per line: <stage> <key> <seed> <profile>
JOBFILE="$LOGS/jobs.txt"
: > "$JOBFILE"
for st in $STAGES; do
  for k in $DATASETS; do
    case "$st" in
      echo|scores|ref) echo "$st $k 0 -" >> "$JOBFILE" ;;
      traj)
        is_variant "$k" && continue
        seeds="$SEEDS"; contains "$k" "$EXTRA_TRAJ" && seeds="$SEEDS $EXTRA_SEEDS"
        for pr in $TRAJ_PROFILES; do for s in $seeds; do echo "$st $k $s $pr" >> "$JOBFILE"; done; done ;;
      full)
        profs="$FULL_PROFILES"
        is_variant "$k" && profs="$VARIANT_PROFILES"
        ! is_variant "$k" && contains "$k" "$MANUAL_FULL" && profs="$profs manual"
        for pr in $profs; do for s in $SEEDS; do echo "$st $k $s $pr" >> "$JOBFILE"; done; done ;;
    esac
  done
done
# complete runs are the long ones: start them first
{ grep '^full ' "$JOBFILE" || true; grep -v '^full ' "$JOBFILE" || true; } > "$JOBFILE.sorted"

job() {
  local st="$1" k="$2" s="$3" pr="$4" rc=0
  local f="$SML/${k%%@*}.sml"
  case "$st" in
    echo)   "$HERE/gm.sh" echo "$f" "$k" "$OUT/datasets/$k.echo.jsonl" > "$LOGS/$k.echo.log" 2>&1 || rc=$? ;;
    scores) "$HERE/gm.sh" scores "$f" "$k" "$OUT/scores/$k.scores.jsonl" > "$LOGS/$k.scores.log" 2>&1 || rc=$? ;;
    ref)    "$HERE/gm.sh" ref "$f" "$k" "$REFS" "$OUT/references/$k.ref.jsonl" > "$LOGS/$k.ref.log" 2>&1 || rc=$? ;;
    traj)   local t="$OUT/trajectories/$k.$pr.seed$s.jsonl"
            "$HERE/gm.sh" traj "$f" "$k" "$pr" "$s" "$STEPS" "$t" > "$LOGS/$k.traj.$pr$s.log" 2>&1 || rc=$?
            [ -f "$t" ] && gzip -n -9 -f "$t" ;;
    full)   "$HERE/gm.sh" full "$f" "$k" "$pr" "$s" "$ITER_CAP" "$WALL_CAP" "$REFS" "$OUT/runs/$k.$pr.seed$s.json" \
                "$OUT/runs/timing.jsonl" > "$LOGS/$k.full.$pr$s.log" 2>&1 || rc=$? ;;
  esac
  echo "$st $k $s $pr rc=$rc" >> "$LOGS/status.txt"
  echo "$st $k seed=$s profile=$pr rc=$rc"
}
export -f job
export HERE OUT SML REFS STEPS ITER_CAP WALL_CAP LOGS
: > "$LOGS/status.txt"
xargs -P "$JOBS" -L 1 bash -c 'job "$0" "$1" "$2" "$3"' < "$JOBFILE.sorted"

# deterministic, sorted summary of what was recorded
if grep -q full <<<"$STAGES" && [ -f "$OUT/runs/timing.jsonl" ]; then
  sort -o "$OUT/runs/timing.jsonl" "$OUT/runs/timing.jsonl"
fi
python3 "$HERE/summarize.py" "$OUT" > "$OUT/MANIFEST.jsonl"
STAGES="$STAGES" SEEDS="$SEEDS" STEPS="$STEPS" TRAJ_PROFILES="$TRAJ_PROFILES" EXTRA_TRAJ="$EXTRA_TRAJ" \
  EXTRA_SEEDS="$EXTRA_SEEDS" FULL_PROFILES="$FULL_PROFILES" VARIANT_PROFILES="$VARIANT_PROFILES" \
  MANUAL_FULL="$MANUAL_FULL" ITER_CAP="$ITER_CAP" DATASETS="$DATASETS" \
  python3 "$HERE/provenance.py" "$REPO" > "$OUT/PROVENANCE.json"
if grep -v 'rc=0$' "$LOGS/status.txt"; then echo "FAILED jobs above (logs: $LOGS)"; exit 1; fi
echo "done: $OUT"
