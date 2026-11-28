# PC: natives Video mit Overlay-Fenster – Design

**Stand:** 2026-11-26 · freigegeben (Ansatz A) · Projekt  
**Plattformen:** PC ✅ (#47) · TV ➖ · iPhone ➖ (eigene Player)

## Warum

Der PC-Player holt heute jedes Videobild über `CallbackVideoSurface` in den RAM und zeichnet es als Compose-`Image`
(`VlcPlayer.kt`, `PlayerScreen.kt:294`). Das erzwingt Software-Dekodierung, RGB-Umrechnung und mehrere Kopien pro Bild.

Gemessen bei 1080p50 (Linux, Iris Xe):

| | CPU | RAM |
|---|---|---|
| heute (Bild-Callback) | 198 % | 1,66 GB |
| Wegwerf-Test `spike/native-video` (VLC zeichnet direkt, Overlay-Fenster) | 10 % | 345 MB |

Ein nativer Neuschrieb der Oberfläche brächte nicht mehr, weil der Gewinn im Video-Weg liegt. Compose bleibt.

## Ziele

- VLC zeichnet direkt in ein natives Fenster (GPU, Hardware-Dekodierung).
- Alle Bedienelemente und Overlays des Players bleiben Compose und funktionieren wie heute: Leiste, Zapp-Karte,
  Pause-Info, Spulen, Puffern/Fehler, Spurmenü, Statistik, Hinweise, Zeiger ausblenden, Klick, Doppelklick, Rad, Tasten.
- Linux und Windows gleichwertig.

## Nicht-Ziele

TV (Media3) und iPhone bleiben unberührt. Kein Wechsel von VLC zu einer anderen Engine.

## Anti-Ziele (wäre ein Fehlschlag, auch wenn es läuft)

- Ein Overlay, das über fremden Fenstern stehen bleibt, verrutscht oder Klicks verschluckt.
- Tasten, die nach einem Klick ins Video nicht mehr ankommen.
- Ein Windows-Fehler, der die Wiedergabe unbenutzbar macht, ohne dass es einen Ausweg gibt.

## Ansätze

- **A – eigenes Overlay-Fenster (gewählt):** Video im Hauptfenster (AWT-`Canvas` über `SwingPanel`), Overlays in einem
  rahmenlosen, durchsichtigen `ComposeDialog`, der zum Hauptfenster gehört. Volle Kontrolle, Verhalten an einer Stelle.
- B – `compose.layers.type=WINDOW`: gilt app-weit und ist experimentell, Maus auf leerem Video landet bei VLCs Canvas. Verworfen.
- Callback verschlanken: bringt nur ~198 % → 110–130 %, weil die Software-Dekodierung bleibt. Verworfen, bleibt aber Rückfallebene.

## Entscheidungen

**Video-Ausgabe (`desktopApp/…/player/`):**
- `VlcPlayer` bekommt zwei Ausgaben hinter einer Schnittstelle: **nativ** (`ComponentVideoSurface` auf einem `Canvas`)
  und **kompatibel** (der heutige Callback, unverändert).
- `frame != null` als Zeichen für „Bild da“ (Zapp-Karte, Pause-Info, Puffer-Logo) wird zu `hasPicture`. Nativ kommt das
  aus VLCs `videoOutput`-Ereignis (Anzahl > 0), kompatibel weiter aus dem ersten Bild. Die Hänger-Erkennung nutzt nativ
  `timeChanged`, das gibt es heute schon.
- Linux: `XInitThreads()` vor der ersten VLC-Fabrik (vlcj-Empfehlung für eingebettete Ausgabe).

**Overlay-Fenster:**
- `ComposeDialog(owner = Hauptfenster, MODELESS)`, `isUndecorated`, `isTransparent`, `focusableWindowState = false`,
  **Typ `POPUP`**: Normale Fenster hält GNOME aus der oberen Leiste heraus, im Vollbild lag das Overlay sonst 32 px zu tief.
  Popups verwaltet der Fenstermanager nicht. Deshalb ist das Overlay **nur bei aktivem Hauptfenster** sichtbar (Alt+Tab,
  Minimieren → aus), sonst bliebe es über fremden Fenstern stehen. `DialogWindow` von Compose scheidet aus, weil es modal ist.
- Grund mit Alpha 1/255, weil Windows bei Alpha 0 Klicks durchlässt.
- Liegt deckungsgleich über dem Canvas. Abgleich bei Bewegen, Größe, Zustand und Sichtbarkeit, jeweils zweimal
  (sofort und nach ~150 ms), weil beim Vollbild-Wechsel die erste Größe veraltet ist (im Wegwerf-Test: 37 px).
- Unsichtbar, solange der Player nicht der aktive Bildschirm ist (auch während des Überblendens) oder das Hauptfenster minimiert ist.
- Klick ins Overlay → das Hauptfenster holt sich den Fokus zurück (`toFront` + `requestFocus`). Ohne das ging unter
  GNOME der Fokus verloren. Tasten bleiben deshalb in der heutigen Behandlung im Hauptfenster.
- Eigene Komposition: `AppTheme` und die nötigen CompositionLocals (`LocalKeyboard`) werden im Overlay erneut bereitgestellt.

**Vollbild (nach Reproduktion geändert):**
- **Linux mit Rahmen** statt rahmenlos (PR #43 zurückgenommen). Der Neuaufbau beim Rahmenwechsel zerstörte den Canvas (VLC
  musste neu starten), nahm den Fokus und warf das Fenster samt Overlay in 2 von 3 Wechseln aus dem Vollbild zurück. Das
  Wachsen um die Titelleiste behebt der `FullscreenController` jetzt, indem er die alte Lage nach dem Verlassen selbst setzt.
- **Windows bleibt rahmenlos** (sonst bleibt die Titelleiste stehen). Dort hält VLC vor dem Neuaufbau an
  (`LocalWindowRebuild`) und hängt sich danach wieder an (Film an der alten Position, Live neu). Das Overlay ist während
  des Wechsels ausgeblendet. Ohne das Anhalten hing die App (X11 unter Linux, belegt).

**Rückfallebene „Kompatibler Videomodus“:**
- Neue Einstellung (PC, Standard **aus**) schaltet auf die heutige Callback-Ausgabe mit Overlays im Hauptfenster.
- Automatisch kompatibel, wenn das System keine durchsichtigen Fenster kann
  (`GraphicsDevice.isWindowTranslucencySupported(PERPIXEL_TRANSLUCENT)` = false), z. B. Linux ohne Compositor, und in
  Tests ohne Bildschirm.
- Ein Wechsel gilt ab der nächsten Wiedergabe.

## Tests

- Unit: Overlay-Abgleich (Rechteck aus Canvas-Lage), Entscheidung nativ/kompatibel, Neu-Anhängen nach Vollbild (mit Attrappe).
- Bestehende UI-Tests laufen im kompatiblen Modus weiter (headless, keine Transparenz).
- Von Hand, Linux: die Checkliste aus dem Wegwerf-Test (Overlay, Maus, Menü, Fenster verschieben/Größe/Minimieren/Alt+Tab,
  Vollbild rein/raus, Tasten nach Klick), dazu eine CPU-Messung.
- Windows: Test durch den User nach dem Release. Bis dahin gilt die Rückfallebene.

## Ergebnis Linux (Reproduktion, 1080p50)

3 von 3 Vollbild-Wechseln korrekt (Fenster, Overlay deckungsgleich, Esc raus, Fokus bleibt). 22 % CPU / 315 MB
(vorher 198 % / 1,66 GB).

## Offene Windows-Risiken (ungetestet)

- Overlay vom Typ `POPUP` unter Windows (Stapelung, Klicks).

- Vollbild rahmenlos: Neu-Anhängen von VLC an das neu erzeugte HWND.
- Overlay über einem D3D-Videofenster: Reihenfolge, Flackern beim Verschieben.
- Fokus und Aktivierung beim Klick auf ein nicht fokussierbares, gehörendes Fenster.
- DPI-Skalierung bei mehreren Bildschirmen (Overlay-Bounds in skalierten Koordinaten).

## Dem Umsetzenden überlassen

Paket- und Klassennamen, Aufteilung von `PlayerScreen` in Video- und Overlay-Teil, Wortlaut der Einstellung.
