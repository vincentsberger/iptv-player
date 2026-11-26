package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackdropTest {
    @get:Rule
    val rule = createComposeRule()

    @Test fun backdropKeeps16to9WhenThereIsRoom() = assertEquals(450.dp, backdropHeight(800.dp, windowHeight = 2000.dp))

    @Test fun backdropIsCappedOnLowWindows() = assertEquals(270.dp, backdropHeight(800.dp, windowHeight = 600.dp))

    @Test fun backdropImagePrefersTheWideImageAndFallsBackToThePoster() {
        assertEquals(BackdropImage("b", poster = false), backdropImage("b", "p"))
        assertEquals(BackdropImage("p", poster = true), backdropImage(null, "p"))
        assertEquals(BackdropImage("p", poster = true), backdropImage(" ", "p"))
        assertNull(backdropImage(null, ""))
    }

    private fun showBackdrop(image: BackdropImage?) = rule.setContent {
        AppTheme {
            CompositionLocalProvider(LocalModalWindowHeight provides 2000.dp) {
                Box(Modifier.width(800.dp)) { Backdrop(image) }
            }
        }
    }

    @Test fun wideImageFillsA16to9Area() {
        showBackdrop(BackdropImage("x", poster = false))
        assertEquals(450f, rule.onNodeWithTag("detail-backdrop").fetchSemanticsNode().boundsInRoot.height, 1f)
        rule.onAllNodesWithTag("detail-poster").assertCountEquals(0)
    }

    @Test fun posterIsShownWhole() {
        showBackdrop(BackdropImage("x", poster = true))
        rule.onAllNodesWithTag("detail-poster").assertCountEquals(1)
    }

    @Test fun withoutImageOnlyACalmStrip() {
        showBackdrop(null)
        assertEquals(72f, rule.onNodeWithTag("detail-backdrop").fetchSemanticsNode().boundsInRoot.height, 1f)
    }
}
