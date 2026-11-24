package de.vincentsberger.iptvplayer.shared.brand

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Taktung der Einschalt-Animation des Logos. */
class BrandMarkTest {

    @Test
    fun frameAtStartIsUndrawn() {
        val f = brandMarkFrame(0f)
        assertEquals(0f, f.drawn)
        assertEquals(0f, f.screenAlpha)
        assertEquals(0f, f.barAlpha)
    }

    @Test
    fun frameDrawnAt35Percent() = assertEquals(1f, brandMarkFrame(0.35f).drawn)

    @Test
    fun screenFlashesThenSettles() {
        val flash = brandMarkFrame(0.38f)
        assertEquals(1f, flash.screenScaleX)
        assertTrue(flash.screenScaleY <= 0.05f)
        assertEquals(1f, flash.screenAlpha)
        assertEquals(1f, brandMarkFrame(0.46f).screenScaleY)
        assertEquals(0.06f, brandMarkFrame(0.9f).screenAlpha)
    }

    @Test
    fun barVisibleFromHalf() {
        assertEquals(0f, brandMarkFrame(0.44f).barAlpha)
        assertEquals(1f, brandMarkFrame(0.5f).barAlpha)
    }

    @Test
    fun valuesStayInUnitRange() {
        for (i in 0..100) {
            val f = brandMarkFrame(i / 100f)
            listOf(f.drawn, f.screenScaleX, f.screenScaleY, f.screenAlpha, f.barAlpha).forEach { assertTrue(it in 0f..1f, "t=$i: $it") }
        }
    }

    @Test
    fun cycleRepeats() = assertEquals(brandMarkFrame(0.25f), brandMarkFrame(1.25f))
}
