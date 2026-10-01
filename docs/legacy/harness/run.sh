#!/bin/sh
# Compiles and runs LegacyVsCdk against the original compchem.jar and CDK 2.13 (resolved via Maven).
# Usage: ./run.sh [stepsPerSeed]   (default 2000)
# Also re-runs the legacy ring finder on every structure the original generator crashed on
# (golden/runs/*.json, crash.candidateCt; atoms from golden/datasets/*.echo.jsonl, stage configured_gui).
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk}
OUT=${TMPDIR:-/tmp}/seneca-legacy-vs-cdk
mkdir -p "$OUT"
cat > "$OUT/pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>x</groupId><artifactId>cp</artifactId><version>1</version>
<dependencies><dependency><groupId>org.openscience.cdk</groupId><artifactId>cdk-bundle</artifactId><version>2.13</version></dependency></dependencies></project>
POM
(cd "$OUT" && mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt)
CP="$ROOT/legacy/original/jar/compchem.jar:$(cat "$OUT/cp.txt")"
"$JAVA_HOME/bin/javac" -nowarn -d "$OUT/classes" -cp "$CP" "$HERE/LegacyVsCdk.java"
python3 - "$ROOT/golden" > "$OUT/crash-candidates.txt" <<'PY'
import glob, json, os, sys
g = sys.argv[1]
for f in sorted(glob.glob(os.path.join(g, "runs", "*.json"))):
    r = json.load(open(f))
    c = r.get("crash") if isinstance(r, dict) else None
    if not c or not c.get("candidateCt"):
        continue
    atoms = None
    for line in open(os.path.join(g, "datasets", r["key"] + ".echo.jsonl")):
        d = json.loads(line)
        if d["t"] == "atoms" and d["stage"] == "configured_gui":
            atoms = d["atoms"]
    print("%s.%s.seed%s|%s|%s|%s" % (r["key"], r["profile"], r["seed"],
          ",".join(a["el"] for a in atoms), ",".join(str(a["h"]) for a in atoms),
          " ".join("%d-%d-%d" % tuple(b) for b in c["candidateCt"])))
PY
"$JAVA_HOME/bin/java" -cp "$OUT/classes:$CP" LegacyVsCdk "${1:-2000}" "$OUT/crash-candidates.txt"
