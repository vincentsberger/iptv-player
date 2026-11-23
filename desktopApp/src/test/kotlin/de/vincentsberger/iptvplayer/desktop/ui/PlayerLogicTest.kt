package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.input.key.Key
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.desktop.player.languageName
import de.vincentsberger.iptvplayer.desktop.player.trackLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Reine Logik des Players und der Programmanzeige – ohne VLC und ohne Fenster. */
class PlayerLogicTest {

    private fun action(key: Key, live: Boolean = false, canSwitch: Boolean = false, fullscreen: Boolean = false) =
        playerActionFor(key, live, canSwitch, fullscreen)

    @Test
    fun movieKeysControlPlayback() {
        assertEquals(PlayerAction.TogglePause, action(Key.Spacebar))
        assertEquals(PlayerAction.TogglePause, action(Key.Enter))
        assertEquals(PlayerAction.SeekBack, action(Key.DirectionLeft))
        assertEquals(PlayerAction.SeekForward, action(Key.DirectionRight))
        assertEquals(PlayerAction.Ignore, action(Key.DirectionUp), "Film ohne Liste: nicht umschalten")
    }

    @Test
    fun liveTvCannotPauseOrSeekButSwitchesChannels() {
        assertEquals(PlayerAction.Ignore, action(Key.Spacebar, live = true))
        assertEquals(PlayerAction.Ignore, action(Key.DirectionLeft, live = true))
        assertEquals(PlayerAction.Previous, action(Key.DirectionUp, live = true, canSwitch = true))
        assertEquals(PlayerAction.Next, action(Key.PageDown, live = true, canSwitch = true))
    }

    @Test
    fun escapeLeavesFullscreenBeforeLeavingThePlayer() {
        assertEquals(PlayerAction.ToggleFullscreen, action(Key.Escape, fullscreen = true))
        assertEquals(PlayerAction.Back, action(Key.Escape))
        assertEquals(PlayerAction.Back, action(Key.Backspace))
    }

    @Test
    fun otherShortcutsAndUnknownKeys() {
        assertEquals(PlayerAction.ToggleFullscreen, action(Key.F))
        assertEquals(PlayerAction.ToggleMute, action(Key.M))
        assertNull(action(Key.A), "fremde Tasten bleiben für andere Elemente frei")
    }

    @Test
    fun wrapIndexWrapsAroundInBothDirections() {
        assertEquals(0, wrapIndex(3, 3))
        assertEquals(2, wrapIndex(-1, 3))
        assertEquals(1, wrapIndex(1, 3))
    }

    @Test
    fun formatTimeUsesHoursOnlyWhenNeeded() {
        assertEquals("00:00", formatTime(-5))
        assertEquals("01:05", formatTime(65_000))
        assertEquals("1:02:03", formatTime(3_723_000))
    }

    @Test
    fun epgProgressWithinAndAcrossMidnight() {
        val evening = EpgEntry("Tagesschau", "20:00", "20:15")
        assertEquals(0f, epgProgress(evening, nowMinute = 19 * 60 + 59), "vor Beginn")
        assertEquals(0.6f, epgProgress(evening, nowMinute = 20 * 60 + 9), 0.001f)
        assertEquals(1f, epgProgress(evening, nowMinute = 21 * 60), "danach")

        val night = EpgEntry("Film", "23:30", "00:30")
        assertEquals(0.5f, epgProgress(night, nowMinute = 0), 0.001f)
        assertEquals(0f, epgProgress(EpgEntry("?", "", ""), nowMinute = 0), "ohne Zeiten")
    }

    @Test
    fun digitsComeFromMainRowAndNumPad() {
        assertEquals(1, digitOf(Key.One))
        assertEquals(7, digitOf(Key.NumPad7))
        assertEquals(0, digitOf(Key.Zero))
        assertNull(digitOf(Key.A))
    }

    @Test
    fun lastChannelOnlyForLiveAndSleepTimerEverywhere() {
        assertEquals(PlayerAction.LastChannel, playerActionFor(Key.L, isLive = true, canSwitch = true, fullscreen = false))
        assertEquals(PlayerAction.Ignore, playerActionFor(Key.L, isLive = false, canSwitch = true, fullscreen = false))
        assertEquals(PlayerAction.SleepTimer, playerActionFor(Key.S, isLive = false, canSwitch = false, fullscreen = false))
    }

    @Test
    fun tKeyOpensTrackMenuAndLabelsAreReadableGerman() {
        assertEquals(PlayerAction.Tracks, action(Key.T))
        assertEquals("Deutsch", languageName("deu"))
        assertEquals("Deutsch", languageName("ger"))
        assertEquals("XX", languageName("xx"))
        assertEquals("Englisch", languageName("en"))
        assertNull(languageName("und"))
        assertEquals("Englisch · 5.1", trackLabel("eng", null, 6, 0))
        assertEquals("Kommentar · Englisch", trackLabel("eng", "Kommentar", 2, 0))
        assertEquals("Spur 2", trackLabel(null, " ", 2, 1))
    }

    @Test
    fun liveZappingShowsZapCardNotTheBar() {
        assertEquals(false, showsBarOnKey(PlayerAction.Next, isDigit = false, isLive = true))
        assertEquals(false, showsBarOnKey(PlayerAction.Previous, isDigit = false, isLive = true))
        assertEquals(false, showsBarOnKey(PlayerAction.LastChannel, isDigit = false, isLive = true))
        assertEquals(false, showsBarOnKey(null, isDigit = true, isLive = true))
        assertEquals(true, showsBarOnKey(PlayerAction.TogglePause, isDigit = false, isLive = true))
        assertEquals(true, showsBarOnKey(PlayerAction.Next, isDigit = false, isLive = false), "Folgen: Leiste wie bisher")
        assertEquals(false, barVisibleAtStart(isLive = true))
        assertEquals(true, barVisibleAtStart(isLive = false))
    }

    @Test
    fun pausingAMovieShowsPauseInfoNotTheBar() {
        assertEquals(false, showsBarOnKey(PlayerAction.TogglePause, isDigit = false, isLive = false))
        assertEquals(true, showsBarOnKey(PlayerAction.SeekForward, isDigit = false, isLive = false))
    }
}
