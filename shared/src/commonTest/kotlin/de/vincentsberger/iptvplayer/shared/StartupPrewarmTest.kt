package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Repository
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Beim Start rechnet der Hintergrund die Ansichten vor; der Ladebildschirm bleibt so lange stehen. */
class StartupPrewarmTest {

    /** Dispatcher, der Aufgaben sammelt, bis der Test sie ausführt – so lässt sich „Vorberechnen läuft noch“ festhalten. */
    private class Held : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    private fun storage() = Storage(InMemoryKeyValueStore()).apply { saveAccount(SampleData.account) }

    @Test
    fun loadingScreenStaysUntilViewsArePrewarmed() = runTest {
        val held = Held()
        val app = AppState(
            storage(), scope = this, clock = { 1_000L }, prewarm = held,
            newRepository = { a, c -> Repository(a, SampleData.http(), c, Dispatchers.Unconfined) },
        )
        advanceUntilIdle()
        assertTrue(Section.entries.all { app.sections[it]?.loading == false }, "alle Bereiche geladen")
        assertFalse(app.startupDone, "Ladebildschirm steht noch, solange vorberechnet wird")
        held.runAll()
        advanceUntilIdle()
        assertTrue(app.startupDone)
        assertEquals(3, app.section(Section.MOVIES).items.size)
    }

    @Test
    fun withoutPrewarmStartupEndsRightAfterLoading() = runTest {
        val app = AppState(storage(), scope = this, clock = { 1_000L }, newRepository = { a, c -> Repository(a, SampleData.http(), c, Dispatchers.Unconfined) })
        advanceUntilIdle()
        assertTrue(app.startupDone)
    }
}
