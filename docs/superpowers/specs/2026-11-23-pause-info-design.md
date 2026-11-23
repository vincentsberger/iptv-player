# Pause-Info

**Stand:** 23.11.2026 · Größe: kleine Änderung · Plattformen: Android TV und Desktop
Teil 2 von 6 der Player-Verbesserungen (davor: Zapp-Karte/Sendernummern; danach: Spulen mit Ziel, Info-Abzeichen, Desktop-Feinschliff).

## Warum

Beim Pausieren eines Films oder einer Folge steht heute nur ein Pause-Symbol in der Mitte. Der Anbieter liefert
aber Titel, Jahr, Bewertung, Genre, Dauer, FSK und Handlung – wie bei Streamingdiensten soll man beim Pausieren
sehen, was läuft, ohne dass kurzes Pausieren gleich das Bild mit Text füllt.

## Ziele

- Sofort beim Pausieren eine kompakte Leiste unten; nach 5 s Pause die ausführliche Info links unten (Variante C).
- Daten liegen beim Pausieren bereits vor (beim Öffnen im Hintergrund geladen).

## Nicht-Ziele

- Live-Sender (dort keine Pause-Info).
- Regie und Darsteller, Trailer, Poster.
- Eigene Handlung je Folge (liefert der Anbieter nicht).

## Anti-Ziele

- Verzögert aufpoppende Info, weil erst beim Pausieren geladen wird.
- Ein Abruf-Fehler, der die Wiedergabe oder das Pausieren stört.

## Ansatz

Ein sinnvoller Weg: `Repository.vodInfo(item)` (Xtream `get_vod_info`) bzw. Serien-Info aus dem ohnehin geladenen
`get_series_info`, zusammengefasst als `MediaInfo`, pro Sitzung im Speicher; die Zeitlogik (keine / kompakt /
ausführlich) als kleine testbare Klasse in `shared`; jede Plattform zeichnet selbst. Verworfen: erst beim Pausieren
laden (Verzögerung), Infos in die Listen aufnehmen (`get_vod_info` gibt es nur einzeln – 140.000 Abrufe).

## Design

```
Sofort (kompakt, unten):                    Nach 5 s (ausführlich, links unten, Bild leicht abgedunkelt):
┌────────────────────────────────────┐      ┌───────────────────────────────────────────┐
│ Dune: Part Two · 2024 · ★ 8,3       │      │ Dune: Part Two                            │
│ 1:03:12 ━━━━━━░░░░░░  endet um 22:41│      │ ★ 8,3 · 2024 · 2 h 46 min · FSK 12 · Sci-Fi│
└────────────────────────────────────┘      │ Paul Atreides vereint sich mit … (≤ 4 Z.) │
                                            │ 1:03:12 ━━━━━━░░░░░░  noch 1:42:48         │
                                            └───────────────────────────────────────────┘
```

- **Auslöser:** Pause bei Filmen und Folgen (nicht Live). Ersetzt das heutige Pause-Symbol in der Mitte.
- **Stufen:** kompakt sofort; ausführlich nach **5 s** ununterbrochener Pause. Fortsetzen (OK, Leertaste, Klick) blendet beides aus.
- **Kompakt:** Titel · Jahr · ★ Bewertung (sofern vorhanden), Fortschritt, „endet um“.
- **Ausführlich:** Titel; ★ Bewertung · Jahr · Dauer · FSK · Genres (je vorhanden); Handlung (höchstens 4 Zeilen, Auslassungspunkte); Fortschritt mit „noch …“.
- **Folgen:** Titel „S2 · F3 · <Folgentitel>“ plus Serienname; Bewertung, Jahr, Genres, Handlung von der Serie.
- **Ohne Daten** (M3U, Abruf gescheitert, noch nicht geladen): nur die kompakte Stufe mit Titel und Fortschritt; keine ausführliche Stufe.
- **Daten laden:** Filme beim Öffnen des Players im Hintergrund (`get_vod_info`), pro Sitzung im Speicher; Folgen aus der Serien-Info, die beim Öffnen der Staffeln ohnehin geladen wird.
- **Werte:** Bewertung auf 10er-Skala mit einer Nachkommastelle, deutsches Komma („8,3“); fehlt oder 0 → weglassen. Jahr aus `releasedate`/`releaseDate` (erste vier Ziffern). Dauer aus `duration_secs` („2 h 46 min“). FSK nur, wenn `age` eine Zahl enthält („FSK 12“). Genres aus `genre`, kommagetrennt, höchstens drei.
- Info-Leiste und Pause-Info nicht gleichzeitig: Solange die Pause-Info sichtbar ist, bleibt die normale Leiste weg (Tasten zum Spulen zeigen wie bisher die Leiste und beenden die ausführliche Stufe).

## Was der Builder entscheidet

- Schnittstelle der Zeitlogik (z. B. `PauseInfo` mit `onPaused`, `onResumed`, `tick`, Zustand `NONE/COMPACT/FULL`).
- Form von `MediaInfo` und wie die Serien-Info neben den Staffeln zurückgegeben wird.

## Tests

- Parsen von `get_vod_info` (alle Felder, fehlende/0/leere Werte) und der Serien-Info.
- Zeitlogik: kompakt sofort, ausführlich nach 5 s, Fortsetzen blendet aus, erneutes Pausieren beginnt wieder kompakt.
- Formatierung (Bewertung, Jahr, Dauer, FSK, höchstens drei Genres).
- UI je Plattform: Komponenten für kompakt/ausführlich mit und ohne Daten.
