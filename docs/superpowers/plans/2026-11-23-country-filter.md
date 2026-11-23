# Länderfilter für Live-TV – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Live-TV pro Anbieter auf gewählte Länderkürzel (z. B. `DE`, `AT`, `CH`) eingrenzen.

**Architecture:** Auswahl wird pro Anbieter in `Storage` gespeichert. `AppState.applyPrefs` filtert Live-Kategorien nach
dem Kürzel aus `splitProviderTag` – alle Listen, „Alle“, Suche, Player-Senderliste und Zappen lesen schon über
`section()`. Android TV und Desktop bekommen je einen Einstellungsabschnitt und einen Hinweis in Live-TV.

**Tech Stack:** Kotlin Multiplatform (`shared`), Compose (Android TV / Desktop), kotlin.test, Compose UI-Tests.

**Spec:** `docs/superpowers/specs/2026-11-23-country-filter-design.md`

## Global Constraints

- Nur `Section.LIVE` wird gefiltert; Filme/Serien unverändert.
- Leere Auswahl = kein Filter (Standard). Kategorien ohne Kürzel bleiben immer sichtbar.
- Gewählte Kürzel, die beim Anbieter nicht (mehr) vorkommen, zählen nicht; bleibt keins übrig, ist der Filter aus.
- Speicherung pro Anbieter (Schlüssel in `PER_ACCOUNT_KEYS`), überlebt Neustart.
- Texte deutsch; Code-Bezeichner englisch.
- Build/Tests: `./gradlew` (Daemon-JDK 17 über `gradle/gradle-daemon-jvm.properties`).

## Review Focus

- **Offene Kategorie fällt weg:** Live-TV steht auf „DE | Sport“, Nutzer wählt nur `AT` → erwartet „Alle“ statt leerer Liste. (Test in Task 1)
- **Anbieter liefert gewähltes Kürzel nicht mehr:** Auswahl `{FR}`, Anbieter hat kein `FR` → alle Kategorien sichtbar, `FR` bleibt in der Auswahl-Liste zum Abwählen (Anzahl 0). (Test in Task 1)
- **Anbieterwechsel:** Auswahl von Anbieter A darf bei Anbieter B nicht greifen. (Test in Task 1)
- **Zusammenspiel mit „Kategorie ausblenden“ und A–Z:** ausgeblendete DE-Kategorie bleibt ausgeblendet, Sortierung bleibt. (Test in Task 1)
- **Suche:** Sender aus ausgefilterten Ländern tauchen nicht in der Suche auf. (Test in Task 1)

---

### Task 1: Filterlogik und Speicherung (`shared`)

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Storage.kt` (Schlüssel `liveCountries` in `PER_ACCOUNT_KEYS`; Getter/Setter neben `hiddenCategories`)
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/AppState.kt` (neben `hidden`/`applyPrefs`, Reload bei Anbieterwechsel wie `hidden` in Zeile ~374)
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/CountryFilterTest.kt`

**Interfaces:**
- Produces (`Storage`): `fun liveCountries(): Set<String>`, `fun saveLiveCountries(tags: Set<String>)` – JSON-Array wie `hiddenCategories`.
- Produces (`AppState`):
  - `var liveCountries: Set<String>` (Compose-State, `private set`) – gespeicherte Auswahl
  - `fun toggleLiveCountry(tag: String)` / `fun clearLiveCountries()` – speichern sofort; springt auf `LIVE_ALL`, wenn `liveCategory` danach nicht mehr in `section(Section.LIVE).categories` ist
  - `val activeLiveCountries: Set<String>` – Auswahl ∩ Kürzel der geladenen Live-Kategorien (leer = Filter aus)
  - `fun availableLiveCountries(): List<CountryOption>` – alle Kürzel der **ungefilterten** Live-Kategorien mit Anzahl, absteigend nach Anzahl, dann alphabetisch; gewählte, aber fehlende Kürzel mit `count = 0` am Ende
- Produces (`shared/.../data/Models.kt`): `data class CountryOption(val tag: String, val count: Int)`

- [ ] **Step 1: Failing Tests schreiben** – eigene Antworten über `SampleData.http(overrides)`:

```kotlin
private val tagged = mapOf(
    "get_live_categories" to """[{"category_id":"1","category_name":"DE | Nachrichten"},{"category_id":"2","category_name":"AT | Sport"},
        {"category_id":"3","category_name":"DE | Sport"},{"category_id":"4","category_name":"FOR ADULTS"}]""",
    "get_live_streams" to """[{"stream_id":1,"name":"Das Erste","category_id":"1"},{"stream_id":2,"name":"ORF Sport","category_id":"2"},
        {"stream_id":3,"name":"Sport1","category_id":"3"},{"stream_id":4,"name":"Nacht","category_id":"4"}]""",
)
private fun app(kv: InMemoryKeyValueStore = InMemoryKeyValueStore()) = testAppState(http = SampleData.http(tagged), kv = kv)
```

Tests (Namen + Kern-Assertions):
- `noSelectionShowsEverything` – 4 Kategorien, `activeLiveCountries` leer
- `selectedCountryKeepsItsCategoriesAndUntaggedOnes` – `toggleLiveCountry("DE")` → Kategorie-IDs `["1","3","4"]`, Sender ohne „ORF Sport“; `query = "orf"` → `searchResults[Section.LIVE] == null`
- `availableCountriesAreCountedAndSorted` – `[CountryOption("DE",2), CountryOption("AT",1)]`, auch bei aktivem Filter `{AT}` unverändert
- `selectionSurvivesRestartAndIsPerAccount` – neue `testAppState(kv = kv)` hat `{"DE"}`; nach `addAccount()` + `login(Account.Xtream("other.example","u","p"))` (Muster aus `AppStateTest`, Zeile ~106) ist die Auswahl leer; `switchAccount(<erste id>)` bringt `{"DE"}` zurück
- `missingCountryDisablesFilterButStaysSelectable` – `toggleLiveCountry("FR")` → 4 Kategorien sichtbar, `activeLiveCountries` leer, `availableLiveCountries().last() == CountryOption("FR", 0)`
- `openCategoryFallsBackToAllWhenFilteredOut` – `selectTab(Tab.LIVE)`, `liveCategory = "2"`, `toggleLiveCountry("DE")` → `liveCategory == LIVE_ALL`
- `filterCombinesWithHiddenAndSort` – `toggleHidden(LIVE,"3")`, `sortAz = true`, `toggleLiveCountry("DE")` → Kategorien `["1","4"]`
- `clearShowsEverythingAgain` – nach `clearLiveCountries()` 4 Kategorien

- [ ] **Step 2: Tests laufen lassen, Fehlschlag prüfen**
Run: `./gradlew :shared:desktopTest --tests '*CountryFilterTest*'`
Expected: FAIL – `Unresolved reference 'toggleLiveCountry'`

- [ ] **Step 3: Implementieren** – `applyPrefs` für `Section.LIVE`: Kategorie sichtbar, wenn nicht ausgeblendet und (`activeLiveCountries` leer oder Kürzel `null` oder Kürzel ∈ aktiv). Die Abkürzung am Anfang von `applyPrefs` muss den aktiven Filter berücksichtigen. `availableLiveCountries` liest `sections[Section.LIVE]` (roh), nicht `section()`.

- [ ] **Step 4: Tests grün**
Run: `./gradlew :shared:desktopTest`
Expected: BUILD SUCCESSFUL, alle bestehenden Tests weiterhin grün

- [ ] **Step 5: Commit**
```bash
git add shared/src && git commit -m "add per-account country filter for live TV"
```

### Task 2: Desktop – Einstellungen und Hinweis

**Files:**
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HomeScreen.kt` (`ComfortSettings` ~Zeile 789; Live-TV-Kopf ~Zeile 240)
- Test: `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `availableLiveCountries()`, `liveCountries`, `activeLiveCountries`, `toggleLiveCountry`, `clearLiveCountries` aus Task 1

- [ ] **Step 1: Failing Test** `countryFilterInSettingsNarrowsLiveTv` – App mit den getaggten Daten aus Task 1 (`show(app = testAppState(http = SampleData.http(tagged)))`, `tagged` wie in Task 1), `nav("Einstellungen")`, Klick auf Knoten mit Text `"DE"` im Abschnitt „Länder in Live-TV“ → `app.liveCountries == setOf("DE")`; zurück zu Live-TV → Text `"Gefiltert: DE"` existiert, „ORF Sport“ nicht; Klick „Alle zeigen“ → `liveCountries` leer.

- [ ] **Step 2: Fehlschlag prüfen** – Run: `./gradlew :desktopApp:test --tests '*HomeScreenTest*'` → FAIL (Knoten nicht gefunden)

- [ ] **Step 3: Implementieren** – in `ComfortSettings` nach dem Sortier-/Ausblende-Block: `Rule`, Titel „Länder in Live-TV“, Hinweistext aus der Spec, `FlowRow` mit einem umschaltbaren Chip je `CountryOption` (`TagChip`-Optik + Anzahl, ausgewählt hervorgehoben, `testTag("country:<TAG>")`), `SecondaryButton("Alle zeigen")` nur wenn Auswahl nicht leer. Abschnitt ausblenden, wenn `availableLiveCountries()` leer (M3U ohne Kürzel). In Live-TV unter dem Titel `"Gefiltert: " + activeLiveCountries.sorted().joinToString(", ")`, nur wenn aktiv.

- [ ] **Step 4: Tests grün** – Run: `./gradlew :desktopApp:test` → BUILD SUCCESSFUL

- [ ] **Step 5: Commit** – `git commit -m "add country filter settings to desktop app"`

### Task 3: Android TV – Einstellungen und Hinweis

**Files:**
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/BrowseScreens.kt` (`ComfortSettings`, Panel „Listen“ ~Zeile 337)
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/LiveTvScreen.kt` (Kopf ~Zeile 146, neben „N Sender“)
- Test: `androidApp/src/androidTest/java/de/vincentsberger/iptvplayer/app/HomeScreenTest.kt`

**Interfaces:**
- Consumes: wie Task 2

- [ ] **Step 1: Test schreiben** `countryFilterInSettingsNarrowsLiveTv` – analog Task 2, Klick über `testTag("country:DE")`; prüft `vm.liveCountries` und Hinweistext.

- [ ] **Step 2: Kompilieren** – instrumentierte Tests brauchen einen Emulator und laufen nicht in der CI.
Run: `./gradlew :androidApp:compileDebugAndroidTestKotlin` → FAIL (`Unresolved reference`)

- [ ] **Step 3: Implementieren** – im Panel „Listen“ nach „Ausgeblendete Kategorien“: Titel „Länder in Live-TV“, Hinweis, `FlowRow` mit `TvButton` je `CountryOption` (Text `"${tag} ${count}"`, davor `FlagOrTag`-Flagge falls vorhanden, `selected = tag in vm.liveCountries`, `testTag("country:<TAG>")`), `TvButton("Alle zeigen")` nur bei Auswahl. D-Pad: Buttons sind fokussierbar, keine Extra-Fokuslogik. Hinweis „Gefiltert: …“ im Live-TV-Kopf.

- [ ] **Step 4: Prüfen**
Run: `./gradlew :androidApp:compileDebugAndroidTestKotlin :androidApp:testDebugUnitTest :androidApp:assembleDebug` → BUILD SUCCESSFUL.
Mit Emulator/TV-Box zusätzlich: `./gradlew :androidApp:connectedDebugAndroidTest`.

- [ ] **Step 5: Commit** – `git commit -m "add country filter settings to Android TV app"`

### Task 4: README

- [ ] In `README.md` unter „Funktionen“ die Zeile „Schlaf-Timer, Kategorien ausblenden, …“ um „**Länderfilter** für Live TV“ ergänzen; Commit `document country filter in README`.
