# Info-Abzeichen im Player

**Stand:** 23.11.2026 · Größe: kleine Änderung · Plattformen: Android TV und Desktop · Teil 4 von 6 der Player-Verbesserungen.  
**Plattformen:** TV ✅ · PC ✅ · iPhone 🟡 (nur HD/4K aus dem Namen)

## Warum
Auf einen Blick sehen, was gerade läuft: Bildauflösung, Tonkanäle, Tonsprache, Untertitel.

## Design
```
 ←  [ARD]  Das Erste HD                     [FHD] [5.1] [DE] [UT]   20:14
```
- Obere Player-Leiste, rechts vor der Uhr; Live, Filme, Folgen; sichtbar mit der Leiste.
- Nur was der Player kennt, als kleine Mono-Kästchen (Stil `TagChip`).
- Auflösung aus der Bildhöhe: ≥ 2000 → `4K`, ≥ 1000 → `FHD`, ≥ 700 → `HD`, sonst `SD`; ohne Bild (Radio) keines.
- Tonkanäle der gewählten Tonspur: 1 → `Mono`, 2 → `2.0`, 6 → `5.1`, 8 → `7.1`, sonst keines.
- Tonsprache der gewählten Spur als zweistelliges Kürzel (`DE`, `EN`; ISO-639-2 wie `deu`/`ger` → `DE`); `und`/`mul`/`zxx`/leer → keines.
- `UT`, wenn ein Untertitel eingeschaltet ist.

## Ansatz
Ein sinnvoller Weg: Übersetzung der Rohwerte als reine Funktion `mediaBadges(...)` in `shared`; Android liest aus ExoPlayer-`Tracks`, Desktop aus VLC (Bildhöhe aus dem Bildpuffer, Spuren wie fürs Optionsmenü); je Plattform eine kleine Zeile `MediaBadges`.

## Tests
- `mediaBadges`: Schwellen, Kanäle, Sprachkürzel, `UT`, fehlende Werte.
- UI je Plattform: Zeile zeigt die Kürzel.
