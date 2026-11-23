# Desktop-Player-Feinschliff – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mauszeiger ausblenden, Lautstärke per Hover-Regler und Mausrad, Tooltips mit Tastenkürzeln, Doppelklick = Vollbild.
**Architecture:** Nur `desktopApp/.../ui/PlayerScreen.kt` (+ Hilfsfunktionen dort); Tests in `PlayerLogicTest`.
**Tech Stack:** Compose Desktop.
**Spec:** `docs/superpowers/specs/2026-11-23-desktop-player-polish-design.md`

## Review Focus
- **Mausrad während Pause-Info/Zapp-Karte** → Lautstärke ändert sich, nichts anderes. 
- **Doppelklick bei Live** → nur Vollbild (kein Pausieren). 
- **Zeiger über der sichtbaren Leiste** → immer sichtbar.

### Task 1: Hilfsfunktionen + Verdrahtung
- [ ] Failing Tests `wheelVolumeStepsAndClamps`, `tooltipsNameTheShortcut` → FAIL
- [ ] `internal fun wheelVolume(volume: Float, scrollDeltaY: Float): Float` (je Raste 5, nach oben scrollen = lauter, 0..100), `internal fun ctrlTooltip(label: String, shortcut: String?): String`; Verdrahtung: `pointerHoverIcon(if (showUi) PointerIcon.Default else HiddenCursor)`, `onPointerEvent(Scroll)`, `combinedClickable(onClick = pause, onDoubleClick = onToggleFullscreen)`, `TooltipArea` in `CtrlButton`, Lautstärke-Regler nur bei Hover.
- [ ] `./gradlew :desktopApp:test` grün → Commit `polish desktop player for mouse use`
