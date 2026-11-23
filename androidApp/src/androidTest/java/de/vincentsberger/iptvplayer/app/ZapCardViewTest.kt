package de.vincentsberger.iptvplayer.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.AppTheme
import de.vincentsberger.iptvplayer.app.ui.ZapCardView
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZapCardViewTest {

    @get:Rule val rule = createComposeRule()

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
        rule.onNodeWithText("bis", substring = true).assertDoesNotExist()
    }
}
