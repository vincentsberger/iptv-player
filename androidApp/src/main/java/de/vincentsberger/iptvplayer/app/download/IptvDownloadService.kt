package de.vincentsberger.iptvplayer.app.download

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import de.vincentsberger.iptvplayer.app.R

/**
 * Lädt im Hintergrund weiter, auch wenn man die App verlässt (Vordergrunddienst mit Benachrichtigung).
 * Ohne Scheduler: Bricht das System den Dienst ab, geht es beim nächsten App-Start weiter.
 */
@OptIn(UnstableApi::class)
class IptvDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    0,
) {
    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager {
        Downloads.init(this)
        return Downloads.manager
    }

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        notifications.buildProgressNotification(
            this, android.R.drawable.stat_sys_download, null, getString(R.string.download_running), downloads, notMetRequirements,
        )

    private companion object {
        const val NOTIFICATION_ID = 4711
        const val CHANNEL_ID = "downloads"
    }
}
