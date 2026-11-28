# Idee: Datensparmodus – Fassung nach Bitrate wählen

Stand: 2026-11-28 · Status: Idee, nicht entschieden

**Plattformen:** TV · PC · iPhone (Logik in `shared`)

## Ausgangslage

Bei schlechter Verbindung ruckeln FHD- und 4K-Streams. Eine Messung am echten Anbieterkatalog (über den IPTV Manager, 28.11.2026, nur `DE|`-Kategorien) zeigt:

- **Filme:** 12.478 Titel, davon 8.966 in **mehreren Fassungen**. Fast keine trägt eine Qualitätsangabe im Namen (nur 4K ist beschriftet). Die Fassungen unterscheiden sich aber deutlich in der **Bitrate**, die `get_vod_info` liefert (`info.bitrate`, Stichprobe: 1, 2, 5 und 7 Mbit/s für denselben Film). `info.video.height` fehlte in der Stichprobe.
- **Live-TV:** 2.219 Sender, 606 in FHD/4K, nur 82 davon zusätzlich in HD/SD. Hier hilft eine Fassungswahl wenig (dafür bräuchte es Transcoding, eigenes Thema).

## Idee

- Die vorhandene **Quellenwahl bei mehreren Streams** zeigt je Fassung die Bitrate („≈ 2 Mbit/s“). Die Bitrate fragt der Player vor dem Abspielen per `get_vod_info` je Fassung ab: nur wenige Aufrufe, Ergebnis zwischenspeichern.
- **Einstellung „Datensparmodus“:** Ist sie an, wählt der Player automatisch die Fassung mit der kleinsten Bitrate über einer Untergrenze (z. B. ≥ 1 Mbit/s, damit es keine Briefmarke wird). Ist sie aus, bleibt das heutige Verhalten.
- Fassungen kennt der Player über das `variants`-Feld des IPTV Managers (Stufe 1 aus `2026-11-27-manager-schnittstelle.md`) oder über seine eigene Gruppierung.

## Offene Fragen

- Untergrenze fest oder einstellbar?
- Automatisch umschalten, wenn die Wiedergabe mehrfach puffert („Verbindung schwach – kleinere Fassung nehmen?“)?
- Serien: Liefert `get_series_info` je Episode eine Bitrate, oder nur bei Filmen?
