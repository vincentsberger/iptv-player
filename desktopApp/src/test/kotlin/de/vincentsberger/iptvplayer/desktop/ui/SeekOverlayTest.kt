package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.vincentsberger.iptvplayer.shared.SeekState
import org.junit.Rule
import kotlin.test.Test

class SeekOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsDeltaAndTargetTime() {
        rule.setContent { AppTheme { SeekOverlay(SeekState(3_792_000, 3_912_000), durationMs = 9_960_000, positionMs = 3_792_000) } }
        rule.onNodeWithText("+2 min ▶▶").assertExists()
        rule.onNodeWithText("1:05:12").assertExists()
    }

    @Test
    fun backwardsShowsRewindSymbol() {
        rule.setContent { AppTheme { SeekOverlay(SeekState(3_792_000, 3_752_000), durationMs = 9_960_000, positionMs = 3_792_000) } }
        rule.onNodeWithText("◀◀ −40 s").assertExists()
    }
}
