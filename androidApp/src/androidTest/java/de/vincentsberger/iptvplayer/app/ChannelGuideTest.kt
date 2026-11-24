package de.vincentsberger.iptvplayer.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.AppTheme
import de.vincentsberger.iptvplayer.app.ui.ChannelGuide
import de.vincentsberger.iptvplayer.app.ui.TvChrome
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Senderliste im Player: Länderkürzel nur, wenn die Liste Sender mehrerer Länder mischt – wie in Live TV. */
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
        rule.setContent { AppTheme { TvChrome { ChannelGuide(vm, playing, startCategory, onPick = { _, _ -> }) } } }
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
}
