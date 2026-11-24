package de.vincentsberger.iptvplayer.shared


import de.vincentsberger.iptvplayer.shared.data.HistoryEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComfortFeaturesTest {

    private fun names(list: List<Item>) = list.map { it.name }

    // ---------- Sortieren ----------

    @Test
    fun providerOrderIsKeptByDefault() {
        val app = testAppState()
        assertEquals(listOf("Das Erste", "ZDF", "Sport1"), names(app.section(Section.LIVE).items))
    }

    @Test
    fun sortAzSortsItemsAndCategoriesAndIsRemembered() {
        val kv = InMemoryKeyValueStore()
        val app = testAppState(kv = kv)
        app.sortAz = true
        assertEquals(listOf("Das Erste", "Sport1", "ZDF"), names(app.section(Section.LIVE).items))
        assertEquals(listOf("Das Erste", "ZDF"), names(app.section(Section.LIVE).byCategory.getValue("1")))
        assertEquals(listOf("Nachrichten", "Sport"), app.section(Section.LIVE).categories.map { it.name })
        assertTrue(testAppState(kv = kv).sortAz, "Einstellung muss einen Neustart überleben")
    }

    // ---------- Ausblenden ----------

    @Test
    fun hiddenCategoryDisappearsFromListsAllAndSearch() {
        val kv = InMemoryKeyValueStore()
        val app = testAppState(kv = kv)
        app.toggleHidden(Section.LIVE, "2")

        val live = app.section(Section.LIVE)
        assertEquals(listOf("1"), live.categories.map { it.id })
        assertEquals(listOf("Das Erste", "ZDF"), names(live.items))
        app.selectTab(Tab.LIVE)
        assertEquals(listOf("Das Erste", "ZDF"), names(app.liveChannels))
        app.query = "sport"
        assertNull(app.searchResults[Section.LIVE])
        assertEquals(listOf("Sport"), app.hiddenCategories(Section.LIVE).map { it.name })

        val again = testAppState(kv = kv)
        assertTrue(again.isHidden(Section.LIVE, "2"), "Ausblendung muss einen Neustart überleben")
        again.toggleHidden(Section.LIVE, "2")
        assertEquals(3, again.section(Section.LIVE).items.size)
    }

    @Test
    fun hidingIsPerSection() {
        val app = testAppState()
        app.toggleHidden(Section.MOVIES, "1") // gleiche ID wie die Live-Kategorie, anderer Bereich
        assertEquals(3, app.section(Section.LIVE).items.size)
    }

    // ---------- Schlaf-Timer ----------

    @Test
    fun sleepTimerCyclesThroughStepsAndOff() {
        var now = 0L
        val app = testAppState(now = { now })
        assertNull(app.sleepMinutesLeft())
        app.cycleSleepTimer(); assertEquals(30, app.sleepMinutesLeft())
        app.cycleSleepTimer(); assertEquals(60, app.sleepMinutesLeft())
        app.cycleSleepTimer(); assertEquals(90, app.sleepMinutesLeft())
        app.cycleSleepTimer(); assertNull(app.sleepMinutesLeft())
    }

    @Test
    fun sleepTimerFiresOnceWhenDue() {
        var now = 0L
        val app = testAppState(now = { now })
        app.cycleSleepTimer()
        now = 29 * 60_000L
        assertFalse(app.consumeSleepTimer())
        assertEquals(1, app.sleepMinutesLeft())
        now = 30 * 60_000L
        assertTrue(app.consumeSleepTimer())
        assertFalse(app.consumeSleepTimer(), "nach dem Auslösen ist der Timer aus")
        assertNull(app.sleepMinutesLeft())
    }

    @Test
    fun cyclingARunningTimerJumpsToTheNextStep() {
        var now = 0L
        val app = testAppState(now = { now })
        app.cycleSleepTimer()            // 30
        now = 20 * 60_000L               // noch 10 Minuten
        app.cycleSleepTimer()
        assertEquals(30, app.sleepMinutesLeft(), "von 10 Minuten Rest geht es auf 30, nicht auf 60")
    }

    // ---------- Nächste Folge ----------

    @Test
    fun nextEpisodeOnlyForSeriesAndNotAfterTheLast() {
        val app = testAppState()
        val ep = { n: Int -> Item("$n", "Folge $n", null, "200", "http://tv.example/series/u/p/$n.mp4", Section.SERIES) }
        val season = listOf(ep(1), ep(2))
        assertEquals(1, app.nextEpisodeIndex(season, 0))
        assertNull(app.nextEpisodeIndex(season, 1))
        val movies = app.section(Section.MOVIES).items.ifEmpty { listOf(Item("100", "Arrival", null, "10", "http://x/1.mp4", Section.MOVIES), Item("101", "Dune", null, "10", "http://x/2.mp4", Section.MOVIES)) }
        assertNull(app.nextEpisodeIndex(movies, 0), "Filme laufen nicht automatisch weiter")
    }

    @Test
    fun autoNextIsOnByDefaultAndCanBeTurnedOff() {
        val kv = InMemoryKeyValueStore()
        val app = testAppState(kv = kv)
        assertTrue(app.autoNextEpisode)
        app.autoNextEpisode = false
        assertFalse(testAppState(kv = kv).autoNextEpisode)
    }

    @Test
    fun connectionIndicatorIsOffByDefaultAndPersists() {
        val kv = InMemoryKeyValueStore()
        val app = testAppState(kv = kv)
        assertFalse(app.showConnectionIndicator)
        app.showConnectionIndicator = true
        assertTrue(testAppState(kv = kv).showConnectionIndicator)
    }

    // ---------- Start mit letztem Sender ----------

    private fun kvWithLastChannel(enabled: Boolean): InMemoryKeyValueStore {
        val kv = InMemoryKeyValueStore()
        val storage = Storage(kv)
        storage.saveAccount(SampleData.account) // Verlauf gehört zu einem Anbieter
        val zdf = Item("2", "ZDF", null, "1", "http://tv.example/live/user/pass/2.ts", Section.LIVE)
        storage.saveHistory(listOf(HistoryEntry(zdf, 0, 0, 1)))
        storage.startWithLastChannel = enabled
        return kv
    }

    @Test
    fun startsWithLastChannelWhenEnabled() {
        val app = testAppState(kv = kvWithLastChannel(true))
        val player = assertIs<Screen.Player>(app.screen)
        assertEquals("ZDF", player.items[player.index].name)
        assertEquals(3, player.items.size, "ganze Senderliste, damit man weiterzappen kann")
        app.back()
        assertEquals(Screen.Home, app.screen)
    }

    @Test
    fun startsOnHomeWhenDisabled() {
        assertEquals(Screen.Home, testAppState(kv = kvWithLastChannel(false)).screen)
    }

    // ---------- Senderwahl per Ziffern ----------

    @Test
    fun digitsAreCollectedAndTakenAsNumber() {
        val input = ChannelNumberInput()
        input.type(1); input.type(2)
        assertEquals("12", input.digits)
        assertEquals(12, input.take())
        assertEquals("", input.digits)
    }

    @Test
    fun tooManyDigitsStartOverAndZeroIsNoChannel() {
        val input = ChannelNumberInput(maxDigits = 3)
        "1234".forEach { input.type(it.digitToInt()) }
        assertEquals("4", input.digits)
        input.take()
        input.type(0)
        assertNull(input.take())
    }

    @Test
    fun numberMapsToListIndex() {
        assertEquals(0, ChannelNumberInput.indexFor(1, 3))
        assertEquals(2, ChannelNumberInput.indexFor(3, 3))
        assertNull(ChannelNumberInput.indexFor(4, 3))
        assertNull(ChannelNumberInput.indexFor(0, 3))
    }

}
