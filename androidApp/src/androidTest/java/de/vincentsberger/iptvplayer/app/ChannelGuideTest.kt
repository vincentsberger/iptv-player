package de.vincentsberger.iptvplayer.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.AppTheme
import de.vincentsberger.iptvplayer.app.ui.ChannelGuide
import de.vincentsberger.iptvplayer.app.ui.TvChrome
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

/** Senderliste im Player: Länderkürzel nur, wenn die Liste Sender mehrerer Länder mischt – wie in Live TV. */
@OptIn(ExperimentalTestApi::class, ExperimentalEncodingApi::class)
@RunWith(AndroidJUnit4::class)
class ChannelGuideTest {

    @get:Rule
    val rule = createComposeRule()

    private val liveStreams = """[
        {"stream_id":1,"name":"DE: Das Erste","category_id":"1"},
        {"stream_id":2,"name":"DE: ZDF","category_id":"1"},
        {"stream_id":3,"name":"AT: ORF 1","category_id":"2"}
    ]"""

    /** Gemischte Kategorie: DE und AT in „1“. */
    private val mixedStreams = """[
        {"stream_id":1,"name":"DE: Das Erste","category_id":"1"},
        {"stream_id":3,"name":"AT: ORF 1","category_id":"1"}
    ]"""

    // Die Liste startet in der Kategorie des laufenden Senders (hier „1“).
    private fun showGuide(streams: String = liveStreams, startCategory: String = "1") {
        val vm = testAppState(http = SampleData.http(mapOf("get_live_streams" to streams)))
        val playing = vm.section(Section.LIVE).items.first()
        rule.setContent { AppTheme { TvChrome { ChannelGuide(vm, playing, startCategory, onPick = { _, _ -> }, onCatchUp = { _, _ -> }) } } }
        rule.waitForIdle()
    }

    @Test
    fun singleCountryCategoryShowsNoCountryTag() {
        showGuide()
        rule.onAllNodesWithText("Das Erste").assertCountEquals(1)
        rule.onAllNodesWithText("DE").assertCountEquals(0)
    }

    @Test
    fun mixedListKeepsCountryTags() {
        showGuide(streams = mixedStreams)
        rule.onAllNodesWithText("AT").assertCountEquals(1)
    }

    // ---------- Raster (Spec 2026-11-28-epg-grid-design) ----------

    private val gridStreams = """[
        {"stream_id":1,"name":"DE: Das Erste","category_id":"1","epg_channel_id":"daserste.de"},
        {"stream_id":2,"name":"DE: ZDF","category_id":"1","epg_channel_id":"zdf.de"},
        {"stream_id":3,"name":"DE: Drei","category_id":"1","epg_channel_id":"drei.de"}
    ]"""

    private fun listing(title: String, from: String, to: String) =
        """{"title":"${Base64.encode(title.encodeToByteArray())}","start":"2026-11-28 $from:00","end":"2026-11-28 $to:00"}"""

    private val programmes = mapOf(
        "1" to listOf(listing("Tagesschau", "20:00", "20:15"), listing("Krimi", "20:15", "21:45")),
        "2" to listOf(listing("Show", "19:30", "20:45"), listing("Film", "20:45", "22:30")),
        "3" to listOf(listing("Alt", "19:30", "20:05"), listing("Neu", "20:05", "21:00")),
    )

    private var picked: Int? = null

    /** Raster um 20:10, der laufende Sender ist der erste der Liste („Das Erste“). */
    private fun showGrid(streams: String = gridStreams, categories: String? = null, startCategory: String = "1") {
        val http = FakeHttp { url ->
            when (FakeHttp.actionOf(url)) {
                "get_live_streams" -> streams
                "get_live_categories" -> categories ?: SampleData.respond(url)
                "get_simple_data_table" -> {
                    val listings = programmes[url.substringAfter("stream_id=").substringBefore('&')].orEmpty()
                    """{"epg_listings":[${listings.joinToString(",")}]}"""
                }
                else -> SampleData.respond(url)
            }
        }
        val vm = testAppState(http = http, localTime = { "2026-11-28 20:10:00" })
        val playing = vm.section(Section.LIVE).items.first()
        rule.setContent { AppTheme { TvChrome { ChannelGuide(vm, playing, startCategory, onPick = { _, i -> picked = i }, onCatchUp = { _, _ -> }) } } }
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onNode(isFocused()).performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test
    fun focusStartsOnRunningShowOfPlayingChannel() {
        showGrid()
        rule.onNodeWithTag("guide:focused").assertTextContains("Tagesschau")
    }

    @Test
    fun downKeepsTimePosition() {
        showGrid()
        press(Key.DirectionRight) // „Krimi“ ab 20:15
        press(Key.DirectionDown)
        rule.onNodeWithTag("guide:focused").assertTextContains("Show")
    }

    @Test
    fun okOnRunningShowSwitches() {
        showGrid()
        press(Key.DirectionCenter)
        assertEquals(0, picked ?: -1)
    }

    @Test
    fun okOnFutureShowOpensInfo() {
        showGrid()
        press(Key.DirectionDown) // „Show“
        press(Key.DirectionRight) // „Film“ ab 20:45 – noch nicht gelaufen
        press(Key.DirectionCenter)
        rule.onNodeWithTag("guide:info").assertIsDisplayed().assertTextContains("Film", substring = true)
        assertEquals(null, picked)
    }

    @Test
    fun leftAtLimitLeavesGrid() {
        showGrid()
        press(Key.DirectionLeft)
        rule.onNodeWithTag("guide:category:1").assertIsFocused()
    }

    @Test
    fun downFromRunningShowStaysInThePresent() {
        showGrid()
        press(Key.DirectionDown) // „Show“ läuft noch
        press(Key.DirectionDown) // um 20:10 läuft „Neu“, nicht das schon beendete „Alt“
        rule.onNodeWithTag("guide:focused").assertTextContains("Neu")
    }

    @Test
    fun leftAtLimitReturnsToPlayingCategoryEvenWhenScrolledAway() {
        val categories = (1..30).joinToString(",", "[", "]") { """{"category_id":"$it","category_name":"Kategorie $it"}""" }
        val streams = """[{"stream_id":1,"name":"DE: Das Erste","category_id":"30","epg_channel_id":"daserste.de"}]"""
        showGrid(streams = streams, categories = categories, startCategory = "30")
        press(Key.DirectionLeft)
        rule.onNodeWithTag("guide:category:30").assertIsFocused()
    }
}
