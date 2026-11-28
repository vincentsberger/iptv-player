# Idee: eigener Bereich „Verpasst“ (Catch-up)

Stand: 2026-11-27 · Status: Idee, nicht entschieden

## Idee

Neben Live-TV, Filmen und Serien ein eigener Bereich, der **vergangene Sendungen aus dem Anbieter-Archiv** zeigt – „was lief in den letzten Tagen auf Sendern mit Archiv“ –, statt dass man Catch-up nur findet, wenn man im EPG eines einzelnen Senders zurückblättert.

## Datenlage beim eigenen Anbieter (ausgewertet 2026-11-27)

Quelle: zwischengespeichertes `get_live_streams` der Desktop-App vom 26.11.2026 (nur das Flag `tv_archive`, keine Abspielprobe).

- **864 von 32 294 Sendern (2,7 %)** haben `tv_archive: 1`, alle mit `tv_archive_duration: 3` (Tage). Verteilt auf 34 von 580 Kategorien.
- Nach Region: UK 293 (fast alle General-Sender, dazu Sport, Sky Cinema, News, Doku) · Israel 148 · Griechenland 91 · Niederlande 78 · **Deutschland 51** · Schweden 45 · Belgien 44 · Polen 30 · Rest einzeln.
- **Deutsch nur WOW:** WOW Sport (27 von 28) und WOW Entertainment (24 von 25). **Kein Archiv** für ARD, ZDF, RTL & Co.
- Für einen deutschsprachigen Nutzer hieße „Verpasst“ also im Kern: WOW Sport + WOW Entertainment, 3 Tage zurück.

⚠️ Offen: Ob `tv_archive: 1` wirklich abspielbar ist, belegt erst `has_archive` je Sendung (`get_simple_data_table`) plus ein Timeshift-Abruf. Stichprobe vor dem Bauen machen.

## Was es im Player schon gibt

- Modell: `archiveDays` (`shared/.../data/Models.kt:47`), `hasArchive` je EPG-Eintrag (`Models.kt:101`), `timeshiftStart` (`Models.kt:401`).
- URL-Bau: `XtreamClient.timeshiftUrl(streamId, entry)` (`shared/.../data/XtreamClient.kt:212`).
- Abspielen aus dem EPG eines Senders: `ProgrammePanel.kt` (Android), `HomeScreen.kt` (Desktop); Tests in `CatchupTest.kt`.
- Länderfilter (`liveCountries`) – ein „Verpasst“-Bereich sollte ihn respektieren, sonst dominieren UK/IL.

## Skizze

- Quelle: alle Sender mit `archiveDays > 0` innerhalb der gewählten Länder.
- Inhalt: Sendungen mit `hasArchive` der letzten `archiveDays` Tage, gruppiert z. B. nach „Gestern / Vorgestern / …“ oder nach Sender; Sport ggf. hervorgehoben.
- Kosten: pro Archiv-Sender ein `get_simple_data_table`-Abruf → bei 51 DE-Sendern vertretbar, bei 864 nicht ohne Cache/Begrenzung. Alternative: `xmltv.php` einmal laden und lokal filtern.
- Bereich nur anzeigen, wenn es im gewählten Länderfilter überhaupt Archiv-Sender gibt.

## Offene Fragen

- Lohnt sich ein eigener Bereich bei ~51 relevanten Sendern, oder reicht ein Filter „nur Sender mit Archiv“ in Live-TV plus Kennzeichnung im EPG?
- Struktur: nach Tag, nach Sender oder nach Genre (Sport/Unterhaltung)?
- Zusammenspiel mit dem IPTV Manager: Der Manager könnte Archiv-Sender als eigene Kategorie ausgeben (z. B. „Verpasst – 3 Tage“) – dann bräuchte der Player gar keinen eigenen Bereich. Bei gruppierten Sender-Varianten darauf achten, dass die bevorzugte Variante das Archiv auch hat.
