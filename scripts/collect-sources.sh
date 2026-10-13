#!/bin/bash
# Collects the source code of the third-party software inside dist/Seneca.app, to be published as assets of
# the same GitHub release as the disk image. The GNU GPL 2 (Java runtime, vecmath) and LGPL 2.1 (CDK,
# JFreeChart, FreeHEP) ask that whoever distributes their binaries offers the corresponding source from the
# same place.
#
#   dist/Seneca-<version>-third-party-sources.zip     the -sources.jar of each of the 27 libraries in
#                                                     Seneca.app/Contents/app, as published on Maven Central,
#                                                     plus SOURCES.txt (which jar belongs to which)
#   dist/OpenJDK<n>U-jdk-sources_<release>.tar.gz     the source of the bundled Java runtime (and of the
#                                                     jpackage launcher), the archive Eclipse Adoptium
#                                                     publishes for TEMURIN_RELEASE, checked against
#                                                     TEMURIN_SOURCES_SHA256
#
#   scripts/collect-sources.sh            (after RUNTIME_JDK=temurin scripts/build-app.sh)
#
# Needs network access: Maven runs online (dependency:copy-dependencies -Dclassifier=sources), the JDK
# source archive (about 120 MB) is downloaded once into SENECA_CACHE.
#
# Environment:
#   JAVA_HOME, BUILD_DIR (default target-sources), DIST (default dist), SENECA_CACHE as in build-app.sh
#   TEMURIN_RELEASE  must be the release build-app.sh used (default jdk-25.0.4.1+1)
#   TEMURIN_SOURCES_SHA256  SHA-256 of its source archive (default: the pinned release's, from the
#               .sha256.txt next to the archive in adoptium/temurin25-binaries); change both together
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}"
TEMURIN_RELEASE="${TEMURIN_RELEASE:-jdk-25.0.4.1+1}"
TEMURIN_SOURCES_SHA256="${TEMURIN_SOURCES_SHA256:-cb9e50d2eb3de72ffd28466d7c511b4039b4bc4bd8f0d276d1e4cf81e30fdf09}"
SENECA_CACHE="${SENECA_CACHE:-$HOME/Library/Caches/seneca-build}"
BUILD_DIR="${BUILD_DIR:-$HERE/target-sources}"
DIST="${DIST:-$HERE/dist}"
APP="$DIST/Seneca.app"
[ -d "$APP/Contents/app" ] || { echo "collect-sources: $APP missing; build it with scripts/build-app.sh" >&2; exit 1; }

VERSION_FULL="$(python3 - "$HERE/pom.xml" <<'EOF'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
EOF
)"
VERSION="${VERSION_FULL%%-*}"

# 1. the libraries: one -sources.jar per jar in the bundle (seneca.jar itself is this repository)
WORK="$(mktemp -d "${TMPDIR:-/tmp}/seneca-sources.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/third-party-sources"
(cd "$HERE" && mvn -q -Dbuild.dir="$BUILD_DIR" dependency:copy-dependencies -DincludeScope=runtime \
    -Dclassifier=sources -DoutputDirectory="$WORK/third-party-sources")
{
    echo "Source code of the third-party Java libraries in Seneca.app $VERSION (Contents/app)."
    echo "Each file is the -sources.jar published on Maven Central next to the binary jar, unmodified."
    echo "Licences: THIRD-PARTY-NOTICES.md (in the disk image and in the repository)."
    echo "Source of the Java runtime: the OpenJDK source archive attached to the same release."
    echo "Source of SENECA itself: https://github.com/steinbeck/seneca-jcics-modern (tag v$VERSION)."
    echo
    echo "jar in Contents/app                  source"
} > "$WORK/third-party-sources/SOURCES.txt"
MISSING=0
N=0
for jar in "$APP"/Contents/app/*.jar; do
    name="$(basename "$jar" .jar)"
    [ "$name" = seneca ] && continue
    if [ -f "$WORK/third-party-sources/$name-sources.jar" ]; then
        printf '%-36s %s\n' "$name.jar" "$name-sources.jar" >> "$WORK/third-party-sources/SOURCES.txt"
        N=$((N + 1))
    else
        echo "collect-sources: no sources jar for $name.jar" >&2
        MISSING=1
    fi
done
[ "$MISSING" = 0 ] || exit 1
EXTRA="$(cd "$WORK/third-party-sources" && for s in *-sources.jar; do [ -f "$APP/Contents/app/${s%-sources.jar}.jar" ] || echo "$s"; done)"
[ -z "$EXTRA" ] || (cd "$WORK/third-party-sources" && rm -f $EXTRA)
ZIP="$DIST/Seneca-$VERSION-third-party-sources.zip"
rm -f "$ZIP"
(cd "$WORK" && zip -q -X -r "$ZIP" third-party-sources)
echo "collect-sources: $ZIP ($N libraries, $(du -h "$ZIP" | cut -f1 | tr -d ' '))"

# 2. the Java runtime: Adoptium's source archive of the same Temurin release
TV="${TEMURIN_RELEASE#jdk-}"                                       # 25.0.4.1+1
TAR="OpenJDK${TV%%.*}U-jdk-sources_${TV/+/_}.tar.gz"
URL="https://github.com/adoptium/temurin${TV%%.*}-binaries/releases/download/${TEMURIN_RELEASE/+/%2B}/$TAR"
RUNTIME_RELEASE="$APP/Contents/runtime/Contents/Home/release"
if ! grep -qx "JAVA_VERSION=\"${TV%%+*}\"" "$RUNTIME_RELEASE"; then
    echo "collect-sources: the runtime in $APP is not Java ${TV%%+*} ($RUNTIME_RELEASE); build it with RUNTIME_JDK=temurin" >&2
    exit 1
fi
mkdir -p "$SENECA_CACHE"
if [ ! -f "$SENECA_CACHE/$TAR" ]; then
    echo "collect-sources: downloading $URL"
    curl -fsSL --retry 3 -o "$SENECA_CACHE/$TAR.part" "$URL"
    mv "$SENECA_CACHE/$TAR.part" "$SENECA_CACHE/$TAR"
fi
GOT="$(shasum -a 256 "$SENECA_CACHE/$TAR" | cut -d' ' -f1)"
if [ "$GOT" != "$TEMURIN_SOURCES_SHA256" ]; then
    rm -f "$SENECA_CACHE/$TAR"
    echo "collect-sources: $TAR: SHA-256 $GOT, expected $TEMURIN_SOURCES_SHA256" >&2; exit 1
fi
cp "$SENECA_CACHE/$TAR" "$DIST/$TAR"
echo "collect-sources: $DIST/$TAR ($(du -h "$DIST/$TAR" | cut -f1 | tr -d ' '), sha256 $GOT)"
