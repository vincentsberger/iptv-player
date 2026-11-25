# PC-App: Detail-Fenster und Angleichung an die TV-App – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Klick auf Film/Serie öffnet ein animiertes Detail-Fenster über der Startseite; die PC-App übernimmt sichtbar Gestaltung und Bewegung der TV-App.

**Architecture:** `Main.kt` trennt eine *Grund-Ebene* (Bildschirmwechsel, Movie/Series → Home) von einer *Detail-Ebene* darüber.
Navigation bleibt `app.open`/`app.back` (kein Eingriff in `AppState`/`shared`). Danach Paket für Paket Seitenleiste, Raster,
Bewegung, Live TV, Ladezustände, Startseite, Einstellungen – je ein Commit.

**Tech Stack:** Kotlin, Compose Multiplatform 1.9.3 (Desktop), Material 3, Coil 3, JUnit4-Compose-Tests (`createComposeRule`).

**Spec:** `docs/superpowers/specs/2026-11-24-desktop-detail-modal-design.md` (Entwurf vom 24.11., vor dem UI/UX-Review).
Seither umgesetzt: `docs/superpowers/specs/2026-11-25-desktop-ui-ux-review.md` (Pakete A–G, 2.3.0). Wo beide sich
widersprechen, gilt der Abschnitt **Abweichungen vom Entwurf** unten.

Abkürzungen: `D/` = `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/`, `DT/` = `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/`,
`T/` = `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/` (TV-Vorlagen zum Abschauen, kein gemeinsames UI-Modul).
Zeilenangaben: Stand `c9f30b0` (2.3.0).

## Global Constraints

- Farben nur über die Tokens in `D/ui/Components.kt` (`Bg`, `Surface1/2`, `Line`, `TextColor`, `Muted`, `Signal`, `FocusRing`, `Danger`) – keine neuen Hex-Werte.
- Style-Lock (`.tastemaker/style-lock.md`): Ecken 4 dp (`Corner`), klein 2 dp (`SmallCorner`); **keine Kapseln, keine Verläufe, keine Schatten** – feine Linien.
- Bernstein (`Signal`) nur für Auswahl, „Live“, Hauptaktion, Fortschritt. Hover/Fokus = warmweiß (`FocusRing`).
- Bewegung: Alltägliches 120–220 ms, `FastOutSlowInEasing`, keine Federn/Überschwinger; nichts über 320 ms.
- Detail-Fenster: Öffnen 240 ms, Schließen 180 ms, Skalierung 0,94 → 1, Grund dunkelt per Alpha (flache Farbe).
- Keine Änderung an `shared/` (AppState-API nutzen drei Oberflächen).
- Bezeichner englisch, KDoc/Kommentare deutsch, Zeilen ≤ 160. `./gradlew detekt` ohne neue Befunde (nicht in die Baseline).
- Commits englisch, **ohne Co-Authored-By**, ein Commit je Task, Branch `feat/desktop-detail-modal`.
- Testlauf je Task: `./gradlew :desktopApp:test --tests '<Klasse>' > $SCRATCH/t.log 2>&1; tail -30 $SCRATCH/t.log`.

## Abweichungen vom Entwurf (durch das Review neuer entschieden – bitte bestätigen)

- **P1.3 Strg-Kappen:** bleiben wie in 2.3.0 (nur bei gehaltener Strg-Taste, Review K1) statt beim Überfahren.
- **P2.2 Hover:** warmweißer Ring 3 dp **als Überlagerung** (zeichnet außen, ändert das Layout nicht) + Skalierung 1,08 über
  `graphicsLayer` – erfüllt den Entwurf (TV-Fokus) und Review M9 (Inhalt springt nicht). Ruhezustand: 1 dp `Line`.
- **P6.1 Einstellungen:** Gliederung aus Review M6 (Anbieter/Wiedergabe/Startseite/Listen & Filter/App + Inhaltsverzeichnis)
  bleibt; nur Dashboard-Kategorien, Länderfilter und Updates bekommen je eine eigene Teilfläche (Zwischenüberschrift + Linie).
- **P6.2 An/Aus:** Kontrollkästchen aus Review K8 bleiben (PC-üblich, keine Kapsel) – keine An/Aus-Knöpfe.
- **P3.3 Hinweistexte:** schon erledigt (2.3.0), entfällt.

## Review Focus

- **Zurück aus dem Player** (Film aus dem Fenster abgespielt, Esc/Zurück im Player) → Startseite mit offenem Fenster, ohne erneute Einblend-Animation der Startseite. Test: Task 2 `backFromPlayerShowsTheModalAgain`.
- **Strg+1–7 / Strg+F bei offenem Fenster** → Fenster schließt, Bereich wechselt (nicht: Bereich wechselt unsichtbar unter dem Fenster). Test: Task 2 `ctrlDigitOnDetailClosesAndSelectsTab`.
- **Tastatur im Fenster:** Beim Öffnen liegt der Fokus auf der Hauptaktion (Enter spielt ab); Tab läuft nicht in die verdeckte Startseite. Test: Task 2 `primaryActionIsFocusedOnOpen`.
- **Mausrad über dem Fenster/Grund** scrollt nur das Fenster, nie das Raster darunter; Scrollposition des Rasters bleibt nach dem Schließen. Test: Task 2 `gridKeepsScrollPositionBehindModal`.
- **Schnelles Zappen in Live TV:** Pfeil ↓ mehrfach schnell → Infospalte blendet je Wechsel höchstens einmal über, zeigt am Ende Sender + EPG des letzten Senders. Test: Task 6 `infoColumnCrossfadesOncePerChannel`.

---

### Task 1: Ebenen-Logik und Kachel-Anker (reine Funktionen)

**Files:**
- Create: `D/ui/DetailAnchor.kt`
- Test: `DT/ui/DetailAnchorTest.kt`

**Interfaces:**
- Produces:
  - `class DetailAnchor { var tileCenter: Offset? }` (Compose-State, Fensterkoordinaten in px) und `val LocalDetailAnchor: ProvidableCompositionLocal<DetailAnchor>`
  - `internal fun baseScreen(screen: Screen): Screen` – `Movie`/`Series` → `Screen.Home`, sonst unverändert
  - `internal fun detailItemOf(screen: Screen): Item?` – Film bzw. Serie, sonst null
  - `internal fun modalTransformOrigin(tileCenter: Offset?, modal: Rect): TransformOrigin` – Bruchteile relativ zu `modal`, auf 0..1 begrenzt; null → `TransformOrigin.Center`

- [ ] **Step 1: Failing Tests schreiben** – `DetailAnchorTest`:
  - `originIsCenterWithoutTile`: `modalTransformOrigin(null, Rect(100f, 50f, 1060f, 850f)) == TransformOrigin.Center`
  - `originFollowsTileRelativeToModal`: `Offset(340f, 450f)` in obigem Rect → `TransformOrigin(0.25f, 0.5f)`
  - `originOutsideModalIsClamped`: `Offset(0f, 0f)` → `TransformOrigin(0f, 0f)`; `Offset(2000f, 2000f)` → `TransformOrigin(1f, 1f)`
  - `detailScreensKeepHomeAsBase`: `baseScreen(Screen.Movie(m)) == Screen.Home`, `baseScreen(Screen.Series(s)) == Screen.Home`, `baseScreen(Screen.Player(listOf(m), 0)) is Screen.Player`
  - `detailItemOnlyForMovieAndSeries`: `detailItemOf(Screen.Movie(m)) == m`, `detailItemOf(Screen.Home) == null`
- [ ] **Step 2: Laufen lassen** – `--tests '*DetailAnchorTest'` → FAIL (unresolved reference)
- [ ] **Step 3: `DetailAnchor.kt` implementieren** (Signaturen oben; KDoc deutsch)
- [ ] **Step 4: Tests grün**
- [ ] **Step 5: Commit** `Add layer and anchor logic for the desktop detail modal`

### Task 2: Detail-Fenster (Film + Serie) und Ebenen in Main

**Files:**
- Create: `D/ui/DetailModal.kt` (Ebene, Rahmen, `MovieDetail`, `SeriesDetail`, übernommene `Credit`, `EpisodeRow`)
- Create: `D/ScreenLayers.kt` (aus `Main.kt` herausgelöst, damit testbar)
- Modify: `D/Main.kt:113-145` (Box + `AnimatedContent` → `ScreenLayers`), `D/ui/GlobalKeys.kt:30-38`
- Delete: `D/ui/MovieScreen.kt`, `D/ui/SeriesScreen.kt`
- Test: Create `DT/ui/DetailModalTest.kt`; Move `DT/ui/SeriesScreenTest.kt` → `DT/ui/SeriesDetailTest.kt`; Modify `DT/ui/HomeScreenTest.kt:304-311` (`playOnTheMovieDetailsStartsIt` → in `DetailModalTest`), `DT/ui/GlobalKeysTest.kt`

**Interfaces:**
- Consumes: Task 1 (`baseScreen`, `detailItemOf`, `modalTransformOrigin`, `LocalDetailAnchor`)
- Produces:
  - `@Composable fun ScreenLayers(app: AppState, player: @Composable (Screen.Player) -> Unit)` – stellt `LocalDetailAnchor` bereit, Grund-Ebene `AnimatedContent(baseScreen(app.screen))` mit `screenTransition()`, darüber `DetailLayer(app)`
  - `@Composable fun DetailLayer(app: AppState)` – Grund-Abdunkelung (`Bg.copy(alpha = 0.78f)`, Test-Tag `detail-scrim`, Klick → `app.back()`), Fenster (Test-Tag `detail-modal`) mit Breite `min(960.dp, 90 %)`, Höhe max. 90 %, `Surface1`, 1 dp `Line`, `Corner`
  - Test-Tags: `detail-close` (✕, contentDescription „Schließen“)

- [ ] **Step 1: Failing Tests schreiben** – `DetailModalTest` (rendert `ScreenLayers(app) { Text("player") }` mit `testAppState()`):
  - `clickingAMovieOpensTheModalOverHome`: Filme-Tab, Klick „Dune“ → `app.screen is Screen.Movie`, `detail-modal` existiert, Seitenleiste („Filme“) existiert weiter
  - `playOnTheMovieDetailsStartsIt` (aus HomeScreenTest übernommen): „Abspielen“ im Fenster → `Screen.Player`, Titel „Dune“
  - `closeButtonScrimAndBackClose`: je ein Durchlauf ✕ / Klick auf `detail-scrim` (Ecke, nicht Fenster) → `app.screen == Screen.Home`; `detail-modal` nach `mainClock.advanceTimeBy(300)` weg
  - `backFromPlayerShowsTheModalAgain`: Film öffnen, „Abspielen“, `app.back()` → `detail-modal` und „Dune“ sichtbar, `app.screen is Screen.Movie`
  - `primaryActionIsFocusedOnOpen`: nach Öffnen `onNode(hasText("Abspielen") and hasClickAction()).assertIsFocused()`
  - `gridKeepsScrollPositionBehindModal`: Filme-Raster per `performScrollToIndex` nach unten, Film öffnen, ✕ → erster sichtbarer Index unverändert (Raster-Tag aus `BrowseScreen`, ggf. `browse-grid` ergänzen)
  - `SeriesDetailTest`: bisherige Tests auf `DetailLayer`/`ScreenLayers` umstellen, Aussagen unverändert („2 Staffeln · 3 Folgen“, Staffelwechsel, Weiterschauen); neu `episodesScrollWithTheModal`: Folgen und Titel liegen in **einem** Scrollbereich (`detail-modal` hat `hasScrollAction()`, keine eigene Folgen-Liste mit Scroll)
  - `GlobalKeysTest.ctrlDigitOnDetailClosesAndSelectsTab`: `globalActionFor(Screen.Movie(m), Key.Two, ctrl = true, …) == GlobalAction.SelectTab(desktopTabs[1])`, ebenso `Key.F` → `FocusSearch`
- [ ] **Step 2: Laufen lassen** → FAIL
- [ ] **Step 3: `ScreenLayers` + `DetailLayer` implementieren**
  - Bewegung: `AnimatedVisibility(detailItemOf(app.screen) != null)`; letztes Item merken (`remember`), damit beim Schließen Inhalt stehen bleibt. Abdunkelung `fadeIn/Out`; Fenster `fadeIn(240)`/`fadeOut(180)` plus Skalierung über `transition.animateFloat` (0,94 ↔ 1) in `Modifier.graphicsLayer { scaleX; scaleY; transformOrigin = modalTransformOrigin(anchor.tileCenter, modalBounds) }` – `modalBounds` aus `onGloballyPositioned { it.boundsInWindow() }`, im `graphicsLayer`-Block gelesen (Zeichenphase, keine Neukomposition).
  - Wechsel Film→Film (selten) per `AnimatedContent` auf das Item innerhalb des Fensters, 180 ms Überblendung.
  - Inhalt Film/Serie wie im Entwurf „Inhalt Film/Serie“ und Skizze: Breitbild oben (Film `info?.backdrop ?: movie.logo`, Serie `series.backdrop ?: series.logo`, Höhe 300 dp, `ContentScale.Crop`, ohne Bild: `Surface2`-Fläche 120 dp), Kicker in `Muted`, ein `verticalScroll` für alles, ✕ oben rechts außerhalb des Scrollbereichs. Staffel-Chips wie bisher (`FilterChip`, `Corner`), Folgen als `Column` (nicht `LazyColumn`).
  - Fokus: `FocusRequester` auf der Hauptaktion, `LaunchedEffect(item) { requestFocus() }`; Fenster-Box mit `Modifier.focusGroup()` + `focusProperties { exit = { FocusRequester.Cancel } }`, damit Tab im Fenster bleibt.
  - Grund-Abdunkelung verbraucht Mausrad (`Modifier.onPointerEvent(PointerEventType.Scroll) { it.changes.forEach { c -> c.consume() } }`).
- [ ] **Step 4: `GlobalKeys` erweitern** – `Movie`/`Series`: zuerst `isBackKey` → `Back`, sonst Strg-Zweig wie `Home`. In `Main.handleWindowKey` vor `SelectTab`/`FocusSearch`: `if (detailItemOf(app.screen) != null) app.back()`.
- [ ] **Step 5: `Main.kt` umbauen** – `ScreenLayers(app) { s -> PlayerScreen(…) }`; Pointer-Handler (Maus-Zurück) bleibt auf der äußeren Box. `MovieScreen.kt`, `SeriesScreen.kt` löschen, Importe bereinigen.
- [ ] **Step 6: Alle PC-Tests grün** – `./gradlew :desktopApp:test`
- [ ] **Step 7: Commit** `Open movies and series in a modal over the home screen`

### Task 3: Hover-Karte zurückbauen, Kachel setzt den Anker

**Files:**
- Delete: `D/ui/HoverInfoCard.kt`, `DT/ui/HoverInfoCardTest.kt`
- Modify: `D/ui/Components.kt:405-482` (`MediaCard`), `:512-555` (`ItemsRow`), `D/ui/HomeScreen.kt:772, 800, 819, 950` (Aufrufer)
- Test: `DT/ui/DetailModalTest.kt`

**Interfaces:**
- Consumes: `LocalDetailAnchor` (Task 1)
- Produces: `MediaCard(item, favorite, progress, onClick, onToggleFavorite, modifier)` – Parameter `info`, `onDwell`, `infoLoaded` entfallen; `ItemsRow` ohne `infoOf`, `onDwell`, `infoLoadedOf`

- [ ] **Step 1: Failing Tests** – `DetailModalTest`:
  - `clickSetsTheAnchorToTheTileCenter`: Klick auf Kachel „Dune“ → `anchor.tileCenter` ≈ Mittelpunkt von `onNodeWithTag("media-card:Dune").getBoundsInRoot()` (±1 px; Test stellt `LocalDetailAnchor` selbst bereit)
  - `hoverShowsNoFloatingCard`: Maus 1 s auf „Dune“ (`performMouseInput { moveTo(center) }`, `advanceTimeBy(1000)`) → kein Popup: `onAllNodesWithText("Beschreibung wird geladen …").assertCountEquals(0)` und `onAllNodesWithText("Dune")` Anzahl unverändert
- [ ] **Step 2: Laufen lassen** → FAIL
- [ ] **Step 3: Umsetzen** – `MediaCard`: Hover-Karten-Zweig, `showCard`, `cardInteraction` raus; Stern erscheint bei `hovered`. `Modifier.onGloballyPositioned { coords = it }` auf der Karte, im `onClick` erst `anchor.tileCenter = coords.boundsInWindow().center`, dann `onClick()`. Test-Tag `media-card:<name>`.
- [ ] **Step 4: Tests grün** (`:desktopApp:test`)
- [ ] **Step 5: Commit** `Drop the hover info card in favour of the detail modal`

### Task 4: Paket 1 – Seitenleiste

**Files:** Modify `D/ui/HomeScreen.kt:231-290` (`Sidebar`, `SidebarItem`), `D/ui/Components.kt:271-276` (`SelectionMark`). Test: `DT/ui/KeyboardUiTest.kt` oder neu `DT/ui/SidebarTest.kt`.

**Interfaces:**
- Produces: `SelectionMark(visible: Boolean, modifier: Modifier = Modifier, height: Dp = 22.dp)`; `internal fun clockText(now: LocalTime): String` (`"HH:mm"`)

- [ ] **Step 1: Failing Tests** – `SidebarTest`:
  - `headerShowsLogoNameAndClock`: breit (1440 dp) → `onNodeWithContentDescription("IPTV Player")` (LogoMark) und Text „IPTV Player“ und ein Text mit Muster `\d\d:\d\d`
  - `compactHeaderShowsOnlyTheLogo`: `LocalCompact provides true` → Text „IPTV Player“ nicht vorhanden, LogoMark vorhanden
  - `clockFormatsHoursAndMinutes`: `clockText(LocalTime.of(7, 5)) == "07:05"`
- [ ] **Step 2: FAIL bestätigen**
- [ ] **Step 3: Umsetzen** – Kopf nach `T/HomeScreen.kt:167-175` (Uhr minütlich per `LaunchedEffect` + `delay` bis zur nächsten Minute); Symbol des gewählten Bereichs `Signal`; Hintergrund per `animateColorAsState(tween(160))`; harter `drawBehind`-Strich → `SelectionMark(selected, Modifier.align(CenterStart))` (wächst animiert, Vorlage `T/Components.kt:693`).
- [ ] **Step 4: Tests grün**
- [ ] **Step 5: Commit** `Sidebar header with logo and clock, amber icon for the selected area`

### Task 5: Paket 2 – Raster Filme/Serien

**Files:** Modify `D/ui/Components.kt:405-505` (`MediaCard`, `cardSize`, `cardColumns`), `D/ui/HomeScreen.kt:762-773` (`BrowseScreen`). Test: `DT/ui/RowsGridTest.kt`.

**Interfaces:**
- Produces: `MediaCard(…, fillCell: Boolean = false)`; `fun cardColumns(wide: Boolean): GridCells` → `GridCells.Adaptive(150.dp)` (Poster) / `Adaptive(210.dp)` (breit); `internal const val HOVER_SCALE = 1.08f`

- [ ] **Step 1: Failing Tests** – `RowsGridTest`:
  - `gridColumnsMatchTheCardWidth` ersetzen durch `gridCardsFillTheirCell`: bei 1440×900 haben alle `media-card:*` im Filme-Raster dieselbe Breite, Abstand benachbarter Karten = 20 dp ±1, Höhe = Breite × 1,5 ±1
  - `yearAndRatingSitOnTheCover`: Film mit Jahr → Fakten-Text (`posterFacts`) liegt innerhalb der Bounds von `media-card:<name>`
- [ ] **Step 2: FAIL bestätigen**
- [ ] **Step 3: Umsetzen** – `fillCell`: `fillMaxWidth().aspectRatio(2f / 3f)` (breit `16f / 9f`), Vorlage `T/Components.kt:533-540`. Hover: Ruhe 1 dp `Line`; Hover zusätzlich Ring 3 dp `FocusRing` per `drawWithContent` außerhalb der Karte (Alpha animiert 160 ms) + `graphicsLayer { scaleX = scaleY = animateFloatAsState(if (hovered) HOVER_SCALE else 1f, tween(180)) }` – Layout ändert sich nicht. Etikett Jahr/Bewertung auf dem Cover unten links, 13 sp weiß auf `Bg.copy(alpha = 0.8f)`, `SmallCorner` (Vorlage `T/Components.kt:558-565`; Farbe aus Token statt `0xCC000000`). Mono-Zeile darunter entfällt.
- [ ] **Step 4: Tests grün** (`:desktopApp:test`, Reihen/Suche nutzen `MediaCard` ohne `fillCell` weiter)
- [ ] **Step 5: Commit** `Grid cards fill their cell, warm-white hover ring with scale, facts on the cover`

### Task 6: Paket 7 + 3 – Bewegung und Live TV

**Files:** Modify `D/ui/HomeScreen.kt:338-370` (`NavRow`), `:380-660` (`LiveScreen`, `ChannelRow`, `LiveInfo`), `:473, :744` (Kategorie-Überschrift), `D/ui/Components.kt:386-400` (`FavoriteToggle`), `:643-656` (`screenTransition`, `tabTransition`). Test: `DT/ui/LiveListTest.kt`.

**Interfaces:**
- Produces: `internal const val LIVE_PREVIEW_DELAY_MS = 250L`; `LiveInfo` überblendet nur auf `item?.favKey` (EPG wechselt im selben Inhalt ohne Überblendung); Test-Tags `live-info`, `channel-card:<name>`

- [ ] **Step 1: Failing Tests** – `LiveListTest`:
  - `infoColumnCrossfadesOncePerChannel`: ↓ drücken, `mainClock.autoAdvance = false`, in 20-ms-Schritten bis 600 ms vorspulen; zu keinem Zeitpunkt nach Ende der ersten Überblendung (> 220 ms) existieren zwei `live-info`-Kinder (`onAllNodesWithTag("live-info").fetchSemanticsNodes().size <= 1`), am Ende EPG-Titel des neuen Senders sichtbar
  - `channelsAreSeparateCards`: zwei benachbarte `channel-card:*` haben 6 dp ±1 Abstand, Höhe 64 dp ±1
  - `hoverPreviewWaitsForTheDwell`: Maus auf „ZDF“, nach 200 ms zeigt die Info noch den alten Sender, nach 300 ms „ZDF“ (ersetzt/ergänzt `hoverPreviewsWithoutSelecting`)
- [ ] **Step 2: FAIL bestätigen**
- [ ] **Step 3: Live TV umsetzen** – Senderliste: `LazyColumn(verticalArrangement = spacedBy(6.dp))`, je Karte `Surface1`/`Corner`/64 dp, Fortschritt als 3-dp-Balken über die ganze Breite unten (Vorlage `T/LiveTvScreen.kt:270-310`); äußeres `panel()` entfällt, Liste wächst nicht bis zum Boden. Kategorien 15 sp, `lineHeight` 18 sp, 11 dp oben/unten (Vorlage `T/LiveTvScreen.kt:246-248`); Trennlinie und Kartenspalte gleich breit.
- [ ] **Step 4: Bewegung umsetzen** – `animateColorAsState(tween(160))` für Hintergründe/Farben in `NavRow`, `ChannelRow`, Sidebar, `MediaCard`-Titel; Augen-Symbol in `NavRow`, Abspielen-/Stern-Knopf in `ChannelRow`, `FavoriteToggle` per `AnimatedVisibility(fadeIn(120)/fadeOut(120))`; Kategorie-Überschrift per `AnimatedContent` (Überblendung 180 ms). `tabTransition`: `fadeIn(tween(220, delayMillis = 40))` / `fadeOut(tween(120))`; `screenTransition`: Einblenden mit `delayMillis = 60` (Vorlage `T/Components.kt:683-689`).
- [ ] **Step 5: Tests grün** (`:desktopApp:test`)
- [ ] **Step 6: Commit** `Live TV channel cards, single crossfade per channel, animated state changes`

### Task 7: Paket 4 – Ladezustände

**Files:** Create `D/ui/Skeletons.kt`; Modify `D/ui/HomeScreen.kt:382-384` (Live), `:723-725` (Browse), `:978-996` (Suche). Test: `DT/ui/HomeScreenTest.kt`.

**Interfaces:**
- Produces: `@Composable fun LiveSkeleton()`, `@Composable fun GridSkeleton(wide: Boolean = false)`, `@Composable fun Spinner(size: Dp = 32.dp)`, `fun Modifier.shimmer(): Modifier` (Puls `Surface1`↔`Surface2`, 1200 ms, linear – kein Verlauf); Test-Tags `live-skeleton`, `grid-skeleton`

- [ ] **Step 1: Failing Tests** – `HomeScreenTest`:
  - `liveShowsASkeletonWhileLoading`, `moviesShowAGridSkeletonWhileLoading`: `testAppState()` mit Bereich im Ladezustand (FakeHttp verzögert / Abschnitt `loading`) → Tag existiert, kein `LogoLoader`
  - `searchLoadsWithASpinner`: Suche mit ladenden Listen → `Spinner` (Tag `spinner`), kein „Listen werden geladen …“-Logo
- [ ] **Step 2: FAIL bestätigen**
- [ ] **Step 3: Umsetzen** – Vorlagen `T/LiveTvScreen.kt:373-388`, `T/BrowseScreens.kt:216-225`, `T/Components.kt:423, 430`; Laden → Inhalt per `Crossfade(tween(200))`.
- [ ] **Step 4: Tests grün**
- [ ] **Step 5: Commit** `Layout-shaped loading skeletons and a plain search spinner`

### Task 8: Paket 5 – Startseite

**Files:** Modify `D/ui/HomeScreen.kt:870-958` (`StartScreen`, `StartScreenContent`), `:789-805` (`SearchSection`), `D/ui/Components.kt:512-555` (`ItemsRow`). Test: `DT/ui/HomeScreenTest.kt`, `DT/ui/RowsGridTest.kt`.

- [ ] **Step 1: Failing Tests**:
  - `startHeaderScrollsWithTheRows`: Startseite, `onNodeWithTag("start-list").performScrollToIndex(3)` → Gruß-Text nicht mehr angezeigt (`assertIsNotDisplayed`)
  - `updateHintIsAPanelWithPrimaryAction`: App mit verfügbarem Update → Knopf „Jetzt aktualisieren“ (o. vorhandener Text) ist `PrimaryButton` (Tag `update-hint`), Symbol vorhanden
  - `rowHasNoDividerAndHidesScrollbarUntilHover`: in `RowsGridTest` – Reihe ohne Hover hat keinen `HorizontalScrollbar`-Knoten (Tag `row-scrollbar`), nach Hover schon
- [ ] **Step 2: FAIL bestätigen**
- [ ] **Step 3: Umsetzen** – Kopf, Abo-Zeile, Update-Hinweis, `ChooseCategoriesCard` als `item {}` in die `LazyColumn` (Tag `start-list`); Update-Hinweis als `panel()`-Zeile mit `SystemUpdate`-Symbol 26 dp in `Signal` + `PrimaryButton` (Vorlage `T/StartScreen.kt:112-122`), Update-Hinweis und Kategorien-Karte per `AnimatedVisibility`; `ItemsRow`/`SearchSection`: `Rule` oben weg, Titel 20 sp, Scrollbalken nur bei Hover (`AnimatedVisibility(rowHovered)`), senkrechter Balken der Startseite ebenso.
- [ ] **Step 4: Tests grün**
- [ ] **Step 5: Commit** `Start screen header scrolls with the rows, update hint as panel, quieter rows`

### Task 9: Paket 6 + Kleinkram – Einstellungen, Favoriten, Ladebildschirm

**Files:** Modify `D/ui/HomeScreen.kt:1014-1305` (Einstellungen), `:807-827` (`FavoritesScreen`), `:176-186` (`Tab.icon`), `D/ui/Components.kt:668-690` (`PlaylistLoadingScreen`). Test: `DT/ui/DashboardSettingsTest.kt`, `DT/ui/DesktopTextsTest.kt`.

- [ ] **Step 1: Failing Tests**:
  - `settingsShowSubheadingsForDashboardCountryAndUpdates`: Zwischenüberschriften „Dashboard-Kategorien“, „Länderfilter“, „Updates“ existieren je einmal
  - `showAllExpandsAnimated`: „Alle anzeigen“ → weitere Chips sichtbar nach `advanceTimeBy(300)` (sichert `animateContentSize`, Inhalt unverändert)
  - `favoritesUseOneStarIconAndOneText`: Favoriten leer → genau ein Erklärtext (der bisherige Leertext), Kopf- und Leer-Symbol beide `contentDescription`/Tag `star`
  - `loadingScreenShowsBarAboveA96dpLogo`: `PlaylistLoadingScreen` → Bar-Knoten liegt oberhalb des Logos, Logo 96 dp ±1
- [ ] **Step 2: FAIL bestätigen**
- [ ] **Step 3: Umsetzen** – Teilflächen (Zwischenüberschrift `Kicker` + `Rule`) innerhalb der bestehenden `SettingsPart`s; Chip-Hintergründe `animateColorAsState`; FlowRow `animateContentSize(tween(200))`; Bestätigungsdialog „Anbieter entfernen“ per `AnimatedVisibility`-Einblendung (oder `fadeIn` des Dialoginhalts, 160 ms). Favoriten: Untertitel entfällt, Stern (`Icons.Rounded.Star`) für Seitenleiste, Kopf und Leerzustand. Ladebildschirm: Logo 96 dp, `IndeterminateBar` darüber mit 14 dp Abstand (Vorlage `T/Components.kt:744-748`), Texte per `AnimatedContent`.
- [ ] **Step 4: Tests grün**
- [ ] **Step 5: Commit** `Settings subsections, one favourites icon, loading screen like TV`

### Task 10: Abschluss – Sichtprüfung, Analyse, Doku

- [ ] **Step 1:** `ScreenshotDump` um Detail-Fenster (Film, Serie; breit + schmal) erweitern (rendert `ScreenLayers`), laufen lassen: `./gradlew :desktopApp:test --tests '*ScreenshotDump*'`, PNGs in `$TMPDIR/shots/` mit 2.3.0-Stand vergleichen (vorher einmal auf `master` erzeugen).
- [ ] **Step 2:** `./gradlew :shared:desktopTest :desktopApp:test detekt > $SCRATCH/all.log 2>&1; tail -40 $SCRATCH/all.log` → BUILD SUCCESSFUL, keine neuen detekt-Befunde.
- [ ] **Step 3:** App lokal starten (`./gradlew :desktopApp:run`, Demo-Server `tools/demo-server`, Login `demo`/`demo`) und von Hand: Film öffnen/schließen (✕, Esc, Grund, Maus-Zurück), abspielen + Zurück, Strg+3 bei offenem Fenster, Live-TV-Zappen mit ↓.
- [ ] **Step 4:** Spec-Kopf ergänzen (`**Umgesetzt:** …, Abweichungen siehe Plan`), Commit `Mark desktop detail modal design as implemented`.
