#!/bin/bash
# Launches the SENECA GUI client (seneca.client.Seneca) from classes/.
#
#   ./run.sh [file.sml]     optional dataset to open at startup
#
# Environment:
#   JAVA_HOME   JDK to use (default: /opt/homebrew/opt/openjdk, i.e. JDK 25)
#   JAVA_OPTS   extra JVM options
#
# The client's log4j configuration writes ~/seneca.log,
# ~/seneca.results.sdf and ~/seneca.annealing.log (as in 2001).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk}"
LIB="$HERE/../original/jar"

[ -d "$HERE/classes/seneca" ] || "$HERE/build.sh"

CP="$HERE/classes"
for j in "$LIB"/*.jar; do CP="$CP:$j"; done

exec "$JAVA_HOME/bin/java" ${JAVA_OPTS:-} -Dseneca.home="$HERE" -cp "$CP" seneca.client.Seneca "$@"
