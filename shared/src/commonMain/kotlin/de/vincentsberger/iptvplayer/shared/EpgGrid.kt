package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.epgSeconds

/** Wo eine Sendung zur Ortszeit steht. */
enum class Airing { PAST, NOW, FUTURE }

/**
 * Eine Zelle einer Raster-Zeile von [start] bis [end] (Sekunden im Maßstab von [epgSeconds], also der EPG-Texte).
 * [entry] null = Lücke oder keine Programmdaten.
 */
data class GridCell(val entry: EpgEntry?, val start: Long, val end: Long)

/**
 * Logik des Programmführer-Rasters, ohne Oberfläche – damit TV, PC und iPhone dieselben Regeln nutzen
 * (Spec `docs/superpowers/specs/2026-11-28-epg-grid-design.md`). Alle Zeiten in Sekunden über [epgSeconds] aus
 * `startAt`/`endAt` bzw. `localNow()` – nie über die oft falschen Timestamps des Anbieters.
 */
object EpgGrid {
    /** Raster der Kopfzeile und des Fensterbeginns: halbe Stunde. */
    const val SLOT_S = 1800L

    /** Sichtbare Breite des Rasters: zwei Stunden. */
    const val VISIBLE_S = 7200L

    private const val DAY_S = 86_400L

    /** Fensterbeginn beim Öffnen: letzte volle oder halbe Stunde vor [now]. */
    fun openingStart(now: Long): Long = floorSlot(now)

    /** Linke Grenze des ganzen Rasters: so weit zurück wie das längste Archiv der Kategorie, ohne Archiv der Fensterbeginn. */
    fun leftLimit(now: Long, openingStart: Long, maxArchiveDays: Int): Long =
        if (maxArchiveDays > 0) now - maxArchiveDays * DAY_S else openingStart

    /** Rechte Grenze: spätestes Ende über alle Zeilen, mindestens das Ende des ersten Fensters. */
    fun rightLimit(rows: List<List<EpgEntry>>, openingStart: Long): Long {
        val latest = rows.asSequence().flatten().mapNotNull { epgSeconds(it.endAt) }.maxOrNull()
        return maxOf(latest ?: Long.MIN_VALUE, openingStart + VISIBLE_S)
    }

    /**
     * Zellen einer Zeile, lückenlos von [from] bis [to]: Sendungen am Rand zugeschnitten, Lücken als leere Zellen,
     * Einträge ohne lesbare Zeiten fallen weg. Ohne Daten genau eine leere Zelle.
     */
    fun cells(entries: List<EpgEntry>, from: Long, to: Long): List<GridCell> {
        val timed = entries.mapNotNull { e ->
            val s = epgSeconds(e.startAt) ?: return@mapNotNull null
            val end = epgSeconds(e.endAt) ?: return@mapNotNull null
            Triple(e, s, end).takeIf { end > s }
        }.sortedBy { it.second }
        val out = ArrayList<GridCell>()
        var cursor = from
        for ((entry, s, e) in timed) {
            val start = maxOf(s, cursor)
            val end = minOf(e, to)
            if (end <= start) continue
            if (start > cursor) out += GridCell(null, cursor, start)
            out += GridCell(entry, start, end)
            cursor = end
        }
        if (cursor < to) out += GridCell(null, cursor, to)
        return out
    }

    /** Index der Zelle, die [time] überdeckt; davor die erste, danach die letzte. */
    fun cellAt(cells: List<GridCell>, time: Long): Int {
        val i = cells.indexOfFirst { time >= it.start && time < it.end }
        return when {
            i >= 0 -> i
            cells.isNotEmpty() && time < cells.first().start -> 0
            else -> cells.lastIndex.coerceAtLeast(0)
        }
    }

    /** Zeitstelle, die beim Wechsel der Zeile (▲▼) erhalten bleibt: Beginn der Zelle, aber nicht vor dem Fenster. */
    fun anchorOf(cell: GridCell, windowStart: Long): Long = maxOf(cell.start, windowStart)

    /**
     * Neuer Fensterbeginn, damit [cell] sichtbar ist – auf [SLOT_S] gerastert und auf `[left, right − VISIBLE_S]`
     * begrenzt. Liegt die Zelle links vom Fenster, beginnt es an ihr; ragt sie rechts hinaus, rückt das Fenster so weit,
     * dass ihr Ende sichtbar wird, aber nie über ihren Beginn hinaus (lange Sendungen zeigen ihren Anfang).
     */
    fun scrollTo(windowStart: Long, cell: GridCell, left: Long, right: Long): Long {
        val wanted = when {
            cell.start < windowStart -> floorSlot(cell.start)
            cell.end > windowStart + VISIBLE_S -> minOf(ceilSlot(cell.end - VISIBLE_S), floorSlot(cell.start))
            else -> return windowStart
        }
        return wanted.coerceIn(left, maxOf(left, right - VISIBLE_S))
    }

    /** Läuft [entry] zur Ortszeit [now], ist sie vorbei oder kommt sie noch? Unlesbare Zeiten gelten als künftig. */
    fun airing(entry: EpgEntry, now: Long): Airing {
        val start = epgSeconds(entry.startAt) ?: return Airing.FUTURE
        val end = epgSeconds(entry.endAt) ?: return Airing.FUTURE
        return when {
            now >= end -> Airing.PAST
            now >= start -> Airing.NOW
            else -> Airing.FUTURE
        }
    }

    private fun floorSlot(t: Long): Long = t - t.mod(SLOT_S)

    private fun ceilSlot(t: Long): Long = floorSlot(t + SLOT_S - 1)
}
