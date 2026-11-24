package de.vincentsberger.iptvplayer.shared.state

import de.vincentsberger.iptvplayer.shared.HomeRow
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsStoreTest {

    private val kv = InMemoryKeyValueStore()
    private var now = 0L
    private fun store() = SettingsStore(Storage(kv)) { now }

    @Test
    fun sortAzSurvivesANewStore() {
        store().sortAz = true
        assertTrue(store().sortAz)
    }

    @Test
    fun sleepTimerCyclesAndCountsDown() {
        val s = store()
        s.cycleSleepTimer()
        assertEquals(30, s.sleepMinutesLeft())
        now = s.sleepAt!!
        assertTrue(s.consumeSleepTimer())
        assertNull(s.sleepAt)
    }

    @Test
    fun homeRowsMoveAndReset() {
        val s = store()
        val second = HomeRow.entries[1]
        s.moveHomeRow(second, up = true)
        assertEquals(second, s.homeRowOrder.first())
        s.resetHomeRows()
        assertEquals(HomeRow.entries.toList(), s.homeRowOrder)
    }
}
