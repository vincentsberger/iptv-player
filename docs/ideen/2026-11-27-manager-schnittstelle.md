# Idee: Player ↔ IPTV Manager – Xtream erweitern statt eigene Schnittstelle

Stand: 2026-11-27 · Status: Idee, Richtung entschieden, Umsetzung offen

## Ausgangslage

Der IPTV Manager (`../iptv-manager`) ist selbst ein Xtream-Server: `player_api.php`, `get.php`, `xmltv.php` und die Stream-Routen (`/live`, `/movie`, `/series`, `/timeshift` → Weiterleitung zum Anbieter). Der Player meldet sich dort mit den echten Anbieter-Zugangsdaten an und sieht den aufgeräumten Katalog (eigene Kategorien, eigene Reihenfolge, neu vergebenes `num`, gruppierte Varianten).

## Entscheidung (Richtung)

**Xtream bleibt die Basis; für den eigenen Player kommen optionale Zusätze dazu – keine zweite, vollwertige Schnittstelle.**

- Ein Codepfad im Player: Der Manager ist für die App „ein besonders guter Anbieter“; die App funktioniert weiter direkt mit jedem Anbieter.
- Fremde Player (TiviMate, Smarters …) bleiben nutzbar.
- Erweitern statt ersetzen: zusätzliche JSON-Felder ignorieren fremde Player.

## Stufe 1 – optionale Felder im Xtream-Format (ohne neue Endpunkte)

- `variants` je Eintrag (gibt es schon im Manager): Liste der Qualitäten (`stream_id`/`series_id`, `quality`, `codec`, `container_extension`) → Qualitätsauswahl vor der Wiedergabe. Ohne Feld Verhalten wie bisher. (Folgeaufgabe „TV-App liest `variants`“ aus der Manager-Spec.)
- Fähigkeiten-Hinweis in der Login-Antwort, z. B. `server_info.manager = { "version": 1, "features": ["variants"] }` – daran erkennt die App den Manager und schaltet Zusatzfunktionen frei.
- Denkbar: Archiv-Info je Gruppe (welche Variante hat `tv_archive`), Herkunfts-Kategorie, „neu“-Kennzeichen.

## Stufe 2 – kleine Zusatz-API, erst wenn Xtream es nicht ausdrücken kann

Xtream ist rein lesend. Eine Zusatz-API (z. B. `/api/player/v1`, Anmeldung mit denselben Xtream-Zugangsdaten, kein zweiter Login) lohnt sich für:

- Favoriten und Verlauf geräteübergreifend synchronisieren (Fire TV ↔ Desktop ↔ iPhone)
- „Weiterschauen bei Minute X“ auf allen Geräten
- serverseitig berechnete Listen, z. B. „Verpasst“ aus EPG + Archiv (siehe `2026-11-27-catch-up-bereich.md`)
- Hinweise wie „neue Folgen“

Die App nutzt sie nur, wenn `features` sie meldet; ohne Manager bleibt alles lokal wie heute.

## Offene Fragen

- Welche Zusatzfunktion zuerst – `variants` (Stufe 1) oder Sync (Stufe 2)?
- Sync-Konflikte: letzte Änderung gewinnt, oder Zusammenführen je Eintrag?
- Gehört die Zusatz-API in den Manager-Spec (eigener Plan) – vermutlich ja, nach der Verwaltungsoberfläche.
