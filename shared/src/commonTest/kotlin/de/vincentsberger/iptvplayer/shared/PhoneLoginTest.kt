package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Account
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneLoginTest {

    @Test
    fun formBodyIsDecodedIncludingUmlautsAndSpaces() {
        val form = PhoneLogin.parseForm("type=xtream&server=http%3A%2F%2Fsrv%3A8080&user=J%C3%BCrgen+M&pass=a%26b%3Dc")
        assertEquals(mapOf("type" to "xtream", "server" to "http://srv:8080", "user" to "Jürgen M", "pass" to "a&b=c"), form)
    }

    @Test
    fun brokenPercentSequencesStayAsTheyAre() {
        assertEquals("100%", PhoneLogin.urlDecode("100%"))
        assertEquals("%zz", PhoneLogin.urlDecode("%zz"))
        assertEquals(mapOf("flag" to ""), PhoneLogin.parseForm("flag"))
    }

    @Test
    fun xtreamAccountIsTrimmed() {
        val r = PhoneLogin.accountFrom(mapOf("type" to "xtream", "server" to " srv:8080 ", "user" to "u ", "pass" to " p"))
        assertEquals(PhoneLogin.Result.Ok(Account.Xtream("srv:8080", "u", "p")), r)
    }

    @Test
    fun m3uAccount() {
        val r = PhoneLogin.accountFrom(mapOf("type" to "m3u", "url" to "http://srv/list.m3u"))
        assertEquals(PhoneLogin.Result.Ok(Account.M3u("http://srv/list.m3u")), r)
    }

    @Test
    fun missingFieldsGiveAMessageForThePhone() {
        val r = PhoneLogin.accountFrom(mapOf("type" to "xtream", "server" to "srv", "user" to "", "pass" to "p"))
        assertTrue(r is PhoneLogin.Result.Invalid)
        assertTrue(PhoneLogin.accountFrom(mapOf("type" to "m3u")) is PhoneLogin.Result.Invalid)
    }

    @Test
    fun errorMessageIsEscapedInThePage() {
        val html = PhoneLogin.formPage("<b>x</b>")
        assertTrue("&lt;b&gt;x&lt;/b&gt;" in html)
        assertTrue("<b>x</b>" !in html)
    }
}
