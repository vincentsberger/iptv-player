package de.vincentsberger.iptvplayer.shared.data

import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Infos zu Film/Serie für die Pause-Info: Abruf, Parsen und deutsche Formatierung. */
class MediaInfoTest {

    private val account = Account.Xtream("tv.example", "u", "p")
    private val movie = Item("7", "Dune: Part Two", null, "10", "http://tv.example/movie/u/p/7.mkv", Section.MOVIES)

    private fun client(vararg r: Pair<String, String>) = FakeHttp.xtream(mapOf(*r)).let { XtreamClient(account, it) to it }

    @Test
    fun vodInfoIsParsed() = runTest {
        val (c, _) = client("get_vod_info" to """{"info":{"releasedate":"2024-02-27","rating":"8.3","duration_secs":9960,
            "age":"12","genre":"Science Fiction, Abenteuer, Drama, Action","plot":"Paul Atreides vereint sich …"}}""")
        val info = c.vodInfo(movie)!!
        assertEquals("2024", info.year)
        assertEquals("8,3", info.ratingLabel)
        assertEquals("2 h 46 min", info.durationLabel)
        assertEquals("FSK 12", info.fskLabel)
        assertEquals("Science Fiction, Abenteuer, Drama", info.genresLabel)
        assertEquals("Paul Atreides vereint sich …", info.plot)
    }

    @Test
    fun emptyOrMissingInfoIsNull() = runTest {
        assertNull(client("get_vod_info" to """{"info":[]}""").first.vodInfo(movie))
        assertNull(client("get_vod_info" to """{"movie_data":{}}""").first.vodInfo(movie))
    }

    @Test
    fun ratingFormats() {
        fun r(v: String) = MediaInfo(null, v.toDoubleOrNull(), null, null, emptyList(), null).ratingLabel
        assertEquals("8,3", r("8.3"))
        assertEquals("7,0", r("7"))
        assertNull(r("0"))
        assertNull(r(""))
    }

    @Test
    fun slashSeparatedGenresMaxThree() = runTest {
        val (c, _) = client("get_vod_info" to """{"info":{"genre":"Action / Abenteuer/Komödie / Drama"}}""")
        assertEquals("Action, Abenteuer, Komödie", c.vodInfo(movie)!!.genresLabel)
    }

    @Test
    fun durationLabel() {
        assertEquals("2 h 46 min", MediaInfo(null, null, 9960, null, emptyList(), null).durationLabel)
        assertEquals("46 min", MediaInfo(null, null, 2760, null, emptyList(), null).durationLabel)
        assertNull(MediaInfo(null, null, 0, null, emptyList(), null).durationLabel)
    }

    @Test
    fun fskOnlyForNumbers() = runTest {
        assertEquals("FSK 16", client("get_vod_info" to """{"info":{"age":"16+"}}""").first.vodInfo(movie)!!.fskLabel)
        assertNull(client("get_vod_info" to """{"info":{"age":"PG","plot":"x"}}""").first.vodInfo(movie)!!.fskLabel)
    }

    @Test
    fun seriesInfoFromSeasonsCall() = runTest {
        val http = FakeHttp.xtream(mapOf("get_series_info" to """{"info":{"releaseDate":"2017-10-13","rating":"8.4","genre":"Krimi","plot":"Berlin 1929"},
            "episodes":{"1":[{"id":"100","episode_num":1,"title":"Pilot"}]}}"""))
        val repo = Repository(account, http, work = kotlinx.coroutines.Dispatchers.Unconfined)
        val series = Item("200", "Babylon Berlin", null, "20", "", Section.SERIES)
        repo.seasons(series)
        val info = repo.seriesInfo(series)!!
        assertEquals("2017", info.year)
        assertEquals("Berlin 1929", info.plot)
        assertEquals(1, http.requests.count { FakeHttp.actionOf(it) == "get_series_info" }, "kein zweiter Abruf")
    }

    @Test
    fun infoWithoutUsableFieldsIsNull() = runTest {
        assertNull(client("get_vod_info" to """{"info":{"rating":"0","genre":"","plot":" "}}""").first.vodInfo(movie))
    }

    @Test
    fun seriesInfoWithoutOpenSeasonsKeepsEpisodeImages() = runTest {
        val http = FakeHttp.xtream(mapOf("get_series_info" to """{"info":{"plot":"Berlin 1929"},"episodes":{"1":[{"id":"100","title":"Pilot"}]}}"""))
        val repo = Repository(account, http, work = kotlinx.coroutines.Dispatchers.Unconfined)
        val series = Item("200", "Babylon Berlin", "http://img/bb.jpg", "20", "", Section.SERIES)
        assertEquals("Berlin 1929", repo.seriesInfo(series)?.plot)
        assertEquals("http://img/bb.jpg", repo.seasons(series).single().episodes.single().logo, "Ersatzbild der Serie bleibt")
        assertEquals(1, http.requests.size, "Staffeln aus dem Info-Abruf wiederverwendet")
    }
}
