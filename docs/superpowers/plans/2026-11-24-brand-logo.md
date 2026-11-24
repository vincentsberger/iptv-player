# App-Logo, Einschalt-Animation, Häkchen im Ladebildschirm – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Logo statt Bernstein-Quadrat in allen drei Apps, die „Einschalt“-Animation als Ladesymbol (Start + große Ladestellen), Ladeschritte mit Häkchen-Kästchen rechts und Überschrift „Sender/Filme/Serien werden gesucht“.

**Architecture:** Form, Taktung, Ladeschritt-Zustand und Überschrift sind reine Logik in `shared/…/shared/brand/` (getestet). Jede Oberfläche zeichnet `LogoMark`/`LogoLoader` per Compose-`Canvas` aus diesen Werten und baut sie in Wortmarke, Ladebildschirm und Ladestellen ein.

**Tech Stack:** Kotlin Multiplatform, Compose (Canvas, `rememberInfiniteTransition`), Android-Theme (values-v31), iOS `UILaunchScreen`.

**Spec:** `docs/superpowers/specs/2026-11-24-brand-logo-design.md` · Entwurf https://claude.ai/artifact/LqdPMXZZwUN7aJmXLjgooA (Tafeln „Start“, „Navigation TV“, „A · Einschalten (iPhone)“)

## Global Constraints

- Form im ViewBox 240 × 200 (wie App-Icon und Entwurf): Antenne `M84 20 L120 52 L156 20`, Gehäuse `x 24..216, y 56..188`, Radius 16, Strich 12; Bildfläche innen; Balken `x 52..188, y 140..158`, 60 % `Signal`, 40 % `Surface2`. Farben: Gehäuse `Text`, Röhrenblitz `FocusBg`, Balken `Signal`/`Surface2` – nur aus `Palette`.
- Animation: Zyklus 4 000 ms; Zeichnen 0–35 %, Röhre 30–46 % (Strich → Fläche, Blitz dann auf Alpha 0,06), Balken erscheint 44–50 %; wiederholt, solange geladen wird. Statisch bei „Animationen aus“ (Android) / „Bewegung reduzieren“ (iOS).
- Überschrift: „Sender werden gesucht“ (LIVE), „Filme werden gesucht“ (MOVIES), „Serien werden gesucht“ (SERIES); erste noch ladende in dieser Reihenfolge; ist keine offen, bleibt die letzte stehen.
- Ladezeile: links Bereichssymbol (unverändert), rechts Kästchen 20 dp – leer (Rand `Muted`) mit Spinner 14 dp + „lädt …“ davor; fertig: gefüllt `Signal` mit Häkchen `OnSignal`, Anzahl in Mono davor; Fehler: Rand `Danger` mit ✕. Wartende Bereiche: leeres Kästchen, „wartet“.
- `LogoLoader` ersetzt nur große Ladestellen (siehe Tasks); kleine Spinner in Zeilen/Knöpfen und Skelette bleiben. Player: nur bis zum ersten Bild eines Titels.
- Bezeichner englisch, KDoc deutsch, Zeilen ≤ 160; detekt-Befunde beheben, nicht in die Baseline. Commits englisch ohne Co-Authored-By. Worktree `.worktrees/brand-logo`, Branch `feat/brand-logo`.

## Review Focus

- **Start aus dem Zwischenspeicher (alles sofort fertig):** keine hängende leere Überschrift → Test `headlineKeepsLastWhenNothingOpen` (Task 1).
- **Fehler in einem Bereich:** Häkchen nur für fertige, ✕ für den fehlerhaften, Überschrift springt zum nächsten offenen → Test `failedSectionIsNotLoadingAndHeadlineMovesOn` (Task 1).
- **Nachpuffern mitten im Film:** kein Logo, Spinner wie bisher → Test `logoOnlyBeforeFirstFrame` (Task 2, PC-UI-Test).
- **Bewegung reduzieren:** statisches Logo, keine Endlosanimation → Handtest je Plattform (Task 3, 4).
- **Kleine Größen (Wortmarke 21 sp):** Logo bleibt erkennbar, Strich nicht dünner als 1,5 dp → Handtest/Screenshot (Task 2).

---

### Task 1: Logo-Geometrie, Taktung, Ladeschritt und Überschrift in `shared`

**Files:**
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/brand/BrandMark.kt`
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/brand/StartupText.kt`
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/brand/BrandMarkTest.kt`, `StartupTextTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object BrandMark { const val VIEW_W = 240f; const val VIEW_H = 200f; const val STROKE = 12f; const val CORNER = 16f
      const val CYCLE_MS = 4_000; /* Rechtecke/Linien als Float-Konstanten, siehe Global Constraints */ }
  data class BrandMarkFrame(val drawn: Float, val screenScaleX: Float, val screenScaleY: Float, val screenAlpha: Float, val barAlpha: Float)
  fun brandMarkFrame(t: Float): BrandMarkFrame          // t = Anteil im Zyklus 0..1 (Werte außerhalb werden modulo 1 genommen)
  val BRAND_MARK_STATIC: BrandMarkFrame                  // fertig gezeichnet, Bild aus, Balken sichtbar
  enum class LoadStep { WAITING, LOADING, DONE, FAILED }
  fun loadStep(raw: SectionState?, active: Boolean): LoadStep
  fun loadingHeadline(sections: Map<Section, SectionState?>): String?   // null = nichts offen (UI behält die letzte)
  ```

- [ ] **Step 1: Failing tests**
```kotlin
@Test fun frameAtStartIsUndrawn()          // brandMarkFrame(0f): drawn == 0f, screenAlpha == 0f, barAlpha == 0f
@Test fun frameDrawnAt35Percent()          // brandMarkFrame(0.35f).drawn == 1f
@Test fun screenFlashesThenSettles()       // 0.38f: screenScaleX == 1f, screenScaleY klein (≤ 0.05f), screenAlpha == 1f; 0.46f: screenScaleY == 1f; 0.9f: screenAlpha == 0.06f
@Test fun barVisibleFromHalf()             // 0.44f: barAlpha == 0f; 0.5f: barAlpha == 1f
@Test fun valuesStayInUnitRange()          // für t in 0..1 Schritt 0.01 alle Felder in 0f..1f
@Test fun cycleRepeats()                   // brandMarkFrame(1.2f) == brandMarkFrame(0.2f)
@Test fun loadStepStates()                 // null,active=false → WAITING; null,active=true → LOADING; loading=true → LOADING; loading=false,error=null → DONE; error!=null → FAILED
@Test fun headlineFollowsFirstOpenSection() // LIVE lädt → „Sender werden gesucht“; LIVE fertig, MOVIES lädt → „Filme werden gesucht“; nur SERIES offen → „Serien werden gesucht“
@Test fun failedSectionIsNotLoadingAndHeadlineMovesOn() // LIVE Fehler, MOVIES lädt → „Filme werden gesucht“
@Test fun headlineKeepsLastWhenNothingOpen()  // alle fertig → null
```
- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*brand*' 2>&1 | tail -10` → FAIL (unresolved).
- [ ] **Step 3:** Implementieren; Phasenwerte als benannte Konstanten (`DRAW_END = 0.35f`, `CRT_START = 0.30f`, `CRT_LINE = 0.38f`, `CRT_END = 0.46f`, `BAR_START = 0.44f`, `BAR_END = 0.50f`, `SCREEN_IDLE_ALPHA = 0.06f`); zwischen den Stützstellen linear. `loadStep`: „aktiv“ = erster noch nicht fertiger Bereich (wie iPhone heute), wartende = WAITING.
- [ ] **Step 4:** `./gradlew :shared:desktopTest detekt 2>&1 | tail -5` → grün.
- [ ] **Step 5:** Commit `Add brand mark geometry, animation timing and startup texts`.

### Task 2: PC – `LogoMark`/`LogoLoader`, Wortmarke, Ladebildschirm, Ladestellen

**Files:**
- Create: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/Logo.kt`
- Modify: `desktopApp/…/desktop/ui/Components.kt` (`Masthead` ~Z. 259, `Loading` ~Z. 300, `PlaylistLoadingScreen` ~Z. 532, `LoadingStep` ~Z. 581), `desktopApp/…/desktop/ui/PlayerScreen.kt` (~Z. 288-290)
- Test: `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/LogoTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces (Muster für Tasks 3/4): `@Composable fun LogoMark(size: Dp, modifier: Modifier = Modifier)` (Breite; Höhe = size × 200/240), `@Composable fun LogoLoader(size: Dp, label: String? = null, modifier: Modifier = Modifier)` mit `contentDescription` „Wird geladen“, `@Composable fun LoadCheck(step: LoadStep)` (20 dp Kästchen, `contentDescription` „wartet“/„lädt“/„geladen“/„Fehler“).

- [ ] **Step 1: Failing tests** (`createComposeRule`, Muster `HomeScreenTest`)
```kotlin
@Test fun loadingScreenShowsHeadlineOfFirstOpenSection()   // testAppState mit LIVE fertig, MOVIES lädt → Text „Filme werden gesucht“, contentDescription „geladen“ einmal, „lädt“ einmal
@Test fun failedSectionShowsErrorBox()                     // MOVIES error → contentDescription „Fehler“
@Test fun loadingUsesLogoLoader()                          // Loading("Sender werden geladen …") zeigt contentDescription „Wird geladen“ und den Text
@Test fun logoOnlyBeforeFirstFrame()                       // Player-Puffern vor erstem Bild → „Wird geladen“; nach erstem Bild erneut puffernd → kein „Wird geladen“, Spinner
```
  (Wie der Ladezustand für die Tests erzeugt wird – `FakeHttp`, das langsam antwortet, oder `SectionState` direkt –, legt der Bau fest; der Test darf keine Echtzeit-Wartezeit brauchen.)
- [ ] **Step 2:** `./gradlew :desktopApp:test --tests '*LogoTest' 2>&1 | tail -10` → FAIL.
- [ ] **Step 3:** `Logo.kt`: `Canvas` zeichnet Gehäuse/Antenne mit `PathMeasure`-Teilstück nach `drawn`, Bildfläche als `drawRect` mit Skalierung um die Mitte, Balken mit `barAlpha`; `LogoLoader` treibt `t` per `rememberInfiniteTransition` (linear, `BrandMark.CYCLE_MS`). `Masthead`: `LogoMark(size.value * 1.6f .dp)` statt Quadrat. `PlaylistLoadingScreen`: `LogoLoader` neben „IPTV Player“, Überschrift aus `loadingHeadline` (letzte per `remember` halten), Zeilen mit `LoadCheck` rechts. `Loading(text)` → `LogoLoader(56.dp, text)`. Player: Flag „erstes Bild gesehen“ je Titel (`player.frame != null || player.playing` einmal true) → davor `LogoLoader(72.dp)`, danach wie heute.
- [ ] **Step 4:** `./gradlew :desktopApp:test :shared:desktopTest detekt 2>&1 | tail -5` → grün.
- [ ] **Step 5: Handtest:** `./gradlew :desktopApp:run` – Start, Wortmarke bei 21 sp lesbar, Kategorie-Laden, Player-Start.
- [ ] **Step 6:** Commit `Show brand logo and startup animation in desktop app`.

### Task 3: TV – Logo, Ladebildschirm, Seitenleiste, Player, System-Startfenster

**Files:**
- Create: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/Logo.kt`
- Modify: `androidApp/…/app/ui/Components.kt` (`SignalMark`/`Masthead` ~Z. 269-278, `PlaylistLoadingScreen` ~Z. 741, `LoadingStep` ~Z. 756), `androidApp/…/app/ui/HomeScreen.kt:168` (Seitenleiste: `LogoMark(40.dp)` statt `SignalMark`), `androidApp/…/app/ui/PlayerScreen.kt:~364` (Puffer-Hinweis), `androidApp/src/main/res/values/themes.xml` + neu `values-v31/themes.xml` (`android:windowSplashScreenBackground` = `@color/bg_deep`, Farbe aus Palette ergänzen falls fehlt)
- Test: `androidApp/src/androidTest/java/de/vincentsberger/iptvplayer/app/LoadingScreenTest.kt`

**Interfaces:** wie Task 2 (eigene TV-Fassung); „Animationen aus“: `Settings.Global.getFloat(contentResolver, ANIMATOR_DURATION_SCALE, 1f) == 0f` → `BRAND_MARK_STATIC`.

- [ ] **Step 1: Failing test** `loadingScreenChecksFinishedSections` (androidTest): wie Task 2 `loadingScreenShowsHeadlineOfFirstOpenSection`.
- [ ] **Step 2:** `./gradlew :androidApp:compileDebugAndroidTestKotlin 2>&1 | tail -5` → FAIL.
- [ ] **Step 3:** Umsetzen wie Task 2; `SignalMark` entfällt, wenn keine Verwendung mehr übrig ist.
- [ ] **Step 4:** `./gradlew :androidApp:compileDebugKotlin :androidApp:compileDebugAndroidTestKotlin detekt 2>&1 | tail -5` → grün (Gerätetest-Lauf in Task 4).
- [ ] **Step 5:** Commit `Show brand logo and startup animation in TV app`.

### Task 4: iPhone – Logo, Ladebildschirm, Anmeldung, Programm-Blatt, Player, LaunchScreen; Gesamtprüfung

**Files:**
- Create: `iosUi/src/iosMain/kotlin/de/vincentsberger/iptvplayer/ios/ui/design/Logo.kt`
- Modify: `iosUi/…/ios/ui/LoadingScreen.kt` (Kopf: `LogoLoader` + Überschrift aus `loadingHeadline`; `SectionLoadRow`: links Bereichssymbol `sectionIcon(s)`, rechts `LoadCheck` – Gesamt-Fortschritt und Knöpfe bleiben), `iosUi/…/ios/ui/LoginScreen.kt:108` (`LogoMark` über `H1`), `iosUi/…/ios/ui/player/ProgrammeSheet.kt:68` (`LogoLoader`), `iosUi/…/ios/ui/player/PlayerOverlays.kt:~337` (vor erstem Bild `LogoLoader`), `iosApp/xcode/iosApp/Info.plist` (`UILaunchScreen`: `UIColorName` = `LaunchBackground`, `UIImageName` = `LaunchLogo`) + Asset-Katalog-Einträge (Farbe `bg-deep`, PNG des statischen Logos aus `tools/brand/make_icons.py` erzeugt – Skript um `--launch-logo` erweitern)

**Interfaces:** wie Task 2; „Bewegung reduzieren“: `UIAccessibilityIsReduceMotionEnabled()` → `BRAND_MARK_STATIC`.

- [ ] **Step 1:** Logo/Loader/LoadCheck wie Task 2, Einbau laut Files.
- [ ] **Step 2:** `./gradlew :iosUi:compileKotlinIosArm64 :iosUi:compileKotlinIosSimulatorArm64 detekt 2>&1 | tail -5` → grün.
- [ ] **Step 3: Gesamtprüfung:**
  ```bash
  ./gradlew :shared:desktopTest :desktopApp:test detekt :androidApp:compileDebugKotlin :desktopApp:compileKotlin :iosUi:compileKotlinIosSimulatorArm64 > build/brand-check.log 2>&1; tail -3 build/brand-check.log
  ./gradlew :androidApp:connectedDebugAndroidTest 2>&1 | tail -10   # einmal, Emulator AndroidTV_1080p_API34
  ```
- [ ] **Step 4:** Commit `Show brand logo and startup animation in iOS app`.

## Abschluss

PR `feat/brand-logo` → `master` (deutsch); offen: Handtests TV/iPhone, LaunchScreen erst mit `.ipa` aus der CI sichtbar. Nicht selbst mergen.
