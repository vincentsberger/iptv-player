# Detail-Fenster: ganzes Bild, schmaler (PC) – Design

**Stand:** 2026-11-26 · freigegeben · klein

## Warum

Im Detail-Fenster für Filme und Serien (`desktopApp/…/ui/DetailModal.kt`) fehlt oben oft viel vom Bild. Das Bild ist fest
300 dp hoch bei bis zu 960 dp Breite (3,2:1), ein Hintergrundbild aber 16:9. Liefert der Anbieter nur ein Hochformat-Poster
(Rückfall `backdrop ?: logo`), bleibt nur ein Streifen aus dessen Mitte. Außerdem wirkt das Fenster zu breit.

## Ziel

- Fensterbreite höchstens **820 dp** (statt 960); 90 % Anteil bei kleinen Fenstern bleibt.
- Hintergrundbild im vollen **16:9**, unten weicher Verlauf in `Surface1`. Höhe höchstens **45 %** der Fensterhöhe, damit
  Titel und „Abspielen“ auf kleinen Fenstern sichtbar bleiben (dann wird nur dort beschnitten).
- Nur Poster vorhanden: Poster **ganz** (`ContentScale.Fit`) mittig, dahinter eine unscharfe, vergrößerte Kopie als Füllung.
- Ohne Bild: unverändert die schmale ruhige Fläche (72 dp).
- Gilt für Filme und Serien (gemeinsames `Backdrop`).

## Nicht-Ziele

TV- und iPhone-Detailansichten bleiben unverändert.

## Ansätze

Ein sinnvoller Weg: `Backdrop` bekommt die Art des Bildes (Hintergrund oder Poster) und die verfügbare Fensterhöhe. Ein
Erkennen per Seitenverhältnis nach dem Laden schied aus: Die Höhe würde nach dem Laden springen.

## Dem Umsetzenden überlassen

Verlaufshöhe, Unschärfe-Radius, Abdunklung der Poster-Füllung; Test-Tags `detail-backdrop`, `detail-poster`.
