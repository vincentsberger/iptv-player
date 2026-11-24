package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Category
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardChoicesTest {
    private val cats = (1..12).map { Category("$it", "Kategorie $it") }

    @Test
    fun shortListIsShownCompletely() {
        val few = cats.take(DASHBOARD_COLLAPSED_COUNT)
        assertEquals(few, dashboardOptionsShown(few, chosenIds = emptySet(), expanded = false))
    }

    @Test
    fun longListShowsFirstEightWhenCollapsed() {
        assertEquals(cats.take(8).map { it.id }, dashboardOptionsShown(cats, emptySet(), expanded = false).map { it.id })
    }

    @Test
    fun chosenCategoriesComeFirstAndStayVisibleWhenCollapsed() {
        val shown = dashboardOptionsShown(cats, chosenIds = setOf("11", "3"), expanded = false).map { it.id }
        assertEquals(listOf("3", "11", "1", "2", "4", "5", "6", "7"), shown, "gewählte zuerst (in Listenreihenfolge), dann auffüllen bis 8")
    }

    @Test
    fun moreChosenThanLimitShowsAllChosen() {
        val chosen = (1..10).map { "$it" }.toSet()
        assertEquals(10, dashboardOptionsShown(cats, chosen, expanded = false).size)
    }

    @Test
    fun expandedShowsEverythingChosenFirst() {
        val shown = dashboardOptionsShown(cats, setOf("12"), expanded = true).map { it.id }
        assertEquals(12, shown.size)
        assertEquals("12", shown.first())
    }

    @Test
    fun toggleOnlyWhenListIsLong() {
        assertEquals(false, dashboardNeedsToggle(cats.take(8)))
        assertEquals(true, dashboardNeedsToggle(cats))
    }
}
