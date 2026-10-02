# SENECA – Kurzanleitung

SENECA sucht zu einer Summenformel und NMR-Daten die Konstitution, die am besten zu den Spektren passt
(Simulated Annealing; Steinbeck, J. Chem. Inf. Comput. Sci. 2001, 41, 1500). Diese Fassung ist die
Publikationsversion von 2001, portiert auf Java 25 und CDK 2.13. Fenster, Menüs und Beschriftungen sind die
von 2001 und daher englisch.

## 1. Starten

- **Seneca.app**: Doppelklick auf `dist/Seneca.app` (gebaut mit `scripts/build-app.sh`, siehe `README.md`).
  Ein Doppelklick auf eine `.sml`-Datei öffnet sie ebenfalls in SENECA.
  Die App ist nur ad hoc signiert. Auf einem anderen Mac als dem, auf dem sie gebaut wurde, beim ersten
  Start Rechtsklick ▸ Öffnen wählen.
- **Ohne App, aus dem Quellbaum**: `scripts/run-app.sh [datei.sml]`.

Das Hauptfenster hat oben die Werkzeugleiste (Neu, Öffnen, Speichern) und unten die Statuszeile mit Name und
Summenformel des aktuellen Datensatzes. Jeder geöffnete Datensatz bekommt ein eigenes Fenster im Hauptfenster.
Zwischen mehreren Datensätzen wechselt man mit **Datasets ▸ switch**.

## 2. Datensatz laden

- **File ▸ Open ▸ Example Dataset**: die zehn Beispiele von 2001 (pinene, eurabidiol, polycarpol …).
  Zum Kennenlernen `pinene` nehmen. Der Lauf dauert dort nur Sekunden.
- **File ▸ Open ▸ SENECA Dataset**: eine eigene `.sml`-Datei. Der Dialog startet beim ersten Mal im Ordner
  der Beispiele.
- **File ▸ Import ▸ LUCY dataset**: eine LUCY-Datei (`.col`).
- **File ▸ New Dataset**: ein leerer Datensatz. Die Summenformel trägst Du unter *General Settings* ein,
  die Signale unter *NMR Spectra* (von Hand oder mit „Paste WinNMR pp from clipboard").
- **File ▸ Save ▸ SENECA Dataset** speichert den aktuellen Datensatz als SML.

## 3. Datensatz vorbereiten

Das Datensatzfenster hat sieben Reiter. Für einen Lauf braucht es drei Schritte.

**Atom Properties.** Fehlen den Kohlenstoffen die Verschiebungen, drückst Du **Autoassignment**: SENECA ordnet
die 13C-Signale den Atomen zu und bestimmt die H-Zahlen aus den DEPT-Spektren. Die Zeile darunter zeigt die
H-Bilanz. Ist sie **grün**, stimmt die Summe der Wasserstoffe mit der Formel überein. Ist sie **rot**, fehlen
Wasserstoffe an Heteroatomen, und Du trägst sie in der Spalte *Hydrogens* ein. Beispiel eurabidiol: nach
Autoassignment fehlen 2 H, je 1 in die Zeilen O-16 und O-17 eintragen, dann wird die Bilanz grün.

**Judges.** Die Judges bewerten eine Kandidatenstruktur gegen die Daten. Jeder hat einen Unterreiter:
H,H-COSY und C,H-long-range Corr. (2D-Korrelationen, HMBC), Carbon Shifts (HOSE-Code-Vorhersage der
13C-Verschiebungen), Symmetric Atoms Classes, Features, Hybridization und Heterobond Attachments.
In jedem Unterreiter:

- **Autoconfigure Judge** füllt den Judge aus den Spektren. Bewährt ist: H,H-COSY, C,H-long-range Corr.,
  Carbon Shifts und Features nacheinander automatisch konfigurieren.
- **Activate this Judge?** schaltet den Judge ein oder aus. Ein 2D-Judge ohne passendes Spektrum muss aus sein.
- **Report Judgeconfig** zeigt die Konfiguration eines 2D-Judges als Text.

Die Judge-Konfiguration wird beim Speichern **nicht** mit abgelegt (wie 2001). Nach dem Öffnen also
Autoconfigure wiederholen.

**Annealing Schedule.** Der Kühlplan. Hat der Datensatz keinen, legt **New ->** einen mit den Standardwerten an.
Werte ändern: Eintrag rechts auswählen, Felder links ändern, **-> Update ->**.

## 4. Rechnen

Reiter **Structure Generation**:

1. **Add run slot** legt einen Rechenplatz an (eine Zeile der Tabelle). Mehrere Plätze rechnen parallel,
   jeder mit eigenem Zufallsstart.
2. Zeile(n) markieren, **Start as selected**. Läuft ein Platz schon, wird sein Lauf erst beendet.
3. **Stop Selected** hält an. **Reset selected** verwirft Lauf und Kurven. **Clear Slot List** beendet alle
   Läufe und leert die Tabelle; der nächste Platz heißt wieder `local0`.

Ein Lauf rechnet auch dann mit voller Geschwindigkeit, wenn SENECA im Hintergrund liegt: solange gerechnet
wird, bittet SENECA macOS, das Programm nicht zu drosseln (App Nap).

Fehlt etwas (keine Atome, kein Kühlplan, ein Judge passt nicht zur Formel), meldet SENECA das, und nichts startet.

## 5. Ergebnisse lesen

- **Tabelle**: Status („Running", „Holding Results" = fertig, „Stopped by User") und Iterationen je Platz.
- **Annealing Data**: der beste Score über die Iterationen, je Platz eine Kurve, dazu die Linie „max. Score"
  (der bestmögliche Wert). Ein Klick auf das Diagramm schaltet auf die Temperatur kT um und zurück.
- **Best structure / Evaluation**: alle zwei Sekunden die bisher beste Struktur eines Platzes (reihum) und ihre
  Bewertung je Judge. Score gleich max. Score heißt: alle Daten erklärt. Ein Doppelklick auf die Struktur
  öffnet sie groß in einem eigenen Fenster. Die Trennlinien zwischen Tabelle, Diagramm, Struktur und
  Bewertung lassen sich verschieben.
- **Summarize Result**: alle Ergebnisse aller Plätze, nach Score sortiert, in einem Fenster
  *Structure Display - <Datensatz>*.
  Tooltip einer Kachel: die Bewertung. Dort: **Save MDL** (eine Molfile je Struktur), **Save SDF** (alle in
  einer Datei), **Copy** (Molfile und Bild in die Zwischenablage, auch ⌘C), **Load MDL** und
  **Load LUCY Result** zum Anschauen anderer Strukturen. **View ▸ Current Structures** öffnet ein leeres
  solches Fenster.
- **Log Annealing Data** schreibt die Kurvendaten als Tabelle in `~/seneca.annealing.log`.
- **Logbook** (letzter Reiter): das Protokoll aller Läufe. Es zeigt nur das neueste Megabyte; das vollständige
  Protokoll steht in `seneca.log`.

Protokolldateien im Home-Verzeichnis, bei jedem Programmstart geleert: `seneca.log` (Protokoll),
`seneca.results.sdf` (jede neue beste Struktur), `seneca.annealing.log`.

## 6. Optionen beim Start

Sie werden an die App angehängt, z.B. `open -n dist/Seneca.app --args --logdir=/tmp/seneca`:

| Option | Wirkung |
|---|---|
| `--logdir=<Ordner>` | Protokolldateien dort statt im Home-Verzeichnis |
| `--seed=<n>` | reproduzierbare Läufe: der erste Platz rechnet mit Startwert n, der zweite mit n + 1 usw. |
| `--iteration-cap=<n>` | Lauf nach n Iterationen beenden |
| `--examples=<Ordner>` | anderer Ordner für File ▸ Open ▸ Example Dataset |
| `--smoke[=<Bericht>]` | Selbsttest: öffnet pinene, rechnet, schreibt einen Bericht, beendet sich |
