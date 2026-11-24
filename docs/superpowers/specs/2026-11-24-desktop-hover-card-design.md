# PC: Info-Karte beim Überfahren von Film- und Serienkacheln, Hand-Mauszeiger

**Stand:** 24.11.2026 · Größe: kleine Änderung · Plattform: PC (Windows/Linux), dazu `shared` für die Serien-Beschreibung

## Warum

Die Kacheln schneiden den Titel nach einer Zeile ab, eine Beschreibung gibt es in der Übersicht gar nicht. Wer am PC
mit der Maus stöbert, will den vollen Titel und worum es geht lesen, ohne den Titel erst zu öffnen. Bedient wird nur
mit der Maus; eine Pfeiltasten-Navigation im Raster ist nicht gewünscht.

## Ziele

- Verweilt die Maus etwa 450 ms auf einer **Film- oder Serienkachel**, schwebt darüber eine größere **Info-Karte**
  (Variante A): Bild, voller Titel, Jahr · Genre · Bewertung, 4–6 Zeilen Beschreibung. Sie verdeckt kurz die
  Nachbarn und klappt am rechten Rand nach links auf, damit sie im Fenster bleibt.
- Gilt überall, wo die Kachel `MediaCard` steht: Film- und Serienübersicht, Startseiten-Reihen.
- **Hand-Mauszeiger** auf allen klickbaren Elementen der PC-App (Kacheln, Knöpfe, Kategorien, Seitenleiste,
  Menüeinträge). Über Textfeldern bleibt der Textcursor.

## Nicht-Ziele

- Pfeiltasten-/Fernbedienungs-Navigation im Raster.
- Live-Sender und Folgen-Kacheln (die haben keine Beschreibung in diesem Sinn).
- TV und iPhone.

## Ansätze

- **A. Info-Karte über der Kachel** (gewählt): am ehesten „das Element wird hervorgehoben“.
- **B. Info-Blase daneben:** einfacher, wirkt aber wie ein Tooltip.
- **C. Feste Vorschau-Zeile über dem Raster:** verdeckt nichts, aber der Blick wandert und das Raster rückt nach
  unten.

## Design

- **Darstellung:** ein `Popup`, das an der Kachel verankert ist. Es muss aus dem Zuschnitt des Rasters
  herauskommen. Die Breite ist etwa doppelt so groß wie die Kachel. Rahmen und Schatten folgen dem heutigen
  Hover-Stil (Signal-Rahmen), Farben kommen aus `Palette`.
  - Die Karte schließt, sobald die Maus die Kachel und die Karte verlässt.
  - Ein Klick auf die Karte wirkt wie ein Klick auf die Kachel (öffnen) und nimmt den Stern zum Merken mit.
- **Beschreibung von Serien:** Die Serienliste des Anbieters (`get_series`) liefert `plot` bereits mit. Neues Feld
  `Item.plot: String? = null` wird nur für Serien aus der Liste gefüllt, so wie `backdrop`/`trailer`. Weil der
  Playlist-Zwischenspeicher die Rohdaten neu einliest, ist keine Migration nötig.
- **Beschreibung von Filmen:** Beim Öffnen der Karte wird `app.requestMediaInfo(item)` angestoßen, und zwar erst
  nach dem Verweilen, damit Mausbewegungen über das Raster keine Anfragen auslösen. Die Daten kommen aus
  `app.mediaInfoFor(item)` und sind dort schon zwischengespeichert. Bis sie da sind, stehen Titel und Fakten,
  dazu eine Zeile „Beschreibung wird geladen …“. Ohne Beschreibung entfällt der Absatz.
- **Fakten:** Jahr, Genre und Bewertung kommen wie heute aus `posterFacts(item)` bzw. `MediaInfo`, sobald es
  geladen ist.
- **Mauszeiger:** ein gemeinsamer Modifier `Modifier.handCursor()` in `Components.kt` mit
  `pointerHoverIcon(PointerIcon.Hand)`. Er wird bei allen klickbaren Elementen der PC-Oberfläche angewendet, auch in
  `PrimaryButton`/`SecondaryButton` und `MediaCard`, damit neue Stellen ihn automatisch erben.

## Dem Bau überlassen

Das genaue Layout und die Animation der Karte im Stil der App, die Verweilzeit im Bereich 400–500 ms und die
Ausrichtung am Fensterrand.

## Tests

- `shared`: Der Parser übernimmt `plot` aus der Serienliste, Filme und Sender bleiben ohne.
- PC (`desktopApp/src/test`, Compose-UI-Test):
  - Nach dem Verweilen erscheint die Karte mit vollem Titel und Beschreibung.
  - Sie verschwindet beim Verlassen.
  - Für einen Film wird die Detailabfrage erst nach dem Verweilen gestellt.

## Umsetzung

Branch `feat/desktop-hover-card` von `master`. Gebaut wird in einem eigenen Worktree, weil im Haupt-Checkout
gerade die Statistik-Umsetzung läuft.
