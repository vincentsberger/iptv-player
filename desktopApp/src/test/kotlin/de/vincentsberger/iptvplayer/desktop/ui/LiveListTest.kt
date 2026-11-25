package de.vincentsberger.iptvplayer.desktop.ui

import de.vincentsberger.iptvplayer.shared.testing.SampleData
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
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

    // ---------- Paket 3/7: Senderkarten, ruhige Info-Spalte ----------

    private val epgJson = """{"epg_listings":[{"title":"${java.util.Base64.getEncoder().encodeToString("Tagesschau".toByteArray())}",""" +
        """"start":"1970-01-01 00:00:00","end":"1970-01-01 01:00:00"}]}"""

    @Test
    fun infoColumnCrossfadesOncePerChannel() {
        val app = testAppState(SampleData.http(mapOf("get_short_epg" to epgJson))).also { it.selectTab(Tab.LIVE) }
        rule.setContent { AppTheme { HomeScreen(app) } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        var fades = 0
        var wasFading = false
        repeat(50) {
            rule.mainClock.advanceTimeBy(20)
            val fading = rule.onAllNodesWithTag("live-info").fetchSemanticsNodes().size > 1
            if (fading && !wasFading) fades++
            wasFading = fading
        }
        assertEquals(1, fades, "Sender und Programm blenden zusammen über – einmal je Senderwechsel")
        assertTrue(rule.onAllNodes(hasText("Tagesschau") and hasAnyAncestor(hasTestTag("live-info"))).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun channelsAreSeparateCards() {
        show()
        val first = rule.onNodeWithTag("channel-card:Das Erste").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("channel-card:ZDF").fetchSemanticsNode().boundsInRoot
        assertEquals(64f, first.height, 1f)
        assertEquals(6f, second.top - first.bottom, 1f)
    }

    @Test
    fun hoverPreviewWaitsForTheDwell() {
        show()
        hover("ZDF")
        rule.mainClock.advanceTimeBy(200)
        rule.onAllNodesWithText("ZDF").assertCountEquals(1)
        rule.mainClock.advanceTimeBy(250)
        infoShows("ZDF")
    }
}
