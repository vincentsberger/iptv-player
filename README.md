# TV Player – IPTV-Player für Fire TV, Android TV und Windows

Ein Projekt, zwei Apps: Datenlogik und App-Zustand werden **einmal** geschrieben und von beiden Apps genutzt (Kotlin Multiplatform).
Nur die Oberfläche ist getrennt, weil die Bedienung mit Fernbedienung und Maus unterschiedlich ist.

```
TVPlayer/
  shared/        gemeinsam: Xtream Codes, M3U, Serien, EPG, Favoriten, Verlauf, Suche, Navigation (AppState)
    commonMain/    Code für beide Plattformen
    androidMain/   HTTP und Speicher (SharedPreferences) für Android
    desktopMain/   HTTP und Speicher (JSON-Datei in %APPDATA%) für den PC
  androidApp/    Fire TV / Android TV: Oberfläche für die Fernbedienung, Player mit Media3
  desktopApp/    Windows: Oberfläche für Maus und Tastatur, Player mit VLC, MSIX-Paketierung
```

Eine Änderung in `shared/` (z. B. an der Xtream-Anbindung) wirkt automatisch in beiden Apps.

## Funktionen (beide Apps)
- Anmeldung per **Xtream Codes** oder **M3U-Playlist**; die Anmeldung bleibt gespeichert
- **Live TV** mit Kategorien, „Jetzt/Danach“ und Sendungsfortschritt; außerdem „Zuletzt gesehen“
- **Filme** und **Serien** (mit Staffeln); **Weiterschauen** an der letzten Stelle, die nächste Folge startet automatisch
- **Favoriten**, **Suche** über alle Bereiche, **Einstellungen** (neu laden, Verlauf löschen, abmelden)

## Entwickeln
Einfachster Weg: **Android Studio** (aktuell) öffnen und den Hauptordner `TVPlayer` laden. Dort laufen beide Apps.
JDK 17 wird benötigt; Android Studio bringt es mit.
Gradle 8.11 startet nicht mit neueren JDKs wie 25 und bricht dann nur mit der Versionsnummer als Fehlermeldung ab.
Ist ein neueres JDK der Systemstandard, `org.gradle.java.home=/pfad/zu/jdk-17` in `~/.gradle/gradle.properties` eintragen
oder einmalig `JAVA_HOME=/pfad/zu/jdk-17 ./gradlew …` aufrufen.

**Fire TV / Android TV starten**
- Im Emulator: *Device Manager → + → TV → Android TV (1080p)*, dann oben die Konfiguration **androidApp** wählen und auf ▶ klicken.
  Die Pfeiltasten entsprechen dem Steuerkreuz, Enter ist OK, Esc ist Zurück.
  Reagiert das Fenster nicht auf die Tastatur: In der AVD-`config.ini` `hw.keyboard=yes` setzen und den Emulator neu starten.
- Auf dem Fire TV: ADB-Debugging einschalten (*Einstellungen → Mein Fire TV → Entwickleroptionen*), dann `adb connect <IP>` und ▶.

**Windows-App starten**
- VLC (64-Bit) installieren, dann im Terminal: `gradlew :desktopApp:run`
  (oder im Gradle-Fenster *desktopApp → Tasks → compose desktop → run*).

**Bedienung PC:** Leertaste Pause · ← → spulen · ↑ ↓ Sender/Folge · F Vollbild · M Ton aus · Esc zurück

## Testen
| Was | Befehl | Wo |
|---|---|---|
| Gemeinsame Logik (M3U, Xtream, Repository, Speicher, AppState) | `./gradlew :shared:desktopTest` | `shared/src/commonTest` |
| Windows-Oberfläche (Login, Live TV, Filme/Serien, Suche, Einstellungen, Player-Tasten) | `./gradlew :desktopApp:test` | `desktopApp/src/test`, läuft ohne sichtbares Fenster |
| TV-Oberfläche inkl. Fernbedienung (D-Pad, Fokus) | `./gradlew :androidApp:connectedDebugAndroidTest` | `androidApp/src/androidTest`, braucht laufenden Emulator oder Gerät |

Alle Tests laufen ohne Netzwerk: Das Modul `testing/` liefert Attrappen für HTTP und Speicher sowie einen
Beispiel-Server (`SampleData`, `testAppState()`). Die Wiedergabe selbst (VLC, ExoPlayer) wird nicht getestet,
wohl aber die Tastenbelegung des Players (`playerActionFor`).

Berichte: `*/build/reports/tests/…/index.html` bzw. `androidApp/build/reports/androidTests/connected/`

## Bauen
| Ziel | Befehl | Ergebnis |
|---|---|---|
| Fire TV / Amazon (APK) | *Build → Generate Signed App Bundle / APK* (Modul androidApp) | signierte APK |
| Google Play (AAB) | wie oben, „Android App Bundle“ wählen | .aab |
| Windows-Installer zum Testen | `gradlew :desktopApp:packageReleaseMsi` | `desktopApp/build/compose/binaries/main-release/msi/` |
| Microsoft Store (MSIX) | `powershell -ExecutionPolicy Bypass -File desktopApp\packaging\build-msix.ps1` | `desktopApp/build/msix/TVPlayer.msix` |

Vor dem ersten Store-Upload anpassen: `applicationId` in `androidApp/build.gradle.kts`, `vendor` und `upgradeUuid` in
`desktopApp/build.gradle.kts` sowie die „ANPASSEN“-Werte in `desktopApp/packaging/msix/AppxManifest.xml`.

**VLC für die Windows-Store-Version:** `libvlc.dll`, `libvlccore.dll` und den Ordner `plugins` aus der VLC-ZIP-Version nach
`desktopApp/resources/windows/vlc/` kopieren (Details: `desktopApp/resources/windows/VLC-HIER-ABLEGEN.txt`).

## Veröffentlichen

### Android: Amazon Appstore und Google Play

#### Amazon Appstore (Fire TV)
1. Kostenloses Konto in der Amazon Developer Console anlegen.
2. *Add new app → Android* → APK hochladen. Als Zielgeräte die Fire-TV-Modelle auswählen.
3. Pflichtangaben: Icons (512×512 und 114×114), **Fire-TV-Hintergrund 1920×1080**, Screenshots (1920×1080),
   Beschreibung, Altersfreigabe-Fragebogen und **Link zur Datenschutzerklärung** (Vorlage: `DATENSCHUTZ.md`).
4. Einreichen. Die Prüfung dauert meist einige Tage.

#### Google Play (Android TV und Google TV)
1. Entwicklerkonto anlegen (einmalig 25 US-$). Neue private Konten müssen vor dem öffentlichen Start einen
   geschlossenen Test mit mehreren Testern über einen Zeitraum durchführen.
2. AAB hochladen und unter *Erweiterte Einstellungen → Formfaktoren* **Android TV** hinzufügen.
   Google prüft TV-Apps zusätzlich auf TV-Qualität (Fernbedienung, Banner, kein Touch-Zwang).
3. Datenschutz-Link, Formular zur Datensicherheit („keine Daten erhoben/geteilt“) und TV-Screenshots ausfüllen.

#### Damit die App die Store-Prüfung besteht
IPTV-Player werden besonders genau geprüft und werden oft wegen Urheberrechtsbeschwerden entfernt.
- **Keine Inhalte mitliefern:** keine vorinstallierten Playlists, Sender oder Test-Zugänge in der App.
- **Keine Anbieter bewerben** oder verlinken, keine Senderlogos bekannter Marken in Screenshots oder in der Beschreibung.
- **Screenshots** nur mit eigenen oder frei nutzbaren Test-Streams erstellen (z. B. eigene Videos oder freie Demo-Streams).
- In der Beschreibung klar sagen: *„Die App enthält keine Inhalte. Du benötigst ein Abonnement eines berechtigten Anbieters.“*
  (Der Hinweis steht bereits auf dem Login-Bildschirm.)
- Für die Prüfer im Kommentarfeld einen **legalen Test-Zugang** (z. B. eine eigene M3U mit freien Streams) angeben,
  sonst können sie die App nicht testen.
- Name und Icon frei wählen, nicht „Smarters“, „TiviMate“ o. Ä. verwenden (Markenrecht).

### Windows: Microsoft Store
1. **Konto anlegen:** auf storedeveloper.microsoft.com → *Get started for free* → *Individual developer*.
   Für Einzelpersonen ist das kostenlos; du bestätigst deine Identität mit Ausweis und Selfie.
2. **App-Namen reservieren:** In Partner Center unter *Apps und Spiele → Neues Produkt → MSIX- oder PWA-App*
   den Namen eintragen, z. B. „TV Player – IPTV“. Er muss im Store einmalig sein.
3. **Identität übernehmen:** unter *Produktverwaltung → Produktidentität* die Werte
   `Package/Identity/Name`, `Package/Identity/Publisher` und `PublisherDisplayName` kopieren und in
   `AppxManifest.xml` eintragen. Danach das MSIX neu bauen.
4. **Einreichung ausfüllen:**
   - *Preise und Verfügbarkeit:* kostenlos, Märkte wählen
   - *Eigenschaften:* Kategorie „Unterhaltung“, Link zur Datenschutzerklärung (Vorlage aus dem Android-Projekt)
   - *Altersfreigaben:* Fragebogen ausfüllen
   - *Pakete:* `TVPlayer.msix` hochladen
   - *Store-Einträge:* Beschreibung, mindestens 1 Screenshot (1920×1080 empfohlen), Logo
   - *Anmerkungen für die Zertifizierung:* einen **legalen Test-Zugang** angeben (z. B. eine eigene M3U mit frei
     nutzbaren Streams) und erwähnen: „Die App nutzt runFullTrust, weil die Wiedergabe über libVLC läuft.“
5. **Einreichen.** Die Zertifizierung dauert meist einige Tage.

Bei MSIX signiert Microsoft das Paket selbst. Ein MSI- oder EXE-Installer bräuchte ein eigenes, kostenpflichtiges Code-Signing-Zertifikat.

### Hinweis: Fire TV mit Vega OS
Neuere Amazon-Geräte mit **Vega OS** (z. B. Fire TV Stick 4K Select) können keine Android-Apps ausführen.
Diese App läuft auf allen Fire-TV-Geräten mit Fire OS (Android-basiert) sowie auf Android TV und Google TV.
Für Vega OS bräuchte es eine eigene Version mit Amazons React-Native-SDK.

## Ideen für später
Vollständige EPG-Ansicht (XMLTV), mehrere Profile, Untertitel- und Tonspurauswahl, Kindersicherung, Catch-up.
Die gemeinsame Logik in `shared/` ließe sich auch für eine iOS-/tvOS-Version nutzen.
