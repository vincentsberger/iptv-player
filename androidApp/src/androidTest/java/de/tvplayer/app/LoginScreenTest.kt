package de.tvplayer.app

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.tvplayer.app.ui.AppTheme
import de.tvplayer.app.ui.LoginScreen
import de.tvplayer.shared.data.Account
import de.tvplayer.shared.data.TvException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule val rule = createComposeRule()

    private val logins = mutableListOf<Account>()

    private fun show(onLogin: suspend (Account) -> Unit = { logins += it }) {
        leaveTouchMode()
        rule.setContent { AppTheme { LoginScreen(onLogin) } }
    }

    private fun clickLogin() = rule.onNode(hasText("Anmelden") and hasClickAction()).select()

    @Test
    fun serverFieldHasFocusForTheRemote() {
        show()
        rule.onNodeWithTag("login:server").assertIsFocused()
    }

    @Test
    fun emptyXtreamFormShowsHint() {
        show()
        clickLogin()
        rule.onNodeWithText("Bitte Server, Benutzername und Passwort eingeben.").assertExists()
        assertTrue(logins.isEmpty())
    }

    @Test
    fun emptyM3uFormShowsHint() {
        show()
        rule.onNodeWithText("M3U-Playlist").select()
        clickLogin()
        rule.onNodeWithText("Bitte den Link zur Playlist eingeben.").assertExists()
    }

    @Test
    fun xtreamLoginPassesTrimmedValues() {
        show()
        rule.onNodeWithTag("login:server").performTextInput(" tv.example:8080 ")
        rule.onNodeWithTag("login:user").performTextInput("max ")
        rule.onNodeWithTag("login:pass").performTextInput("geheim")
        clickLogin()
        rule.waitForIdle()
        assertEquals(listOf(Account.Xtream("tv.example:8080", "max", "geheim")), logins)
    }

    @Test
    fun m3uLoginPassesTheLink() {
        show()
        rule.onNodeWithText("M3U-Playlist").select()
        rule.onNodeWithTag("login:m3u").performTextInput("http://tv.example/list.m3u")
        clickLogin()
        rule.waitForIdle()
        assertEquals(listOf(Account.M3u("http://tv.example/list.m3u")), logins)
    }

    @Test
    fun loginErrorFromServerIsShown() {
        show { throw TvException("Benutzername oder Passwort falsch") }
        rule.onNodeWithTag("login:server").performTextInput("srv")
        rule.onNodeWithTag("login:user").performTextInput("u")
        rule.onNodeWithTag("login:pass").performTextInput("p")
        clickLogin()
        rule.onNodeWithText("Benutzername oder Passwort falsch").assertExists()
    }

    @Test
    fun buttonShowsProgressWhileChecking() {
        val pending = CompletableDeferred<Unit>()
        show { pending.await() }
        rule.onNodeWithText("M3U-Playlist").select()
        rule.onNodeWithTag("login:m3u").performTextInput("http://tv.example/list.m3u")
        clickLogin()
        rule.onNodeWithText("Wird geprüft …").assertExists()

        pending.complete(Unit)
        rule.waitForIdle()
        rule.onNode(hasText("Anmelden") and hasClickAction()).assertExists()
    }

    @Test
    fun plainHttpServerShowsEncryptionHint() {
        show()
        rule.onNodeWithText("unverschlüsselt", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("login:server").performTextInput("http://tv.example")
        rule.onNodeWithText("unverschlüsselt", substring = true).assertExists()
    }

    @Test
    fun httpsServerShowsNoHint() {
        show()
        rule.onNodeWithTag("login:server").performTextInput("https://tv.example")
        rule.onNodeWithText("unverschlüsselt", substring = true).assertDoesNotExist()
    }
}
