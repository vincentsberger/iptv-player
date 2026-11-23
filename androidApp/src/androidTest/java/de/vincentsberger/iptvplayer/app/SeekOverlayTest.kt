package de.vincentsberger.iptvplayer.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.AppTheme
import de.vincentsberger.iptvplayer.app.ui.SeekOverlay
import de.vincentsberger.iptvplayer.shared.SeekState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeekOverlayTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun showsDeltaAndTargetTime() {
        rule.setContent { AppTheme { SeekOverlay(SeekState(3_792_000, 3_912_000), durationMs = 9_960_000, positionMs = 3_792_000) } }
        rule.onNodeWithText("+2 min ▶▶").assertExists()
        rule.onNodeWithText("1:05:12").assertExists()
    }
}
