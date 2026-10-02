# dist/

Hier entsteht die macOS-Anwendung `Seneca.app`. Sie wird nicht versioniert (`.gitignore`), nur diese Datei.

```bash
scripts/build-app.sh      # baut dist/Seneca.app (mvn package, jlink, jpackage, Icon, Ad-hoc-Signatur)
scripts/launch-test.sh    # startet die gebaute App unbeaufsichtigt (Selbsttest) und beendet sie wieder
open dist/Seneca.app      # normal starten
```

`Seneca.app` bringt ihre eigene Java-Laufzeit (JDK 25, mit jlink verkleinert) und die zehn Beispieldatensätze
mit (`Contents/app/examples`, im Programm unter File ▸ Open ▸ Example Dataset). Die Signatur ist ad hoc: die App
startet auf dem Rechner, auf dem sie gebaut wurde. Auf einem anderen Mac verlangt Gatekeeper beim ersten Start
Rechtsklick ▸ Öffnen. Einzelheiten stehen in `docs/USER-GUIDE.md` und in `README.md`.
