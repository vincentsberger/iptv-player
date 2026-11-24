package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Account
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** „Link mit Zugangsdaten einfügen füllt alles aus“ (Desktop-Login): Xtream-Daten aus einem M3U-/API-Link. */
class XtreamLinkTest {

    @Test
    fun getPhpLink() {
        assertEquals(
            Account.Xtream("http://tv.example.com:8080", "wohnzimmer", "geheim1234"),
            xtreamFromLink("http://tv.example.com:8080/get.php?username=wohnzimmer&password=geheim1234&type=m3u_plus&output=ts"),
        )
    }

    @Test
    fun playerApiAndHttps() {
        assertEquals(
            Account.Xtream("https://tv.example.com", "u", "p&q"),
            xtreamFromLink("  https://tv.example.com/player_api.php?password=p%26q&username=u  "),
        )
    }

    @Test
    fun notAnXtreamLink() {
        assertNull(xtreamFromLink("http://tv.example.com/get.php?password=p"), "ohne Benutzer")
        assertNull(xtreamFromLink("http://tv.example.com/list.m3u"))
        assertNull(xtreamFromLink("irgendein Text"))
    }

    @Test
    fun keepsServerSubPath() {
        assertEquals(
            Account.Xtream("http://tv.example.com/xtream", "u", "p"),
            xtreamFromLink("http://tv.example.com/xtream/get.php?username=u&password=p"),
        )
    }
}
