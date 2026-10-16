# dist/

Build output for the macOS application. Only this file is versioned. Git ignores `Seneca.app` and the disk
image (`.gitignore`); they are published as assets of a GitHub release instead. The release carries the
source code of the third-party software inside the app next to the image, because the GNU GPL and LGPL ask
that the source be offered from the same place as the binaries.

```bash
RUNTIME_JDK=temurin scripts/build-app.sh   # dist/Seneca.app with a self-contained Java runtime
scripts/launch-test.sh                     # starts the app unattended (self-test) and quits it again
scripts/build-dmg.sh                       # dist/Seneca-<version>-macOS-arm64.dmg and its .sha256
scripts/collect-sources.sh                 # the source code of the bundled third-party software (online)
```

`build-app.sh` runs Maven offline; on a machine whose local Maven repository lacks a plugin or library, prefix
the first call with `MVN_OFFLINE=` (empty).

| File | What it is |
|---|---|
| `Seneca.app` | The application: SENECA, its 27 libraries, the ten example datasets of 2001 (`Contents/app`), a Java 25 runtime cut down with `jlink` (`Contents/runtime`), `THIRD-PARTY-NOTICES.md` and `LICENSE` (`Contents/Resources`). Apple silicon only, macOS 11 or later. Ad-hoc signed, not notarized. |
| `Seneca-<version>-macOS-arm64.dmg` | The release asset: `Seneca.app` and a link to `/Applications` (drag to install), `README.txt` (installation and the Gatekeeper steps), `THIRD-PARTY-NOTICES.md`, and `LICENSE.txt`. |
| `Seneca-<version>-macOS-arm64.dmg.sha256` | Its checksum; `shasum -a 256 -c Seneca-<version>-macOS-arm64.dmg.sha256` checks a download. |
| `Seneca-<version>-third-party-sources.zip` | `scripts/collect-sources.sh`: the `-sources.jar` of each of the 27 libraries in `Contents/app`, from Maven Central, with `SOURCES.txt`. |
| `OpenJDK25U-jdk-sources_<release>.tar.gz` | `scripts/collect-sources.sh`: the source of the bundled Java runtime, Eclipse Adoptium's archive for the Temurin release in `build-app.sh`, checked against a pinned SHA-256. |

**Why `RUNTIME_JDK=temurin`.** `build-app.sh` makes the bundled runtime with the `jlink` of `RUNTIME_JDK`
(default: `JAVA_HOME`). Homebrew's `openjdk` links its font, colour, and image libraries against other Homebrew
packages under `/opt/homebrew`, so an app built from it runs only on Macs that have those packages; the build
warns, and `build-dmg.sh` refuses to package it. `RUNTIME_JDK=temurin` downloads Eclipse Temurin (the release
pinned in `build-app.sh`, checked against its SHA-256) once into `~/Library/Caches/seneca-build` and builds the
runtime from it. Any other self-contained JDK 25 works as well: `RUNTIME_JDK=/path/to/jdk/Contents/Home`.

**What `build-dmg.sh` checks.** Before packing, it checks the signature, that the bundle version matches
`pom.xml`, that no native library of the app loads anything from outside macOS and the bundle, that every
binary is built for the same architecture, that the notices are in the bundle, and that every file is writable
by its owner (otherwise a user's `xattr -drs com.apple.quarantine` fails on it; `build-app.sh` makes the
runtime writable after `jlink`). After packing, it mounts the image read-only, without showing it in the
Finder, verifies the signature of the app inside and the `/Applications` link, and checks that no file carries
extended attributes from the build machine (Dropbox and quarantine tags; only macOS's own
`com.apple.provenance` and the Finder's `com.apple.FinderInfo` are allowed).

**Gatekeeper.** The app is ad-hoc signed and not notarized, so macOS blocks the first start of a downloaded
copy. `README.md` ("Installation") and `README.txt` on the disk image tell users what to do about it.
On the machine that built it, `dist/Seneca.app` starts directly (`open dist/Seneca.app`).

Details of the build steps: `scripts/build-app.sh` and `scripts/build-dmg.sh` (comments at the top), and
`docs/port/gui-design.md`, section 15.
