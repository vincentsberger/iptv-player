package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section

/** Ein Download, so wie die Oberfläche ihn zeigt (TV-App und iPhone). */
data class DownloadInfo(
    val item: Item,
    val state: State,
    /** 0..1, solange bekannt; sonst null. */
    val progress: Float?,
    val bytes: Long,
) {
    enum class State { QUEUED, LOADING, PAUSED, DONE, FAILED }
}

/** Ergebnis von „Herunterladen“. */
enum class DownloadStartResult { STARTED, ALREADY, NOT_POSSIBLE, NO_SPACE }

/**
 * Regeln für Downloads, gleich in allen Apps: nur Dateien (Filme, Folgen), nie Live-Sender oder HLS-Listen;
 * ein Titel nur einmal; mindestens [MIN_FREE_BYTES] frei. Geladen wird immer nur ein Titel gleichzeitig – viele
 * Anbieter erlauben nur wenige Verbindungen. Die Plattformen setzen nur Speicher und Übertragung um.
 */
object DownloadRules {
    /** Unter diesem Wert startet kein neuer Download (Puffer fürs System und andere Apps). */
    const val MIN_FREE_BYTES = 1_000_000_000L

    /** Nur Dateien (Filme, Folgen) – Live-Sender und HLS-Streams lassen sich so nicht speichern. */
    fun canDownload(item: Item): Boolean =
        item.section != Section.LIVE && !item.catchup && item.url.isNotEmpty() &&
            !item.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    /** Darf [item] starten? [existing] = schon vorhandener Download derselben Adresse, [freeBytes] = freier Platz. */
    fun check(item: Item, existing: DownloadInfo?, freeBytes: Long): DownloadStartResult = when {
        !canDownload(item) -> DownloadStartResult.NOT_POSSIBLE
        existing != null -> DownloadStartResult.ALREADY
        freeBytes < MIN_FREE_BYTES -> DownloadStartResult.NO_SPACE
        else -> DownloadStartResult.STARTED
    }

    /** Der nächste Download, der laden darf: der älteste wartende – solange keiner lädt und nichts pausiert ist. */
    fun nextToLoad(all: List<DownloadInfo>, paused: Boolean): DownloadInfo? =
        if (paused || all.any { it.state == DownloadInfo.State.LOADING }) null
        else all.lastOrNull { it.state == DownloadInfo.State.QUEUED } // Liste: neueste zuerst

    /** Gibt es noch etwas zu laden? (nach einem Neustart gleich weitermachen) */
    fun hasOpen(all: List<DownloadInfo>): Boolean =
        all.any { it.state != DownloadInfo.State.DONE && it.state != DownloadInfo.State.FAILED }
}

/** „1,5 GB“, „350 MB“, „12 kB“ – wie in der TV-App. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> {
        val tenths = (bytes + 50_000_000) / 100_000_000
        "${tenths / 10},${tenths % 10} GB"
    }
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    else -> "${(bytes / 1000).coerceAtLeast(0)} kB"
}

/** Zustand eines Downloads in Worten. */
fun downloadStatus(info: DownloadInfo): String = when (info.state) {
    DownloadInfo.State.DONE -> "Offline verfügbar · ${formatBytes(info.bytes)}"
    DownloadInfo.State.LOADING -> listOfNotNull(
        "Wird geladen",
        info.progress?.let { "${(it * 100).toInt()} %" },
        formatBytes(info.bytes),
    ).joinToString(" · ")
    DownloadInfo.State.QUEUED -> "Wartet"
    DownloadInfo.State.PAUSED -> "Pausiert, solange du etwas aus dem Netz ansiehst"
    DownloadInfo.State.FAILED -> "Fehlgeschlagen – Verbindung oder Speicher prüfen"
}

/** Meldung nach „Herunterladen“; null = nichts zu sagen (schon vorhanden). */
fun downloadStartMessage(result: DownloadStartResult, freeBytes: Long): String? = when (result) {
    DownloadStartResult.STARTED -> "Wird geladen – den Fortschritt siehst du unter „Downloads“."
    DownloadStartResult.NO_SPACE -> "Zu wenig Speicher frei (noch ${formatBytes(freeBytes)})."
    DownloadStartResult.NOT_POSSIBLE -> "Dieser Titel lässt sich nicht herunterladen."
    DownloadStartResult.ALREADY -> null
}
