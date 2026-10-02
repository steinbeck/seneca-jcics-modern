#!/bin/bash
# Starts the SENECA application (io.github.steinbeck.seneca.app.SenecaApp) from a Maven build directory.
#
#   scripts/run-app.sh [file.sml]
#
# Environment:
#   BUILD_DIR    Maven build directory (default: target; build it with mvn -Dbuild.dir=... compile)
#   JAVA_HOME    JDK 25 (default: Homebrew openjdk)
#   JAVA_OPTS    extra JVM options, e.g. the hidden test hooks -Dseneca.seed=1 -Dseneca.iterationCap=5000000,
#                or -Dseneca.logdir=/tmp/x to keep the log files out of the home directory
# Options of the application (SenecaApp): --logdir=<dir>, --seed=<n>, --iteration-cap=<n>, --examples=<dir>,
# --smoke[=<report>] (unattended self-test). The example datasets are legacy/original/sml.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}"
BUILD_DIR="${BUILD_DIR:-$HERE/target}"
CPFILE="$BUILD_DIR/app.classpath"
if [ ! -f "$CPFILE" ] || [ "$HERE/pom.xml" -nt "$CPFILE" ]; then
    (cd "$HERE" && JAVA_HOME="$JAVA_HOME" mvn ${MVN_OFFLINE--o} -q -Dbuild.dir="$BUILD_DIR" dependency:build-classpath \
        -DincludeScope=runtime -Dmdep.outputFile="$CPFILE")
fi
exec "$JAVA_HOME/bin/java" -Dseneca.examples="$HERE/legacy/original/sml" ${JAVA_OPTS:-} -Xss16m --enable-native-access=ALL-UNNAMED -cp "$BUILD_DIR/classes:$(cat "$CPFILE")" \
    io.github.steinbeck.seneca.app.SenecaApp "$@"
