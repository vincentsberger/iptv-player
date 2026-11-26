package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.testing.FakeHttp
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import kotlin.test.AfterTest
import kotlin.test.Test

/** Fassung aus dem Microsoft Store: Updates liefert der Store – die App bietet nie selbst GitHub-Updates an. */
class StoreBuildTest {

    @get:Rule
    val rule = createComposeRule()

    private val release = """{"tag_name":"v9.9.0","html_url":"https://example.org/v9.9.0","assets":[]}"""

    @AfterTest
    fun clearStoreFlag() {
        System.clearProperty("iptvplayer.store")
    }

    private fun show(tab: Tab, store: Boolean): AppState {
        if (store) System.setProperty("iptvplayer.store", "true") else System.clearProperty("iptvplayer.store")
        val app = testAppState().also { it.selectTab(tab) }
        runBlocking { app.checkForUpdate("1.0.0", force = true, http = FakeHttp { release }) }
        rule.setContent { AppTheme { HomeScreen(app) } }
        return app
    }

    @Test
    fun storeBuildShowsStoreTextInsteadOfUpdateButton() {
        show(Tab.SETTINGS, store = true)
        rule.onNodeWithText(STORE_UPDATES_TEXT).assertExists()
        rule.onAllNodesWithText("Nach Updates suchen").assertCountEquals(0)
        rule.onAllNodesWithText("Download-Seite öffnen").assertCountEquals(0)
        rule.onAllNodesWithText("Einmal am Tag automatisch nachsehen", substring = true).assertCountEquals(0)
    }

    @Test
    fun storeBuildShowsNoUpdateHintOnStart() {
        show(Tab.HOME, store = true)
        rule.onAllNodesWithTag("update-hint").assertCountEquals(0)
    }

    @Test
    fun regularBuildStillOffersUpdates() {
        show(Tab.SETTINGS, store = false)
        rule.onNode(hasText("Download-Seite öffnen") and hasClickAction()).assertExists()
        rule.onAllNodesWithText(STORE_UPDATES_TEXT).assertCountEquals(0)
    }
}
