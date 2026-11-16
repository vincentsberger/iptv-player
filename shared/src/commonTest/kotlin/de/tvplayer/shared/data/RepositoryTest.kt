package de.tvplayer.shared.data

import de.tvplayer.shared.testing.FakeHttp
import de.tvplayer.shared.testing.FakeHttp.Companion.LOGIN_OK
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RepositoryTest {

    private val playlist = """
        #EXTM3U
        #EXTINF:-1 group-title="News",ARD
        http://srv/live/1.ts
        #EXTINF:-1 group-title="Kino",Film
        http://srv/movie/2.mp4
        #EXTINF:-1 group-title="News",ZDF
        http://srv/live/3.ts
    """.trimIndent()

    @Test
    fun m3uIsLoadedOnceAndSplitIntoSections() = runTest {
        val http = FakeHttp { playlist }
        val repo = Repository(Account.M3u(" http://srv/list.m3u "), http)

        val (liveCats, live) = repo.load(Section.LIVE)
        val (movieCats, movies) = repo.load(Section.MOVIES)
        val (seriesCats, series) = repo.load(Section.SERIES)

        assertEquals(listOf("http://srv/list.m3u"), http.requests, "nur eine Anfrage, URL getrimmt")
        assertEquals(listOf("ARD", "ZDF"), live.map { it.name })
        assertEquals(listOf("News"), liveCats.map { it.name })
        assertEquals(listOf("Film"), movies.map { it.name })
        assertEquals(listOf("Kino"), movieCats.map { it.name })
        assertEquals(emptyList(), series)
        assertEquals(emptyList(), seriesCats)
    }

    @Test
    fun emptyM3uFailsWithReadableMessage() = runTest {
        val repo = Repository(Account.M3u("http://srv/list.m3u"), FakeHttp { "#EXTM3U\n" })
        val e = assertFailsWith<TvException> { repo.login() }
        assertEquals("Die Playlist ist leer oder ungültig", e.message)
    }

    @Test
    fun loginRemembersTheReachedServerAddress() = runTest {
        val repo = Repository(Account.Xtream("srv", "u", "p"), FakeHttp.xtream(mapOf("login" to LOGIN_OK)))
        repo.login()
        assertEquals(Account.Xtream("https://srv", "u", "p"), repo.account)
    }

    @Test
    fun xtreamSectionsAreCached() = runTest {
        val http = FakeHttp.xtream(
            mapOf(
                "get_vod_categories" to """[{"category_id":"1","category_name":"Kino"}]""",
                "get_vod_streams" to """[{"stream_id":5,"name":"Film","category_id":"1"}]""",
            )
        )
        val repo = Repository(Account.Xtream("srv", "u", "p"), http)
        val first = repo.load(Section.MOVIES)
        val second = repo.load(Section.MOVIES)
        assertEquals(first, second)
        assertEquals(2, http.requests.size, "Kategorien + Filme, danach aus dem Cache")
    }

    @Test
    fun seasonsAreCachedPerSeries() = runTest {
        val http = FakeHttp.xtream(mapOf("get_series_info" to """{"episodes":{"1":[{"id":"9","title":"A"}]}}"""))
        val repo = Repository(Account.Xtream("srv", "u", "p"), http)
        val series = Item("11", "Serie", null, "2", "", Section.SERIES)
        repo.seasons(series)
        repo.seasons(series)
        assertEquals(1, http.requests.size)
    }

    @Test
    fun m3uHasNoSeasonsAndNoEpg() = runTest {
        val http = FakeHttp { error("darf nicht aufgerufen werden") }
        val repo = Repository(Account.M3u("http://srv/list.m3u"), http)
        val live = Item("1", "ARD", null, "c", "http://srv/live/1.ts", Section.LIVE)
        assertEquals(emptyList(), repo.seasons(live))
        assertEquals(emptyList(), repo.nowPlaying(live))
    }

    @Test
    fun nowPlayingOnlyAsksForLiveItemsAndSwallowsErrors() = runTest {
        val http = FakeHttp.xtream(mapOf("login" to LOGIN_OK)) // EPG-Anfrage löst einen Fehler aus
        val repo = Repository(Account.Xtream("srv", "u", "p"), http)
        val movie = Item("5", "Film", null, "c", "http://srv/movie/5.mp4", Section.MOVIES)
        val live = Item("1", "ARD", null, "c", "http://srv/live/1.ts", Section.LIVE)

        assertEquals(emptyList(), repo.nowPlaying(movie))
        assertEquals(0, http.requests.size)
        assertEquals(emptyList(), repo.nowPlaying(live))
        assertEquals(1, http.requests.size)
    }
}
