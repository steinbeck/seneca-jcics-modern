#!/bin/bash
# Builds the macOS application dist/Seneca.app (app image with its own Java runtime; no installer).
# scripts/build-dmg.sh then packs it into the release disk image.
#
#   scripts/build-app.sh [--skip-maven]
#   RUNTIME_JDK=temurin scripts/build-app.sh      # release build: runtime from Eclipse Temurin (see below)
#
# Steps:
#   1. mvn package (tests skipped: run them separately) and collect the runtime jars
#      (the application jar plus every runtime dependency, unshaded) into an input directory;
#   2. the ten example datasets of the 2001 distribution (legacy/original/sml/*.sml) into input/examples,
#      mode 644 (git keeps them executable, as imported from steinbeck/seneca-jcics) (the application
#      finds them through -Dseneca.examples=$APPDIR/examples: File > Open > Example Dataset, and the
#      first directory of the Open dialog);
#   3. the icon: alpha-pinene drawn by CDK at every icon size (scripts/gui/MakeIcon.java), assembled
#      with iconutil into Seneca.icns;
#   4. jlink (of RUNTIME_JDK): a trimmed Java runtime with the modules jdeps finds (plus jdk.localedata,
#      English and German locale data), the legal/ notices of every module (jlink without jmods/ leaves
#      out jdk.localedata's; copied from the JDK), every file made owner-writable (jlink copies the JDK's legal/
#      notices read-only, and a user's "xattr -drs com.apple.quarantine Seneca.app" would then fail on
#      them); then the portability check: every native library of the runtime may load only macOS
#      system libraries and libraries of the runtime itself;
#   5. jpackage --type app-image: Seneca.app, main class io.github.steinbeck.seneca.app.SenecaApp,
#      bundle id io.github.steinbeck.seneca, version from pom.xml (without -SNAPSHOT);
#   6. Info.plist: the .sml document type (Finder "Open With", double click) and its exported type;
#      LSMinimumSystemVersion = the highest minimum macOS version of the bundle's binaries (jpackage
#      writes 10.11, the binaries say otherwise); jpackage's default NSMicrophoneUsageDescription is
#      removed (SENECA never uses the microphone);
#   7. THIRD-PARTY-NOTICES.md and LICENSE (when present) into Contents/Resources;
#   8. ad-hoc code signature (codesign --force --deep -s -): no Developer ID, not notarized; a downloaded
#      copy must be released from Gatekeeper's quarantine once (README.md, "Installation");
#   9. Launch Services registration (only for dist/, see REGISTER).
#
# Environment:
#   JAVA_HOME   JDK 25 for Maven, jdeps and the icon (default: Homebrew openjdk)
#   RUNTIME_JDK JDK 25 whose jlink and jpackage build the bundled runtime and the app image (default:
#               JAVA_HOME). It must be self-contained: Homebrew's openjdk links its font, colour and image
#               libraries against Homebrew's harfbuzz, freetype, little-cms2, libpng, giflib and jpeg-turbo
#               (/opt/homebrew/opt/...), so a runtime made from it starts only on Macs that have these
#               Homebrew packages. Such a build is fine on the development machine (with a warning) but
#               scripts/build-dmg.sh refuses to package it.
#               RUNTIME_JDK=temurin: download Eclipse Temurin TEMURIN_RELEASE for macOS/aarch64 once,
#               check it against TEMURIN_SHA256, unpack it into SENECA_CACHE and use it.
#   TEMURIN_RELEASE  default jdk-25.0.4.1+1; TEMURIN_SHA256 the SHA-256 of its .tar.gz (default: the pinned
#               release's, from api.adoptium.net); change both together
#   SENECA_CACHE download cache for RUNTIME_JDK=temurin (default: ~/Library/Caches/seneca-build)
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
RUNTIME_JDK="${RUNTIME_JDK:-$JAVA_HOME}"
TEMURIN_RELEASE="${TEMURIN_RELEASE:-jdk-25.0.4.1+1}"
TEMURIN_SHA256="${TEMURIN_SHA256:-61979887f7506a24a57439ff99adb8b3a7fc89977d9cfe3b8984f58a981b7b9d}"
SENECA_CACHE="${SENECA_CACHE:-$HOME/Library/Caches/seneca-build}"
BUILD_DIR="${BUILD_DIR:-$HERE/target-app}"
DIST="${DIST:-$HERE/dist}"
MVN_OFFLINE="${MVN_OFFLINE--o}"
SKIP_MAVEN=0
[ "${1:-}" = "--skip-maven" ] && SKIP_MAVEN=1

for tool in jdeps java; do
    [ -x "$JAVA_HOME/bin/$tool" ] || { echo "build-app: $JAVA_HOME/bin/$tool missing (JDK 25 needed)" >&2; exit 1; }
done
for tool in iconutil codesign plutil otool lipo file; do
    command -v "$tool" >/dev/null || { echo "build-app: $tool missing (macOS with the Xcode command line tools)" >&2; exit 1; }
done

# Eclipse Temurin for the bundled runtime: downloaded once, checked against the pinned SHA-256.
if [ "$RUNTIME_JDK" = temurin ]; then
    [ "$(uname -m)" = arm64 ] || { echo "build-app: RUNTIME_JDK=temurin fetches the Apple silicon (aarch64) JDK only; set RUNTIME_JDK to a JDK 25 directory" >&2; exit 1; }
    TEMURIN_HOME="$SENECA_CACHE/$TEMURIN_RELEASE/Contents/Home"
    if [ ! -x "$TEMURIN_HOME/bin/jlink" ]; then
        TV="${TEMURIN_RELEASE#jdk-}"                               # 25.0.4.1+1
        TAR="OpenJDK${TV%%.*}U-jdk_aarch64_mac_hotspot_${TV/+/_}.tar.gz"
        URL="https://github.com/adoptium/temurin${TV%%.*}-binaries/releases/download/${TEMURIN_RELEASE/+/%2B}/$TAR"
        mkdir -p "$SENECA_CACHE"
        echo "build-app: downloading $URL"
        curl -fsSL --retry 3 -o "$SENECA_CACHE/$TAR.part" "$URL"
        GOT="$(shasum -a 256 "$SENECA_CACHE/$TAR.part" | cut -d' ' -f1)"
        if [ "$GOT" != "$TEMURIN_SHA256" ]; then
            rm -f "$SENECA_CACHE/$TAR.part"
            echo "build-app: $TAR: SHA-256 $GOT, expected $TEMURIN_SHA256" >&2; exit 1
        fi
        UNPACK="$(mktemp -d "$SENECA_CACHE/unpack.XXXXXX")"
        tar -xzf "$SENECA_CACHE/$TAR.part" -C "$UNPACK"
        rm -rf "${SENECA_CACHE:?}/$TEMURIN_RELEASE"
        mv "$UNPACK/$TEMURIN_RELEASE" "$SENECA_CACHE/$TEMURIN_RELEASE"
        rm -rf "$UNPACK" "$SENECA_CACHE/$TAR.part"
    fi
    RUNTIME_JDK="$TEMURIN_HOME"
fi
for tool in jlink jpackage; do
    [ -x "$RUNTIME_JDK/bin/$tool" ] || { echo "build-app: $RUNTIME_JDK/bin/$tool missing (JDK 25 with jlink and jpackage needed)" >&2; exit 1; }
done

# Mach-O files below a directory (NUL-separated)
macho_files() {
    find "$1" -type f \( -name '*.dylib' -o -perm -u+x \) -print0 | while IFS= read -r -d '' f; do
        case "$(file -b "$f")" in Mach-O*) printf '%s\0' "$f" ;; esac
    done
}
# libraries loaded from outside macOS and the bundle, as "file: library"
foreign_libs() {
    macho_files "$1" | while IFS= read -r -d '' f; do
        otool -L "$f" | tail -n +2 | awk '{ print $1 }' \
            | grep -vE '^(/usr/lib/|/System/Library/|@rpath/|@loader_path/|@executable_path/)' \
            | sed "s|^|${f#"$1"/}: |" || true
    done
}

VERSION_FULL="$(python3 - "$HERE/pom.xml" <<'EOF'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
EOF
)"
VERSION="${VERSION_FULL%%-*}"              # jpackage wants 1-3 numbers: 1.0.0-SNAPSHOT -> 1.0.0
ARTIFACT="seneca-$VERSION_FULL.jar"
echo "build-app: Seneca $VERSION_FULL (bundle version $VERSION), runtime from $("$RUNTIME_JDK/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.vendor.version = //p;s/^ *java.runtime.version = //p' | tr '\n' ' ')"

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
chmod 644 "$INPUT"/examples/*.sml
echo "build-app: $(ls "$INPUT/examples" | wc -l | tr -d ' ') example datasets"

# 3. icon
CP="$(ls "$INPUT"/*.jar | tr '\n' ':')"
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "$CP" "$HERE/scripts/gui/MakeIcon.java" "$STAGE/icon/Seneca.iconset" > /dev/null
iconutil -c icns -o "$STAGE/icon/Seneca.icns" "$STAGE/icon/Seneca.iconset"

# 4. runtime (made owner-writable: jlink copies the read-only legal/ notices of the JDK)
MODULES="$("$JAVA_HOME/bin/jdeps" --ignore-missing-deps --print-module-deps --multi-release 25 \
    --class-path "$CP" "$INPUT/seneca.jar" | tail -1)"
MODULES="$MODULES,java.logging,jdk.localedata"
echo "build-app: runtime modules $MODULES"
"$RUNTIME_JDK/bin/jlink" --add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages \
    --compress zip-6 --include-locales en,de --output "$STAGE/runtime"
# jlink from a JDK without jmods/ (Temurin 25 ships none) leaves out some modules' legal/ notices
# (jdk.localedata); copy them from the JDK as jlink does from jmods
for mod in ${MODULES//,/ }; do
    if [ -d "$RUNTIME_JDK/legal/$mod" ] && [ ! -d "$STAGE/runtime/legal/$mod" ]; then
        cp -R "$RUNTIME_JDK/legal/$mod" "$STAGE/runtime/legal/$mod"
        echo "build-app: legal/$mod copied from the JDK (jlink left it out)"
    fi
done
chmod -R u+w "$STAGE/runtime"
FOREIGN="$(foreign_libs "$STAGE/runtime")"
if [ -n "$FOREIGN" ]; then
    echo "build-app: WARNING: the runtime is not self-contained; it loads libraries that only this kind of Mac has:" >&2
    echo "$FOREIGN" | sed 's/^/build-app:   /' >&2
    echo "build-app: WARNING: fine for this machine, not for distribution (scripts/build-dmg.sh refuses it); use RUNTIME_JDK=temurin" >&2
fi

# 5. app image
mkdir -p "$DIST"
rm -rf "$DIST/Seneca.app"
"$RUNTIME_JDK/bin/jpackage" --type app-image --dest "$DIST" \
    --name Seneca --app-version "$VERSION" \
    --vendor "Christoph Steinbeck" \
    --copyright "(c) 1997-2001 Christoph Steinbeck; port $(date +%Y)" \
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

# 6. Info.plist: document type .sml, minimum macOS version, no microphone text (jpackage adds one by default)
PL="$APP/Contents/Info.plist"
for key in CFBundleDocumentTypes UTExportedTypeDeclarations NSHighResolutionCapable NSMicrophoneUsageDescription; do
    plutil -remove "$key" "$PL" 2>/dev/null || true
done
plutil -insert CFBundleDocumentTypes -json '[{"CFBundleTypeName": "SENECA Dataset", "CFBundleTypeRole": "Editor",
    "LSHandlerRank": "Owner", "CFBundleTypeIconFile": "Seneca.icns", "CFBundleTypeExtensions": ["sml"],
    "LSItemContentTypes": ["io.github.steinbeck.seneca.sml"]}]' "$PL"
plutil -insert UTExportedTypeDeclarations -json '[{"UTTypeIdentifier": "io.github.steinbeck.seneca.sml",
    "UTTypeDescription": "SENECA Dataset", "UTTypeIconFile": "Seneca.icns", "UTTypeConformsTo": ["public.xml"],
    "UTTypeTagSpecification": {"public.filename-extension": ["sml"]}}]' "$PL"
plutil -insert NSHighResolutionCapable -bool true "$PL"
MINOS="$(macho_files "$APP" | xargs -0 otool -l | awk '
    /cmd LC_BUILD_VERSION/ { b = 1 } b && $1 == "minos" { print $2; b = 0 }
    /cmd LC_VERSION_MIN_MACOSX/ { v = 1 } v && $1 == "version" { print $2; v = 0 }' \
    | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)"
[ -n "$MINOS" ] && plutil -replace LSMinimumSystemVersion -string "$MINOS" "$PL"
plutil -lint "$PL" > /dev/null
ARCHS="$(macho_files "$APP" | while IFS= read -r -d '' f; do lipo -archs "$f"; done | tr ' ' '\n' | sort -u | tr '\n' ' ')"
echo "build-app: minimum macOS ${MINOS:-?}, architectures ${ARCHS% }"

# 7. licences
if [ -f "$HERE/THIRD-PARTY-NOTICES.md" ]; then
    cp "$HERE/THIRD-PARTY-NOTICES.md" "$APP/Contents/Resources/"
else
    echo "build-app: WARNING: THIRD-PARTY-NOTICES.md missing, not in the bundle" >&2
fi
[ -f "$HERE/LICENSE" ] && cp "$HERE/LICENSE" "$APP/Contents/Resources/"

# 8. ad-hoc signature
codesign --force --deep -s - "$APP"
codesign --verify --deep --strict "$APP"

# 9. Finder / Launch Services: register the bundle so that .sml files show "Open With > Seneca". Only the
# default dist/ bundle (or with REGISTER=1): a build into a scratch or build directory would otherwise
# offer itself as .sml handler and leave a stale registration once that directory is deleted.
LSREGISTER=/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister
if [ "${REGISTER:-auto}" = "1" ] || { [ "${REGISTER:-auto}" = "auto" ] && [ "$(cd "$DIST" && pwd)" = "$HERE/dist" ]; }; then
    [ -x "$LSREGISTER" ] && "$LSREGISTER" -f "$APP" || true
else
    if [ "${REGISTER:-auto}" = "0" ]; then WHY="REGISTER=0"; else WHY="DIST is not dist/"; fi
    echo "build-app: not registered with Launch Services ($WHY); to register: $LSREGISTER -f \"$APP\""
fi

echo "build-app: $APP ($(du -sh "$APP" | cut -f1))"
