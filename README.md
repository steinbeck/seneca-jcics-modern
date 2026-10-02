# SENECA – JCICS-Publikationsversion, portiert auf modernes Java und CDK

Ziel: Funktional 1:1 identische Portierung von SENECA (Steinbeck, J. Chem. Inf. Comput. Sci. 2001, 41(6), 1500–1507,
DOI 10.1021/ci000407n) auf aktuelles Java, aktuelles CDK und aktuelle Bibliotheken, lauffähig als `Seneca.app` unter macOS.
Verteiltes Rechnen über RMI entfällt; die Strukturgenerierung läuft im Prozess.

- `legacy/original/` – unveränderter Quellbaum der Publikationsversion (aus github.com/steinbeck/seneca-jcics, Commit fb5ffa3)
- `legacy/dist-2001/` – das 2001 gebaute Original-Jar `seneca-client.jar`
- `src/main/java/io/github/steinbeck/seneca/` – die Portierung: Rechenkern (`chem`, `judges`, `model`, `io`, `config`,
  `structgen`), Kommandozeile (`cli`) und die Swing-Oberfläche (`app`)
- `golden/` – Referenzergebnisse der Originalversion von 2001 (Golden Master), gegen die alles geprüft wird
- `docs/USER-GUIDE.md` – Kurzanleitung für das Programm
- `docs/port/` – Entwurf (`design.md`, `gui-design.md`) und alle Abweichungen von 2001 (`DEVIATIONS.md`)

## Voraussetzungen

- macOS, JDK 25 mit `jlink` und `jpackage`, z.B. Homebrew `openjdk`:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home`
- Maven 3. Alle Abhängigkeiten liegen nach dem ersten Bauen im lokalen Repository; danach geht alles mit `-o`.

## Bauen und testen

```bash
mvn -o test                 # schnelle Testsuite (rund 3 Minuten), Ausgabe in target/
mvn -o -Pslow,oracle test   # alle Tests inkl. lange Golden-Läufe und Vergleich mit dem Code von 2001 (rund 40 Minuten)
```

`-Poracle` setzt `legacy/runnable/classes` voraus (`legacy/runnable/build.sh`). Ein eigenes Ausgabeverzeichnis
gibt `-Dbuild.dir=$PWD/target-<name>`. Die GUI-Tests laufen ohne Bildschirm: sie bedienen die Oberfläche per
Programm und schreiben Bilder aller Fenster nach `target/gui-shots/`. `GuiEndToEndTest` lädt pinene, eurabidiol
und polycarpol über die Oberfläche, konfiguriert sie wie 2001, rechnet mit festem Startwert und vergleicht das
Ergebnis mit `golden/runs`.

## Die Mac-App bauen

```bash
scripts/build-app.sh        # erzeugt dist/Seneca.app (eigene Java-Laufzeit, Icon, Beispieldaten, Ad-hoc-Signatur)
scripts/launch-test.sh      # startet die gebaute App unbeaufsichtigt (Selbsttest) und beendet sie wieder
open dist/Seneca.app        # starten
```

`build-app.sh` baut das Jar (`mvn package`, ohne Tests), sammelt die Laufzeit-Jars, erzeugt mit `jlink` eine
verkleinerte Java-Laufzeit und mit `jpackage` das App-Bundle (`io.github.steinbeck.seneca`, Version aus `pom.xml`).
Das Icon zeichnet CDK selbst: α-Pinen in allen Icon-Größen, zusammengesetzt mit `iconutil`. `.sml`-Dateien werden
der App zugeordnet. `dist/` ist bis auf `dist/README.md` von Git ausgenommen.

`launch-test.sh` startet die App dreimal: direkt und über `open` jeweils im Selbsttest (`--smoke`: pinene öffnen,
alle Reiter zeichnen, wie 2001 konfigurieren, mit Startwert 1 rechnen; das Ergebnis muss dem Golden-Lauf
entsprechen, 21248 Iterationen, Score 2400/2400), dann einmal normal mit einem Datensatz. Protokolldateien gehen
dabei in ein temporäres Verzeichnis.

## Ohne App starten

```bash
scripts/run-app.sh [datei.sml]                                     # GUI aus dem Build-Verzeichnis (BUILD_DIR, Standard target)
JAVA_OPTS="-Dseneca.logdir=/tmp/seneca" scripts/run-app.sh        # Protokolldateien nicht ins Home-Verzeichnis
java -cp target/classes:... io.github.steinbeck.seneca.cli.Main    # Kommandozeile (docs/port/design.md, Abschnitt 10)
```

Bedienung: `docs/USER-GUIDE.md`.
