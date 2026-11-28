package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.epgSeconds
import kotlin.test.Test
import kotlin.test.assertEquals

/** Raster-Logik des Programmführers: Zellen, Grenzen, Sprünge und Fenster (Spec 2026-11-28-epg-grid-design). */
class EpgGridTest {

    private fun s(hhmm: String): Long = epgSeconds("2026-11-28 $hhmm:00")!!
    private fun e(title: String, from: String, to: String) =
        EpgEntry(title, from, to, startAt = "2026-11-28 $from:00", endAt = "2026-11-28 $to:00")

    @Test fun openingStartRoundsDownToHalfHour() = assertEquals(s("20:00"), EpgGrid.openingStart(s("20:17")))

    @Test fun openingStartKeepsExactHalfHour() = assertEquals(s("20:30"), EpgGrid.openingStart(s("20:30")))

    @Test fun leftLimitWithoutArchiveIsOpeningStart() = assertEquals(s("20:00"), EpgGrid.leftLimit(s("20:17"), s("20:00"), 0))

    @Test fun leftLimitWithArchiveGoesBackDays() = assertEquals(s("20:17") - 3 * 86400, EpgGrid.leftLimit(s("20:17"), s("20:00"), 3))

    @Test fun rightLimitIsAtLeastWindowEnd() =
        assertEquals(s("22:00"), EpgGrid.rightLimit(listOf(listOf(e("A", "20:00", "20:30"))), s("20:00")))

    @Test fun rightLimitIsLatestEndOverAllRows() =
        assertEquals(s("23:30"), EpgGrid.rightLimit(listOf(listOf(e("A", "20:00", "23:30")), emptyList()), s("20:00")))

    @Test fun cellsCutAtWindowAndFillGaps() {
        val c = EpgGrid.cells(listOf(e("A", "19:30", "20:15"), e("B", "20:30", "21:00")), s("20:00"), s("21:30"))
        assertEquals(listOf("A", null, "B", null), c.map { it.entry?.title })
        assertEquals(listOf(s("20:00"), s("20:15"), s("20:30"), s("21:00")), c.map { it.start })
        assertEquals(s("21:30"), c.last().end)
    }

    @Test fun rowWithoutDataIsOneEmptyCell() =
        assertEquals(listOf(GridCell(null, s("20:00"), s("22:00"))), EpgGrid.cells(emptyList(), s("20:00"), s("22:00")))

    @Test fun unreadableTimesAreIgnored() {
        val c = EpgGrid.cells(listOf(EpgEntry("X", "20:00", "21:00")), s("20:00"), s("22:00"))
        assertEquals(listOf<String?>(null), c.map { it.entry?.title })
    }

    @Test fun showLongerThanWindowIsOneCell() {
        val c = EpgGrid.cells(listOf(e("Sport", "18:00", "23:59")), s("20:00"), s("22:00"))
        assertEquals(listOf(GridCell(c.single().entry, s("20:00"), s("22:00"))), c)
        assertEquals("Sport", c.single().entry?.title)
    }

    @Test fun cellAtFindsCoveringCellAndClamps() {
        val c = EpgGrid.cells(listOf(e("A", "19:30", "20:15"), e("B", "20:30", "21:00")), s("20:00"), s("21:30"))
        assertEquals(1, EpgGrid.cellAt(c, s("20:20")))
        assertEquals(2, EpgGrid.cellAt(c, s("20:30")))
        assertEquals(0, EpgGrid.cellAt(c, s("19:00")))
        assertEquals(3, EpgGrid.cellAt(c, s("23:00")))
    }

    @Test fun anchorIsCellStartButNotBeforeWindow() {
        assertEquals(s("20:00"), EpgGrid.anchorOf(GridCell(null, s("19:30"), s("20:15")), s("20:00")))
        assertEquals(s("20:15"), EpgGrid.anchorOf(GridCell(null, s("20:15"), s("21:45")), s("20:00")))
    }

    @Test fun scrollToKeepsWindowWhenCellVisible() =
        assertEquals(s("20:00"), EpgGrid.scrollTo(s("20:00"), GridCell(null, s("20:30"), s("21:00")), s("20:00"), s("23:00")))

    @Test fun scrollToFollowsCellPastRightEdge() =
        assertEquals(s("21:00"), EpgGrid.scrollTo(s("20:00"), GridCell(null, s("22:15"), s("23:00")), s("20:00"), s("23:30")))

    @Test fun scrollToClampsAtLeftLimit() =
        assertEquals(s("20:00"), EpgGrid.scrollTo(s("20:30"), GridCell(null, s("19:40"), s("20:10")), s("20:00"), s("23:00")))

    @Test fun scrollToGoesBackForEarlierCell() =
        assertEquals(s("19:30"), EpgGrid.scrollTo(s("20:30"), GridCell(null, s("19:40"), s("20:10")), s("17:00"), s("23:00")))

    @Test fun scrollToLongShowShowsItsStart() =
        assertEquals(s("18:00"), EpgGrid.scrollTo(s("20:00"), GridCell(null, s("18:00"), s("23:59")), s("17:00"), s("23:59")))

    @Test fun scrollToClampsAtRightLimit() =
        assertEquals(s("21:00"), EpgGrid.scrollTo(s("20:00"), GridCell(null, s("22:30"), s("23:00")), s("20:00"), s("23:00")))

    @Test fun airingByLocalNow() {
        val a = e("A", "20:00", "20:30")
        assertEquals(Airing.NOW, EpgGrid.airing(a, s("20:10")))
        assertEquals(Airing.PAST, EpgGrid.airing(a, s("20:30")))
        assertEquals(Airing.FUTURE, EpgGrid.airing(a, s("19:59")))
    }
}
