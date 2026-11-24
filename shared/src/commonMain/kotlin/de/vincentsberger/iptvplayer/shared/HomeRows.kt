package de.vincentsberger.iptvplayer.shared

/** Reihen der Startseite (alle Apps); Reihenfolge und Sichtbarkeit stellt man in den Einstellungen ein. */
enum class HomeRow(val title: String) {
    CONTINUE("Weiterschauen"),
    NEW_MOVIES("Neue Filme"),
    NEW_EPISODES("Neue Folgen"),
    RECENT_CHANNELS("Zuletzt gesehene Sender"),
    OFFLINE("Offline verfügbar"),
    FAVORITE_LIVE("Favoriten · Live TV"),
    FAVORITE_MOVIES("Favoriten · Filme"),
    FAVORITE_SERIES("Favoriten · Serien"),
}

/** Gespeicherte Reihenfolge anwenden: unbekannte Namen fallen weg, neue Reihen hängen hinten an. */
internal fun orderedHomeRows(saved: List<String>): List<HomeRow> {
    val known = saved.mapNotNull { name -> HomeRow.entries.firstOrNull { it.name == name } }.distinct()
    return known + HomeRow.entries.filterNot { it in known }
}
