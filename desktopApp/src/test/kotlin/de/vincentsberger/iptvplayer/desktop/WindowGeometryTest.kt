package de.vincentsberger.iptvplayer.desktop

import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fenstergröße beim Start, gemerkte Lage und Fenstertitel (Review-Paket D: H8, K12). */
class WindowGeometryTest {

    private val fullHd = Bounds(0, 0, 1920, 1040)

    /** Full HD mit 150 % und Taskleiste: nutzbar etwa 1280×672 dp. */
    private val laptop150 = Bounds(0, 0, 1280, 672)

    @Test
    fun startSizeIsCappedOnLargeScreens() {
        assertEquals(1400 to 860, initialSize(fullHd))
    }

    @Test
    fun startSizeFitsALaptopWith150Percent() {
        val (w, h) = initialSize(laptop150)
        assertTrue(w <= 1280 && h <= 672, "passt ganz auf den Bildschirm: ${w}×$h")
        assertTrue(h >= MIN_WINDOW_HEIGHT)
        assertEquals(1000 to 600, minimumSize(laptop150))
    }

    @Test
    fun minimumNeverExceedsTinyScreens() {
        assertEquals(900 to 500, minimumSize(Bounds(0, 0, 900, 500)))
        assertEquals(900 to 500, initialSize(Bounds(0, 0, 900, 500)))
    }

    @Test
    fun firstStartIsCenteredInTheWorkArea() {
        val b = startBounds(null, Bounds(0, 40, 1920, 1000), listOf(fullHd)).bounds
        assertEquals(Bounds(260, 110, 1400, 860), b)
    }

    @Test
    fun savedWindowIsRestoredWithMaximizedState() {
        val saved = SavedWindow(Bounds(100, 50, 1200, 700), maximized = true)
        assertEquals(saved, startBounds(saved, fullHd, listOf(fullHd)))
    }

    @Test
    fun windowOnAMissingMonitorComesBackCentered() {
        val saved = SavedWindow(Bounds(2500, 100, 1200, 700), maximized = false) // zweiter Monitor abgesteckt
        val b = startBounds(saved, fullHd, listOf(fullHd)).bounds
        assertEquals(1400, b.width)
        assertTrue(b.x in 0..fullHd.width - b.width)
    }

    @Test
    fun windowOnTheSecondMonitorStays() {
        val second = Bounds(1920, 0, 1920, 1080)
        val saved = SavedWindow(Bounds(2500, 100, 1200, 700), maximized = false)
        assertEquals(saved, startBounds(saved, fullHd, listOf(fullHd, second)))
    }

    @Test
    fun titleBarAboveTheScreenDoesNotCount() {
        assertFalse(Bounds(100, -200, 1200, 700).titleBarVisibleOn(fullHd))
        assertTrue(Bounds(100, -5, 1200, 700).titleBarVisibleOn(fullHd))
    }

    @Test
    fun tooLargeSavedWindowShrinksToTheWorkArea() {
        val saved = SavedWindow(Bounds(0, 0, 1900, 1000), maximized = false)
        val b = startBounds(saved, laptop150, listOf(laptop150)).bounds
        assertEquals(1280 to 672, b.width to b.height)
    }

    @Test
    fun prefsRoundTripAndIgnoreGarbage() {
        val kv = InMemoryKeyValueStore()
        val prefs = WindowPrefs(kv)
        assertNull(prefs.load())
        val w = SavedWindow(Bounds(-8, 20, 1300, 750), maximized = true)
        prefs.save(w)
        assertEquals(w, prefs.load())
        kv.putString("desktop.window", "1,2,abc")
        assertNull(prefs.load())
        assertNull(SavedWindow.decode("1,2,0,5,0"), "Größe 0 ist kein Fenster")
    }

    @Test
    fun titleNamesWhatIsPlaying() {
        assertEquals("IPTV Player", windowTitle(null))
        assertEquals("IPTV Player", windowTitle(" "))
        assertEquals("Das Erste – IPTV Player", windowTitle("Das Erste"))
    }
}
