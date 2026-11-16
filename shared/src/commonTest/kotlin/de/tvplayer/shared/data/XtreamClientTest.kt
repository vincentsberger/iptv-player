package de.tvplayer.shared.data

import de.tvplayer.shared.testing.FakeHttp
import de.tvplayer.shared.testing.FakeHttp.Companion.LOGIN_OK
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XtreamClientTest {

    private val account = Account.Xtream("tv.example.com:8080", "user", "pass")

    private fun client(vararg responses: Pair<String, String>) =
        FakeHttp.xtream(mapOf(*responses)).let { XtreamClient(account, it) to it }

    // ---------- normalize ----------

    @Test
    fun normalizeAddsSchemeAndRemovesTrailingParts() {
        assertEquals("http://tv.example.com:8080", XtreamClient.normalize(" tv.example.com:8080/ "))
        assertEquals("https://tv.example.com", XtreamClient.normalize("https://tv.example.com/player_api.php"))
        assertEquals("http://tv.example.com", XtreamClient.normalize("http://tv.example.com/get.php"))
    }

    // ---------- login ----------

    @Test
    fun loginSucceedsAndUsesTsByDefault() = runTest {
        val (c, http) = client("login" to LOGIN_OK, "get_live_streams" to """[{"stream_id":1,"name":"ARD","category_id":"1"}]""")
        c.login()
        assertEquals("http://tv.example.com:8080/player_api.php?username=user&password=pass", http.requests.single())
        assertEquals("http://tv.example.com:8080/live/user/pass/1.ts", c.items(Section.LIVE).single().url)
    }

    @Test
    fun loginPrefersHlsWhenServerAllowsIt() = runTest {
        val (c, _) = client(
            "login" to """{"user_info":{"auth":"1","allowed_output_formats":["ts","m3u8"]}}""",
            "get_live_streams" to """[{"stream_id":1,"name":"ARD","category_id":"1"}]""",
        )
        c.login()
        assertTrue(c.items(Section.LIVE).single().url.endsWith("/1.m3u8"))
    }

    @Test
    fun loginRejectsWrongCredentials() = runTest {
        val (c, _) = client("login" to """{"user_info":{"auth":0}}""")
        val e = assertFailsWith<TvException> { c.login() }
        assertEquals("Benutzername oder Passwort falsch", e.message)
    }

    @Test
    fun loginRejectsInactiveAccount() = runTest {
        val (c, _) = client("login" to """{"user_info":{"auth":1,"status":"Expired"}}""")
        val e = assertFailsWith<TvException> { c.login() }
        assertEquals("Dein Konto ist nicht aktiv (Status: Expired)", e.message)
    }

    @Test
    fun loginRejectsNonJsonAnswer() = runTest {
        val (c, _) = client("login" to "<html>404</html>")
        val e = assertFailsWith<TvException> { c.login() }
        assertEquals("Unerwartete Antwort. Stimmt die Server-Adresse?", e.message)
    }

    @Test
    fun credentialsAreUrlEncoded() = runTest {
        val http = FakeHttp.xtream(mapOf("login" to LOGIN_OK))
        XtreamClient(Account.Xtream("srv", " max mü ", "p&w=1"), http).login()
        assertEquals("http://srv/player_api.php?username=max%20m%C3%BC&password=p%26w%3D1", http.requests.single())
    }

    // ---------- Listen ----------

    @Test
    fun categoriesUseTheRightActionPerSection() = runTest {
        val json = """[{"category_id":5,"category_name":"Doku"}]"""
        val (c, _) = client("get_live_categories" to json, "get_vod_categories" to "[]", "get_series_categories" to "[]")
        assertEquals(listOf(Category("5", "Doku")), c.categories(Section.LIVE))
        assertEquals(emptyList(), c.categories(Section.MOVIES))
        assertEquals(emptyList(), c.categories(Section.SERIES))
    }

    @Test
    fun moviesUseContainerExtensionWithMp4Fallback() = runTest {
        val (c, _) = client(
            "get_vod_streams" to """[
                {"stream_id":7,"name":"Film A","stream_icon":"http://img/a.jpg","category_id":"3","container_extension":"mkv"},
                {"stream_id":8,"name":"Film B","stream_icon":null,"category_id":"3"}
            ]""",
        )
        val (a, b) = c.items(Section.MOVIES)
        assertEquals("http://tv.example.com:8080/movie/user/pass/7.mkv", a.url)
        assertEquals("http://img/a.jpg", a.logo)
        assertEquals("http://tv.example.com:8080/movie/user/pass/8.mp4", b.url)
        assertNull(b.logo)
    }

    @Test
    fun seriesAreFoldersWithoutUrl() = runTest {
        val (c, _) = client("get_series" to """[{"series_id":11,"name":"Serie","cover":"http://img/s.jpg","category_id":"2"}]""")
        val s = c.items(Section.SERIES).single()
        assertEquals("11", s.id)
        assertTrue(s.isSeriesFolder)
    }

    @Test
    fun brokenListAnswerGivesEmptyList() = runTest {
        val (c, _) = client("get_live_streams" to "kaputt")
        assertEquals(emptyList(), c.items(Section.LIVE))
    }

    // ---------- Staffeln ----------

    private val series = Item("11", "Serie", "http://img/cover.jpg", "2", "", Section.SERIES)

    @Test
    fun seasonsFromObjectAreSortedNumericallyAndEmptyOnesDropped() = runTest {
        val (c, http) = client(
            "get_series_info" to """{"episodes":{
                "10":[{"id":"100","episode_num":1,"title":"Finale"}],
                "2":[{"id":"20","episode_num":3,"title":"","container_extension":"mkv","info":{"movie_image":"http://img/ep.jpg"}}],
                "3":[]
            }}""",
        )
        val seasons = c.seasons(series)
        assertTrue(http.requests.single().endsWith("&action=get_series_info&series_id=11"))
        assertEquals(listOf("2", "10"), seasons.map { it.number })
        val ep = seasons[0].episodes.single()
        assertEquals("Folge 3", ep.name)
        assertEquals("http://img/ep.jpg", ep.logo)
        assertEquals("http://tv.example.com:8080/series/user/pass/20.mkv", ep.url)
        assertEquals("11", ep.categoryId, "Folgen merken sich ihre Serie über categoryId")
        assertEquals("http://img/cover.jpg", seasons[1].episodes.single().logo, "ohne Bild: Cover der Serie")
    }

    @Test
    fun seasonsFromArrayAreNumberedFromOne() = runTest {
        val (c, _) = client("get_series_info" to """{"episodes":[[{"id":"1","title":"A"}],[{"id":"2","title":"B"}]]}""")
        assertEquals(listOf("1", "2"), c.seasons(series).map { it.number })
    }

    @Test
    fun seasonsWithoutEpisodesGiveEmptyList() = runTest {
        val (c, _) = client("get_series_info" to """{"info":{}}""")
        assertEquals(emptyList(), c.seasons(series))
    }

    // ---------- EPG ----------

    @Test
    fun shortEpgDecodesBase64TitleAndExtractsTime() = runTest {
        val (c, http) = client(
            "get_short_epg" to """{"epg_listings":[
                {"title":"VGFnZXNzY2hhdQ==","start":"2026-11-16 20:00:00","end":"2026-11-16 20:15:00"}
            ]}""",
        )
        assertEquals(listOf(EpgEntry("Tagesschau", "20:00", "20:15")), c.shortEpg("1"))
        assertTrue(http.requests.single().endsWith("&stream_id=1&limit=2"))
    }
}
