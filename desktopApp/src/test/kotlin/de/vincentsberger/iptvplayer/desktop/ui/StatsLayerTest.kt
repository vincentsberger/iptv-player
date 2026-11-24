package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertTrue

/** Statistik-Fenster und Indikator dürfen Zapp-Karte und obere Leiste nicht verdecken. */
class StatsLayerTest {

    @get:Rule
    val rule = createComposeRule()

    private val ard = Item("1", "Das Erste HD", null, "1", "http://srv/live/1.ts", Section.LIVE, number = 101)

    @Test
    fun panelSitsBelowZapCard() {
        rule.setContent {
            AppTheme {
                Box(Modifier.fillMaxSize()) {
                    ZapCardView(
                        ard, 101, EpgEntry("Tagesschau", "20:00", "20:15"), 0.6f,
                        Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = ZapCardTop).testTag("zap"),
                    )
                    StatsLayer(panelOpen = true, stats = null, indicatorOn = false, barShown = false, onOpen = {}, onClose = {})
                }
            }
        }
        val zap = rule.onNodeWithTag("zap").getUnclippedBoundsInRoot()
        val panel = rule.onNodeWithTag("statsPanel").getUnclippedBoundsInRoot()
        assertTrue(panel.top >= zap.bottom, "Fenster ${panel.top} beginnt über dem Ende der Zapp-Karte ${zap.bottom}")
        assertTrue(panel.top >= 80.dp, "Fenster liegt in der oberen Leiste")
    }

    @Test
    fun indicatorYieldsToTopBar() {
        rule.setContent {
            AppTheme { Box(Modifier.fillMaxSize()) { StatsLayer(false, null, true, true, {}, {}) } }
        }
        rule.onNodeWithTag("statsIndicator").assertDoesNotExist()
    }

    @Test
    fun indicatorShownWithoutBar() {
        rule.setContent {
            AppTheme { Box(Modifier.fillMaxSize()) { StatsLayer(false, null, true, false, {}, {}) } }
        }
        rule.onNodeWithTag("statsIndicator").assertIsDisplayed()
    }
}
