#!/bin/bash
# Packs dist/Seneca.app (scripts/build-app.sh) into the release disk image
#
#   dist/Seneca-<version>-macOS-<arch>.dmg          drag-to-install: Seneca.app, a link to /Applications,
#                                                   README.txt, THIRD-PARTY-NOTICES.md, LICENSE
#   dist/Seneca-<version>-macOS-<arch>.dmg.sha256   "<sha256>  <file name>" (shasum -a 256 -c checks it)
#
#   RUNTIME_JDK=temurin scripts/build-app.sh && scripts/build-dmg.sh
#
# Before packing it refuses an application that is not fit for other Macs: an invalid signature, a bundle
# version that is not the pom's, a Java runtime that loads libraries from outside macOS and the bundle
# (a runtime made from Homebrew's openjdk; build with RUNTIME_JDK=temurin), a missing THIRD-PARTY-NOTICES.md,
# files the owner cannot write (a user's "xattr -drs com.apple.quarantine" fails on them; build-app.sh makes
# the runtime writable).
# After packing it mounts the image read-only and checks the copy inside (signature, layout).
#
# The application keeps its ad-hoc signature: no Developer ID, not notarized. A downloaded copy is
# quarantined, and macOS blocks its first start until the user allows it (README.md, "Installation";
# the same text is README.txt in the image).
#
# Environment:
#   DIST        directory of Seneca.app and of the image (default: dist)
#   DMG_FORMAT  hdiutil image format (default: UDZO, zlib, opens on every macOS version the app supports)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="${DIST:-$HERE/dist}"
DMG_FORMAT="${DMG_FORMAT:-UDZO}"
APP="$DIST/Seneca.app"

for tool in hdiutil codesign plutil otool lipo file shasum ditto; do
    command -v "$tool" >/dev/null || { echo "build-dmg: $tool missing (macOS with the Xcode command line tools)" >&2; exit 1; }
done
[ -d "$APP" ] || { echo "build-dmg: $APP missing; build it with scripts/build-app.sh" >&2; exit 1; }

VERSION_FULL="$(python3 - "$HERE/pom.xml" <<'EOF'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
print(ET.parse(sys.argv[1]).getroot().find("m:version", ns).text.strip())
EOF
)"
VERSION="${VERSION_FULL%%-*}"
[ "$VERSION" = "$VERSION_FULL" ] || echo "build-dmg: WARNING: pom.xml has version $VERSION_FULL; the About box will say so" >&2

# Mach-O files below a directory (NUL-separated), as in build-app.sh
macho_files() {
    find "$1" -type f \( -name '*.dylib' -o -perm -u+x \) -print0 | while IFS= read -r -d '' f; do
        case "$(file -b "$f")" in Mach-O*) printf '%s\0' "$f" ;; esac
    done
}
foreign_libs() {
    macho_files "$1" | while IFS= read -r -d '' f; do
        otool -L "$f" | tail -n +2 | awk '{ print $1 }' \
            | grep -vE '^(/usr/lib/|/System/Library/|@rpath/|@loader_path/|@executable_path/)' \
            | sed "s|^|${f#"$1"/}: |" || true
    done
}

# 1. checks on the application
codesign --verify --deep --strict "$APP" || { echo "build-dmg: $APP: signature invalid" >&2; exit 1; }
PL="$APP/Contents/Info.plist"
BUNDLE_VERSION="$(plutil -extract CFBundleShortVersionString raw "$PL")"
[ "$BUNDLE_VERSION" = "$VERSION" ] || { echo "build-dmg: $APP is version $BUNDLE_VERSION, pom.xml says $VERSION; rebuild it" >&2; exit 1; }
FOREIGN="$(foreign_libs "$APP")"
if [ -n "$FOREIGN" ]; then
    echo "build-dmg: $APP loads libraries from outside macOS and the bundle; it would not start on other Macs:" >&2
    echo "$FOREIGN" | sed 's/^/build-dmg:   /' >&2
    echo "build-dmg: rebuild it with RUNTIME_JDK=temurin scripts/build-app.sh" >&2
    exit 1
fi
[ -f "$APP/Contents/Resources/THIRD-PARTY-NOTICES.md" ] || { echo "build-dmg: THIRD-PARTY-NOTICES.md is not in the bundle; rebuild it" >&2; exit 1; }
READONLY="$(find "$APP" -type f ! -perm -u+w | head -5)"
[ -z "$READONLY" ] || { echo "build-dmg: files in $APP are read-only for their owner (xattr -drs would fail on them); rebuild it:" >&2; echo "$READONLY" | sed 's/^/build-dmg:   /' >&2; exit 1; }
ARCH="$(lipo -archs "$APP/Contents/MacOS/Seneca")"
macho_files "$APP" | while IFS= read -r -d '' f; do
    lipo -archs "$f" | tr ' ' '\n' | grep -qx "$ARCH" || { echo "build-dmg: ${f#"$APP"/} lacks $ARCH" >&2; exit 1; }
done
MINOS="$(plutil -extract LSMinimumSystemVersion raw "$PL")"
NAME="Seneca-$VERSION-macOS-$ARCH"
DMG="$DIST/$NAME.dmg"
echo "build-dmg: Seneca $VERSION_FULL, $ARCH, macOS $MINOS or later -> $DMG"

# 2. contents of the image, staged outside the repository and without extended attributes: a sync client
# (Dropbox: com.dropbox.attrs) or macOS (quarantine) tags the files of a working copy, and hdiutil would
# carry the tags into the published image. Only com.apple.provenance (and com.apple.FinderInfo, which the
# Finder may set) remain: macOS puts it on every file
# that a process started from a tagged file writes (this script in a working copy, hdiutil as its child);
# it holds a number that means something only to the ExecPolicy database of the building Mac.
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/seneca-dmg-stage.XXXXXX")"
chmod 755 "$STAGE"                          # the root folder of the image (mktemp makes it 700)
MNT="$(mktemp -d "${TMPDIR:-/tmp}/seneca-dmg-mnt.XXXXXX")"
cleanup() {
    hdiutil detach -quiet "$MNT" 2>/dev/null || hdiutil detach -quiet -force "$MNT" 2>/dev/null || true
    rmdir "$MNT" 2>/dev/null || true
    rm -rf "$STAGE"
}
trap cleanup EXIT
ditto --norsrc --noextattr --noqtn --noacl "$APP" "$STAGE/Seneca.app"
cp -X "$HERE/THIRD-PARTY-NOTICES.md" "$STAGE/"
if [ -f "$HERE/LICENSE" ]; then cp -X "$HERE/LICENSE" "$STAGE/LICENSE.txt"; else echo "build-dmg: WARNING: LICENSE missing, not in the image" >&2; fi
case "$ARCH" in arm64) MACS="Macs with Apple silicon" ;; x86_64) MACS="Intel Macs" ;; *) MACS="$ARCH Macs" ;; esac
cat > "$STAGE/README.txt" <<EOF
SENECA $VERSION for macOS ($ARCH: $MACS; macOS $MINOS or later)

Install: drag Seneca onto Applications.

The app is not notarized by Apple, so macOS blocks the first start of a downloaded copy:

1. Double-click Seneca in Applications. macOS says "“Seneca” Not Opened".
   Click "Done" (not "Move to Trash").
2. Open System Settings > Privacy & Security and scroll down to "Security".
   Next to "“Seneca” was blocked to protect your Mac." click "Open Anyway",
   confirm with "Open Anyway" and your password or Touch ID.
   This needs to be done only once.

On macOS 14 and earlier, Control-click Seneca in Applications, choose Open,
then click Open.

Or, on any version, in Terminal:

    xattr -drs com.apple.quarantine /Applications/Seneca.app

Documentation and source code:
https://github.com/steinbeck/seneca-jcics-modern
Checksum of this image (.sha256), release notes, and the source code of the
third-party libraries and the Java runtime inside the app:
https://github.com/steinbeck/seneca-jcics-modern/releases/tag/v$VERSION
EOF
ln -s /Applications "$STAGE/Applications"

# 3. the image
rm -f "$DMG" "$DMG.sha256"
hdiutil create -quiet -volname "Seneca $VERSION" -srcfolder "$STAGE" -fs HFS+ -format "$DMG_FORMAT" -ov "$DMG"
hdiutil verify -quiet "$DMG"

# 4. check the copy inside: mounted read-only, never shown in the Finder
hdiutil attach -quiet -readonly -nobrowse -noautoopen -mountpoint "$MNT" "$DMG"
broken() { echo "build-dmg: $1; $DMG removed" >&2; rm -f "$DMG"; exit 1; }
codesign --verify --deep --strict "$MNT/Seneca.app" || broken "the app in the image has no valid signature"
[ "$(readlink "$MNT/Applications")" = /Applications ] || broken "the Applications link is missing"
XATTRS="$(xattr -r -s "$MNT" 2>/dev/null | grep -vE ': com\.apple\.(provenance|FinderInfo)$' || true)"
[ -z "$XATTRS" ] || { echo "$XATTRS" | head >&2; broken "files in the image carry extended attributes"; }
echo "build-dmg: image contents: $(ls -A "$MNT" | tr '\n' ' ')"
cleanup
trap - EXIT

# 5. checksum
(cd "$DIST" && shasum -a 256 "$NAME.dmg" > "$NAME.dmg.sha256")
echo "build-dmg: $DMG ($(du -h "$DMG" | cut -f1 | tr -d ' '), $(stat -f %z "$DMG") bytes)"
echo "build-dmg: sha256 $(cut -d' ' -f1 "$DMG.sha256")"
