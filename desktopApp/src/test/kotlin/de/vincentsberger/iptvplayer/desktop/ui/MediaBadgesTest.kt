package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import kotlin.test.Test

class MediaBadgesTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsBadges() {
        rule.setContent { AppTheme { MediaBadges(listOf("FHD", "5.1", "DE", "UT")) } }
        listOf("FHD", "5.1", "DE", "UT").forEach { rule.onNodeWithText(it).assertExists() }
    }
}
