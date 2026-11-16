package de.tvplayer.app

import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.tvplayer.app.ui.PlayerAction
import de.tvplayer.app.ui.epgProgress
import de.tvplayer.app.ui.formatTime
import de.tvplayer.app.ui.playerActionFor
import de.tvplayer.app.ui.wrapIndex
import de.tvplayer.shared.data.EpgEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Reine Logik aus Player und Live-TV – ohne Oberfläche, läuft trotzdem auf dem Gerät. */
@RunWith(AndroidJUnit4::class)
class PlayerLogicTest {

    @Test
    fun upAndDownZapOnlyForLiveAndEpisodes() {
        assertEquals(PlayerAction.PREVIOUS, playerActionFor(Key.DirectionUp, isLive = true, isEpisodes = false))
        assertEquals(PlayerAction.NEXT, playerActionFor(Key.ChannelDown, isLive = false, isEpisodes = true))
        assertEquals(PlayerAction.SHOW_INFO, playerActionFor(Key.DirectionDown, isLive = false, isEpisodes = false))
    }

    @Test
    fun seekingIsDisabledForLive() {
        assertEquals(PlayerAction.SHOW_INFO, playerActionFor(Key.DirectionLeft, isLive = true, isEpisodes = false))
        assertEquals(PlayerAction.SHOW_INFO, playerActionFor(Key.MediaFastForward, isLive = true, isEpisodes = false))
        assertEquals(PlayerAction.REWIND, playerActionFor(Key.MediaRewind, isLive = false, isEpisodes = false))
        assertEquals(PlayerAction.FORWARD, playerActionFor(Key.DirectionRight, isLive = false, isEpisodes = true))
    }

    @Test
    fun okAndMediaKeysControlPlayback() {
        listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause).forEach {
            assertEquals(PlayerAction.TOGGLE_PLAY, playerActionFor(it, isLive = false, isEpisodes = false))
        }
        assertEquals(PlayerAction.PLAY, playerActionFor(Key.MediaPlay, isLive = false, isEpisodes = false))
        assertEquals(PlayerAction.PAUSE, playerActionFor(Key.MediaPause, isLive = false, isEpisodes = false))
    }

    @Test
    fun unknownKeysAreNotHandledSoBackStillWorks() {
        assertNull(playerActionFor(Key.Back, isLive = true, isEpisodes = false))
        assertNull(playerActionFor(Key.A, isLive = false, isEpisodes = false))
    }

    @Test
    fun wrapIndexGoesAroundBothEnds() {
        assertEquals(2, wrapIndex(-1, 3))
        assertEquals(0, wrapIndex(3, 3))
        assertEquals(1, wrapIndex(1, 3))
    }

    @Test
    fun formatTimeUsesHoursOnlyWhenNeeded() {
        assertEquals("00:00", formatTime(-5_000))
        assertEquals("01:05", formatTime(65_000))
        assertEquals("1:01:01", formatTime(3_661_000))
    }

    @Test
    fun epgProgressHandlesNormalAndMidnightShows() {
        val show = EpgEntry("Tagesschau", "20:00", "20:15")
        assertEquals(0f, epgProgress(show, nowMinutes = 20 * 60), 0.001f)
        assertEquals(0.4f, epgProgress(show, nowMinutes = 20 * 60 + 6), 0.001f)
        assertEquals(1f, epgProgress(show, nowMinutes = 21 * 60), 0.001f)
        assertEquals("noch nicht angefangen", 0f, epgProgress(show, nowMinutes = 19 * 60), 0.001f)

        // Über Mitternacht: Ende vor Beginn, „jetzt“ nach Mitternacht
        val night = EpgEntry("Film", "23:30", "00:30")
        assertEquals(0.5f, epgProgress(night, nowMinutes = 0), 0.001f)
        assertEquals(0.25f, epgProgress(night, nowMinutes = 23 * 60 + 45), 0.001f)
    }

    @Test
    fun epgProgressIsZeroForUnreadableTimes() {
        assertEquals(0f, epgProgress(EpgEntry("?", "", "20:00"), nowMinutes = 600), 0f)
    }
}
