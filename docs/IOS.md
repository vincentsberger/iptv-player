# iPhone-App (Vorabversion)

Die iPhone-App ist eine **Vorabversion**. Sie wird ohne Mac gebaut (GitHub Actions) und **unsigniert** verteilt –
signiert wird erst beim Installieren, mit deiner eigenen, kostenlosen Apple-ID.

> Getestet am 23.11.2026 auf einem **iPhone 11 mit iOS 27**: Installation mit iloader (kostenlose Apple-ID) und SideStore,
> Wiedergabe von HLS (`.m3u8`) und MPEG-TS (`.ts`) über MobileVLCKit, Ton auch bei Stummschalter, Querformat.

## Was die Vorabversion kann

- Anmelden per Xtream Codes oder M3U-Link, mehrere Anbieter, Übersicht nach dem Laden
- **Start:** Abo-Laufzeit, zuletzt gesehene Sender, Favoriten, Offline verfügbar, Weiterschauen, neue Filme und Folgen;
  oben Favoriten, Downloads und Einstellungen
- **Live TV:** Kategorien mit Landesflaggen, „Jetzt“ mit Fortschritt und Beschreibung, Favoriten per langem Drücken,
  Kategorie ausblenden per langem Drücken auf die Kategorie
- **Filme und Serien:** Kategorien (Weiterschauen, Neu, Favoriten, Alle, Anbieter), Detailseiten, Staffeln und Folgen,
  Weiterschauen an der letzten Stelle, nächste Folge mit Countdown
- **Suche** über Live TV, Filme und Serien · **Favoriten** als eigene Übersicht
- **Downloads:** Filme, Folgen und ganze Staffeln offline – lädt einen Titel nach dem anderen, der laufende auch im
  Hintergrund; pausiert beim Streamen (abschaltbar)
- **Player:** Hochformat mit Programm und Senderliste unter dem Bild, Querformat bildschirmfüllend (Knopf „Vollbild“
  dreht auch bei Dreh-Sperre), Zap-Karte beim Umschalten, Schlaf-Timer, letzter Sender, Ton- und Untertitelspur
  (die Sprache gilt auch für den nächsten Titel), Bildformat (einpassen, füllen, strecken), verbindet sich bei
  Abbrüchen selbst neu. Im Hintergrund stoppt Live TV, Filme pausieren.
- **Einstellungen:** Anbieter, Wiedergabe, Sortierung, ausgeblendete Kategorien, Länderfilter für Live TV, Filme und
  Serien, Kategorien für „Neu“ auf der Startseite, Downloads, Verlauf löschen, Lizenzen
- **Gesten:** hoch/runter wischen = Sender wechseln · vom linken Rand wischen = Senderliste (Querformat) ·
  Doppeltipp links/rechts = 10 s zurück / 30 s vor · Tippen = Bedienelemente
- **Noch nicht:** Bild-in-Bild, AirPlay. Live-Ton läuft im Hintergrund nicht weiter. Ist ein Download im Hintergrund fertig, startet
  der nächste erst, wenn die App wieder geöffnet wird.

## 1. App-Datei laden

- **Releases:** `IPTV-Player-<version>.ipa` aus dem [neuesten Release](https://github.com/vincentsberger/iptv-player/releases/latest) –
  liegt dort keine, hat sich am iPhone nichts geändert: Der Release-Text verlinkt die gültige `.ipa` eines früheren Releases
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
- In SideStore die Quelle des Projekts hinzufügen (*Quellen → +*):
  `https://github.com/vincentsberger/iptv-player/releases/latest/download/sidestore-source.json` – dann zeigt SideStore
  neue Versionen selbst an und installiert sie mit einem Tippen. Alternativ die `.ipa` direkt öffnen (*Meine Apps → +*).

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
  (*Einstellungen → IPTV Player → Lokales Netzwerk*). Läuft der Server auf einem Linux-PC mit aktiver Firewall (ufw),
  den Port fürs Heimnetz freigeben, z. B. `sudo ufw allow from 192.168.178.0/24 to any port 8099 proto tcp`.

## Für Entwickler

- Bauen nur auf macOS: `tools/ios/fetch-vlckit.sh` und dann `tools/ios/build-ipa.sh IPTV-Player-ios.ipa`
  (braucht Xcode und `brew install xcodegen`). In der CI: Workflow `iOS` (`.github/workflows/ios.yml`).
- Unter Linux lässt sich der Kotlin-Code für iOS kompilieren – `./gradlew :shared:compileKotlinIosArm64` und die
  ganze Oberfläche mit `./gradlew :iosUi:compileKotlinIosArm64`. Nur `iosApp` (MobileVLCKit per cinterop), Linken
  und Tests laufen ausschließlich auf macOS. Die Oberfläche kennt den Player nur über `PlayerEngine`
  (`iosUi/.../player/PlayerEngine.kt`); `iosApp` setzt ihn mit `VlcController` um.
- VLC: MobileVLCKit (LGPL 2.1) ist als eigenes, dynamisches Framework eingebettet – Lizenz- und Quellcode-Hinweis in
  der App unter „Lizenzen“ (`tools/vlc-licenses/QUELLCODE-ios.txt`).
