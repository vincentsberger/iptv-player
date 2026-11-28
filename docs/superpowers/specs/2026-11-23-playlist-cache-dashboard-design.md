# Playlist-Cache, Lade-Übersicht und Dashboard

**Stand:** 23.11.2026 · Größe: Projekt in zwei Teilen (1: Cache + Übersicht, 2: Dashboard) · Plattformen: Android TV und Desktop  
**Plattformen:** TV ✅ · PC ✅ · iPhone ✅

## Warum

- Nach dem Laden will der Nutzer **prüfen, ob die Playlist vollständig geladen ist** – heute schließt sich der
  Ladebildschirm von selbst, die Zahlen sind kaum zu sehen und es gibt keinen Vergleich.
- Die App lädt bei **jedem** Start alle Listen neu (Beispielanbieter: 32.294 Sender, 140.789 Filme, 31.274 Serien;
  108 MB JSON, ~20 MB übertragen, 5–10 s am PC). Ein Cache macht den Start an den meisten Tagen sofort.
- Auf der Startseite sollen **Neuheiten** erscheinen (neue Filme, neue Folgen) – aber nur aus Kategorien, die den
  Nutzer interessieren (beim Beispielanbieter sind von 389 neuen Filmen in 7 Tagen nur 14 deutsch, 33 „FOR ADULTS“;
  Serienkategorien tragen gar keine Länderkürzel).

## Ziele

- Listen pro Anbieter zwischenspeichern; Start aus dem Cache, wenn er jünger als 24 h ist.
- Nach jedem Start eine **Übersicht**, die immer anhält: Anzahl je Bereich, Veränderung zum letzten Laden,
  Warnung bei Auffälligem, Knöpfe „Neu laden“ und „Weiter“.
- „Listen neu laden“ in den Einstellungen beider Plattformen.
- Startseite um Abo-Zeile, „Neue Filme“ und „Neue Folgen“ erweitern, gespeist aus selbst gewählten Kategorien.

## Nicht-Ziele

- Eigenes Speicherformat oder Datenbank (siehe Ansätze).
- „Neu“ im Sinne von „seit meinem letzten Besuch“ – gewählt ist „in den letzten 7 Tagen“ laut Anbieterfeld.
- Reihe „Neue Sender“.
- Länderfilter auf Filme/Serien ausweiten (Filme nur zu 73 %, Serien gar nicht gekennzeichnet).
- EPG zwischenspeichern (bleibt wie heute im Speicher mit eigener Gültigkeit).

## Anti-Ziele

- Eine leere oder halbe Liste, weil ein Bereich beim Neuladen fehlschlug – dann lieber der alte Stand aus dem Cache.
- Erwachseneninhalte auf der Startseite.
- Ein Start, der still auf veralteten Daten läuft, ohne dass der Stand erkennbar ist.

## Ansätze (Cache)

- **A – Anbieter-Antworten unverändert speichern (gewählt):** je Liste eine gzip-Datei pro Anbieter (~20 MB beim
  Beispielanbieter). Laden aus dem Cache nutzt denselben Parser wie heute; kein eigenes Format, keine Migration;
  neue Felder (`added`, `rating`, …) sind automatisch enthalten. Kosten: beim Start wird weiter das ganze JSON geparst
  (PC < 1 s, TV-Box geschätzt 2–4 s).
- **B – Eigenes kompaktes Format:** kleiner/schneller, aber eigener Serializer, Versionierung, Formatänderung bei jedem neuen Feld. Verworfen.
- **C – SQLite:** Teilabfragen möglich, aber größter Umbau (neue Bibliothek, heutige Logik arbeitet mit Listen im Speicher). Verworfen.

## Teil 1 – Cache, Übersicht, Neu laden

### Cache

- **Naht:** `Repository` bekommt seine Daten über `HttpSource` (`getText`) bzw. `httpReadLines` (M3U). Der Cache sitzt
  dazwischen: Listen-Abrufe (`get_live_categories`, `get_live_streams`, `get_vod_categories`, `get_vod_streams`,
  `get_series_categories`, `get_series`, bei M3U die Playlist) werden beim Laden aus dem Netz zusätzlich gespeichert;
  beim Laden aus dem Cache wird die Datei statt des Netzes gelesen. Login, Serien-Details (`get_series_info`) und EPG
  gehen immer ins Netz.
- **Ablage:** pro Anbieter (Anbieter-ID wie in `Storage`) ein Verzeichnis; Desktop unter dem vorhandenen Datenordner
  (`~/.freeiptvplayer/cache/<id>/`, Windows `%APPDATA%\FreeIPTVPlayer\cache\<id>\`), Android im App-internen
  Speicher (`filesDir/playlist-cache/<id>/`, nicht `cacheDir`, damit das System ihn nicht still leert).
  Dateien gzip-komprimiert. Ein Abruf wird erst nach vollständigem Empfang geschrieben (temporäre Datei + Umbenennen).
- **Metadaten** pro Anbieter **und Bereich** (im `Storage`, klein): Zeitpunkt des letzten erfolgreichen Ladens,
  Anzahl beim letzten und vorletzten Laden (für die Veränderung).
- **Zugangsdaten:** Dateinamen nach Aktion (`get_vod_streams.json.gz`), nie die URL. Der Inhalt kann Zugangsdaten
  enthalten (M3U-Stream-URLs, Xtream `direct_source`) – deshalb werden Cache-Dateien **genauso geschützt wie die
  übrigen App-Daten**: Windows mit DPAPI (vorhandener `DataProtector`), Linux/Android nur für den Benutzer bzw. die App
  lesbar (Rechte 600 bzw. App-interner Speicher).
- **Verdächtige Antworten überschreiben den Cache nicht:** Schlägt ein Bereich fehl oder kommt er leer zurück,
  obwohl er beim letzten Laden Einträge hatte, bleibt die alte Datei samt altem Stand. „Mehr als 10 % weniger“
  wird gespeichert (der Anbieter kann wirklich aufgeräumt haben), aber in der Übersicht gewarnt.
- **Abmelden/Anbieter entfernen** löscht dessen Cache-Verzeichnis.

### Ablauf beim Start

- Alle drei Bereiche im Cache **und** jeder jünger als 24 h (und beim letzten Mal ohne Fehler) → Listen aus dem
  Cache laden (kein Download), dann Übersicht.
- Sonst → Ladebildschirm wie heute (Fortschritt je Bereich) mit Netzabruf **aller** Bereiche, dann Übersicht.
- **Was als Start zählt:** Kaltstart der App; Anmeldung und Anbieterwechsel; **Android TV zusätzlich die Rückkehr
  in die App, wenn sie länger als 1 Stunde im Hintergrund war** (der Prozess läuft dort oft tagelang weiter).
  Desktop: nur Kaltstart, Anmeldung, Anbieterwechsel. „Erneut versuchen“ im Fehlerbildschirm lädt aus dem Netz und
  zeigt danach die Übersicht.
- Schlägt beim Netzabruf ein Bereich fehl und gibt es für ihn einen Cache → dieser Bereich kommt aus dem Cache,
  in der Übersicht als „Fehler – Stand vom …“ markiert. Ohne Cache: „Fehler“, Bereich leer (wie heute).
- „Beim Start den letzten Sender abspielen“ greift erst nach „Weiter“.
- Die 24 h sind ein fester Wert, keine Einstellung.

### Übersicht

```
Playlist                                   tv.example · Stand: heute 08:12
  Live TV    32.294 Sender   ✓   +12 seit letztem Laden
  Filme     140.789 Filme    ✓   +389
  Serien     31.274 Serien   ⚠   −18.402 – deutlich weniger als zuletzt
                                       [ Neu laden ]   [ Weiter ▸ ]
```

- Hält **immer** an – auch beim Start aus dem Cache.
- Zahlen = Einträge je Bereich (ungefiltert, wie vom Anbieter geliefert). Veränderung gegenüber dem vorherigen
  Laden; beim Start aus dem Cache ohne neues Laden gilt die beim letzten Laden ermittelte Veränderung.
- **Warnung (⚠)**, wenn ein Bereich einen Fehler hatte, leer ist, obwohl er zuvor Einträge hatte, oder mehr als 10 %
  weniger Einträge als beim vorherigen Laden hat.
- „Stand“ = Zeitpunkt des letzten vollständigen Ladens.
- „Weiter“ hat den Fokus (TV: ein OK genügt) und führt auf die Startseite bzw. den zuletzt genutzten Bereich wie heute.
- „Neu laden“ holt alle Listen frisch aus dem Netz (Ladebildschirm), danach wieder die Übersicht.

### Neu laden in den Einstellungen

- Android TV: vorhandener Knopf „Listen neu laden“ bleibt, führt jetzt über Ladebildschirm → Übersicht.
- Desktop: vorhandener Knopf „Listen neu laden“ (`HomeScreen.kt`, Einstellungen) ebenso.

## Teil 2 – Dashboard (Startseite)

Die vorhandene Startseite wird erweitert, nicht ersetzt. Reihenfolge:

```
Startseite
  Abo: läuft bis 14.03.2027 · 1 von 2 Verbindungen      ⚠ ab 7 Tagen vor Ablauf hervorgehoben
  Weiterschauen            (wie heute)
  Neue Filme               letzte 7 Tage, aus deinen Kategorien, neueste zuerst   → „Alle anzeigen“
  Neue Folgen              Serien mit neuen Folgen (last_modified ≤ 7 Tage)       → „Alle anzeigen“
  Zuletzt gesehen          (wie heute)
  Favoriten · Downloads    (wie heute)
```

- **Abo-Zeile** (nur Xtream): aus der Login-Antwort `user_info.exp_date`, `max_connections`, `active_cons`. Heute
  meldet sich die App beim Start nicht an; neu: bei jedem Start (auch aus dem Cache) ein Login-Abruf (wenige KB),
  dessen `user_info` im `Storage` gespeichert wird. Ohne Netz zeigt die Zeile die zuletzt bekannten Werte mit Datum;
  hervorgehoben, wenn das Abo in ≤ 7 Tagen abläuft. Fehlt `exp_date` (unbegrenzt) → „unbegrenzt“. Bei M3U keine Zeile.
- **Neue Filme:** Filme mit `added` innerhalb der letzten 7 Tage (bezogen auf die aktuelle Uhrzeit), nur aus den
  gewählten Filmkategorien, neueste zuerst.
- **Neue Folgen:** Serien mit `last_modified` innerhalb der letzten 7 Tage, nur aus den gewählten Serienkategorien,
  zuletzt geänderte zuerst.
- „Alle anzeigen“ öffnet die vollständige Liste der jeweiligen Reihe.
- Leere Reihe → entfällt.
- **Kategorienauswahl** („Für die Startseite“): Positivliste je Bereich (Filme, Serien), pro Anbieter gespeichert.
  Solange nichts gewählt ist, steht an Stelle der beiden Reihen eine Karte „Wähle, was dich interessiert“, die die
  Auswahl öffnet. Änderbar in den Einstellungen. Neue Kategorien des Anbieters sind nicht automatisch gewählt.
- **Erwachseneninhalte** erscheinen nie in den Reihen und nicht in der Auswahl: Kategorie „FOR ADULTS“ bzw. Einträge
  mit `is_adult = 1`.
- M3U-Playlists haben kein `added`/`last_modified` → keine „Neu“-Reihen, keine Auswahl.

## Was der Builder entscheidet

- Genaue Form der Cache-Naht (Wrapper um `HttpSource` vs. Parameter in `Repository.load`), solange Login/Details/EPG nie aus dem Cache kommen.
- Wie `added`/`last_modified`/`is_adult` ins Modell kommen (z. B. optionale Felder an `Item`).
- Layout der Kategorienauswahl im Rahmen der bestehenden Komponenten (Android: fokussierbare `TvButton`s, Desktop: Chips wie beim Länderfilter).
- Erkennung von „FOR ADULTS“ über den Kategorienamen (Groß/Klein egal, enthält „ADULT“) zusätzlich zu `is_adult`.

## Tests

- Cache: Speichern nach Netzabruf, Laden aus Cache < 24 h, Netzabruf ≥ 24 h oder nach Teilfehler, Fehler-/Leer-Antwort überschreibt Cache nicht, Löschen bei Abmelden, keine Zugangsdaten in Dateinamen, Schutz wie `data.dat`.
- Start-Erkennung: Android-Rückkehr nach > 1 h löst Prüfung + Übersicht aus, nach < 1 h nicht.
- Übersicht: Veränderung, ⚠-Regeln (Fehler, leer, > 10 % weniger), hält immer an, „Weiter“/„Neu laden“, Letzter-Sender-Start erst nach „Weiter“.
- Dashboard: 7-Tage-Grenze, nur gewählte Kategorien, Erwachsene ausgeschlossen, leere Reihen entfallen, Abo-Ablaufwarnung.
- UI-Tests je Plattform (Desktop in CI, Android als Emulator-Test).
