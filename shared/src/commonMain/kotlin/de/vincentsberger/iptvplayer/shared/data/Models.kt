package de.vincentsberger.iptvplayer.shared.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    /** Seit wann beim Anbieter (ms; Filme: `added`, Serien: `last_modified`) – für „Neu“ auf der Startseite. */
    val addedAt: Long? = null,
    /** Erwachseneninhalt laut Anbieter (`is_adult`) – erscheint nie auf der Startseite. */
    val isAdult: Boolean = false,
    /** Sendernummer des Anbieters (Xtream `num`, M3U `tvg-chno`); null = keine, dann zählt die Listenposition. */
    val number: Int? = null,
    /**
     * Sender mit Programmdaten? Xtream: nur mit `epg_channel_id` – ohne liefert `get_short_epg` immer nichts, die
     * Anfrage wäre umsonst (bei manchen Anbietern 80 % aller Sender). Andere Quellen: unbekannt, also ja.
     */
    val hasEpg: Boolean = true,
    /** Bewertung 0–10 aus der Liste des Anbieters (Xtream `rating`) – für Poster ohne Detailabfrage. */
    val rating: Double? = null,
) {
    val isSeriesFolder: Boolean get() = section == Section.SERIES && url.isEmpty()

    /** Als JSON, z. B. als Zusatzdaten eines Downloads (der muss offline wissen, was er ist). */
    fun toJson(): String = buildJsonObject {
        put("id", id); put("name", name); put("logo", logo ?: ""); put("cat", categoryId); put("url", url); put("section", section.name)
        number?.let { put("num", it.toString()) }
    }.toString()

    companion object {
        /** Gegenstück zu [toJson]; kaputte oder unbekannte Daten → null. */
        fun fromJson(json: String): Item? = try {
            val o = parseJson(json) as? JsonObject ?: return null
            Item(o.str("id"), o.str("name"), o.str("logo").ifBlank { null }, o.str("cat"), o.str("url"), Section.valueOf(o.str("section")),
                number = o.str("num").toIntOrNull())
                .takeIf { it.url.isNotEmpty() }
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    /** Stabiler Schlüssel für Favoriten. */
    val favKey: String get() = "${section.name}:" + (if (url.isEmpty()) id else url)
}

/** Eine Staffel; [details] je Folgen-URL (Handlung, Dauer) für die Folgenliste – nur Folgen mit Infos. */
data class Season(val number: String, val episodes: List<Item>, val details: Map<String, EpisodeDetail> = emptyMap())

/** Kurzinfos einer Folge für die Liste: Handlung und Dauer, soweit der Anbieter sie liefert. */
data class EpisodeDetail(val plot: String?, val durationSecs: Int?)

/**
 * Eine Sendung. [start]/[end]: Uhrzeit „HH:MM“ zur Anzeige. [startAt]/[endAt]: Ortszeit „yyyy-MM-dd HH:mm:ss“ wie vom
 * Anbieter (leer, wenn unbekannt) – damit lässt sich prüfen, ob die Sendung schon vorbei ist.
 */
data class EpgEntry(
    val title: String, val start: String, val end: String, val startAt: String = "", val endAt: String = "",
    /** Beschreibung der Sendung (Xtream `description`); leer, wenn der Anbieter keine liefert. */
    val description: String = "",
)

/**
 * Sendungen, die zur Ortszeit [localNow] („yyyy-MM-dd HH:mm:ss“) noch nicht zu Ende sind – die erste ist „Jetzt“.
 * Nötig, weil Anbieter oft schon beendete Sendungen mitliefern (falsch umgerechnete Timestamps, Zwischenspeicher).
 * Bewusst über die Texte, nicht die Timestamps: Die Texte stehen in der Zeit des Senders, die Timestamps sind oft falsch.
 */
fun List<EpgEntry>.notEndedAt(localNow: String): List<EpgEntry> = filter { it.endAt.isEmpty() || it.endAt > localNow }

/**
 * EPG eines Senders bereinigen. Manche Anbieter mischen zwei EPG-Quellen: jede Sendung kommt doppelt mit um Sekunden
 * verschobener Startzeit, oder zwei verschiedene Programme überlappen sich – dann zeigten „Jetzt“ und „Danach“ dieselbe
 * Sendung. Nach Startzeit sortiert bleibt nur eine Kette ohne Überlappung: Ein Eintrag, der mehr als
 * [EPG_OVERLAP_TOLERANCE_S] vor dem Ende des vorigen beginnt, fällt weg (die erste Quelle gewinnt).
 * Ohne vollständige Zeitangaben bleibt die Liste, nur exakte Doppel fallen weg.
 */
fun List<EpgEntry>.cleaned(): List<EpgEntry> {
    val unique = distinct()
    val times = unique.map { epgSeconds(it.startAt) to epgSeconds(it.endAt) }
    if (times.any { (s, e) -> s == null || e == null }) return unique
    val kept = mutableListOf<EpgEntry>()
    var lastEnd: Long? = null // nicht Long.MIN_VALUE: minus Toleranz liefe über
    unique.zip(times).sortedBy { it.second.first }.forEach { (entry, t) ->
        val (start, end) = t
        val previous = lastEnd
        if (previous == null || start!! >= previous - EPG_OVERLAP_TOLERANCE_S) {
            kept += entry
            lastEnd = end!!
        }
    }
    return kept
}

/** Kleine Überlappungen (Sekunden bis wenige Minuten) gibt es auch innerhalb einer Quelle – die bleiben. */
const val EPG_OVERLAP_TOLERANCE_S = 5 * 60L

/** „yyyy-MM-dd HH:mm:ss“ → Sekunden seit 1970 (ohne Zeitzone, nur zum Vergleichen); unlesbar → null. */
internal fun epgSeconds(text: String): Long? {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})""").matchEntire(text.trim()) ?: return null
    val (y, mo, d, h, mi, se) = m.destructured
    // Tage seit 1970 nach dem bürgerlichen Kalender (Algorithmus von H. Hinnant).
    val yy = y.toLong() - if (mo.toInt() <= 2) 1 else 0
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val mp = (mo.toLong() + 9) % 12
    val doy = (153 * mp + 2) / 5 + d.toLong() - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * 146097 + doe - 719468
    return days * 86400 + h.toLong() * 3600 + mi.toLong() * 60 + se.toLong()
}

/** Verlauf: zuletzt gesehene Sender sowie Filme/Folgen mit Wiedergabeposition ("Weiterschauen"). */
data class HistoryEntry(val item: Item, val position: Long, val duration: Long, val updated: Long) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val canResume: Boolean get() = item.section != Section.LIVE && position > 30_000 && progress < 0.95f
}

/** Fehler mit verständlicher Meldung für die Oberfläche. */
class TvException(message: String) : Exception(message)

/**
 * Stand des Zwischenspeichers eines Bereichs: [loadedAt] = letztes erfolgreiches Laden aus dem Netz,
 * [previousCount] = Anzahl beim Laden davor (für „+389 seit letztem Laden“), [failed] = letzter Versuch scheiterte.
 */
data class CacheMeta(val loadedAt: Long, val count: Int, val previousCount: Int?, val failed: Boolean)

/**
 * Infos zu einem Film oder einer Serie für die Pause-Info (Xtream `get_vod_info` bzw. `get_series_info`).
 * Die Labels sind fertig formatiert (deutsch) und null, wenn der Anbieter nichts Brauchbares liefert.
 */
data class MediaInfo(
    val year: String?,
    val rating: Double?,
    val durationSecs: Int?,
    val age: Int?,
    val genres: List<String>,
    val plot: String?,
    // Für die Film-Detailseite – nur, wenn der Anbieter sie liefert.
    val director: String? = null,
    val cast: List<String> = emptyList(),
    val country: String? = null,
    /** Originaltitel, nur wenn er sich vom angezeigten Namen unterscheidet. */
    val originalTitle: String? = null,
    /** Großes Hintergrundbild (Adresse). */
    val backdrop: String? = null,
) {
    /** „8,3“ – 10er-Skala, eine Nachkommastelle; 0 oder fehlend → null. */
    val ratingLabel: String? get() = rating?.takeIf { it > 0 }?.let {
        val tenths = kotlin.math.round(it * 10).toInt()
        "${tenths / 10},${tenths % 10}"
    }

    /** „2 h 46 min“ bzw. „46 min“. */
    val durationLabel: String? get() = durationSecs?.takeIf { it > 0 }?.let {
        val min = (it + 30) / 60
        if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
    }

    val fskLabel: String? get() = age?.let { "FSK $it" }

    /** Höchstens drei Genres, kommagetrennt. */
    val genresLabel: String? get() = genres.take(3).takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/** Abo-Daten aus der Anmeldung: Ablauf (ms, null = unbegrenzt) und Verbindungen. */
data class AccountInfo(val expiresAt: Long?, val maxConnections: Int?, val activeConnections: Int?)

/** Abo-Daten mit dem Zeitpunkt, zu dem sie abgerufen wurden. */
data class AccountStatus(val info: AccountInfo, val fetchedAt: Long)

/** Länderkürzel des Anbieters mit der Zahl seiner Live-Kategorien – für die Länderauswahl. */
data class CountryOption(val tag: String, val count: Int)

/** Name aufgeteilt in das Anbieter-Etikett (Land/Format, z. B. „DE“, „4K“, „24/7“) und den eigentlichen Namen. */
/**
 * Anzeigename eines Senders, Films, einer Serie oder Kategorie: [tag] = Länder-/Formatkürzel davor, [name] = der
 * eigentliche Name ohne Qualitätszusätze, [badges] = diese Zusätze vereinheitlicht („4K“, „HD“, „RAW“ …),
 * [year] = Erscheinungsjahr aus „(2024)“.
 */
data class TaggedName(val tag: String?, val name: String, val badges: List<String> = emptyList(), val year: String? = null) {
    /** Name mit Kennzeichen („Netflix Movies 4K“) – für Kategorien, die sich nur darin unterscheiden. */
    val label: String get() = (listOf(name) + badges).joinToString(" ")
}

// Trenner nach dem Kürzel: | oder : sowie Pfeile (➥ ➤ ► ▶ → ») – Gedankenstriche nur mit Leerzeichen davor,
// sonst würde aus „ZDF-info“ fälschlich ein Land.
private val providerTag = Regex("""^\s*\|?\s*([A-Z]{2,3}|EXYU|4K|8K|UHD|24/7)\s*\|?\s*(?:[|:➥➤►▶→»]|\s[-–—])\s*(.+)$""")

/**
 * Anbieter schreiben Länder- und Formatkürzel vor Kategorien und Sender („DE | NACHRICHTEN“, „DE: Sport 1 HD“,
 * „|AT| Alpen TV“). Für die Anzeige trennen wir das Kürzel ab – so bleibt der eigentliche Name lesbar.
 */
fun splitProviderTag(raw: String): TaggedName {
    val base = providerTag.matchEntire(raw)?.let { TaggedName(it.groupValues[1], it.groupValues[2].trim()) } ?: TaggedName(null, raw.trim())
    val d = stripDecorations(base.name)
    // Bliebe nichts übrig („HD“ als ganzer Name), lieber den Namen wie geliefert.
    return base.copy(name = softenCaps(d.name.ifBlank { base.name }), badges = d.badges, year = d.year)
}

/** Wie [splitProviderTag], aber die Kennzeichen bleiben im Namen – Kategorien heißen sonst doppelt. */
fun splitCategoryName(raw: String): TaggedName = splitProviderTag(raw).let { it.copy(name = it.label) }

private class Decorations(val name: String, val badges: List<String>, val year: String?)

/**
 * Qualitäts- und Herkunftszusätze aus einem Namen lösen, wie Anbieter sie anhängen:
 * hochgestellte Wörter („ᴿᴬᵂ“, „ᴴᴰ“, „³⁸⁴⁰ᴾ“, „ᴰᵒˡᵇʸ ⱽᶦˢᶦᵒⁿ“) überall, normale („HD“, „FHD“, „4K“, „HEVC“) nur am Ende,
 * eckige Klammern („[4K]“), Jahr „(2024)“, Herkunft „(US)“ sowie Zierzeichen („◉“, „★“).
 */
private fun stripDecorations(raw: String): Decorations {
    var text = raw
    val badges = mutableListOf<String>()
    var year: String? = null
    YEAR.findAll(text).lastOrNull()?.let { year = it.groupValues[1] }
    text = YEAR.replace(text, " ")
    text = ORIGIN.replace(text, " ")
    text = BRACKET.replace(text) { m -> badgeOf(m.groupValues[1]).let { b -> if (b != null) { badges += b; " " } else m.value } }
    val words = text.split(' ').filter { it.isNotBlank() }.toMutableList()
    // Hochgestellte Wörter überall; zusammengehörige („ᴰᵒˡᵇʸ ⱽᶦˢᶦᵒⁿ“) erst als Ganzes deuten.
    val kept = mutableListOf<String>()
    var i = 0
    while (i < words.size) {
        val w = words[i]
        if (w.all { it in SUPERSCRIPT || it == '.' }) {
            val plain = w.map { SUPERSCRIPT[it] ?: it }.joinToString("").uppercase()
            val next = words.getOrNull(i + 1)?.takeIf { n -> n.all { it in SUPERSCRIPT } }?.map { SUPERSCRIPT[it] ?: it }?.joinToString("")?.uppercase()
            val pair = next?.let { badgeOf("$plain $it") }
            if (pair != null) { badges += pair; i += 2; continue }
            badgeOf(plain)?.let { badges += it }
            i++; continue
        }
        if (w in ORNAMENTS) { i++; continue }
        kept += w; i++
    }
    // Normale Zusätze nur am Ende („Sport 1 FHD“), damit „Ultra HD Sender“ heil bleibt.
    while (kept.size > 1) {
        val b = badgeOf(kept.last().trim('|', '-')) ?: break
        badges += b; kept.removeAt(kept.lastIndex)
    }
    val order = listOf("8K", "4K", "FHD", "HD", "SD", "HDR", "Dolby Vision", "Dolby Audio", "HEVC", "60 FPS", "RAW", "Multi")
    return Decorations(
        kept.joinToString(" ").trim().trimEnd('-', '|', ':').trim(),
        badges.distinct().sortedBy { order.indexOf(it).let { n -> if (n < 0) order.size else n } },
        year,
    )
}

/** Zusatz → einheitliches Kennzeichen; kein Qualitätszusatz → null. */
private fun badgeOf(token: String): String? = when (token.trim().uppercase().replace(" ", "")) {
    "8K", "4320P" -> "8K"
    "4K", "UHD", "2160P", "3840P", "4KUHD", "4KHDR" -> "4K"
    "FHD", "1080P", "FULLHD" -> "FHD"
    "HD", "720P" -> "HD"
    "SD", "576P", "480P" -> "SD"
    "HDR", "HDR10" -> "HDR"
    "HEVC", "H265", "H.265" -> "HEVC"
    "RAW" -> "RAW"
    "60FPS", "50FPS" -> "60 FPS"
    "DOLBYVISION" -> "Dolby Vision"
    "DOLBYAUDIO", "DOLBYATMOS" -> "Dolby Audio"
    "MULTI", "MULTI-SUBS", "MULTISUBS" -> "Multi"
    else -> null
}

private val YEAR = Regex("""\s*\(((?:19|20)\d\d)\)""")
private val ORIGIN = Regex("""\s*\([A-Z]{2}\)(?=\s|$)""")
private val BRACKET = Regex("""\[([^\]]{1,16})]""")
private val ORNAMENTS = setOf("◉", "●", "★", "☆", "✪", "⭐", "◆", "♦")

/** Hochgestellte Buchstaben und Ziffern (wie Anbieter sie für „ᴴᴰ“ nutzen) → normale Zeichen. */
private val SUPERSCRIPT: Map<Char, Char> = buildMap {
    "ᴬᴮᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿᵀᵁⱽᵂ".zip("ABDEGHIJKLMNOPRTUVW").forEach { (a, b) -> put(a, b) }
    "ᵃᵇᶜᵈᵉᶠᵍʰᶦʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻ".zip("abcdefghijklmnoprstuvwxyz").forEach { (a, b) -> put(a, b) }
    "⁰¹²³⁴⁵⁶⁷⁸⁹".zip("0123456789").forEach { (a, b) -> put(a, b) }
}

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

/**
 * Trennzeile statt Sender: Manche Anbieter setzen Überschriften als „Sender“ in die Liste („##### 4K UHD #####“,
 * „==== SPORT ====“) – nicht abspielbar, sie fallen beim Einlesen weg.
 */
fun isSeparatorEntry(name: String): Boolean = SEPARATOR.matches(name)

private val SEPARATOR = Regex("""^\s*[#=*~_-]{3,}.*[#=*~_-]{3,}\s*$""")
