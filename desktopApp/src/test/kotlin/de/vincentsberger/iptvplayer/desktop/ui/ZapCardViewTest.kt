package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import org.junit.Rule
import kotlin.test.Test

class ZapCardViewTest {

    @get:Rule
    val rule = createComposeRule()

    private val ard = Item("1", "Das Erste HD", null, "1", "http://srv/live/1.ts", Section.LIVE, number = 101)

    @Test
    fun showsNumberNameAndProgram() {
        rule.setContent { AppTheme { ZapCardView(ard, 101, EpgEntry("Tagesschau", "20:00", "20:15"), 0.6f) } }
        rule.onNodeWithText("101").assertExists()
        rule.onNodeWithText("Das Erste HD").assertExists()
        rule.onNodeWithText("Tagesschau · bis 20:15").assertExists()
    }

    @Test
    fun withoutEpgOnlyNameLine() {
        rule.setContent { AppTheme { ZapCardView(ard, 101, null, 0f) } }
        rule.onNodeWithText("Das Erste HD").assertExists()
        rule.onNodeWithText("bis", substring = true).assertDoesNotExist()
    }
}
