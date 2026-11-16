package de.vincentsberger.iptvplayer.shared.data

/** Liest M3U/M3U8-Playlists zeilenweise, damit auch sehr große Listen funktionieren. */
object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    data class Result(val categories: List<Category>, val items: List<Item>)

    fun parse(lines: Sequence<String>): Result {
        val items = ArrayList<Item>()
        val groups = LinkedHashMap<String, Category>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var index = 0

        lines.forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = titleOf(line).ifBlank { attrs["tvg-name"].orEmpty() }
                    logo = attrs["tvg-logo"]?.ifBlank { null }
                    group = attrs["group-title"]?.ifBlank { null }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    group = line.substringAfter(':').trim().ifBlank { group }
                line.isEmpty() || line.startsWith("#") -> Unit
                // Nur Stream-Protokolle: eine fremde Playlist soll den Player keine lokalen Dateien öffnen lassen.
                !isAllowedStreamUrl(line) -> { name = null; logo = null; group = null }
                else -> {
                    val section = sectionOf(line)
                    val groupName = group ?: "Allgemein"
                    val catId = "${section.name}:$groupName"
                    groups.getOrPut(catId) { Category(catId, groupName) }
                    items += Item("m3u-${index++}", name?.ifBlank { null } ?: line.substringAfterLast('/'),
                        logo, catId, line, section)
                    name = null; logo = null; group = null
                }
            }
        }
        return Result(groups.values.toList(), items)
    }

    /** Der Titel steht nach dem ersten Komma außerhalb von Anführungszeichen. */
    private fun titleOf(line: String): String {
        var quoted = false
        line.forEachIndexed { i, c ->
            if (c == '"') quoted = !quoted
            else if (c == ',' && !quoted) return line.substring(i + 1).trim()
        }
        return ""
    }

    private fun sectionOf(url: String): Section {
        val lower = url.lowercase()
        return when {
            "/series/" in lower -> Section.SERIES
            "/movie/" in lower || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") -> Section.MOVIES
            else -> Section.LIVE
        }
    }
}
