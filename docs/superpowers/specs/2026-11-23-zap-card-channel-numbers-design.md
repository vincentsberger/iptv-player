# Zapp-Karte und Anbieter-Sendernummern

**Stand:** 23.11.2026 · Größe: kleine Änderung · Plattformen: Android TV und Desktop
Teil 1 von 6 der Player-Verbesserungen (danach: Pause-Info, Spulen mit Ziel, Info-Abzeichen, Desktop-Feinschliff).

## Warum

Beim Umschalten sieht man bis zum ersten Bild nur einen Spinner auf Schwarz – der Moment, in dem die App am
langsamsten wirkt. Oben im Player steht „17 / 32294“ (Position in der Liste) statt einer Sendernummer, die man
kennt und per Zifferntasten wählen kann.

## Ziele

- Bei jedem Senderwechsel sofort eine Karte mit Nummer, Logo, Name und laufender Sendung.
- Sendernummer des Anbieters statt Listenposition – im Player, in Listen und bei der Zifferneingabe.

## Nicht-Ziele

- Filme/Serien (keine Zapp-Karte).
- Eigene Nummerierung oder Umsortieren durch den Nutzer.

## Anti-Ziele

- Karte und Info-Leiste gleichzeitig.
- Zifferneingabe, die woanders landet als die angezeigte Nummer.

## Ansatz

Ein sinnvoller Weg: `Item.number: Int?` (Xtream `num`, M3U `tvg-chno`); die Zeitlogik der Karte als kleine,
testbare Klasse in `shared`; jede Plattform zeichnet die Karte selbst. Verworfen: Nummern-Zuordnung außerhalb von
`Item` (zweite Wahrheit), Zeitlogik je Player (doppelt, schwer testbar), gemeinsame Oberfläche (TV und PC haben
getrennte UI-Bausteine).

## Design

### Zapp-Karte (Variante B: kleine Karte oben links)

```
┌──────────────────────────────┐
│▌ 101  [ARD]  Das Erste HD    │
│▌ Tagesschau · bis 20:15      │
│▌ ███████████░░░░░░           │
└──────────────────────────────┘
```

- Erscheint bei jedem Senderwechsel: ▲▼, Zifferneingabe, Auswahl in der Senderliste, „letzter Sender“, Öffnen des Players.
- Inhalt: Nummer, Logo, Name; Zeile „<Sendung> · bis <Ende>“ mit Fortschrittsbalken – ohne Programmdaten entfällt diese Zeile.
- Bleibt, solange der Sender lädt; blendet **4 s nach dem ersten Bild** aus. Schnelles Weiterzappen zeigt sie durchgehend mit dem jeweils neuen Sender.
- Wird nicht gezeigt, solange die Info-Leiste (OK, Pause, Senderliste, Optionen) sichtbar ist.
- Optik: Hintergrund `Surface1` mit 92 % Deckkraft, linker Rand in `Signal`, Nummer in Mono-Schrift.

### Sendernummer

- `Item.number`: Xtream `num` (Live-Sender); M3U `tvg-chno`, sonst null. Nur Zahlen > 0 gelten.
- Anzeige: `number ?: Position in der Liste (1-basiert)`.
- Player oben: Nummer statt „x / y“.
- Klein (Mono, gedämpft) vor dem Sendernamen in der Live-TV-Liste und in der Senderliste des Players.
- Zifferntasten: suchen die Nummer in allen sichtbaren Live-Sendern (`section(LIVE).items`, Länderfilter gilt);
  ohne `number` beim Anbieter weiter Position. Nicht vorhanden → kurzer Hinweis „Sender <n> nicht gefunden“.

## Was der Builder entscheidet

- Genaue Schnittstelle der Zeitlogik (z. B. `ZapCard`-Zustand mit `onChannelChanged`, `onFirstFrame`, Tick/Timeout).
- Wiederverwendung vorhandener Bausteine (`ChannelLogo`, `ProgressBar`, `epgProgress`).

## Tests

- Nummer aus `num` und `tvg-chno`; ungültige Werte → null.
- Zifferneingabe: Anbieter-Nummer, Fallback Position, nicht gefunden.
- Zeitlogik: sichtbar ab Wechsel, bis 4 s nach erstem Bild, Neustart bei schnellem Zappen, unterdrückt bei Info-Leiste.
- UI je Plattform: Karte erscheint nach ▲, zeigt Nummer und Name; Player-Kopf zeigt Nummer.
