# PlaybackController – Player-Ablauf in `shared` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Den Wiedergabe-Ablauf, den heute alle drei `PlayerScreen`s je für sich bauen (Titelwechsel, Fortschritt sichern, Neuverbinden, nächste Folge, Ziffernwahl, Schlaf-Timer), einmal in `shared/commonMain` als `PlaybackController` führen und dort ohne Gerät testen.

**Architecture:** Neues Paket `shared/…/shared/player/` mit `PlaybackEngine` (schmales Interface: Zustand lesen, `play`, `seekTo`) und `PlaybackController` (Compose-State + `suspend`-Abläufe). Die Oberflächen behalten ihre Compose-Hüllen (`LaunchedEffect`-Schlüssel, Tasten/Gesten, Overlays) und rufen nur noch Controller-Methoden auf. Plattform-Engines: `VlcPlayer` (PC) und `PlayerEngine` (iPhone) implementieren das Interface direkt, Android bekommt den Adapter `ExoPlaybackEngine` um ExoPlayer.

**Tech Stack:** Kotlin Multiplatform, Compose Runtime (`mutableStateOf`) in commonMain, kotlinx-coroutines 1.11 (`runTest` + virtuelle Zeit in commonTest), Media3/ExoPlayer, vlcj, AVPlayer/VLCKit.

**Spec:** Keine eigene Spec – Grundlage ist die Refactoring-Bewertung vom 2026-11-24 (Punkt 1 „Player-Logik nach `shared`“). Ist-Stand: `androidApp/…/app/ui/PlayerScreen.kt:157-400`, `desktopApp/…/desktop/ui/PlayerScreen.kt:114-322`, `iosUi/…/ios/ui/player/PlayerScreen.kt:118-300`.

## Global Constraints

- Reines Refactoring: sichtbares Verhalten bleibt gleich, außer bei den drei unten genannten Vereinheitlichungen.
- Bestehende Bausteine wiederverwenden, nicht neu schreiben: `StreamRecovery`, `ZapCard`, `SeekTarget`, `ChannelNumberInput`, `wrapIndex`, `isPausedForInfo`, `mediaBadges`.
- Neuer Zustand gehört in den Controller, **nicht** in `AppState` (Fassade bleibt unverändert).
- Bezeichner englisch, KDoc/Kommentare deutsch, Zeilen bis 160 Zeichen. Neue detekt-Befunde beheben, nicht in die Baseline.
- Commits englisch, ohne Co-Authored-By. Branch `refactor/playback-controller` von `master`.
- `PauseInfo`, Tastenbelegung (`playerActionFor`, `digitOf`), Gesten, MediaSession/NowPlaying, Lautstärke, Spuren bleiben in den Oberflächen.
- Gewollte Vereinheitlichungen (im PR-Text nennen):
  1. PC: Startposition gilt nur beim ersten Start (heute `index == startIndex` → Zurückzappen auf den Startsender springt erneut zur alten Stelle).
  2. PC: „Erneut versuchen“ verbindet wie TV/iPhone mit `keepFrame = true` und zeigt „Neu verbinden …“.
  3. TV/PC: EPG („Jetzt läuft“) wird nur noch für Live geladen (wie iPhone).

## Review Focus

- **Zurückzappen auf den Startsender (PC):** setzt bei 0 bzw. Live-Kante an, nicht erneut bei `startPosition` → Test `startPositionOnlyOnFirstStart` (Task 2).
- **Stream bricht ab, während die App im Hintergrund ist (iPhone):** kein Neuverbinden, solange `suspended` → Test `noReconnectWhileSuspended` (Task 3).
- **Ziffernwahl in eine andere Kategorie:** Liste wechselt, `lastIndex` wird verworfen (sonst springt „Letzter Sender“ in die falsche Liste) → Test `digitsSwitchToAllChannelsList` (Task 2).
- **Dauer unbekannt (Live, ExoPlayer `C.TIME_UNSET`):** `lengthMs` ist nie negativ → Test `lengthNeverNegative` (Task 6, Android-Adapter).
- **Countdown „Nächste Folge“ abgebrochen, dann Folge manuell gewechselt:** neuer Countdown für die neue Folge möglich → Test `autoNextAgainAfterManualSwitch` (Task 2).

---

### Task 1: `PlaybackEngine`, Test-Attrappe und Anzeige-Helfer in `shared`

**Files:**
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/player/PlaybackEngine.kt`
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/player/PlayerFormat.kt`
- Create: `testing/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/testing/FakePlaybackEngine.kt`
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/player/PlayerFormatTest.kt`
- Modify: `androidApp/…/app/ui/PlayerScreen.kt:753-762` (Helfer löschen, Import), `desktopApp/…/desktop/ui/PlayerScreen.kt:664-673` (dito)
- Modify: `androidApp/src/androidTest/…/app/PlayerLogicTest.kt:115-119`, `desktopApp/src/test/…/desktop/ui/PlayerLogicTest.kt` (Fälle für `seekMultiplier`/`endsAt` entfernen – wandern nach `PlayerFormatTest`)

**Interfaces:**
- Produces:
  ```kotlin
  interface PlaybackEngine {
      val timeMs: Long          // ≥ 0
      val lengthMs: Long        // ≥ 0, 0 = unbekannt
      val playing: Boolean
      val paused: Boolean       // vom Nutzer angehalten
      val finished: Boolean
      val failed: Boolean
      /** Letzter Bild-/Zeitfortschritt, monotone ms; Long.MIN_VALUE / 2 = Engine meldet das nicht (Controller misst selbst). */
      val lastProgressAt: Long
      /** Start des aktuellen Streams, monotone ms. */
      val startedAt: Long
      /** [keepFrame] = Neuverbinden: letztes Bild stehen lassen, Live an der Live-Kante. */
      fun play(item: Item, url: String, startMs: Long, keepFrame: Boolean = false)
      fun seekTo(ms: Long)
  }
  fun seekMultiplier(repeatCount: Int): Int
  fun endsAt(remainingMs: Long, now: Long = currentTimeMillis()): String
  class FakePlaybackEngine(clock: () -> Long) : PlaybackEngine  // alle vals als public var; plays: MutableList<Play>
  data class Play(val item: Item, val url: String, val startMs: Long, val keepFrame: Boolean)
  ```

- [ ] **Step 1: Failing test `PlayerFormatTest`**

```kotlin
@Test fun seekMultiplierGrowsWhileHeld() {
    assertEquals(1, seekMultiplier(0)); assertEquals(1, seekMultiplier(9))
    assertEquals(3, seekMultiplier(10)); assertEquals(3, seekMultiplier(29)); assertEquals(6, seekMultiplier(30))
}
@Test fun endsAtIsLocalClockTime() {
    val now = 1_700_000_000_000L // Ortszeit über localDateTime gegenprüfen – Test hängt so nicht an der Zeitzone
    assertEquals(localDateTime(now + 45 * 60_000L).substring(11, 16), endsAt(45 * 60_000L, now))
}
@Test fun endsAtClampsNegativeRemaining() { assertEquals(localDateTime(5_000L).substring(11, 16), endsAt(-1_000L, 5_000L)) }
```

- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*PlayerFormatTest' 2>&1 | tail -20` → FAIL (unresolved reference).
- [ ] **Step 3:** `PlaybackEngine` und `PlayerFormat.kt` anlegen. `endsAt` = `localDateTime(now + remainingMs.coerceAtLeast(0)).substring(11, 16)`; `seekMultiplier` aus Android übernehmen. `FakePlaybackEngine`: `play()` hängt an `plays` an, setzt `startedAt = clock()`, `failed = false`, `finished = false`, `timeMs = startMs`; `seekTo` setzt `timeMs`.
- [ ] **Step 4:** Android- und PC-Kopien von `seekMultiplier`/`endsAt` löschen, Aufrufer importieren `…shared.player.*`; die zwei Fälle aus beiden `PlayerLogicTest` entfernen.
- [ ] **Step 5:** `./gradlew :shared:desktopTest :desktopApp:test :androidApp:compileDebugAndroidTestKotlin 2>&1 | tail -20` → BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit `Move player format helpers to shared and add PlaybackEngine`.

---

### Task 2: `PlaybackController` – Titel, Quellen, Ziffernwahl, nächste Folge, Uhr-Takt

**Files:**
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/player/PlaybackController.kt`
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/player/PlaybackControllerTest.kt`

**Interfaces:**
- Consumes: `PlaybackEngine`, `FakePlaybackEngine` (Task 1); `AppState.saveProgress/nextEpisodeIndex/autoNextEpisode/sleepMinutesLeft/consumeSleepTimer/sourcesOf/nowPlaying/requestMediaInfo/section`.
- Produces:
  ```kotlin
  class PlaybackController(
      private val app: AppState,
      val engine: PlaybackEngine,
      startItems: List<Item>, startIndex: Int, private val startPosition: Long,
      clock: () -> Long,                                  // Wanduhr: ZapCard, SeekTarget
      private val monotonic: () -> Long,                  // StreamRecovery (Task 3)
      private val resolveUrl: (Item) -> String = { it.url },
  ) {
      // Compose-State, nur lesbar von außen
      val items: List<Item>; val index: Int; val item: Item; val isLive: Boolean; val isEpisodes: Boolean
      val sources: List<Item>; val sourceIndex: Int; val source: Item
      val lastIndex: Int?; val epg: List<EpgEntry>
      val nextIn: Int?; val sleepLeft: Int?; val positionMs: Long; val durationMs: Long
      /** Startposition des laufenden Titels (0 = von vorn) – die Oberflächen zeigen die Leiste nach einem Fortsetzen länger. */
      val startedFromMs: Long
      val zap: ZapCard; val seek: SeekTarget; val numberInput: ChannelNumberInput
      fun switchTo(i: Int)                 // wrapIndex, seek.cancel(), lastIndex merken
      fun lastChannel()                    // lastIndex?.let(::switchTo)
      fun selectSource(i: Int)             // i != sourceIndex: sourceIndex = i, recovery.reset(), connectionLost = false,
                                           // engine.play(item, resolveUrl(sources[i]), if (isLive) 0 else engine.timeMs, keepFrame = true)
      fun cancelNext()
      fun saveNow()                        // app.saveProgress(item, seek.positionOf(engine.timeMs), engine.lengthMs)
      suspend fun startCurrent()           // Schlüssel (items, index): vorigen sichern, play, zap/mediaInfo, EPG
      suspend fun runAutoNext()            // Schlüssel (engine.finished, index, nextCancelled)
      suspend fun commitDigits(onNotFound: (Int) -> Unit)   // Schlüssel numberInput.digits
      suspend fun runClock(onSleep: () -> Unit, onTick: () -> Unit = {})  // Schlüssel Unit
  }
  ```
  `nextCancelled` ist intern und wird bei jedem Indexwechsel zurückgesetzt; für den `LaunchedEffect`-Schlüssel als `val nextCancelled: Boolean` lesbar.

- [ ] **Step 1: Failing tests** (`runTest`, `testAppState(scope = this)`, Items aus `SampleData`; Live-Liste mit 3 Sendern, Serie mit 3 Folgen)

```kotlin
@Test fun startCurrentPlaysAtStartPosition()        // plays.single() == Play(item0, item0.url, 42_000, false); startedFromMs == 42_000
@Test fun selectSourcePlaysAlternate()              // Daten wie DuplicatesTest (BBC ONE ×3): selectSource(1) → plays.last().url == sources[1].url, startMs 0, keepFrame
@Test fun sourceResetsOnTitleChange()               // selectSource(1); switchTo(1) → sourceIndex == 0
@Test fun startPositionOnlyOnFirstStart()           // start(idx 0, pos 42_000) → switchTo(1) → switchTo(0): plays[2].startMs == 0
@Test fun switchingSavesPreviousProgress()          // engine.timeMs = 30_000, lengthMs = 100_000 → switchTo(1); startCurrent(): History von item0 hat 30_000
@Test fun switchToWrapsAndRemembersLast()           // index 0, switchTo(-1) → index 2, lastIndex 0; lastChannel() → index 0
@Test fun switchCancelsPendingSeek()                // seek.press(30_000, 0, 100_000); switchTo(1) → seek.positionOf(5) == 5
@Test fun resolveUrlIsUsed()                        // resolveUrl = { "file://x" } → plays.single().url == "file://x"
@Test fun epgOnlyForLive()                          // Film: epg leer und keine EPG-Anfrage an FakeHttp
@Test fun autoNextCountsDownAndSwitches()           // finished = true; runAutoNext(): nextIn 10 … 1, nach 10 s index == 1
@Test fun autoNextCancelled()                       // cancelNext() während Countdown → index bleibt, nextIn == null
@Test fun autoNextAgainAfterManualSwitch()          // cancelNext(); switchTo(1); finished → neuer Countdown startet
@Test fun digitsSwitchWithinList()                  // numberInput.type(2); commitDigits → nach 1500 ms index == 1
@Test fun digitsSwitchToAllChannelsList()           // Nummer nur in app.section(LIVE).items → items == alle Sender, lastIndex == null
@Test fun digitsNotFound()                          // type(9); type(9) → onNotFound(99), index unverändert
@Test fun clockSavesEvery15sForVodOnly()            // runClock 15 s virtuell: Film 1× gespeichert, Live 0×
@Test fun clockStopsOnSleepTimer()                  // Schlaf-Timer abgelaufen → onSleep() genau einmal, runClock kehrt zurück
```

- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*PlaybackControllerTest' 2>&1 | tail -20` → FAIL.
- [ ] **Step 3:** `PlaybackController` implementieren. Abläufe 1:1 aus den heutigen `LaunchedEffect`-Körpern übernehmen (Android `PlayerScreen.kt:281-363`, Startposition über internes `startPending`-Flag wie iPhone). `startCurrent`: `zap.onChannelChanged(item)` bei Live, sonst `zap.hide()` + `app.requestMediaInfo(item)`; `epg = emptyList()`, bei Live `epg = app.nowPlaying(item)`. `runClock` alle 500 ms, Speichern bei `++ticks % 30 == 0 && !isLive`.
- [ ] **Step 4:** Tests → PASS; `./gradlew detekt 2>&1 | tail -15` ohne neue Befunde.
- [ ] **Step 5:** Commit `Add PlaybackController for playlist, auto-next, channel digits and clock`.

---

### Task 3: `PlaybackController` – Neuverbinden (`watch`, `retry`, `suspended`)

**Files:**
- Modify: `shared/…/shared/player/PlaybackController.kt`
- Test: `shared/src/commonTest/…/shared/player/PlaybackControllerRecoveryTest.kt`

**Interfaces:**
- Consumes: `StreamRecovery` (unverändert), `monotonic` aus dem Konstruktor.
- Produces:
  ```kotlin
  val reconnecting: Boolean; val connectionLost: Boolean
  var suspended: Boolean               // iPhone im Hintergrund: Wächter pausiert
  suspend fun watch()                  // Schlüssel (items, index), läuft endlos; UI: LaunchedEffect(items, index) { startCurrent(); watch() }
  fun retry()                          // recovery.reset(); connectionLost = false; reconnecting = true; reload()
  ```
  `reload()` (privat) = `engine.play(item, resolveUrl(source), if (isLive) 0 else resumeAt, keepFrame = true)`.
  Fortschritt zählt, wenn `monotonic() - engine.lastProgressAt < 2_000` **oder** `engine.playing && engine.timeMs != letzteMessung` (deckt ExoPlayer ohne eigene Meldung ab). Hänger: `!engine.paused && !engine.finished && recovery.isStalled(maxOf(engine.lastProgressAt, gemessenerFortschritt, engine.startedAt), now)`. `connectionLost` blendet die Zapp-Karte aus (`zap.hide()`).

- [ ] **Step 1: Failing tests** (`monotonic = { testScheduler.currentTime }`)

```kotlin
@Test fun failedStreamReconnectsWithKeepFrame()     // failed = true → nach 1 s + 2 s: plays.last().keepFrame, reconnecting war true
@Test fun vodResumesAtLastGoodPosition()            // playing, timeMs 50_000 → 60_000; dann failed → plays.last().startMs == 60_000
@Test fun liveReconnectsAtLiveEdge()                // Live, failed → plays.last().startMs == 0
@Test fun liveFinishedCountsAsBroken()              // Live, finished = true → Neuverbinden
@Test fun stallDetectedWithoutEngineProgress()      // lastProgressAt = MIN/2, playing, timeMs bleibt stehen 15 s → Neuverbinden
@Test fun progressViaTimeSamplingResetsState()      // nach Neuverbinden läuft timeMs weiter → reconnecting == false
@Test fun givesUpAfterAllRetries()                  // dauerhaft failed → nach 2+4+8+15+30 s connectionLost, zap.channel == null
@Test fun retryAfterLostReconnects()                // connectionLost → retry() → connectionLost false, reconnecting true, neues Play mit keepFrame
@Test fun noReconnectWhileSuspended()               // suspended = true, failed → 60 s: keine weiteren Plays
@Test fun pausedIsNotStalled()                      // paused = true, timeMs steht 60 s → keine Plays
```

- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*PlaybackControllerRecoveryTest' 2>&1 | tail -20` → FAIL.
- [ ] **Step 3:** `watch()`, `retry()`, `reload()`, `suspended` implementieren (Vorlage: iPhone `PlayerScreen.kt:246-271`, plus Zeit-Messung aus Android `:307-338`). Nach `delay(wait)` erneut `suspended` prüfen, wie iPhone heute.
- [ ] **Step 4:** Tests → PASS; detekt ohne neue Befunde.
- [ ] **Step 5:** Commit `Add stream recovery to PlaybackController`.

---

### Task 4: PC auf den Controller umstellen

**Files:**
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/player/VlcPlayer.kt` (`: PlaybackEngine`)
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/PlayerScreen.kt:114-322` und Aufrufer der entfernten Zustände bis Dateiende
- Test: `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/PlayerLogicTest.kt` (bestehend, muss grün bleiben)

**Interfaces:**
- Consumes: `PlaybackController`, `PlaybackEngine` (Task 1–3).
- Produces: `VlcPlayer` erfüllt `PlaybackEngine` (`timeMs = time`, `lengthMs = length`, `failed = error != null`; `play(item, url, startMs, keepFrame)` delegiert an das bestehende `play(url, startMs, keepFrame)`).

- [ ] **Step 1:** `VlcPlayer` implementiert `PlaybackEngine` (nur `override`-Delegationen, bestehende Namen bleiben für die Oberfläche).
- [ ] **Step 2:** In `PlayerScreen` `val c = remember { PlaybackController(app, player, startItems, startIndex, startPosition, System::currentTimeMillis, ::monotonicMs) }`. Entfernen: lokale `items/index/lastIndex/sources/sourceIndex/epg/position/duration/nextIn/nextCancelled/sleepLeft/recovery/reconnecting/connectionLost/resumeAt/previous`, `switchTo`, die Effekte `:204-217`, `:244-266`, `:275-296`, `:308-321`, Zapp-Ticks. Ersetzen durch:
  ```kotlin
  LaunchedEffect(c.items, c.index) { c.startCurrent(); if (!c.isLive) poke(); c.watch() }
  LaunchedEffect(player.finished, c.index, c.nextCancelled) { c.runAutoNext() }
  LaunchedEffect(c.numberInput.digits) { c.commitDigits { notFound = it } }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() }; c.runClock(onSleep = onBack) { clock = fmt.format(Date()); player.applyPreferencesIfPending() } }
  ```
  `onDispose` ruft `c.saveNow()`; „Erneut versuchen“ ruft `c.retry()`; Quellen-Menü ruft `c.selectSource(i)`. Lokales `resumedAt` entfällt: die Leisten-Dauer liest `c.startedFromMs > 0`, das Zurücksetzen nach dem Ausblenden bleibt ein lokales `Boolean` der UI.
- [ ] **Step 3:** `./gradlew :desktopApp:test detekt 2>&1 | tail -20` → BUILD SUCCESSFUL, keine neuen Befunde.
- [ ] **Step 4: Handtest** mit Demo-Server (`cd tools/demo-server && python3 demo_server.py 8099`, `./gradlew :desktopApp:run`): Film starten → bei 1 min schließen → „Weiterschauen“ steht bei 1 min; Sender zappen ▲▼, Ziffer `2`, Zurückzappen auf Startsender (Review Focus 1); Demo-Server während der Wiedergabe beenden → „Neu verbinden …“, nach ~60 s Fehlerdialog → Server starten → „Erneut versuchen“ läuft.
- [ ] **Step 5:** Commit `Use PlaybackController in desktop player`.

---

### Task 5: iPhone auf den Controller umstellen

**Files:**
- Modify: `iosUi/src/iosMain/kotlin/de/vincentsberger/iptvplayer/ios/ui/player/PlayerEngine.kt` (`interface PlayerEngine : PlaybackEngine`, doppelte Member entfernen)
- Modify: alle `PlayerEngine`-Implementierungen in `iosUi/…/player/` (`play(item, url, …)` ergänzen, ruft bestehendes `play(url, …)`)
- Modify: `iosUi/…/ios/ui/player/PlayerScreen.kt:118-300` und `:395-405` (Retry)

**Interfaces:**
- Consumes: `PlaybackController` (Task 2–3), `resolveUrl = ::playUrl`.
- Produces: `PlayerEngine : PlaybackEngine`; `timeMs/lengthMs/failed/finished/paused/playing/lastProgressAt/startedAt` kommen aus `PlaybackEngine`.

- [ ] **Step 1:** `PlayerEngine` erbt von `PlaybackEngine`; die Default-Getter (`playing`, `paused`, …) bleiben als `override val … get() = state == …`.
- [ ] **Step 2:** `PlayerScreen` auf `c = remember { PlaybackController(app, player, …, ::currentTimeMillis, ::monotonicMs, resolveUrl = ::playUrl) }` umstellen; Effekte `:185-195`, `:246-271`, `:273-281`, `:290-298` durch Controller-Aufrufe ersetzen (Muster wie Task 4). `backgrounded` → `c.suspended`; `BackgroundAction.PAUSE` ruft `c.saveNow()`. `handle(TouchAction)` nutzt `c.switchTo` und `c.seek`.
- [ ] **Step 3:** `./gradlew :iosUi:compileKotlinIosSimulatorArm64 detekt 2>&1 | tail -20` → BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit `Use PlaybackController in iOS player`. Handtest auf dem iPhone erst mit der nächsten `.ipa` (im PR-Text als offen vermerken).

---

### Task 6: TV auf den Controller umstellen (`ExoPlaybackEngine`)

**Files:**
- Create: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/ExoPlaybackEngine.kt`
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/PlayerScreen.kt:157-400` und Tastenbehandlung (`LAST_CHANNEL`, OK bei `connectionLost`)
- Test: `androidApp/src/androidTest/java/de/vincentsberger/iptvplayer/app/ExoPlaybackEngineTest.kt`

**Interfaces:**
- Consumes: `PlaybackEngine`, `PlaybackController`, `mediaItem(url, item)` (bestehend).
- Produces:
  ```kotlin
  class ExoPlaybackEngine(val player: ExoPlayer, private val clock: () -> Long = SystemClock::elapsedRealtime) : PlaybackEngine {
      val error: String?        // „Wiedergabe nicht möglich (<errorCodeName>)“, Compose-State
      val buffering: Boolean    // Compose-State aus onPlaybackStateChanged
      fun release()             // Listener ab, player.release()
  }
  ```
  `timeMs = currentPosition.coerceAtLeast(0)`, `lengthMs = duration.coerceAtLeast(0)`, `playing = isPlaying`, `paused = !playWhenReady`, `finished = state == STATE_ENDED`, `failed = error != null`, `lastProgressAt = Long.MIN_VALUE / 2`. `play()`: `error = null`, `startedAt = clock()`; **Live + keepFrame → `setMediaItem(mi)` (Standardposition = Live-Kante), sonst `setMediaItem(mi, startMs)`** – genau das heutige Verhalten von Erststart und `reload()`. Danach `prepare(); play()`.

- [ ] **Step 1: Failing test `ExoPlaybackEngineTest`** (Instrumentation, `ExoPlayer.Builder(context).build()` auf dem Main-Thread)

```kotlin
@Test fun lengthNeverNegative()                // vor prepare: duration == C.TIME_UNSET → lengthMs == 0
@Test fun vodReloadStartsAtResumePosition()    // play(film, url, 60_000, keepFrame = true) → player.currentPosition == 60_000 (vor prepare)
@Test fun playSetsStartedAt()                  // clock = { 777 } → play(...) → startedAt == 777, failed == false
```

- [ ] **Step 2:** `./gradlew :androidApp:compileDebugAndroidTestKotlin 2>&1 | tail -20` → FAIL (unresolved).
- [ ] **Step 3:** `ExoPlaybackEngine` implementieren; Listener für `error`, `buffering`, `finished` zieht aus dem `PlayerScreen`-Listener (`:228-251`) um. Im Screen bleibt ein Listener nur für `tracks`, `videoSize`, `c.zap.onFirstFrame()`.
- [ ] **Step 4:** `PlayerScreen` umstellen (Muster Task 4): `remember { ExoPlaybackEngine(buildPlayer(context, vm)) }`, Controller mit `System::currentTimeMillis`, `SystemClock::elapsedRealtime`. `ON_STOP` → `c.saveNow()`; `BackHandler` → `c.seek.commitNow()?.let(engine::seekTo); onBack()`; OK bei Verbindungsverlust → `c.retry()`; `LAST_CHANNEL` → `c.lastChannel()`; Ziffern → `c.commitDigits { Feedback.show("Sender $it nicht gefunden") }`; `isPausedForInfo(…, failed = engine.failed || c.reconnecting || c.connectionLost)`.
- [ ] **Step 5:** Gesamtprüfung:
  ```bash
  ./gradlew :shared:desktopTest :desktopApp:test detekt > build/playback-check.log 2>&1; tail -5 build/playback-check.log
  ./gradlew :androidApp:compileDebugKotlin :androidApp:compileDebugAndroidTestKotlin :desktopApp:compileKotlin :iosUi:compileKotlinIosSimulatorArm64 2>&1 | tail -5
  ./gradlew :androidApp:connectedDebugAndroidTest 2>&1 | tail -15   # einmal, Emulator AndroidTV_1080p_API34
  ```
  Erwartet: alles BUILD SUCCESSFUL, alle Tests grün.
- [ ] **Step 6: Handtest im Emulator** (Login per `nc`, siehe CLAUDE.md): Live-Sender nach Abbruch neu verbinden → setzt an der Live-Kante ein, nicht am Fensteranfang (`setMediaItem` ohne Position); Live zappen ▲▼, Ziffern `2` und `99` (Hinweis „nicht gefunden“), Film bis Ende einer Folge → Countdown, Zurück bricht ab; Demo-Server beenden → „Neu verbinden …“ → Fehler → Server starten → OK.
- [ ] **Step 7:** Commit `Use PlaybackController in TV player`.

---

## Abschluss

- PR `refactor/playback-controller` → `master`, Titel deutsch („Player-Ablauf gemeinsam in shared“), Text nennt die drei Vereinheitlichungen aus den Global Constraints und den offenen iPhone-Handtest. Nicht selbst mergen.
- Erwartete Wirkung: ~250 Zeilen weniger in den drei `PlayerScreen`s, ~25 neue Tests in `commonTest` für bisher ungetestete Abläufe (Neuverbinden, Countdown, Ziffernwahl).
