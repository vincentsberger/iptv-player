package de.vincentsberger.iptvplayer.shared.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
class PlatformIosTest {

    private fun tempFileWith(text: String): String {
        val path = NSTemporaryDirectory() + "test-" + NSUUID().UUIDString + ".txt"
        NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        return path
    }

    private fun tempEntries(): Int =
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(NSTemporaryDirectory(), error = null)?.size ?: 0

    @Test
    fun crlfAndMissingTrailingNewlineAreHandled() {
        val path = tempFileWith("#EXTM3U\r\n#EXTINF:-1,Eins\r\nhttp://a/1")
        assertEquals(listOf("#EXTM3U", "#EXTINF:-1,Eins", "http://a/1"), readFileLines(path) { it.toList() })
    }

    @Test
    fun invalidAddressErrorNeverShowsTheCredentials() = runTest {
        val e = assertFailsWith<TvException> {
            PlatformHttp.getText("http://1.0.2.2:80990/player_api.php?username=demo&password=geheim")
        }
        val msg = e.message.orEmpty()
        assertFalse("geheim" in msg, msg)
        assertFalse("username" in msg, msg)
    }

    @Test
    fun addressWithSpaceIsRejectedWithoutCredentials() = runTest {
        val e = assertFailsWith<TvException> {
            PlatformHttp.getText("http://anbieter example/get.php?username=demo&password=geheim")
        }
        // Ab iOS 17 kodiert NSURL Leerzeichen selbst → dann scheitert erst die Namensauflösung. Beides ist ok, Zugangsdaten nie.
        assertTrue(e.message in setOf(HttpErrors.INVALID_ADDRESS, HttpErrors.UNREACHABLE), e.message)
        assertFalse("geheim" in e.message.orEmpty())
    }

    @Test
    fun tempFileIsDeletedAfterReading() = runTest {
        val source = tempFileWith("a\nb")
        val before = tempEntries()
        val lines = httpReadLines(NSURL.fileURLWithPath(source).absoluteString!!) { it.toList() }
        assertEquals(listOf("a", "b"), lines)
        assertEquals(before, tempEntries(), "Download-Kopie muss nach dem Lesen gelöscht sein")
    }

    @Test
    fun currentTimeIsEpochMillis() {
        assertTrue(currentTimeMillis() > 1_700_000_000_000)
    }
}
