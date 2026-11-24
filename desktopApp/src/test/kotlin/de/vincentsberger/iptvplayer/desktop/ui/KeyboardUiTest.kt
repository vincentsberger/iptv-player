package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import de.vincentsberger.iptvplayer.shared.Palette as Tokens
import de.vincentsberger.iptvplayer.shared.Tab
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Bedienung per Tastatur in der Startseite: Suchfeld-Fokus, Tastenkappen, Fokusrahmen. */
class KeyboardUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val keys = KeyboardState()
    private lateinit var focusManager: FocusManager

    private fun show(tab: Tab) {
        val app = testAppState().also { it.selectTab(tab) }
        rule.setContent {
            focusManager = LocalFocusManager.current
            // Breites Fenster (≥ 1280 dp): Seitenleiste mit Beschriftung und Tastenkappen statt nur Symbolen.
            Box(Modifier.requiredSize(1440.dp, 900.dp)) {
                CompositionLocalProvider(LocalKeyboard provides keys) { AppTheme { HomeScreen(app) } }
            }
        }
    }

    @Test
    fun searchFieldHasFocusWhenSearchOpens() {
        show(Tab.SEARCH)
        rule.onNodeWithTag("search-field").assertIsFocused()
    }

    @Test
    fun ctrlFFocusesSearchFieldAgain() {
        show(Tab.SEARCH)
        rule.runOnIdle { focusManager.clearFocus() }
        rule.runOnIdle { keys.searchFocusRequests++ }
        rule.onNodeWithTag("search-field").assertIsFocused()
    }

    @Test
    fun shortcutCapsOnlyWhileCtrlIsHeld() {
        show(Tab.HOME)
        rule.onNodeWithText("Strg 1").assertDoesNotExist()
        rule.runOnIdle { keys.ctrlHeld = true }
        rule.onNodeWithText("Strg 1").assertExists()
    }

    /** Linker Rand der Zeile in Fokusfarbe? (Mitte der Höhe – dort ist der Rahmen nicht abgerundet.) */
    private fun hasFocusRing(text: String): Boolean {
        val pixels = rule.onNode(hasText(text) and hasClickAction()).captureToImage().toPixelMap()
        val edge = pixels[0, pixels.height / 2]
        val ring = Color(Tokens.FocusBg)
        return abs(edge.red - ring.red) < 0.05f && abs(edge.green - ring.green) < 0.05f && abs(edge.blue - ring.blue) < 0.05f
    }

    @Test
    fun focusRingShowsOnlyAfterKeyboardUse() {
        show(Tab.HOME)
        rule.onNode(hasText("Filme") and hasClickAction()).requestFocus()
        rule.waitForIdle()
        assertFalse(hasFocusRing("Filme"), "nach Mausbedienung kein Rahmen")
        rule.runOnIdle { keys.keyboardMode = true }
        assertTrue(hasFocusRing("Filme"), "mit Tastatur: Rahmen in Fokusfarbe")
    }
}
