package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Kategorie eines Titels fürs Kontextmenü – Folgen tragen die Serien-ID als categoryId, nicht die Kategorie. */
class CategoryOfTest {

    @Test
    fun movieAndChannelUseTheirOwnCategory() {
        val app = testAppState()
        assertEquals("Science-Fiction", app.categoryOf(app.section(Section.MOVIES).items.first { it.name == "Arrival" })?.name)
        assertEquals("Nachrichten", app.categoryOf(app.section(Section.LIVE).items.first())?.name)
    }

    @Test
    fun episodeUsesCategoryOfItsSeries() {
        val app = testAppState()
        // Folge der Serie „Babylon Berlin“ (series_id 200, Kategorie 20 „Drama“): categoryId = Serien-ID.
        val episode = Item("2001", "Pilot", null, "200", "http://tv.example/series/user/pass/2001.mp4", Section.SERIES)
        assertEquals("Drama", app.categoryOf(episode)?.name)
    }

    @Test
    fun unknownCategoryIsNull() {
        val app = testAppState()
        assertNull(app.categoryOf(Item("x", "X", null, "999", "http://x", Section.MOVIES)))
    }

    @Test
    fun m3uSeriesItemKeepsItsOwnCategory() {
        val app = testAppState()
        // M3U: Serien-Einträge haben eine Adresse und tragen die echte Gruppe als categoryId (keine Serie mit dieser ID).
        val m3uSeries = Item("m1", "Babylon Berlin S01E01", null, "20", "http://m3u/series/1.mkv", Section.SERIES)
        assertEquals("Drama", app.categoryOf(m3uSeries)?.name)
    }
}
