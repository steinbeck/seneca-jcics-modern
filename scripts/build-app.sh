#!/bin/bash
# Builds the macOS application dist/Seneca.app (app image with its own Java runtime; no installer).
#
#   scripts/build-app.sh [--skip-maven]
#
# Steps:
#   1. mvn package (tests skipped: run them separately) and collect the runtime jars
#      (the application jar plus every runtime dependency, unshaded) into an input directory;
#   2. the ten example datasets of the 2001 distribution (legacy/original/sml/*.sml) into input/examples
#      (the application finds them through -Dseneca.examples=$APPDIR/examples: File > Open > Example
#      Dataset, and the first directory of the Open dialog);
#   3. the icon: alpha-pinene drawn by CDK at every icon size (scripts/gui/MakeIcon.java), assembled
#      with iconutil into Seneca.icns;
#   4. jlink: a trimmed Java runtime with the modules jdeps finds (plus jdk.localedata, English and
#      German locale data);
#   5. jpackage --type app-image: Seneca.app, main class io.github.steinbeck.seneca.app.SenecaApp,
#      bundle id io.github.steinbeck.seneca, version from pom.xml (without -SNAPSHOT);
#   6. Info.plist: the .sml document type (Finder "Open With", double click) and its exported type;
#   7. ad-hoc code signature (codesign --force --deep -s -), so that it starts on this Mac;
#   8. Launch Services registration (only for dist/, see REGISTER).
#
# Environment:
#   JAVA_HOME   JDK 25 with jlink and jpackage (default: Homebrew openjdk)
#   BUILD_DIR   Maven build directory (default: target-app)
#   DIST        output directory (default: dist); only DIST/Seneca.app is replaced
#   REGISTER    1: register the bundle with Launch Services (.sml files open in it), 0: never; default:
#               only when DIST is the default dist/ directory
#   MVN_OFFLINE Maven offline flag (default: -o; set MVN_OFFLINE= when the local Maven repository lacks
#               the dependencies, e.g. on a new machine)
#
# Afterwards: scripts/launch-test.sh starts the built application unattended (self-test) and quits it.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}"
BUILD_DIR="${BUILD_DIR:-$HERE/target-app}"
DIST="${DIST:-$HERE/dist}"
MVN_OFFLINE="${MVN_OFFLINE--o}"
SKIP_MAVEN=0
[ "${1:-}" = "--skip-maven" ] && SKIP_MAVEN=1

for tool in jlink jpackage jdeps java; do
    [ -x "$JAVA_HOME/bin/$tool" ] || { echo "build-app: $JAVA_HOME/bin/$tool missing (JDK 25 with jpackage needed)" >&2; exit 1; }
done
for tool in iconutil codesign plutil; do
    command -v "$tool" >/dev/null || { echo "build-app: $tool missing (macOS only)" >&2; exit 1; }
done

VERSION_FULL="$(python3 - "$HERE/pom.xml" <<'EOF'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
EOF
)"
VERSION="${VERSION_FULL%%-*}"              # jpackage wants 1-3 numbers: 1.0.0-SNAPSHOT -> 1.0.0
ARTIFACT="seneca-$VERSION_FULL.jar"
echo "build-app: Seneca $VERSION_FULL (bundle version $VERSION)"

STAGE="$BUILD_DIR/app-stage"
INPUT="$STAGE/input"
rm -rf "$STAGE"
mkdir -p "$INPUT/examples" "$STAGE/icon"

# 1. jars
if [ "$SKIP_MAVEN" = 0 ] || [ ! -f "$BUILD_DIR/$ARTIFACT" ]; then
    (cd "$HERE" && mvn $MVN_OFFLINE -q -Dbuild.dir="$BUILD_DIR" -DskipTests package)
fi
(cd "$HERE" && mvn $MVN_OFFLINE -q -Dbuild.dir="$BUILD_DIR" dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory="$INPUT" -DoverWriteIfNewer=true)
cp "$BUILD_DIR/$ARTIFACT" "$INPUT/seneca.jar"
echo "build-app: $(ls "$INPUT"/*.jar | wc -l | tr -d ' ') jars"

# 2. examples
cp "$HERE"/legacy/original/sml/*.sml "$INPUT/examples/"
echo "build-app: $(ls "$INPUT/examples" | wc -l | tr -d ' ') example datasets"

# 3. icon
CP="$(ls "$INPUT"/*.jar | tr '\n' ':')"
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "$CP" "$HERE/scripts/gui/MakeIcon.java" "$STAGE/icon/Seneca.iconset" > /dev/null
iconutil -c icns -o "$STAGE/icon/Seneca.icns" "$STAGE/icon/Seneca.iconset"

# 4. runtime
MODULES="$("$JAVA_HOME/bin/jdeps" --ignore-missing-deps --print-module-deps --multi-release 25 \
    --class-path "$CP" "$INPUT/seneca.jar" | tail -1)"
MODULES="$MODULES,java.logging,jdk.localedata"
echo "build-app: runtime modules $MODULES"
"$JAVA_HOME/bin/jlink" --add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages \
    --compress zip-6 --include-locales en,de --output "$STAGE/runtime"

# 5. app image
mkdir -p "$DIST"
rm -rf "$DIST/Seneca.app"
"$JAVA_HOME/bin/jpackage" --type app-image --dest "$DIST" \
    --name Seneca --app-version "$VERSION" \
    --vendor "Christoph Steinbeck" \
    --copyright "(c) 1998-2001 Christoph Steinbeck; port $(date +%Y)" \
    --description "SENECA: computer-assisted structure elucidation from NMR data (J. Chem. Inf. Comput. Sci. 2001, 41, 1500)" \
    --input "$INPUT" --main-jar seneca.jar --main-class io.github.steinbeck.seneca.app.SenecaApp \
    --runtime-image "$STAGE/runtime" \
    --icon "$STAGE/icon/Seneca.icns" \
    --mac-package-identifier io.github.steinbeck.seneca --mac-package-name Seneca --mac-app-category education \
    --java-options '-Xss16m' \
    --java-options '--enable-native-access=ALL-UNNAMED' \
    --java-options '-Dseneca.examples=$APPDIR/examples' \
    --java-options '-Dapple.laf.useScreenMenuBar=true' \
    --java-options '-Dapple.awt.application.name=Seneca'
APP="$DIST/Seneca.app"

# 6. Info.plist: document type .sml
PL="$APP/Contents/Info.plist"
for key in CFBundleDocumentTypes UTExportedTypeDeclarations NSHighResolutionCapable; do
    plutil -remove "$key" "$PL" 2>/dev/null || true
done
plutil -insert CFBundleDocumentTypes -json '[{"CFBundleTypeName": "SENECA Dataset", "CFBundleTypeRole": "Editor",
    "LSHandlerRank": "Owner", "CFBundleTypeIconFile": "Seneca.icns", "CFBundleTypeExtensions": ["sml"],
    "LSItemContentTypes": ["io.github.steinbeck.seneca.sml"]}]' "$PL"
plutil -insert UTExportedTypeDeclarations -json '[{"UTTypeIdentifier": "io.github.steinbeck.seneca.sml",
    "UTTypeDescription": "SENECA Dataset", "UTTypeIconFile": "Seneca.icns", "UTTypeConformsTo": ["public.xml"],
    "UTTypeTagSpecification": {"public.filename-extension": ["sml"]}}]' "$PL"
plutil -insert NSHighResolutionCapable -bool true "$PL"
plutil -lint "$PL" > /dev/null

# 7. ad-hoc signature
codesign --force --deep -s - "$APP"
codesign --verify --deep --strict "$APP"

# Finder / Launch Services: register the bundle so that .sml files show "Open With > Seneca". Only the
# default dist/ bundle (or with REGISTER=1): a build into a scratch or build directory would otherwise
# offer itself as .sml handler and leave a stale registration once that directory is deleted.
LSREGISTER=/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister
if [ "${REGISTER:-auto}" = "1" ] || { [ "${REGISTER:-auto}" = "auto" ] && [ "$(cd "$DIST" && pwd)" = "$HERE/dist" ]; }; then
    [ -x "$LSREGISTER" ] && "$LSREGISTER" -f "$APP" || true
else
    echo "build-app: not registered with Launch Services (DIST is not dist/); to register: $LSREGISTER -f \"$APP\""
fi

echo "build-app: $APP ($(du -sh "$APP" | cut -f1))"
