package de.vincentsberger.iptvplayer.app.ui

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.atomic.AtomicLong

/**
 * Zählt die Bytes, die die Datenquelle des Players aus dem Netz holt – auch bei fortlaufenden Quellen (MPEG-TS live, .mkv/.mp4),
 * bei denen `onLoadCompleted` erst am Ende meldet. Wird auf dem Ladethread aufgerufen, gelesen auf dem Main-Thread; gezählt
 * wird nur, solange [active] gesetzt ist (Statistik an). Aus dem Cache gelesene Bytes zählen nicht.
 */
@OptIn(UnstableApi::class)
class TransferCounter : TransferListener {
    private val counted = AtomicLong()

    @Volatile
    var active = false

    /** Bisher gezählte Bytes seit [reset]. */
    val total: Long get() = counted.get()

    /** Zähler auf 0 (neuer Titel oder neue Quelle). */
    fun reset() = counted.set(0)

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (active && isNetwork) counted.addAndGet(bytesTransferred.toLong())
    }

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
}
