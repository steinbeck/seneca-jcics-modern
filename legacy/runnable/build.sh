#!/bin/bash
# Builds the runnable copy of the 2001 SENECA code base on a modern JDK.
#
#   ./build.sh            compile src/ -> classes/ (plus resources)
#
# Environment:
#   JAVA_HOME   JDK to use (default: /opt/homebrew/opt/openjdk, i.e. JDK 25)
#
# Notes:
#   * --release 8 is deliberate: the 2001 source calls the inherited static
#     Thread.yield() unqualified as yield(), which javac >= 14 rejects
#     ("restricted identifier 'yield'") for --release >= 14. Release 8 keeps
#     the algorithm source untouched. The bytecode (class version 52) runs on
#     any JDK >= 8, including JDK 25. See PATCHES.md.
#   * Third-party jars are used in place from ../original/jar (not copied).
#   * All packages are compiled, including the RMI classes (seneca.engine,
#     seneca.gatekeeper, seneca.compute, jnt.scimark2): the GUI's
#     "Start local server" button instantiates seneca.engine.StructureGeneratorServer
#     in-process, so nothing can be excluded without patching the GUI.
#     No rmic step is needed (dynamic RMI stubs since Java 5).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk}"
JAVAC="$JAVA_HOME/bin/javac"
SRC="$HERE/src"
OUT="$HERE/classes"
LIB="$HERE/../original/jar"

CP=""
for j in "$LIB"/*.jar; do CP="$CP${CP:+:}$j"; done

rm -rf "$OUT"
mkdir -p "$OUT"

# Sources (ISO-8859-1: some files contain Latin-1 umlauts in comments/strings).
find "$SRC" -name '*.java' > "$OUT/.sources"
"$JAVAC" --release 8 -encoding ISO-8859-1 -nowarn -Xlint:-options \
    -d "$OUT" -cp "$CP" @"$OUT/.sources"
rm -f "$OUT/.sources"

# Resources: every non-.java file under src (properties, images, policies),
# as the 2001 ant target did, minus CVS backup files (.#*).
( cd "$SRC" && find . -type f ! -name '*.java' ! -name '.#*' -print0 \
    | while IFS= read -r -d '' f; do
        mkdir -p "$OUT/$(dirname "$f")"
        cp -p "$f" "$OUT/$f"
      done )

echo "Built $(find "$OUT" -name '*.class' | wc -l | tr -d ' ') classes into $OUT"
