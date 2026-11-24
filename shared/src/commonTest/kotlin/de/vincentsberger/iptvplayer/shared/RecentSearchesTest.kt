package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentSearchesTest {
    @Test
    fun newestFirstNoDuplicatesLimitedAndSaved() {
        val kv = InMemoryKeyValueStore()
        val app = testAppState(kv = kv)
        app.rememberSearch("tatort")
        app.rememberSearch("x") // zu kurz
        app.query = "Dune "; app.rememberSearch()
        app.rememberSearch("TATORT")
        assertEquals(listOf("TATORT", "Dune"), app.recentSearches)
        repeat(10) { app.rememberSearch("Begriff $it") }
        assertEquals(AppState.MAX_RECENT_SEARCHES, app.recentSearches.size)
        assertEquals("Begriff 9", app.recentSearches.first())
        assertEquals(app.recentSearches, testAppState(kv = kv).recentSearches, "überlebt den Neustart")
        app.clearRecentSearches()
        assertEquals(emptyList(), app.recentSearches)
    }
}
