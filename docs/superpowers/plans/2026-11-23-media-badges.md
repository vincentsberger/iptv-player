# Info-Abzeichen – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Abzeichen `4K/FHD/HD/SD`, `Mono/2.0/5.1/7.1`, Sprache, `UT` in der oberen Player-Leiste.
**Architecture:** `mediaBadges(videoHeight, audioChannels, audioLanguage, subtitlesOn): List<String>` in `shared`; je Plattform Erfassung der Rohwerte + Composable `MediaBadges(badges)`.
**Tech Stack:** Kotlin Multiplatform, Compose, Media3, vlcj.
**Spec:** `docs/superpowers/specs/2026-11-23-media-badges-design.md`

## Global Constraints
- Schwellen/Zuordnungen exakt wie in der Spec; fehlende Werte → kein Abzeichen.

## Review Focus
- **Sprache `und` / leer** → kein Abzeichen. (Task 1)
- **Bildhöhe 1088 (Encoder-Padding)** → `FHD`. (Task 1)
- **Radio** (keine Bildhöhe) → keine Auflösung. (Task 1)
- **Spurwechsel im Optionsmenü** → Abzeichen aktualisieren sich. (Task 2/3)

### Task 1: `mediaBadges` in `shared`
**Files:** Create `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/MediaBadges.kt`; Test `shared/src/commonTest/.../MediaBadgesTest.kt`
- [ ] Failing Tests (`resolutionThresholds`, `channelLayouts`, `languageCodes`, `subtitles`, `missingValuesGiveNoBadges`) → FAIL → implementieren → `./gradlew :shared:desktopTest` grün → Commit `add media badge mapping`

### Task 2: Desktop
**Files:** Modify `desktopApp/.../player/VlcPlayer.kt` (`videoHeight` State aus `getBufferFormat`, `selectedAudioInfo()`), Create `desktopApp/.../ui/MediaBadges.kt`, Modify `PlayerScreen.kt` (obere Leiste, Aktualisierung alle 2 s solange sichtbar); Test `desktopApp/src/test/.../ui/MediaBadgesTest.kt`
- [ ] Failing Test `showsBadges` → FAIL → implementieren → `./gradlew :desktopApp:test` grün → Commit `show media badges on desktop`

### Task 3: Android TV
**Files:** Create `androidApp/.../ui/MediaBadges.kt`, Modify `PlayerScreen.kt` (aus `tracks`: gewählte Video-/Ton-/Textspur); Test `androidApp/src/androidTest/.../MediaBadgesTest.kt`
- [ ] Failing Test `showsBadges` (Emulator) → FAIL → implementieren → Android-Suite grün → Commit `show media badges on Android TV`
