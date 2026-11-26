# Einfacher Ladebildschirm Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Ladebildschirm von PC- und TV-App zeigt nur noch das animierte Logo mittig und darunter drei Zeilen (Icon, Bereichsname, rechts Spinner → Haken bzw. ✕).

**Architecture:** Umbau der vorhandenen `PlaylistLoadingScreen`, `LoadingStep` und `LoadCheck` je App. Keine Änderung an `shared` (`loadingHeadline` bleibt, iPhone nutzt es).

**Tech Stack:** Compose Desktop (Material 3), androidx Compose + tv-material, Compose-UI-Tests.

**Spec:** `docs/superpowers/specs/2026-11-26-simple-loading-screen-design.md`

## Global Constraints

- iPhone (`iosUi/…/LoadingScreen.kt`) und `shared` bleiben unverändert.
- Farben nur aus der Palette (`Signal` Spinner/Haken, `Danger` Fehler, `Muted` Icons).
- Zustands-Semantik von `LoadCheck` bleibt: `contentDescription` „wartet“ / „lädt“ / „geladen“ / „Fehler“; das Logo behält „Wird geladen“.
- Weg: Ladebalken, „IPTV Player“, Überschrift, Kontoname, Hinweistext, Status-Texte/Anzahlen in den Zeilen, Kästchen-Rahmen.
- KDoc deutsch, Commits englisch ohne Co-Authored-By.

## Review Focus

- Fehler in einem Bereich: rotes ✕ statt Spinner – sonst sieht ein Fehler aus wie endloses Laden (Test `failedSectionShowsErrorBox` bleibt).
- „Wartet“ und „lädt“ sehen gleich aus (Spinner), unterscheiden sich aber weiter in der Semantik (Tests zählen beide).
- Kleine Fenster (PC < 1280 dp, `LocalCompact`): Logo + Zeilen müssen ohne Abschneiden passen – feste Breite ~320 dp, mittig.
- Überblenden Spinner → Haken darf keine Doppelanzeige in der Semantik hinterlassen (nach Abschluss der Animation genau 1 × „geladen“).
- Unbenutzt gewordene Helfer (`IndeterminateBar`, TV-`LoadStatus`) entfernen, sonst meldet detekt sie.

---

### Task 1: PC-Ladebildschirm

**Files:**
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/Components.kt` (`PlaylistLoadingScreen` ~Z. 712, `LoadingStep` ~Z. 773, `IndeterminateBar` ~Z. 704 entfernen, Import `loadingHeadline` entfernen)
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/Logo.kt` (`LoadCheck` ~Z. 127)
- Test: `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/LogoTest.kt`, `…/ui/SettingsFavoritesLoadingTest.kt`

**Interfaces:**
- Produces: `PlaylistLoadingScreen(app: AppState)` (Signatur unverändert), `LoadCheck(step: LoadStep)` (Signatur unverändert, 22 dp, ohne Rahmen).

- [ ] **Step 1: Tests anpassen (sollen fehlschlagen)**

`LogoTest.loadingScreenShowsHeadlineOfFirstOpenSection` → umbenennen in `loadingScreenShowsStateOfEachSection`: Zeile `onNodeWithText("Filme werden gesucht")` ersetzen durch
```kotlin
rule.onAllNodesWithText("Filme werden gesucht").assertCountEquals(0)
rule.onAllNodesWithText("lädt …").assertCountEquals(0)
```
(Zählungen „geladen“/„lädt“/„wartet“ je 1 bleiben.)

`SettingsFavoritesLoadingTest.loadingScreenShowsBarAboveA96dpLogo` → ersetzen durch `loadingScreenShowsCenteredLogoAboveRows`:
```kotlin
rule.mainClock.autoAdvance = false
rule.setContent { AppTheme { PlaylistLoadingScreen(testAppState()) } }
rule.mainClock.advanceTimeBy(100)
rule.onAllNodesWithTag("loading-bar").assertCountEquals(0)
rule.onAllNodesWithText("IPTV Player").assertCountEquals(0)
val logo = rule.onNodeWithTag("loading-logo").fetchSemanticsNode().boundsInRoot
val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
assertEquals(root.center.x, logo.center.x, 1f)
assertEquals(120f, logo.width, 1f)
val live = rule.onNodeWithText("Sender").fetchSemanticsNode().boundsInRoot
assertTrue(logo.bottom <= live.top, "Logo $logo über den Zeilen $live")
```

- [ ] **Step 2: Tests laufen lassen – müssen fehlschlagen**

Run: `./gradlew :desktopApp:test --tests '*LogoTest*' --tests '*SettingsFavoritesLoadingTest*'`
Expected: FAIL (Überschrift/Balken noch vorhanden, Logo 96 dp)

- [ ] **Step 3: Umbau**

- `PlaylistLoadingScreen`: `Box(fillMaxSize, Center)` → `Column(Modifier.width(320.dp), horizontalAlignment = CenterHorizontally)`: `LogoLoader(120.dp)` mit `testTag("loading-logo")`, `Spacer(40.dp)`, dann die drei `LoadingStep`. Ermittlung von `active`/`loadStep` wie bisher.
- `LoadingStep(section, step)`: Parameter `count` entfällt; Zeile = Icon (20 dp, `Muted`), Name (`weight(1f)`, padding start 12 dp), `LoadCheck(step)`.
- `LoadCheck(step)`: 22-dp-Box ohne Rahmen/Hintergrund; `Crossfade(step, tween(200))`: WAITING/LOADING → `CircularProgressIndicator(Modifier.size(18.dp), color = Signal, trackColor = Line, strokeWidth = 2.dp)`; DONE → `Icon(Icons.Rounded.Check, tint = Signal, size 22 dp)`; FAILED → `Icon(Icons.Rounded.Close, tint = Danger, 20 dp)`. `contentDescription` außen an der Box (eine Semantik je Zeile). KDoc anpassen.
- `IndeterminateBar` und den `loadingHeadline`-Import löschen.

- [ ] **Step 4: Tests + detekt**

Run: `./gradlew :desktopApp:test detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add desktopApp/src
git commit -m "Simplify the desktop playlist loading screen to logo and section checks"
```

### Task 2: TV-Ladebildschirm

**Files:**
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/Components.kt` (`PlaylistLoadingScreen` ~Z. 737, `LoadingStep` ~Z. 760, `IndeterminateBar` ~Z. 700 entfernen, Import `loadingHeadline` entfernen)
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/Logo.kt` (`LoadCheck` ~Z. 130, `LoadStatus` ~Z. 148 entfernen)
- Test: `androidApp/src/androidTest/java/de/vincentsberger/iptvplayer/app/LoadingScreenTest.kt`

**Interfaces:**
- Consumes: Gestaltung aus Task 1 (gleiche Maße, gleiche Zustände).
- Produces: `PlaylistLoadingScreen(vm: AppState)` (unverändert), `LoadCheck(step: LoadStep)` (22 dp, ohne Rahmen).

- [ ] **Step 1: Test anpassen**

`loadingScreenChecksFinishedSections`: `onNodeWithText("Filme werden gesucht").assertExists()` ersetzen durch
```kotlin
rule.onAllNodesWithText("Filme werden gesucht").assertCountEquals(0)
rule.onAllNodesWithText("IPTV Player").assertCountEquals(0)
```
Zählungen bleiben (je 1 × geladen/lädt/wartet/„Wird geladen“).

- [ ] **Step 2: Umbau** wie Task 1 Step 3, mit TV-Bausteinen: `LogoLoader(120.dp)`, `Spinner(18.dp)`-Äquivalent über `CircularProgressIndicator` bzw. vorhandenen `Spinner`, `Icon(…, tint, size)`-Signatur der TV-App; Schriftgröße Name 18 sp (TV-Leseabstand). `LoadStatus` und `IndeterminateBar` löschen.

- [ ] **Step 3: Kompilieren + detekt**

Run: `./gradlew :androidApp:compileDebugKotlin :androidApp:compileDebugAndroidTestKotlin detekt`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add androidApp/src
git commit -m "Simplify the TV playlist loading screen to logo and section checks"
```

### Task 3: Sichtprüfung + TV-UI-Tests (einmal am Ende)

- [ ] PC: `./gradlew :desktopApp:run` mit Demo-Server (`tools/demo-server`), Neu laden in den Einstellungen → Ladebildschirm ansehen (User bestätigt).
- [ ] TV: Emulator `AndroidTV_1080p_API34`, `./gradlew :androidApp:connectedDebugAndroidTest` (~3 Min) → grün.
- [ ] Spec + Plan committen, PR „Einfacher Ladebildschirm (PC und TV)“.
