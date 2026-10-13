#!/bin/bash
# Launch test of the built application dist/Seneca.app (scripts/build-app.sh), unattended. The windows
# appear on the screen for a few seconds; nothing needs to be clicked.
#
#   scripts/launch-test.sh [path/to/Seneca.app]
#
#   1. launcher binary (Contents/MacOS/Seneca --smoke): the self-test of the application
#      (io.github.steinbeck.seneca.app.Smoke): opens the example pinene, paints every tab, configures it
#      with the golden "gui" recipe through the buttons, runs it with seed 1 and summarizes; must report
#      PASSED with the golden result pinene.gui.seed1 (21248 iterations, best score 2400/2400);
#   2. the same through Launch Services (open -n Seneca.app --args --smoke ...), as the Finder starts it;
#   3. a normal start with an example dataset as document (open -n Seneca.app --args <examples>/eurabidiol.sml):
#      the process must still run after 10 s with nothing on stderr that looks like an exception; then it
#      is killed;
#   4. speed in the background (App Nap, gui-design.md DV-89): the self-test on narelin capped at
#      SPEED_ITERATIONS iterations, started with open -g (never activated, as an app left behind other
#      windows), next to the same run on the command line (cli generate, bundled runtime); the app must
#      reach at least 60 % of the command line's iterations per second and report App Nap suspended.
#      SPEED_TEST=0 skips it (it takes about a minute).
# The log files go to a scratch directory (--logdir), never to the real ~/seneca.*.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$(cd "${1:-$HERE/dist/Seneca.app}" && pwd)"
BIN="$APP/Contents/MacOS/Seneca"
EXAMPLES="$APP/Contents/app/examples"
SCRATCH="$(mktemp -d "${TMPDIR:-/tmp}/seneca-launch.XXXXXX")"
echo "launch-test: $APP, scratch $SCRATCH"
FAILED=0

check_report() {   # report file, label
    local r="$1" label="$2"
    if [ ! -f "$r" ]; then echo "launch-test: $label: no report" >&2; FAILED=1; return; fi
    grep -E '^(java.home|lookAndFeel|examples|dataset|tabs|judges.enabled|run\.|status|PROBLEM)' "$r" | sed "s/^/  [$label] /"
    grep -qx 'status=PASSED' "$r" || { echo "launch-test: $label: self-test FAILED" >&2; FAILED=1; }
    grep -qx 'run.iterations=21248' "$r" || { echo "launch-test: $label: iterations differ from golden pinene.gui.seed1 (21248)" >&2; FAILED=1; }
    grep -qx 'run.bestScore=2400/2400' "$r" || { echo "launch-test: $label: best score differs from golden (2400/2400)" >&2; FAILED=1; }
    grep -q "^java.home=$APP/Contents/runtime" "$r" || { echo "launch-test: $label: not the bundled runtime" >&2; FAILED=1; }
}

wait_for() {       # file, seconds
    local f="$1" n=0
    while [ ! -s "$f" ] && [ $n -lt "$2" ]; do sleep 1; n=$((n + 1)); done
}

# 1. launcher binary
mkdir -p "$SCRATCH/direct"
set +e
"$BIN" --smoke="$SCRATCH/direct/report.txt" --logdir="$SCRATCH/direct" > "$SCRATCH/direct/stdout.txt" 2> "$SCRATCH/direct/stderr.txt" &
PID=$!
wait_for "$SCRATCH/direct/report.txt" 300
sleep 2
if kill -0 $PID 2>/dev/null; then echo "launch-test: launcher did not exit, killing it" >&2; kill $PID; FAILED=1; fi
wait $PID; CODE=$?
set -e
echo "launch-test: launcher exit code $CODE"
[ $CODE -eq 0 ] || FAILED=1
check_report "$SCRATCH/direct/report.txt" launcher
if grep -E 'Exception|Error' "$SCRATCH/direct/stderr.txt"; then echo "launch-test: launcher: stderr shows an exception" >&2; FAILED=1; fi

# 2. through Launch Services
mkdir -p "$SCRATCH/open"
open -n --stdout "$SCRATCH/open/stdout.txt" --stderr "$SCRATCH/open/stderr.txt" "$APP" \
    --args --smoke="$SCRATCH/open/report.txt" --logdir="$SCRATCH/open"
wait_for "$SCRATCH/open/report.txt" 300
sleep 3
if pgrep -f "$BIN --smoke=$SCRATCH/open" > /dev/null; then
    echo "launch-test: open: application still running, killing it" >&2
    pkill -f "$BIN --smoke=$SCRATCH/open" || true
    FAILED=1
fi
check_report "$SCRATCH/open/report.txt" open
if grep -E 'Exception|Error' "$SCRATCH/open/stderr.txt" 2>/dev/null; then echo "launch-test: open: stderr shows an exception" >&2; FAILED=1; fi

# 3. a normal start with a document, then quit (kill)
mkdir -p "$SCRATCH/doc"
open -n --stdout "$SCRATCH/doc/stdout.txt" --stderr "$SCRATCH/doc/stderr.txt" "$APP" \
    --args --logdir="$SCRATCH/doc" "$EXAMPLES/eurabidiol.sml"
sleep 10
DOC_PID="$(pgrep -f "$BIN --logdir=$SCRATCH/doc" || true)"
if [ -z "$DOC_PID" ]; then
    echo "launch-test: document start: the application is not running after 10 s" >&2; FAILED=1
else
    echo "launch-test: document start: running (pid $DOC_PID), log files: $(ls "$SCRATCH/doc" | grep seneca | tr '\n' ' ')"
    kill $DOC_PID
    sleep 2
    kill -0 $DOC_PID 2>/dev/null && kill -9 $DOC_PID
fi
if grep -E 'Exception|Error|Could not' "$SCRATCH/doc/stderr.txt" 2>/dev/null; then echo "launch-test: document start: stderr shows a problem" >&2; FAILED=1; fi

# 4. background speed (App Nap)
if [ "${SPEED_TEST:-1}" != 0 ]; then
    N="${SPEED_ITERATIONS:-400000}"
    mkdir -p "$SCRATCH/speed"
    open -g -n --stdout "$SCRATCH/speed/stdout.txt" --stderr "$SCRATCH/speed/stderr.txt" "$APP" \
        --args --smoke="$SCRATCH/speed/report.txt" --logdir="$SCRATCH/speed" --iteration-cap="$N" "$EXAMPLES/narelin.sml"
    "$APP/Contents/runtime/Contents/Home/bin/java" -Xss16m -cp "$APP/Contents/app/*" io.github.steinbeck.seneca.cli.Main \
        generate "$EXAMPLES/narelin.sml" --key narelin --profile gui --seed 1 --cap "$N" -o "$SCRATCH/speed/cli.sdf" \
        > "$SCRATCH/speed/cli.txt" 2>&1 || true
    wait_for "$SCRATCH/speed/report.txt" 600
    sleep 3
    if pgrep -f "$BIN --smoke=$SCRATCH/speed" > /dev/null; then
        pkill -f "$BIN --smoke=$SCRATCH/speed" 2>/dev/null || true
        sleep 2
        # an app whose self-test timed out under load can ignore SIGTERM (seen with the run still going)
        pkill -9 -f "$BIN --smoke=$SCRATCH/speed" 2>/dev/null || true
    fi
    CLI_RATE="$(sed -nE 's/^result .* after ([0-9]+) iterations, ([0-9.]+) s$/\1 \2/p' "$SCRATCH/speed/cli.txt" | awk '{ printf "%d", $1 / $2 }')"
    APP_RATE="$(sed -n 's/^run.iterationsPerSecond=//p' "$SCRATCH/speed/report.txt" 2>/dev/null)"
    NAP="$(sed -n 's/^run.appNapSuspended=//p' "$SCRATCH/speed/report.txt" 2>/dev/null)"
    echo "launch-test: background speed: app ${APP_RATE:-?} it/s, command line ${CLI_RATE:-?} it/s, App Nap suspended: ${NAP:-?}"
    if [ -z "$APP_RATE" ] || [ -z "$CLI_RATE" ] || [ "$CLI_RATE" -le 0 ]; then
        echo "launch-test: background speed: no measurement" >&2; FAILED=1
    elif [ $((APP_RATE * 100)) -lt $((CLI_RATE * 60)) ]; then
        echo "launch-test: background speed: the app is slower than 60 % of the command line (App Nap?)" >&2; FAILED=1
    fi
    [ "$NAP" = true ] || { echo "launch-test: background speed: App Nap was not suspended" >&2; FAILED=1; }
    grep -qx 'status=PASSED' "$SCRATCH/speed/report.txt" 2>/dev/null || { echo "launch-test: background speed: self-test FAILED" >&2; FAILED=1; }
fi

if [ $FAILED -eq 0 ]; then
    echo "launch-test: PASSED ($SCRATCH)"
else
    echo "launch-test: FAILED ($SCRATCH)" >&2
    exit 1
fi
