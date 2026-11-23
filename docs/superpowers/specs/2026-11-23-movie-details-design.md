# Filme: Detailseite vor dem Abspielen

**Stand:** 23.11.2026 · Größe: mittel · Fire TV + PC (iPhone später in Etappe 3)

## Absicht
Beim Auswählen eines Films soll man **entscheiden können, ob man ihn schauen will** – deshalb zuerst eine Detailseite
mit Beschreibung und allen Infos, die der Anbieter liefert, statt sofort abzuspielen.

## Ziele / Nicht-Ziele
- Detailseite mit Poster, Hintergrundbild, Titel (+ Originaltitel, wenn anders), Jahr, Bewertung, Dauer, FSK, Genre,
  Land, Handlung, Regie, Besetzung – jedes Feld nur, wenn vorhanden.
- Knöpfe: „Abspielen“ bzw. „Weiterschauen ab H:MM:SS“ + „Von vorn“; „Merken“; Fire TV zusätzlich „Herunterladen“.
- **Kein Trailer** (YouTube-IDs lassen sich nicht im eigenen Player abspielen – entschieden: weglassen).
- Keine zusätzliche Anfrage: dieselbe `get_vod_info`-Antwort wie für die Pause-Info, derselbe Zwischenspeicher.

## Ansatz (einziger sinnvoller)
Neuer Bildschirm `Screen.Movie(item)` in `AppState` wie `Screen.Series` (gleiche Bedienung, „Zurück“ eindeutig).
Ein Overlay über der Liste wurde verworfen (andere Bedienung als die Serienseite, doppeldeutiges Zurück auf Fire TV).

## Entscheidungen
- `AppState.open(list, index)`: Film → Detailseite (`Screen.Movie`); alles andere wie bisher.
- Neu `AppState.play(list, index, fromStart = false)`: spielt direkt (Detailseite, „Weiterschauen“-Reihen).
  „Weiterschauen“ auf Start-/Bereichsseiten und Downloads spielen direkt – die Entscheidung ist dort schon gefallen
  *(Claudes Entscheidung)*.
- `MediaInfo` bekommt `director`, `cast` (Liste, aus `cast` oder `actors`), `country`, `originalTitle` (`o_name`, nur wenn
  vom Namen verschieden), `backdrop` (`backdrop_path[0]`, sonst nichts). Bewertung kann Zahl oder Text sein.
- Daten des Anbieters (geprüft 23.11.2026, 4 Filme): `director`, `cast`/`actors`, `backdrop_path`, `country`, `o_name`
  meist gefüllt; `age` leer; `youtube_trailer` teils gefüllt (ungenutzt).

## Tests
- Parser: neue Felder inkl. Zahl-Bewertung, leere Felder → null/leer.
- `open` für Film → `Screen.Movie`, Infos werden angefordert; `play` → `Screen.Player` mit Startposition bzw. 0 bei „Von vorn“.
- Serien, Live TV, Folgen unverändert (bestehende Tests).
