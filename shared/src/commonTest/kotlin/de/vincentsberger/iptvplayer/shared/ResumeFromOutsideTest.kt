package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Weiterschauen von außen – z. B. eine Kachel auf dem Startbildschirm von Google TV. */
class ResumeFromOutsideTest {

    @Test
    fun entryPlaysAtItsPositionWithHomeBehindIt() {
        val kv = InMemoryKeyValueStore()
        val movie = testAppState(kv = kv).run {
            section(Section.MOVIES).items.first().also { saveProgress(it, 60_000, 600_000) }
        }
        val app = testAppState(kv = kv)

        assertTrue(app.resumeFromHistory { it.item.url == movie.url })
        val player = app.screen as Screen.Player
        assertEquals(movie.url, player.items[player.index].url)
        assertEquals(60_000L, player.startPosition)
        assertNull(app.overview) // keine Übersicht dazwischen – man wollte direkt weiterschauen
        app.back()
        assertEquals(Screen.Home, app.screen)
    }

    @Test
    fun unknownEntryChangesNothing() {
        val app = testAppState()
        assertFalse(app.resumeFromHistory { false })
        assertEquals(Screen.Home, app.screen)
    }

    @Test
    fun homeScreenRowIsOnByDefaultAndRemembered() {
        val kv = InMemoryKeyValueStore()
        assertTrue(testAppState(kv = kv).showOnSystemHome)
        testAppState(kv = kv).showOnSystemHome = false
        assertFalse(testAppState(kv = kv).showOnSystemHome)
    }
}
