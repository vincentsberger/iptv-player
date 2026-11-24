# Bauen und in Stores veröffentlichen

Diese Anleitung ergänzt das [README](../README.md). GitHub-Releases entstehen automatisch per Tag
(siehe README, Abschnitt „Releases“); hier geht es um Store-Pakete und Store-Einreichungen.

## Pakete bauen
| Ziel | Befehl | Ergebnis |
|---|---|---|
| Fire TV / Amazon (APK) | *Build → Generate Signed App Bundle / APK* (Modul androidApp) | signierte APK |
| Google Play (AAB) | wie oben, „Android App Bundle“ wählen | .aab |
| Windows-Installer zum Testen | `gradlew :desktopApp:packageReleaseMsi` | `desktopApp/build/compose/binaries/main-release/msi/` |
| Windows portabel (ZIP) | `gradlew :desktopApp:createReleaseDistributable`, Ordner zippen | `desktopApp/build/compose/binaries/main-release/app/IPTVPlayer/` |
| Microsoft Store (MSIX) | `powershell -ExecutionPolicy Bypass -File desktopApp\packaging\build-msix.ps1` | `desktopApp/build/msix/IPTVPlayer.msix` |

Vor dem ersten Store-Upload anpassen: `applicationId` in `androidApp/build.gradle.kts`, `vendor` und `upgradeUuid` in
`desktopApp/build.gradle.kts` sowie die „ANPASSEN“-Werte in `desktopApp/packaging/msix/AppxManifest.xml`.

**VLC für Windows (MSI und Store):** `tools/fetch-vlc.sh` lädt die fest eingetragene VLC-Version, prüft die SHA-256-Prüfsumme
und legt `libvlc.dll`, `libvlccore.dll`, die gekürzten `plugins`, die Lizenztexte (GPL, LGPL) und einen Quellcode-Hinweis nach `desktopApp/resources/windows/vlc/`.
Der Release-Workflow macht das automatisch. Neue VLC-Version: `VLC_VERSION` und `VLC_SHA256` im Skript anpassen
(Prüfsumme aus der `.sha256`-Datei unter download.videolan.org).

## Android: Amazon Appstore und Google Play

### Amazon Appstore (Fire TV)
1. Kostenloses Konto in der Amazon Developer Console anlegen.
2. *Add new app → Android* → APK hochladen. Als Zielgeräte die Fire-TV-Modelle auswählen.
3. Pflichtangaben: Icons (512×512 und 114×114), **Fire-TV-Hintergrund 1920×1080**, Screenshots (1920×1080),
   Beschreibung, Altersfreigabe-Fragebogen und **Link zur Datenschutzerklärung** (Vorlage: `DATENSCHUTZ.md`).
4. Einreichen. Die Prüfung dauert meist einige Tage.

### Google Play (Android TV und Google TV)
1. Entwicklerkonto anlegen (einmalig 25 US-$). Neue private Konten müssen vor dem öffentlichen Start einen
   geschlossenen Test mit mehreren Testern über einen Zeitraum durchführen.
2. AAB hochladen und unter *Erweiterte Einstellungen → Formfaktoren* **Android TV** hinzufügen.
   Google prüft TV-Apps zusätzlich auf TV-Qualität (Fernbedienung, Banner, kein Touch-Zwang).
3. Datenschutz-Link, Formular zur Datensicherheit („keine Daten erhoben/geteilt“) und TV-Screenshots ausfüllen.

### Damit die App die Store-Prüfung besteht
IPTV-Player werden besonders genau geprüft und werden oft wegen Urheberrechtsbeschwerden entfernt.
- **Keine Inhalte mitliefern:** keine vorinstallierten Playlists, Sender oder Test-Zugänge in der App.
- **Keine Anbieter bewerben** oder verlinken, keine Senderlogos bekannter Marken in Screenshots oder in der Beschreibung.
- **Screenshots** nur mit eigenen oder frei nutzbaren Test-Streams erstellen (z. B. eigene Videos oder freie Demo-Streams).
- In der Beschreibung klar sagen: *„Die App enthält keine Inhalte. Du benötigst ein Abonnement eines berechtigten Anbieters.“*
  (Der Hinweis steht bereits auf dem Login-Bildschirm.)
- Für die Prüfer im Kommentarfeld einen **legalen Test-Zugang** (z. B. eine eigene M3U mit freien Streams) angeben,
  sonst können sie die App nicht testen.
- Name und Icon frei wählen, nicht „Smarters“, „TiviMate“ o. Ä. verwenden (Markenrecht).

## Windows: Microsoft Store
1. **Konto anlegen:** auf storedeveloper.microsoft.com → *Get started for free* → *Individual developer*.
   Für Einzelpersonen ist das kostenlos; du bestätigst deine Identität mit Ausweis und Selfie.
2. **App-Namen reservieren:** In Partner Center unter *Apps und Spiele → Neues Produkt → MSIX- oder PWA-App*
   den Namen eintragen, z. B. „IPTV Player“. Er muss im Store einmalig sein.
3. **Identität übernehmen:** unter *Produktverwaltung → Produktidentität* die Werte
   `Package/Identity/Name`, `Package/Identity/Publisher` und `PublisherDisplayName` kopieren und in
   `AppxManifest.xml` eintragen. Danach das MSIX neu bauen.
4. **Einreichung ausfüllen:**
   - *Preise und Verfügbarkeit:* kostenlos, Märkte wählen
   - *Eigenschaften:* Kategorie „Unterhaltung“, Link zur Datenschutzerklärung (Vorlage aus dem Android-Projekt)
   - *Altersfreigaben:* Fragebogen ausfüllen
   - *Pakete:* `IPTVPlayer.msix` hochladen
   - *Store-Einträge:* Beschreibung, mindestens 1 Screenshot (1920×1080 empfohlen), Logo
   - *Anmerkungen für die Zertifizierung:* einen **legalen Test-Zugang** angeben (z. B. eine eigene M3U mit frei
     nutzbaren Streams) und erwähnen: „Die App nutzt runFullTrust, weil die Wiedergabe über libVLC läuft.“
5. **Einreichen.** Die Zertifizierung dauert meist einige Tage.

Bei MSIX signiert Microsoft das Paket selbst. Ein MSI- oder EXE-Installer bräuchte ein eigenes, kostenpflichtiges Code-Signing-Zertifikat.

## Hinweis: Fire TV mit Vega OS
Neuere Amazon-Geräte mit **Vega OS** (z. B. Fire TV Stick 4K Select) können keine Android-Apps ausführen.
Diese App läuft auf allen Fire-TV-Geräten mit Fire OS (Android-basiert) sowie auf Android TV und Google TV.
Für Vega OS bräuchte es eine eigene Version mit Amazons React-Native-SDK.
