package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRowsTest {
    @Test
    fun moveHideAndKeepAfterRestart() {
        val kv = InMemoryKeyValueStore()
        val app = testAppState(kv = kv)
        assertEquals(HomeRow.entries.toList(), app.homeRows)
        app.moveHomeRow(HomeRow.RECENT_CHANNELS, up = true)
        app.moveHomeRow(HomeRow.RECENT_CHANNELS, up = true)
        app.moveHomeRow(HomeRow.RECENT_CHANNELS, up = true)
        app.moveHomeRow(HomeRow.RECENT_CHANNELS, up = true) // schon ganz oben: nichts
        app.toggleHomeRow(HomeRow.OFFLINE)
        val expected = listOf(HomeRow.RECENT_CHANNELS, HomeRow.CONTINUE, HomeRow.NEW_MOVIES, HomeRow.NEW_EPISODES,
            HomeRow.FAVORITE_LIVE, HomeRow.FAVORITE_MOVIES, HomeRow.FAVORITE_SERIES)
        assertEquals(expected, app.homeRows)
        assertEquals(expected, testAppState(kv = kv).homeRows, "überlebt den Neustart")
        app.resetHomeRows()
        assertEquals(HomeRow.entries.toList(), app.homeRows)
    }

    @Test
    fun unknownNamesDropNewRowsAppend() {
        assertEquals(listOf(HomeRow.OFFLINE) + HomeRow.entries.filterNot { it == HomeRow.OFFLINE }, orderedHomeRows(listOf("OFFLINE", "WEG")))
    }
}
