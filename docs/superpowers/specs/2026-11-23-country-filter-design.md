# Länderfilter für Live-TV

**Stand:** 23.11.2026 · Größe: kleine Änderung (gemeinsame Logik + je ein Einstellungsbereich Android TV / Desktop)  
**Plattformen:** TV ✅ · PC ✅ · iPhone ✅

## Warum

Große Xtream-Anbieter liefern Sender aus Dutzenden Ländern (Beispielanbieter: 580 Live-Kategorien, 32.000 Sender).
Ziel: Die Senderliste auf die Länder eingrenzen, die man sieht – z. B. nur Deutschland oder DE/AT/CH.

## Ziele / Nicht-Ziele

- **Ziel:** In Live-TV nur Kategorien der gewählten Länder anzeigen – in Kategorienliste, „Alle“, Suche,
  Senderliste im Player und beim Zappen.
- **Ziel:** Bleibt richtig, wenn der Anbieter Kategorien hinzufügt oder umbenennt.
- **Nicht-Ziel:** Filme und Serien (Serien tragen beim Beispielanbieter gar keine Kürzel, Filme nur zu 73 % und teils Sprache statt Land).
- **Nicht-Ziel:** Sender einzeln einordnen (Namenskürzel/`epg_channel_id` fehlen bei der Mehrheit der Sender).
- **Anti-Ziel:** Eine leere Senderliste, ohne dass klar ist, warum.

## Datengrundlage

Xtream hat kein Länderfeld. Anbieter schreiben ein Kürzel vor den Kategorienamen („DE | NACHRICHTEN“), das
`splitProviderTag` bereits erkennt. Beim Beispielanbieter tragen 576 von 580 Live-Kategorien ein Kürzel.
Die Kürzel sind keine sauberen ISO-Codes (`UK`, `AR` vermutlich Arabisch, Formatkürzel wie `4K`, `24/7`).

## Ansätze

- **A – Filter über das Kategorie-Kürzel (gewählt):** Auswahl aus den Kürzeln, die der Anbieter tatsächlich verwendet.
  Neue Kategorien werden automatisch mitgefiltert.
- **B – „Nur DE“-Knopf, der die vorhandene Ausblende-Liste füllt:** am billigsten, aber neue Kategorien tauchen
  ungefiltert auf, Zurücknehmen ist mühsam. Verworfen.
- **C – Jeden Sender einzeln einordnen:** Daten dafür fehlen meist, die Kategorie liefert die Information schon. Verworfen.

## Design

- **Speicherung:** pro Anbieter (wie Favoriten/Ausblendungen) eine Menge gewählter Kürzel, neuer Schlüssel in
  `PER_ACCOUNT_KEYS`. Leere Menge = kein Filter (Standard).
- **Logik (`AppState.applyPrefs`, nur `Section.LIVE`):** Eine Kategorie ist sichtbar, wenn sie nicht ausgeblendet ist und
  - kein Kürzel hat (bleibt immer sichtbar), oder
  - ihr Kürzel in der Auswahl ist.
  Da Liste, „Alle“, Suche und Player-Senderliste schon über `section(s)` laufen, greift der Filter dort automatisch.
- **Verfügbare Kürzel:** aus den geladenen Live-Kategorien, mit Anzahl der Kategorien, häufigste zuerst.
- **Oberfläche:** in den Einstellungen bei „Listen“ (Android TV) bzw. „Komfort“ (Desktop) ein Abschnitt
  „Länder in Live-TV“: Kürzel als An/Aus-Chips mit Flagge und Anzahl, dazu „Alle zeigen“ (leert die Auswahl).

  ```
  Länder in Live-TV                       [ Alle zeigen ]
  [✓ 🇩🇪 DE 47] [  🇬🇧 UK 86] [  🇪🇸 ES 56] [✓ 🇦🇹 AT 1] [✓ 🇨🇭 CH 1] …
  Nur Kategorien dieser Länder werden angezeigt. Kategorien ohne Kürzel bleiben sichtbar.
  ```
- **Hinweis bei aktivem Filter:** In Live-TV ein kleiner Hinweis „Gefiltert: DE, AT, CH“, damit eine kurze Liste erklärbar bleibt.

## Entscheidungen des Builders (zum Gegenlesen)

- Formatkürzel (`4K`, `8K`, `24/7`) erscheinen wie Länder in der Auswahl – wer sie will, hakt sie an; keine Sonderlogik.
- Favoriten und Verlauf (Startseite) werden nicht gefiltert – dort steht, was man bewusst gewählt hat.
  Die Kategorie „Favoriten“ innerhalb von Live-TV folgt dem Filter (wie heute schon beim Ausblenden).
- Ist die gerade offene Live-Kategorie nach einer Filteränderung nicht mehr sichtbar, springt Live-TV auf „Alle“.
- Flaggen nur dort, wo die App sie schon zeigt (Android TV, `FlagOrTag`); der Desktop zeigt das Kürzel (`TagChip`).
- Ist keines der gewählten Kürzel mehr beim Anbieter vorhanden, gilt der Filter als aus (statt leerer Liste);
  in den Einstellungen bleiben solche Kürzel sichtbar, damit man sie abwählen kann.
- Tests: Filterlogik in `shared` (Unit-Tests), je ein UI-Test für den Einstellungsabschnitt.
