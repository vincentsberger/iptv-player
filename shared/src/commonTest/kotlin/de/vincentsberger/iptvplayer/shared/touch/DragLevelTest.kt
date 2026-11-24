package de.vincentsberger.iptvplayer.shared.touch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DragLevelTest {
    @Test
    fun zonesByThirds() {
        assertEquals(DragZone.BRIGHTNESS, dragZone(50f, 900f))
        assertEquals(DragZone.NONE, dragZone(450f, 900f))
        assertEquals(DragZone.VOLUME, dragZone(850f, 900f))
        assertEquals(DragZone.NONE, dragZone(10f, 0f))
    }

    @Test
    fun halfTheHeightIsTheFullRange() {
        assertEquals(1f, dragLevel(0.5f, -200f, 800f)) // 200 px nach oben = +50 %
        assertEquals(0f, dragLevel(0.5f, 400f, 800f), "nicht unter 0")
        assertEquals(0.75f, dragLevel(0.5f, -100f, 800f))
    }

    @Test
    fun onlyClearlyVerticalDragsCount() {
        assertTrue(isVerticalDrag(10f, 60f))
        assertFalse(isVerticalDrag(80f, 60f))
    }
}
