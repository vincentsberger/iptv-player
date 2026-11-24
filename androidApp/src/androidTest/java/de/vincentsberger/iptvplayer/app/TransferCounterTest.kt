package de.vincentsberger.iptvplayer.app

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.TransferCounter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Byte-Zähler der Player-Datenquelle (Datenrate der Statistik). */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TransferCounterTest {
    private val source = DefaultHttpDataSource.Factory().createDataSource()
    private val spec = DataSpec(android.net.Uri.parse("http://127.0.0.1:1/x.ts"))

    @Test fun countsNetworkBytesOnlyWhileActive() {
        val c = TransferCounter()
        c.onBytesTransferred(source, spec, true, 100)
        assertEquals(0L, c.total)
        c.active = true
        c.onBytesTransferred(source, spec, true, 100)
        c.onBytesTransferred(source, spec, true, 50)
        assertEquals(150L, c.total)
        c.active = false
        c.onBytesTransferred(source, spec, true, 70)
        assertEquals(150L, c.total)
    }

    @Test fun ignoresCacheReadsAndResets() {
        val c = TransferCounter().also { it.active = true }
        c.onBytesTransferred(source, spec, false, 999)
        assertEquals(0L, c.total)
        c.onBytesTransferred(source, spec, true, 10)
        c.reset()
        assertEquals(0L, c.total)
    }
}
