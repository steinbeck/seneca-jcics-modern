#!/bin/bash
# Builds the golden-master harness.
#
#   ./build.sh     1. compiles legacy/runnable/src -> legacy/harness/legacy-classes (always, fresh;
#                     same javac flags and resource copy as legacy/runnable/build.sh, but into a
#                     private directory, so a concurrent rebuild of legacy/runnable cannot interfere)
#                  2. compiles src/ -> classes/ against it
#
# Environment: JAVA_HOME (default /opt/homebrew/opt/openjdk, JDK 25)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk}"
LSRC="$HERE/../runnable/src"
LOUT="$HERE/legacy-classes"
LIB="$HERE/../original/jar"

CP=""
for j in "$LIB"/*.jar; do CP="$CP${CP:+:}$j"; done

# 1. legacy classes (flags: see legacy/runnable/build.sh)
rm -rf "$LOUT"
mkdir -p "$LOUT"
find "$LSRC" -name '*.java' | LC_ALL=C sort > "$LOUT/.sources"
"$JAVA_HOME/bin/javac" --release 8 -encoding ISO-8859-1 -nowarn -Xlint:-options \
    -d "$LOUT" -cp "$CP" @"$LOUT/.sources"
rm -f "$LOUT/.sources"
( cd "$LSRC" && find . -type f ! -name '*.java' ! -name '.#*' -print0 \
    | while IFS= read -r -d '' f; do
        mkdir -p "$LOUT/$(dirname "$f")"
        cp -p "$f" "$LOUT/$f"
      done )

# 2. harness
rm -rf "$HERE/classes"
mkdir -p "$HERE/classes"
find "$HERE/src" -name '*.java' | LC_ALL=C sort > "$HERE/classes/.sources"
# --release 21: the harness itself uses StackWalker/lambdas; the legacy classes it links
# against are class version 52.
"$JAVA_HOME/bin/javac" --release 21 -encoding UTF-8 -Xlint:-options -nowarn \
    -d "$HERE/classes" -cp "$LOUT:$CP" @"$HERE/classes/.sources"
rm -f "$HERE/classes/.sources"
echo "Built legacy classes into $LOUT and the harness into $HERE/classes"
