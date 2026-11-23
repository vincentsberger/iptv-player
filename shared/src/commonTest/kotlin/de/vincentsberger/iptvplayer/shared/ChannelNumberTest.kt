package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.M3uParser
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.XtreamClient
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Sendernummern des Anbieters statt Listenposition – in Anzeige und Ziffernwahl. */
class ChannelNumberTest {

    private fun ch(name: String, number: Int?) = Item(name, name, null, "1", "http://srv/live/$name.ts", Section.LIVE, number = number)

    @Test
    fun xtreamNumIsParsed() = runTest {
        val http = FakeHttp.xtream(mapOf("get_live_streams" to """[{"stream_id":1,"name":"A","category_id":"1","num":101},{"stream_id":2,"name":"B","category_id":"1","num":"0"}]"""))
        val (a, b) = XtreamClient(Account.Xtream("tv.example", "u", "p"), http).items(Section.LIVE)
        assertEquals(101, a.number)
        assertNull(b.number)
    }

    @Test
    fun m3uTvgChnoIsParsed() {
        val r = M3uParser.parse(sequenceOf("#EXTM3U", "#EXTINF:-1 tvg-chno=\"7\",ARD", "http://srv/live/1.ts", "#EXTINF:-1,ZDF", "http://srv/live/2.ts"))
        assertEquals(listOf(7, null), r.items.map { it.number })
    }

    @Test
    fun displayNumberFallsBackToPosition() {
        val list = listOf(ch("A", null), ch("B", 205))
        assertEquals(1, channelNumberOf(list[0], list))
        assertEquals(205, channelNumberOf(list[1], list))
    }

    @Test
    fun findPrefersCurrentListThenAll() {
        val a = ch("A", 1); val b = ch("B", 2); val c = ch("C", 3)
        val current = listOf(a, b)
        val all = listOf(a, b, c)
        assertEquals(ChannelPick(current, 1), ChannelNumberInput.find(2, current, all))
        assertEquals(ChannelPick(all, 2), ChannelNumberInput.find(3, current, all))
        assertNull(ChannelNumberInput.find(9, current, all))
    }

    @Test
    fun duplicateNumbersTakeFirst() {
        val all = listOf(ch("A", 5), ch("B", 5))
        assertEquals(ChannelPick(all, 0), ChannelNumberInput.find(5, all, all))
    }

    @Test
    fun withoutProviderNumbersUsesPosition() {
        val current = listOf(ch("A", null), ch("B", null))
        assertEquals(ChannelPick(current, 1), ChannelNumberInput.find(2, current, current + ch("C", null)))
        assertNull(ChannelNumberInput.find(3, current, current + ch("C", null)))
    }
}
