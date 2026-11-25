package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Senderliste: Vorschau beim Überfahren, Filterfeld, Kontextmenüs (Review-Paket C). */
@OptIn(ExperimentalTestApi::class)
class LiveListTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(tab: Tab = Tab.LIVE): AppState {
        val app = testAppState()
        app.selectTab(tab)
        rule.setContent { AppTheme { HomeScreen(app) } }
        return app
    }

    /** Senderzeile (klickbar) – die Info-Spalte trägt denselben Namen als Überschrift. */
    private fun row(name: String) = rule.onNode(hasText(name) and hasClickAction())
    private fun hover(name: String) = row(name).performMouseInput { moveTo(center) }
    private fun infoShows(name: String) = rule.onAllNodesWithText(name).assertCountEquals(2)

    // ---------- H4: Überfahren zeigt nur eine Vorschau ----------

    @Test
    fun hoverPreviewsWithoutSelecting() {
        show()
        hover("ZDF")
        rule.mainClock.advanceTimeBy(400)
        infoShows("ZDF")
        row("ZDF").assertIsNotSelected()
    }

    @Test
    fun passingOverARowDoesNotChangeTheInfo() {
        show()
        hover("Das Erste")
        rule.mainClock.advanceTimeBy(400)
        hover("ZDF")
        rule.mainClock.advanceTimeBy(50)
        rule.onAllNodesWithText("Live TV").onFirst().performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(400)
        infoShows("Das Erste")
        rule.onAllNodesWithText("ZDF").assertCountEquals(1)
    }

    @Test
    fun keyboardStartsAtTheTopAndWinsOverThePreview() {
        show()
        hover("Sport1")
        rule.mainClock.advanceTimeBy(400)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.mainClock.advanceTimeBy(400)
        row("Das Erste").assertIsSelected()
        infoShows("Das Erste")
    }

    // ---------- M3: Filterfeld über der Senderliste ----------

    @Test
    fun filterNarrowsTheChannelList() {
        show()
        rule.onNodeWithTag("live-filter").performTextInput("zd")
        rule.onNodeWithText("Das Erste").assertDoesNotExist()
        row("ZDF").assertExists()
        rule.onNodeWithText("1 Sender").assertExists()
    }

    @Test
    fun typingInTheFilterTriggersNoListShortcut() {
        val app = show()
        row("ZDF").performClick() // Auswahl da → F würde sie sonst merken
        rule.onNodeWithTag("live-filter").performClick()
        rule.onNodeWithTag("live-filter").performKeyInput { pressKey(Key.F) }
        assertTrue(app.favorites.isEmpty(), "F im Filterfeld ist ein Buchstabe, kein Favorit")
    }

    @Test
    fun downFromTheFilterSelectsTheFirstHit() {
        show()
        rule.onNodeWithTag("live-filter").performTextInput("sport")
        rule.onNodeWithTag("live-filter").performKeyInput { pressKey(Key.DirectionDown) }
        row("Sport1").assertIsSelected()
    }

    // ---------- M2: Kontextmenüs ----------

    @Test
    fun channelRowOffersPlayAndFavoriteOnRightClick() {
        val app = show()
        row("ZDF").performMouseInput { rightClick(center) }
        rule.onNodeWithText("Ansehen").assertExists()
        rule.onNodeWithText("Zu Favoriten hinzufügen").performClick()
        assertEquals(listOf("ZDF"), app.favoriteItems(Section.LIVE).map { it.name })
    }

    @Test
    fun movieCardOffersOpenOnRightClick() {
        show(Tab.MOVIES)
        rule.onAllNodesWithText("Dune").onFirst().performMouseInput { rightClick(center) }
        rule.onNodeWithText("Öffnen").assertExists()
        rule.onNodeWithText("Zu Favoriten hinzufügen").assertExists()
    }

    @Test
    fun categoryCanBeHiddenFromTheContextMenu() {
        val app = show()
        row("Sport").performMouseInput { rightClick(center) }
        rule.onNodeWithText("Kategorie ausblenden").performClick()
        assertTrue(app.section(Section.LIVE).categories.none { it.name == "Sport" })
    }
}
