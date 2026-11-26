package de.vincentsberger.iptvplayer.desktop.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoOutputTest {
    @Test fun nativeOnlyWithTranslucentWindowsAndWithoutCompatibleMode() {
        assertTrue(useNativeVideo(compatibleMode = false, translucentWindows = true))
        assertFalse(useNativeVideo(compatibleMode = true, translucentWindows = true))
        assertFalse(useNativeVideo(compatibleMode = false, translucentWindows = false))
    }

    @Test fun fitKeepsVlcDefaults() = assertEquals(VlcScaling(aspect = null, crop = null), vlcScaling("fit", 1920, 1080))

    @Test fun fillStretchesToTheWindowShape() = assertEquals(VlcScaling(aspect = "1920:800", crop = null), vlcScaling("fill", 1920, 800))

    @Test fun zoomCropsToTheWindowShape() = assertEquals(VlcScaling(aspect = null, crop = "1920:800"), vlcScaling("zoom", 1920, 800))

    @Test fun unknownWindowSizeKeepsDefaults() = assertNull(vlcScaling("zoom", 0, 0).crop)
}
