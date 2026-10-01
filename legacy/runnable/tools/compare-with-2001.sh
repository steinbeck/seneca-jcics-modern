#!/bin/bash
# Compares every class of legacy/dist-2001/seneca-client.jar (A) with the
# rebuilt legacy/runnable/classes (B) using bytecode_fingerprint.py.
# Run ./build.sh first. Prints one block per class that differs.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$HERE/../dist-2001/seneca-client.jar"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
( cd "$TMP" && unzip -q "$JAR" )
cd "$TMP"
for c in $(find seneca jnt -name '*.class' | sort); do
  if [ -f "$HERE/classes/$c" ]; then
    r="$(python3 "$HERE/tools/bytecode_fingerprint.py" "$c" "$HERE/classes/$c")"
    [ -n "$r" ] && { echo "### $c"; echo "$r"; }
  else
    echo "### only in 2001 jar: $c"
  fi
done
( cd "$HERE/classes" && find seneca jnt -name '*.class' | sort ) | while read -r c; do
  [ -f "$TMP/$c" ] || echo "### only in rebuild: $c"
done
