package de.vincentsberger.iptvplayer.shared.data

import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp.Companion.LOGIN_OK
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
        assertEquals("https://tv.example.com:8080/player_api.php?username=user&password=pass", http.requests.single())
        assertEquals("https://tv.example.com:8080/live/user/pass/1.ts", c.items(Section.LIVE).single().url)
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
        assertEquals("https://srv/player_api.php?username=max%20m%C3%BC&password=p%26w%3D1", http.requests.single())
    }

    // ---------- https zuerst ----------

    @Test
    fun withoutSchemeHttpsIsTriedFirstThenHttp() = runTest {
        val http = FakeHttp { url ->
            if (url.startsWith("https://")) throw TvException("Server nicht erreichbar.") else LOGIN_OK
        }
        val c = XtreamClient(account, http)
        c.login()
        assertEquals(listOf("https", "http"), http.requests.map { it.substringBefore("://") })
        assertEquals("http://tv.example.com:8080", c.base)
    }

    @Test
    fun explicitSchemeIsUsedAsIs() = runTest {
        val http = FakeHttp.xtream(mapOf("login" to LOGIN_OK))
        val c = XtreamClient(Account.Xtream("http://tv.example.com", "u", "p"), http)
        c.login()
        assertEquals(1, http.requests.size)
        assertEquals("http://tv.example.com", c.base)
    }

    @Test
    fun wrongPasswordOverHttpsDoesNotFallBackToHttp() = runTest {
        val http = FakeHttp.xtream(mapOf("login" to """{"user_info":{"auth":0}}"""))
        assertFailsWith<TvException> { XtreamClient(account, http).login() }
        assertEquals(1, http.requests.size, "Server hat geantwortet – kein zweiter Versuch über http")
    }

    @Test
    fun unreachableOnBothSchemesReportsTheError() = runTest {
        val http = FakeHttp { throw TvException("Server nicht erreichbar.") }
        val e = assertFailsWith<TvException> { XtreamClient(account, http).login() }
        assertEquals("Server nicht erreichbar.", e.message)
        assertEquals(2, http.requests.size)
    }

    @Test
    fun beforeLoginStoredAccountsKeepUsingHttp() {
        assertEquals("http://tv.example.com:8080", XtreamClient(account, FakeHttp { "" }).base)
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
    fun unsafeIdsAndExtensionsFromServerAreCleanedOrDropped() = runTest {
        val (c, _) = client(
            "get_vod_streams" to """[
                {"stream_id":"7/../../etc","name":"Pfad","category_id":"3","container_extension":"mp4?x=1"},
                {"stream_id":"../","name":"Nur Punkte","category_id":"3"}
            ]""",
        )
        val movie = c.items(Section.MOVIES).single()
        assertEquals("http://tv.example.com:8080/movie/user/pass/7etc.mp4", movie.url)
    }

    @Test
    fun seriesAreFoldersWithoutUrl() = runTest {
        val (c, _) = client("get_series" to """[{"series_id":11,"name":"Serie","cover":"http://img/s.jpg","category_id":"2"}]""")
        val s = c.items(Section.SERIES).single()
        assertEquals("11", s.id)
        assertTrue(s.isSeriesFolder)
    }

    @Test
    fun seriesListCarriesPlot() = runTest {
        val (c, _) = client(
            "get_series" to """[{"series_id":1,"name":"A","category_id":"2","plot":"  Berlin 1929. "},
                {"series_id":2,"name":"B","category_id":"2"},{"series_id":3,"name":"C","category_id":"2","plot":""}]""",
            "get_vod_streams" to """[{"stream_id":9,"name":"Film","category_id":"1","container_extension":"mp4","plot":"x"}]""",
        )
        assertEquals(listOf("Berlin 1929.", null, null), c.items(Section.SERIES).map { it.plot })
        assertNull(c.items(Section.MOVIES).single().plot, "Filme bewusst ohne – die Beschreibung kommt aus dem Detailabruf")
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

    // ---------- Folgen-Details (Netflix-Liste) ----------

    @Test
    fun episodesCarryPlotAndDuration() = runTest {
        val (c, _) = client("get_series_info" to """{"episodes":{"1":[
            {"id":"1","episode_num":1,"title":"Pilot","info":{"plot":" Gereon Rath kommt nach Berlin. ","duration_secs":"2712"}},
            {"id":"2","episode_num":2,"title":"Der Zug","info":{"plot":"","duration":"00:45:12"}},
            {"id":"3","episode_num":3,"title":"Ohne"}
        ]}}""")
        val season = c.seasons(series).single()
        val (pilot, zug, ohne) = season.episodes
        assertEquals(EpisodeDetail("Gereon Rath kommt nach Berlin.", 2712), season.details[pilot.url])
        assertEquals(EpisodeDetail(null, 2712), season.details[zug.url], "Dauer als hh:mm:ss, leere Handlung → null")
        assertEquals(null, season.details[ohne.url], "ohne Infos kein Eintrag")
    }

    // ---------- Film-Details ----------

    @Test
    fun vodInfoReadsDirectorCastCountryBackdropAndOriginalTitle() = runTest {
        val (c, _) = client()
        val info = c.parseVodInfo("""{"info":{"name":"Die Ankunft","o_name":"Arrival","director":"Denis Villeneuve",
            "cast":"Amy Adams, Jeremy Renner , ","country":"USA","backdrop_path":["https://img.example/bg.jpg"],"rating":8}}""")!!
        assertEquals("Denis Villeneuve", info.director)
        assertEquals(listOf("Amy Adams", "Jeremy Renner"), info.cast)
        assertEquals("USA", info.country)
        assertEquals("https://img.example/bg.jpg", info.backdrop)
        assertEquals("Arrival", info.originalTitle)
        assertEquals("8,0", info.ratingLabel, "Bewertung als Zahl statt Text")
    }

    @Test
    fun vodInfoFallsBackToActorsAndHidesOriginalTitleWhenEqual() = runTest {
        val (c, _) = client()
        val info = c.parseVodInfo("""{"info":{"name":"Arrival","o_name":"Arrival","actors":"Amy Adams","plot":"x","backdrop_path":[]}}""")!!
        assertEquals(listOf("Amy Adams"), info.cast)
        assertEquals(null, info.originalTitle)
        assertEquals(null, info.backdrop)
        assertEquals(null, info.director)
    }

    // ---------- EPG ----------

    @Test
    fun shortEpgDecodesBase64TitleAndExtractsTime() = runTest {
        val (c, http) = client(
            "get_short_epg" to """{"epg_listings":[
                {"title":"VGFnZXNzY2hhdQ==","start":"2026-11-16 20:00:00","end":"2026-11-16 20:15:00"}
            ]}""",
        )
        assertEquals(listOf(EpgEntry("Tagesschau", "20:00", "20:15", "2026-11-16 20:00:00", "2026-11-16 20:15:00")), c.shortEpg("1"))
        // 6 statt 2: Anbieter liefern oft schon beendete Sendungen mit – es muss danach noch „Jetzt“ und „Danach“ übrig sein.
        assertTrue(http.requests.single().endsWith("&stream_id=1&limit=10"))
    }

    @Test
    fun shortEpgIgnoresMalformedDateTimes() = runTest {
        val (c, _) = client("get_short_epg" to """{"epg_listings":[{"title":"QQ==","start":"20:00","end":""}]}""")
        assertEquals(listOf(EpgEntry("A", "20:00", "", "", "")), c.shortEpg("1"))
    }

    // ---------- Dashboard: Datum, Erwachsene, Abo ----------

    @Test
    fun moviesCarryAddedAndAdultFlag() = runTest {
        val (c, _) = client("get_vod_streams" to """[
            {"stream_id":1,"name":"A","category_id":"1","added":"1700000000","is_adult":"1"},
            {"stream_id":2,"name":"B","category_id":"1"}
        ]""")
        val (a, b) = c.items(Section.MOVIES)
        assertEquals(1_700_000_000_000L, a.addedAt)
        assertTrue(a.isAdult)
        assertNull(b.addedAt)
        assertEquals(false, b.isAdult)
    }

    @Test
    fun seriesUseLastModifiedAsAdded() = runTest {
        val (c, _) = client("get_series" to """[{"series_id":5,"name":"S","category_id":"2","last_modified":"1700000000"}]""")
        assertEquals(1_700_000_000_000L, c.items(Section.SERIES).single().addedAt)
    }

    @Test
    fun accountInfoFromLogin() = runTest {
        val (c, _) = client("login" to """{"user_info":{"auth":1,"status":"Active","exp_date":"1800000000","max_connections":"2","active_cons":"1"}}""")
        c.login()
        assertEquals(AccountInfo(1_800_000_000_000L, 2, 1), c.accountInfo)
        assertEquals(AccountInfo(1_800_000_000_000L, 2, 1), c.fetchAccountInfo())
    }

    @Test
    fun unlimitedAccountHasNoExpiry() = runTest {
        for (exp in listOf("null", "\"\"", "\"0\"")) {
            val (c, _) = client("login" to """{"user_info":{"auth":1,"status":"Active","exp_date":$exp}}""")
            c.login()
            assertNull(c.accountInfo?.expiresAt, exp)
        }
    }
}
