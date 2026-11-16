package de.tvplayer.shared.data

sealed interface Account {
    data class Xtream(val server: String, val username: String, val password: String) : Account
    data class M3u(val url: String) : Account
}

enum class Section(val title: String) {
    LIVE("Live TV"),
    MOVIES("Filme"),
    SERIES("Serien"),
}

data class Category(val id: String, val name: String)

/**
 * Ein Eintrag in einer Liste: Live-Sender, Film, Serie oder Episode.
 * Bei einer Xtream-Serie ist [url] leer; die Folgen werden erst beim Öffnen geladen.
 */
data class Item(
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val url: String,
    val section: Section,
) {
    val isSeriesFolder: Boolean get() = section == Section.SERIES && url.isEmpty()
    /** Stabiler Schlüssel für Favoriten. */
    val favKey: String get() = "${section.name}:" + (if (url.isEmpty()) id else url)
}

data class Season(val number: String, val episodes: List<Item>)

data class EpgEntry(val title: String, val start: String, val end: String)

/** Verlauf: zuletzt gesehene Sender sowie Filme/Folgen mit Wiedergabeposition ("Weiterschauen"). */
data class HistoryEntry(val item: Item, val position: Long, val duration: Long, val updated: Long) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val canResume: Boolean get() = item.section != Section.LIVE && position > 30_000 && progress < 0.95f
}

/** Fehler mit verständlicher Meldung für die Oberfläche. */
class TvException(message: String) : Exception(message)
