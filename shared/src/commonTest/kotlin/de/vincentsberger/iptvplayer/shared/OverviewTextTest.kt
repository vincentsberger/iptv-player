package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.AccountInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Texte der Übersicht nach dem Laden (früher je eine Kopie in PC- und TV-App). */
class OverviewTextTest {
    private val now = 1_763_900_000_000L // 23.11.2025, mittags

    @Test
    fun countsUseGermanThousandsSeparator() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("12.345", formatCount(12_345))
        assertEquals("1.234.567", formatCount(1_234_567))
        assertEquals("-1.200", formatCount(-1_200))
    }

    @Test
    fun countLabels() {
        assertEquals("Sender", countLabel(Section.LIVE))
        assertEquals("Filme", countLabel(Section.MOVIES))
        assertEquals("Serien", countLabel(Section.SERIES))
    }

    @Test
    fun standLabelSaysTodayYesterdayOrDate() {
        assertTrue(standLabel(now, now).matches(Regex("heute \\d\\d:\\d\\d")), standLabel(now, now))
        assertTrue(standLabel(now - 24 * 3_600_000L, now).matches(Regex("gestern \\d\\d:\\d\\d")))
        assertTrue(standLabel(now - 5 * 24 * 3_600_000L, now).matches(Regex("\\d\\d\\.\\d\\d\\. \\d\\d:\\d\\d")))
    }

    @Test
    fun overviewNotes() {
        fun row(delta: Int?, warning: OverviewWarning? = null, loadedAt: Long? = null) = OverviewRow(Section.LIVE, 100, delta, warning, loadedAt)
        assertEquals("", overviewNote(row(null), now))
        assertEquals("unverändert", overviewNote(row(0), now))
        assertEquals("+1.389 seit letztem Laden", overviewNote(row(1_389), now))
        assertEquals("-12 seit letztem Laden", overviewNote(row(-12), now))
        assertEquals("-500 – deutlich weniger als zuletzt", overviewNote(row(-500, OverviewWarning.FEWER), now))
        assertEquals("leer geliefert – alter Stand behalten", overviewNote(row(null, OverviewWarning.EMPTY), now))
        assertEquals("Fehler – keine Daten", overviewNote(row(null, OverviewWarning.FAILED), now))
        assertTrue(overviewNote(row(null, OverviewWarning.FAILED, now), now).startsWith("Fehler – Stand vom heute "))
    }

    @Test
    fun subscriptionAndConnections() {
        val noon = 1805025600000L // 14.03.2027 12:00 UTC – in jeder Zeitzone derselbe Tag
        assertEquals("Abo bis 14.03.2027", subscriptionLabel(AccountInfo(noon, 2, 1)))
        assertEquals("Abo ohne Ablaufdatum", subscriptionLabel(AccountInfo(null, null, null)))
        assertEquals("1 von 2 Verbindungen in Gebrauch", connectionsLabel(AccountInfo(noon, 2, 1)))
        assertEquals("0 von 1 Verbindungen in Gebrauch", connectionsLabel(AccountInfo(null, 1, null)))
        assertNull(connectionsLabel(AccountInfo(noon, null, 1)))
    }

    @Test
    fun countryNames() {
        assertEquals("Österreich", countryNameOf("AT"))
        assertEquals("Deutschland", countryNameOf("de"))
        assertEquals("Großbritannien", countryNameOf("UK"))
        assertEquals("XX", countryNameOf("XX"))
    }
}
