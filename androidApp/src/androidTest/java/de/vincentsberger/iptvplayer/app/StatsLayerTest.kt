package de.vincentsberger.iptvplayer.app

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
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.AppTheme
import de.vincentsberger.iptvplayer.app.ui.StatsLayer
import de.vincentsberger.iptvplayer.app.ui.ZapCardTop
import de.vincentsberger.iptvplayer.app.ui.ZapCardView
import de.vincentsberger.iptvplayer.shared.data.EpgEntry
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Statistik-Fenster und Indikator dürfen Zapp-Karte und obere Leiste nicht verdecken. */
@RunWith(AndroidJUnit4::class)
class StatsLayerTest {

    @get:Rule val rule = createComposeRule()

    private val ard = Item("1", "Das Erste HD", null, "1", "http://srv/live/1.ts", Section.LIVE, number = 101)

    @Test
    fun panelSitsBelowZapCardAndBar() {
        rule.setContent {
            AppTheme {
                Box(Modifier.fillMaxSize()) {
                    ZapCardView(
                        ard, 101, EpgEntry("Tagesschau", "20:00", "20:15"), 0.6f,
                        Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = ZapCardTop).testTag("zap"),
                    )
                    StatsLayer(panelOpen = true, stats = null, indicatorOn = false, barShown = false, guideOpen = false)
                }
            }
        }
        val zap = rule.onNodeWithTag("zap").getUnclippedBoundsInRoot()
        val panel = rule.onNodeWithTag("stats:panel").getUnclippedBoundsInRoot()
        assertTrue("Fenster beginnt über dem Ende der Zapp-Karte", panel.top >= zap.bottom)
        assertTrue("Fenster liegt in der oberen Leiste", panel.top >= 124.dp)
    }

    @Test
    fun indicatorYieldsToTopBar() {
        rule.setContent { AppTheme { Box(Modifier.fillMaxSize()) { StatsLayer(false, null, true, true, false) } } }
        rule.onNodeWithTag("stats:indicator").assertDoesNotExist()
    }

    @Test
    fun indicatorShownWithoutBar() {
        rule.setContent { AppTheme { Box(Modifier.fillMaxSize()) { StatsLayer(false, null, true, false, false) } } }
        rule.onNodeWithTag("stats:indicator").assertIsDisplayed()
    }

    @Test
    fun panelYieldsToChannelGuide() {
        rule.setContent { AppTheme { Box(Modifier.fillMaxSize()) { StatsLayer(true, null, false, false, true) } } }
        rule.onNodeWithTag("stats:panel").assertDoesNotExist()
    }
}
