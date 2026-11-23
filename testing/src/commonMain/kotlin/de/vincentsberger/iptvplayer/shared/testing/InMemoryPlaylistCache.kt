package de.vincentsberger.iptvplayer.shared.testing

import de.vincentsberger.iptvplayer.shared.data.PlaylistCacheStore

/** Playlist-Zwischenspeicher im Arbeitsspeicher; [entries] zeigt Tests, was gespeichert wurde. */
class InMemoryPlaylistCache : PlaylistCacheStore {
    val entries = mutableMapOf<String, String>()

    override fun readText(key: String): String? = entries[key]
    override fun writeText(key: String, text: String) { entries[key] = text }
    override fun <T> readLines(key: String, block: (Sequence<String>) -> T): T? = entries[key]?.let { block(it.lineSequence()) }
    override fun writeLines(key: String, lines: Sequence<String>) { entries[key] = lines.joinToString("\n") }
    override fun <T> teeLines(key: String, lines: Sequence<String>, parse: (Sequence<String>) -> T, keep: (T) -> Boolean): T {
        val all = lines.toList()
        return parse(all.asSequence()).also { if (keep(it)) entries[key] = all.joinToString("\n") }
    }
    override fun clear() = entries.clear()
}
