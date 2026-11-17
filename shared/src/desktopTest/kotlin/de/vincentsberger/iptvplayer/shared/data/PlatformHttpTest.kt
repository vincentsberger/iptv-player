package de.vincentsberger.iptvplayer.shared.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PlatformHttpTest {

    @Test
    fun invalidAddressErrorNeverShowsTheCredentials() = runTest {
        // Port > 65535: OkHttp lehnt die URL ab. Die Meldung landet auf dem Bildschirm – ohne Passwort.
        val e = assertFailsWith<TvException> {
            PlatformHttp.getText("http://1.0.2.2:80990/player_api.php?username=demo&password=geheim")
        }
        val msg = e.message.orEmpty()
        assertFalse("geheim" in msg, msg)
        assertFalse("username" in msg, msg)
    }
}
