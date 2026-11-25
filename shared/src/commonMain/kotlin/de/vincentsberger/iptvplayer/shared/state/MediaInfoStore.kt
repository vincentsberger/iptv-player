package de.vincentsberger.iptvplayer.shared.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.data.EpisodeDetail
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.MediaInfo
import de.vincentsberger.iptvplayer.shared.data.Season
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Infos zu Filmen und Serien (Pause-Info, Detailseiten) und die Staffeln der geöffneten Serie. */
interface MediaDetails {
    val seasons: List<Season>
    val seasonsLoading: Boolean
    val seasonsError: String?
    fun mediaInfoFor(item: Item): MediaInfo?
    /** Abfrage beendet (auch ohne Ergebnis, z. B. M3U oder Fehler) – sonst „wird geladen“. Ohne Detailabfrage: true. */
    fun mediaInfoLoaded(item: Item): Boolean
    fun episodeDetail(episode: Item): EpisodeDetail?
    fun seriesInfoFor(series: Item): MediaInfo?
    fun requestMediaInfo(item: Item)
    fun episodeLabel(item: Item): String?
    fun pauseTitles(item: Item): Pair<String, String?>
    fun seriesNameOf(item: Item): String?
    /**
     * Nächste Folge nach [episode] in ihrer Serie (über Staffelgrenzen hinweg): alle Folgen der Serie und der Index der
     * nächsten. Null bei der letzten Folge, ohne Serien-Ordner (M3U), bei Fehlern oder nach einem Anbieterwechsel.
     */
    suspend fun nextEpisodeAfter(episode: Item): Pair<List<Item>, Int>?
}

/** Schlüssel der geladenen Infos: Film („MOVIES:id“) bzw. die Serie einer Folge („SERIES:id“); sonst null. */
private fun mediaKey(item: Item): String? = when {
    item.section == Section.MOVIES -> "MOVIES:${item.id}"
    item.section == Section.SERIES && !item.isSeriesFolder -> "SERIES:${item.categoryId}" // Folge → ihre Serie
    else -> null
}

@Suppress("TooManyFunctions") // die Funktionen gibt die Schnittstelle MediaDetails vor (Fassade AppState)
class MediaInfoStore(
    private val session: Session,
    private val library: Library,
    private val scope: CoroutineScope,
) : MediaDetails {

    override var seasons by mutableStateOf<List<Season>>(emptyList()); private set
    override var seasonsLoading by mutableStateOf(false); private set
    override var seasonsError by mutableStateOf<String?>(null); private set

    /** Geladene Infos je Film („MOVIES:id“) bzw. Serie („SERIES:id“); null = Anbieter liefert keine. */
    private val mediaInfos = mutableStateMapOf<String, MediaInfo?>()
    /** Staffeln von Serien, deren Folgen direkt geöffnet wurden (z. B. aus „Weiterschauen“) – für das Folgen-Label. */
    private val episodeSeasons = mutableStateMapOf<String, List<Season>>()

    override fun mediaInfoFor(item: Item): MediaInfo? = mediaKey(item)?.let { mediaInfos[it] }

    override fun mediaInfoLoaded(item: Item): Boolean = mediaKey(item)?.let { it in mediaInfos } ?: true

    /** Handlung und Dauer einer Folge aus den geladenen Staffeln (Folgenliste der Serienseite). */
    override fun episodeDetail(episode: Item): EpisodeDetail? = seasons.firstNotNullOfOrNull { it.details[episode.url] }

    /** Infos (Beschreibung, Jahr, Genre …) einer Serie für die Serienseite – kommen mit den Staffeln. */
    override fun seriesInfoFor(series: Item): MediaInfo? = mediaInfos["SERIES:${series.id}"]

    /** Beim Öffnen des Players im Hintergrund laden, damit die Info beim Pausieren schon da ist. */
    override fun requestMediaInfo(item: Item) {
        if (item.catchup) return
        val key = mediaKey(item) ?: return
        if (key in mediaInfos) return
        val r = session.repo ?: return
        scope.launch {
            // Netz- oder Formatfehler: als „ohne Ergebnis“ eintragen, sonst bliebe „wird geladen“ für immer stehen.
            val info = runCatching<MediaInfo?> { if (item.section == Section.MOVIES) r.vodInfo(item) else {
                val series = library.sections[Section.SERIES]?.items?.firstOrNull { it.id == item.categoryId && it.isSeriesFolder }
                    ?: Item(item.categoryId, "", null, "", "", Section.SERIES)
                r.seriesInfo(series).also { r.cachedSeasons(item.categoryId)?.let { s -> if (session.repo === r) episodeSeasons[item.categoryId] = s } }
            } }.getOrNull()
            if (session.repo === r) mediaInfos[key] = info
        }
    }

    /** „S2 · F3 · Der Zug“ – aus den geladenen Staffeln; null, wenn die Folge dort nicht steht. */
    override fun episodeLabel(item: Item): String? {
        (seasons + episodeSeasons[item.categoryId].orEmpty()).forEach { season ->
            val i = season.episodes.indexOfFirst { it.url == item.url }
            if (i >= 0) return "S${season.number} · F${i + 1} · ${item.name}"
        }
        return null
    }

    /**
     * Titel und Untertitel der Pause-Info: Filme ihr Name; Folgen der Serienname mit „S2 · F3 · Titel“ – ist die
     * Folge in keiner geladenen Staffel zu finden, wenigstens ihr eigener Titel.
     */
    override fun pauseTitles(item: Item): Pair<String, String?> {
        if (item.section != Section.SERIES) return item.name to null
        val series = seriesNameOf(item) ?: return item.name to episodeLabel(item)
        return series to (episodeLabel(item) ?: item.name)
    }

    /** Name der Serie zu einer Folge (über die Serien-ID der Folge). */
    override fun seriesNameOf(item: Item): String? =
        library.sections[Section.SERIES]?.items?.firstOrNull { it.id == item.categoryId && it.isSeriesFolder }?.name

    override suspend fun nextEpisodeAfter(episode: Item): Pair<List<Item>, Int>? {
        if (episode.section != Section.SERIES || episode.isSeriesFolder || episode.catchup) return null
        val r = session.repo ?: return null
        val series = library.sections[Section.SERIES]?.items?.firstOrNull { it.id == episode.categoryId && it.isSeriesFolder }
            ?: Item(episode.categoryId, "", null, "", "", Section.SERIES)
        // Ohne Netz: dann eben keine nächste Folge, wie bisher. Abbruch (Player geschlossen) weiterreichen.
        val all = runCatching { r.seasons(series) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            ?.flatMap { it.episodes } ?: return null
        if (session.repo !== r) return null
        val i = all.indexOfFirst { it.url == episode.url }
        return if (i in 0 until all.lastIndex) all to i + 1 else null
    }

    /** Staffeln einer Serie laden (Serienseite) – die Serien-Infos kommen dabei gleich mit. */
    internal fun loadSeasons(series: Item) {
        seasons = emptyList(); seasonsError = null; seasonsLoading = true
        scope.launch {
            try {
                val r = session.repo
                seasons = r?.seasons(series).orEmpty()
                if (seasons.isEmpty()) seasonsError = "Für diese Serie sind keine Folgen verfügbar."
                // Schon mit den Staffeln geladen – kein weiterer Abruf.
                if (r != null && session.repo === r) mediaInfos["SERIES:${series.id}"] = r.seriesInfo(series)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                seasonsError = e.message ?: "Folgen konnten nicht geladen werden"
            } finally {
                seasonsLoading = false
            }
        }
    }

    /** Infos gelten nur je Anbieter (IDs) – beim Wechsel leeren. */
    internal fun reloadForAccount() {
        mediaInfos.clear(); episodeSeasons.clear()
    }
}
