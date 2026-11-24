package de.vincentsberger.iptvplayer.shared.player.stats

private const val DASH = "–"
private const val SCHEME_SEPARATOR = "://"
private const val MEGA = 1_000_000L
private const val KILO = 1_000L
private const val TENTH_BPS = 100_000L
private const val TENTH_MS = 100L
private const val TEN = 10L
private const val HUNDRED = 100
private const val MONO = 1
private const val STEREO = 2

/** `host:port` der Stream-Adresse – nie Zugangsdaten, Pfad oder Query. */
fun hostAndPort(url: String): String {
    val schemeEnd = url.indexOf(SCHEME_SEPARATOR)
    if (schemeEnd <= 0 || url.any { it.isWhitespace() }) return DASH
    if (url.substring(0, schemeEnd).equals("file", ignoreCase = true)) return "Datei auf dem Gerät"
    val rest = url.substring(schemeEnd + SCHEME_SEPARATOR.length)
    val authority = rest.substring(0, rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it })
    return authority.substringAfterLast('@').ifEmpty { DASH }
}

/** Art des Streams aus der Endung: HLS, MPEG-TS, sonst die Endung in Großbuchstaben. */
fun streamKind(url: String): String {
    val schemeEnd = url.indexOf(SCHEME_SEPARATOR)
    val afterScheme = if (schemeEnd >= 0) url.substring(schemeEnd + SCHEME_SEPARATOR.length) else url
    val noQuery = afterScheme.substringBefore('?').substringBefore('#')
    if (!noQuery.contains('/')) return DASH
    val last = noQuery.substringAfterLast('/')
    if (!last.contains('.')) return DASH
    return when (val ext = last.substringAfterLast('.').lowercase()) {
        "" -> DASH
        "m3u8" -> "HLS (m3u8)"
        "ts" -> "MPEG-TS"
        else -> ext.uppercase()
    }
}

/** Erkennungsmerkmale (Teilstrings der Kleinschreibung) → Anzeigename; E-AC-3 steht vor AC-3, weil es dessen Teilstring enthält. */
private val CODECS = listOf(
    listOf("avc", "h264", "h.264") to "H.264",
    listOf("hvc", "hev", "h265", "h.265") to "H.265",
    listOf("av01") to "AV1",
    listOf("ec-3", "eac3", "e-ac-3") to "E-AC-3",
    listOf("ac-3", "ac3", "a52") to "AC-3",
    listOf("mp4a", "aac") to "AAC",
)

/** Lesbarer Codec-Name; unbekannte Kennungen in Großbuchstaben, `null` bleibt `null`. */
fun codecName(raw: String?): String? {
    val c = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return CODECS.firstOrNull { (keys, _) -> keys.any { it in c } }?.second ?: c.uppercase()
}

private fun tenths(t: Long) = "${t / TEN},${t % TEN}"

/** „6,1 Mbit/s“, „850 kbit/s“ oder „–“. */
fun formatBitrate(bps: Long?): String = when {
    bps == null || bps < 0 -> DASH
    bps >= MEGA -> "${tenths((bps + TENTH_BPS / 2) / TENTH_BPS)} Mbit/s"
    else -> "${(bps + KILO / 2) / KILO} kbit/s"
}

/** „12,4 s“ oder „–“. */
fun formatBuffer(ms: Long?): String = if (ms == null || ms < 0) DASH else "${tenths((ms + TENTH_MS / 2) / TENTH_MS)} s"

private fun formatFps(fps: Float): String {
    val hundredths = kotlin.math.round(fps * HUNDRED).toInt()
    val whole = hundredths / HUNDRED
    val frac = (hundredths % HUNDRED).toString().padStart(2, '0').trimEnd('0')
    return if (frac.isEmpty()) "$whole fps" else "$whole,$frac fps"
}

/** „H.264 · 1920×1080 · 50 fps“; fehlende Teile entfallen, fehlt alles: „–“. */
fun videoLine(s: EngineStats): String {
    val w = s.width?.takeIf { it > 0 }
    val h = s.height?.takeIf { it > 0 }
    val size = if (w != null && h != null) "${w}×${h}" else null
    val fps = s.fps?.takeIf { it > 0f }?.let { formatFps(it) }
    return listOfNotNull(codecName(s.videoCodec), size, fps).joinToString(" · ").ifEmpty { DASH }
}

/** „AAC · Stereo · deu“; fehlende Teile entfallen, fehlt alles: „–“. */
fun audioLine(s: EngineStats): String {
    val channels = s.audioChannels?.takeIf { it > 0 }?.let {
        when (it) {
            MONO -> "Mono"
            STEREO -> "Stereo"
            else -> "$it Kanäle"
        }
    }
    return listOfNotNull(codecName(s.audioCodec), channels, s.audioLanguage?.takeIf { it.isNotBlank() })
        .joinToString(" · ").ifEmpty { DASH }
}
