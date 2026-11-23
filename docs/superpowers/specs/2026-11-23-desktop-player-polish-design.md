# Desktop-Player: Feinschliff für die Maus

**Stand:** 23.11.2026 · Größe: kleine Änderung · Nur Desktop · Teil 6 von 6 der Player-Verbesserungen.

## Design
- **Mauszeiger** über dem Player ausblenden, solange die Leiste ausgeblendet ist (Vollbild und Fenster); Bewegung zeigt ihn wieder.
- **Lautstärke:** Regler nur sichtbar, solange die Maus über dem Lautsprecher-Knopf bzw. dem Regler steht. **Mausrad** über dem Bild: ±5 % je Raste (0–100), Stummschaltung aufheben, kurze Anzeige „🔊 65 %“ (1,5 s) oben rechts.
- **Tooltips** beim Überfahren der Knöpfe mit Tastenkürzel: „Vorheriger Sender (↑)“, „10 Sekunden zurück (←)“, „Pause (Leertaste)“, „30 Sekunden vor (→)“, „Nächster Sender (↓)“, „Ton aus (M)“, „Ton & Untertitel (T)“, „Schlaf-Timer (S)“, „Vollbild (F)“.
- **Doppelklick** ins Bild: Vollbild an/aus; einfacher Klick pausiert weiterhin (Filme/Folgen).

## Ansatz
Ein sinnvoller Weg: reine Hilfsfunktion `wheelVolume(volume, scrollDelta)` (testbar), sonst Compose-Desktop-Bordmittel (`pointerHoverIcon`, `TooltipArea`, `combinedClickable`, Hover-Zustand).

## Tests
- `wheelVolume`: Schritt, Richtung, Grenzen.
- Tooltip-Texte über eine Zuordnung `ctrlTooltip(label, shortcut)`.
