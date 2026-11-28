# Roadmap

Was als Nächstes kommt – **ohne Termine**, die Reihenfolge kann sich ändern. Was die App heute auf welcher Plattform
kann und wo es noch Lücken gibt, steht in [PLATTFORMEN.md](PLATTFORMEN.md).

**Stand:** 28.11.2026 (nach Feature-Review) · Plattformen: TV = Fire TV / Android TV, PC = Windows/Linux. Hat ein Punkt ein verlinktes
Issue, ist Rückmeldung dort willkommen.

## Jetzt

- **Microsoft Store** (PC, Windows) – die PC-App kommt in den Microsoft Store; das Paket steht, die Einreichung steht an.
  → [Spec](superpowers/specs/2026-11-26-microsoft-store-msix-design.md)

## Als Nächstes

Reihenfolge und Begründung: [Feature-Review](ideen/2026-11-28-feature-review.md).

- **Programm auch für M3U-Playlisten** (TV · PC · iPhone) – Sendungsdaten (XMLTV) aus der Playlist oder von einer
  eigenen Adresse; bisher zeigen nur Xtream-Anbieter ein Programm.
- **Programmführer als Raster** (TV zuerst, dann PC · iPhone) – alle Sender mit ihren Sendungen auf einer Zeitleiste.
- **Erinnerungen an Sendungen** (TV · PC · iPhone) – aus dem Programm „Erinnern“ wählen, kurz vor Beginn ein Hinweis,
  auf Wunsch schaltet die App um.

## Später

- **Englische Oberfläche** (TV · PC · iPhone) – die App spricht die Sprache des Geräts, zunächst Deutsch und Englisch.
  Entschieden, zurückgestellt.
- **„Diagnose kopieren“** (TV · PC · iPhone) – ein lokales Protokoll, das man bei Problemen mit einem Knopf in den
  Einstellungen kopieren und mitschicken kann.
- **Sender verwalten** (TV · PC · iPhone) – Sender umbenennen, sortieren, ausblenden und in eigene Gruppen legen.
- **Einstellungen übertragen und sichern** (TV · PC · iPhone) – als Datei oder per QR-Code vom Handy auf den Fernseher,
  ohne Konto und ohne Server.
- **Kindersicherung per PIN** (TV · PC · iPhone) – Erwachsenen-Kategorien nur nach PIN-Eingabe sichtbar und abspielbar.
- **Bildwiederholrate anpassen** (TV) – der Fernseher schaltet auf die Bildrate des Films, damit nichts ruckelt.

## Ideen

Noch nicht entschieden. Größere Vorhaben warten auf Bedarf (Rückmeldung gern per Issue).

- **Live-TV pausieren und zurückspulen** (TV · PC) – ein Zwischenspeicher für den laufenden Sender.
- **Bild-in-Bild** (TV · PC · iPhone) – Sender in einem kleinen Fenster weiterlaufen lassen.
- **Aufnahme** (TV · PC) – Sendungen aus dem Programm aufnehmen und später ansehen.
- **Mehrere Sender gleichzeitig** (TV · PC) – z. B. vier Spiele nebeneinander; hängt an der Verbindungszahl des Anbieters.
- **AirPlay** (iPhone) – Wiedergabe auf Apple TV oder AirPlay-Fernseher.

- **Bereich „Verpasst“** (TV · PC · iPhone) – Sendungen der letzten Tage gesammelt an einer Stelle statt nur im
  Programm einzelner Sender. → [Idee](ideen/2026-11-27-catch-up-bereich.md)
- **Zusammenspiel mit dem IPTV Manager** (TV · PC · iPhone) – der eigene Server liefert dem Player Zusatzinfos; Richtung entschieden
  (Xtream erweitern statt eigener Schnittstelle), Umsetzung offen. → [Idee](ideen/2026-11-27-manager-schnittstelle.md)

**Bewusst nicht:** eingebautes VPN, DRM, Reseller-Konten, Plex/Jellyfin/HDHomeRun als Quelle.

## Technik (intern)

Aufräumen ohne sichtbare Wirkung, dazu der Umsetzungsweg für Punkte oben.

- Weg zur englischen Oberfläche: `interface Strings` + `object De`/`En` in `shared/i18n`, Auswahl per
  `expect fun deviceLanguage()`; Stufen 1) shared + TV, 2) PC, 3) iPhone, 4) Englisch; ~490 Texte; Vergleiche mit Text
  (z. B. `title == "Weiterschauen"`) auf Schlüssel umstellen.
- Guards in `fetchEpg`/`loadSeasons` (aus dem AppState-Review).
- Mehr Asserts in `AccountSwitchTest`.
- Benennung `Favorites` ↔ `HistoryStore` vereinheitlichen.
- Arbeitsvorrat für „Diagnose kopieren“: `TooGenericExceptionCaught`/`SwallowedException` in der detekt-Baseline.

## Pflege

Weg eines Punkts: **Idee** (eine Zeile unter „Ideen“, eine Detailseite in `ideen/` nur bei Substanz) → **entschieden**
(„Als Nächstes“ oder „Später“) → **Spec** → **Jetzt** (offener PR oder laufende Aufgabe) → beim Merge hier streichen; der Stand steht dann in
[PLATTFORMEN.md](PLATTFORMEN.md). Soll ein Punkt Rückmeldung bekommen, bekommt er ein Issue, verlinkt in seiner Zeile.
