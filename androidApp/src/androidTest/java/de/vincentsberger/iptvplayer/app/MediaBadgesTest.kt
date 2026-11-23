package de.vincentsberger.iptvplayer.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.app.ui.AppTheme
import de.vincentsberger.iptvplayer.app.ui.MediaBadges
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaBadgesTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun showsBadges() {
        rule.setContent { AppTheme { MediaBadges(listOf("FHD", "5.1", "DE", "UT")) } }
        listOf("FHD", "5.1", "DE", "UT").forEach { rule.onNodeWithText(it).assertExists() }
    }
}
