package de.vincentsberger.iptvplayer.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenTest {

    @Test
    fun onlyWindowsNeedsBorderlessFullscreen() {
        assertTrue(needsBorderlessFullscreen("Windows 11"))
        assertTrue(needsBorderlessFullscreen("Windows 10"))
        assertFalse(needsBorderlessFullscreen("Linux"))
        assertFalse(needsBorderlessFullscreen("Mac OS X"))
    }
}
