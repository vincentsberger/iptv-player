# UI/UX-Review der PC-App (Compose Desktop)

**Stand:** 25.11.2026 · Größe: Projekt (in Paketen) · Plattform: PC (Windows/Linux) · **Umgesetzt:** Pakete A–G (#33–#39), Version 2.3.0  
**Plattformen:** PC ✅ (H8 Laptop-Test offen) · TV ➖ · iPhone ➖ – übertragbar wäre „Verlauf mit Rückgängig“ (TV, iPhone)

## Warum

Die PC-App übernimmt viele Muster der TV-App: Fokus statt Maus, ↑↓ = Sender, waagerechte Reihen, keine Kontextmenüs.
Am PC erwartet man dagegen Maus und Tastatur: Esc/Alt+← zurück, Rechtsklick, senkrechtes Scrollen, sichtbaren
Tab-Fokus, Lautstärke per Taste. Dazu kommen ein paar falsche Texte und Verstöße gegen den Style-Lock
(`.tastemaker/style-lock.md`).

**Grundlage:** alle Bereiche headless gerendert (`ScreenshotDump`, 1440×900 und 1100×750, Testdaten ohne Poster) und
den UI-Code der PC-App gelesen. Den Player nur im Code geprüft, nicht laufend gesehen.

## Was bleibt (nicht anfassen)

- Gestaltung aus einem Guss: Farbwerte nur aus `Palette.kt`, Mono für Ziffern, Bewegung kurz (≤ 320 ms).
- Lade-, Leer- und Fehlerzustände sind gestaltet (Ladeschritte je Bereich, eigener Bildschirm ohne VLC).
- Tastaturkonzept (Strg 1–7, Tooltips mit Kürzel im Player, Zifferneingabe), schmales Layout unter 1280 dp.

## Befunde

Zeilenangaben beziehen sich auf den Stand von `b6f6ea9`.

### 🔴 Hoch – bremst täglich oder ist falsch

- [x] **H1. Detailseiten ohne Tastatur-Zurück:** Esc, Alt+← und die Zurück-Taste der Maus tun auf Film- und
  Serienseite nichts, nur der Pfeil oben links. Ort: `MovieScreen.kt:48`, `SeriesScreen.kt`.
  Lösung: Esc, Alt+← und Maustaste 4 global in `Main.kt` auf `app.back()` legen (Player und Vollbild behalten
  ihre eigene Esc-Logik).
- [x] **H2. Suchfeld ohne Fokus:** Nach Strg+2 muss man erst ins Feld klicken. Es gibt kein Strg+F und kein `/`.
  Ort: `HomeScreen.kt:869`. Lösung: `FocusRequester` und `LaunchedEffect`; Strg+F öffnet die Suche.
- [x] **H3. Falscher Hinweis in Live TV:** „ein Doppelklick startet den Sender“, aber schon ein Klick spielt ab.
  Ort: `HomeScreen.kt:541` gegen `:481`. Lösung: „Klick oder Enter startet den Sender.“
- [x] **H4. Überfahren setzt die Auswahl:** Fährt man über die Senderliste, ändern sich Auswahl und
  Programm-Spalte. Das kollidiert mit der Tastatur, und schräge Mauswege zum „Ansehen“-Knopf springen auf andere
  Sender. Ort: `HomeScreen.kt:473`. Lösung: Überfahren zeigt nur eine Vorschau, die Auswahl setzt erst ein Klick
  oder eine Taste. Mindestens: nicht umschalten, solange die Tastatur zuletzt benutzt wurde.
- [x] **H5. Waagerechte Reihen** (Start, Favoriten, Suche) lassen sich mit dem Mausrad nur mit Umschalt bewegen.
  Ort: `Components.kt:465`. Lösung: Beim Überfahren ‹ ›-Knöpfe zum Blättern zeigen, das Mausrad über der Reihe
  waagerecht umrechnen. Suchergebnisse als Raster statt in Reihen.
- [x] **H6. Übersicht bei jedem Start:** Der Ladeablauf setzt immer `overviewPending`, also wartet jeder Start auf
  „Weiter“, auch beim Laden aus dem Zwischenspeicher. Ort: `AppState.kt:217`, `:353`. Lösung: Übersicht nur nach
  Login, „Neu laden“ oder bei Warnungen.
- [x] **H7. Tab-Fokus unsichtbar:** Die klickbaren Zeilen in Seitenleiste, Kategorien und Senderliste haben
  `indication = null` und keinen eigenen Fokusrand. Ort: `HomeScreen.kt:258`, `:325`, `:481`. Lösung: Fokusrand
  in `focus-bg`/`focus-fg` (Style-Lock), über `onFocusChanged`.
- [x] **H8. Fenstergröße auf Laptops:** Start 1400×860, Mindestgröße 1100×680. Bei Full HD mit 150 % bleiben unter
  Windows nur etwa 1280×670 dp. Größe und Position werden nicht gespeichert. Ort: `Main.kt:37`, `:51`. Lösung:
  Startgröße als Anteil des Arbeitsbereichs mit Ober- und Untergrenze, Mindesthöhe etwa 600, Größe, Position und
  Maximiert-Zustand speichern. **Erst am Laptop bestätigen.** Umgesetzt in Paket D (#36), Laptop-Test steht noch aus.

### 🟠 Mittel – wirkt unfertig oder nicht wie eine PC-App

- [x] **M1. Keine Lautstärke per Tastatur im Player:** `+`/`−` sind nicht belegt, bei Filmen geht Lautstärke nur
  mit dem Mausrad. Ort: `PlayerScreen.kt:592`. Lösung: `+`/`−` überall, ↑↓ bei Filmen ohne Folgen.
- [x] **M2. Kein Rechtsklick:** Karten, Senderzeilen und Kategorien haben kein Kontextmenü. Lösung:
  `ContextMenuArea` mit Abspielen / Favorit / Details / Kategorie ausblenden.
- [x] **M3. Live TV ohne Filterfeld** (Filme und Serien haben eins). Ort: `HomeScreen.kt:418` gegen `:669`.
  Lösung: dasselbe Filterfeld über der Senderliste.
- [x] **M4. Ungleiche Abstände im Raster:** Die Karte ist fest 160 dp breit, die Spalte mindestens 170 dp
  (Screenshot: etwa 67 px statt 18 zwischen Arrival und Dune). Ort: `HomeScreen.kt:684`, `Components.kt:392`.
  Lösung: Karte füllt die Spalte (Seitenverhältnis), oder `GridCells.FixedSize`.
- [x] **M5. Gefährliche Aktionen:** „Anbieter entfernen“ wird beim Bestätigen zu einem bernsteinfarbenen
  Hauptknopf, ohne Abbrechen und ohne Zurücksetzen. „Verlauf löschen“ fragt nicht und lässt sich nicht rückgängig
  machen. Ort: `HomeScreen.kt:922`. Lösung: Dialog mit Abbrechen und rotem Bestätigen-Knopf; beim Verlauf ein
  Hinweis mit „Rückgängig“.
- [x] **M6. Einstellungen schlecht gegliedert:** „Komfort“ sammelt Sortierung, ausgeblendete Kategorien,
  Länderfilter, Neues und Reihen der Startseite. Ort: `HomeScreen.kt:1025`. Lösung: Abschnitte Anbieter /
  Wiedergabe / Startseite / Filter / App, links ein Inhaltsverzeichnis.
- [x] **M7. Liste der Tastenkürzel falsch:** Sie heißt „im Player“, enthält aber Kürzel aus den Listen. F steht
  doppelt, T und I fehlen. Ort: `HomeScreen.kt:939`. Lösung: nach Ort trennen (Überall / Listen / Player).
- [x] **M8. Bernstein als Schmuck:** Symbole an Seitentiteln, Nummern 01–03 im Login, Oberzeile „FILM“. Das
  widerspricht dem Style-Lock. Ort: `Components.kt:490`, `LoginScreen.kt`, `MovieScreen.kt:53`. Lösung: `muted`
  oder weglassen.
- [x] **M9. Schatten statt Linien, dreifache Reaktion auf Überfahren:** `panel()` und die Karten haben Schatten,
  obwohl der Lock Linien vorgibt. Die Karte vergrößert sich, ändert den Rand von 1 auf 2 dp (Inhalt verschiebt
  sich um 1 dp) und bekommt Schatten. Ort: `Components.kt:179`, `:389`, `:414`. Lösung: Rand von 1 dp in `line`;
  beim Überfahren Rand **oder** Vergrößern, Randbreite konstant.
- [x] **M10. Anbieter-Feld zeigt das Falsche:** „Xtream Codes · user …“ wird abgeschnitten, das Kürzel ist immer
  „XC“. Ort: `HomeScreen.kt:300`. Lösung: Host zuerst, Kürzel aus dem Host.
- [x] **M11. Abo-Status doppelt und verschieden formuliert:** „Abo: unbegrenzt“ gegen „Abo ohne Ablaufdatum“.
  Ort: `HomeScreen.kt:792` gegen Seitenleiste. Lösung: ein Wortlaut; auf der Startseite nur bei Warnung zeigen.
- [x] **M12. Kopf in Live TV überladen:** Bedienungsanleitung über der Liste, „3 Sender“ doppelt. Die
  Überschriften sitzen nicht auf einer Linie (Live TV ist um 14 dp versetzt, Filme nicht). Ort:
  `HomeScreen.kt:421`. Lösung: Hilfetext in Tooltips, gleiche Grundlinie in allen Listen.

### 🟡 Klein

- [x] **K1.** Sieben Tastenkappen „Strg 1–7“ immer sichtbar. Lösung: nur bei gedrückter Strg-Taste zeigen.
  `HomeScreen.kt:269`
- [x] **K2.** Lautstärke-Anzeige, „Sender nicht gefunden“ und Nummerneingabe liegen übereinander oben rechts;
  die Lautstärke nutzt das Emoji 🔊 statt eines Symbols. `PlayerScreen.kt:310`, `:340`, `:348`
- [x] **K3.** Der Tooltip am Pause-Knopf heißt immer „Pause“. `PlayerScreen.kt:432`
- [x] **K4.** Tonverzögerung nur sechs feste Werte, ganz unten im langen Spurenmenü. `PlayerScreen.kt:526`
- [x] **K5.** Unter „Startseite: Reihen“ ist der Pfeil nach unten auch beim letzten Eintrag aktiv.
  `HomeScreen.kt:1076`
- [x] **K6.** Keine Hand als Mauszeiger auf Kategorien-Flächen der Startseite, „Löschen“ und den Flächen unter
  „Zuletzt gesucht“. `HomeScreen.kt:976`, `:1107`, `:1113`
- [x] **K7.** Version steht in den Einstellungen zweimal. `HomeScreen.kt:750`, `:952`
- [x] **K8.** Material-Schalter als Kapsel, obwohl der Lock „keine Kapseln“ sagt. `HomeScreen.kt:1088`
- [x] **K9.** Login: linke Hälfte ist ein leerer Verlauf, „Tab = nächstes Feld“ ist überflüssig, der Platzhalter
  sieht aus wie ein eingetragener Wert.
- [x] **K10.** Startseite: „Guten Abend“ ist die größte Überschrift, sagt aber am wenigsten; darunter stapeln sich
  Update-Hinweis, Abo-Zeile und Kategorien-Karte ohne Rangfolge.
- [x] **K11.** Die Zeitleiste im Player zeigt beim Überfahren keine Zeit an.
- [x] **K12.** Der Fenstertitel ist immer „IPTV Player“; beim Abspielen wäre der Sender- oder Filmname nützlich.

## Vorgeschlagene Pakete (je ein PR)

- **Paket A – Tastatur & Navigation:** H1, H2, H7, M1, M7, K1. Kaum Gestaltung, viel Nutzen im Alltag.
- **Paket B – Texte & Kleinkram:** H3, M10, M11, M12, K3, K5, K6, K7. Schnell, ohne Risiko.
- **Paket C – Live TV mit Maus:** H4, M3, M2 (Kontextmenü zuerst hier, dann überall).
- **Paket D – Start & Fenster:** H6, H8 (nach dem Test am Laptop), K12.
- **Paket E – Reihen & Raster:** H5, M4, M9, Suchergebnisse als Raster.
- **Paket F – Einstellungen:** M5, M6, K8.
- **Paket G – Style-Lock & Player-Feinschliff:** M8, K2, K4, K9, K10, K11.

## Tests

- Bestehende Tests: `./gradlew :shared:desktopTest :desktopApp:test`. Neue Tastenbelegung als reine Funktionen
  testbar halten (wie `playerActionFor`).
- Sichtprüfung: `ScreenshotDump` vorher und nachher. Zusätzlich mit `Density(1.5f)` bei 1920×1008 Pixeln
  (≈ Full-HD-Laptop mit 150 % und Taskleiste) rendern.
- Am Laptop (nicht headless prüfbar):
  - **1.** Windows mit 125 % und 150 %: Passt das Fenster beim ersten Start? (H8)
  - **2.** Reihen mit Touchpad und mit Mausrad. (H5)
  - **3.** Nur Tastatur: Tab durch die Seitenleiste, Strg+2 und tippen, Film öffnen, Esc. (H1, H2, H7)
  - **4.** Vollbild ein und aus, danach kommen Tasten noch an.

## Nicht Teil dieser Spec

- Texte in Ressourcen / Englisch (zurückgestellt, siehe CLAUDE.md „Offene Punkte“).
- TV- und iPhone-App: Die Befunde gelten nur für die PC-App, auch wenn einzelne Punkte (M5, M11) dort ebenso
  zutreffen könnten.
