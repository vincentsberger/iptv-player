package de.vincentsberger.iptvplayer.shared

/**
 * Info-Abzeichen für die Player-Leiste aus den Rohwerten des Players: Auflösung (aus der Bildhöhe), Tonkanäle,
 * Tonsprache und eingeschaltete Untertitel. Unbekannte oder unbrauchbare Werte ergeben kein Abzeichen.
 */
fun mediaBadges(videoHeight: Int?, audioChannels: Int?, audioLanguage: String?, subtitlesOn: Boolean): List<String> = listOfNotNull(
    resolutionBadge(videoHeight),
    when (audioChannels) { 1 -> "Mono"; 2 -> "2.0"; 6 -> "5.1"; 8 -> "7.1"; else -> null },
    languageBadge(audioLanguage),
    "UT".takeIf { subtitlesOn },
)

/** Großzügige Schwellen: Encoder runden die Höhe oft auf (1088 statt 1080). */
private fun resolutionBadge(height: Int?): String? = when {
    height == null || height <= 0 -> null
    height >= 2000 -> "4K"
    height >= 1000 -> "FHD"
    height >= 700 -> "HD"
    else -> "SD"
}

/** „de“/„deu“/„ger“ → „DE“; Platzhalter wie „und“ (unbestimmt) → null. */
private fun languageBadge(code: String?): String? {
    val c = code?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_') ?: return null
    if (c.isEmpty() || c in setOf("und", "mul", "zxx", "mis", "qaa")) return null
    if (c.length == 2) return c.uppercase()
    return (ISO3_TO_ISO2[c] ?: c).uppercase()
}

private val ISO3_TO_ISO2 = mapOf(
    "deu" to "de", "ger" to "de", "eng" to "en", "fra" to "fr", "fre" to "fr", "spa" to "es", "ita" to "it",
    "tur" to "tr", "nld" to "nl", "dut" to "nl", "pol" to "pl", "por" to "pt", "rus" to "ru", "ara" to "ar",
    "swe" to "sv", "dan" to "da", "nor" to "no", "fin" to "fi", "ell" to "el", "gre" to "el", "ces" to "cs",
    "cze" to "cs", "hun" to "hu", "ron" to "ro", "rum" to "ro", "hrv" to "hr", "srp" to "sr", "bul" to "bg",
    "ukr" to "uk", "jpn" to "ja", "kor" to "ko", "zho" to "zh", "chi" to "zh", "hin" to "hi", "alb" to "sq", "sqi" to "sq",
)
