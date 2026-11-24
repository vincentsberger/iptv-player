package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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

    private fun count(description: String, n: Int) = rule.onAllNodesWithContentDescription(description).assertCountEquals(n)

    @Test fun loadingScreenShowsHeadlineOfFirstOpenSection() {
        val app = testAppState()
        app.sections[Section.LIVE] = SectionState()
        app.sections[Section.MOVIES] = SectionState(loading = true)
        app.sections.remove(Section.SERIES)
        rule.setContent { AppTheme { PlaylistLoadingScreen(app) } }
        rule.onNodeWithText("Filme werden gesucht").assertExists()
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
        rule.waitForIdle()
        count("Wird geladen", 0)
        count("Puffert", 1)
    }
}
