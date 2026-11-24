# Startseiten-Kategorien in den Einstellungen einklappen

**Stand:** 24.11.2026 · Größe: kleine Änderung · Plattformen: PC, TV, iPhone

## Warum
Anbieter haben oft Hunderte Film- und Serienkategorien. Die Auswahl „Startseite: Neues aus diesen Kategorien“ zeigte am PC
alle auf einmal, auf TV und iPhone nach dem Aufklappen ebenfalls. Die Einstellungen darunter waren damit kaum erreichbar.

## Entschieden (Vincent, Variante a)
- Ab mehr als 8 Kategorien je Bereich sind nur 8 sichtbar. Gewählte stehen immer zuerst, damit sie auch zugeklappt zu
  sehen sind. Sind mehr als 8 gewählt, sind alle gewählten sichtbar.
- „Alle anzeigen (n)“ klappt auf, „Weniger anzeigen“ wieder zu. Das gilt je Bereich (Filme, Serien).
- TV und iPhone behalten das bisherige Auf- und Zuklappen je Bereich. Innerhalb des offenen Bereichs gilt dieselbe
  Kürzung.

## Umsetzung
- In `shared` die Funktionen `dashboardOptionsShown(options, chosenIds, expanded)`, `dashboardNeedsToggle(options)` und
  `DASHBOARD_COLLAPSED_COUNT = 8`, getestet in `DashboardChoicesTest`.
- Am PC ein Chip „Alle anzeigen (n)“ / „Weniger anzeigen“ (`testTag` `dashmore:<Bereich>`), UI-Test in
  `DashboardSettingsTest`. Am TV ein `TvButton`, am iPhone ein `Chip`, jeweils mit derselben Logik.
