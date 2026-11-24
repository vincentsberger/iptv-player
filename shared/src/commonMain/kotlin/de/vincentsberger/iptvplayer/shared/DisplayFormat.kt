package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.currentTimeMillis
import de.vincentsberger.iptvplayer.shared.data.localDateTime

// Anzeige-Helfer für alle Apps (PC, TV, iPhone) – reine Funktionen ohne Plattform-API.

/** Index im Kreis: vor dem ersten kommt der letzte, nach dem letzten der erste. */
fun wrapIndex(i: Int, size: Int): Int = ((i % size) + size) % size

/** Spielzeit „mm:ss“, ab einer Stunde „h:mm:ss“; negative Werte zählen als 0. */
fun formatTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
}

/** Minute des Tages (0–1439) aus der Ortszeit „yyyy-MM-dd HH:mm:ss“; unlesbar → 0. */
fun minuteOfDay(localDateTime: String): Int {
    val time = localDateTime.substringAfter(' ', "")
    val h = time.substringBefore(':').toIntOrNull() ?: return 0
    val m = time.substringAfter(':', "").substringBefore(':').toIntOrNull() ?: return 0
    return if (h in 0..23 && m in 0..59) h * 60 + m else 0
}

/** Minute des Tages jetzt, in der Zeitzone des Geräts. */
fun currentMinuteOfDay(): Int = minuteOfDay(localDateTime(currentTimeMillis()))

/** Anteil einer Sendung, der schon gelaufen ist (0..1), anhand der Uhrzeiten „HH:mm“; [nowMinute] = Minute des Tages. */
fun epgProgress(e: EpgEntry, nowMinute: Int = currentMinuteOfDay()): Float {
    fun minutes(s: String): Int? {
        val p = s.split(":")
        if (p.size < 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }
    val start = minutes(e.start) ?: return 0f
    var end = minutes(e.end) ?: return 0f
    var now = nowMinute
    // Nur Sendungen über Mitternacht (z. B. 23:30–00:30) verschieben Ende und „jetzt“ in den nächsten Tag.
    // Sonst bedeutet „jetzt vor Beginn“ schlicht: noch nicht angefangen (0 %).
    if (end <= start) {
        end += 1440
        if (now < start) now += 1440
    }
    return ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

/**
 * Flaggen-Emoji für einen Ländercode („DE“ → 🇩🇪), sonst null. „UK“ ist für Anbieter Großbritannien (offiziell „GB“);
 * „AR“ steht bei IPTV-Anbietern fast immer für Arabisch, nicht für Argentinien – daher keine Flagge.
 */
fun flagOf(tag: String): String? {
    val code = when (val t = tag.uppercase()) { "UK" -> "GB"; "AR" -> return null; else -> t }
    if (code !in ISO_COUNTRIES) return null
    return code.map { regionalIndicator(it - 'A') }.joinToString("")
}

/** Regionalindikator-Buchstabe (U+1F1E6 + n) als UTF-16-Ersatzpaar – ohne java.lang.Character. */
private fun regionalIndicator(n: Int): String {
    val cp = 0x1F1E6 + n - 0x10000
    return charArrayOf((0xD800 + (cp shr 10)).toChar(), (0xDC00 + (cp and 0x3FF)).toChar()).concatToString()
}

/** ISO-3166-1-alpha-2-Codes (Stand: java.util.Locale.getISOCountries() des JDK 17). */
private val ISO_COUNTRIES: Set<String> = """
    AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE
    BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD
    CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM
    DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF
    GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU
    ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN
    KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME
    MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA
    NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM
    PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI
    SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK
    TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI
    VN VU WF WS YE YT ZA ZM ZW
""".trim().split(Regex("\\s+")).toSet()

/** Ganze Sendungsbeschreibung für die Vorschau – null, wenn es keine gibt oder sie nur den Titel wiederholt. */
fun epgDescription(entry: de.vincentsberger.iptvplayer.shared.data.EpgEntry?): String? =
    entry?.description?.trim()?.takeIf { it.isNotEmpty() && !it.equals(entry.title.trim(), ignoreCase = true) }

/**
 * Auszug aus der Sendungsbeschreibung für Listen und Info-Leisten: Zeilenumbrüche zu Leerzeichen, höchstens [max]
 * Zeichen, an einer Wortgrenze gekürzt mit „…“. Null, wenn es keine Beschreibung gibt oder sie nur den Titel wiederholt.
 */
fun epgExcerpt(entry: de.vincentsberger.iptvplayer.shared.data.EpgEntry?, max: Int = 140): String? {
    val text = entry?.description?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    if (text.isEmpty() || text.equals(entry?.title?.trim(), ignoreCase = true)) return null
    if (text.length <= max) return text
    val cut = text.take(max + 1).substringBeforeLast(' ').ifEmpty { text.take(max) }
    return cut.trimEnd(',', ';', ':', '-', '–', ' ') + " …"
}

/** Kurzangaben unter einem Poster: „2024 · ★ 7,3“ – Jahr aus dem Titel, Bewertung aus der Liste; nichts davon → null. */
fun posterFacts(item: de.vincentsberger.iptvplayer.shared.data.Item): String? = listOfNotNull(
    de.vincentsberger.iptvplayer.shared.data.splitProviderTag(item.name).year,
    item.rating?.let { r -> "★ " + ((r * 10).toInt() / 10).toString() + "," + ((r * 10).toInt() % 10).toString() },
).joinToString(" · ").ifEmpty { null }

/** Kopfzeile einer Serie sofort aus der Liste: „2017 · ★ 8,7 · Drama, Krimi“ (höchstens drei Genres); nichts → null. */
fun seriesFacts(item: de.vincentsberger.iptvplayer.shared.data.Item): String? = listOfNotNull(
    posterFacts(item),
    item.genre?.let { splitGenres(it).take(3).joinToString(", ") }?.takeIf { it.isNotEmpty() },
).joinToString(" · ").ifEmpty { null }

/** Ein Tag im Programm eines Senders: Überschrift („Heute“, „Mo, 23.11.“) und seine Sendungen. */
data class ProgrammeDay(val label: String, val entries: List<de.vincentsberger.iptvplayer.shared.data.EpgEntry>)

/**
 * Programm nach Tagen gruppieren, relativ zu [localNow] („yyyy-MM-dd HH:mm:ss“, Ortszeit wie die EPG-Texte):
 * „Gestern“, „Heute“, „Morgen“, sonst Wochentag und Datum. Einträge ohne Datum landen unter „Heute“.
 */
fun programmeDays(entries: List<de.vincentsberger.iptvplayer.shared.data.EpgEntry>, localNow: String): List<ProgrammeDay> {
    val today = localNow.take(10)
    fun dayIndex(date: String) = de.vincentsberger.iptvplayer.shared.data.epgSeconds("$date 00:00:00")?.div(86_400)
    val todayIndex = dayIndex(today)
    return entries.groupBy { it.startAt.take(10).ifEmpty { today } }.toList().sortedBy { it.first }.map { (date, list) ->
        val d = dayIndex(date)
        val label = when {
            d == null || todayIndex == null -> date
            d == todayIndex -> "Heute"
            d == todayIndex - 1 -> "Gestern"
            d == todayIndex + 1 -> "Morgen"
            else -> {
                val weekday = listOf("Do", "Fr", "Sa", "So", "Mo", "Di", "Mi")[(((d % 7) + 7) % 7).toInt()] // 1.1.1970 war ein Donnerstag
                "$weekday, ${date.substring(8, 10)}.${date.substring(5, 7)}."
            }
        }
        ProgrammeDay(label, list)
    }
}

/** Läuft [entry] zur Ortszeit [localNow]? */
fun isOnAir(entry: de.vincentsberger.iptvplayer.shared.data.EpgEntry, localNow: String): Boolean =
    entry.startAt.isNotEmpty() && entry.endAt.isNotEmpty() && entry.startAt <= localNow && localNow < entry.endAt
