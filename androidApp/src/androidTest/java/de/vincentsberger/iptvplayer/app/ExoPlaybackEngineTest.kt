package de.vincentsberger.iptvplayer.app

import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.vincentsberger.iptvplayer.app.ui.ExoPlaybackEngine
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Der ExoPlayer-Adapter für den PlaybackController – Player und Zugriffe laufen auf dem Main-Thread. */
@RunWith(AndroidJUnit4::class)
class ExoPlaybackEngineTest {
    private var engine: ExoPlaybackEngine? = null
    private val film = Item("1", "Film", null, "c", "http://127.0.0.1:1/film.mp4", Section.MOVIES)

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun newEngine(clock: () -> Long = { 0L }): ExoPlaybackEngine {
        var e: ExoPlaybackEngine? = null
        onMain { e = ExoPlaybackEngine(ExoPlayer.Builder(InstrumentationRegistry.getInstrumentation().targetContext).build(), clock) }
        return e!!.also { engine = it }
    }

    @After fun release() = onMain { engine?.release() }

    @Test fun lengthNeverNegative() {
        val e = newEngine()
        onMain { assertEquals(0L, e.lengthMs) } // vor prepare: duration == C.TIME_UNSET
    }

    @Test fun vodReloadStartsAtResumePosition() {
        val e = newEngine()
        onMain {
            e.play(film, film.url, 60_000, keepFrame = true)
            assertEquals(60_000L, e.player.currentPosition)
        }
    }

    @Test fun playSetsStartedAt() {
        val e = newEngine { 777L }
        onMain {
            e.play(film, film.url, 0)
            assertEquals(777L, e.startedAt)
            assertFalse(e.failed)
        }
    }
}
