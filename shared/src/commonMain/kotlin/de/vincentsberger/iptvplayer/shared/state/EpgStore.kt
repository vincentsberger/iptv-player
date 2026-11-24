package de.vincentsberger.iptvplayer.shared.state

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.notEndedAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Programmdaten: „Jetzt läuft“ je Sender (zwischengespeichert), ganzes Programm, Sendung verpasst. */
interface Epg {
    val programmes: SnapshotStateMap<String, List<EpgEntry>>
    fun requestProgramme(item: Item, force: Boolean = false)
    fun localNow(): String
    fun canCatchUp(channel: Item, entry: EpgEntry): Boolean
    fun nowFor(item: Item): EpgEntry?
    fun requestEpg(item: Item)
    suspend fun nowPlaying(item: Item): List<EpgEntry>
}

class EpgStore(
    private val session: Session,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val localTime: (Long) -> String,
) : Epg {

    // ---------- Ganzes Programm je Sender ----------

    /** Ganzes Programm je Sender (Schlüssel: Adresse) – geladen mit [requestProgramme]. */
    override val programmes = mutableStateMapOf<String, List<EpgEntry>>()
    private val programmeFetchedAt = HashMap<String, Long>()

    /** Programm laden, höchstens alle [PROGRAMME_REFRESH_MS] neu (es reicht ohnehin Tage weit). */
    override fun requestProgramme(item: Item, force: Boolean) {
        if (item.section != Section.LIVE || !item.hasEpg) return
        val last = programmeFetchedAt[item.url]
        if (!force && last != null && clock() - last < PROGRAMME_REFRESH_MS) return
        programmeFetchedAt[item.url] = clock()
        val r = session.repo ?: return
        scope.launch {
            val programme = r.programme(item)
            // Antwort eines inzwischen gewechselten Anbieters verwerfen – und den Abruf vergessen, sonst gälte der Sender
            // bis zu [PROGRAMME_REFRESH_MS] als geladen und das Panel zeigte so lange „Wird geladen …“.
            if (session.repo === r) programmes[item.url] = programme else programmeFetchedAt.remove(item.url)
        }
    }

    /** Jetzt in Ortszeit („yyyy-MM-dd HH:mm:ss“) – derselbe Maßstab wie die EPG-Texte. */
    override fun localNow(): String = localTime(clock())

    /** Lässt sich [entry] von [channel] nachträglich abspielen? */
    override fun canCatchUp(channel: Item, entry: EpgEntry): Boolean = session.repo?.catchupUrl(channel, entry) != null

    // ---------- „Jetzt läuft“: Programmdaten je Sender, zwischengespeichert ----------

    private class EpgCacheEntry(val entries: List<EpgEntry>, val fetchedAt: Long)
    private val epgCache = mutableStateMapOf<String, EpgCacheEntry>()
    private val epgPending = HashSet<String>()

    /** Laufende Sendung aus dem Zwischenspeicher (oder null, solange nichts geladen ist). */
    override fun nowFor(item: Item): EpgEntry? = epgCache[item.url]?.entries?.notEndedAt(localTime(clock()))?.firstOrNull()

    /** Programmdaten für einen sichtbaren Sender anfordern – lädt nur, wenn nichts Frisches da ist. */
    override fun requestEpg(item: Item) {
        // Ohne EPG-Kennung liefert der Anbieter nie Programmdaten – nicht fragen (spart beim Scrollen Tausende Anfragen).
        if (item.section != Section.LIVE || !item.hasEpg || item.url in epgPending || isFresh(item)) return
        epgPending += item.url
        scope.launch {
            try { fetchEpg(item) } finally { epgPending -= item.url }
        }
    }

    /**
     * Gespeichertes Programm reicht noch: jünger als [EPG_MIN_REFRESH_MS], oder jünger als [EPG_MAX_AGE_MS] und es
     * sind noch „Jetzt“ und „Danach“ übrig (bzw. so viele, wie der Anbieter überhaupt geliefert hat – Sender ohne EPG
     * werden so nur alle 3 Stunden gefragt).
     */
    private fun isFresh(item: Item): Boolean {
        val cached = epgCache[item.url] ?: return false
        val age = clock() - cached.fetchedAt
        if (age < EPG_MIN_REFRESH_MS) return true
        if (age >= EPG_MAX_AGE_MS) return false
        return cached.entries.notEndedAt(localTime(clock())).size >= minOf(2, cached.entries.size)
    }

    private suspend fun fetchEpg(item: Item): List<EpgEntry> {
        val entries = session.repo?.nowPlaying(item).orEmpty()
        epgCache[item.url] = EpgCacheEntry(entries, clock())
        return entries
    }

    /** Programm eines Senders (für Info-Spalte und Player) – nutzt den Zwischenspeicher, wenn er frisch ist. */
    override suspend fun nowPlaying(item: Item): List<EpgEntry> =
        (if (isFresh(item)) epgCache.getValue(item.url).entries else fetchEpg(item)).notEndedAt(localTime(clock()))

    /** Zwischenspeicher leeren – neuer Anbieter oder Listen neu geladen. */
    internal fun clear() = epgCache.clear()

    companion object {
        /** Ganzes Programm höchstens so oft neu laden (es reicht ohnehin Tage weit). */
        const val PROGRAMME_REFRESH_MS = 30 * 60_000L
        /** Frühestens nach dieser Zeit neu fragen – auch wenn der Vorrat leer ist (Anbieter liefert evtl. dasselbe). */
        const val EPG_MIN_REFRESH_MS = 10 * 60_000L
        /** Spätestens nach dieser Zeit neu laden, damit Programmänderungen (z. B. Verlängerung) ankommen. */
        const val EPG_MAX_AGE_MS = 3 * 60 * 60_000L
    }
}
