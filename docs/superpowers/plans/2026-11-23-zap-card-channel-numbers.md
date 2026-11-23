# Zapp-Karte und Anbieter-Sendernummern – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bei jedem Senderwechsel eine Karte oben links (Nummer, Logo, Name, Sendung) und überall die Anbieter-Sendernummer statt der Listenposition.

**Architecture:** `Item.number` aus Xtream `num` / M3U `tvg-chno`. In `shared`: `channelNumberOf` (Anzeige-Nummer mit Fallback), `ChannelNumberInput.find` (Ziffernwahl über Anbieter-Nummer) und `ZapCard` (Zeitlogik). Beide Player zeichnen die Karte selbst und zeigen die Nummer im Kopf; Listen zeigen sie vor dem Namen.

**Tech Stack:** Kotlin Multiplatform, Compose (Android TV / Desktop), Media3, vlcj, kotlin.test, Compose UI-Tests.

**Spec:** `docs/superpowers/specs/2026-11-23-zap-card-channel-numbers-design.md`

## Global Constraints

- Karte blendet **4 s nach dem ersten Bild** aus (`ZapCard.HIDE_AFTER_MS = 4_000L`); bleibt während des Ladens; nicht sichtbar, solange die Info-Leiste sichtbar ist.
- Nur für Live-Sender (nicht Filme/Serien).
- Nummer: nur ganze Zahlen > 0; Anzeige `number ?: Position (1-basiert)`.
- Ziffernwahl sucht in `section(Section.LIVE).items` (Länderfilter gilt); bevorzugt einen Treffer in der aktuellen Liste; ohne Nummern beim Anbieter: Position in der aktuellen Liste (bisheriges Verhalten); nicht gefunden → Hinweis „Sender <n> nicht gefunden“.
- Optik: `Surface1` 92 % Deckkraft, linker Rand 3 dp in `Signal`, Nummer in `Mono`; Position oben links.

## Review Focus

- **Zwei Sender mit derselben Nummer** (Anbieter vergibt doppelt) → der erste in Anbieter-Reihenfolge, kein Absturz. (Test in Task 1)
- **Schnelles Zappen ▲▲▲** → Karte zeigt jeweils den neuen Sender, der Ausblend-Countdown beginnt erst mit dem ersten Bild des letzten Senders. (Test in Task 1)
- **Erstes Bild kommt nie (Stream tot)** → Karte bleibt, bis der Fehlerdialog erscheint; dann ausgeblendet (Fehlerdialog = Info-Overlay). (Test in Task 1 über `hide()`)
- **Nummer gewählt, die nur außerhalb der aktuellen Kategorie existiert** → Player wechselt auf die Liste aller Sender. (Test in Task 1)
- **M3U ohne `tvg-chno`** → Positionen wie bisher. (Test in Task 1)

---

### Task 1: Nummer, Ziffernwahl und Zeitlogik in `shared`

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/Models.kt` (`Item.number`)
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/XtreamClient.kt` (LIVE: `num`)
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/data/M3uParser.kt` (`tvg-chno`)
- Modify: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/ChannelNumberInput.kt`
- Create: `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/ZapCard.kt`
- Test: `shared/src/commonTest/kotlin/de/vincentsberger/iptvplayer/shared/ZapCardTest.kt`, `.../ChannelNumberTest.kt`

**Interfaces:**
- Produces: `Item(..., val number: Int? = null)` (letzter Parameter, Default null)
- Produces: `fun channelNumberOf(item: Item, list: List<Item>): Int` – `item.number ?: (list.indexOfFirst { it.url == item.url } + 1)`
- Produces: `data class ChannelPick(val list: List<Item>, val index: Int)` und `fun ChannelNumberInput.Companion.find(number: Int, current: List<Item>, all: List<Item>): ChannelPick?`
- Produces: `class ZapCard(private val clock: () -> Long)` mit `val channel: Item?` (State; nicht null = Karte soll gezeigt werden), `fun onChannelChanged(item: Item)`, `fun onFirstFrame()`, `fun tick()` (blendet aus, wenn seit erstem Bild ≥ `HIDE_AFTER_MS`), `fun hide()`; `companion object { const val HIDE_AFTER_MS = 4_000L }`

- [ ] **Step 1: Failing Tests**
  - `ChannelNumberTest.xtreamNumIsParsed` – `get_live_streams` mit `"num":101` und `"num":"0"` → `101`, `null`
  - `ChannelNumberTest.m3uTvgChnoIsParsed` – `#EXTINF:-1 tvg-chno="7",ARD` → `7`; ohne Attribut → null
  - `ChannelNumberTest.displayNumberFallsBackToPosition` – `channelNumberOf` mit/ohne `number`
  - `ChannelNumberTest.findPrefersCurrentListThenAll` – Nummer in `current` → Pick mit `current`; nur in `all` → Pick mit `all`; nirgends → null
  - `ChannelNumberTest.duplicateNumbersTakeFirst`
  - `ChannelNumberTest.withoutProviderNumbersUsesPosition` – keine `number` in `all` → Position in `current`
  - `ZapCardTest.showsUntilFourSecondsAfterFirstFrame` – Wechsel → sichtbar; `tick()` nach 10 s ohne erstes Bild → noch sichtbar; `onFirstFrame()`, +3,9 s → sichtbar, +4 s → null
  - `ZapCardTest.fastZappingRestartsWithNewChannel` – Wechsel A, erstes Bild, +3 s Wechsel B → Karte zeigt B, Countdown erst nach B's erstem Bild
  - `ZapCardTest.hideClearsImmediately`
- [ ] **Step 2:** Run `./gradlew :shared:desktopTest --tests '*ChannelNumberTest*' --tests '*ZapCardTest*'` → FAIL (`Unresolved reference`)
- [ ] **Step 3:** Implementieren (`num`/`tvg-chno` per `toIntOrNull()?.takeIf { it > 0 }`; bestehendes `indexFor` bleibt für den Fallback).
- [ ] **Step 4:** Run `./gradlew :shared:desktopTest` → alle grün
- [ ] **Step 5:** Commit `add provider channel numbers, number lookup and zap card timing`

### Task 2: Desktop

**Files:**
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/PlayerScreen.kt` (Kopf ~Z. 345, Zifferneingabe ~Z. 226, Overlay)
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HomeScreen.kt` (Senderzeilen in `LiveScreen`)
- Create: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/ZapCardView.kt`
- Test: `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/ZapCardViewTest.kt`, `HomeScreenTest.kt`

**Interfaces:** Consumes `Item.number`, `channelNumberOf`, `ChannelNumberInput.find`, `ChannelPick`, `ZapCard` (Task 1)

- [ ] **Step 1: Failing Tests**
  - `ZapCardViewTest.showsNumberNameAndProgram` – `ZapCardView(item(number = 101, name = "Das Erste HD"), number = 101, now = EpgEntry("Tagesschau", "20:00", "20:15"), progress = 0.6f)` → Texte „101“, „Das Erste HD“, „Tagesschau · bis 20:15“
  - `ZapCardViewTest.withoutEpgOnlyNameLine` – `now = null` → kein „bis“
  - `HomeScreenTest.liveListShowsProviderNumbers` – `get_live_streams` mit `num` → Text „101“ in der Senderzeile
- [ ] **Step 2:** Run `./gradlew :desktopApp:test --tests '*ZapCardViewTest*' --tests '*HomeScreenTest.liveList*'` → FAIL
- [ ] **Step 3:** `ZapCardView(item, number, now: EpgEntry?, progress: Float, modifier)` nach Spec-Skizze (wiederverwendet `ChannelLogo`, `ProgressBar`). PlayerScreen: `remember { ZapCard(System::currentTimeMillis) }`; `onChannelChanged` bei jedem Wechsel des Live-Senders (inkl. Start); erstes Bild = erster `player.frame != null` nach dem Wechsel; `LaunchedEffect(zap.channel) { while (zap.channel != null) { delay(250); zap.tick() } }`; anzeigen, wenn `zap.channel != null && !showUi && !connectionLost`; bei `connectionLost` → `zap.hide()`. Kopf: `channelNumberOf(item, items)` statt „x / y“ (Live), Episoden behalten „x / y“. Zifferneingabe: `ChannelNumberInput.find(n, items, app.section(Section.LIVE).items)` → bei anderer Liste `items`/`index` ersetzen (wie beim Senderlisten-Wechsel); null → `Feedback`/Hinweis „Sender <n> nicht gefunden“ (vorhandenen Hinweis-Mechanismus nutzen, sonst 2 s Text an der Stelle der Zifferneingabe). Senderzeilen: Nummer klein in `Mono`, `Muted` vor dem Namen.
- [ ] **Step 4:** Run `./gradlew :desktopApp:test` → grün
- [ ] **Step 5:** Commit `show zap card and provider channel numbers on desktop`

### Task 3: Android TV

**Files:**
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/PlayerScreen.kt` (Kopf ~Z. 470, Zifferneingabe ~Z. 282, Overlay)
- Modify: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/LiveTvScreen.kt` (Senderzeilen, Zifferneingabe ~Z. 129), `.../ui/ChannelGuide.kt` (Senderzeilen)
- Create: `androidApp/src/main/java/de/vincentsberger/iptvplayer/app/ui/ZapCardView.kt`
- Test: `androidApp/src/androidTest/java/de/vincentsberger/iptvplayer/app/ZapCardViewTest.kt`, `HomeScreenTest.kt`

**Interfaces:** wie Task 2

- [ ] **Step 1: Tests** analog Task 2 (`ZapCardViewTest` mit TV-Schriftgrößen; `HomeScreenTest.liveListShowsProviderNumbers`; `HomeScreenTest.typingAProviderNumberStartsThatChannel` – `num` 101/102/103, Eingabe „102“ → Player mit dem Sender `num` 102)
- [ ] **Step 2:** Run (Emulator `AndroidTV_1080p_API28`) die neuen Tests → FAIL
- [ ] **Step 3:** Wie Desktop. Erstes Bild: `Player.Listener.onRenderedFirstFrame` → `zap.onFirstFrame()`; anzeigen, wenn `zap.channel != null && !showInfo && !guideOpen && !optionsOpen && !connectionLost`. LiveTvScreen-Zifferneingabe ebenfalls über `find` (öffnet den Player mit `pick.list`, `pick.index`).
- [ ] **Step 4:** Run `./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest` → grün (bestehender Test `typingAChannelNumberInTheListStartsThatChannel` gilt mit SampleData ohne `num` weiter über Position)
- [ ] **Step 5:** Commit `show zap card and provider channel numbers on Android TV`
