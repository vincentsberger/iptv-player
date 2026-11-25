package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/** Texte und kleine Bedienfehler der PC-App (Review-Paket B). */
class DesktopTextsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(app: AppState = testAppState(), tab: Tab, wide: Boolean = false) {
        app.selectTab(tab)
        rule.setContent {
            // Breit (≥ 1280 dp): Seitenleiste mit Text; sonst nur Symbole.
            if (wide) Box(Modifier.requiredSize(1440.dp, 900.dp)) { AppTheme { HomeScreen(app) } }
            else AppTheme { HomeScreen(app) }
        }
    }

    @Test
    fun liveHintMatchesSingleClickPlayback() {
        show(tab = Tab.LIVE)
        rule.onNodeWithText("Doppelklick", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Ein Klick oder Enter startet ihn", substring = true).assertExists()
    }

    @Test
    fun channelListHeaderHasNoInstructions() {
        show(tab = Tab.LIVE)
        rule.onNodeWithText("Klick oder Enter zum Ansehen", substring = true).assertDoesNotExist()
    }

    @Test
    fun sidebarNamesTheProviderByHost() {
        show(tab = Tab.HOME, wide = true)
        rule.onNodeWithText("tv.example").assertExists()
        rule.onNodeWithText("XC").assertDoesNotExist()
        rule.onNodeWithText("TE").assertExists()
    }

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1000 * day
    private fun expiringIn(days: Int) = testAppState(
        http = SampleData.http(mapOf(
            "login" to """{"user_info":{"auth":1,"status":"Active","exp_date":"${(now + days * day) / 1000}","max_connections":"2","active_cons":"1"}}""",
        )),
        now = { now },
    )

    @Test
    fun startScreenMentionsSubscriptionOnlyWhenItEndsSoon() {
        // Schmales Testfenster: Die Seitenleiste zeigt kein Abo → jeder Treffer stammt von der Startseite.
        show(expiringIn(30), Tab.HOME)
        rule.onAllNodesWithText("Abo", substring = true).assertCountEquals(0)
    }

    @Test
    fun startScreenWarnsBeforeTheSubscriptionEnds() {
        show(expiringIn(3), Tab.HOME)
        val line = rule.onAllNodesWithText("Abo bis", substring = true)
        line.assertCountEquals(1)
        line.onFirst().assertExists()
        rule.onNodeWithText("1 von 2 Verbindungen", substring = true).assertExists()
    }

    @Test
    fun lastHomeRowCannotMoveFurtherDown() {
        show(tab = Tab.SETTINGS)
        rule.onAllNodesWithContentDescription("Nach unten").onFirst().assertIsEnabled()
        rule.onAllNodesWithContentDescription("Nach unten").onLast().assertIsNotEnabled()
    }

    @Test
    fun settingsShowTheVersionOnce() {
        show(tab = Tab.SETTINGS)
        rule.onAllNodesWithText("(Entwicklung)", substring = true).assertCountEquals(1)
    }

    @Test
    fun playButtonLabelFollowsPlaybackState() {
        assertEquals("Pause", playPauseLabel(playing = true))
        assertEquals("Abspielen", playPauseLabel(playing = false))
    }
}
