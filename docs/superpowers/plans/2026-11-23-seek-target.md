# Spulen mit Ziel – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ◀▶ sammeln das Ziel, 0,8 s nach dem letzten Druck ein Sprung; Anzeige Sprungweite (Mitte) + Zielzeit-Blase an Markierung (Variante C).

**Architecture:** `SeekTarget` + `seekDeltaLabel` in `shared`; je Plattform eine `SeekOverlay`-Komponente (Pill in der Mitte, Balken mit Markierung und Blase unten) und Umbau der Spul-Tasten.

**Tech Stack:** Kotlin Multiplatform, Compose (Android TV / Desktop), kotlin.test, Compose UI-Tests.

**Spec:** `docs/superpowers/specs/2026-11-23-seek-target-design.md`

## Global Constraints

- `SeekTarget.COMMIT_AFTER_MS = 800L`; Schritte −10 s / +30 s × `seekMultiplier`.
- Ziel in `0..(Dauer − 1000)`; ohne Dauer (≤ 0) nur `≥ 0`.
- Nur Filme/Folgen. Andere Taste während des Sammelns → erst springen, dann Taste ausführen.
- Sprungweite: Vorzeichen „+“ bzw. „−“ (U+2212), „40 s“, „2 min“, „1 min 30 s“, „1 h 5 min“, 0 → „±0 s“.

## Review Focus

- **Spulen über das Ende hinaus** → Ziel Dauer − 1 s, Film gilt nicht als beendet. (Test in Task 1)
- **Erster Druck während Pufferns/Ladens** (Position 0, Dauer 0) → kein Absturz, Ziel ≥ 0. (Test in Task 1)
- **Taste gehalten (Wiederholung)** → Schritte wachsen, kein Sprung, solange gehalten wird. (Test in Task 1 über `press` ohne `due`)
- **Spieler wechselt Folge während des Sammelns** (Countdown) → Sammeln verworfen. (Task 2/3: `cancel()` beim Wechsel)
- **Pause-Info offen** → `dismissFull()` wie bisher.

---

### Task 1: `SeekTarget` in `shared`

**Files:** Create `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/SeekTarget.kt`; Test `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/SeekTargetTest.kt`

**Interfaces:**
- `data class SeekState(val anchorMs: Long, val targetMs: Long) { val deltaMs: Long }`
- `class SeekTarget(clock: () -> Long)`: `val state: SeekState?` (State), `fun press(deltaMs: Long, positionMs: Long, durationMs: Long)`, `fun due(): Long?` (Ziel, wenn ≥ 800 ms seit letztem Druck; leert den Zustand), `fun commitNow(): Long?`, `fun cancel()`
- `fun seekDeltaLabel(deltaMs: Long): String`

- [ ] Step 1: Failing Tests – `firstPressAnchorsAtPosition`, `pressesAccumulate`, `clampedToStartAndBeforeEnd`, `unknownDurationOnlyClampsAtZero`, `commitsOnceAfter800ms`, `newPressExtendsTheWait`, `commitNowAndCancel`, `deltaLabels`
- [ ] Step 2: Run `./gradlew :shared:desktopTest --tests '*SeekTargetTest*'` → FAIL
- [ ] Step 3: Implementieren
- [ ] Step 4: Run `./gradlew :shared:desktopTest` → grün
- [ ] Step 5: Commit `add seek target accumulation`

### Task 2: Desktop

**Files:** Create `desktopApp/.../ui/SeekOverlay.kt`; Modify `desktopApp/.../ui/PlayerScreen.kt` (SeekBack/SeekForward, Tick, andere Tasten); Test `desktopApp/src/test/.../ui/SeekOverlayTest.kt`

- [ ] Step 1: Failing Test `showsDeltaAndTargetTime` – `SeekOverlay(SeekState(3_792_000, 3_912_000), durationMs = 9_960_000, positionMs = 3_792_000)` → Texte „+2 min ▶▶“, „1:05:12“
- [ ] Step 2: Run → FAIL
- [ ] Step 3: Overlay; Tasten ◀▶ → `seek.press(...)` statt `seekBy`; `LaunchedEffect(seek.state) { while (seek.state != null) { delay(100); seek.due()?.let(player::seekTo) } }`; andere Aktion → `seek.commitNow()?.let(player::seekTo)` vor der Aktion; bei Folgenwechsel `seek.cancel()`; während `seek.state != null` Steuerleiste ausgeblendet (Overlay hat eigenen Balken).
- [ ] Step 4: Run `./gradlew :desktopApp:test` → grün
- [ ] Step 5: Commit `accumulate seeks with target overlay on desktop`

### Task 3: Android TV

**Files:** Create `androidApp/.../ui/SeekOverlay.kt`; Modify `androidApp/.../ui/PlayerScreen.kt`; Test `androidApp/src/androidTest/.../SeekOverlayTest.kt`

- [ ] Step 1–5 analog Task 2 (REWIND/FORWARD mit `repeatCount`; Info-Leiste während des Sammelns aus); Suite `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest`; Commit `accumulate seeks with target overlay on Android TV`
