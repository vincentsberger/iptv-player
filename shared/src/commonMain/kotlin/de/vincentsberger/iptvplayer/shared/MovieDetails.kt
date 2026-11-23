package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.MediaInfo

/** Infozeile der Film-Detailseite: Jahr · ★ Bewertung · Dauer · FSK · Genres · Land – nur, was vorhanden ist. */
fun movieFacts(info: MediaInfo?): List<String> = if (info == null) emptyList() else listOfNotNull(
    info.year, info.ratingLabel?.let { "★ $it" }, info.durationLabel, info.fskLabel, info.genresLabel, info.country,
)

/** Hauptknopf: „Abspielen“ oder „Weiterschauen ab 1:12:30“ (bzw. „ab 12:05“ unter einer Stunde). */
fun playLabel(resumeAtMs: Long): String {
    if (resumeAtMs <= 0) return "Abspielen"
    val s = resumeAtMs / 1000
    val (h, m, sec) = Triple(s / 3600, s / 60 % 60, s % 60)
    fun two(n: Long) = n.toString().padStart(2, '0')
    return "Weiterschauen ab " + if (h > 0) "$h:${two(m)}:${two(sec)}" else "$m:${two(sec)}"
}

/** Folgenliste: „45 min“, angefangen „45 min · noch 23 min“; ohne Dauer null. */
fun episodeMeta(durationSecs: Int?, progress: Float): String? {
    val secs = durationSecs?.takeIf { it > 0 } ?: return null
    fun label(s: Int): String { val min = (s + 30) / 60; return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min" }
    val left = (secs * (1f - progress)).toInt()
    return if (progress > 0f && progress < 1f) "${label(secs)} · noch ${label(left)}" else label(secs)
}
