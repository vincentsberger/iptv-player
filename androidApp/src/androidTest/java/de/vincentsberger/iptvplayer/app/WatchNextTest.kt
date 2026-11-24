package de.vincentsberger.iptvplayer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tvprovider.media.tv.TvContractCompat
import de.vincentsberger.iptvplayer.shared.data.HistoryEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchNextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun movie(id: String, adult: Boolean = false) =
        Item(id, "Film $id", "http://img/$id.jpg", "1", "http://srv/movie/max/geheim123/$id.mp4", Section.MOVIES, isAdult = adult)

    private fun entry(item: Item, position: Long = 600_000, duration: Long = 6_000_000, updated: Long = 1L) =
        HistoryEntry(item, position, duration, updated)

    @After
    fun cleanUp() = runBlocking { WatchNext.sync(context, "acc", emptyList()) }

    @Test
    fun idHidesTheStreamUrlAndSeparatesAccounts() {
        val id = WatchNext.id("acc", movie("1"))
        assertFalse(id.contains("geheim123"))
        assertEquals(id, WatchNext.id("acc", movie("1")))
        assertNotEquals(id, WatchNext.id("other", movie("1")))
    }

    @Test
    fun onlyResumableMoviesAndEpisodesWithoutAdultContent() {
        val live = Item("9", "Sender", null, "1", "http://srv/live/u/p/9.ts", Section.LIVE)
        val history = listOf(
            entry(movie("1")),
            entry(movie("2"), position = 5_900_000), // fast zu Ende
            entry(movie("3", adult = true)),
            entry(live),
        ) + (10..30).map { entry(movie("$it")) }
        val shown = WatchNext.entries(history, enabled = true)
        assertEquals("1", shown.first().item.id)
        assertTrue(shown.none { it.item.id in setOf("2", "3", "9") })
        assertEquals(WatchNext.MAX, shown.size)
        assertTrue(WatchNext.entries(history, enabled = false).isEmpty())
    }

    @Test
    fun syncInsertsUpdatesAndRemovesProgramsOnTheSystemHome() = runBlocking {
        assumeTrue(WatchNext.supported(context))
        WatchNext.sync(context, "acc", listOf(entry(movie("1")), entry(movie("2"))))
        assertEquals(setOf(WatchNext.id("acc", movie("1")), WatchNext.id("acc", movie("2"))), programs().keys)

        WatchNext.sync(context, "acc", listOf(entry(movie("1"), position = 1_200_000)))
        assertEquals(1_200_000, programs().getValue(WatchNext.id("acc", movie("1"))))
        assertEquals(1, programs().size)

        WatchNext.sync(context, "acc", emptyList())
        assertTrue(programs().isEmpty())
    }

    /** Eigene Watch-Next-Einträge: interne ID → gespeicherte Position. */
    private fun programs(): Map<String, Int> =
        context.contentResolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null, null, null)!!.use { c ->
            val id = c.getColumnIndexOrThrow(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
            val pos = c.getColumnIndexOrThrow(TvContractCompat.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
            buildMap { while (c.moveToNext()) put(c.getString(id), c.getInt(pos)) }
        }
}
