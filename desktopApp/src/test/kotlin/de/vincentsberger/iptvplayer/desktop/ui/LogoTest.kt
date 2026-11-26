package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import de.vincentsberger.iptvplayer.shared.SectionState
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Rule
import kotlin.test.Test

/** Logo, Ladeanimation und Ladekästchen der PC-App. */
class LogoTest {

    @get:Rule
    val rule = createComposeRule()

    /** Wartet, bis die Logo-Anzeige ([BufferingOverlay]-Effekt) den neuen Zustand übernommen hat. */
    private fun settle() = rule.mainClock.advanceTimeBy(200)

    private fun count(description: String, n: Int) = rule.onAllNodesWithContentDescription(description).assertCountEquals(n)

    @Test fun loadingScreenShowsStateOfEachSection() {
        val app = testAppState()
        app.sections[Section.LIVE] = SectionState()
        app.sections[Section.MOVIES] = SectionState(loading = true)
        app.sections.remove(Section.SERIES)
        rule.setContent { AppTheme { PlaylistLoadingScreen(app) } }
        // Nur Logo und Zeilen: keine Überschrift, keine Status-Texte
        rule.onAllNodesWithText("Filme werden gesucht").assertCountEquals(0)
        rule.onAllNodesWithText("lädt …").assertCountEquals(0)
        count("geladen", 1)
        count("lädt", 1)
        count("wartet", 1)
    }

    @Test fun failedSectionShowsErrorBox() {
        val app = testAppState()
        app.sections[Section.LIVE] = SectionState()
        app.sections[Section.MOVIES] = SectionState(error = "kaputt")
        app.sections.remove(Section.SERIES)
        rule.setContent { AppTheme { PlaylistLoadingScreen(app) } }
        count("Fehler", 1)
    }

    @Test fun loadingUsesLogoLoader() {
        rule.setContent { AppTheme { Loading("Sender werden geladen …") } }
        count("Wird geladen", 1)
        rule.onNodeWithText("Sender werden geladen …").assertExists()
    }

    @Test fun logoOnlyBeforeFirstFrame() {
        var hasPicture by mutableStateOf(false)
        rule.setContent { AppTheme { BufferingOverlay("a", hasPicture, visible = true, reconnecting = false) } }
        count("Wird geladen", 1)
        count("Puffert", 0)
        hasPicture = true
        settle()
        count("Wird geladen", 0)
        count("Puffert", 1)
    }

    @Test fun logoReturnsOnTitleChangeWhileOldPictureStillSet() {
        var key by mutableStateOf("a")
        var hasPicture by mutableStateOf(false)
        rule.setContent { AppTheme { BufferingOverlay(key, hasPicture, visible = true, reconnecting = false) } }
        hasPicture = true
        settle()
        count("Puffert", 1)
        key = "b"
        settle()
        count("Wird geladen", 1)
        hasPicture = false
        settle()
        hasPicture = true
        settle()
        count("Puffert", 1)
    }
}
