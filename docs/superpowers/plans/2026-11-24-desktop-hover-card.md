# PC: Info-Karte beim Überfahren, Hand-Mauszeiger – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Am PC zeigt eine Film- oder Serienkachel nach kurzem Verweilen eine schwebende Info-Karte mit vollem Titel, Fakten und Beschreibung; alle klickbaren Elemente zeigen die Hand.

**Architecture:** `Item.plot` kommt für Serien aus der Liste (`shared`), Filme laden die Beschreibung erst nach dem Verweilen über `requestMediaInfo`. Die Karte ist ein an `MediaCard` verankertes `Popup` in `desktopApp`; der Hand-Zeiger ist ein gemeinsamer Modifier in `Components.kt`.

**Tech Stack:** Compose Desktop (`Popup`, `pointerHoverIcon`, `hoverable`), Compose-UI-Tests (`performMouseInput`, `mainClock`).

**Spec:** `docs/superpowers/specs/2026-11-24-desktop-hover-card-design.md`

## Global Constraints

- Nur PC-Oberfläche plus `Item.plot` in `shared`; TV und iPhone unverändert.
- Karte nur für Filme und Serien (nicht `Section.LIVE`, nicht Folgen mit `url`), Verweilzeit `HOVER_CARD_DELAY_MS = 450L`.
- Filme: `app.requestMediaInfo(item)` erst nach der Verweilzeit, nie beim bloßen Überfahren.
- Texte: „Beschreibung wird geladen …“ solange ein Film noch keine `MediaInfo` hat; ohne Beschreibung kein Absatz.
- Farben aus `Palette`, Stil wie der bestehende Hover (Signal-Rahmen). Bezeichner englisch, KDoc deutsch, Zeilen ≤ 160.
- detekt: neue Befunde beheben. Commits englisch, ohne Co-Authored-By. Arbeit im Worktree `.worktrees/desktop-hover-card` (Branch `feat/desktop-hover-card`).

## Review Focus

- **Bestehender Test „Stern bei Hover“** (`HomeScreenTest.starOnHoverRemembersMovieForFavoritesCategory`) muss grün bleiben: die Karte darf den Stern nicht unerreichbar machen → Karte trägt den Stern selbst mit, Test läuft unverändert (Task 3).
- **Schnelles Überfahren vieler Kacheln:** keine Detailabfragen → Test `quickPassDoesNotRequestInfo` (Task 3).
- **Karte am rechten Fensterrand:** bleibt im Fenster (klappt nach links) → Handtest (Task 3, Step 5), Layout ist nicht sinnvoll unit-testbar.
- **Maus verlässt Kachel und Karte:** Karte verschwindet → Test `cardClosesWhenMouseLeaves` (Task 3).
- **Serie ohne `plot` in der Liste:** kein leerer Absatz, keine Ladezeile (Serien laden nichts nach) → Test `seriesWithoutPlotShowsNoDescription` (Task 3).

---

### Task 1: `Item.plot` aus der Serienliste

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Models.kt` (Feld nach `trailer`, KDoc wie bei `backdrop`)
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/XtreamClient.kt:100-105`
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/data/XtreamClientTest.kt`

**Interfaces:**
- Produces: `Item.plot: String? = null` – nur Serien aus `get_series`, getrimmt, leer → null.

- [ ] **Step 1: Failing test** `seriesListCarriesPlot`: `get_series` mit `"plot":"  Berlin 1929. "` → `plot == "Berlin 1929."`; ohne `plot` bzw. `"plot":""` → `null`; ein Film aus `get_vod_streams` mit `"plot":"x"` → `plot == null` (Filme bewusst nicht).
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*XtreamClientTest' 2>&1 | tail -10` → FAIL.
- [ ] **Step 3:** Feld anlegen, im Serien-Zweig `plot = it.str("plot").trim().ifBlank { null }` setzen. `toJson`/`fromJson` bleiben unverändert (Downloads brauchen die Beschreibung nicht).
- [ ] **Step 4:** `./gradlew :shared:desktopTest detekt 2>&1 | tail -5` → grün.
- [ ] **Step 5:** Commit `Keep series plot from the provider list`.

### Task 2: Hand-Mauszeiger auf klickbaren Elementen

**Files:**
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/Components.kt` (Modifier + `PrimaryButton`/`SecondaryButton`/`MediaCard`/`FavoriteToggle`)
- Modify: übrige klickbare Stellen in `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/*.kt` (`.clickable`, `Card(onClick)`, `IconButton`, `TextButton`, `DropdownMenuItem`, Navigationseinträge)

**Interfaces:**
- Produces: `fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)` (in `Components.kt`, öffentlich im Paket).

- [ ] **Step 1:** Modifier anlegen und in `PrimaryButton`, `SecondaryButton`, `MediaCard` (Card), `FavoriteToggle` setzen.
- [ ] **Step 2:** Alle übrigen klickbaren Elemente der PC-Oberfläche (`grep -nE '\.clickable\(|onClick' desktopApp/src/main`) mit `.handCursor()` versehen; Textfelder und der Player-Hintergrund (eigener Zeiger, `HiddenCursor`) bleiben unberührt. Bestehendes `pointerHoverIcon(PointerIcon.Hand)` in `SeriesScreen.kt:160` durch `.handCursor()` ersetzen.
- [ ] **Step 3:** `./gradlew :desktopApp:test detekt 2>&1 | tail -5` → grün (rein visuell, bestehende Tests sichern ab, dass nichts kaputtging).
- [ ] **Step 4:** Commit `Show hand cursor on clickable elements in desktop app`.

### Task 3: Info-Karte an `MediaCard`

**Files:**
- Create: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HoverInfoCard.kt`
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/Components.kt` (`MediaCard`, ~Z. 353) und die zwei Aufrufer (`Components.kt` `ItemsRow`, `HomeScreen.kt` Raster ~Z. 685) – `MediaCard` bekommt `info: MediaInfo?` und `onDwell: () -> Unit`
- Modify: `testing/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/testing/SampleData.kt` (Serie „Babylon Berlin“ mit `"plot"`, `get_vod_info` für Dune mit `"plot"` – sonst bestehende Tests prüfen, dass nichts kippt)
- Test: `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HoverInfoCardTest.kt`

**Interfaces:**
- Consumes: `Item.plot` (Task 1), `Modifier.handCursor()` (Task 2), `app.requestMediaInfo(item)`, `app.mediaInfoFor(item)`, `posterFacts(item)`.
- Produces: `@Composable fun HoverInfoCard(item: Item, info: MediaInfo?, favorite: Boolean, onClick: () -> Unit, onToggleFavorite: (() -> Unit)?)`; in `MediaCard` State `showCard` (true nach `HOVER_CARD_DELAY_MS` Verweilen auf Kachel, false sobald weder Kachel noch Karte überfahren sind).

- [ ] **Step 1: Failing tests** (`createComposeRule`, `performMouseInput { moveTo(center) }`, `rule.mainClock.advanceTimeBy(…)`, Muster wie `HomeScreenTest`)

```kotlin
@Test fun cardShowsAfterDwellWithFullTitleAndPlot()   // Serie „Babylon Berlin“ überfahren, 300 ms → keine Karte; 500 ms → Text des plot sichtbar
@Test fun movieRequestsInfoOnlyAfterDwell()           // Dune überfahren, 300 ms → app.mediaInfoFor(dune) == null und keine Anfrage get_vod_info in FakeHttp; 500 ms → Anfrage gestellt, danach plot sichtbar
@Test fun quickPassDoesNotRequestInfo()               // nacheinander 5 Kacheln je 100 ms überfahren → keine get_vod_info-Anfrage
@Test fun cardClosesWhenMouseLeaves()                 // Karte offen, Maus auf leere Fläche → Karte weg
@Test fun seriesWithoutPlotShowsNoDescription()       // Serie ohne plot → Karte ohne „Beschreibung wird geladen …“
@Test fun clickOnCardOpensTitle()                     // Klick auf die Karte → wie Klick auf die Kachel (Detailseite offen)
```

- [ ] **Step 2:** `./gradlew :desktopApp:test --tests '*HoverInfoCardTest' 2>&1 | tail -10` → FAIL.
- [ ] **Step 3:** Implementieren: Verweilen per `LaunchedEffect(hovered) { if (hovered) { delay(HOVER_CARD_DELAY_MS); showCard = true; if (Film) onDwell() } }`; `Popup` mit eigener `PopupPositionProvider` (über der Kachel zentriert, horizontal so verschoben, dass sie im Fenster bleibt), Breite ≈ 2 × Kachel; Inhalt: Bild (`item.logo`), voller Titel (`splitProviderTag(item.name).name`, ohne maxLines), Fakten, Beschreibung (`item.plot ?: info?.plot`) mit `maxLines = 6`; Stern wie `FavoriteToggle`. Karte und Kachel teilen einen Hover-Zustand, damit das Wechseln der Maus von der Kachel auf die Karte sie nicht schließt.
- [ ] **Step 4:** `./gradlew :desktopApp:test :shared:desktopTest detekt 2>&1 | tail -5` → grün, inkl. unverändertem `starOnHoverRemembersMovieForFavoritesCategory`.
- [ ] **Step 5: Handtest** (`./gradlew :desktopApp:run`, Demo-Server): Karte erscheint nach kurzem Verweilen, rechte Spalte klappt nach links, Maus aus Kachel und Karte schließt, Hand auf allen Klickzielen.
- [ ] **Step 6:** Commit `Show hover info card on movie and series tiles in desktop app`.
