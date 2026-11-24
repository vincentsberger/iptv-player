# Verbindungsanzeige und Statistik-Fenster – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Im Player eine per Einstellung dauerhaft einblendbare Verbindungsanzeige (`●●●`) und ein selbst zu öffnendes Statistik-Fenster („Statistiken für Nerds“) auf TV, PC und iPhone.

**Architecture:** Messen, Verlauf (60 s), Bewertung und alle Anzeigetexte liegen in `shared/…/shared/player/stats/` und werden in `commonTest` getestet. `PlaybackEngine` bekommt `stats(): EngineStats` (Rohwerte, Standard leer); der `PlaybackController` tastet per `runStats()` einmal pro Sekunde ab. Die drei Oberflächen zeichnen nur Indikator und Fenster und öffnen es per Menü, Taste oder Tippen.

**Tech Stack:** Kotlin Multiplatform, Compose Runtime-State, kotlinx-coroutines-test (virtuelle Zeit), Media3 `AnalyticsListener`, vlcj 4.7.3, MobileVLCKit.

**Spec:** `docs/superpowers/specs/2026-11-24-playback-stats-design.md` (verbindlich, vor allem die mit *eigene Festlegung* markierten Punkte).

## Global Constraints

- Angezeigter Server nur `host:port` – nie Benutzer, Passwort, Pfad oder Query; Messwerte nie ins Log.
- Ohne eingeschaltete Anzeige und ohne offenes Fenster: keine Abfrage (`runStats` läuft nicht), nichts gezeichnet.
- Fehlender Wert → „–“, Zeile bleibt stehen; nichts schätzen.
- Bewertung: `POOR` = `reconnecting` ∨ `connectionLost` ∨ ≥ 3 gestockte s in den letzten 10 s; `FAIR` = Neuverbindung oder Stockung in den letzten 60 s ∨ verworfene Bilder > 2 % (nur mit bekanntem fps) ∨ (Puffer bekannt ∧ < 2 000 ms ∧ `!isLive`); `GOOD` sonst; `UNKNOWN` in den ersten 3 Messpunkten nach Titel-/Quellenstart. Schwellen als benannte Konstanten.
- Indikator: `●●●` GOOD, `●●○` FAIR, `●○○` POOR, `○○○` UNKNOWN; Texte „gut“, „mittel“, „schlecht“, „–“.
- Einstellung `showConnectionIndicator`, Standard `false`, Bezeichnung „Verbindungsanzeige im Player“; Speicher-Schlüssel `connIndicator` (Wert `"1"` = an).
- Farben nur aus `Palette.kt`, Stil nach `.tastemaker/style-lock.md`; Texte deutsch; Bezeichner englisch, KDoc deutsch, Zeilen ≤ 160.
- Neue detekt-Befunde beheben, nicht in die Baseline. Commits englisch, ohne Co-Authored-By. Branch `feat/playback-stats` (existiert, enthält die Spec).

## Review Focus

- **Anlaufen nach dem Zappen:** Die ersten Sekunden Puffern vor dem ersten Bild zählen nicht als Stockung, sonst zeigt jeder Senderwechsel kurz „schlecht“ → Test `stallsNotCountedBeforeFirstProgress` (Task 1).
- **Pausierter Film:** Pause ist keine Stockung → Test `pausedSecondsAreNotStalls` (Task 1).
- **Zähler springen zurück** (Neuladen, neues Medium bei VLC): keine negative oder riesige Datenrate → Test `counterDropDiscardsThatSecond` (Task 1).
- **Zugangsdaten in der Adresse** (Xtream-Pfad, `user:pass@`, `?username=…&password=…`) → Test `serverShowsNeverCredentials` (Task 1).
- **Fenster offen beim Zappen:** keine Werte des vorigen Senders im neuen Verlauf → Test `statsResetOnTitleAndSourceChange` (Task 2).

---

### Task 1: Messwerte, Verlauf, Bewertung und Anzeigetexte in `shared`

**Files:**
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/player/stats/EngineStats.kt`
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/player/stats/StatsMeter.kt`
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/player/stats/ConnectionQuality.kt`
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/player/stats/StatsText.kt`
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/player/PlaybackEngine.kt` (Default-Methode)
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/player/stats/StatsMeterTest.kt`, `ConnectionQualityTest.kt`, `StatsTextTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class EngineStats(                         // null = unbekannt; Zähler kumuliert je Medium
      val bytesReceived: Long? = null, val bufferedMs: Long? = null, val droppedFrames: Long? = null,
      val videoCodec: String? = null, val width: Int? = null, val height: Int? = null, val fps: Float? = null,
      val audioCodec: String? = null, val audioChannels: Int? = null, val audioLanguage: String? = null,
  )
  // in PlaybackEngine:
  fun stats(): EngineStats = EngineStats()
  data class StatsSample(val bufferMs: Long?, val bitrateBps: Long?, val stalled: Boolean, val droppedDelta: Long?, val fps: Float?, val reconnected: Boolean)
  class StatsMeter(capacity: Int = 60) {
      val history: List<StatsSample>               // älteste zuerst, max. capacity
      val droppedTotal: Long?                       // Summe der Deltas seit reset, null wenn nie bekannt
      fun sample(raw: EngineStats, progressed: Boolean, shouldPlay: Boolean, reconnectsTotal: Int): StatsSample
      fun reset()
  }
  enum class ConnectionQuality { GOOD, FAIR, POOR, UNKNOWN }
  fun connectionQuality(history: List<StatsSample>, reconnecting: Boolean, connectionLost: Boolean, isLive: Boolean): ConnectionQuality
  // StatsText.kt
  fun hostAndPort(url: String): String               // „Datei auf dem Gerät“ bei file://, „–“ wenn nicht lesbar
  fun streamKind(url: String): String                // „HLS (m3u8)“, „MPEG-TS“, Endung groß, „–“
  fun codecName(raw: String?): String?               // „avc1.64001f“/„video/avc“/„h264“ → „H.264“ usw.
  fun formatBitrate(bps: Long?): String              // „6,1 Mbit/s“, „850 kbit/s“, „–“
  fun formatBuffer(ms: Long?): String                // „12,4 s“, „–“
  fun qualityDots(q: ConnectionQuality): String      // „●●●“ …
  fun qualityLabel(q: ConnectionQuality): String     // „gut“ …
  fun videoLine(s: EngineStats): String              // „H.264 · 1920×1080 · 50 fps“, fehlende Teile weggelassen, alles fehlt → „–“
  fun audioLine(s: EngineStats): String              // „AAC · Stereo · deu“ (1 → „Mono“, 2 → „Stereo“, n → „n Kanäle“)
  ```

- [ ] **Step 1: Failing tests**

```kotlin
// StatsMeterTest
@Test fun bitrateFromByteDelta()                 // bytes 0 → 1_000_000 in 1 Sample → bitrateBps == 8_000_000
@Test fun firstSampleHasNoBitrate()              // erstes Sample: bitrateBps == null
@Test fun counterDropDiscardsThatSecond()        // 5_000_000 → 1_000 → bitrateBps == null; danach 1_000 → 126_000 → 1_000_000
@Test fun missingBytesGiveNoBitrate()            // bytesReceived = null → bitrateBps == null
@Test fun stallsNotCountedBeforeFirstProgress()  // shouldPlay, !progressed ×3 → alle stalled == false; dann progressed, dann !progressed → stalled == true
@Test fun pausedSecondsAreNotStalls()            // nach Fortschritt: shouldPlay = false, !progressed → stalled == false
@Test fun historyKeepsLast60()                   // 75 Samples → history.size == 60, erstes = Sample Nr. 16
@Test fun reconnectedFlagOnCounterIncrease()     // reconnectsTotal 0,0,1,1 → reconnected false,false,true,false
@Test fun droppedDeltaAndTotal()                 // dropped 10, 14, 14 → deltas null(erstes),4,0; droppedTotal == 4
@Test fun resetClearsEverything()                // reset() → history leer, droppedTotal == null, nächstes Sample ohne bitrate
// ConnectionQualityTest  (Helfer: n „gute“ Samples = progressed, buffer null, dropped 0, fps 50)
@Test fun unknownForFirstThreeSamples()          // 2 Samples → UNKNOWN; 3 → GOOD
@Test fun reconnectingOrLostIsPoor()             // 10 gute Samples, reconnecting → POOR; connectionLost → POOR
@Test fun threeStalledSecondsInLastTenIsPoor()   // 20 gut + 3 stalled in den letzten 10 → POOR; 2 stalled → FAIR
@Test fun oldStallOnlyIsFair()                   // 1 stalled vor 30 Samples, sonst gut → FAIR; vor 61 (aus Verlauf) → GOOD
@Test fun reconnectInLast60IsFair()              // ein Sample reconnected = true → FAIR
@Test fun droppedOverTwoPercentIsFair()          // fps 50, droppedDelta 2/s über 10 s (4 %) → FAIR; 1/s (2 %) → GOOD; fps null → Kriterium aus
@Test fun lowBufferFairOnlyForVod()              // buffer 1_500 → isLive=false FAIR, isLive=true GOOD; buffer null → GOOD
// StatsTextTest
@Test fun serverShowsNeverCredentials() {
    assertEquals("prov.example:8080", hostAndPort("http://prov.example:8080/live/user/secret/123.ts"))
    assertEquals("10.0.0.5", hostAndPort("https://user:pw@10.0.0.5/movie/u/p/9.mkv?token=x"))  // IP statt Domain: der Pre-Commit-Hook hält Zugangsdaten vor einer Domain für eine Mail-Adresse
    assertEquals("prov.example:80", hostAndPort("http://prov.example:80/get.php?username=u&password=p"))
    assertEquals("[::1]:8080", hostAndPort("http://[::1]:8080/x.ts"))
    assertEquals("Datei auf dem Gerät", hostAndPort("file:///data/x.mp4"))
    assertEquals("–", hostAndPort("kein url"))
}
@Test fun streamKinds()      // …/1.m3u8 → „HLS (m3u8)“, …/1.ts → „MPEG-TS“, …/9.mkv?x=1 → „MKV“, …/live/u/p/1 → „–“
@Test fun codecNames()       // „avc1.64001f“, „video/avc“, „h264“ → „H.264“; „hvc1“, „hevc“ → „H.265“; „mp4a.40.2“, „audio/mp4a-latm“ → „AAC“; „ac-3“/„a52“ → „AC-3“; „ec-3“ → „E-AC-3“; „xyz“ → „XYZ“; null → null
@Test fun numbersGerman()    // formatBitrate(6_100_000) == „6,1 Mbit/s“, (850_000) == „850 kbit/s“, (null) == „–“; formatBuffer(12_400) == „12,4 s“
@Test fun lines()            // videoLine(EngineStats(videoCodec="h264", width=1920, height=1080, fps=50f)) == „H.264 · 1920×1080 · 50 fps“; videoLine(EngineStats()) == „–“; audioLine(…"mp4a.40.2", 2, "deu") == „AAC · Stereo · deu“
```

- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*player.stats*' 2>&1 | tail -15` → FAIL (unresolved).
- [ ] **Step 3:** Implementieren. `StatsMeter`: merkt vorigen `bytesReceived`/`droppedFrames`/`reconnectsTotal` und ein Flag „hatte schon Fortschritt“; `stalled = shouldPlay && !progressed && hadProgress`. `connectionQuality` mit benannten Konstanten (`POOR_STALLS = 3`, `POOR_WINDOW = 10`, `MIN_SAMPLES = 3`, `DROP_RATIO = 0.02`, `LOW_BUFFER_MS = 2_000L`) über die letzten Samples; Anteil verworfener Bilder = Σ`droppedDelta` / Σ`fps` über alle Samples des Verlaufs mit bekanntem `fps` und `droppedDelta`. `hostAndPort` ohne Regex-Raterei: Schema bis `://`, Authority bis zum ersten `/`, `?` oder `#`, dann alles bis einschließlich `@` entfernen.
- [ ] **Step 4:** Tests grün; `./gradlew detekt 2>&1 | tail -5` ohne neue Befunde.
- [ ] **Step 5:** Commit `Add playback stats meter, connection quality and stats texts`.

---

### Task 2: Abtastung im `PlaybackController` und Einstellung

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/player/PlaybackController.kt`
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/player/stats/PlaybackStats.kt`
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Storage.kt` (bei `autoNextEpisode`, ~Z. 188), `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/state/SettingsStore.kt` (Muster `autoNextEpisode`, Z. 23/97-98)
- Modify: `testing/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/testing/FakePlaybackEngine.kt` (`var stats = EngineStats()`; `override fun stats() = stats`)
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/player/PlaybackControllerStatsTest.kt`, Einstellung in `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/ComfortFeaturesTest.kt` (bestehende Einstellungstests)

**Interfaces:**
- Consumes: alles aus Task 1.
- Produces:
  ```kotlin
  data class PlaybackStats(
      val quality: ConnectionQuality, val history: List<StatsSample>, val reconnects: Int, val stalledSeconds: Int,  // gestockt im Verlauf
      val droppedFrames: Long?, val engine: EngineStats, val streamKind: String, val source: String /* „2/3“ */, val server: String,
  )
  // PlaybackController:
  var stats: PlaybackStats?   (Compose-State, private set; null bis zum ersten Messpunkt und nach Reset)
  var reconnects: Int         (Compose-State, private set) – +1 je reload() aus watch() und je retry(); 0 nach moveTo/selectSource
  suspend fun runStats()      // endlos, 1 Messpunkt/s; UI startet es nur bei (Anzeige an || Fenster offen)
  // AppState/Settings:
  var showConnectionIndicator: Boolean
  ```
  `progressed` je Sekunde = `engine.timeMs != voriges timeMs || monotonic() - engine.lastProgressAt < PROGRESS_FRESH_MS`; `shouldPlay = !engine.paused && !engine.finished && !suspended`. Abtast-Adresse für `streamKind`/`hostAndPort` = `resolveUrl(source)`.

- [ ] **Step 1: Failing tests** (`runTest`, `FakePlaybackEngine`, Muster wie `PlaybackControllerRecoveryTest`)

```kotlin
@Test fun statsNullUntilFirstSample()            // vor runStats: stats == null
@Test fun sampleEverySecond()                    // runStats 5 s virtuell → stats.history.size == 5
@Test fun statsResetOnTitleAndSourceChange()     // 10 Samples, switchTo(1) → stats == null, reconnects == 0, nächstes Sample history.size == 1; ebenso selectSource(1)
@Test fun reconnectsCountReloadAndRetry()        // failed → watch lädt neu → reconnects == 1; connectionLost → retry() → 2
@Test fun serverAndKindFromResolvedSource()      // resolveUrl = { "http://h:8080/live/u/p/1.m3u8" } → server == "h:8080", streamKind == "HLS (m3u8)", source == "1/1"
@Test fun qualityFollowsController()             // nach 5 Samples, reconnecting (failed) → stats.quality == POOR
@Test fun indicatorSettingPersists()             // app.showConnectionIndicator = true → neuer AppState auf demselben kv liest true; Standard false
```

- [ ] **Step 2:** `./gradlew :shared:desktopTest --tests '*PlaybackControllerStatsTest' --tests '*ComfortFeaturesTest' 2>&1 | tail -15` → FAIL.
- [ ] **Step 3:** Implementieren: `private val meter = StatsMeter()`; `meter.reset()`, `stats = null`, `reconnects = 0` in `moveTo` und `selectSource`; `reconnects++` in `reload()`-Pfad von `watch()` und in `retry()` (nicht in `restart()`). Einstellung: `Storage.showConnectionIndicator` (Schlüssel `connIndicator`, `"1"` = an), `SettingsStore` wie `autoNextEpisode`.
- [ ] **Step 4:** Tests grün; `./gradlew :shared:desktopTest detekt 2>&1 | tail -5`.
- [ ] **Step 5:** Commit `Sample playback stats in PlaybackController and add indicator setting`.

---

### Task 3: PC – VLC-Rohwerte, Indikator, Fenster, Einstellung

**Files:**
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/player/VlcPlayer.kt` (`override fun stats()`)
- Create: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/StatsOverlay.kt` (`ConnectionIndicator`, `StatsPanel`, Verlaufskurve)
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/PlayerScreen.kt` (Taste, Menüeintrag, Effekt), `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HomeScreen.kt` (~Z. 1011, Schalter neben „Nächste Folge automatisch abspielen“)
- Test: `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/PlayerLogicTest.kt` (Taste I)

**Interfaces:**
- Consumes: `PlaybackStats`, `StatsText`-Funktionen, `c.stats`, `c.runStats()`, `app.showConnectionIndicator`.
- Produces: Muster für Tasks 4/5 – `@Composable fun ConnectionIndicator(q: ConnectionQuality, onClick: (() -> Unit)?)`, `@Composable fun StatsPanel(s: PlaybackStats?, onClose: () -> Unit)`; Fenster-Zustand lokal `var statsOpen by remember { mutableStateOf(false) }`; Effekt `LaunchedEffect(app.showConnectionIndicator || statsOpen, c.items, c.index) { if (app.showConnectionIndicator || statsOpen) c.runStats() }`.

- [ ] **Step 1: Failing test** in `PlayerLogicTest`: `playerActionFor(Key.I, …)` liefert neue Aktion `PlayerAction.Stats` (live und nicht live).
- [ ] **Step 2:** `./gradlew :desktopApp:test --tests '*PlayerLogicTest' 2>&1 | tail -10` → FAIL.
- [ ] **Step 3:** `VlcPlayer.stats()`: gibt einen im vlcj-Ereignis-Thread (`timeChanged`, höchstens 1×/s) aktualisierten, `@Volatile` gehaltenen Wert zurück – `media().info().statistics(…)` (gelesene Bytes, verlorene Bilder), Video-/Audiospur-Info (Codec, Breite/Höhe, fps, Kanäle, Sprache); `bufferedMs = null`. Taste **I** und Menüeintrag „Statistik“ toggeln `statsOpen`; Esc schließt zuerst das Fenster. Indikator oben rechts, Klick öffnet. Fenster oben links, Zeilen und Reihenfolge exakt wie die Skizze der Spec, Verlauf für Puffer und Datenrate als kleine Kurve über `history`.
- [ ] **Step 4:** `./gradlew :desktopApp:test detekt 2>&1 | tail -5` → grün.
- [ ] **Step 5: Handtest** (Demo-Server, `./gradlew :desktopApp:run`): Einstellung an → Indikator; I öffnet Fenster, Werte laufen; Zappen setzt Verlauf zurück; Server-Zeile ohne `demo/demo`.
- [ ] **Step 6:** Commit `Show connection indicator and stats panel in desktop player`.

---

### Task 4: TV – ExoPlayer-Rohwerte, Indikator, Fenster, Einstellung

**Files:**
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/ExoPlaybackEngine.kt`
- Create: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/StatsOverlay.kt`
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/PlayerScreen.kt`, `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/PlayerOptions.kt` (Eintrag „Statistik“ im `PlayerOptionsPanel`), `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/BrowseScreens.kt` (~Z. 419, `SettingToggle(…, "setting:connIndicator")`)
- Test: `androidApp/src/androidTest/java/de/vincentsberger/iptvplayer/app/ExoPlaybackEngineTest.kt`

**Interfaces:**
- Consumes: wie Task 3; Muster `ConnectionIndicator`/`StatsPanel` aus Task 3 (eigene TV-Fassung, ohne Klick).

- [ ] **Step 1: Failing test** `statsBeforePrepareAreEmptyButSafe`: frische Engine → `stats().bytesReceived == 0L`, `droppedFrames == 0L`, `bufferedMs` ≥ 0 oder null, kein Absturz.
- [ ] **Step 2:** `./gradlew :androidApp:compileDebugAndroidTestKotlin 2>&1 | tail -10` → FAIL.
- [ ] **Step 3:** `ExoPlaybackEngine`: `player.addAnalyticsListener` – `onLoadCompleted` summiert `loadEventInfo.bytesLoaded`, `onDroppedVideoFrames` summiert; beide in `play()` auf 0. `stats()` liest direkt (Main-Thread): `bufferedPosition - currentPosition` (≥ 0), `videoFormat`/`audioFormat` (`codecs` bzw. `sampleMimeType`, `width`, `height`, `frameRate`, `channelCount`, `language`); `release()` entfernt den Analytics-Listener. Menüeintrag „Statistik“ toggelt `statsOpen`, Zurück schließt zuerst das Fenster. Indikator oben rechts ohne Fokus.
- [ ] **Step 4:** `./gradlew :androidApp:compileDebugKotlin :androidApp:compileDebugAndroidTestKotlin detekt 2>&1 | tail -5` → grün. (Gerätetests einmal am Ende, Task 5 Step 4.)
- [ ] **Step 5:** Commit `Show connection indicator and stats panel in TV player`.

---

### Task 5: iPhone – VLCKit-Rohwerte, Indikator, Fenster, Einstellung; Gesamtprüfung

**Files:**
- Modify: `iosApp/src/iosMain/kotlin/de/vincentsberger/iptvplayer/ios/player/VlcController.kt` (`override fun stats()`)
- Create: `iosUi/src/iosMain/kotlin/de/vincentsberger/iptvplayer/ios/ui/player/StatsOverlay.kt`
- Modify: `iosUi/src/iosMain/kotlin/de/vincentsberger/iptvplayer/ios/ui/player/PlayerScreen.kt`, `…/player/PlayerOptions.kt` (Eintrag „Statistik“), `iosUi/src/iosMain/kotlin/de/vincentsberger/iptvplayer/ios/ui/SettingsScreen.kt` (~Z. 90, `Toggle`)

**Interfaces:**
- Consumes: wie Task 3; `PlayerEngine` erbt `stats()` von `PlaybackEngine`.

- [ ] **Step 1:** `VlcController.stats()`: Wert im Delegate-Rückruf (`mediaPlayerTimeChanged`, höchstens 1×/s) aus der `VLCMedia`-Statistik (gelesene Bytes, verlorene Bilder) und der Spurinfo aktualisieren und zurückgeben; `bufferedMs = null`. **Kompiliert nur auf macOS** – sorgfältig gegen die VLCKit-Header lesen, keine Defaults auf Overrides.
- [ ] **Step 2:** Oberfläche: Indikator oben rechts (Antippen öffnet), Eintrag „Statistik“ im Optionen-Blatt; Fenster quer oben links, hoch als Blatt, Schließen per ×; Effekt wie Task 3.
- [ ] **Step 3:** `./gradlew :iosUi:compileKotlinIosArm64 :iosUi:compileKotlinIosSimulatorArm64 detekt 2>&1 | tail -5` → grün.
- [ ] **Step 4: Gesamtprüfung:**
  ```bash
  ./gradlew :shared:desktopTest :desktopApp:test detekt :androidApp:compileDebugKotlin :desktopApp:compileKotlin :iosUi:compileKotlinIosSimulatorArm64 > build/stats-check.log 2>&1; tail -3 build/stats-check.log
  ./gradlew :androidApp:connectedDebugAndroidTest 2>&1 | tail -10   # einmal, Emulator AndroidTV_1080p_API34
  ```
  Erwartet: BUILD SUCCESSFUL, alle Tests grün. `iosApp` prüft `ios.yml` im PR.
- [ ] **Step 5:** Commit `Show connection indicator and stats panel in iOS player`.

## Abschluss

PR `feat/playback-stats` → `master` (deutsch), offen gelassen: Handtests TV-Emulator, iPhone (`.ipa` aus `ios.yml` per iloader). Nicht selbst mergen.
