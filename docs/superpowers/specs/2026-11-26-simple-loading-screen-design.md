# Einfacher Ladebildschirm (PC + TV) – Design

**Stand:** 2026-11-26 · freigegeben · klein

## Warum

Der Ladebildschirm beim Laden der Playlist zeigt heute viel auf einmal: Ladebalken, Logo neben „IPTV Player“, eine
wechselnde Überschrift, den Kontonamen, je Bereich einen Status-Text samt Anzahl, ein Kästchen mit Mini-Spinner und einen
Hinweistext. Er soll ruhiger werden. Wichtig ist nur noch, welche Bereiche laufen und welche fertig sind.

## Ziel

```
                 ┌──────────┐
                 │  Logo    │   ← animiertes Logo (LogoLoader), ~120 dp, mittig
                 └──────────┘
      Einträge werden geladen, bitte warten …

        📺  Sender                    ◌    ← runder Spinner (Signal/Bernstein)
        🎬  Filme                     ✓    ← fertig: Haken an gleicher Stelle
        🎞  Serien                    ◌
```

- PC (`desktopApp/…/ui/Components.kt` `PlaylistLoadingScreen`) und TV (`androidApp/…/ui/Components.kt`) gleich.
- Je Zeile: Bereichs-Icon, Bereichsname, rechts der Zustand:
  - wartet / lädt → runder Spinner (beide gleich, die Bereiche laden parallel)
  - geladen → Haken an derselben Stelle, kurz überblendet
  - Fehler → rotes ✕ (sonst sähe ein Fehler aus wie endloses Laden)

## Nicht-Ziele

- iPhone (`iosUi/…/LoadingScreen.kt`) bleibt unverändert.
- Übersicht nach dem Laden (`PlaylistOverviewScreen`), Ablauf und Zeitpunkte (300 ms Verzögerung, Überblenden) bleiben.

## Fällt weg

Ladebalken, Schriftzug „IPTV Player“, wechselnde Überschrift (`loadingHeadline`), Kontoname, Hinweis „Große Playlists …“, Status-Texte
und Anzahlen in den Zeilen (die Übersicht danach zeigt die Anzahlen), das eckige Kästchen um Spinner/Haken.

## Ansätze

Nur ein sinnvoller Weg: die vorhandenen `PlaylistLoadingScreen`/`LoadingStep`/`LoadCheck` beider Apps umbauen. Ein
gemeinsamer Composable in `shared` scheidet aus, weil PC (Material 3) und TV (tv-material) eigene UI-Bausteine haben.

## Dem Umsetzenden überlassen

- Genaue Größen (Logo ~120 dp, Spinner/Haken ~22 dp, Zeilenbreite ~320 dp) nach Augenmaß und `.tastemaker/style-lock.md`.
- Ob `loadingHeadline` danach noch woanders gebraucht wird; sonst entfernen samt Test.
- Testanpassungen: `LogoTest`, `SettingsFavoritesLoadingTest` (PC), `LoadingScreenTest` (TV) prüfen künftig Zustände über
  die `contentDescription` von `LoadCheck` statt über Status-Texte.

## Nachtrag (Sichtprüfung 2026-11-26)

Unter dem Logo steht fest „Einträge werden geladen, bitte warten …“ (gedämpft) – auf Wunsch nach der ersten Sichtprüfung.
