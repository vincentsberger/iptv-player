package de.vincentsberger.iptvplayer.app

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import de.vincentsberger.iptvplayer.shared.data.HistoryEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * „Weiterschauen“ auf dem Startbildschirm von Google TV / Android TV (Watch Next, ab Android 8). Fire TV zeigt die
 * Zeile nicht an – dort ist das Abgleichen harmlos und ohne Wirkung.
 *
 * Kennung und Link enthalten nur einen Hash, nie die Stream-Adresse: Die trägt Benutzername und Passwort, und die
 * Einträge liegen im System-Speicher, den der Launcher liest.
 */
object WatchNext {
    const val MAX = 10
    private const val SCHEME = "iptvplayer"
    private const val HOST = "watchnext"

    /** Kennung eines Eintrags je Anbieter – stabil, ohne Rückschluss auf Adresse oder Zugangsdaten. */
    fun id(accountId: String?, item: Item): String =
        MessageDigest.getInstance("SHA-256").digest("${accountId.orEmpty()}|${item.favKey}".encodeToByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    /** Was auf den Startbildschirm gehört: angefangene Filme und Folgen, nie Erwachseneninhalte, neueste zuerst. */
    fun entries(history: List<HistoryEntry>, enabled: Boolean): List<HistoryEntry> =
        if (!enabled) emptyList()
        else history.filter { it.item.section != Section.LIVE && it.canResume && !it.item.isAdult }.take(MAX)

    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.resolveContentProvider(TvContractCompat.AUTHORITY, 0) != null

    /** Kennung aus einem Link vom Startbildschirm, sonst null. */
    fun linkId(intent: Intent?): String? =
        intent?.data?.takeIf { it.scheme == SCHEME && it.host == HOST }?.lastPathSegment

    /** Startbildschirm an [entries] angleichen: fehlende anlegen, vorhandene aktualisieren, übrige entfernen. */
    suspend fun sync(context: Context, accountId: String?, entries: List<HistoryEntry>) = withContext(Dispatchers.IO) {
        if (!supported(context)) return@withContext
        try {
            syncNow(context, accountId, entries)
        } catch (e: Exception) {
            // Launcher-Speicher fehlt, ist gesperrt oder verhält sich herstellerspezifisch: nie die App gefährden.
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun syncNow(context: Context, accountId: String?, entries: List<HistoryEntry>) {
        val resolver = context.contentResolver
        // Die Abfrage liefert nur die eigenen Einträge der App.
        val existing = HashMap<String, Long>()
        resolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            arrayOf(TvContractCompat.WatchNextPrograms._ID, TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) c.getString(1)?.let { existing[it] = c.getLong(0) } }

        val wanted = entries.associateBy { id(accountId, it.item) }
        existing.forEach { (key, row) ->
            if (key !in wanted) resolver.delete(TvContractCompat.buildWatchNextProgramUri(row), null, null)
        }
        wanted.forEach { (key, entry) ->
            val values = program(key, entry).toContentValues()
            val row = existing[key]
            if (row != null) resolver.update(TvContractCompat.buildWatchNextProgramUri(row), values, null, null)
            else resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
        }
    }

    private fun program(key: String, entry: HistoryEntry): WatchNextProgram {
        val episode = entry.item.section == Section.SERIES
        return WatchNextProgram.Builder()
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setType(if (episode) TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setTitle(splitProviderTag(entry.item.name).name)
            .apply {
                entry.item.logo?.takeIf { it.isNotBlank() }?.let {
                    setPosterArtUri(Uri.parse(it))
                    // Filme: Hochformat-Cover; Folgen: Standbild im Querformat.
                    setPosterArtAspectRatio(
                        if (episode) TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
                        else TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER,
                    )
                }
            }
            .setLastEngagementTimeUtcMillis(entry.updated)
            .setLastPlaybackPositionMillis(entry.position.toInt())
            .setDurationMillis(entry.duration.toInt())
            .setIntentUri(Uri.parse("$SCHEME://$HOST/$key"))
            .setInternalProviderId(key)
            .build()
    }
}
