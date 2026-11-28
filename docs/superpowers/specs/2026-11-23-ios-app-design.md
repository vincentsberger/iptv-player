# iOS-App (iPhone)

**Stand:** 23.11.2026 · Größe: Projekt · Neues Modul `iosApp`, `shared` bekommt iOS-Targets.  
**Plattformen:** iPhone 🟡 Vorabversion (Lücken: `docs/PLATTFORMEN.md`) · TV ➖ · PC ➖

## Absicht
Der Player soll auch auf dem iPhone laufen – **unterwegs und zu Hause**, mit dem **Funktionsumfang, den Fire TV und PC
heute haben**. Zuerst nur auf Vincents eigenem iPhone, später auch bei Freunden.

## Ziele
- iPhone-App mit Touch-Oberfläche, die `shared` (AppState, Xtream, M3U, EPG, Favoriten, Verlauf, Suche, Cache) unverändert nutzt.
- Gebaut ausschließlich in GitHub Actions (macOS-Runner) – lokal gibt es nur Linux, keinen Mac.
- Installation per USB von Linux aus.
- Freigabe an Freunde ohne Umbau der App: zuerst über SideStore, später optional TestFlight / AltStore PAL.

## Nicht-Ziele (erste Version)
- Kein iPad-Layout, kein tvOS, kein macOS.
- Kein Bild-in-Bild, kein AirPlay, keine Steuerung im Sperrbildschirm – ausdrücklich nachreichbar.
- Kein App Store. Kein Selbst-Update (unter iOS nicht erlaubt), kein QR-Login (das iPhone ist selbst das Eingabegerät).

## Anti-Ziele
- Kein zweiter Logik-Stand: Was in `shared` steht, wird nicht in der iOS-App nachgebaut. Fehlt etwas, wandert es nach `shared`
  (entschieden, auch wenn bestehende Apps dafür umgebaut werden) – betrifft die Download-Regeln aus
  `androidApp/.../download/Downloads.kt` (ladbare Titel, einer gleichzeitig, ≥ 1 GB frei, Startergebnisse, Offline-Erkennung) und
  die Sprachwahl der Spuren aus `desktopApp/.../player/Tracks.kt`. Umbau jeweils mit Regressionstests.
- Die bestehenden Apps und ihre CI dürfen nicht langsamer oder instabiler werden (iOS-Jobs laufen getrennt).
- Keine Swift-Schicht, die nur in der CI kompiliert – Fehlersuche wäre ohne Mac zu langsam.

## Rahmenbedingungen
- Entwicklung unter Linux. Kotlin/Native-Apple-Targets bauen und linken nur auf macOS → jeder iOS-Build und jeder Test
  auf iOS läuft in GitHub Actions. Logik bleibt unter Linux testbar (`:shared:desktopTest`).
- Kein iOS-Simulator lokal; getestet wird auf dem echten iPhone. Diagnose über `idevicesyslog` / `idevicecrashreport`
  (libimobiledevice).
- Stand heute: Kotlin 2.1.0, Compose Multiplatform 1.7.3, JDK 17. iOS ist in Compose Multiplatform erst ab 1.8 stabil,
  Kotlin 2.1.0 passt nicht zum Xcode der aktuellen Runner → **vorab Update für alle Apps** (Etappe 0, entschieden).
- Zielgerät: **iPhone 11 mit iOS 27.** Mindest-iOS der App: 16 (Builder-Call). Ob MobileVLCKit 3 auf iOS 27 sauber läuft,
  ist ungeprüft → Etappe 1 enthält einen Wiedergabe-Test auf dem Gerät.
- IPTV-Streams sind oft `http://` und rohes MPEG-TS bzw. MKV → App Transport Security braucht eine Ausnahme
  (`NSAllowsArbitraryLoads`), AVPlayer reicht nicht.

## Ansätze (Oberfläche)
- **1. Compose Multiplatform, neue Touch-Oberfläche in Kotlin – gewählt.** Gleiches Muster wie `androidApp`/`desktopApp`,
  eine Sprache, AppState nutzt bereits die Compose-Runtime. Native Teile (Player, Keychain, Hintergrund-Downloads) per
  Kotlin↔Objective-C-Interop.
- 2. SwiftUI + `shared` als Framework: am nativsten, aber Swift ohne lokalen Compiler → jeder Fehler ein CI-Lauf. Verworfen.
- 3. PC-Oberfläche mit iOS teilen: Maus/Querformat vs. Daumen/Hochformat – es bliebe kaum etwas gemeinsam. Verworfen.

## Ansätze (Player)
- **MobileVLCKit 3 – gewählt.** Spielt alle Formate wie die PC-App (vlcj), stabil, Objective-C → direkt aus Kotlin per
  `cinterop` nutzbar.
- VLCKit 4: bringt Bild-in-Bild, war lange Vorabversion. Kandidat, wenn Bild-in-Bild nachgereicht wird.
- AVPlayer: nur HLS/MP4; `.ts`-Live-Streams, MKV-Filme scheitern. Später evtl. als Zweitplayer für HLS (PiP/AirPlay).
- KSPlayer / MPVKit: Swift → Brückenschicht nötig. Verworfen.

## Verteilung und Signierung
- **Phase 1 (jetzt, kostenlose Apple-ID):** CI erzeugt eine **unsignierte `.ipa`** als Workflow-Artefakt. Vincent signiert und
  installiert sie unter Linux mit einem Sideloading-Tool (Kandidaten: Plume Impactor, AltServer-Linux – Auswahl beim Bau,
  dokumentiert in `docs/IOS.md`). Gültig 7 Tage, danach neu installieren. Auf dem iPhone Entwicklermodus aktivieren.
- **Phase 1b (Freunde, ohne Developer Account) – entschieden: SideStore.** Jeder Freund installiert einmalig SideStore
  (Kopplung am Rechner) und signiert mit seiner eigenen kostenlosen Apple-ID; SideStore erneuert die App alle 7 Tage auf dem
  Gerät. Wir veröffentlichen eine **Quelle** (AltStore-Source-JSON: Name, Bundle-ID, Version, Datum, Größe, `.ipa`-URL aus dem
  GitHub-Release), erzeugt im Release-Workflow und z. B. über GitHub Pages oder als Release-Datei mit festem Namen erreichbar.
  Vincent selbst nutzt dasselbe (Builder-Call) – dann entfällt das wöchentliche Neuinstallieren per USB. Anleitung in `docs/IOS.md`.
  Ob SideStore unter iOS 27 läuft, wird in Etappe 1 geprüft; sonst bleibt das Linux-Sideloading aus Phase 1.
- **Phase 2 (später, Developer Account 99 €/Jahr, ohne Mac):** Zertifikat per `openssl`-CSR, App-ID/Gerät/Profil im
  Developer-Portal, beides als GitHub-Secrets; CI signiert. Installation per `ideviceinstaller`, Freunde über TestFlight
  (Upload per App-Store-Connect-API-Key aus der CI).
- Bundle-ID: `de.vincentsberger.iptvplayer` (wie Android-Namespace-Stamm).
- Beim Wechsel Phase 1 → 2 darf die App neu eingerichtet werden (neuer Login, Verlauf/Downloads weg): Sideloading-Tools
  ändern Bundle-ID/Team-Präfix. Keine Migration, keine Keychain-Access-Group.

## Design

### Module
- `shared`: neue Targets `iosArm64` (Gerät) und `iosSimulatorArm64` (für Tests in der CI); `iosMain` mit `actual`s.
- `testing`: dieselben iOS-Targets, damit `commonTest` auch auf iOS läuft.
- `iosApp`: Kotlin-Modul (Compose Multiplatform, `ComposeUIViewController`) + minimales Xcode-Projekt bzw. Xcode-freier
  Bundle-Bau – Wahl beim Bau, Hauptsache in der CI reproduzierbar und ohne händische Xcode-Klicks.

### Plattformschicht `shared/iosMain`
- `httpGetText` / `httpReadLines`: Ktor-Client mit Darwin-Engine **oder** direkt NSURLSession; `httpReadLines` muss große
  M3U-Listen zeilenweise verarbeiten, ohne sie komplett im Speicher zu halten. User-Agent `USER_AGENT`.
- `currentTimeMillis`: `NSDate`.
- `KeyValueStore`: Datei im Application-Support-Verzeichnis (analog `FileKeyValueStore`); **Zugangsdaten in der Keychain**
  (Gegenstück zur DPAPI-Verschlüsselung unter Windows).
- `PlaylistCacheStore`: Datei-Cache im Caches-Verzeichnis (analog `FilePlaylistCache`).
- Dateizugriff über `kotlinx-io` oder Foundation – Builder-Wahl.

### Oberfläche (`iosApp`)
Bildschirme wie die bestehenden Apps, gesteuert über `AppState`:
Login (Xtream / M3U), Übersicht nach dem Laden, Startseite (Weiterschauen, Neu der letzten 7 Tage, Abo-Info, zuletzt
gesehen, Downloads, Favoriten), Live TV (Kategorien, Flaggen, Jetzt/Danach, Fortschritt), Filme, Serien mit Staffeln,
Suche, Downloads, Einstellungen (Anbieter, Länderfilter, ausgeblendete Kategorien, Sortierung, Schlaf-Timer, Downloads
pausieren, …).

- Navigation: Tab-Leiste unten (Start · Live TV · Filme · Serien · Suche), Einstellungen über die Startseite.
- Listen im Hochformat; Player dreht automatisch ins Querformat, Hochformat zeigt Bild oben + Senderliste darunter.
- **Player-Gesten** (Ersatz für Fernbedienung/Tastatur):
  - Tippen: Bedienelemente ein/aus
  - Wischen hoch/runter (Live): Sender wechseln
  - Wischen vom linken Rand: Senderliste über dem laufenden Bild
  - Doppeltipp links/rechts (Film/Folge): 10 s zurück / 30 s vor; Ziehen auf der Leiste: spulen
  - Knöpfe: Ton & Untertitel, Bildformat, Schlaf-Timer, letzter Sender
- Bestehende Funktionen aus `shared` werden genutzt, nicht neu gebaut: Zap-Karte, Pause-Info, Medien-Badges, Seek-Ziel,
  Stream-Wiederherstellung, Senderwahl-Logik.
- Wegfall: Tastenleiste, Zifferntasten, QR-Login, Selbst-Update (Hinweis in den Einstellungen: „Updates über neue Installation“).

### Player
- MobileVLCKit 3 **dynamisch als eingebettetes `.framework`** (LGPL: austauschbar) – per CocoaPods oder heruntergeladen und
  prüfsummengeprüft (analog `tools/fetch-vlc.sh`); `cinterop` auf die Objective-C-Header. `VLCMediaPlayer` in einer `UIView`, eingebettet per `UIKitView`.
- Funktionsumfang wie `desktopApp/player/VlcPlayer.kt` + `Tracks.kt`: Ton-/Untertitelspuren, bevorzugte Sprache,
  Bildformat, Position/Fortsetzen, Fehler-/Hänger-Erkennung für die Stream-Wiederherstellung.
- Audio-Session `.playback`, damit Ton auch im Stummmodus läuft. Bildschirm bleibt während der Wiedergabe an.
- LGPL-Hinweis und Quellcode-Verweis für VLCKit in der App (wie `tools/vlc-licenses` beim PC).

### Downloads
- Filme und ganze Staffeln, offline abspielbar über VLC aus der lokalen Datei.
- `NSURLSession` mit Hintergrund-Konfiguration: Downloads laufen weiter, wenn die App im Hintergrund ist; Zuordnung der
  Zusatzdaten über das vorhandene Model-JSON (`Models.kt`). Einstellung „Downloads pausieren während Wiedergabe“ wird beachtet.
- Ablage im Application-Support-Verzeichnis, vom iCloud-Backup ausgeschlossen.

### CI
- Neuer Workflow-Job auf `macos-latest` (in `ci.yml` bei Push, nur wenn sich `shared/`, `testing/`, `iosApp/` oder der
  Workflow ändern; im Release-Workflow bei Tags): `:shared:iosSimulatorArm64Test`, App bauen, **unsignierte `.ipa`** als
  Artefakt hochladen. In Releases als zusätzliche Datei anhängen – **ein fehlschlagender iOS-Build blockiert das Release
  nicht** (Release erscheint dann ohne `.ipa`).
- macOS-Minuten zählen 10-fach gegen das kostenlose Kontingent privater Repos; das Repo ist öffentlich → kostenlos.

## Etappen
Jede Etappe (ab 1) endet mit einer installierbaren `.ipa`, die Vincent auf dem iPhone prüft.
- **0. Versions-Update:** Kotlin, Compose Multiplatform, AGP auf aktuelle, zueinander passende Versionen – alle Apps; Erfolg:
  bestehende Tests grün, Fire-TV- und PC-App manuell kurz geprüft. Eigener Commit, vor allem iOS-Code.
- **1. Gerüst:** iOS-Targets in `shared`/`testing`, `iosMain`-Actuals, `iosApp` mit Platzhalter-Bildschirm, CI-Job, Anleitung
  `docs/IOS.md` (Sideloading unter Linux). Erfolg: App startet auf dem iPhone, `commonTest` grün auf iOS, ein Test-Stream
  (`.ts` und `.m3u8`, z. B. vom Demo-Server) spielt über MobileVLCKit – sonst Player-Wahl neu bewerten (VLCKit 4).
- **2. Login + Live TV:** Login, Laden/Übersicht, Startseite, Live TV, Player mit Gesten, Senderliste, Zap-Karte.
- **3. Filme + Serien:** Kataloge, Detail, Weiterschauen, nächste Folge, Spuren/Untertitel/Bildformat, Suche. Vorher: Sprachwahl
  aus `Tracks.kt` nach `shared`, PC-App darauf umstellen.
- **4. Downloads + Rest:** (inkl. SideStore-Quelle im Release-Workflow) Vorher: Download-Regeln nach `shared`, Fire-TV-App darauf umstellen. Dann Downloads, Einstellungen vollständig, Mehrere Anbieter, Schlaf-Timer, Lizenzhinweise.
- **Später:** Developer Account + TestFlight, Bild-in-Bild (VLCKit 4 oder AVPlayer für HLS), AirPlay, Sperrbildschirm, iPad.

## Tests
- Logik: vorhandene `commonTest`-Suite läuft zusätzlich auf iOS (Simulator in der CI).
- Neue `iosMain`-Actuals (HTTP, Speicher, Cache) mit eigenen Tests in `iosTest`, analog `FileKeyValueStoreTest` /
  `FilePlaylistCacheTest`.
- Oberfläche: reine Hilfsfunktionen (z. B. Gesten → Aktion) testbar halten; UI-Tests auf Gerät sind nicht vorgesehen.

## Dem Builder überlassen
Ktor vs. NSURLSession, `kotlinx-io` vs. Foundation, CocoaPods vs. heruntergeladenes Framework, Xcode-Projekt vs.
Xcode-freier Bundle-Bau, konkrete Sideloading-Empfehlung für Linux, Einzelheiten des Layouts.
