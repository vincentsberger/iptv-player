# Playlist-Cache und Lade-Übersicht (Teil 1) – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Listen pro Anbieter zwischenspeichern (Start aus dem Cache, wenn < 24 h) und nach jedem Start eine Übersicht mit Zahlen, Veränderung, Warnungen, „Neu laden“ und „Weiter“ zeigen.

**Architecture:** Neue Schnittstelle `PlaylistCacheStore` (Text/Zeilen je Schlüssel) mit Datei-Implementierungen pro Plattform. `Repository.load(section, fromCache)` liest entweder Cache oder Netz und schreibt nach erfolgreichem Netzabruf in den Cache. `AppState` entscheidet beim Start anhand gespeicherter Metadaten (`CacheMeta` je Bereich) und ersetzt das Auto-Schließen des Ladebildschirms durch einen Übersichts-Zustand.

**Tech Stack:** Kotlin Multiplatform (`shared`), OkHttp, `java.util.zip.GZIP*`, Compose (Android TV / Desktop), kotlin.test, Compose UI-Tests.

**Spec:** `docs/superpowers/specs/2026-11-23-playlist-cache-dashboard-design.md` (Teil 1)

## Global Constraints

- Cache gilt 24 h (fester Wert, `CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L`), je Bereich.
- Aus dem Cache nur, wenn **alle drei** Bereiche frisch und beim letzten Laden fehlerfrei waren; sonst alle aus dem Netz.
- Login, `get_series_info`, EPG nie aus dem Cache.
- Fehler oder leere Antwort (obwohl vorher > 0) überschreiben den Cache nicht.
- Warnung bei: Fehler, leer obwohl vorher > 0, mehr als 10 % weniger als beim vorherigen Laden.
- Übersicht hält immer an; „Letzten Sender abspielen“ erst nach „Weiter“.
- Cache-Dateien: Dateiname = Aktion (`get_vod_streams.json.gz`, M3U: `playlist.m3u.gz`), nie die URL; Schutz wie `data.dat` (Windows DPAPI über `DataProtector`, sonst Rechte 600 / App-interner Speicher).
- Android TV: Rückkehr nach > 1 h im Hintergrund zählt als Start (`RESUME_RESTART_MS = 60 * 60 * 1000L`).
- Texte deutsch, Bezeichner englisch. Build: `./gradlew` (Daemon-JDK 17).

## Review Focus

- **Anbieter liefert HTTP 200 mit `[]` für Filme**, vorher 140.000 → alter Cache bleibt, Übersicht warnt, Stand des Bereichs bleibt alt. (Test in Task 2/3)
- **Cache-Datei beschädigt/abgeschnitten** (Absturz beim Schreiben, gzip-Fehler) → Bereich wird aus dem Netz geladen statt Absturz. (Test in Task 1/3)
- **Anbieterwechsel** → Cache und Metadaten des anderen Anbieters werden nicht gemischt. (Test in Task 3)
- **Abmelden** → Cache-Verzeichnis des Anbieters weg. (Test in Task 3)
- **Uhr springt zurück** (Stand liegt in der Zukunft) → gilt als nicht frisch, Netzabruf. (Test in Task 3)

---

### Task 1: `PlaylistCacheStore` und Datei-Implementierungen

**Files:**
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/PlaylistCache.kt`
- Create: `testing/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/testing/InMemoryPlaylistCache.kt`
- Modify: `shared/src/desktopMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Platform.desktop.kt` (Datei-Cache neben `FileKeyValueStore`)
- Modify: `shared/src/androidMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Platform.android.kt`
- Test: `shared/src/desktopTest/kotlin/de/vincentsberger/iptvplayer/shared/data/FilePlaylistCacheTest.kt`

**Interfaces:**
- Produces (common):
  ```kotlin
  interface PlaylistCacheStore {
      fun readText(key: String): String?                       // null = fehlt oder unlesbar
      fun writeText(key: String, text: String)                 // atomar: tmp + rename
      fun <T> readLines(key: String, block: (Sequence<String>) -> T): T?
      fun writeLines(key: String, lines: Sequence<String>)     // atomar
      fun clear()                                              // ganzes Anbieter-Verzeichnis
  }
  object NoPlaylistCache : PlaylistCacheStore                  // liest null, schreibt nichts
  ```
- Produces (desktop): `class FilePlaylistCache(dir: File, protector: DataProtector = DataProtector.forCurrentOs()) : PlaylistCacheStore`; `fun FilePlaylistCache.Companion.forAccount(accountId: String): FilePlaylistCache` → `FileKeyValueStore.defaultDir()/cache/<accountId>/`
- Produces (android): `class AndroidPlaylistCache(dir: File)`; Fabrik `forAccount(context, accountId)` → `filesDir/playlist-cache/<accountId>/`
- Produces (testing): `class InMemoryPlaylistCache : PlaylistCacheStore` mit `val entries: Map<String, String>` zum Prüfen
- Dateiname: `"$key.gz"`; Inhalt: gzip(protector.protect(utf8)). `accountId` vor Verwendung als Pfad mit `safePathSegment` säubern.

- [ ] **Step 1: Failing Tests** (`FilePlaylistCacheTest`, mit `kotlin.io.path.createTempDirectory`):
  - `roundTripTextAndLines` – `writeText("get_vod_streams.json", "[1]")` → `readText` == `"[1]"`; `writeLines("playlist.m3u", sequenceOf("a","b"))` → `readLines { it.toList() }` == `["a","b"]`
  - `filesAreGzippedAndNamedByKey` – Datei `get_vod_streams.json.gz` existiert, beginnt mit gzip-Magic `0x1f 0x8b`
  - `protectorIsApplied` – Protector, der Bytes umdreht → Rohdatei (entpackt) ≠ Klartext, `readText` liefert Klartext
  - `corruptFileReadsAsNull` – Datei mit Müll überschreiben → `readText` == null, kein Throw
  - `clearRemovesDirectory`
- [ ] **Step 2:** Run `./gradlew :shared:desktopTest --tests '*FilePlaylistCacheTest*'` → FAIL (`Unresolved reference 'FilePlaylistCache'`)
- [ ] **Step 3:** Implementieren (gzip über `GZIPOutputStream`/`GZIPInputStream`; Fehler beim Lesen → null; Rechte 600/700 wie `restrictToOwner`). Android-Klasse analog ohne Protector. `InMemoryPlaylistCache` im `testing`-Modul.
- [ ] **Step 4:** Run `./gradlew :shared:desktopTest :androidApp:compileDebugKotlin` → BUILD SUCCESSFUL
- [ ] **Step 5:** Commit `add file-based playlist cache store`

### Task 2: `Repository` liest/schreibt den Cache

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Repository.kt`
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/XtreamClient.kt` (Abruf und Parsen trennen)
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/data/RepositoryCacheTest.kt`

**Interfaces:**
- Consumes: `PlaylistCacheStore`, `InMemoryPlaylistCache` (Task 1)
- Produces:
  - `class Repository(account: Account, http: HttpSource = PlatformHttp, cache: PlaylistCacheStore = NoPlaylistCache)`
  - `suspend fun load(section: Section, fromCache: Boolean = false): SectionLoad`
  - `data class SectionLoad(val categories: List<Category>, val items: List<Item>, val fromCache: Boolean)`
  - `Repository.load` wirft wie heute bei Netzfehlern; liefert bei `fromCache = true` und fehlender/kaputter Datei automatisch Netz (`fromCache = false` im Ergebnis).
  - `fun Repository.clearCache()` → `cache.clear()`
  - Schreiben in den Cache nur, wenn `items.isNotEmpty()` **oder** es noch keine Cache-Datei gibt (vorher > 0 → leere Antwort nicht speichern).
- XtreamClient: `fun listAction(section): Pair<String, String>` (Kategorie- und Item-Aktion), `fun parseCategories(text): List<Category>`, `fun parseItems(section, text): List<Item>`; `categories()`/`items()` nutzen diese intern weiter.
- M3U: Netzabruf über `http.readLines`; Zeilen werden für den Cache als `writeLines("playlist.m3u", …)` gespeichert (dafür die Zeilen einmal in eine Liste sammeln – M3U ist ein einziger Abruf) und dann geparst.

- [ ] **Step 1: Failing Tests** (mit `SampleData.http()` und einem zählenden `FakeHttp`):
  - `networkLoadWritesCache` – `load(LIVE)` → `cache.entries` enthält `get_live_categories.json` und `get_live_streams.json`
  - `cacheLoadMakesNoHttpCall` – zweites `Repository` mit gleichem Cache, `load(LIVE, fromCache = true)` → 0 HTTP-Abrufe, gleiche Items, `fromCache == true`
  - `missingCacheFallsBackToNetwork` – leerer Cache, `fromCache = true` → Items geladen, `fromCache == false`
  - `emptyAnswerDoesNotOverwriteCache` – erst normal laden, dann HTTP liefert `[]` für `get_live_streams` → Cache-Eintrag unverändert
  - `m3uIsCachedAsLines` – M3U-Account, `load(LIVE)` dann neues Repository `fromCache = true` → gleiche Items ohne HTTP
  - `credentialsNeverInKeys` – kein Schlüssel in `cache.entries` enthält `"user"`/`"pass"` aus `SampleData.account`
- [ ] **Step 2:** Run `./gradlew :shared:desktopTest --tests '*RepositoryCacheTest*'` → FAIL (`No parameter with name 'cache'`)
- [ ] **Step 3:** Implementieren; bestehender Speicher-Cache (`HashMap` in `Repository`) bleibt für wiederholte Aufrufe innerhalb einer Sitzung.
- [ ] **Step 4:** Run `./gradlew :shared:desktopTest` → alle grün (bestehende `RepositoryTest`s unverändert grün)
- [ ] **Step 5:** Commit `load playlist sections from cache or network`

### Task 3: Startentscheidung und Übersichts-Zustand in `AppState`

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Storage.kt` (`CacheMeta`, Schlüssel `cacheMeta:<SECTION>` in `PER_ACCOUNT_KEYS`)
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/AppState.kt`
- Modify: `testing/.../SampleData.kt` (`testAppState` reicht Cache durch)
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/PlaylistOverviewTest.kt`

**Interfaces:**
- Consumes: `Repository(account, http, cache)`, `load(section, fromCache)`, `SectionLoad`, `clearCache()` (Task 2)
- Produces:
  - `data class CacheMeta(val loadedAt: Long, val count: Int, val previousCount: Int?, val failed: Boolean)`; `Storage.cacheMeta(s: Section): CacheMeta?`, `Storage.saveCacheMeta(s: Section, meta: CacheMeta)`
  - `AppState`-Konstruktor: `newRepository: (Account, PlaylistCacheStore) -> Repository = { a, c -> Repository(a, cache = c) }`, `playlistCache: (accountId: String) -> PlaylistCacheStore = { NoPlaylistCache }`
  - `data class OverviewRow(val section: Section, val count: Int, val delta: Int?, val warning: OverviewWarning?, val loadedAt: Long?)`
  - `enum class OverviewWarning { FAILED, EMPTY, FEWER }`
  - `val overview: List<OverviewRow>?` (Compose-State) – nicht null, solange die Übersicht angezeigt werden soll
  - `fun continueFromOverview()` – schließt Übersicht, startet ggf. letzten Sender
  - `fun reloadFromNetwork()` – ersetzt die Logik von `reloadAll()` (`reloadAll` ruft sie auf): alles aus dem Netz, danach Übersicht
  - `fun onAppResumed(backgroundMillis: Long)` – bei `> RESUME_RESTART_MS` und angemeldetem Anbieter: Start-Ablauf wie Kaltstart (Cache-Entscheidung + Übersicht)
  - `startupDone` bleibt: true, sobald alle Bereiche geladen sind (Ladebildschirm weg); die Übersicht ist ein eigener Zustand danach.
- Regeln (aus der Spec): frisch = `0 <= now - loadedAt < CACHE_MAX_AGE_MS && !failed` für alle drei Bereiche. Netzfehler in einem Bereich mit vorhandenem Cache → Bereich aus Cache laden, `CacheMeta.failed = true`, `loadedAt` unverändert. Leere Antwort bei vorher > 0 → wie Fehler behandeln (alte Daten, `failed = true`, Warnung `EMPTY`). Erfolg → `CacheMeta(now, count, previousCount = alter count, failed = false)`. `delta = count - previousCount`. `FEWER` bei `count < previousCount * 0.9`.
- `logout()` ruft `clearCache()` des entfernten Anbieters.

- [ ] **Step 1: Failing Tests** (feste Uhr über `now`, `InMemoryPlaylistCache` pro Account-ID):
  - `firstStartLoadsFromNetworkAndShowsOverview` – `overview` hat 3 Zeilen mit Anzahl 3/3/1 (SampleData), `delta == null`
  - `overviewWaitsForContinue` – `startWithLastChannel = true` + gemerkter Sender → `screen` bleibt `Home` bis `continueFromOverview()`, danach Player
  - `freshCacheIsUsedWithoutHttp` – zweiter Start 1 h später mit gleichem kv/Cache → 0 Listen-Abrufe beim zählenden `FakeHttp`
  - `staleCacheLoadsFromNetwork` – zweiter Start 25 h später → Listen-Abrufe > 0
  - `clockBehindCacheCountsAsStale` – `now` kleiner als `loadedAt` → Netz
  - `failedSectionKeepsCacheAndWarns` – zweiter Start 25 h später, `get_vod_streams` wirft → Filme aus Cache, Zeile MOVIES `warning == FAILED`, nächster Start (1 h später) lädt wieder aus dem Netz
  - `emptyAnswerKeepsCacheAndWarns` – 25 h später liefert `get_vod_streams` `[]` → Filme weiter 3 Einträge, `warning == EMPTY`
  - `fewerThan90PercentWarns` – zweiter Abruf liefert 2 statt 3 Filme → `delta == -1`, `warning == FEWER`
  - `reloadFromNetworkShowsOverviewAgain` – nach `continueFromOverview()`, `reloadFromNetwork()` → Listen-Abrufe, `overview != null`
  - `resumeAfterMoreThanAnHourRestarts` – `onAppResumed(61 min)` → `overview != null`; `onAppResumed(59 min)` → bleibt null
  - `accountsDoNotShareCache` – zweiter Anbieter → seine Abrufe gehen ins Netz, eigener Cache
  - `logoutClearsCache` – nach `logout()` ist der Cache des Anbieters leer
- [ ] **Step 2:** Run `./gradlew :shared:desktopTest --tests '*PlaylistOverviewTest*'` → FAIL (`Unresolved reference 'overview'`)
- [ ] **Step 3:** Implementieren. Bestehende Tests, die heute `startupDone`/Autostart des letzten Senders prüfen, an „erst nach `continueFromOverview()`“ anpassen – jede Anpassung im Ledger als Ruling.
- [ ] **Step 4:** Run `./gradlew :shared:desktopTest` → alle grün
- [ ] **Step 5:** Commit `start from playlist cache and show load overview`

### Task 4: Desktop – Übersicht und Cache verdrahten

**Files:**
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/Components.kt` (neben `PlaylistLoadingScreen`)
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HomeScreen.kt` (Phasen-Logik um `phase`, ~Z. 160)
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/Main.kt` (`AppState(..., playlistCache = FilePlaylistCache::forAccount)`)
- Test: `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HomeScreenTest.kt`

**Interfaces:** Consumes `overview`, `OverviewRow`, `OverviewWarning`, `continueFromOverview()`, `reloadFromNetwork()` (Task 3)

- [ ] **Step 1: Failing Test** `overviewShowsCountsAndContinues` – frischer `testAppState` → Texte „Playlist“, „3 Sender“, Knöpfe „Neu laden“ und „Weiter“; Klick „Weiter“ → `app.overview == null`, Live-TV sichtbar. Zweiter Test `overviewWarnsAboutFewerEntries` – Zustand mit `FEWER` → Text „deutlich weniger als zuletzt“.
- [ ] **Step 2:** Run `./gradlew :desktopApp:test --tests '*HomeScreenTest*'` → FAIL
- [ ] **Step 3:** `PlaylistOverviewScreen(app)` nach Skizze der Spec: Kopf „Playlist“, Anbieter + „Stand: …“ (`loadedAt` als „heute HH:mm“/„gestern HH:mm“/Datum), je Bereich Zeile mit Anzahl (Tausenderpunkt), ✓ bzw. ⚠, Veränderung („+389 seit letztem Laden“, bei Warnung Text: FAILED → „Fehler – Stand vom …“, EMPTY → „leer – alter Stand behalten“, FEWER → „deutlich weniger als zuletzt“); `SecondaryButton("Neu laden")`, Primärknopf „Weiter“ mit Fokus. Anzeige-Reihenfolge in `HomeScreen`: Ladebildschirm solange `!startupDone`, dann Übersicht solange `overview != null`, dann wie heute.
- [ ] **Step 4:** Run `./gradlew :desktopApp:test` → grün
- [ ] **Step 5:** Commit `show playlist overview on desktop`

### Task 5: Android TV – Übersicht, Cache und Rückkehr nach > 1 h

**Files:**
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/Components.kt` (neben `PlaylistLoadingScreen`), `.../ui/HomeScreen.kt` (~Z. 99)
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/MainActivity.kt` (`AppHolder`: `playlistCache = { AndroidPlaylistCache.forAccount(app, it) }`; Zeitpunkt in `onStop` merken, in `onStart` `state.onAppResumed(now - stoppedAt)`)
- Test: `androidApp/src/androidTest/java/de/vincentsberger/iptvplayer/app/HomeScreenTest.kt`

**Interfaces:** wie Task 4, zusätzlich `onAppResumed(backgroundMillis)`

- [ ] **Step 1: Test** `overviewShowsCountsAndContinues` – wie Desktop; „Weiter“ hat den Fokus (`assertIsFocused()` auf `testTag("overview:continue")`), `select()` → `vm.overview == null`.
- [ ] **Step 2:** Run (Emulator `AndroidTV_1080p_API28`): `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.vincentsberger.iptvplayer.app.HomeScreenTest#overviewShowsCountsAndContinues` → FAIL
- [ ] **Step 3:** `PlaylistOverviewScreen(vm)` mit `TvButton`s („Neu laden“, „Weiter“ mit `FocusRequester` beim Erscheinen), Texte wie Desktop. Lifecycle-Verdrahtung in `MainActivity`.
- [ ] **Step 4:** Run `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest` → grün
- [ ] **Step 5:** Commit `show playlist overview and refresh after long background on Android TV`
