# Style-Lock: IPTV Player

Gilt für TV-, PC- und iPhone-App. Quelle der Werte: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/Palette.kt`
(alle Oberflächen und `tools/brand/make_icons.py` lesen dort; Kontraste prüft `PaletteTest`).

## Richtung
„Anthrazit & Bernstein“ (seit v2.1) – warm wie ein abgedunkelter Kinosaal. Bernstein ist Signal, keine Dekoration:
nur Auswahl-Markierung, „Live“, Hauptaktion, Fortschritt. Fokus warmweiß statt reinweiß – aus 3 m Abstand das
deutlichste Signal, blendet abends aber weniger.

## Farben (WCAG geprüft, siehe PaletteTest)
| Rolle | Hex |
|---|---|
| bg-deep | #141210 |
| bg | #1A1816 |
| surface0 | #1F1D1A |
| surface1 | #25221F |
| surface2 | #2F2B27 |
| line | #3B3631 |
| text | #EEE7DE |
| text-soft | #D6CDC1 |
| muted | #A99F93 |
| signal | #FFB224 |
| on-signal | #1A1205 |
| danger | #FF6B5E |
| focus-bg | #FFF3DF |
| focus-fg | #1E1912 |
| shade | #0B0A09 (nur mit Alpha, über Bild/Video) |

- Text-sicher (≥ 4,5:1): text, text-soft und muted auf allen Flächen; signal und danger auf bg, surface1, surface2.
- Danger bleibt klar rot – Bernstein allein liest sich sonst wie eine Warnung.
- bg/surface1/surface2 sind kaum unterscheidbar → Auswahl immer mit Signal-Strich, nie nur per Fläche.
- Über Video: Weiß und Schwarz mit Alpha bleiben neutral (kein Farbstich auf dem Bild).

## Schrift
- Archivo 400/500/600/700 (Titel SemiBold, Laufweite −2 %), IBM Plex Mono 400 für Sendernummern und Uhrzeiten. Beide OFL.
- Tabellenziffern (`tnum`) global.

## Form
- Ecken 4 dp (klein 2 dp), keine Kapseln, keine Verläufe, feine Linien statt Karten-Schatten.

## Verworfen
- Indigo/Blau-Verlauf (ursprünglich), „Programmheft“ (Bernstein + Instrument Serif – die Serife, nicht das
  Bernstein), Nachtblau/Koralle, Tiefgrün/Messing, Hell.
- „Graphit + Signalrot“ (v2.0): Signalrot schaffte auf Karten nur 4,6:1, auf surface2 3,95:1; Fokus in Reinweiß blendete.
- Weitere Alternativen (2026-11-24): Nachtblau & Petrol, OLED-Schwarz & Eisblau, Aubergine & Koralle.
