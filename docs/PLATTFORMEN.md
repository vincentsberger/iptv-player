# Plattform-Stand

Welche Oberfläche was kann. Die drei Oberflächen teilen nur die Logik (`shared`), nicht die UI – eine Funktion gilt
erst als vorhanden, wenn die jeweilige Oberfläche sie anbietet.

**Stand:** 28.11.2026, `master` nach #46 (aus dem Code erhoben). Offene PRs stehen unter [In Arbeit](#in-arbeit).

✅ vorhanden · 🟡 teilweise (Notiz) · ❌ fehlt · ➖ bewusst nicht / passt nicht zur Plattform

**Pflege:** Jeder PR aktualisiert die Zeilen, die er ändert, und bekommt ein Label `platform:tv|pc|ios|mehrere`.
Neue Specs nennen im Kopf `**Plattformen:**` (siehe CLAUDE.md). ❌ heißt „noch nicht entschieden“ – wird etwas
bewusst weggelassen, wird es ➖ mit Grund.

## Anmeldung & Anbieter

| Funktion | TV | PC | iPhone | Notiz |
|---|:-:|:-:|:-:|---|
| Xtream- und M3U-Login | ✅ | ✅ | ✅ | PC: eingefügter Xtream-Link füllt alle Felder |
| Login per QR-Code vom Handy | ✅ | ➖ | ➖ | nur dort sinnvoll, wo Tippen mühsam ist |
| Mehrere Anbieter, Wechsel | ✅ | ✅ | ✅ | |
| Anbieter entfernen mit Sicherheitsabfrage | ✅ | ✅ | ✅ | TV: zweiter Druck statt Dialog |
| Hinweis bei unsicherem http-Server | ✅ | ✅ | ❌ | `insecureServerHint` liegt in shared |

## Laden & Start

| Funktion | TV | PC | iPhone | Notiz |
|---|:-:|:-:|:-:|---|
| Zwischenspeicher (1×/Tag neu) | ✅ | ✅ | ✅ | |
| Ladebildschirm: Logo + Häkchen je Bereich | ✅ | ✅ | 🟡 | iPhone: eigene Fassung mit Gesamtbalken, nicht an #44 angeglichen |
| Lade-Übersicht nach Login / bei Warnungen | ✅ | ✅ | ✅ | |
| Start ohne Übersicht aus dem Zwischenspeicher | ✅ | ✅ | ✅ | |
| Beim Start letzten Sender abspielen | ✅ | ✅ | ✅ | |

## Startseite

| Funktion | TV | PC | iPhone | Notiz |
|---|:-:|:-:|:-:|---|
| Weiterschauen | ✅ | ✅ | ✅ | |
| Neues der letzten 7 Tage aus gewählten Kategorien | ✅ | ✅ | ✅ | nur Xtream (`supportsDashboard`) |
| Favoriten-Reihen (Live, Filme, Serien) | ✅ | ✅ | ✅ | |
| Zuletzt gesehene Sender, Offline-Reihe | ✅ | 🟡 | ✅ | PC: ohne Offline-Reihe (keine Downloads) |
| Reihen ein-/ausblenden und sortieren | ✅ | ✅ | ✅ | |
| Kategorienauswahl einklappbar | ✅ | ✅ | ✅ | |
| Abo-Laufzeit, Verbindungen, Ablauf-Warnung | ✅ | ✅ | ✅ | |
| Waagerechte Reihen mit der Maus blättern | ➖ | ✅ | ➖ | |

## Live TV

| Funktion | TV | PC | iPhone | Notiz |
|---|:-:|:-:|:-:|---|
| Kategorien inkl. Zuletzt gesehen / Favoriten / Alle | ✅ | ✅ | ✅ | |
| Landesflaggen | ✅ | ✅ | ✅ | |
| Jetzt/Danach + Fortschritt | ✅ | ✅ | ✅ | iPhone: in der Liste nur „Jetzt“ |
| Filterfeld über der Senderliste | ❌ | ✅ | ✅ | |
| Länderfilter | ✅ | ✅ | ✅ | |
| Programm des Senders, „Sendung verpasst“ | ✅ | ✅ | ✅ | |
| Programmführer als Raster über alle Sender | ❌ | ❌ | ❌ | überall nur je Sender als Liste |
| Senderliste im Player | ✅ | ❌ | ✅ | |
| Zappen | ✅ | ✅ | ✅ | TV/PC ▲▼, iPhone Wischen |
| Senderwahl per Ziffern | ✅ | ✅ | 🟡 | iPhone: Nummern sichtbar, keine Eingabe |
| Letzter Sender | ✅ | ✅ | ✅ | TV: nur per Taste, kein sichtbarer Knopf |
| Zapp-Karte beim Umschalten | ✅ | ✅ | ✅ | |
| Favorit per Wischen in der Liste | ➖ | ➖ | ✅ | |

## Filme & Serien

| Funktion | TV | PC | iPhone | Notiz |
|---|:-:|:-:|:-:|---|
| Raster mit Kategorien und Genres | ✅ | ✅ | ✅ | |
| Detailseite vor dem Abspielen | ✅ | ✅ | ✅ | PC als Detail-Fenster über der Seite |
| Info-Karte beim Überfahren | ➖ | ➖ | ➖ | PC: durch ruhigen Hover + Detail-Fenster ersetzt (#41) |
| Staffeln/Folgen, Sprung zur zuletzt gesehenen Folge | ✅ | ✅ | ✅ | |
| Weiterschauen an Position, „Von vorn“ | ✅ | ✅ | ✅ | |
| Nächste Folge mit Countdown (abschaltbar) | ✅ | ✅ | ✅ | |
| Favoriten | ✅ | ✅ | ✅ | |
| Suche über alle Bereiche, zuletzt gesucht | ✅ | ✅ | ✅ | |
| Kontextmenü | ✅ | ✅ | ✅ | TV: OK halten · PC: Rechtsklick · iPhone: lange drücken |
| Kategorien ausblenden | ✅ | ✅ | ✅ | |
| Sortierung A–Z / neueste zuerst | ✅ | ✅ | ✅ | |
| Trailer, „Ähnliche Titel“ | ❌ | ❌ | ✅ | |

## Player

| Funktion | TV | PC | iPhone | Notiz |
|---|:-:|:-:|:-:|---|
| Automatisch neu verbinden | ✅ | ✅ | ✅ | |
| Tonspur, Untertitel, Bildformat | ✅ | ✅ | ✅ | |
| Sprache für den nächsten Film merken | ✅ | ✅ | ✅ | |
| Quellenwahl bei mehreren Streams | ✅ | ✅ | ✅ | |
| Pause-Info | ✅ | ✅ | 🟡 | iPhone: nur stehende Bedienelemente |
| Spulen mit Ziel | ✅ | ✅ | ✅ | |
| Beschleunigtes Spulen | ✅ | ✅ | ❌ | iPhone: feste 10/30 s |
| Info-Abzeichen (Auflösung, Ton, Sprache, UT) | ✅ | ✅ | 🟡 | iPhone: nur HD/4K aus dem Namen |
| Verbindungsanzeige + Statistik | ✅ | ✅ | ✅ | |
| Schlaf-Timer | ✅ | ✅ | ✅ | |
| „endet um“ | ✅ | ✅ | ❌ | iPhone: nur Restzeit |
| Lautstärke in der App | ➖ | ✅ | ✅ | TV regelt die Fernbedienung · iPhone: Wischen rechts |
| Ton-Verzögerung | ❌ | ✅ | ❌ | |
| Vollbild umschalten | ➖ | ✅ | ✅ | |
| Touch-Gesten | ➖ | ➖ | ✅ | |
| Hoch-/Querformat | ➖ | ➖ | ✅ | |
| Bild-in-Bild | ❌ | ❌ | ❌ | iPhone: „später“ laut iOS-Spec |
| AirPlay / Cast | ❌ | ❌ | ❌ | iPhone: „später“ laut iOS-Spec |
| Systemsteuerung (Medientasten, Sperrbildschirm) | ✅ | ❌ | ✅ | PC: kein MPRIS/SMTC, Medientaste nur mit Fokus |
| Systemempfehlungen (Watch Next) | ✅ | ➖ | ➖ | nur Google TV, auf Fire TV ausgeblendet |
| Bildschirm bleibt beim Abspielen wach | ✅ | ✅ | ✅ | |

## Downloads

| Funktion | TV | PC | iPhone | Notiz |
|---|:-:|:-:|:-:|---|
| Filme und Staffeln laden, offline abspielen | ✅ | ➖ | ✅ | PC bewusst ohne Downloads |
| Downloads pausieren beim Schauen | ✅ | ➖ | ✅ | |

## App & Einstellungen

| Funktion | TV | PC | iPhone | Notiz |
|---|:-:|:-:|:-:|---|
| Updates aus der App | ✅ | 🟡 | ➖ | PC: prüft und öffnet die Download-Seite, Store-Fassung über den Store · iPhone: Sideloading |
| Tastenkürzel sichtbar | 🟡 | ✅ | ➖ | TV: Tastenleiste je Element, keine Gesamtübersicht |
| Einstellungen in Abschnitten | ✅ | ✅ | ✅ | PC mit Inhaltsverzeichnis |
| Verlauf löschen: Rückfrage + Rückgängig | ❌ | ✅ | 🟡 | TV löscht sofort · iPhone: Rückfrage, kein Rückgängig (`restoreHistory` liegt in shared) |
| App-Logo mit Einschalt-Animation | ✅ | ✅ | ✅ | |
| Diagnose kopieren | ❌ | ❌ | ❌ | offener Punkt in CLAUDE.md |

## Nur auf einer Plattform

- **TV:** Uhr in der Navigationsleiste, Toast-Einblendungen, Downloads-Tab öffnet sich ohne Internet.
- **PC:** Fenster merkt Größe/Lage/Maximiert, Fenstertitel zeigt Sender/Film, Strg 1–7 und Strg+F, Tastenkappen bei
  gehaltener Strg-Taste, Maus-Zurück-Taste, Doppelklick = Vollbild, Mauszeiger verschwindet im Player, Microsoft Store (MSIX).
- **iPhone:** Ziehen zum Neuladen, Vibration bei Rückmeldungen, Lizenzen-Seite, Einstellung „immer quer“.

## In Arbeit

Derzeit nichts. Offene PRs mit Plattform und Inhalt hier eintragen, beim Merge in die Tabellen oben übernehmen.

## Ideen

Noch nicht entschieden, ohne Plattformzuordnung: `docs/ideen/` (Catch-up-Bereich, Manager-Schnittstelle).
