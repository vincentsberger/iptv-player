package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.input.key.Key
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tasten, die das Fenster abfängt – unabhängig davon, ob gerade ein Element den Tastaturfokus hat. */
class GlobalKeysTest {

    private val movie = Item("1", "Arrival", null, "sf", "http://x/1.mp4", Section.MOVIES)
    private val series = Item("2", "Babylon Berlin", null, "drama", "", Section.SERIES)

    private fun action(screen: Screen, key: Key, ctrl: Boolean = false, alt: Boolean = false, fullscreen: Boolean = false) =
        globalActionFor(screen, key, ctrl = ctrl, alt = alt, fullscreen = fullscreen)

    @Test
    fun escapeAndAltLeftLeaveDetailPages() {
        for (screen in listOf(Screen.Movie(movie), Screen.Series(series))) {
            assertEquals(GlobalAction.Back, action(screen, Key.Escape), "$screen: Esc")
            assertEquals(GlobalAction.Back, action(screen, Key.Backspace), "$screen: Rücktaste")
            assertEquals(GlobalAction.Back, action(screen, Key.DirectionLeft, alt = true), "$screen: Alt+←")
        }
    }

    @Test
    fun homeAndPlayerKeepTheirOwnBackKeys() {
        // Startseite hat Textfelder (Rücktaste!) und kein „zurück“; der Player regelt Esc selbst (Vollbild zuerst).
        assertNull(action(Screen.Home, Key.Escape))
        assertNull(action(Screen.Home, Key.Backspace))
        assertNull(action(Screen.Player(listOf(movie), 0), Key.Escape))
        assertNull(action(Screen.Movie(movie), Key.DirectionLeft), "← ohne Alt bleibt frei")
    }

    @Test
    fun escapeLeavesFullscreenEverywhere() {
        assertEquals(GlobalAction.ExitFullscreen, action(Screen.Player(listOf(movie), 0), Key.Escape, fullscreen = true))
        assertEquals(GlobalAction.ExitFullscreen, action(Screen.Movie(movie), Key.Escape, fullscreen = true))
    }

    @Test
    fun ctrlDigitsSelectTabsWithoutDownloads() {
        assertEquals(GlobalAction.SelectTab(Tab.HOME), action(Screen.Home, Key.One, ctrl = true))
        assertEquals(GlobalAction.SelectTab(Tab.SEARCH), action(Screen.Home, Key.Two, ctrl = true))
        assertEquals(GlobalAction.SelectTab(Tab.SETTINGS), action(Screen.Home, Key.NumPad7, ctrl = true))
        assertNull(action(Screen.Home, Key.Eight, ctrl = true), "Downloads gibt es am PC nicht")
        assertNull(action(Screen.Home, Key.One), "ohne Strg: Ziffer bleibt für Eingaben frei")
        assertNull(action(Screen.Player(listOf(movie), 0), Key.One, ctrl = true), "im Player keine Bereiche")
    }

    @Test
    fun ctrlFOpensSearch() {
        assertEquals(GlobalAction.FocusSearch, action(Screen.Home, Key.F, ctrl = true))
        assertNull(action(Screen.Home, Key.F), "F allein ist in Live TV „Favorit“")
        assertNull(action(Screen.Player(listOf(movie), 0), Key.F, ctrl = true))
    }
}
