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
