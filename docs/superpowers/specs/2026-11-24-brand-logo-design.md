# App-Logo überall, Einschalt-Animation als Ladesymbol, Häkchen im Ladebildschirm

**Stand:** 24.11.2026 · Größe: Projekt · Plattformen: Android TV, PC, iPhone  
**Plattformen:** TV ✅ · PC ✅ · iPhone ✅
**Entwurf:** https://claude.ai/artifact/LqdPMXZZwUN7aJmXLjgooA (Tafeln unter „Gewählt – Einschalten als Logo-Animation“)

## Warum

Die App soll beim Start und im Alltag erkennbarer wirken. Bisher steht als Marke nur ein Bernstein-Quadrat neben
„IPTV Player“, das Logo (Fernseher mit Bernstein-Balken) taucht nur als App-Icon auf. Vincent will das Logo öfter
sehen. Die „Einschalt“-Animation aus dem Entwurf soll als Ladesymbol dienen: beim Start über dem bisherigen
Ladebildschirm und an größeren Ladestellen statt eines Spinners.

## Ziele

- **Logo statt Bernstein-Quadrat** in der Wortmarke (`Masthead`) aller drei Apps. Dazu oben in der TV-Seitenleiste
  (heute `SignalMark` in `HomeScreen.kt:168`). Überall dort ist es statisch.
- **Einschalt-Animation** (`LogoLoader`):
  - Der Fernseher zeichnet sich.
  - Der Bildschirm flackert kurz auf wie eine Röhre.
  - Der Balken im Bild erscheint.
  - Der Zyklus dauert 4 s und wiederholt sich, solange geladen wird.
- **Startbildschirm:** Der bisherige Ladebildschirm (Überschrift, Anbieter, laufender Balken, Ladeschritte mit
  Spinnern) **bleibt**. Die Wortmarke oben trägt den animierten `LogoLoader` statt des Quadrats.
- **Überschrift nennt, was gerade lädt:** Statt „Playlist wird geladen“ steht „Sender werden gesucht“, „Filme werden
  gesucht“ oder „Serien werden gesucht“, je nachdem, was gerade lädt. Laden mehrere Bereiche gleichzeitig, zählt die
  Reihenfolge Live → Filme → Serien. Ist nichts mehr offen (kurz vor dem Wechsel), bleibt die letzte Überschrift
  stehen. Die Logik ist eine reine Funktion in `shared`: `loadingHeadline(states: Map<Section, SectionState?>): String`,
  getestet.
- **Ladeschritte im Startbildschirm:**
  - Das Bereichssymbol links bleibt.
  - Rechts steht ein **Kästchen**: leer mit „lädt …“ und Spinner davor, solange der Bereich lädt. Ein
    Bernstein-Häkchen, sobald er fertig ist, mit der Anzahl davor („1.284 Sender“). Ein rotes ✕ bei einem Fehler.
- **Größere Ladestellen** zeigen den `LogoLoader` statt eines Spinners. Kleine Spinner in Zeilen und Knöpfen und die
  Platzhalter-Skelette (Raster, Senderlisten) bleiben, wie sie sind.

## Nicht-Ziele

- Neues App-Icon oder neue Markenfarben.
- Ein animierter System-Startbildschirm. Android ab 12 zeigt beim Kaltstart sein eigenes Icon-Fenster, iOS den
  statischen `LaunchScreen`. Beide bekommen nur Hintergrund `bg-deep` und das statische Logo (siehe unten).
- Animation in der Navigation.

## Anti-Ziele

- **Unruhe:** Das Logo bewegt sich nur, solange wirklich geladen wird; danach steht es still.
- **Bewegung trotz Systemeinstellung:** „Bewegung reduzieren“ (iOS) bzw. „Animationen aus“ (Android) zeigt das
  statische Logo. Auf dem PC gibt es keine solche Einstellung, dort ist es immer animiert.
- **Drei abweichende Zeichnungen:** Form und Taktung des Logos werden genau einmal festgelegt.

## Ansätze

- **A. Geometrie und Taktung in `shared`, Zeichnen je Oberfläche** (gewählt):
  - `shared/…/shared/brand/BrandMark.kt` enthält die Form im ViewBox-Maß 240 × 200: Antennen-Linienzug, gerundetes
    Gehäuse, Bildfläche, Balkenanteil 60 % Bernstein und 40 % `surface2`, Strichstärke.
  - Dazu kommen die Animationsphasen als Anteile des Zyklus: Zeichnen 0–35 %, Röhre 30–46 %, Balken 44–50 %,
    Zyklus 4 000 ms. Außerdem eine reine Funktion `brandMarkFrame(t: Float): BrandMarkFrame` mit den Werten
    `drawn`, `screenScaleX`, `screenScaleY`, `screenAlpha` und `barAlpha`. Sie ist in `commonTest` getestet.
  - Jede Oberfläche zeichnet mit Compose `Canvas` (`Path`, `drawRoundRect`) nach diesen Werten.
  - Die Farben kommen aus `Palette`: Gehäuse `Text`, Bildflash `FocusBg`, Balken `Signal`/`Surface2`.
- **B. Lottie oder Vektor-Animation als Datei je Plattform:** verworfen. Das wäre eine neue Abhängigkeit, drei
  Formate, und Form und Farben lägen doppelt neben der `Palette`.
- **C. Animiertes GIF oder WebP:** verworfen. Es wird unscharf auf TV-Größe, und Farben sowie „Bewegung reduzieren“
  ließen sich nicht steuern.

## Entschieden (mit Vincent)

- Variante „Einschalten“, die iPhone-Fassung ist die Vorlage.
- Der bisherige Ladebildschirm mit Spinnern bleibt, die Logo-Animation kommt darüber.
- Rechts in jeder Ladezeile ein Kästchen, das beim Fertigladen ein Häkchen bekommt; die Symbole links bleiben.
- Das Logo erscheint öfter: in der Navigation oben bzw. in der Wortmarke überall.
- Die Animation erscheint auch an größeren Ladestellen statt eines Spinners.

## Design

### Komponenten je Oberfläche
- `@Composable fun LogoMark(size: Dp)` ist statisch.
- `@Composable fun LogoLoader(size: Dp, label: String? = null)` ist animiert und hat eine Bedienungshilfe-Beschreibung
  „Wird geladen“. Mit `label` steht der Text darunter in `Muted`.
- `Masthead` nutzt `LogoMark` statt `SignalMark` bzw. des Quadrats. Die Größe beträgt etwa das 1,6-Fache der heutigen
  Quadrat-Kante, damit das Logo neben dem Schriftzug lesbar bleibt.

### Wo `LogoLoader` einen Spinner ersetzt
- **PC:**
  - `Components.kt:307` `Loading(text)` → `LogoLoader(56.dp, text)`. Das deckt „Sender/Filme/Serien/Listen/Folgen
    werden geladen …“ ab.
  - Player-Puffern `PlayerScreen.kt:290`: nur bis zum ersten Bild eines Titels der `LogoLoader`. Späteres
    Nachpuffern behält den Spinner, damit mitten im Film nichts aufblinkt.
- **TV:**
  - Der Player zeigt bis zum ersten Bild den `LogoLoader` an der Stelle des heutigen Puffer-Hinweises.
  - Die Skelette in Live-TV und im Raster bleiben.
  - Die Film-Detailzeile „Infos werden geladen …“ bleibt Text.
- **iPhone:**
  - Programm-Blatt `ProgrammeSheet.kt:68` → `LogoLoader`.
  - Player bis zum ersten Bild wie auf dem TV.
  - Die Skelette bleiben.
- **Startbildschirm, alle drei:** Die Wortmarke über „Playlist wird geladen“ trägt `LogoLoader` statt `LogoMark`.

### Ladeschritte
`LoadingStep`/Entsprechung je App:
- Links das Bereichssymbol (unverändert), dann der Name.
- Rechts:
  - **lädt:** Spinner 14 dp, „lädt …“, leeres Kästchen 20 dp mit Rand `Muted`.
  - **fertig:** Anzahl in Mono („1.284 Sender“), Kästchen gefüllt `Signal` mit Häkchen `OnSignal`.
  - **Fehler:** wie heute der Hinweis, Kästchen mit Rand `Danger` und ✕.
- Der Wechsel wird mit kurzer Überblendung animiert, wie heute (`AnimatedContent`).

### System-Startfenster
- **Android:** `windowSplashScreenBackground = bg-deep`, das Icon bleibt das App-Icon. Es gibt keine eigene
  Splash-Activity.
- **iOS:** `LaunchScreen` mit Hintergrund `bg-deep` und dem statischen Logo in der Mitte. Es ist nur Bild, ohne Text,
  damit es nicht übersetzt werden muss.
- **PC:** Es gibt kein System-Startfenster. Das Fenster zeigt sofort den Ladebildschirm.

## Dem Bau überlassen

- Genaue Pfadkonstruktion in `Canvas` (die Werte stehen in `BrandMark`).
- Easing-Kurven im Rahmen der Phasen.
- Wie „Animationen aus“ je Plattform erkannt wird: Android `Settings.Global.ANIMATOR_DURATION_SCALE`, iOS
  `UIAccessibility.isReduceMotionEnabled`.

## Tests

- **`commonTest`:**
  - `brandMarkFrame` an den Phasengrenzen: 0, 0,35, 0,30–0,46 und 0,5.
  - Werte bleiben stets in 0..1.
  - Der Zyklus wiederholt sich.
- **PC-UI-Test:** Ladeschritt-Zustände (leer, Häkchen, ✕) über `contentDescription`. `Loading` zeigt den
  `LogoLoader` mit Text.
- **TV-UI-Test** (androidTest): Ladebildschirm zeigt Häkchen für fertige Bereiche.
- **Handtest:** Start auf allen drei Plattformen; „Animationen aus“ bzw. „Bewegung reduzieren“ zeigt das statische
  Logo.
