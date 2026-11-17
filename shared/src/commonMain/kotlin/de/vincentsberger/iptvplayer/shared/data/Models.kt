package de.vincentsberger.iptvplayer.shared.data

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

/** Name aufgeteilt in das Anbieter-Etikett (Land/Format, z. B. „DE“, „4K“, „24/7“) und den eigentlichen Namen. */
data class TaggedName(val tag: String?, val name: String)

private val providerTag = Regex("""^\s*\|?\s*([A-Z]{2,3}|4K|8K|UHD|24/7)\s*\|?\s*[|:]\s*(.+)$""")

/**
 * Anbieter schreiben Länder- und Formatkürzel vor Kategorien und Sender („DE | NACHRICHTEN“, „DE: Sport 1 HD“,
 * „|AT| Alpen TV“). Für die Anzeige trennen wir das Kürzel ab – so bleibt der eigentliche Name lesbar.
 */
fun splitProviderTag(raw: String): TaggedName =
    (providerTag.matchEntire(raw)?.let { TaggedName(it.groupValues[1], it.groupValues[2].trim()) } ?: TaggedName(null, raw.trim()))
        .let { it.copy(name = softenCaps(it.name)) }

private val smallWords = setOf("und", "oder", "mit", "ohne", "nur", "für", "von", "der", "die", "das", "im", "in", "am", "an", "zu", "bei", "auf", "aus")

/**
 * Durchgehend großgeschriebene Namen („NACHRICHTEN & INFO“) in normale Schreibweise bringen: Versalien
 * schreien, brauchen mehr Platz und werden in schmalen TV-Spalten mitten im Wort umbrochen.
 * Kurze Kürzel (HD, TV, UHD, 4K) bleiben groß, Füllwörter klein. Gemischte Schreibweise bleibt unangetastet.
 */
fun softenCaps(name: String): String {
    // Nur reguläre Groß-/Kleinbuchstaben zählen: hochgestellte Zeichen wie „ᴴᴰ“ sind formal Kleinbuchstaben.
    val letters = name.filter { it.category == CharCategory.UPPERCASE_LETTER || it.category == CharCategory.LOWERCASE_LETTER }
    if (letters.length < 4 || letters.any { it.category == CharCategory.LOWERCASE_LETTER }) return name
    var first = true
    return Regex("""\p{L}+""").replace(name) { m ->
        val w = m.value
        val out = when {
            w.length <= 3 && w.lowercase() !in smallWords -> w          // Kürzel
            !first && w.lowercase() in smallWords -> w.lowercase()
            else -> w.lowercase().replaceFirstChar { it.uppercase() }
        }
        first = false
        out
    }
}

/**
 * Eindeutige, stabile Schlüssel für Listen (z. B. für Animationen beim Umsortieren). Basis ist die Stream-URL;
 * M3U-Playlists enthalten aber manchmal denselben Stream mehrfach – Duplikate bekommen dann „#2“, „#3“ …
 */
fun stableKeys(items: List<Item>): List<String> {
    val seen = HashMap<String, Int>()
    return items.map { item ->
        val n = (seen[item.url] ?: 0) + 1
        seen[item.url] = n
        if (n == 1) item.url else "${item.url}#$n"
    }
}
