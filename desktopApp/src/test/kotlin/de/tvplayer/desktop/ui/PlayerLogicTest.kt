package de.tvplayer.desktop.ui

import androidx.compose.ui.input.key.Key
import de.tvplayer.shared.data.EpgEntry
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
}
