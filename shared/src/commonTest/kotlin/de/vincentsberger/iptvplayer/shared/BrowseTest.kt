package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Repository
import de.vincentsberger.iptvplayer.shared.data.Season
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp.Companion.LOGIN_OK
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Kataloge „Filme“/„Serien“ (für alle Apps): Sonder-Kategorien, Inhalte je Kategorie, Startkategorie. */
class BrowseTest {
    private val storage = Storage(InMemoryKeyValueStore())
    private val http = FakeHttp.xtream(
        mapOf(
            "login" to LOGIN_OK,
            "get_live_categories" to "[]",
            "get_live_streams" to "[]",
            "get_vod_categories" to """[{"category_id":"3","category_name":"Kino"},{"category_id":"4","category_name":"Doku"}]""",
            "get_vod_streams" to """[
                {"stream_id":7,"name":"Arrival","category_id":"3"},
                {"stream_id":8,"name":"Planet Erde","category_id":"4"}
            ]""",
            "get_series_categories" to "[]",
            "get_series" to "[]",
        ),
    )

    private suspend fun TestScope.loggedIn(): AppState {
        val app = AppState(storage, scope = this, clock = { 1_000L }, newRepository = { a, c -> Repository(a, http, c, Dispatchers.Unconfined) })
        app.login(Account.Xtream("srv", "user", "pass"))
        advanceUntilIdle()
        return app
    }

    @Test
    fun specialCategoriesComeFirstThenTheProvidersOwn() = runTest {
        val app = loggedIn()
        assertEquals(
            listOf(AppState.BROWSE_FAV to "Favoriten", AppState.BROWSE_ALL to "Alle Filme", "3" to "Kino", "4" to "Doku"),
            app.browseCategories(Section.MOVIES).map { it.id to it.name },
        )
        val arrival = app.section(Section.MOVIES).items.first { it.name == "Arrival" }
        app.saveProgress(arrival, 120_000, 6_000_000)
        assertEquals(AppState.BROWSE_CONTINUE to "Weiterschauen", app.browseCategories(Section.MOVIES).first().let { it.id to it.name })
    }

    @Test
    fun itemsPerCategory() = runTest {
        val app = loggedIn()
        val arrival = app.section(Section.MOVIES).items.first { it.name == "Arrival" }
        assertEquals(listOf("Arrival", "Planet Erde"), app.browseItems(Section.MOVIES, AppState.BROWSE_ALL).map { it.name })
        assertEquals(listOf("Planet Erde"), app.browseItems(Section.MOVIES, "4").map { it.name })
        assertEquals(emptyList(), app.browseItems(Section.MOVIES, AppState.BROWSE_FAV))
        app.toggleFavorite(arrival)
        assertEquals(listOf("Arrival"), app.browseItems(Section.MOVIES, AppState.BROWSE_FAV).map { it.name })
        app.saveProgress(arrival, 120_000, 6_000_000)
        assertEquals(listOf("Arrival"), app.browseItems(Section.MOVIES, AppState.BROWSE_CONTINUE).map { it.name })
        assertEquals(emptyList(), app.browseItems(Section.MOVIES, "gibt-es-nicht"))
    }

    @Test
    fun initialCategoryIsTheRememberedOneIfItStillExists() = runTest {
        val app = loggedIn()
        assertEquals("3", app.initialBrowseCategory(Section.MOVIES), "ohne Verlauf: erste Kategorie des Anbieters")
        app.rememberBrowseCategory(Section.MOVIES, "4")
        assertEquals("4", app.initialBrowseCategory(Section.MOVIES))
        app.rememberBrowseCategory(Section.MOVIES, "weg")
        val arrival = app.section(Section.MOVIES).items.first { it.name == "Arrival" }
        app.saveProgress(arrival, 120_000, 6_000_000)
        assertEquals(AppState.BROWSE_CONTINUE, app.initialBrowseCategory(Section.MOVIES), "gemerkte gibt es nicht mehr → Weiterschauen")
    }

    @Test
    fun episodePositionFindsSeasonAndEpisode() {
        fun ep(n: Int) = Item("$n", "Folge $n", null, "11", "http://srv/series/$n.mkv", Section.SERIES)
        val seasons = listOf(Season("1", listOf(ep(1), ep(2))), Season("2", listOf(ep(3), ep(4), ep(5))))
        assertEquals(EpisodePosition(1, 1), episodePosition(seasons, "http://srv/series/4.mkv"))
        assertEquals(EpisodePosition(0, 0), episodePosition(seasons, "http://srv/series/1.mkv"))
        assertNull(episodePosition(seasons, "http://srv/series/99.mkv"))
        assertNull(episodePosition(seasons, null))
    }
}
