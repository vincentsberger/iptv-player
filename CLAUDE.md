# IPTV Player (Kotlin Multiplatform)

Werbefreier IPTV-Player (Xtream Codes / M3U) für **Fire TV & Android TV**, **Windows/Linux** und **iPhone** (Vorabversion).
GPLv3, Repo `vincentsberger/iptv-player`. Enthält keine Inhalte; zum Ausprobieren gibt es den Demo-Server.

## Module

| Modul | Inhalt |
|---|---|
| `shared` | Gemeinsame Logik (commonMain) + Plattform-Teile (androidMain/desktopMain/iosMain): Daten, Speicher, Xtream/M3U, Zustand |
| `shared/…/state/` | Stores hinter der Fassade `AppState` (Interface-Delegation): Settings, Library, Epg+Session, MediaInfo, History, Dashboard, Catalog |
| `androidApp` | TV-App: androidx Compose + tv-material, Media3/ExoPlayer, Downloads, Watch Next, MediaSession |
| `desktopApp` | PC-App: Compose Desktop, Wiedergabe über VLC |
| `iosUi` / `iosApp` | iPhone-Oberfläche (Compose MP) / Xcode-Hülle |
| `testing` | Attrappen (`InMemoryKeyValueStore`, `FakeHttp`), `SampleData`, `testAppState()` |

- **Farben:** nur in `shared/…/Palette.kt` („Anthrazit & Bernstein“); `PaletteTest` prüft WCAG-Kontraste. Icons: `python3 tools/brand/make_icons.py [--ios …/icon-1024.png]` liest die Palette. Gestaltungsregeln: `.tastemaker/style-lock.md`.
- **Pläne/Specs:** `docs/superpowers/plans|specs/`.

## Konventionen

- Bezeichner englisch, KDoc/Kommentare **deutsch**. Projekt nutzt bewusst lange Zeilen (bis 160).
- Commits englisch, **ohne Co-Authored-By**. PR-Titel/-Text deutsch. Branches `feat/…`, `fix/…`, `refactor/…` von `master`.
- **Nie selbst mergen** – nur auf ausdrückliche Anweisung (dann `gh pr merge N --merge`). PR-Konflikte per Rebase lösen, danach **nicht pushen** (User pusht mit `--force-with-lease`).
- AppState-API ist von drei Oberflächen genutzt: neue Zustände in den passenden Store, AppState bleibt Fassade. Asynchrones Laden merkt sich das Repository und trägt nur ein, wenn `session.repo === r` (Anbieterwechsel).

## Bauen & Testen

```bash
./gradlew :shared:desktopTest :desktopApp:test      # Logik + PC (schnell, ohne Gerät)
./gradlew :androidApp:compileDebugKotlin :androidApp:compileDebugAndroidTestKotlin :desktopApp:compileKotlin :iosUi:compileKotlinIosSimulatorArm64
./gradlew detekt                                    # statische Analyse; Altlasten in config/detekt/baseline.xml
./gradlew :androidApp:connectedDebugAndroidTest     # TV-UI-Tests, Emulator nötig, ~3 Min – nur einmal am Ende laufen lassen
```

- Lange Gradle-Ausgaben in Datei umleiten und nur Fehler/Tail lesen. Testergebnisse: `shared/build/test-results/desktopTest/`, `androidApp/build/outputs/androidTest-results/`.
- detekt: neue Befunde beheben, nicht einfrieren. Baseline nur für verschobene Altbefunde neu erzeugen (`./gradlew detektBaseline`) und den Diff prüfen.
- CI (`.github/workflows/ci.yml`): Android, Desktop, `lint` (detekt), Windows auf master; `ios.yml` braucht einen macOS-Runner und wartet oft lange in der Schlange.

## Emulator & Demo-Server (manuelle Tests, Screenshots)

- AVDs: `AndroidTV_1080p_API34` (Standard), `AndroidTV_1080p_API28` (Fire-OS-ähnlich). Start: `~/Android/Sdk/emulator/emulator -avd AndroidTV_1080p_API34 -no-audio -no-snapshot-save &`
- Der Snapshot enthält eine **Release-1.2.0** mit anderer Signatur → vor `installDebug` `adb uninstall de.vincentsberger.iptvplayer` (freigegeben). `connectedDebugAndroidTest` deinstalliert die App danach (Login weg).
- Emulator-Uhr steht auf dem Snapshot-Datum (kein Root zum Stellen). Demo-Server sonst mit falschem EPG → mit Zeitversatz starten (Wrapper, der `datetime.now()` um `adb shell date +%s` minus Host-Zeit verschiebt).
- Demo-Server: `cd tools/demo-server && python3 demo_server.py 8099`, Login `demo`/`demo`, Server im Emulator `10.0.2.2:8099`.
- Anmelden ohne Tippen: Handy-Login-Server der App lauscht nur auf `10.0.2.15:8790/<token>` (Token per `uiautomator dump`) → POST per `nc` **im** Emulator (`adb shell "printf 'POST /<token> HTTP/1.0…' | nc 10.0.2.15 8790"`, Felder `server`, `user`, `pass`).
- Navigation per `adb shell input keyevent` (21/22/19/20 = ◀▶▲▼, 23 = OK, 4 = Zurück, `--longpress 23` = OK halten). Seitenleiste wechselt den Bereich erst mit OK. Fokus prüfen statt raten: `uiautomator dump` → Knoten mit `focused="true"`.
- Screenshots fürs README: `docs/screenshots/android-tv/01…07`, 1280×720 (Emulator 1920×1080 verkleinern).

## Release

1. `appVersion` in `gradle.properties` anheben, Commit `Version X.Y.Z` auf master.
2. `git tag vX.Y.Z && git push origin master vX.Y.Z` → `release.yml` baut nur geänderte Plattformen (APK, .deb, .msi, .ipa), prüft das Signaturzertifikat und schreibt die Notizen aus den PRs.
- Android-Updates: App lädt die APK vom GitHub-Release; Android installiert nur mit gleichem Signaturschlüssel (keine eigene Prüfung in der App, bewusst).

## Offene Punkte (Stand 2026-11-24)

- **Texte in Ressourcen / Englisch** – entschieden, zurückgestellt: `interface Strings` + `object De`/`En` in `shared/i18n`, Auswahl per Gerätesprache (`expect fun deviceLanguage()`), Stufen: 1) shared + TV, 2) PC, 3) iPhone, 4) Englisch. ~490 Texte. Vergleiche mit Text (z. B. `title == "Weiterschauen"`) auf Schlüssel umstellen.
- **Diagnose-Log** (lokal, „Diagnose kopieren“ in den Einstellungen) – Arbeitsvorrat: `TooGenericExceptionCaught`/`SwallowedException` in der detekt-Baseline.
- Aus dem AppState-Review zurückgestellt: Guards in `fetchEpg`/`loadSeasons`, mehr Asserts in `AccountSwitchTest`, Benennung `Favorites`↔`HistoryStore`.
