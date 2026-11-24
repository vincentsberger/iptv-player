package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/** Startseiten-Auswahl: bei vielen Kategorien zugeklappt (8 sichtbar, gewählte zuerst), „Alle anzeigen“ klappt auf. */
class DashboardSettingsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun movieChips() = rule.onAllNodes(hasTestTagPrefix("dashcat:MOVIES:")).fetchSemanticsNodes()

    private fun hasTestTagPrefix(prefix: String) = androidx.compose.ui.test.SemanticsMatcher("testTag startsWith $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun show() {
        val cats = (1..12).joinToString(",") { """{"category_id":"$it","category_name":"Kategorie $it"}""" }
        val movies = (1..12).joinToString(",") { """{"stream_id":${500 + it},"name":"Film $it","category_id":"$it","added":"1700000000"}""" }
        val app = testAppState(http = SampleData.http(mapOf("get_vod_categories" to "[$cats]", "get_vod_streams" to "[$movies]")))
        rule.setContent { AppTheme { HomeScreen(app) } }
        rule.onNode(hasText("Einstellungen") and hasClickAction()).performClick()
        rule.waitForIdle()
    }

    @Test
    fun manyCategoriesAreCollapsedToEight() {
        show()
        assertEquals(8, movieChips().size)
        rule.onNode(hasTestTag("dashmore:MOVIES")).performScrollTo().performClick()
        assertEquals(12, movieChips().size)
        rule.onNode(hasTestTag("dashmore:MOVIES")).performScrollTo().performClick()
        assertEquals(8, movieChips().size)
    }

    @Test
    fun chosenCategoryStaysVisibleWhenCollapsed() {
        show()
        rule.onNode(hasTestTag("dashmore:MOVIES")).performScrollTo().performClick()
        rule.onNode(hasTestTag("dashcat:MOVIES:12")).performScrollTo().performClick()
        rule.onNode(hasTestTag("dashmore:MOVIES")).performScrollTo().performClick()
        rule.onNode(hasTestTag("dashcat:MOVIES:12")).assertExists()
        assertEquals(8, movieChips().size)
    }
}
