# Verbindungsanzeige und „Statistik“-Fenster im Player

**Stand:** 24.11.2026 · Größe: Projekt · Plattformen: Android TV, PC (Windows/Linux), iPhone  
**Plattformen:** TV ✅ · PC ✅ · iPhone ✅

## Warum

Ruckelt ein Stream, ist heute nicht zu erkennen, woran es liegt: Liefert der Anbieter zu langsam oder setzt er aus,
oder hakt die eigene Leitung bzw. das WLAN? Und was liefert der Anbieter technisch überhaupt (Codec, Auflösung,
Bitrate)? Vincent will beides während der Wiedergabe beantworten können, ähnlich wie mit „Statistiken für Nerds“
bei YouTube. Im normalen Bild darf davon nichts auftauchen, solange man es nicht selbst einschaltet.

## Ziele

- **Verbindungsanzeige:** ein kleiner Indikator (z. B. `●●●○`) in einer Ecke des Players. In den Einstellungen
  einschaltbar, danach dauerhaft sichtbar, damit man ein Ruckeln im Moment selbst sieht.
- **Statistik-Fenster:** blendet man im Player selbst ein. Oben steht die Diagnose, darunter die Technik:

  ```
  ┌ Statistik ─────────────────────────────┐
  │ Verbindung   ●●●○  gut                 │
  │ Puffer       12,4 s   ▁▂▃▅▆▆▇ (60 s)   │
  │ Datenrate    6,1 Mbit/s ▅▆▅▃▅▆▆ (60 s)  │
  │ Ausfälle     2 Neuverbindungen · 3 s gestockt · 14 verworfene Bilder │
  │ ─────────────────────────────────────  │
  │ Video        H.264 · 1920×1080 · 50 fps │
  │ Audio        AAC · Stereo · deu         │
  │ Stream       HLS (m3u8) · Quelle 2/3    │
  │ Server       provider.example:8080      │
  └────────────────────────────────────────┘
  ```

  Der **Verlauf über 60 Sekunden** ist wesentlich, nicht nur der aktuelle Wert. „Fällt immer wieder kurz weg“ (eher
  Leitung) unterscheidet sich von „kommt von Anfang an zu langsam“ (eher Anbieter).
- Funktioniert auf allen drei Plattformen mit derselben Bewertung.

## Nicht-Ziele

- Datensparmodus oder Qualitätsbegrenzung (bewusst verworfen).
- Speichern, Exportieren oder Kopieren der Messwerte. Das bleibt eine mögliche spätere Erweiterung, etwa zusammen
  mit „Diagnose kopieren“.
- Eigener Geschwindigkeitstest (Speedtest) oder Messung außerhalb der laufenden Wiedergabe.
- Hinweise oder Benachrichtigungen bei schlechter Verbindung.

## Anti-Ziele

Das gilt als gescheitert, selbst wenn es technisch läuft:

- **Zugangsdaten im Bild.** Xtream-Adressen enthalten Benutzername und Passwort im Pfad
  (`http://host:port/live/<user>/<pass>/<id>.ts`). Angezeigt werden darf nur `host:port`, niemals Pfad oder Query.
- **Spürbare Last, wenn beides aus ist.** Ohne eingeschaltete Anzeige und ohne offenes Fenster wird nichts
  abgefragt und nichts gezeichnet.
- **Der Indikator verdeckt Inhalt oder lenkt ab.** Er bleibt klein und dezent und folgt dem Stil der App.
- **Falsche Sicherheit.** Fehlt einer Engine ein Wert, steht „–“ da. Die Zeile verschwindet nicht, und es wird kein
  geschätzter Wert als gemessener ausgegeben.

## Vorgaben

- Architektur wie beim `PlaybackController` (`shared/…/shared/player/`): Logik in `commonMain`, Plattformen liefern
  nur Rohwerte und zeichnen.
- Texte deutsch (die Umstellung auf Ressourcen ist zurückgestellt; siehe CLAUDE.md „Offene Punkte“).
- Farben nur aus `shared/…/Palette.kt`, Gestaltung nach `.tastemaker/style-lock.md`.
- Neue Zustände gehören nicht in `AppState`, außer der Einstellung selbst. Die Einstellung kommt in den
  `SettingsStore`.

## Ansätze

- **A. Gemeinsame Messwerte und Bewertung in `shared`, Anzeige je Oberfläche** (gewählt).
  `PlaybackStats`, der 60-Sekunden-Verlauf und die Bewertung liegen in `commonMain` und werden dort getestet. Jede
  Engine übersetzt ihre Rohwerte, jede Oberfläche zeichnet. Doppelt bleibt nur das Zeichnen, etwa 30 Zeilen
  Verlaufskurve je Oberfläche.
- **B. Zusätzlich ein gemeinsames UI-Modul für das Fenster.** Verworfen: Es bräuchte ein neues Modul mit
  Compose-UI-Abhängigkeit für alle Apps, und das lohnt sich nur bei mehr geteilten Oberflächen. Das TV braucht
  außerdem eine andere Bedienung (Fokus statt Maus oder Touch).
- **C. Alles je Plattform.** Verworfen: Das wäre wieder dreifache Logik, und die Bewertung wäre nicht gemeinsam
  testbar.

## Entschieden (mit Vincent)

- Inhalt und Aufbau des Fensters wie in der Skizze oben.
- Der Indikator wird in den Einstellungen eingeschaltet und ist dann dauerhaft sichtbar. Das Fenster öffnet man
  jedes Mal selbst.
- Bauweise A.

## Design

### Rohwerte der Engines (`PlaybackEngine`)

Das Interface bekommt eine Abfrage mit Standardwert, damit Attrappen und künftige Engines nichts tun müssen:

```kotlin
/** Momentaufnahme der Rohwerte; null = diese Engine kennt den Wert nicht. Zähler sind seit Start des aktuellen Streams kumuliert. */
data class EngineStats(
    val bytesReceived: Long? = null,      // kumuliert → Datenrate rechnet shared aus der Differenz
    val bufferedMs: Long? = null,         // Puffer vor der Wiedergabeposition
    val droppedFrames: Long? = null,      // kumuliert (verworfene/verlorene Bilder)
    val videoCodec: String? = null, val width: Int? = null, val height: Int? = null, val fps: Float? = null,
    val audioCodec: String? = null, val audioChannels: Int? = null, val audioLanguage: String? = null,
)
fun stats(): EngineStats = EngineStats()   // in PlaybackEngine
```

Herkunft je Engine (Richtwerte für den Bau; was es nicht gibt, bleibt `null`):

| Wert | ExoPlayer (TV) | vlcj (PC) | VLCKit (iPhone) |
|---|---|---|---|
| bytesReceived | Summe `bytesLoaded` aus `AnalyticsListener.onLoadCompleted` | `media().info().statistics()` → gelesene Bytes | `VLCMedia.statistics` → `numberOfReadBytesOnInput` |
| bufferedMs | `bufferedPosition - currentPosition` | – (nicht verfügbar) | – |
| droppedFrames | Summe aus `onDroppedVideoFrames` | verlorene Bilder | `numberOfLostPictures` |
| Codec/Auflösung/fps/Audio | aktuelles `Format` von Video- und Audiospur | Spur- und Medieninfo | Spurinfo |

Fehlende Werte (z. B. der Puffer bei VLC) sind kein Fehler. Die Bewertung darf deshalb nicht allein am Puffer hängen
(siehe unten).

### Gemeinsame Logik (`shared/…/shared/player/`)

- **`PlaybackStats`**: das, was das Fenster anzeigt. Es fasst `EngineStats`, die aus der Bytes-Differenz berechnete
  Datenrate (bit/s), die Zahl der Neuverbindungen und die gestockten Sekunden zusammen. Außerdem Protokoll bzw.
  Container (aus der Endung der Stream-Adresse: `m3u8` → HLS, `ts` → MPEG-TS, sonst die Endung), Quelle *n/m* und
  `host:port`.
- **`StatsHistory`**: Ringpuffer mit 60 Messpunkten, ein Punkt pro Sekunde, für Puffer und Datenrate.
- **Stockungen** *(eigene Festlegung)*: Eine Sekunde zählt als gestockt, wenn die Wiedergabe laufen soll (nicht
  pausiert, nicht beendet) und in dieser Sekunde kein Fortschritt kam, weder Zeitfortschritt noch ein frischer
  `lastProgressAt` der Engine.
  - Das ist ein **eigener, schärferer Zähler** pro Sekunde, nicht erst die Hänger-Schwelle von `StreamRecovery`.
  - Grund: Sonst würde `POOR` kaum vor dem Neuverbinden auslösen.
  - So gibt es einen Messwert, der von jeder Engine unabhängig ist.
- **Neuverbindungen** *(eigene Festlegung)*: Es zählen das automatische Neuladen aus `watch()` und das manuelle
  „Erneut versuchen“ (`retry()`). Ein Quellenwechsel zählt nicht, denn er setzt Verlauf und Zähler zurück wie ein
  Titelwechsel. „Neuverbindungsversuch läuft“ heißt `reconnecting`, „Verbindung verloren“ heißt `connectionLost`.
- **Zählersprünge** *(eigene Festlegung)*: Ist ein kumulierter Zähler kleiner als im Messpunkt davor, gilt das als
  Neustart des Streams. Die Rate dieser Sekunde wird verworfen (`null`), danach wird von dem neuen Wert aus weiter
  gerechnet. Engines müssen ihre Zähler deshalb nicht selbst nullen. Bei vlcj und VLCKit beginnen sie je Medium
  ohnehin neu.
- **Anteil verworfener Bilder** *(eigene Festlegung)*: verworfene Bilder pro Sekunde geteilt durch `fps`. Ist `fps`
  unbekannt, entfällt dieses Kriterium in der Bewertung. Im Fenster steht dann nur die absolute Zahl.
- **`ConnectionQuality`**: `GOOD`, `FAIR`, `POOR` oder `UNKNOWN`, als reine Funktion der letzten Messpunkte.
  Anfangswerte, die der Bau als benannte Konstanten anlegt und die sich später einstellen lassen:
  - `POOR`: Es läuft gerade ein Neuverbindungsversuch, die Verbindung ist verloren, oder in den letzten 10 s waren
    mindestens 3 s gestockt.
  - `FAIR`: In den letzten 60 s gab es eine Neuverbindung oder eine Stockung, oder mehr als 2 % der Bilder wurden
    verworfen. Bei bekanntem Puffer gilt auch ein Puffer unter 2 s als `FAIR`, aber **nur für Filme und Folgen**
    (`!isLive`). Bei Live ist der Puffer oft klein und sagt nichts aus.
  - `GOOD`: sonst.
  - `UNKNOWN`: in den ersten 3 s nach dem Start eines Titels.
  - Der Indikator zeigt `●●●` für GOOD, `●●○` für FAIR, `●○○` für POOR und `○○○` für UNKNOWN. Farben kommen aus
    der Palette, zusätzlich steht im Fenster der Text, damit die Anzeige nicht nur an der Farbe hängt.
- **`hostAndPort(url)`**: liefert nur `host:port` und niemals Benutzer, Pfad oder Query. Dafür gibt es eigene Tests,
  auch für Xtream-Adressen und für `user:pass@host`. *(Eigene Festlegungen:)*
  - Ohne Port nur `host`, ohne den Standardport zu ergänzen.
  - IPv6 in Klammern, z. B. `[::1]:8080`.
  - `file://`, also heruntergeladene Titel, ergibt „Datei auf dem Gerät“.
  - Nicht lesbare Adressen ergeben „–“.
  - Die Messwerte werden nirgends ins Log geschrieben.
- **Anzeige von „Stream“ und „Quelle“** *(eigene Festlegung)*:
  - Quelle steht immer da, auch als „1/1“.
  - Beim Stream-Typ ergibt `m3u8` „HLS (m3u8)“ und `ts` „MPEG-TS“. Andere Endungen erscheinen in Großbuchstaben
    (z. B. „MKV“), ohne Endung steht „–“.
- **Abtastung im `PlaybackController`:** `suspend fun runStats()` misst einmal pro Sekunde. Bei einem Titel- oder
  Quellenwechsel werden der Verlauf und die Zähler zurückgesetzt. Lesbarer Compose-State: `stats: PlaybackStats?`,
  `history`, `quality`. *(Eigene Festlegungen:)*
  - Die Oberfläche startet die Abtastung per `LaunchedEffect(anzeigeAn || fensterOffen, c.items, c.index)`, so wie
    `watch()`. Sind beide aus, startet sie nicht. So ist „keine Abtastung, wenn beides aus ist“ eine Frage des
    Effekts und lässt sich im Controller-Test prüfen.
  - Die Abtastung läuft im Compose-Scope der Oberfläche, also auf dem Main-Thread.
  - `PlaybackEngine.stats()` muss billig sein und darf nicht blockieren. Engines, deren Statistik-Abfrage teuer ist
    oder an einen anderen Thread gebunden (vlcj, VLCKit), halten einen Wert vor, den ihr eigener Ereignis-Thread
    aktualisiert. ExoPlayer liest direkt, weil es ohnehin auf dem Main-Thread läuft.

### Einstellung

- `SettingsStore`: `showConnectionIndicator: Boolean`, Standard `false`.
- Eintrag in den Einstellungen auf allen drei Plattformen beim Player. Bezeichnung: „Verbindungsanzeige im Player“.

### Oberflächen

- **Indikator:** oben rechts im Player, klein und halbtransparent. Er steht über der Zapp-Karte und der Info-Leiste
  und verdeckt keine Bedienelemente. Antippen auf dem iPhone bzw. Klicken am PC öffnet das Fenster. Auf dem TV hat er
  keinen Fokus, ist also reine Anzeige.
- **Fenster öffnen und schließen:**
  - TV: Eintrag „Statistik“ im Optionen-Menü des Players (`PlayerOptionsPanel`); mit Zurück schließen.
  - PC: Taste **I** (heute frei) und ein Eintrag im Menü des Players; mit I oder Esc schließen.
  - iPhone: Eintrag im Optionen-Blatt (`PlayerOptions.kt`) und Antippen des Indikators; Schließen über ein ×.
- Das Fenster bleibt offen, während man zappt (die Werte beginnen dann neu). Es bleibt nicht über einen Neustart des
  Players hinaus offen.
- Das Fenster liegt halbtransparent über dem Bild: am TV und PC oben links, am iPhone im Querformat oben links und im
  Hochformat als Blatt.

## Dem Bau überlassen

- Die genauen Rohwert-Quellen je Engine, im Rahmen der Tabelle oben. Fehlt etwas, bleibt es `null`.
- Das Zeichnen der Verlaufskurven (Canvas oder Blockzeichen) und die Feinheiten von Layout und Typografie im
  Rahmen des Stils.
- Die Namen interner Hilfsfunktionen.

## Tests

- `commonTest`:
  - Datenrate aus Byte-Differenzen, auch für Zählersprünge beim Neustart und fehlende Werte.
  - Ringpuffer mit 60 Punkten.
  - Alle Bewertungsgrenzen von `ConnectionQuality`, auch ohne Puffer.
  - `hostAndPort` ohne Zugangsdaten.
  - Zurücksetzen bei Titelwechsel.
  - Keine Abtastung, solange Anzeige und Fenster aus sind.
- Je Engine ein schmaler Test, der die Übersetzung in `EngineStats` prüft, wo es ohne Gerät geht. Sonst bleibt es ein
  Handtest.
- **iPhone:** Die Engine ist `VlcController` in `iosApp` (implementiert `PlayerEngine : PlaybackEngine`). Unter Linux
  kompiliert `iosApp` nicht (VLCKit-cinterop). Geprüft wird er in `ios.yml` im PR; den Handtest macht Vincent mit
  der `.ipa` aus der CI per iloader.
- Die Einstellung folgt dem Muster der übrigen Einträge im `SettingsStore`. Es gibt keine Migration und keinen
  Export.
