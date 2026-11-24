package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Category

/** So viele Kategorien zeigt die Startseiten-Auswahl zugeklappt – Anbieter haben oft Hunderte. */
const val DASHBOARD_COLLAPSED_COUNT = 8

/** Lohnt „Alle anzeigen“? Erst ab mehr als [DASHBOARD_COLLAPSED_COUNT] Kategorien. */
fun dashboardNeedsToggle(options: List<Category>): Boolean = options.size > DASHBOARD_COLLAPSED_COUNT

/**
 * Sichtbare Kategorien der Startseiten-Auswahl: gewählte immer zuerst (in Listenreihenfolge), damit sie auch zugeklappt
 * zu sehen sind; zugeklappt auf [DASHBOARD_COLLAPSED_COUNT] aufgefüllt (mehr nur, wenn mehr gewählt sind).
 * [chosenIds] sind die Kategorie-IDs dieses Bereichs.
 */
fun dashboardOptionsShown(options: List<Category>, chosenIds: Set<String>, expanded: Boolean): List<Category> {
    val (chosen, rest) = options.partition { it.id in chosenIds }
    val ordered = chosen + rest
    return if (expanded || !dashboardNeedsToggle(options)) ordered else ordered.take(maxOf(DASHBOARD_COLLAPSED_COUNT, chosen.size))
}
