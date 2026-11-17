package de.vincentsberger.iptvplayer.app.download

import android.content.Context
import android.net.ConnectivityManager
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.USER_AGENT
import de.vincentsberger.iptvplayer.shared.data.httpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/** Ein Download, so wie die Oberfläche ihn zeigt. */
data class DownloadInfo(
    val item: Item,
    val state: State,
    /** 0..1, solange bekannt; sonst null. */
    val progress: Float?,
    val bytes: Long,
) {
    enum class State { QUEUED, LOADING, PAUSED, DONE, FAILED }
}

/**
 * Downloads für unterwegs: Filme und Folgen werden vollständig auf das Gerät geladen
 * (Media3 DownloadManager, Dateien im App-Speicher) und vom Player aus diesem Speicher abgespielt –
 * auch ganz ohne Internet. Es lädt immer nur ein Titel gleichzeitig, weil viele Anbieter
 * nur wenige Verbindungen erlauben.
 */
@OptIn(UnstableApi::class)
object Downloads {
    /** Unter diesem Wert startet kein neuer Download (Puffer fürs System und andere Apps). */
    const val MIN_FREE_BYTES = 1_000_000_000L

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val dir: File by lazy { File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "downloads") }
    private val database by lazy { StandaloneDatabaseProvider(appContext) }
    val cache: SimpleCache by lazy { SimpleCache(dir, NoOpCacheEvictor(), database) }
    private val http by lazy { OkHttpDataSource.Factory(httpClient).setUserAgent(USER_AGENT) }

    val manager: DownloadManager by lazy {
        DownloadManager(appContext, database, cache, http, Executors.newFixedThreadPool(2)).apply {
            maxParallelDownloads = 1
            addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(m: DownloadManager, d: Download, e: Exception?) = refresh()
                override fun onDownloadRemoved(m: DownloadManager, d: Download) = refresh()
                override fun onIdle(m: DownloadManager) = refresh()
            })
        }
    }

    /** Alle Downloads, neueste zuerst (Compose-State: Oberflächen zeichnen sich bei Änderungen neu). */
    var all by mutableStateOf<List<DownloadInfo>>(emptyList()); private set

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        refresh()
        // Der DownloadManager startet pausiert und lädt erst, wenn der Dienst läuft –
        // nach einem App-Neustart offene Downloads also gleich weiterladen.
        if (all.any { it.state != DownloadInfo.State.DONE && it.state != DownloadInfo.State.FAILED }) setPaused(false)
        // Fortschritt meldet der DownloadManager nicht von selbst – solange etwas lädt, jede Sekunde nachsehen.
        scope.launch {
            while (true) {
                delay(1000)
                if (manager.currentDownloads.isNotEmpty()) refresh()
            }
        }
    }

    private fun refresh() {
        val list = mutableListOf<Pair<Long, DownloadInfo>>()
        manager.downloadIndex.getDownloads().use { c ->
            while (c.moveToNext()) {
                val d = c.download
                val item = Item.fromJson(d.request.data.decodeToString()) ?: continue
                list += d.startTimeMs to DownloadInfo(item, stateOf(d), progressOf(d), d.bytesDownloaded)
            }
        }
        all = list.sortedByDescending { it.first }.map { it.second }
    }

    private fun stateOf(d: Download) = when (d.state) {
        Download.STATE_COMPLETED -> DownloadInfo.State.DONE
        Download.STATE_DOWNLOADING -> DownloadInfo.State.LOADING
        Download.STATE_FAILED -> DownloadInfo.State.FAILED
        Download.STATE_STOPPED -> DownloadInfo.State.PAUSED
        else -> if (manager.downloadsPaused) DownloadInfo.State.PAUSED else DownloadInfo.State.QUEUED
    }

    private fun progressOf(d: Download): Float? = when {
        d.state == Download.STATE_COMPLETED -> 1f
        d.percentDownloaded >= 0 -> d.percentDownloaded / 100f
        else -> null
    }

    fun infoFor(url: String): DownloadInfo? = all.firstOrNull { it.item.url == url }

    fun isDone(url: String) = infoFor(url)?.state == DownloadInfo.State.DONE

    /** Nur Dateien (Filme, Folgen) – Live-Sender und HLS-Streams lassen sich so nicht speichern. */
    fun canDownload(item: Item): Boolean =
        item.section != Section.LIVE && item.url.isNotEmpty() &&
            !item.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    /** Freier Platz im Download-Ordner. */
    fun freeBytes(): Long = runCatching { dir.mkdirs(); StatFs(dir.path).availableBytes }.getOrDefault(0L)

    /** Belegter Platz aller Downloads. */
    fun usedBytes(): Long = all.sumOf { it.bytes }

    enum class StartResult { STARTED, ALREADY, NOT_POSSIBLE, NO_SPACE }

    fun start(item: Item): StartResult {
        if (!canDownload(item)) return StartResult.NOT_POSSIBLE
        if (infoFor(item.url) != null) return StartResult.ALREADY
        if (freeBytes() < MIN_FREE_BYTES) return StartResult.NO_SPACE
        send(item)
        return StartResult.STARTED
    }

    private fun send(item: Item) {
        val request = DownloadRequest.Builder(item.url, android.net.Uri.parse(item.url))
            .setData(item.toJson().encodeToByteArray())
            .build()
        DownloadService.sendAddDownload(appContext, IptvDownloadService::class.java, request, false)
    }

    fun remove(url: String) {
        DownloadService.sendRemoveDownload(appContext, IptvDownloadService::class.java, url, false)
    }

    /** Fehlgeschlagenen Download neu anstoßen – denselben Auftrag erneut hinzufügen setzt ihn zurück. */
    fun retry(info: DownloadInfo) = send(info.item)

    /** Während der Wiedergabe aus dem Netz alle Downloads anhalten (bzw. wieder weiterladen). */
    fun setPaused(paused: Boolean) {
        if (!::appContext.isInitialized) return
        if (paused) DownloadService.sendPauseDownloads(appContext, IptvDownloadService::class.java, false)
        else DownloadService.sendResumeDownloads(appContext, IptvDownloadService::class.java, false)
    }

    /**
     * Datenquelle für den Player: liest zuerst aus den Downloads, sonst aus dem Netz.
     * Schreibt nichts in den Speicher – gestreamte Inhalte belegen also keinen Platz.
     */
    fun playerDataSource(context: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, http))
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Gibt es gerade Internet? (Für den Start im Hotel: dann gleich zu den Downloads.) */
    @Suppress("DEPRECATION") // activeNetworkInfo: einzige Variante, die auch auf Fire OS 5 (API 22) läuft
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        return cm.activeNetworkInfo?.isConnected == true
    }
}
