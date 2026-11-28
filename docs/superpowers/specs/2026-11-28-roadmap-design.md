# Roadmap statt Ideen-Ordner – Design

**Stand:** 2026-11-28 · klein · freigegeben
**Plattformen:** ➖ (Doku, gilt für alle)

## Warum

Ideen liegen lose in `docs/ideen/`, offene Punkte in CLAUDE.md, Lücken in `docs/PLATTFORMEN.md` – eine Reihenfolge
gibt es nirgends. Die Roadmap ist zuerst Vincents Arbeitsplan, soll aber später auch Nutzern zeigen, was kommt.

- **Ziel:** eine Stelle, die sagt, was jetzt, als Nächstes und später dran ist – schnell zu pflegen, für Nutzer lesbar.
- **Nicht-Ziel:** Termine oder Versionszusagen; Abstimmen/Kommentieren durch Nutzer (kommt erst mit Issues, s. u.).
- **Anti-Ziel:** eine zweite Liste neben CLAUDE.md oder der Matrix, die auseinanderläuft.

## Ansätze

- **A. `docs/ROADMAP.md`** – eine Datei, per PR gepflegt; keine Nutzer-Rückmeldung.
- **B. GitHub Project + Issues** – Board mit Abstimmung; zwei Systeme, viel Aufwand je Punkt.
- **C. Erst A, später einzelne Punkte als Issues** – **gewählt**: heute so leicht wie A, Rückmeldung später ohne Umbau.

Gliederung nach Dringlichkeit (gewählt) statt nach Versionen oder Plattformen.

## Aufbau von `docs/ROADMAP.md`

```
# Roadmap
Kurzer Vorspann: keine Termine, Reihenfolge kann sich ändern. Stand: <Datum>.

## Jetzt            – woran gerade gearbeitet wird (offene PRs, laufende Aufgaben)
## Als Nächstes     – entschieden, kommt danach
## Später           – entschieden, ohne Zeitpunkt
## Ideen            – noch nicht entschieden
## Technik (intern) – Aufräumen ohne sichtbare Wirkung für Nutzer
```

Je Punkt eine Zeile: `- **Titel** (TV · PC · iPhone) – ein Satz in Nutzersprache. → Spec/Idee/PR/Issue`

## Regeln (Vorschlag)

- **Ideen:** neue Idee = eine Zeile unter „Ideen“. Eine Detailseite in `docs/ideen/` nur, wenn es Substanz gibt
  (Datenlage, Skizze, offene Fragen) – die beiden vorhandenen wandern so ins Repo.
- **Weg eines Punkts:** Idee → entschieden (Als Nächstes/Später) → Spec → Jetzt (PR) → beim Merge aus der Roadmap
  streichen; der Stand steht dann in `PLATTFORMEN.md`.
- **Matrix-Lücken:** ❌-Zeilen bleiben in `PLATTFORMEN.md`. Erst wenn eine entschieden „machen“ heißt, wird sie ein
  Roadmap-Punkt. Die Roadmap verlinkt die Matrix für alle offenen Lücken.
- **CLAUDE.md:** Abschnitt „Offene Punkte“ zieht in die Roadmap um; CLAUDE.md verweist nur noch darauf, plus Regel
  „PR, der einen Roadmap-Punkt erledigt, streicht ihn“.
- **README:** Link auf die Roadmap erst, wenn Nutzer sie sehen sollen (Vincents Entscheidung, nicht Teil dieser Änderung).
- **Issues später:** Soll ein Punkt Rückmeldung bekommen, bekommt er ein Issue, verlinkt in seiner Zeile.

## Erste Befüllung (Vorschlag, beim Umsetzen mit Vincent abgleichen)

- **Jetzt:** natives Video am PC (#47) · Microsoft-Store-Einreichung (Upload ins Partner Center).
- **Später:** Englische Oberfläche (Texte in Ressourcen, Stufen 1–4) · Diagnose-Log („Diagnose kopieren“).
- **Ideen:** Bereich „Verpasst“ (Catch-up) · Player ↔ IPTV Manager (Richtung entschieden, Umsetzung offen).
- **Technik (intern):** Guards in `fetchEpg`/`loadSeasons`, Asserts in `AccountSwitchTest`, Benennung `Favorites`↔`HistoryStore`.
- **Als Nächstes:** bleibt leer, bis Vincent entscheidet – Kandidaten sind die ❌-Zeilen der Matrix.

## Dem Umsetzenden überlassen

Formulierung der Zeilen in Nutzersprache, Vorspann-Text.
