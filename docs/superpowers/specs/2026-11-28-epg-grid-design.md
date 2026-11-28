# Programmführer als Raster in der Senderliste (TV) – Design

**Stand:** 2026-11-28 · Projekt · freigegeben
**Plattformen:** TV 🔄 (diese Spec) · PC ❌ später, eigene Spec · iPhone ❌ später, eigene Spec

## Warum

Beim Schauen will Vincent sehen, was **auf dem laufenden Sender** und **auf den anderen Sendern derselben Kategorie**
gerade und gleich läuft. Heute zeigt die Senderliste im Player (`ChannelGuide`) je Sender nur „Jetzt läuft“; das
Programm gibt es nur für den laufenden Sender als Liste (`ProgrammePanel`). Alle verglichenen IPTV-Apps haben ein
EPG-Raster, TiviMate wird genau dafür gekauft ([Feature-Review](../../ideen/2026-11-28-feature-review.md)).
Erster Punkt „Als Nächstes“ in der [Roadmap](../../ROADMAP.md).

- **Ziel:** Die Senderliste im Player wird zum Raster – Sender untereinander, Sendungen auf einer Zeitleiste, Start bei
  „jetzt“, mit der Fernbedienung durch Sender und Zeit.
- **Nicht-Ziele (diese Spec):** Raster auf der Live-TV-Seite; Ersatz von `ProgrammePanel` (folgt, wenn das Raster sich
  bewährt); Erinnerungen (nächster Roadmap-Punkt – nur Platz dafür lassen); EPG für M3U/XMLTV; PC und iPhone.
- **Anti-Ziele:** Öffnen der Senderliste wird langsamer als heute (Liste steht sofort, Programm füllt sich nach);
  Ruckeln auf dem Fire-TV-Stick (Emulator `AndroidTV_1080p_API28` als Maßstab); Anfragen-Flut an den Anbieter.

## Ansätze

- **Laden: Programm je sichtbarem Sender nachladen** – `get_simple_data_table` wie heute `Repository.programme()`,
  Ergebnis in `EpgStore.programmes`. **Gewählt:** baut auf Vorhandenem auf, skaliert auch bei 32 294 Sendern
  (Vincents Anbieter), weil nur eine Kategorie und nur sichtbare Zeilen laden.
- *Verworfen:* `xmltv.php` auf einmal – bei großen Anbietern Hunderte MB, zu viel für einen Fire-TV-Stick.
- *Verworfen:* nur `get_short_epg` – ~10 Sendungen, kein Archiv-Kennzeichen, reicht nicht für mehrere Tage.
- **Ort in der Oberfläche** (Vincent): **A** – die Senderliste wird zum Raster, ein Ort, eine Gewohnheit. *Verworfen:*
  B – eigene Ansicht auf eigener Taste (Fire-TV-Fernbedienungen haben keine Programmtaste). C – auch `ProgrammePanel`
  ersetzen – später, wenn A sich bewährt.
- **Zeitleiste** (Vincent): Start bei „jetzt“, nach rechts so weit der Anbieter Daten liefert (meist mehrere Tage),
  nach links nur bei Sendern mit Archiv.

## Verhalten

### Öffnen und Aufbau

- Öffnen wie heute mit OK im Player. Links die Kategorien-Spalte (unverändert), vorausgewählt die Kategorie des
  laufenden Senders; rechts das Raster. Fokus beim Öffnen: **Zelle der laufenden Sendung des laufenden Senders**.
- Je Zeile links ein fester Sender-Kopf (Nummer, Logo, Name, Favoriten-Stern – wie `ChannelRow` heute), rechts die
  Sendungen als Zellen, Breite proportional zur Dauer. Kopfzeile mit Uhrzeiten (halbstündlich), senkrechte
  „Jetzt“-Linie. Der laufende Sender ist markiert (▶).
- Sichtbar etwa **2 Stunden**; das Fenster beginnt beim Öffnen an der letzten halben Stunde vor „jetzt“.
- Solange ein Sender kein Programm hat: Zeile mit „Programm wird geladen …“, danach Zellen; liefert der Anbieter
  nichts, eine durchgehende Zelle „Keine Programmdaten“ (fokussierbar, OK schaltet um).
- Sendungen, die nur teilweise im Fenster liegen, werden am Rand abgeschnitten; Lücken zwischen Sendungen werden als
  leere Zellen gefüllt (fokussierbar, OK schaltet um).
- Die Kategorien-Spalte darf sich schmal machen oder ausblenden, solange der Fokus im Raster ist, und kommt mit ◀ an
  der linken Kante zurück (Platz fürs Raster).

### Bedienung

- **▲▼** wechselt den Sender. Der Fokus bleibt **an derselben Zeitstelle**: Fokus geht auf die Zelle, die den
  Zeitanker der vorigen Zelle überdeckt (Zeitanker = Beginn der fokussierten Zelle, mindestens Fensterbeginn).
- **▶ / ◀** springt Sendung für Sendung; das Fenster scrollt mit, sobald die Zelle den Rand erreicht.
- **Eine Zeitachse für alle Zeilen** (Vincent, Variante A): Grenzen gelten fürs ganze Raster, nicht je Zeile.
  - **Linke Grenze:** `jetzt − max(archiveDays)` über die Sender der Kategorie; hat keiner Archiv, der Fensterbeginn
    beim Öffnen. **◀ an der linken Grenze** führt in die Kategorien-Spalte.
  - **Rechte Grenze:** spätestes Datenende über die geladenen Zeilen.
  - **Vergangenheit:** Alle Zeilen zeigen vergangene Sendungen, soweit der Anbieter sie liefert; nicht abspielbare
    (kein Archiv) sind abgedunkelt. ▲▼ behält die Zeitstelle auch dort; OK zeigt Infos.
  - **Keine Daten an der Zeitstelle** (Zeile endet früher oder beginnt später): Zelle „Keine Programmdaten“ über den
    fehlenden Bereich, fokussierbar, OK schaltet um.
- **OK** – je nach Sendung:
  - läuft gerade → auf diesen Sender umschalten (wie heute `onPick`);
  - vergangen und `EpgStore.canCatchUp(channel, entry)` → nachträglich abspielen (wie `ProgrammePanel`);
  - vergangen ohne Archiv → Infos;
  - künftig → Infos (Titel, Zeit, Beschreibung); dort entsteht später „Erinnern“.
- **OK halten** → Optionen des Senders wie heute (`itemMenu`). **Zurück** → schließt (wie heute).
- Tastenhinweise unten anpassen: ▲▼ „Sender“, ◀▶ „Zeit“, OK je nach Fokus „Umschalten“ / „Abspielen“ / „Infos“.

### Infos zu einer Sendung

Kleine Karte über dem Raster (kein neuer Bildschirm): Titel, Sender, Tag und Uhrzeit, Beschreibung; bei Archiv
Knopf „Abspielen“, bei laufender Sendung „Umschalten“. Zurück schließt die Karte. Platz für einen späteren Knopf
„Erinnern“.

## Daten und Logik

### Laden (`shared/…/state/EpgStore.kt`)

- Je **sichtbarer Zeile** plus ~5 Zeilen Vorrat oben/unten `requestProgramme(item)` – vorhandene Funktion mit
  Auffrischungsgrenze `PROGRAMME_REFRESH_MS` und Anbieterwechsel-Schutz (`session.repo === r`) bleibt.
- **Neu:** höchstens **4 gleichzeitige** Programm-Abrufe im `EpgStore`; weitere warten in einer Warteschlange.
  - Verlässt eine Zeile das Bild (inkl. Vorrat), wird ihr **wartender** Abruf verworfen; ein **laufender** darf zu Ende
    laufen, sein Ergebnis landet im Zwischenspeicher. Die zuletzt angefragten Zeilen kommen zuerst dran.
  - Ein Sender gilt erst nach **erfolgreichem** Laden als geladen (`programmeFetchedAt` erst dann setzen) – sonst gälte
    ein verworfener Abruf bis zu `PROGRAMME_REFRESH_MS` als erledigt.
  - Hintergrund: Schnelles Blättern durch „Alle Sender“ (32 294) oder durch Kategorien (Fokus wählt die Kategorie aus)
    darf keine Anfragen-Flut auslösen.
- **Rückfall:** Liefert `programme()` leer, nimmt die Zeile die kurzen „Jetzt/Danach“-Daten aus dem vorhandenen
  `requestEpg`/`nowFor`-Zwischenspeicher.
- Zeiten im **selben Maßstab wie heute**: `startAt`/`endAt` („yyyy-MM-dd HH:mm:ss“) gegen `localNow()`, umgerechnet
  über `epgSeconds` – nicht die Timestamps (siehe KDoc an `notEndedAt`). Programme gehen durch `cleaned()`.

### Raster-Logik (neu, `shared/…/EpgGrid.kt`, ohne Compose)

Rein funktional und in `shared` getestet, damit PC und iPhone sie später ohne zweiten Logik-Stand nutzen:

- Zeilenzellen für ein Zeitfenster: Sendungen auf das Fenster zuschneiden, Lücken als leere Zellen füllen, Zeile ohne
  Daten als eine durchgehende Zelle; Ergebnis je Zelle: Sendung (oder keine), Beginn/Ende in Sekunden, abgeschnitten
  links/rechts.
- Zelle an einer Zeitstelle finden (für ▲▼).
- Nächste/vorige Zelle (für ◀▶), inklusive Grenze links (jetzt bzw. Archivtiefe) und rechts (Ende der Daten).
- Einordnung einer Sendung: läuft / vergangen / künftig zur Ortszeit `localNow`.
- Fensterbeginn beim Öffnen: letzte volle oder halbe Stunde vor „jetzt“.

### Testdaten (`tools/demo-server/demo_server.py`)

Der Demo-Server liefert heute nur `get_short_epg` mit 2 Sendungen. **Neu:** `get_simple_data_table` mit Sendungen von
3 Tagen zurück bis 3 Tagen vor (wechselnde Längen 15–120 min, damit Zuschnitt und ▲▼-Zeitanker sichtbar werden),
`has_archive: "1"` für vergangene Sendungen bei einigen Sendern, dazu `tv_archive`/`tv_archive_duration` in
`get_live_streams` für diese Sender; mindestens ein Sender ganz ohne Programm. Format wie echte Anbieter
(Base64-Titel/-Beschreibung, `start`/`end` als Text).

## Tests

- **`shared` (desktopTest):** Raster-Logik – Zuschnitt am Fensterrand, Lückenfüllung, Zeile ohne Daten, Zelle an
  Zeitstelle (auch auf Lücken und Grenzen), ◀▶ inkl. Grenzen mit/ohne Archiv, Einordnung läuft/vergangen/künftig,
  Fensterbeginn, linke Grenze mit/ohne Archiv in der Kategorie, Zeile mit Datenende vor der Zeitstelle; `EpgStore`: höchstens 4
  gleichzeitige Abrufe, wartender Abruf wird beim Verlassen verworfen, verworfener Abruf gilt nicht als geladen, Rückfall auf Kurz-EPG, Anbieterwechsel verwirft Antwort.
- **`androidApp` (androidTest, `ChannelGuideTest`):** Fokus beim Öffnen auf laufender Sendung; ▲ hält Zeitstelle;
  OK auf laufender Sendung schaltet um; OK auf künftiger zeigt Infos; ◀ an der Grenze führt in die Kategorien.
- **Von Hand im Emulator** mit Demo-Server (API 34 und API 28): Öffnen ohne spürbare Verzögerung, flüssiges Scrollen.

## Abschluss

- `docs/PLATTFORMEN.md`: Zeile „Programmführer als Raster“ TV → ✅; Roadmap-Punkt streichen.
- PR mit Label `platform:tv`.

## Dem Umsetzenden überlassen

Pixelmaße (Zeilenhöhe, Minuten-Breite, wie stark die Kategorien-Spalte schrumpft), genaue Texte, Animationen,
Aufteilung in Composables, Größe des Vorrats beim Vorabladen, Darstellung der „Jetzt“-Linie. Farben nur aus
`Palette.kt`, Gestaltung nach `.tastemaker/style-lock.md` (Bernstein nur für Auswahl/„Live“/Fortschritt).
