package de.vincentsberger.iptvplayer.app

import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.PlayerAction
import de.vincentsberger.iptvplayer.app.ui.digitOf
import de.vincentsberger.iptvplayer.app.ui.epgProgress
import de.vincentsberger.iptvplayer.app.ui.formatTime
import de.vincentsberger.iptvplayer.app.ui.playerActionFor
import de.vincentsberger.iptvplayer.app.ui.wrapIndex
import de.vincentsberger.iptvplayer.app.ui.languageName
import de.vincentsberger.iptvplayer.app.ui.isOkKey
import de.vincentsberger.iptvplayer.app.ui.trackLabel
import de.vincentsberger.iptvplayer.app.ui.preferredTrackParameters
import de.vincentsberger.iptvplayer.shared.data.Storage
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionParameters
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reine Logik aus Player und Live-TV – ohne Oberfläche, läuft trotzdem auf dem Gerät. */
@RunWith(AndroidJUnit4::class)
class PlayerLogicTest {

    @Test
    fun upAndDownZapOnlyForLiveAndEpisodes() {
        assertEquals(PlayerAction.PREVIOUS, playerActionFor(Key.DirectionUp, isLive = true, isEpisodes = false))
        assertEquals(PlayerAction.NEXT, playerActionFor(Key.ChannelDown, isLive = false, isEpisodes = true))
        assertEquals(PlayerAction.SHOW_INFO, playerActionFor(Key.DirectionUp, isLive = false, isEpisodes = false))
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

    @Test
    fun digitsFromRemoteKeyboardAndNumPad() {
        assertEquals(1, digitOf(Key.One))
        assertEquals(9, digitOf(Key.NumPad9))
        assertEquals(null, digitOf(Key.DirectionUp))
    }

    @Test
    fun lastChannelAndSleepTimerKeys() {
        assertEquals(PlayerAction.LAST_CHANNEL, playerActionFor(Key.LastChannel, isLive = true, isEpisodes = false))
        assertEquals(PlayerAction.SHOW_INFO, playerActionFor(Key.LastChannel, isLive = false, isEpisodes = true))
        assertEquals(PlayerAction.SLEEP_TIMER, playerActionFor(Key.S, isLive = false, isEpisodes = false))
    }

    @Test
    fun menuAndTrackKeysOpenTheOptions() {
        assertEquals(PlayerAction.OPTIONS, playerActionFor(Key.Menu, isLive = false, isEpisodes = false))
        assertEquals(PlayerAction.OPTIONS, playerActionFor(Key.MediaAudioTrack, isLive = true, isEpisodes = false))
        assertEquals(PlayerAction.OPTIONS, playerActionFor(Key.Captions, isLive = false, isEpisodes = true))
        // Filme: ▼ öffnet die Optionen; bei Live TV und Folgen bleibt ▼ zum Weiterschalten.
        assertEquals(PlayerAction.OPTIONS, playerActionFor(Key.DirectionDown, isLive = false, isEpisodes = false))
        assertEquals(PlayerAction.NEXT, playerActionFor(Key.DirectionDown, isLive = true, isEpisodes = false))
        assertEquals(PlayerAction.SHOW_INFO, playerActionFor(Key.ChannelDown, isLive = false, isEpisodes = false))
        assertTrue(isOkKey(Key.DirectionCenter) && isOkKey(Key.Enter) && !isOkKey(Key.DirectionDown))
    }

    @Test
    fun trackLabelsAreReadableGerman() {
        assertEquals("Deutsch", languageName("de"))
        assertEquals("Englisch", languageName("eng"))
        assertNull(languageName("und"))
        assertNull(languageName(null))
        assertEquals("XX", languageName("xx"))

        assertEquals("Englisch · 5.1", trackLabel(Format.Builder().setLanguage("en").setChannelCount(6).build(), 0))
        assertEquals("Kommentar · Englisch", trackLabel(Format.Builder().setLanguage("en").setLabel("Kommentar").build(), 0))
        assertEquals("Spur 3", trackLabel(Format.Builder().setChannelCount(2).build(), 2))
    }

    @Test
    fun savedPreferencesBecomePlayerDefaults() {
        val base = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
        val off = preferredTrackParameters(base, "en", Storage.SUBTITLES_OFF)
        assertEquals(listOf("en"), off.preferredAudioLanguages)
        assertTrue(C.TRACK_TYPE_TEXT in off.disabledTrackTypes)

        val german = preferredTrackParameters(base, null, "de")
        assertEquals(listOf("de"), german.preferredTextLanguages)
        assertTrue(german.preferredAudioLanguages.isEmpty())
        assertTrue(C.TRACK_TYPE_TEXT !in german.disabledTrackTypes)
    }
}
