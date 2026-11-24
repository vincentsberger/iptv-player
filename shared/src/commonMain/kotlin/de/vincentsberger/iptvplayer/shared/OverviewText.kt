package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.AccountInfo
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.currentTimeMillis
import de.vincentsberger.iptvplayer.shared.data.localDateTime
import kotlin.math.abs

// Texte der Übersicht nach dem Laden – gleich in allen Apps.

/** Ganze Zahl mit deutschem Tausenderpunkt („12.345“). */
fun formatCount(n: Int): String {
    val digits = abs(n.toLong()).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return if (n < 0) "-$grouped" else grouped
}

/** Wort hinter der Anzahl eines Bereichs. */
fun countLabel(s: Section): String = when (s) {
    Section.LIVE -> "Sender"
    Section.MOVIES -> "Filme"
    Section.SERIES -> "Serien"
}

/** „heute 08:12“, „gestern 21:40“ oder „14.11. 08:12“ – in der Zeitzone des Geräts. */
fun standLabel(millis: Long, now: Long = currentTimeMillis()): String {
    val t = localDateTime(millis) // yyyy-MM-dd HH:mm:ss
    val date = t.substringBefore(' ')
    val time = t.substringAfter(' ').take(5)
    return when (date) {
        localDateTime(now).substringBefore(' ') -> "heute $time"
        localDateTime(now - DAY_MS).substringBefore(' ') -> "gestern $time"
        else -> "${date.drop(8).take(2)}.${date.drop(5).take(2)}. $time"
    }
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** „+389 seit letztem Laden“ bzw. der Grund der Warnung. */
fun overviewNote(row: OverviewRow, now: Long = currentTimeMillis()): String = when (row.warning) {
    OverviewWarning.FAILED -> "Fehler – " + (row.loadedAt?.let { "Stand vom ${standLabel(it, now)}" } ?: "keine Daten")
    OverviewWarning.EMPTY -> "leer geliefert – alter Stand behalten"
    OverviewWarning.CANCELLED -> "abgebrochen – nicht geladen"
    OverviewWarning.FEWER -> "${row.delta} – deutlich weniger als zuletzt"
    null -> when (val d = row.delta) {
        null -> ""
        0 -> "unverändert"
        else -> (if (d > 0) "+" else "") + formatCount(d) + " seit letztem Laden"
    }
}

/** „Abo bis 14.03.2027“ bzw. „Abo ohne Ablaufdatum“ – Datum in der Zeitzone des Geräts. */
fun subscriptionLabel(info: AccountInfo): String = info.expiresAt?.let { millis ->
    val date = localDateTime(millis).substringBefore(' ') // yyyy-MM-dd
    "Abo bis ${date.drop(8)}.${date.drop(5).take(2)}.${date.take(4)}"
} ?: "Abo ohne Ablaufdatum"

/** „1 von 2 Verbindungen in Gebrauch“; null, wenn der Anbieter keine Höchstzahl nennt. */
fun connectionsLabel(info: AccountInfo): String? =
    info.maxConnections?.let { "${info.activeConnections ?: 0} von $it Verbindungen in Gebrauch" }

/** Abo-Zeile der Startseite: „Abo bis 14.03.2027 · 1 von 2 Verbindungen“. */
fun accountSummary(info: AccountInfo): String =
    subscriptionLabel(info) + (info.maxConnections?.let { " · ${info.activeConnections ?: 0} von $it Verbindungen" } ?: "")
