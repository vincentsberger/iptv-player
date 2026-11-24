package de.vincentsberger.iptvplayer.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.AppTheme
import de.vincentsberger.iptvplayer.app.ui.PlaylistLoadingScreen
import de.vincentsberger.iptvplayer.shared.SectionState
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class LoadingScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun count(desc: String) = rule.onAllNodesWithContentDescription(desc, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test fun loadingScreenChecksFinishedSections() {
        val vm = testAppState()
        vm.sections[Section.LIVE] = SectionState()
        vm.sections[Section.MOVIES] = SectionState(loading = true)
        rule.setContent { AppTheme { PlaylistLoadingScreen(vm) } }
        rule.onNodeWithText("Filme werden gesucht").assertExists()
        assertEquals(1, count("geladen"))
        assertEquals(1, count("lädt"))
        assertEquals(1, count("wartet"))
        assertEquals(1, count("Wird geladen"))
    }

    @Test fun failedSectionShowsErrorBox() {
        val vm = testAppState()
        vm.sections[Section.LIVE] = SectionState(error = "x")
        rule.setContent { AppTheme { PlaylistLoadingScreen(vm) } }
        assertEquals(1, count("Fehler"))
    }
}
