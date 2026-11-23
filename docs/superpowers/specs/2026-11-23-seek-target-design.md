# Spulen mit Ziel

**Stand:** 23.11.2026 · Größe: kleine Änderung · Plattformen: Android TV und Desktop
Teil 3 von 6 der Player-Verbesserungen.

## Warum

Heute springt jeder Druck auf ◀▶ sofort; bei IPTV-Streams lädt der Player nach jedem Sprung neu – fünfmal ▶ sind
fünf Ladepausen. Man sieht außerdem erst nach dem Sprung, wo man landet.

## Ziele

- Drücke sammeln, erst 0,8 s nach dem letzten Druck einmal springen (eine Ladepause).
- Während des Sammelns Sprungweite groß in der Mitte und Zielzeit als Blase an einer Markierung auf dem Balken (Variante C).

## Nicht-Ziele

- Live-Sender (dort kein Spulen, wie heute).
- Vorschaubilder auf dem Balken.
- Mausbedienung des Desktop-Schiebereglers (bleibt wie heute).

## Ansatz

Ein sinnvoller Weg: kleine, testbare Sammel-Logik in `shared` (`SeekTarget`), beide Player zeichnen die Anzeige und
springen, wenn die Logik es meldet. Verworfen: sofort springen mit besserer Anzeige (ruckelt weiter, gewählt war
„Sammeln“), Logik je Player (doppelt, Timing schwer testbar).

## Design

```
                 ┌──────────────┐
                 │  +2 min ▶▶   │            ← Sprungweite, groß, Mitte
                 └──────────────┘
                              ┌───────┐
                              │1:05:12│     ← Zielzeit als Blase über der Markierung
 ━━━━━━━━━━━━━━━━━░░░░░░░░░░░░|░░░░░░░░░░░░░   ← gespielt · Bereich bis zum Ziel · Markierung
 1:03:12                                   2:46:00
```

- Schrittweiten wie heute: ◀ −10 s, ▶ +30 s; gehaltene Taste vergrößert die Schritte (vorhandenes `seekMultiplier`).
- Erster Druck merkt die aktuelle Position als Ausgang; jeder weitere verschiebt nur das Ziel (Ausgang + Summe).
- Ziel bleibt in `0..Dauer` (beim Ende knapp davor: Dauer − 1 s); ohne bekannte Dauer nur nach unten begrenzt.
- **0,8 s** nach dem letzten Druck: ein Sprung zum Ziel, Anzeige verschwindet. Wiedergabe läuft während des Sammelns weiter bzw. bleibt pausiert.
- Sprungweite als „+2 min“, „−40 s“, „+1 h 5 min“; Richtungssymbol ▶▶ bzw. ◀◀; Summe 0 → „±0 s“.
- Andere Taste (OK, Zurück, ▲▼) während des Sammelns: sofort springen, dann die Taste wie gewohnt ausführen.
- Die Leiste ist während des Sammelns sichtbar (Balken mit Markierung); die Pause-Info geht wie bisher auf kompakt zurück.

## Was der Builder entscheidet

- Schnittstelle von `SeekTarget` (z. B. `press(deltaMs, positionMs, durationMs)`, `due(): Long?`, Zustand für die Anzeige).
- Wie der Balken die Markierung zeichnet (vorhandene `ProgressBar` erweitern oder eigener Baustein).

## Tests

- Sammeln: Ausgang beim ersten Druck, Summe, Grenzen 0/Dauer, ohne Dauer.
- Zeitpunkt: kein Sprung vor 0,8 s, genau ein Sprung danach, neuer Druck verlängert.
- Andere Taste: sofortiger Sprung.
- Formatierung der Sprungweite.
- UI je Plattform: Anzeige mit Sprungweite und Zielzeit.
