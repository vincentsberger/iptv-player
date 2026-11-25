package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Kopf der Seitenleiste wie in der TV-App: Logo, Name, Uhr – schmal nur das Logo. */
@OptIn(ExperimentalTestApi::class)
class SidebarTest {

    private fun SemanticsNodeInteractionsProvider.clockShown() =
        onAllNodesWithText(Regex("""\d\d:\d\d""")).fetchSemanticsNodes().isNotEmpty()

    private fun SemanticsNodeInteractionsProvider.onAllNodesWithText(pattern: Regex) =
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher("Text ~ $pattern") { node ->
            node.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.any { pattern.matches(it.text) }
        })

    @Test
    fun headerShowsLogoNameAndClock() = runDesktopComposeUiTest(1440, 900) {
        setContent { AppTheme { HomeScreen(testAppState()) } }
        onNodeWithTag("sidebar-logo").assertExists()
        onAllNodesWithText("IPTV Player").assertCountEquals(1)
        assertTrue(clockShown(), "Uhr im Kopf der Seitenleiste")
    }

    @Test
    fun compactHeaderShowsOnlyTheLogo() = runDesktopComposeUiTest(1100, 750) {
        setContent { AppTheme { HomeScreen(testAppState()) } }
        onNodeWithTag("sidebar-logo").assertExists()
        onAllNodesWithText("IPTV Player").assertCountEquals(0)
        assertTrue(!clockShown(), "schmal keine Uhr")
    }

    @Test
    fun clockFormatsHoursAndMinutes() {
        assertEquals("07:05", clockText(LocalTime.of(7, 5)))
        assertEquals("23:59", clockText(LocalTime.of(23, 59, 59)))
    }
}
