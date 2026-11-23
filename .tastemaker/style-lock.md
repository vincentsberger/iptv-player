# Style-Lock: IPTV Player

Gilt für Desktop- und (später) TV-App. Quelle der Werte: `desktopApp/.../ui/Components.kt` (`Graphit`).

## Richtung
„Graphit + Signalrot“ – nüchtern wie ein Sende-Regieraum. Rot ist Signal, keine Dekoration:
nur Auswahl-Markierung, „Live“, Hauptaktion, Fortschritt.

## Farben (WCAG geprüft)
| Rolle | Hex |
|---|---|
| bg | #16181B |
| surface1 | #202327 |
| surface2 | #2B2F34 |
| line | #34383E |
| text | #E6E8EB |
| muted | #9BA1A8 |
| signal | #FF453A |
| on-signal | #16181B |
| danger | #FF8A65 |

- Text-sicher (≥ 4,5:1): text und muted auf allen Flächen; signal/danger auf bg und surface1; on-signal auf signal.
- Signal auf surface2 nur 3,95:1 → dort nie als Textfarbe.
- bg/surface1/surface2 sind kaum unterscheidbar → Auswahl immer mit Signal-Strich, nie nur per Fläche.

## Schrift
- Archivo 400/500/600/700 (Titel SemiBold, Laufweite −2 %), IBM Plex Mono 400 für Sendernummern und Uhrzeiten. Beide OFL.
- Tabellenziffern (`tnum`) global.

## Form
- Ecken 4 dp (klein 2 dp), keine Kapseln, keine Verläufe, feine Linien statt Karten-Schatten.

## Verworfen
- Indigo/Blau-Verlauf (ursprünglich), „Programmheft“ (Bernstein + Instrument Serif), Nachtblau/Koralle, Tiefgrün/Messing, Hell.
