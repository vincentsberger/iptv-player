package de.tvplayer.desktop.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.tvplayer.shared.data.Account
import de.tvplayer.shared.data.TvException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val logins = mutableListOf<Account>()

    private fun show(onLogin: suspend (Account) -> Unit = { logins += it }) =
        rule.setContent { AppTheme { LoginScreen(onLogin) } }

    private fun clickLogin() = rule.onNode(hasText("Anmelden") and hasClickAction()).performClick()

    @Test
    fun emptyXtreamFormShowsHintAndDoesNotLogIn() {
        show()
        clickLogin()
        rule.onNodeWithText("Bitte Server, Benutzername und Passwort eingeben.").assertExists()
        assertEquals(emptyList(), logins)
    }

    @Test
    fun xtreamLoginPassesTrimmedCredentials() {
        show()
        rule.onNodeWithTag("field:Server").performTextInput("  tv.example:8080 ")
        rule.onNodeWithTag("field:Benutzername").performTextInput(" max ")
        rule.onNodeWithTag("field:Passwort").performTextInput("geheim")
        clickLogin()
        rule.waitForIdle()
        assertEquals(listOf<Account>(Account.Xtream("tv.example:8080", "max", "geheim")), logins)
    }

    @Test
    fun m3uModeValidatesAndLogsIn() {
        show()
        rule.onNodeWithText("M3U-Playlist").performClick()
        clickLogin()
        rule.onNodeWithText("Bitte den Link zur Playlist eingeben.").assertExists()

        rule.onNodeWithTag("field:Link zur M3U-Playlist").performTextInput("http://tv.example/list.m3u")
        clickLogin()
        rule.waitForIdle()
        assertEquals(listOf<Account>(Account.M3u("http://tv.example/list.m3u")), logins)
        rule.onNodeWithText("Bitte den Link zur Playlist eingeben.").assertDoesNotExist()
    }

    @Test
    fun serverErrorIsShown() {
        show { throw TvException("Benutzername oder Passwort falsch") }
        rule.onNodeWithTag("field:Server").performTextInput("srv")
        rule.onNodeWithTag("field:Benutzername").performTextInput("u")
        rule.onNodeWithTag("field:Passwort").performTextInput("p")
        clickLogin()
        rule.onNodeWithText("Benutzername oder Passwort falsch").assertExists()
        rule.onNode(hasText("Anmelden") and hasClickAction()).assertExists()
    }

    @Test
    fun buttonShowsProgressAndIgnoresDoubleClicks() {
        val pending = CompletableDeferred<Unit>()
        var calls = 0
        show { calls++; pending.await() }
        rule.onNodeWithText("M3U-Playlist").performClick()
        rule.onNodeWithTag("field:Link zur M3U-Playlist").performTextInput("http://tv.example/list.m3u")
        clickLogin()

        rule.onNodeWithText("Wird geprüft …").assertExists().performClick()
        assertEquals(1, calls)

        pending.complete(Unit)
        rule.waitForIdle()
        rule.onNodeWithText("Wird geprüft …").assertDoesNotExist()
    }
}
