package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Storage

/**
 * Eine wählbare Ton- oder Untertitelspur aus VLC (PC und iPhone). [id] ist die Spur-ID des Players, [language] der
 * Sprachcode, wie VLC ihn meldet („deu“, „ger“, „de“ …).
 */
data class MediaTrack(val id: Int, val label: String, val language: String?, val selected: Boolean, val channels: Int = 0)

/** Bildformate im Player; [key] ist der gespeicherte Wert ([AppState.videoFit]). */
enum class VideoFit(val key: String, val label: String) {
    FIT("fit", "Einpassen"),
    ZOOM("zoom", "Füllen"),
    FILL("fill", "Strecken");

    companion object {
        fun of(key: String?): VideoFit = entries.firstOrNull { it.key == key } ?: FIT
    }
}

/** Sprachcode → deutscher Name („deu“/„ger“/„de“ → „Deutsch“); leer oder „und“ → null, Unbekanntes in Großbuchstaben. */
fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code.equals("und", ignoreCase = true)) return null
    return LANGUAGES[normalizeLanguage(code)] ?: code.uppercase()
}

/** Gleiche Sprache, egal ob zwei- oder dreistellig angegeben („de“ = „deu“ = „ger“). */
fun sameLanguage(a: String?, b: String?): Boolean =
    a != null && b != null && normalizeLanguage(a) == normalizeLanguage(b)

/** ISO 639-2 (auch bibliografisch, „ger“) → ISO 639-1, soweit bekannt; sonst klein geschrieben unverändert. */
internal fun normalizeLanguage(code: String): String {
    val c = code.trim().lowercase()
    return if (c.length == 3) ISO3_TO_ISO2[c] ?: c else c
}

/** „Englisch · 5.1“, „Kommentar · Englisch“ oder notfalls „Spur 2“ – in allen Apps gleich. */
fun trackLabel(language: String?, description: String?, channels: Int, position: Int): String {
    val lang = languageName(language)
    val label = description?.trim()?.takeIf { it.isNotEmpty() }
    val parts = buildList {
        add(label ?: lang)
        if (label != null && lang != null && !label.contains(lang, ignoreCase = true)) add(lang)
        add(
            when (channels) {
                1 -> "Mono"
                6 -> "5.1"
                8 -> "7.1"
                else -> null
            }
        )
    }.filterNotNull()
    return parts.joinToString(" · ").ifEmpty { "Spur ${position + 1}" }
}

/** Was mit den Untertiteln beim Start eines Titels passieren soll. */
sealed interface SubtitleChoice {
    /** Keine Vorliebe oder Sprache nicht vorhanden: Vorgabe des Streams lassen. */
    data object Keep : SubtitleChoice
    data object Off : SubtitleChoice
    data class Track(val id: Int) : SubtitleChoice
}

/** Tonspur in der gespeicherten Sprache, sonst null (Vorgabe des Streams bleibt). */
fun preferredAudioTrack(tracks: List<MediaTrack>, preferred: String?): MediaTrack? =
    preferred?.let { p -> tracks.firstOrNull { sameLanguage(it.language, p) } }

/** Untertitel nach Vorliebe: [Storage.SUBTITLES_OFF] schaltet ab, eine Sprache wählt die erste passende Spur. */
fun preferredSubtitles(tracks: List<MediaTrack>, preferred: String?): SubtitleChoice = when (preferred) {
    null -> SubtitleChoice.Keep
    Storage.SUBTITLES_OFF -> SubtitleChoice.Off
    else -> tracks.firstOrNull { sameLanguage(it.language, preferred) }?.let { SubtitleChoice.Track(it.id) } ?: SubtitleChoice.Keep
}

/** ISO 639-1 → deutscher Name; die Sprachen, die IPTV-Anbieter in der Praxis liefern. */
private val LANGUAGES = mapOf(
    "de" to "Deutsch", "en" to "Englisch", "fr" to "Französisch", "es" to "Spanisch", "it" to "Italienisch",
    "pt" to "Portugiesisch", "nl" to "Niederländisch", "pl" to "Polnisch", "tr" to "Türkisch", "ru" to "Russisch",
    "uk" to "Ukrainisch", "ar" to "Arabisch", "fa" to "Persisch", "he" to "Hebräisch", "el" to "Griechisch",
    "cs" to "Tschechisch", "sk" to "Slowakisch", "hu" to "Ungarisch", "ro" to "Rumänisch", "bg" to "Bulgarisch",
    "hr" to "Kroatisch", "sr" to "Serbisch", "bs" to "Bosnisch", "sl" to "Slowenisch", "mk" to "Mazedonisch",
    "sq" to "Albanisch", "da" to "Dänisch", "sv" to "Schwedisch", "no" to "Norwegisch", "nb" to "Norwegisch (Bokmål)",
    "nn" to "Norwegisch (Nynorsk)", "fi" to "Finnisch", "is" to "Isländisch", "et" to "Estnisch", "lv" to "Lettisch",
    "lt" to "Litauisch", "ga" to "Irisch", "cy" to "Walisisch", "eu" to "Baskisch", "ca" to "Katalanisch",
    "gl" to "Galicisch", "hy" to "Armenisch", "ka" to "Georgisch", "az" to "Aserbaidschanisch", "kk" to "Kasachisch",
    "uz" to "Usbekisch", "ku" to "Kurdisch", "hi" to "Hindi", "ur" to "Urdu", "bn" to "Bengalisch", "pa" to "Punjabi",
    "ta" to "Tamil", "te" to "Telugu", "ml" to "Malayalam", "th" to "Thailändisch", "vi" to "Vietnamesisch",
    "id" to "Indonesisch", "ms" to "Malaiisch", "tl" to "Tagalog", "zh" to "Chinesisch", "ja" to "Japanisch",
    "ko" to "Koreanisch", "mn" to "Mongolisch", "am" to "Amharisch", "sw" to "Suaheli", "so" to "Somali",
    "af" to "Afrikaans", "la" to "Latein", "be" to "Belarussisch", "lb" to "Luxemburgisch", "mt" to "Maltesisch",
    "ps" to "Paschtu", "ne" to "Nepalesisch", "si" to "Singhalesisch", "km" to "Khmer", "lo" to "Laotisch",
    "my" to "Birmanisch", "yi" to "Jiddisch", "eo" to "Esperanto",
)

/** ISO 639-2 (terminologisch und bibliografisch) → ISO 639-1 für die Sprachen aus [LANGUAGES]. */
private val ISO3_TO_ISO2 = mapOf(
    "deu" to "de", "ger" to "de", "eng" to "en", "fra" to "fr", "fre" to "fr", "spa" to "es", "ita" to "it",
    "por" to "pt", "nld" to "nl", "dut" to "nl", "pol" to "pl", "tur" to "tr", "rus" to "ru", "ukr" to "uk",
    "ara" to "ar", "fas" to "fa", "per" to "fa", "heb" to "he", "ell" to "el", "gre" to "el", "ces" to "cs",
    "cze" to "cs", "slk" to "sk", "slo" to "sk", "hun" to "hu", "ron" to "ro", "rum" to "ro", "bul" to "bg",
    "hrv" to "hr", "srp" to "sr", "bos" to "bs", "slv" to "sl", "mkd" to "mk", "mac" to "mk", "sqi" to "sq",
    "alb" to "sq", "dan" to "da", "swe" to "sv", "nor" to "no", "nob" to "nb", "nno" to "nn", "fin" to "fi",
    "isl" to "is", "ice" to "is", "est" to "et", "lav" to "lv", "lit" to "lt", "gle" to "ga", "cym" to "cy",
    "wel" to "cy", "eus" to "eu", "baq" to "eu", "cat" to "ca", "glg" to "gl", "hye" to "hy", "arm" to "hy",
    "kat" to "ka", "geo" to "ka", "aze" to "az", "kaz" to "kk", "uzb" to "uz", "kur" to "ku", "hin" to "hi",
    "urd" to "ur", "ben" to "bn", "pan" to "pa", "tam" to "ta", "tel" to "te", "mal" to "ml", "tha" to "th",
    "vie" to "vi", "ind" to "id", "msa" to "ms", "may" to "ms", "tgl" to "tl", "zho" to "zh", "chi" to "zh",
    "jpn" to "ja", "kor" to "ko", "mon" to "mn", "amh" to "am", "swa" to "sw", "som" to "so", "afr" to "af",
    "lat" to "la", "bel" to "be", "ltz" to "lb", "mlt" to "mt", "pus" to "ps", "nep" to "ne", "sin" to "si",
    "khm" to "km", "lao" to "lo", "mya" to "my", "bur" to "my", "yid" to "yi", "epo" to "eo",
)
