package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.vincentsberger.iptvplayer.shared.PauseStage
import de.vincentsberger.iptvplayer.shared.data.MediaInfo
import org.junit.Rule
import kotlin.test.Test

class PauseInfoViewTest {

    @get:Rule
    val rule = createComposeRule()

    private val dune = MediaInfo("2024", 8.3, 9960, 12, listOf("Science Fiction", "Abenteuer"), "Paul Atreides vereint sich mit Chani.")

    @Test
    fun compactShowsTitleYearRating() {
        rule.setContent { AppTheme { PauseInfoView("Dune: Part Two", null, dune, PauseStage.COMPACT, 3_792_000, 9_960_000, "22:41") } }
        rule.onNodeWithText("Dune: Part Two · 2024 · ★ 8,3").assertExists()
        rule.onNodeWithText("endet um 22:41", substring = true).assertExists()
        rule.onNodeWithText("Paul Atreides", substring = true).assertDoesNotExist()
    }

    @Test
    fun fullShowsMetaAndPlot() {
        rule.setContent { AppTheme { PauseInfoView("Dune: Part Two", null, dune, PauseStage.FULL, 3_792_000, 9_960_000, "22:41") } }
        rule.onNodeWithText("★ 8,3 · 2024 · 2 h 46 min · FSK 12 · Science Fiction, Abenteuer").assertExists()
        rule.onNodeWithText("Paul Atreides vereint sich mit Chani.").assertExists()
        rule.onNodeWithText("noch", substring = true).assertExists()
    }

    @Test
    fun withoutInfoOnlyTitle() {
        rule.setContent { AppTheme { PauseInfoView("Unbekannt", "S1 · F2 · Pilot", null, PauseStage.FULL, 0, 0, "") } }
        rule.onNodeWithText("Unbekannt").assertExists()
        rule.onNodeWithText("S1 · F2 · Pilot", substring = true).assertExists()
        rule.onNodeWithText("★", substring = true).assertDoesNotExist()
    }
}
