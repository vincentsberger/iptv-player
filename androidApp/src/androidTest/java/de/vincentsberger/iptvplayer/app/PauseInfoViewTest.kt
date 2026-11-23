package de.vincentsberger.iptvplayer.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.AppTheme
import de.vincentsberger.iptvplayer.app.ui.PauseInfoView
import de.vincentsberger.iptvplayer.shared.PauseStage
import de.vincentsberger.iptvplayer.shared.data.MediaInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PauseInfoViewTest {

    @get:Rule val rule = createComposeRule()

    private val dune = MediaInfo("2024", 8.3, 9960, 12, listOf("Science Fiction", "Abenteuer"), "Paul Atreides vereint sich mit Chani.")

    @Test
    fun compactShowsTitleYearRating() {
        rule.setContent { AppTheme { PauseInfoView("Dune: Part Two", null, dune, PauseStage.COMPACT, 3_792_000, 9_960_000, "22:41") } }
        rule.onNodeWithText("Dune: Part Two · 2024 · ★ 8,3").assertExists()
        rule.onNodeWithText("Paul Atreides", substring = true).assertDoesNotExist()
    }

    @Test
    fun fullShowsMetaAndPlot() {
        rule.setContent { AppTheme { PauseInfoView("Dune: Part Two", null, dune, PauseStage.FULL, 3_792_000, 9_960_000, "22:41") } }
        rule.onNodeWithText("★ 8,3 · 2024 · 2 h 46 min · FSK 12 · Science Fiction, Abenteuer").assertExists()
        rule.onNodeWithText("Paul Atreides vereint sich mit Chani.").assertExists()
    }

    @Test
    fun withoutInfoOnlyTitle() {
        rule.setContent { AppTheme { PauseInfoView("Unbekannt", "S1 · F2 · Pilot", null, PauseStage.FULL, 0, 0, "") } }
        rule.onNodeWithText("Unbekannt").assertExists()
        rule.onNodeWithText("★", substring = true).assertDoesNotExist()
    }
}
