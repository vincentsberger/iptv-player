package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Einstellungen mit Zwischenüberschriften, Favoriten mit einem Symbol, Ladebildschirm wie TV (Paket 6 + Kleinkram). */
class SettingsFavoritesLoadingTest {

    @get:Rule
    val rule = createComposeRule()

    private fun showSettings(http: de.vincentsberger.iptvplayer.shared.testing.FakeHttp = SampleData.http()) {
        val app = testAppState(http)
        rule.setContent { AppTheme { HomeScreen(app) } }
        rule.onNode(hasText("Einstellungen") and hasClickAction()).performClick()
        rule.waitForIdle()
    }

    @Test
    fun settingsShowSubheadingsForDashboardAndUpdates() {
        showSettings()
        rule.onAllNodesWithTag("settings-sub:dashboard").assertCountEquals(1)
        rule.onAllNodesWithTag("settings-sub:updates").assertCountEquals(1)
        rule.onAllNodesWithTag("settings-sub:countries").assertCountEquals(0) // Anbieter ohne Länderkürzel
    }

    @Test
    fun countryFilterGetsItsOwnSubheading() {
        val cats = """[{"category_id":"10","category_name":"DE | Science-Fiction"},{"category_id":"11","category_name":"AT | Komödie"}]"""
        showSettings(SampleData.http(mapOf("get_vod_categories" to cats)))
        rule.onAllNodesWithTag("settings-sub:countries").assertCountEquals(1)
    }

    @Test
    fun favoritesUseOneStarIconAndOneText() {
        val app = testAppState().also { it.selectTab(Tab.FAVORITES) }
        rule.setContent { AppTheme { HomeScreen(app) } }
        rule.onNodeWithText("Deine Lieblingssender, Filme und Serien erscheinen hier.").assertExists()
        rule.onAllNodesWithText("Klicke auf den Stern", substring = true).assertCountEquals(0)
        assertEquals(Icons.Rounded.Star, Tab.FAVORITES.icon())
    }

    @Test
    fun loadingScreenShowsCenteredLogoAboveRows() {
        rule.mainClock.autoAdvance = false // Logo und Spinner laufen endlos
        rule.setContent { AppTheme { PlaylistLoadingScreen(testAppState()) } }
        rule.mainClock.advanceTimeBy(100)
        rule.onAllNodesWithTag("loading-bar").assertCountEquals(0)
        rule.onAllNodesWithText("IPTV Player").assertCountEquals(0)
        val logo = rule.onNodeWithTag("loading-logo").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, logo.center.x, 1f)
        assertEquals(120f, logo.width, 1f) // Logo ist breiter als hoch (Seitenverhältnis der Marke)
        val live = rule.onNodeWithText(Section.LIVE.title).fetchSemanticsNode().boundsInRoot
        assertTrue(logo.bottom <= live.top, "Logo $logo über den Zeilen $live")
    }
}
