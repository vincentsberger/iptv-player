# Pause-Info – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Beim Pausieren von Filmen/Folgen sofort eine kompakte Leiste, nach 5 s die ausführliche Info (Variante C).

**Architecture:** `MediaInfo` aus `get_vod_info` (Filme) bzw. `get_series_info` (Serie) mit Formatierungs-Helfern in `shared`; `AppState` lädt die Info beim Öffnen des Players und hält sie pro Sitzung; `PauseInfo` entscheidet die Stufe. Beide Player zeichnen kompakt/ausführlich statt des Pause-Symbols.

**Tech Stack:** Kotlin Multiplatform, Compose (Android TV / Desktop), kotlin.test, Compose UI-Tests.

**Spec:** `docs/superpowers/specs/2026-11-23-pause-info-design.md`

## Global Constraints

- Nur Filme und Folgen; ausführlich nach `PauseInfo.FULL_AFTER_MS = 5_000L` ununterbrochener Pause.
- Bewertung 10er-Skala, eine Nachkommastelle, deutsches Komma; 0/leer → weglassen. Jahr = erste vier Ziffern von `releasedate`/`releaseDate`/`release_date`. Dauer aus `duration_secs` („2 h 46 min“, unter 1 h „46 min“). FSK nur bei Zahl in `age`. Höchstens drei Genres. Handlung höchstens 4 Zeilen.
- Ohne `MediaInfo`: nur kompakte Stufe (Titel, Fortschritt).
- Pause-Info und normale Leiste nie gleichzeitig.

## Review Focus

- **`get_vod_info` liefert `info: []` oder `info` fehlt** → `MediaInfo` null bzw. leer, kein Absturz. (Test in Task 1)
- **Rating als Zahl `8.3`, String `"8.3"` oder `"0"`** → „8,3“ bzw. weggelassen. (Test in Task 1)
- **Genre-String mit Schrägstrichen** („Action / Abenteuer“) → als Trenner wie Komma behandeln. (Test in Task 1)
- **Kurz pausieren, fortsetzen, wieder pausieren** → erneut erst kompakt, die 5 s beginnen neu. (Test in Task 2)
- **Folge ohne geladene Staffeln** (direkt aus „Weiterschauen“ geöffnet) → Serien-Info wird beim Öffnen nachgeladen. (Test in Task 2)

---

### Task 1: `MediaInfo` – Abruf, Parsen, Formatierung

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Models.kt`, `.../data/XtreamClient.kt`, `.../data/Repository.kt`
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/data/MediaInfoTest.kt`

**Interfaces:**
- Produces: `data class MediaInfo(val year: String?, val rating: Double?, val durationSecs: Int?, val age: Int?, val genres: List<String>, val plot: String?)` mit `val ratingLabel: String?` („8,3“), `val durationLabel: String?`, `val fskLabel: String?` („FSK 12“), `val genresLabel: String?` (max. 3, „Sci-Fi, Abenteuer“)
- Produces: `fun XtreamClient.parseVodInfo(text: String): MediaInfo?`, `suspend fun XtreamClient.vodInfo(item: Item): MediaInfo?`, `fun XtreamClient.parseSeriesInfo(json: JsonObject): MediaInfo?`
- Produces: `suspend fun Repository.vodInfo(item: Item): MediaInfo?` (null bei M3U/Fehler, wirft nicht; im Speicher pro Film-ID), `suspend fun Repository.seriesInfo(seriesId: String): MediaInfo?` (aus dem `get_series_info`-Abruf von `seasons()` mitgefüllt; sonst eigener Abruf)

- [ ] **Step 1: Failing Tests** – `vodInfoIsParsed` (alle Felder), `emptyOrMissingInfoIsNull`, `ratingFormats` (8.3 / "8.3" / "0" / fehlt), `slashSeparatedGenresMaxThree`, `durationLabel` (9960 s → „2 h 46 min“, 2760 → „46 min“), `fskOnlyForNumbers` ("12" → „FSK 12“, "" / "PG" → null), `seriesInfoFromSeasonsCall` (nach `seasons()` liefert `seriesInfo` ohne zweiten Abruf)
- [ ] **Step 2:** Run `./gradlew :shared:desktopTest --tests '*MediaInfoTest*'` → FAIL
- [ ] **Step 3:** Implementieren
- [ ] **Step 4:** Run `./gradlew :shared:desktopTest` → grün
- [ ] **Step 5:** Commit `fetch and format media info for movies and series`

### Task 2: `PauseInfo` und `AppState`

**Files:**
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/PauseInfo.kt`
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/AppState.kt`
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/PauseInfoTest.kt`

**Interfaces:**
- Produces: `enum class PauseStage { NONE, COMPACT, FULL }`; `class PauseInfo(clock: () -> Long)` mit `val stage: PauseStage` (State), `fun onPaused()`, `fun onResumed()`, `fun tick()`, `fun dismissFull()` (Spulen: zurück auf COMPACT); `companion object { const val FULL_AFTER_MS = 5_000L }`
- Produces (`AppState`): `fun mediaInfoFor(item: Item): MediaInfo?` (State-gestützt), `fun requestMediaInfo(item: Item)` (Film: `vodInfo`; Folge: `seriesInfo(item.categoryId)`), `fun episodeLabel(item: Item): String?` („S2 · F3 · <Titel>“ aus `seasons`, null wenn unbekannt), `fun seriesNameOf(item: Item): String?`

- [ ] **Step 1: Failing Tests** – `compactImmediatelyFullAfterFiveSeconds`, `resumeHides`, `pauseAgainStartsCompact`, `dismissFullGoesBackToCompact`, `movieInfoIsRequestedAndKept` (AppState, `get_vod_info` in FakeHttp), `episodeInfoComesFromSeries`, `episodeLabelFromSeasons`
- [ ] **Step 2:** Run `./gradlew :shared:desktopTest --tests '*PauseInfoTest*'` → FAIL
- [ ] **Step 3:** Implementieren
- [ ] **Step 4:** Run `./gradlew :shared:desktopTest` → grün
- [ ] **Step 5:** Commit `add pause info stages and media info in app state`

### Task 3: Desktop

**Files:**
- Create: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/PauseInfoView.kt`
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/PlayerScreen.kt` (Pause-Symbol ersetzen, Leiste unterdrücken, Info beim Öffnen anfordern)
- Test: `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/PauseInfoViewTest.kt`

**Interfaces:** Consumes `MediaInfo`, `PauseInfo`, `PauseStage`, `AppState.mediaInfoFor/requestMediaInfo/episodeLabel/seriesNameOf`

- [ ] **Step 1: Failing Tests** – `compactShowsTitleYearRating`, `fullShowsMetaAndPlot`, `withoutInfoOnlyTitle`
- [ ] **Step 2:** Run `./gradlew :desktopApp:test --tests '*PauseInfoViewTest*'` → FAIL
- [ ] **Step 3:** `PauseInfoView(title, subtitle, info, stage, position, duration, endsAt)`; im Player: `pause.onPaused()/onResumed()` aus `player.playing` (nur Filme/Folgen, nicht beim Puffern), Tick-Schleife, `showUi` unterdrückt bei FULL; Spulen → `dismissFull()`.
- [ ] **Step 4:** Run `./gradlew :desktopApp:test` → grün
- [ ] **Step 5:** Commit `show pause info on desktop`

### Task 4: Android TV

**Files:**
- Create: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/PauseInfoView.kt`
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/PlayerScreen.kt`
- Test: `androidApp/src/androidTest/java/de/vincentsberger/iptvplayer/app/PauseInfoViewTest.kt`

**Interfaces:** wie Task 3

- [ ] **Step 1: Tests** analog Task 3
- [ ] **Step 2:** Run (Emulator) → FAIL
- [ ] **Step 3:** Wie Desktop (Pause aus `playing`/`buffering`, ◀▶ → `dismissFull()`).
- [ ] **Step 4:** Run `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest` → grün
- [ ] **Step 5:** Commit `show pause info on Android TV`
