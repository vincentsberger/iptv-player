package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Info-Karte beim Verweilen auf Film- und Serienkacheln: erst nach ~450 ms, Filme fragen erst dann nach der Beschreibung. */
@OptIn(ExperimentalTestApi::class)
class HoverInfoCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val seriesPlot = "Berlin 1929: Ein Kommissar ermittelt im Sittenmilieu."
    private val moviePlot = "Paul Atreides reist auf den Wüstenplaneten Arrakis."
    private lateinit var http: FakeHttp

    private fun show(section: String, seriesWithPlot: Boolean = true): AppState {
        val series = if (seriesWithPlot) """[{"series_id":200,"name":"Babylon Berlin","category_id":"20","plot":"$seriesPlot"}]"""
        else """[{"series_id":200,"name":"Babylon Berlin","category_id":"20"}]"""
        http = SampleData.http(mapOf("get_series" to series, "get_vod_info" to """{"info":{"plot":"$moviePlot"},"movie_data":{}}"""))
        val app = testAppState(http = http)
        rule.setContent { AppTheme { HomeScreen(app) } }
        rule.onAllNodes(hasText(section) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        return app
    }

    private fun hover(title: String) = rule.onAllNodesWithText(title).onFirst().performMouseInput { moveTo(center) }
    private fun infoRequests() = http.requests.count { "get_vod_info" in it }

    @Test
    fun cardShowsAfterDwellWithFullTitleAndPlot() {
        show("Serien")
        hover("Babylon Berlin")
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText(seriesPlot).assertDoesNotExist()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText(seriesPlot).assertExists()
    }

    @Test
    fun movieRequestsInfoOnlyAfterDwell() {
        show("Filme")
        hover("Dune")
        rule.mainClock.advanceTimeBy(300)
        assertEquals(0, infoRequests(), "beim bloßen Überfahren noch keine Anfrage")
        rule.mainClock.advanceTimeBy(300)
        rule.mainClock.autoAdvance = true
        rule.waitUntil(3_000) { infoRequests() > 0 }
        rule.waitUntil(3_000) { rule.onAllNodesWithText(moviePlot).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun quickPassDoesNotRequestInfo() {
        show("Filme")
        listOf("Arrival", "Dune", "Arrival", "Dune", "Arrival").forEach { // gleiche Kategorie, beide sichtbar
            hover(it)
            rule.mainClock.advanceTimeBy(100)
        }
        assertEquals(0, infoRequests())
    }

    @Test
    fun cardClosesWhenMouseLeaves() {
        show("Serien")
        hover("Babylon Berlin")
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText(seriesPlot).assertExists()
        rule.onAllNodesWithText("Serien").onFirst().performMouseInput { moveTo(center) } // weg von Kachel und Karte
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText(seriesPlot).assertDoesNotExist()
    }

    @Test
    fun seriesWithoutPlotShowsNoDescription() {
        show("Serien", seriesWithPlot = false)
        hover("Babylon Berlin")
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("Beschreibung wird geladen …").assertDoesNotExist()
        assertTrue(rule.onAllNodesWithText("Babylon Berlin").fetchSemanticsNodes().size >= 2, "Karte mit Titel offen")
    }

    @Test
    fun clickOnCardOpensTitle() {
        val app = show("Serien")
        hover("Babylon Berlin")
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText(seriesPlot).performClick()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue(app.screen is Screen.Series, "Klick auf die Karte öffnet den Titel wie ein Klick auf die Kachel")
    }
}
