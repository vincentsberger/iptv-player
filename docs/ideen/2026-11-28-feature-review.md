# Feature-Review: eigener Stand gegen andere IPTV-Apps

Stand: 2026-11-28 · Status: eingearbeitet in [ROADMAP.md](../ROADMAP.md)

Eigener Stand aus [PLATTFORMEN.md](../PLATTFORMEN.md) plus Code-Suche nach typischen Pro-Funktionen. Verglichen mit
TiviMate, OTT Navigator, Sparkle TV, IPTV Smarters Pro, XCIPTV, Televizo, Purple (Android/Fire TV) sowie iPlayTV,
IPTVX, UHF, GSE Smart IPTV, Channels, Infuse, MyIPTV, Kodi + IPTV Simple, Hypnotix (Apple/PC). Reddit war für die
Recherche gesperrt – Nutzerstimmen stammen aus Store-Bewertungen, Trustpilot und Reddit-Aggregatoren.

## Kurzfassung

- **Unser Profil ist schon stark** bei dem, was andere hinter Pro verstecken oder schlecht machen: werbefrei, kein
  Konto, mehrere Anbieter gratis, Catch-up gratis, Downloads (haben auf Android fast alle nicht), Weiterschauen,
  Statistik, PC-App mit Tastatur. Nutzer klagen bei der Konkurrenz über Werbung, Abo-Fallen, Konto-Zwang,
  verschwundene Apps und Datenverlust – genau dort liegt unser Vorteil.
- **Die größte Lücke ist das Programm:** Alle Konkurrenten haben ein **EPG-Raster** (meist gratis), und
  **M3U-Playlisten bekommen bei uns gar kein EPG** – `M3uParser` liest weder `tvg-id` noch `url-tvg`, eine
  XMLTV-Quelle gibt es nicht.
- **Typische Pro-Funktionen**, die uns fehlen: Kanäle verwalten (umbenennen, sortieren, eigene Gruppen),
  Erinnerungen, Kindersicherung per PIN, Backup/Übertragen der Einstellungen, Timeshift, Aufnahme, Multi-View,
  Auto-Frame-Rate.

## Was andere haben (verdichtet)

F = bei den meisten gratis · P = meist Pro · selten = nur wenige Apps

| Funktion | Andere | Wir | Anmerkung |
|---|:-:|:-:|---|
| EPG-Raster über alle Sender | F (alle) | ❌ | TiviMate wird genau dafür gelobt |
| XMLTV/EPG für M3U, mehrere EPG-Quellen, Zuordnung von Hand | F/P | ❌ | M3U-Nutzer sehen heute kein Programm |
| Catch-up/Archiv | F/P (TiviMate: P) | ✅ | Kodi kennt 5 Catch-up-Arten (Flussonic, Shift …) |
| Timeshift (Live pausieren/zurückspulen) | P (Sparkle, Channels), Kodi F | ❌ | nur Archiv über Xtream-`timeshift`-URL |
| Aufnahme/DVR | P (TiviMate, OTT, Sparkle), UHF über eigenen Server | ❌ | OTT: auf USB/SMB/WebDAV |
| Erinnerungen, optional automatisch umschalten | P (TiviMate), F (OTT, XCIPTV) | ❌ | baut auf EPG auf |
| Multi-View | P (TiviMate 9, OTT 20, UHF 4) | ❌ | UHF: 4 Bilder trotz 1 Verbindung |
| Bild-in-Bild | F/P | ❌ | Android TV und iOS bieten System-PiP |
| Kindersicherung/PIN | P (TiviMate, Televizo, Sparkle) | ❌ | Erwachsenen-Kategorien erkennen wir schon (`isAdultCategory`) |
| Profile | selten (OTT P, Smarters, Infuse, Kodi) | ❌ | UHF-Nutzer wünschen es |
| Kanäle umbenennen/sortieren, eigene Favoritengruppen | P (fast alle) | 🟡 | Kategorien ausblenden ✅, Sender nicht |
| Backup/Übertragen der Einstellungen | P (TiviMate, Televizo), F (OTT, XCIPTV) | ❌ | Apple-Apps: iCloud-Sync, wird gelobt |
| Sync zwischen Geräten | P/selten | ❌ | meist über Konto/Cloud |
| Auto-Frame-Rate (TV) | P (TiviMate, OTT), F (Sparkle) | ❌ | Bildrate kennen wir schon (`ExoPlaybackEngine`) |
| Metadaten TMDB/Trakt | selten | ❌ | braucht API-Schlüssel |
| Cast/AirPlay | F (Smarters, Televizo, iOS-Apps) | ❌ | iPhone-AirPlay steht in der iOS-Spec als „später“ |
| Untertitel-Styling, OpenSubtitles | P/selten | ❌ | |
| Downloads/Offline | selten (IPTVX, UHF, Channels) | ✅ TV, iPhone | auf Android fast einzigartig |
| Weiterschauen, Verlauf, Suche | F | ✅ | |
| Statistik/Stream-Infos | selten (Infuse, Kodi) | ✅ | |
| Sleep-Timer | F/P | ✅ | |
| Mehrere Playlists | P (TiviMate, Sparkle) | ✅ gratis | |

Außerhalb unseres Rahmens (bewusst nicht): eingebautes VPN, DRM, Reseller-Konten, Plex/Jellyfin/HDHomeRun als Quelle,
KI-Infopanel, Werbe-Überspringen bei Aufnahmen.

## Was Nutzer loben und vermissen (über alle Apps)

- **Gelobt:** schnelles, klares EPG; flüssiger Senderstart; Sync Handy → TV ohne Abtippen; gute Suche über alle
  Playlists; fairer Einmalkauf statt Abo.
- **Vermisst/kritisiert:** Werbung und Kauf-Popups, Abo-Zwang, Konto-Ärger; Playlists oder Einstellungen nach Update
  weg; kein Sync von Favoriten/Verlauf zwischen Geräten; Ruckeln nach Updates; Apps verschwinden aus den Stores;
  Wiedergabe stoppt beim Sperren (GSE).

## Kandidaten, priorisiert

Bewertung: Nutzen (wie oft vermisst, wie sichtbar) gegen Aufwand (shared-Logik vs. drei UIs).

### Stufe 1 – Grundlage, hoher Nutzen

- **1. EPG für M3U (XMLTV)** (alle) – `url-tvg`/`x-tvg-url` aus der Playlist, eigene EPG-Adresse in den Einstellungen,
  Zuordnung über `tvg-id`, sonst Name. Fast nur `shared`, UI je Plattform klein. Ohne das sind M3U-Nutzer zweitklassig.
- **2. EPG-Raster** (TV zuerst, dann PC, iPhone) – die Funktion, für die TiviMate gekauft wird. Aufwand hoch, weil
  drei UIs; Datenmodell (EPG je Sender) gibt es schon.
- **3. Erinnerungen an Sendungen** (alle) – aus Programm/Raster „Erinnern“, Hinweis kurz vor Beginn, optional
  automatisch umschalten. Klein, sobald 2 steht; TiviMate verlangt dafür Pro.

### Stufe 2 – typische Pro-Funktionen, mittlerer Aufwand

- **4. Sender verwalten** (alle) – umbenennen, sortieren, ausblenden, eigene Favoritengruppen. Logik in `shared`
  (wie ausgeblendete Kategorien), UI je Plattform.
- **5. Einstellungen übertragen und sichern** (alle) – Export/Import als Datei; auf dem TV per QR wie beim Handy-Login
  (Infrastruktur steht). Kein Konto, kein Server – passt zum Profil. Später optional über den IPTV Manager
  (siehe [Manager-Schnittstelle](2026-11-27-manager-schnittstelle.md)).
- **6. Kindersicherung per PIN** (alle) – Erwachsenen-Kategorien werden schon erkannt; PIN zum Anzeigen/Abspielen,
  im Guide ausblenden (IPTVX wird kritisiert, weil es das nicht tut).
- **7. Auto-Frame-Rate** (TV) – Bildwiederholrate des Fernsehers an den Stream anpassen; ExoPlayer kennt die Bildrate.
  Klein, für Filmfans sichtbar; bei TiviMate/OTT Pro.

### Stufe 3 – groß, erst nach Bedarf

- **8. Timeshift für Live** (TV, PC) – lokaler Puffer zum Pausieren/Zurückspulen.
- **9. Bild-in-Bild** (TV per System-PiP, iPhone – laut iOS-Spec mit VLCKit schwierig, PC als Mini-Fenster).
- **10. Aufnahme** (TV, PC) – Live-Stream in Datei, geplant aus dem EPG; Downloads-Technik vorhanden. Fire-TV-Speicher klein.
- **11. Multi-View** (TV, PC) – stark nachgefragt bei Sport, scheitert oft an der Verbindungszahl des Anbieters.
- **12. AirPlay** (iPhone) – steht schon als „später“ in der iOS-Spec.

### Paritätslücken aus der Matrix (klein, nebenbei)

TV: Filterfeld in Live TV, Verlauf mit Rückfrage/Rückgängig · PC: Senderliste im Player, System-Medientasten
(MPRIS/SMTC) · iPhone: „endet um“, beschleunigtes Spulen, Zifferneingabe, Ladebildschirm angleichen, Verlauf
rückgängig, http-Hinweis · TV/PC: Trailer und „Ähnliche Titel“ (gibt es auf dem iPhone schon).

## Quellen (Auswahl)

faq.ott-nav.com (Premium, Backup, Studio) · sparkleplayer.com · televizo.net/premium-version.html · Play-Store-Seiten
TiviMate/Sparkle/XCIPTV · iptvx.app/features · App-Store-Seiten UHF, iPlayTV, GSE, Purple · getchannels.com ·
github.com/kodi-pvr/pvr.iptvsimple · github.com/linuxmint/hypnotix · community.firecore.com (Infuse) ·
gummysearch.com (Reddit-Aggregat) · trustpilot (TiviMate). TiviMate-Preise/Pro-Umfang nur von Drittseiten.
