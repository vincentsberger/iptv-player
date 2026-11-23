package de.vincentsberger.iptvplayer.desktop.player

import java.util.Locale

/** Eine wählbare Ton- oder Untertitelspur aus VLC. [id] ist die Spur-ID für setTrack. */
data class VlcTrack(val id: Int, val label: String, val language: String?, val selected: Boolean, val channels: Int = 0)

/** Sprachcode → deutscher Name („deu“/„de“ → „Deutsch“); leer oder „und“ → null, Unbekanntes in Großbuchstaben. */
internal fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code == "und") return null
    // VLC liefert oft ISO 639-2 („deu“, „ger“) – Java kennt für die Anzeige aber nur zweistellige Codes.
    val two = if (code.length == 3) ISO3_TO_ISO2[code.lowercase()] ?: code else code
    val name = Locale.forLanguageTag(two).getDisplayLanguage(Locale.GERMAN)
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code.uppercase()
    else name.replaceFirstChar { it.titlecase(Locale.GERMAN) }
}

/** ISO 639-2 → ISO 639-1, inklusive der bibliografischen Varianten („ger“ statt „deu“). */
private val ISO3_TO_ISO2: Map<String, String> by lazy {
    Locale.getISOLanguages().associateBy { Locale.forLanguageTag(it).isO3Language } + mapOf(
        "ger" to "de", "fre" to "fr", "dut" to "nl", "chi" to "zh", "cze" to "cs", "gre" to "el", "per" to "fa",
        "rum" to "ro", "slo" to "sk", "alb" to "sq", "arm" to "hy", "baq" to "eu", "ice" to "is", "mac" to "mk", "wel" to "cy",
    )
}

/** Gleiche Sprache, egal ob zwei- oder dreistellig angegeben („de“ = „deu“ = „ger“). */
internal fun sameLanguage(a: String?, b: String?): Boolean {
    fun norm(c: String?) = c?.lowercase()?.let { if (it.length == 3) ISO3_TO_ISO2[it] ?: it else it }
    return a != null && b != null && norm(a) == norm(b)
}

/** „Englisch · 5.1“, „Kommentar · Englisch“ oder notfalls „Spur 2“ – wie in der TV-App. */
internal fun trackLabel(language: String?, description: String?, channels: Int, position: Int): String {
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
