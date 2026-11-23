# iPhone-App (Vorabversion)

Die iPhone-App ist eine **Vorabversion**. Sie wird ohne Mac gebaut (GitHub Actions) und **unsigniert** verteilt –
signiert wird erst beim Installieren, mit deiner eigenen, kostenlosen Apple-ID.

> Getestet auf: _wird nach dem ersten Gerätetest eingetragen (iPhone 11, iOS 27)._

## 1. App-Datei laden

- **Releases:** `IPTV-Player-<version>.ipa` aus dem [neuesten Release](https://github.com/vincentsberger/iptv-player/releases/latest)
- **Neuester Entwicklungsstand:** im Repository unter *Actions → iOS →* letzter grüner Lauf *→ Artifacts → ios-ipa*
  (enthält `IPTV-Player-ios.ipa`)

## 2. Einmalig auf dem iPhone

- **Entwicklermodus** einschalten: *Einstellungen → Datenschutz & Sicherheit → Entwicklermodus* (erscheint erst, nachdem
  einmal eine selbst signierte App installiert wurde), danach Neustart bestätigen.
- Nach der ersten Installation: *Einstellungen → Allgemein → VPN & Geräteverwaltung →* deiner Apple-ID **vertrauen**.

## 3. Installieren – empfohlen: SideStore

SideStore erneuert die App alle 7 Tage **auf dem iPhone selbst** – ohne Kabel und ohne Rechner.

- Einmalig am Rechner (Linux, Windows oder macOS) mit [iloader](https://iloader.app/): iPhone per USB anschließen,
  mit der Apple-ID anmelden, „SideStore installieren“ wählen. iloader legt dabei auch die Kopplungsdatei an.
- In SideStore die `.ipa` öffnen (*Meine Apps → +*) – oder später die Quelle des Projekts hinzufügen (kommt mit Etappe 4).

## 3b. Alternativ: direkt per USB vom Rechner

- [iloader](https://iloader.app/) oder [Impactor](https://github.com/claration/impactor): `.ipa` auswählen, Apple-ID
  angeben, installieren.
- ⚠️ Mit kostenloser Apple-ID läuft die App nach **7 Tagen** ab und muss neu installiert werden; höchstens 3 solche Apps
  gleichzeitig.

## 4. Wenn etwas nicht geht

Unter Linux mit [libimobiledevice](https://libimobiledevice.org/) (`sudo apt install libimobiledevice-utils`):

```bash
idevice_id -l                                # iPhone erkannt?
idevicesyslog | grep -i iptv                 # Live-Protokoll der App
idevicecrashreport -e ./crash                # Absturzberichte holen (enthalten den Stacktrace)
```

- **Kein Ton:** Lautstärke prüfen – der Stummschalter gilt für die App nicht.
- **Stream aus dem Heimnetz startet nicht:** beim ersten Start die Frage „Geräte im lokalen Netzwerk finden“ erlauben
  (*Einstellungen → IPTV Player → Lokales Netzwerk*).

## Für Entwickler

- Bauen nur auf macOS: `tools/ios/fetch-vlckit.sh` und dann `tools/ios/build-ipa.sh IPTV-Player-ios.ipa`
  (braucht Xcode und `brew install xcodegen`). In der CI: Workflow `iOS` (`.github/workflows/ios.yml`).
- Unter Linux lässt sich der Kotlin-Code für iOS kompilieren (`./gradlew :shared:compileKotlinIosArm64`), Linken und
  Tests laufen nur auf macOS.
- VLC: MobileVLCKit (LGPL 2.1) ist als eigenes, dynamisches Framework eingebettet – Lizenz- und Quellcode-Hinweis in
  der App unter „Lizenzen“ (`tools/vlc-licenses/QUELLCODE-ios.txt`).
