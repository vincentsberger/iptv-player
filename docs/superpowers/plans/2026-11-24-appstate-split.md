# AppState aufteilen – Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `AppState.kt` (1276 Zeilen, ~280 Deklarationen) in sechs fachliche Stores zerlegen; `AppState` bleibt als Fassade mit unveränderter öffentlicher API.

**Architecture:** Jeder Store ist eine Klasse mit eigenem Interface im Paket `…shared.state`. `AppState` erhält die Stores als Konstruktor-Parameter mit Default und leitet per Kotlin-Interface-Delegation weiter (`class AppState(…, private val settings: SettingsStore = SettingsStore(store, clock)) : Settings by settings`). Den aktiven Anbieter teilen sich alle Stores über ein kleines `Session`-Objekt statt über `AppState`. Oberflächen und bestehende Tests bleiben unberührt.

**Tech Stack:** Kotlin Multiplatform 2.4.21, Compose-Runtime-Snapshot-State (`mutableStateOf`, `derivedStateOf`), kotlin.test, detekt 1.23.8.

**Spec:** keine eigene – Anlass ist Punkt 2 des Reviews vom 2026-11-24 („AppState ist eine God-Class; Teil-Stores, AppState als Fassade, Oberflächen bleiben unverändert“).

## Global Constraints

- Öffentliche API von `AppState` unverändert (Namen, Typen, `var`/`val`, Sichtbarkeit von Settern): `androidApp`, `desktopApp`, `iosUi` und alle Tests kompilieren ohne Änderung.
- Keine Verhaltensänderung. Nach jedem Task grün: `./gradlew :shared:desktopTest :desktopApp:test`.
- Nach jedem Task kompilieren alle Oberflächen: `./gradlew :androidApp:compileDebugKotlin :desktopApp:compileKotlin :iosUi:compileKotlinIosSimulatorArm64`.
- Zustand bleibt Compose-Snapshot-State (`mutableStateOf`/`mutableStateMapOf`/`derivedStateOf`) – die Oberflächen beobachten ihn.
- Neue Dateien: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/state/<Name>.kt`, Tests unter `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/state/<Name>Test.kt`.
- Bezeichner englisch, KDoc/Kommentare deutsch (Projektkonvention).
- Store-Mutatoren, die nur `AppState` aufrufen darf, sind `internal` und stehen nicht im Interface.
- detekt: Verschobener Code erzeugt neue Baseline-IDs. Nach jedem Task `./gradlew detekt`; scheitert es nur an verschobenen Altbefunden, `./gradlew detektBaseline` und prüfen, dass `grep -c "<ID>" config/detekt/baseline.xml` nicht steigt (Ausgang: 436).
- Ein Commit je Task, Nachricht englisch, ohne Co-Authored-By.

## Review Focus

- **Anbieterwechsel:** Alle anbieterbezogenen Zustände (Favoriten, Verlauf, ausgeblendete Kategorien, Länder, Dashboard, Medien-Infos, EPG) werden beim Wechsel neu geladen bzw. geleert – jeder Store hat `internal fun reloadForAccount()`, und `AppState.reloadAccountData()` ruft alle. Test in Task 6.
- **Verspätete Antworten nach Wechsel:** Netzwerkergebnisse des alten Anbieters dürfen nach `switchAccount` nicht mehr eintragen (heute `if (repo === r)`). Tests in Task 3 und 4.
- **Init-Reihenfolge:** `AppState.init` lädt sofort (in Tests synchron über `Dispatchers.Unconfined`). Stores sind Konstruktor-Parameter und existieren deshalb vorher – kein Store darf im Konstruktor auf `AppState` zugreifen. Abgedeckt durch die bestehenden Tests mit `testAppState()`.
- **Leistung bei großen Listen:** `section(s)` bleibt ein `derivedStateOf` je Bereich (bei 50 000 Sendern rechnet es sonst bei jedem Lesen). Kein Test; Reviewer prüft, dass `views`/`withoutCountryFilter` weiter gecacht sind.
- **Snapshot-State an den richtigen Stellen:** Felder, die die Oberfläche liest, bleiben `mutableStateOf` (nicht zu `var` „vereinfachen“). Reviewer prüft jede verschobene Property.

---

### Task 1: Einstellungen – `SettingsStore`

**Files:**
- Create: `shared/…/shared/state/SettingsStore.kt`
- Modify: `shared/…/shared/AppState.kt:177-279, 418-440, 1153-1156` (Komfort, Startseiten-Reihen, Updates, Wiedergabe-Einstellungen, Schlaf-Timer, Lautstärke)
- Test: `shared/…/shared/state/SettingsStoreTest.kt`

**Interfaces:**
- Produces: `interface Settings` mit genau diesen Membern aus AppState: `sortAz`, `sortByYear`, `playerLandscape`, `startWithLastChannel`, `autoNextEpisode`, `showOnSystemHome`, `updateCheck`, `availableUpdate`, `checkForUpdate(…)`, `audioDelayMs`, `videoFit`, `audioLanguage`, `subtitleLanguage`, `pauseDownloadsWhilePlaying`, `volume`, `homeRowOrder`, `hiddenHomeRows`, `homeRows`, `toggleHomeRow`, `moveHomeRow`, `resetHomeRows`, `sleepAt`, `cycleSleepTimer()`, `sleepMinutesLeft()`, `consumeSleepTimer()`, `nextEpisodeIndex(items, index)`.
- `class SettingsStore(store: Storage, clock: () -> Long) : Settings` – geräteweit, kein `reloadForAccount`.
- `AppState(…, private val settings: SettingsStore = SettingsStore(store, clock)) : Settings by settings`

- [ ] **Step 1: Failing test** `SettingsStoreTest`
  - `sortAzSurvivesANewStore`: `SettingsStore(Storage(kv), { 0 }).sortAz = true` → neuer Store über dasselbe `kv` liefert `true`.
  - `sleepTimerCyclesAndCountsDown`: Uhr `now = 0`; `cycleSleepTimer()` → `sleepMinutesLeft()` ist die erste Stufe aus dem heutigen Code; Uhr hinter `sleepAt` setzen → `consumeSleepTimer()` ist `true` und danach `sleepAt == null`.
  - `homeRowsMoveAndReset`: `moveHomeRow(HomeRow.entries[1], up = true)` → steht an Index 0; `resetHomeRows()` → Standardreihenfolge.
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*SettingsStoreTest*'` → FAIL (`Unresolved reference 'SettingsStore'`).
- [ ] **Step 3:** Code aus den genannten Abschnitten nach `SettingsStore` verschieben (unverändert), `Settings` aus den öffentlichen Membern bilden; in AppState löschen und delegieren. `startWithLastChannel` liest AppState weiter über das Interface.
- [ ] **Step 4:** Test grün; Global-Constraint-Befehle grün.
- [ ] **Step 5:** Commit `move playback and comfort settings out of AppState into SettingsStore`

### Task 2: Kategorien, Länder, Sortierung – `LibraryStore`

**Files:**
- Create: `shared/…/shared/state/LibraryStore.kt`
- Modify: `AppState.kt:127` (`sections`), `:280-416` (ausblenden, Länderfilter, `views`, `applyPrefs`, `section()`)
- Test: `shared/…/shared/state/LibraryStoreTest.kt`

**Interfaces:**
- Consumes: `Settings.sortAz`, `Settings.sortByYear` (Task 1).
- Produces: `interface Library` mit `sections: SnapshotStateMap<Section, SectionState>` (Rohdaten, schreibt weiter AppState beim Laden), `section(s)`, `hidden`, `isHidden`, `setHidden`, `toggleHidden`, `hiddenCategories(s)`, `countries(s)`, `activeCountries(s)`, `availableCountries(s)`, `toggleCountry`, `clearCountries`, `liveCountries`, `activeLiveCountries`, `availableLiveCountries()`, `toggleLiveCountry`, `clearLiveCountries()`.
- `class LibraryStore(store: Storage, settings: Settings) : Library` mit `internal fun sectionWithoutCountryFilter(s: Section): SectionState` (für Favoriten, Task 5) und `internal fun reloadForAccount()` (lädt `hidden` und Länder neu).

- [ ] **Step 1: Failing test** `LibraryStoreTest` (Store direkt, `sections` mit zwei Kategorien „DE | Nachrichten“ und „AT | Sport“ befüllen)
  - `hiddenCategoryDisappearsFromTheView`: `toggleHidden(LIVE, "1")` → `section(LIVE).categories` ohne `"1"`.
  - `countryFilterKeepsOnlyChosenTags`: `toggleCountry(LIVE, "DE")` → nur die DE-Kategorie sichtbar; `sectionWithoutCountryFilter(LIVE)` zeigt beide.
  - `sortAzFollowsSettings`: `settings.sortAz = true` → Kategorien alphabetisch nach Namen ohne Kürzel.
- [ ] **Step 2:** Test → FAIL.
- [ ] **Step 3:** Verschieben; `views`/`withoutCountryFilter` bleiben `derivedStateOf` je Bereich. AppState delegiert `Library`, ruft in `reloadAccountData()` `library.reloadForAccount()` statt der Einzelzeilen.
- [ ] **Step 4:** Grün (inkl. `CountryFilterTest`, `SortByYearTest`, `BrowseTest`).
- [ ] **Step 5:** Commit `move category, country and sort views into LibraryStore`

### Task 3: Programmdaten – `Session` und `EpgStore`

**Files:**
- Create: `shared/…/shared/state/Session.kt`, `shared/…/shared/state/EpgStore.kt`
- Modify: `AppState.kt:120` (`repo` → `session.repo`), `:508-529` (Programm, `localNow`, `canCatchUp`), `:847-886` („Jetzt läuft“), `:1227-1230` (`nowPlaying`)
- Test: `shared/…/shared/state/EpgStoreTest.kt`

**Interfaces:**
- Produces: `class Session { var repo: Repository? }` – einzige Quelle für den aktiven Anbieter; `AppState(…, private val session: Session = Session())`.
- Produces: `interface Epg` mit `programmes`, `requestProgramme(item, force)`, `localNow()`, `canCatchUp(channel, entry)`, `nowFor(item)`, `requestEpg(item)`, `suspend nowPlaying(item)`.
- `class EpgStore(session: Session, scope: CoroutineScope, clock: () -> Long, localTime: (Long) -> String) : Epg` mit `internal fun clear()` (ersetzt `epgCache.clear()` in `activate`/`restartLoading`).
- `playCatchup` bleibt in AppState (öffnet den Player).

- [ ] **Step 1: Failing test** `EpgStoreTest` (FakeHttp-Repository wie in `AppStateTest`, `scope` mit `Dispatchers.Unconfined`)
  - `requestEpgFillsNowFor`: `requestEpg(channel)` → `nowFor(channel)` ist die erste Sendung der Attrappe.
  - `channelWithoutEpgIdIsNeverAsked`: `requestEpg(channel.copy(hasEpg = false))` → FakeHttp hat keine `get_short_epg`-Anfrage gesehen.
  - `answerForPreviousAccountIsDropped`: Anfrage mit einem Repository, dessen Antwort erst nach `session.repo = anderes` freigegeben wird (CompletableDeferred in FakeHttp) → `programmes` bleibt leer.
- [ ] **Step 2:** Test → FAIL.
- [ ] **Step 3:** Verschieben; den `repo === r`-Schutz in `requestProgramme` ergänzen, falls er dort fehlt (Test 3 erzwingt ihn).
- [ ] **Step 4:** Grün (inkl. `ResumeAndEpgTest`, `CatchupTest`, `EpgExcerptTest`).
- [ ] **Step 5:** Commit `move EPG caching into EpgStore and share the active provider via Session`

### Task 4: Medien-Infos und Staffeln – `MediaInfoStore`

**Files:**
- Create: `shared/…/shared/state/MediaInfoStore.kt`
- Modify: `AppState.kt:163-165` (`seasons*`), `:486-507`, `:540-577`, `openSeries` (`:1198-1218`, nur die Zuweisungen an `seasons*`)
- Test: `shared/…/shared/state/MediaInfoStoreTest.kt`

**Interfaces:**
- Consumes: `Session` (Task 3), `Library.sections` (Task 2, Serienordner suchen).
- Produces: `interface MediaDetails` (nicht `MediaInfo` – Namenskonflikt mit `data.MediaInfo`): `seasons`, `seasonsLoading`, `seasonsError`, `mediaInfoFor`, `seriesInfoFor`, `episodeDetail`, `requestMediaInfo`, `episodeLabel`, `pauseTitles`, `seriesNameOf`.
- `class MediaInfoStore(session: Session, library: Library, scope: CoroutineScope) : MediaDetails` mit `internal fun seasonsLoading()`, `internal fun seasonsLoaded(list: List<Season>)`, `internal fun seasonsFailed(message: String)`, `internal fun reloadForAccount()` (leert `mediaInfos`, `episodeSeasons`).

- [ ] **Step 1: Failing test** `MediaInfoStoreTest`
  - `movieInfoIsFetchedOnce`: zweimal `requestMediaInfo(movie)` → genau eine `get_vod_info`-Anfrage, `mediaInfoFor(movie)` gesetzt.
  - `seasonsStatesFollowTheMutators`: `seasonsLoading()` → `seasonsLoading == true`; `seasonsLoaded(list)` → `seasons == list`, `seasonsLoading == false`.
  - `answerForPreviousAccountIsDropped`: wie in Task 3, für `requestMediaInfo`.
- [ ] **Step 2–4:** FAIL → verschieben → grün (inkl. `PauseInfoTest`, `MovieDetailsTest`).
- [ ] **Step 5:** Commit `move media info and seasons into MediaInfoStore`

### Task 5: Favoriten, Verlauf, letzte Suchen – `HistoryStore`

**Files:**
- Create: `shared/…/shared/state/HistoryStore.kt`
- Modify: `AppState.kt:131-149` (`favorites`, `history`, `recentSearches`, `rememberSearch`, `clearRecentSearches`), `:977-985`, `:1094-1099`, `:1108-1152`, `resumePositionOf`, `lastEpisodeOf`
- Test: `shared/…/shared/state/HistoryStoreTest.kt`

**Interfaces:**
- Consumes: `LibraryStore.sectionWithoutCountryFilter` (Task 2) für `favoriteItems`.
- Produces: `interface Favorites` mit `favorites`, `history`, `recentSearches`, `rememberSearch(term)`, `clearRecentSearches()`, `setFavorite`, `toggleFavorite`, `saveProgress`, `clearHistory`, `continueWatching(s)`, `continueWatchingAll`, `recentChannels`, `favoriteItems(s)`, `progressOf`, `resumePositionOf`, `lastEpisodeOf`.
- `rememberSearch(term: String = query)`: der Default `query` liegt in AppState → im Interface ohne Default, AppState behält eine Überladung `fun rememberSearch() = history.rememberSearch(query)`.
- `class HistoryStore(store: Storage, library: LibraryStore, clock: () -> Long) : Favorites` mit `internal fun keepFavoritesOfDuplicates(alternates: Map<String, List<Item>>)` und `internal fun reloadForAccount()`.

- [ ] **Step 1: Failing test** `HistoryStoreTest`
  - `progressIsSavedNewestFirst`: zwei `saveProgress` → `history.first()` ist der zweite Titel; neuer Store über dasselbe `kv` liefert dieselbe Liste.
  - `favoriteOfACollapsedDuplicateMovesToTheKeptEntry`: Favorit auf Duplikat, `keepFavoritesOfDuplicates(mapOf(kept.favKey to listOf(dup)))` → `favorites` enthält `kept.favKey`.
- [ ] **Step 2–4:** FAIL → verschieben → grün (inkl. `RecentSearchesTest`, `DuplicatesTest`, `ResumeFromOutsideTest`).
- [ ] **Step 5:** Commit `move favourites, history and recent searches into HistoryStore`

### Task 6: Startseite und Kataloge – `DashboardStore`, `CatalogStore`; Fassade abschließen

**Files:**
- Create: `shared/…/shared/state/DashboardStore.kt`, `shared/…/shared/state/CatalogStore.kt`
- Modify: `AppState.kt:578-756` (Dashboard, Abo, Kataloge), `reloadAccountData()`, Klassen-KDoc
- Test: `shared/…/shared/state/CatalogStoreTest.kt`, `shared/…/shared/AppStateTest.kt` (ein neuer Test)

**Interfaces:**
- Consumes: `Session`, `Library`, `Favorites` (Tasks 2, 3, 5).
- Produces: `interface Dashboard` (`supportsDashboard`, `dashboardCategories`, `dashboardConfigured`, `toggleDashboardCategory`, `dashboardCategoryOptions`, `newMovies`, `newEpisodes`, `newItemsOf`, `accountStatus`, `accountStatusStale`, `accountExpiresSoon()`) mit `internal fun refreshAccountStatus()`, `internal fun reloadForAccount()`.
- Produces: `interface Catalog` (`browseCategory`, `rememberBrowseCategory`, `browseCategories`, `genreCategories`, `heroItem`, `genreRows`, `similarTitles`, `categoryOf`, `browseItems`, `initialBrowseCategory`). `showNewInBrowse` bleibt in AppState (wechselt den Tab).

- [ ] **Step 1: Failing tests**
  - `CatalogStoreTest.genreRowsComeFromTheVisibleItems`: ausgeblendete Kategorie → ihre Titel fehlen in `genreRows(MOVIES)`.
  - `AppStateTest.switchingAccountResetsEveryStore`: Anbieter A mit Favorit, ausgeblendeter Kategorie, Länderfilter „DE“, Dashboard-Kategorie; `addAccount` + `login(B)` → alle vier leer; `switchAccount(A)` → alle vier wieder da.
- [ ] **Step 2–4:** FAIL → verschieben → grün (inkl. `DashboardTest`, `BrowseRowsTest`, `GenreTest`, `CategoryOfTest`).
- [ ] **Step 5:** AppState-KDoc: Fassade, Liste der Stores mit je einem Satz; `wc -l AppState.kt` < 600; detekt-Befund `LargeClass` für AppState aus der Baseline verschwunden.
- [ ] **Step 6:** Commit `move dashboard and catalog views into stores; AppState is now a facade`
