package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/** Style-Lock und Player-Feinschliff (Review-Paket G: K4, K9, K10, K11). */
class PolishTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun audioDelayStepsIn50MsAndStopsAtOneSecond() {
        assertEquals(50, stepAudioDelay(0, 1))
        assertEquals(-50, stepAudioDelay(0, -1))
        assertEquals(150, stepAudioDelay(100, 1))
        assertEquals(150, stepAudioDelay(120, 1), "alte Werte rasten auf ganze Schritte ein")
        assertEquals(1000, stepAudioDelay(1000, 1))
        assertEquals(-1000, stepAudioDelay(-1000, -1))
    }

    @Test
    fun audioDelayLabelSaysWhatHappens() {
        assertEquals("Aus", audioDelayLabel(0))
        assertEquals("+150 ms (Ton später)", audioDelayLabel(150))
        assertEquals("−100 ms (Ton früher)", audioDelayLabel(-100))
    }

    @Test
    fun timelineFractionFollowsTheTrack() {
        assertEquals(0f, timelineFraction(5f, 1000f, 10f), "vor dem Spuranfang")
        assertEquals(0.5f, timelineFraction(500f, 1000f, 10f))
        assertEquals(1f, timelineFraction(995f, 1000f, 10f), "nach dem Spurende")
        assertEquals(0f, timelineFraction(5f, 10f, 10f), "zu schmal für eine Spur")
    }

    @Test
    fun greetingFollowsTheTimeOfDay() {
        assertEquals("Guten Morgen", greeting(7))
        assertEquals("Guten Tag", greeting(14))
        assertEquals("Guten Abend", greeting(22))
        assertEquals("Guten Abend", greeting(2))
    }

    @Test
    fun startPageTitleIsTheSectionNotTheGreeting() {
        val app = testAppState()
        app.selectTab(Tab.HOME)
        rule.setContent { AppTheme { HomeScreen(app) } }
        rule.onNodeWithText("hier geht es dort weiter", substring = true).assertExists()
        rule.onNodeWithText("Guten Abend").assertDoesNotExist()
        rule.onNodeWithText("Guten Morgen").assertDoesNotExist()
        rule.onNodeWithText("Guten Tag").assertDoesNotExist()
    }

    @Test
    fun loginWithoutTabHintAndWithExamplePlaceholder() {
        rule.setContent { AppTheme { LoginScreen(onLogin = {}) } }
        rule.onNodeWithText("nächstes Feld").assertDoesNotExist()
        rule.onNodeWithText("z. B. http://anbieter.example:8080").assertExists()
    }
}
