# Dashboard auf der Startseite (Teil 2) – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Startseite um Abo-Zeile, „Neue Filme“ und „Neue Folgen“ (letzte 7 Tage, aus selbst gewählten Kategorien, nie Erwachseneninhalte) erweitern.

**Architecture:** `Item` bekommt `addedAt`/`isAdult` aus den Xtream-Listen; `XtreamClient` liest `user_info` als `AccountInfo`. `AppState` hält Kategorienauswahl (pro Anbieter), berechnet die Neu-Reihen als `derivedStateOf` und holt bei jedem Start die Abo-Daten (mit gespeichertem Rückfall). Beide Startseiten zeigen Abo-Zeile, Reihen bzw. Auswahl-Karte; „Alle anzeigen“ öffnet in Filme/Serien die virtuelle Kategorie „Neu (7 Tage)“.

**Tech Stack:** Kotlin Multiplatform, Compose (Android TV / Desktop), kotlin.test, Compose UI-Tests.

**Spec:** `docs/superpowers/specs/2026-11-23-playlist-cache-dashboard-design.md` (Teil 2)

## Global Constraints

- „Neu“ = `addedAt` (Filme: `added`, Serien: `last_modified`, Sekunden → ms) innerhalb von 7 Tagen vor `clock()`: `NEW_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L`.
- Nur Kategorien aus der Positivliste (pro Anbieter, getrennt nach Filme/Serien); neue Anbieter-Kategorien sind nicht automatisch gewählt.
- Erwachseneninhalte nie in Reihen und Auswahl: `is_adult = "1"` am Eintrag **oder** Kategoriename enthält „ADULT“ (Groß/Klein egal).
- Reihen: neueste zuerst; leere Reihe entfällt; M3U: keine Neu-Reihen, keine Auswahl, keine Abo-Zeile.
- Abo-Zeile (nur Xtream): `exp_date` fehlt/0 → „unbegrenzt“; hervorgehoben bei Ablauf in ≤ 7 Tagen; ohne Netz letzte bekannte Werte mit „Stand vom …“.
- Reihenfolge Startseite: Abo · Weiterschauen · Neue Filme · Neue Folgen · Zuletzt gesehen · (Offline) · Favoriten.
- Texte deutsch, Bezeichner englisch.

## Review Focus

- **`added` fehlt oder ist kein Zahlwert** bei einzelnen Einträgen → Eintrag gilt nicht als neu, kein Absturz. (Test in Task 1)
- **`exp_date` als `null`, `""` oder `"0"`** → „unbegrenzt“. (Test in Task 1)
- **Gewählte Kategorie gibt es beim Anbieter nicht mehr** → wird ignoriert, Reihe ggf. leer/entfällt. (Test in Task 2)
- **Ausgeblendete Kategorie ist gleichzeitig gewählt** → Ausblenden gewinnt (Reihen lesen über `section()`). (Test in Task 2)
- **Login-Abruf für die Abo-Zeile scheitert** → Start läuft normal weiter, Zeile zeigt alten Stand. (Test in Task 2)

---

### Task 1: Daten – `addedAt`, `isAdult`, `AccountInfo`

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Models.kt` (`Item`)
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/XtreamClient.kt` (`parseItems`, `checkLogin`)
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Repository.kt`
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/data/XtreamClientTest.kt`

**Interfaces:**
- Produces: `Item(..., val addedAt: Long? = null, val isAdult: Boolean = false)` (neue Parameter am Ende, mit Default – bestehende Aufrufe bleiben gültig; `toJson`/`fromJson` unverändert)
- Produces: `data class AccountInfo(val expiresAt: Long?, val maxConnections: Int?, val activeConnections: Int?)` in `Models.kt`
- Produces: `XtreamClient.accountInfo: AccountInfo?` (nach `login()` gesetzt) und `suspend fun XtreamClient.fetchAccountInfo(): AccountInfo` (Login-Abruf gegen `base`, wirft bei Fehler)
- Produces: `suspend fun Repository.accountInfo(): AccountInfo?` – null bei M3U, sonst `fetchAccountInfo()` (wirft bei Netzfehler; läuft auf `work`)

- [ ] **Step 1: Failing Tests** in `XtreamClientTest`:
  - `moviesCarryAddedAndAdultFlag` – `get_vod_streams` mit `"added":"1700000000","is_adult":"1"` und einem Eintrag ohne `added` → `addedAt == 1_700_000_000_000L`, `isAdult == true`; zweiter `addedAt == null`, `isAdult == false`
  - `seriesUseLastModifiedAsAdded` – `"last_modified":"1700000000"` → `addedAt == 1_700_000_000_000L`
  - `accountInfoFromLogin` – Login-Antwort `{"user_info":{"auth":1,"status":"Active","exp_date":"1800000000","max_connections":"2","active_cons":"1"}}` → `AccountInfo(1_800_000_000_000L, 2, 1)`
  - `unlimitedAccountHasNoExpiry` – `exp_date` als `null`, `""` und `"0"` → `expiresAt == null`
- [ ] **Step 2:** Run `./gradlew :shared:desktopTest --tests '*XtreamClientTest*'` → FAIL (`No parameter with name 'addedAt'` bzw. `Unresolved reference 'accountInfo'`)
- [ ] **Step 3:** Implementieren (`toLongOrNull()`, `0` → null).
- [ ] **Step 4:** Run `./gradlew :shared:desktopTest` → alle grün
- [ ] **Step 5:** Commit `read added date, adult flag and account info from Xtream`

### Task 2: Dashboard-Logik in `AppState`

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Storage.kt` (Schlüssel `dashboardCats`, `accountInfo` in `PER_ACCOUNT_KEYS`)
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/AppState.kt`
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/DashboardTest.kt`

**Interfaces:**
- Consumes: `Item.addedAt`, `Item.isAdult`, `AccountInfo`, `Repository.accountInfo()` (Task 1)
- Produces (`Storage`): `fun dashboardCategories(): Set<String>` / `fun saveDashboardCategories(keys: Set<String>)` (Schlüssel `"<SECTION>:<categoryId>"`); `fun accountStatus(): AccountStatus?` / `fun saveAccountStatus(status: AccountStatus)`
- Produces (Modelle): `data class AccountStatus(val info: AccountInfo, val fetchedAt: Long)`
- Produces (`AppState`):
  - `val supportsDashboard: Boolean` – aktiver Anbieter ist Xtream
  - `var dashboardCategories: Set<String>` (State, `private set`); `fun toggleDashboardCategory(s: Section, categoryId: String)`
  - `fun dashboardCategoryOptions(s: Section): List<Category>` – Kategorien von MOVIES/SERIES ohne „ADULT“, Anbieter-Reihenfolge
  - `val dashboardConfigured: Boolean` – mindestens eine Kategorie gewählt
  - `val newMovies: List<Item>` / `val newEpisodes: List<Item>` – `derivedStateOf`, aus `section(s)` (Ausblenden gilt), nur gewählte Kategorien, nicht adult (Eintrag oder Kategoriename), `addedAt` in `(clock() - NEW_WINDOW_MS)..clock()`, absteigend
  - `var accountStatus: AccountStatus?` (State); `val accountStatusStale: Boolean` – letzter Abruf beim aktuellen Start gescheitert
  - Beim Start (`startLoading`) für Xtream: `scope.launch` Abruf `repo.accountInfo()` → bei Erfolg `AccountStatus(info, clock())` speichern; bei Fehler gespeicherten Wert behalten, `accountStatusStale = true`. Fehler beeinflussen Laden/Übersicht nicht.
  - `fun showNewInBrowse(s: Section)` – `store.saveLastBrowseCategory(s, BROWSE_NEW)` und Tab Filme/Serien wählen; `const val BROWSE_NEW = "__new"`
  - `val NEW_WINDOW_MS` im Companion
- Abo-Hervorhebung: `fun accountExpiresSoon(): Boolean` – `expiresAt != null && expiresAt - clock() <= 7 Tage`.

- [ ] **Step 1: Failing Tests** (`DashboardTest`, eigene Antworten über `SampleData.http(overrides)`, `now` fest; Filme mit `added` relativ zu `now`, ein Eintrag `is_adult`, eine Kategorie „FOR ADULTS“):
  - `nothingChosenMeansNoRowsButNotConfigured` – `dashboardConfigured == false`, `newMovies` leer
  - `newMoviesComeOnlyFromChosenCategoriesWithinSevenDays` – Kategorie wählen → nur deren Filme mit `added` ≥ now−7 Tage, neueste zuerst; 8 Tage alter Film fehlt
  - `adultNeverAppears` – `is_adult`-Film in gewählter Kategorie fehlt; „FOR ADULTS“ nicht in `dashboardCategoryOptions(MOVIES)`
  - `newEpisodesUseLastModified` – Serie mit `last_modified` vor 2 Tagen in gewählter Kategorie erscheint
  - `choiceIsPerAccountAndSurvivesRestart`
  - `vanishedCategoryIsIgnored` – gewählte ID, die es nicht mehr gibt → keine Ausnahme, Reihe ohne sie
  - `hiddenCategoryWins` – gewählt + ausgeblendet → kein Film daraus
  - `accountStatusIsFetchedAndKeptWhenOffline` – Login liefert `exp_date` → `accountStatus.info.expiresAt` gesetzt; zweiter Start mit scheiterndem Login → alter Wert, `accountStatusStale == true`, Übersicht trotzdem da
  - `expiresSoonWithinSevenDays`
  - `m3uHasNoDashboard` – M3U-Anbieter → `supportsDashboard == false`
  - `showNewInBrowseSelectsTabAndCategory`
- [ ] **Step 2:** Run `./gradlew :shared:desktopTest --tests '*DashboardTest*'` → FAIL
- [ ] **Step 3:** Implementieren.
- [ ] **Step 4:** Run `./gradlew :shared:desktopTest` → alle grün
- [ ] **Step 5:** Commit `add dashboard logic: chosen categories, new items, account status`

### Task 3: Desktop – Startseite, Auswahl, „Neu“ in Filme/Serien

**Files:**
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HomeScreen.kt` (`StartScreen` ~Z. 672, `BrowseScreen` virtuelle Kategorien ~Z. 520–552, `ComfortSettings`)
- Test: `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HomeScreenTest.kt`

**Interfaces:** Consumes alles aus Task 2.

- [ ] **Step 1: Failing Tests:**
  - `startScreenShowsAccountLineAndNewMovies` – gewählte Kategorie, Film neu → Texte „Abo: läuft bis“, „Neue Filme“, Filmtitel
  - `startScreenOffersCategoryChoiceWhenNothingChosen` – Karte „Wähle, was dich interessiert“; Klick → Einstellungen mit Abschnitt „Startseite“
  - `showAllOpensNewCategoryInMovies` – „Alle anzeigen“ bei „Neue Filme“ → Tab Filme, Kategorie „Neu (7 Tage)“ ausgewählt
- [ ] **Step 2:** Run `./gradlew :desktopApp:test --tests '*HomeScreenTest*'` → FAIL
- [ ] **Step 3:** Startseite: Abo-Zeile oben (Text „Abo: läuft bis TT.MM.JJJJ · A von M Verbindungen“, „unbegrenzt“, bei Ablauf ≤ 7 Tage in `Danger`, bei `accountStatusStale` Zusatz „Stand vom …“); Reihen „Neue Filme“/„Neue Folgen“ nach „Weiterschauen“ mit „Alle anzeigen“ (`showNewInBrowse`); ohne Auswahl Karte mit Knopf → `selectTab(Tab.SETTINGS)`. Einstellungen: Abschnitt „Startseite“ mit zwei Chip-Gruppen (Filme, Serien) wie `CountryChip`. Browse: virtuelle Kategorie `AppState.BROWSE_NEW` „Neu (7 Tage)“ vor „Favoriten“, nur wenn die Liste nicht leer ist.
- [ ] **Step 4:** Run `./gradlew :desktopApp:test` → grün
- [ ] **Step 5:** Commit `show dashboard rows and category choice on desktop`

### Task 4: Android TV – Startseite, Auswahl, „Neu“ in Filme/Serien

**Files:**
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/StartScreen.kt`, `.../ui/BrowseScreens.kt` (virtuelle Kategorien ~Z. 104–145, `ComfortSettings`)
- Test: `androidApp/src/androidTest/java/de/vincentsberger/iptvplayer/app/StartScreenTest.kt` (anlegen, falls nicht vorhanden; sonst ergänzen)

**Interfaces:** wie Task 3.

- [ ] **Step 1: Tests** analog Task 3 (Klicks über `testTag`: `start:choose`, `start:showall:MOVIES`, `dashcat:MOVIES:<id>`).
- [ ] **Step 2:** Run (Emulator `AndroidTV_1080p_API28`) die neuen Tests → FAIL
- [ ] **Step 3:** Wie Desktop, mit `TvButton`s (fokussierbar), Abschnitt „Startseite“ im Panel „Listen“.
- [ ] **Step 4:** Run `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest` → grün
- [ ] **Step 5:** Commit `show dashboard rows and category choice on Android TV`
