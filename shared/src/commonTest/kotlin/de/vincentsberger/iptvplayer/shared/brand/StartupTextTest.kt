package de.vincentsberger.iptvplayer.shared.brand

import de.vincentsberger.iptvplayer.shared.SectionState
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ladeschritt je Bereich und Überschrift beim Start. */
class StartupTextTest {
    private val loading = SectionState(loading = true)
    private val done = SectionState()
    private val failed = SectionState(error = "x")

    @Test
    fun loadStepStates() {
        assertEquals(LoadStep.WAITING, loadStep(null, active = false))
        assertEquals(LoadStep.LOADING, loadStep(null, active = true))
        assertEquals(LoadStep.LOADING, loadStep(loading, active = false))
        assertEquals(LoadStep.DONE, loadStep(done, active = false))
        assertEquals(LoadStep.FAILED, loadStep(failed, active = false))
    }

    @Test
    fun headlineFollowsFirstOpenSection() {
        assertEquals("Sender werden gesucht", loadingHeadline(mapOf(Section.LIVE to loading)))
        assertEquals("Filme werden gesucht", loadingHeadline(mapOf(Section.LIVE to done, Section.MOVIES to loading)))
        assertEquals("Serien werden gesucht", loadingHeadline(mapOf(Section.LIVE to done, Section.MOVIES to done, Section.SERIES to null)))
    }

    @Test
    fun failedSectionIsNotLoadingAndHeadlineMovesOn() {
        assertEquals("Filme werden gesucht", loadingHeadline(mapOf(Section.LIVE to failed, Section.MOVIES to loading)))
    }

    @Test
    fun headlineKeepsLastWhenNothingOpen() {
        assertNull(loadingHeadline(Section.entries.associateWith { done }))
    }
}
