package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.vincentsberger.iptvplayer.shared.data.Account
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ein Xtream-Link im Server-Feld (eingefügt) füllt den Desktop-Login aus; im M3U-Feld bleibt er, wie er ist. */
class LoginPasteTest {

    @get:Rule
    val rule = createComposeRule()

    private val logins = mutableListOf<Account>()

    private fun show() = rule.setContent { AppTheme { LoginScreen({ logins += it }) } }
    private fun clickLogin() = rule.onNode(hasText("Anmelden") and hasClickAction()).performClick()

    @Test
    fun xtreamLinkInServerFieldFillsEverything() {
        show()
        rule.onNodeWithTag("field:Server").performTextInput("http://tv.example.com:8080/get.php?username=wohnzimmer&password=geheim&type=m3u_plus")
        clickLogin()
        rule.waitForIdle()
        assertEquals(listOf<Account>(Account.Xtream("http://tv.example.com:8080", "wohnzimmer", "geheim")), logins)
    }

    @Test
    fun m3uFieldKeepsTheLinkAsIs() {
        show()
        rule.onNode(hasText("M3U-Playlist") and hasClickAction()).performClick()
        val link = "http://tv.example.com:8080/get.php?username=u&password=p&type=m3u_plus"
        rule.onNodeWithTag("field:Link zur M3U-Playlist").performTextInput(link)
        clickLogin()
        rule.waitForIdle()
        assertEquals(listOf<Account>(Account.M3u(link)), logins)
    }
}
