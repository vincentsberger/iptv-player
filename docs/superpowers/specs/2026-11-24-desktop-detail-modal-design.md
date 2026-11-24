# PC-App: Detail-Fenster, ruhiger Hover und Angleichung an die TV-App

Stand 2026-11-24 · betrifft nur `desktopApp` (TV-App und iPhone bleiben unverändert)

## Warum

Die PC-App wirkt neben der TV-App unfertig. Die Hover-Info-Karte (`HoverInfoCard.kt`, eingeführt in `769111b`) ist
eine Zwischenstufe: für Bild und alle Infos zu klein, als Vorschau beim bloßen Überfahren zu groß und aufdringlich.
Gewünscht ist das Netflix-Muster – Klick öffnet ein großes Fenster über dem Raster mit allem – plus durchgehend weiche
Übergänge. Ein Screenshot-Vergleich PC↔TV (Beispieldaten aus `testAppState()` + `docs/screenshots/android-tv/`) hat
weitere Abweichungen ergeben; alle sieben Pakete daraus sind beauftragt.

## Ziele

- Klick auf Film oder Serie öffnet ein **Detail-Fenster** über der Startseite; es ersetzt `MovieScreen` und
  `SeriesScreen` vollständig (Serien mit Staffeln und Folgen im Fenster).
- Hover zeigt nur noch eine **ruhige Hervorhebung** der Kachel, keine aufspringende Karte.
- Alle Zustandswechsel der PC-Oberfläche sind **animiert** (keine harten Sprünge von Farbe, Fläche, Inhalt).
- Die PC-App folgt sichtbar derselben Gestaltung wie die TV-App (Pakete 1–7 unten).

## Nicht-Ziele

- Keine Änderung an `AppState`/`shared` (die API nutzen drei Oberflächen). Navigation bleibt `app.open`/`app.back`.
- Kein Shared-Element-Übergang (Kachelbild wächst ins Fenster) – bewusst verworfen (Ansatz B), später nachrüstbar.
- Kein Trailer-Abspielen im Fenster (Daten hätten `trailer`, ist eigenes Thema).
- Keine Änderung an Player, Login, Live-TV-Wiedergabe.

## Anti-Ziele (wäre ein Fehlschlag, auch wenn es technisch läuft)

- Das Fenster ruckelt beim Öffnen/Schließen oder das Raster darunter springt/verliert Scrollposition.
- „Zurück“ aus dem Player landet nicht wieder im offenen Fenster.
- Bewegung wirkt verspielt oder langsam (Federn, Überschwinger, > 300 ms für Alltägliches).
- Bernstein als Hover-Deko (Style-Lock: Bernstein nur für Auswahl, „Live“, Hauptaktion, Fortschritt).

## Rahmen

- Gestaltung: `.tastemaker/style-lock.md` – Ecken 4 dp (klein 2 dp), **keine Kapseln, keine Verläufe, feine Linien
  statt Schatten**, Fokus/Hover warmweiß. Farben nur aus `shared/…/Palette.kt` (über die Tokens der PC-App).
- Compose Multiplatform 1.9.3; Fenster-Mindestgröße 1100×680; schmal-Modus `LocalCompact` (< 1280 dp).
- Konventionen aus `CLAUDE.md`: Bezeichner englisch, KDoc deutsch, Zeilen bis 160, detekt sauber halten.

## Ansätze (Detail-Fenster)

- **A – Fenster als Ebene über der Startseite, Navigation unverändert (gewählt).** `Main.kt` zeichnet bei
  `Screen.Movie`/`Screen.Series` weiter `HomeScreen` als Grund und legt das Fenster darüber. Geringes Risiko, keine
  Änderung an `AppState`, Player-Zurück funktioniert von selbst.
- **B – wie A plus Shared Element (Kachelbild wächst ins Fenster).** Am nächsten an Netflix, aber Kachel und Fenster
  liegen in getrennten Ebenen (Raster im Tab-`AnimatedContent`, Fenster darüber); Sonderfälle (Kachel weggescrollt,
  Aufruf aus Suche/Favoriten) – etwa doppelter Aufwand, größtes Ruckel-Risiko. Verworfen, später nachrüstbar.
- Verworfen: eigenes OS-Fenster (`Dialog`/`DialogWindow` – kaum animierbar, Fokusprobleme); Fenster-Zustand nur lokal
  in der PC-App (bricht `app.back()` aus dem Player).

## Entschieden

### Detail-Fenster

- **Ebenen in `Main.kt`:** Der Bildschirmwechsel (`AnimatedContent`) läuft über eine *Grund-Ebene*:
  `Movie`/`Series` → Grund ist `Home`. Damit bleibt `HomeScreen` beim Öffnen stehen (kein Bildschirmwechsel, Raster
  behält Scrollposition – `SaveableStateProvider("home")` bleibt). Das Fenster ist eine eigene Ebene darüber, ein- und
  ausgeblendet per `AnimatedVisibility` (bzw. `AnimatedContent` auf das aktuelle Detail-Item).
- **Inhalt Film** (alles aus `MovieScreen`): Breitbild oben (`info.backdrop`, sonst Cover), Kicker „Film“, Titel,
  Originaltitel, Fakten (`movieFacts`), Knöpfe Abspielen/Weiterschauen · Von vorn (bei Fortschritt) · Merken, Handlung,
  Regie, Besetzung (max. 8), „Infos werden geladen …“ solange `info == null`.
- **Inhalt Serie** (alles aus `SeriesScreen`): Breitbild (`series.backdrop`, sonst Cover), Kicker „Serie“, Titel,
  „N Staffeln · M Folgen“, Handlung, Weiterschauen/Abspielen · Merken, Staffel-Wahl, Folgenliste (`EpisodeRow` wie
  heute), Sprung auf die Staffel der zuletzt gesehenen Folge, Lade- und Fehlerzustand.
- **Aufbau (Skizze):**
  ```
  ┌──────────── Breitbild ──────────────────────────┐
  │  Titel                     ▶ Weiterschauen  ☆  ✕│
  ├─────────────────────────────────────────────────┤
  │ 2021 · 1 Std 52 · ★ 7,4    Regie / Besetzung    │
  │ Handlung …                                      │
  │ [Staffel 1] [Staffel 2]          (nur Serien)   │
  │ ▢ 1  Folgentitel · 42 Min · Handlung …          │
  └─────────────────────────────────────────────────┘
  ```
  Ein Scrollbereich für das ganze Fenster (Bild scrollt mit). Schließen-Knopf ✕ oben rechts, immer sichtbar.
- **Schließen:** ✕, Esc, Klick auf den abgedunkelten Grund → `app.back()`. Esc im Vollbild-Player bleibt wie heute.
- **Bewegung:** Grund dunkelt per Alpha ein (flache Farbe, kein Verlauf). Fenster: Einblenden + Skalierung 0,94 → 1,
  `transformOrigin` = Mittelpunkt der angeklickten Kachel relativ zum Fenster; Öffnen ~240 ms `FastOutSlowIn`,
  Schließen ~180 ms schneller. Ohne bekannte Kachel (Aufruf aus Suche-Verlauf o. Ä.): Ursprung Fenstermitte.
- **Weiterschauen-Reihe** spielt weiterhin direkt ab (heutiges Verhalten, `HomeScreen.kt:693/852`).
- **Abspielen aus dem Fenster** → Player wie heute; `app.back()` aus dem Player zeigt wieder Startseite + Fenster.
- `MovieScreen.kt`, `SeriesScreen.kt`, `HoverInfoCard.kt` und `hasHoverCard`/`onDwell`-Pfad entfallen.

### Paket 1 – Seitenleiste

- Oben `LogoMark` + „IPTV Player“ + Uhr (Muster TV `HomeScreen.kt:167-175`); schmal: nur `LogoMark`.
- Symbol des gewählten Bereichs in Bernstein (wie TV).
- „Strg N“-Kappen nur beim Überfahren der Zeile (eingeblendet).
- Fläche/Farbe animiert, Auswahlstrich wächst weich auf (Muster TV `SelectionMark`).

### Paket 2 – Raster Filme/Serien

- Karten füllen die Zelle, Seitenverhältnis 2:3 (Muster TV `fillCell`, `Components.kt:536-538`); keine festen 160 dp.
- Hover = TV-Fokus: warmweißer Rahmen 3 dp, Skalierung 1,08, animiert. Kein Bernstein-Rahmen.
- Keine Schatten an Karten und Panels (Style-Lock).
- Jahr/Bewertung als Etikett auf dem Cover statt Mono-Zeile darunter (Muster TV `Components.kt:547-554`).

### Paket 3 – Live TV

- Sender als eigene Karten (Abstand 6 dp, ~64 dp hoch), Fortschritt als voller Balken am unteren Rand (Muster TV
  `LiveTvScreen.kt:189, 270-310`); Panel wächst nicht mehr bis zum Fensterboden.
- **Klick spielt weiter sofort ab** (schnelles Zappen, entschieden). Die Infospalte folgt der Maus erst nach kurzem
  Verweilen (~250 ms) und blendet pro Senderwechsel **nur einmal** über (Sender + EPG zusammen, nicht zweimal wenn das
  EPG nachlädt). Beseitigt das Flackern. Pfeiltasten wählen wie heute sofort.
- Hinweiszeile auf „N Sender“ kürzen; Platzhaltertext „Doppelklick“ → Einfachklick.
- Kategorien luftiger (15 sp, 11 dp wie TV); Trennlinie und Panel gleich breit.

### Paket 4 – Ladezustände

- Live TV, Filme, Serien: Platzhalter in Form des späteren Layouts statt Logo (Muster TV `LiveSkeleton`,
  `GridSkeleton`); Übergang Laden → Inhalt per Überblendung.
- Suche lädt mit schlichtem Spinner statt großem Logo.

### Paket 5 – Startseite

- Kopf (Gruß, Abo, „Wähle, was dich interessiert“) scrollt mit den Reihen.
- Update-Hinweis als Fläche mit Bernstein-Symbol und Hauptknopf (Muster TV `StartScreen.kt:112-122`).
- Keine Trennlinien über den Reihen, keine dauerhaft sichtbaren Scrollbalken; Reihentitel 20 sp.

### Paket 6 – Einstellungen

- **Nur umgruppieren und gestalten, keine neue Funktion** (entschieden): die vorhandenen PC-Einstellungen in eigene
  Panels nach dem Muster der TV-App (`BrowseScreens.kt:426-498`) statt eines langen „Komfort“-Blocks. PC-eigene
  Bereiche (Dashboard-Kategorien, Länderfilter, Anbieter, Updates) bleiben und bekommen je ein Panel. Der vorhandene
  Reihen-Editor (`HomeScreen.kt:1071-1076`, Häkchen + ▲/▼) wandert unverändert in das Panel „Startseite: Reihen“.
- Eckige An/Aus-Knöpfe statt Material-Switch-Kapseln.

### Paket 7 – Bewegung

Grundsatz: jeder sichtbare Zustandswechsel animiert; Alltägliches 120–220 ms, `FastOutSlowIn`, keine Federn.
Bereichs-/Bildschirmwechsel an TV angleichen (Neues 40–60 ms verzögert einblenden, Altes schneller ausblenden).
Bekannte harte Stellen (aus dem Vergleich): Seitenleiste; Kategorien `NavRow` inkl. Augen-Symbol; Senderzeile inkl.
Abspielen-/Stern-Knopf; Infospalte (nur einmal überblenden, nicht zweimal bei EPG-Nachladen); `MediaCard` Rahmen/Titel
und Stern; Kategorie-Überschrift; Laden → Inhalt und Suchergebnisse; Einstellungen (Chips, „Alle anzeigen“ per
`animateContentSize`, „Anbieter entfernen“-Bestätigung); Ladebildschirm-Texte; Update-Hinweis/Karte auf Start per
`AnimatedVisibility`.

### Kleinkram

Favoriten: Untertitel/Leertext entdoppeln, ein Symbol (Stern) für Kopf und Leerzustand. Ladebildschirm: Logo 96 dp,
Ladebalken über dem Text wie TV.

## Dem Builder überlassen

- Genaue Fenstermaße (Vorschlag: Breite `min(960 dp, 90 %)`, Höhe max. 90 % des Fensters) und Breitbild-Höhe.
- Wie die Kachel-Position fürs `transformOrigin` übergeben wird (z. B. PC-lokaler Zustand, gesetzt beim Klick via
  `onGloballyPositioned`/`boundsInWindow`) – ohne `AppState` zu berühren.
- Aufteilung in Dateien (z. B. `DetailModal.kt`), Wiederverwendung von `Credit`, `EpisodeRow`, `episodePosition`.
- Ob Skeletons/An-Aus-Knöpfe aus `androidApp` kopiert oder neu gebaut werden (kein gemeinsames UI-Modul vorhanden).
- Tests: bestehende PC-UI-Tests (`desktopApp/src/test`) anpassen, die `MovieScreen`/`SeriesScreen`/Hover-Karte
  ansprechen; neue Tests für Öffnen/Schließen (Esc, ✕, Grund) und Zurück aus dem Player.
- Reihenfolge der Umsetzung: Fenster (inkl. Hover-Rückbau) → 1 → 2 → 7 → 3 → 4 → 5 → 6, je ein Commit.

## Meine Entscheidungen (zur Prüfung)

- Hover warmweiß statt Bernstein (Style-Lock) – weicht von meinem ersten Vorschlag im Gespräch ab.
- Ausblend-Verlauf am Listenende (TV `TvChrome.kt:106`) **nicht** übernommen: Style-Lock verbietet Verläufe.
- Schließen per Klick auf den Grund zusätzlich zu ✕/Esc.
