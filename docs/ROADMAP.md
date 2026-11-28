# Roadmap

Was als Nächstes kommt – **ohne Termine**, die Reihenfolge kann sich ändern. Was die App heute auf welcher Plattform
kann und wo es noch Lücken gibt, steht in [PLATTFORMEN.md](PLATTFORMEN.md).

**Stand:** 28.11.2026 · Plattformen: TV = Fire TV / Android TV, PC = Windows/Linux. Hat ein Punkt ein verlinktes
Issue, ist Rückmeldung dort willkommen.

## Jetzt

- **Microsoft Store** (PC, Windows) – die PC-App kommt in den Microsoft Store; das Paket steht, die Einreichung läuft.
  → [Spec](superpowers/specs/2026-11-26-microsoft-store-msix-design.md)

## Als Nächstes

Noch nichts festgelegt. Kandidaten sind die ❌-Zeilen in [PLATTFORMEN.md](PLATTFORMEN.md).

## Später

- **Englische Oberfläche** (TV · PC · iPhone) – die App spricht die Sprache des Geräts, zunächst Deutsch und Englisch.
  Entschieden, zurückgestellt.
- **„Diagnose kopieren“** (TV · PC · iPhone) – ein lokales Protokoll, das man bei Problemen mit einem Knopf in den
  Einstellungen kopieren und mitschicken kann.

## Ideen

Noch nicht entschieden.

- **Bereich „Verpasst“** (TV · PC · iPhone) – Sendungen der letzten Tage gesammelt an einer Stelle statt nur im
  Programm einzelner Sender. → [Idee](ideen/2026-11-27-catch-up-bereich.md)
- **Zusammenspiel mit dem IPTV Manager** – der eigene Server liefert dem Player Zusatzinfos; Richtung entschieden
  (Xtream erweitern statt eigener Schnittstelle), Umsetzung offen. → [Idee](ideen/2026-11-27-manager-schnittstelle.md)

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
(„Als Nächstes“ oder „Später“) → **Spec** → **Jetzt** (offener PR) → beim Merge hier streichen; der Stand steht dann in
[PLATTFORMEN.md](PLATTFORMEN.md). Soll ein Punkt Rückmeldung bekommen, bekommt er ein Issue, verlinkt in seiner Zeile.
