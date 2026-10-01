#!/bin/bash
# Runs one GoldenMaster invocation (one dataset, one mode) in its own JVM.
#   ./gm.sh <mode> <args...>     see src/seneca/structgen/GoldenMaster.java
# Environment: JAVA_HOME (default JDK 25), GM_HOME (user.home for the JVM; default: a temp dir),
#              GM_JAVA_OPTS (extra JVM options, e.g. -Dgm.refRestarts=32 to re-check the reference search)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk}"
[ -d "$HERE/classes/seneca" ] && [ -d "$HERE/legacy-classes/seneca" ] || "$HERE/build.sh" >&2
CP="$HERE/classes:$HERE/legacy-classes"
for j in "$HERE"/../original/jar/*.jar; do CP="$CP:$j"; done
GM_HOME="${GM_HOME:-${TMPDIR:-/tmp}/seneca-golden-home}"
mkdir -p "$GM_HOME"
# headless: the judge configurators are Swing panels, no window is opened.
# user.home: keeps any legacy log4j file output away from the real home directory.
exec "$JAVA_HOME/bin/java" ${GM_JAVA_OPTS:-} -Djava.awt.headless=true -Duser.home="$GM_HOME" -Xss16m \
    -cp "$CP" seneca.structgen.GoldenMaster "$@"
