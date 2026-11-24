package de.vincentsberger.iptvplayer.shared

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Lesbarkeit aus 3 m Abstand: Jede Palette muss diese Kontraste (WCAG 2.x) halten – sonst scheitert der Test,
 * bevor eine neue Farbe auf dem Fernseher landet.
 */
class PaletteTest {

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Long, b: Long): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertContrast(name: String, fg: Long, bg: Long, min: Double = 4.5) {
        val c = contrast(fg, bg)
        assertTrue(c >= min, "$name: ${(c * 100).toInt() / 100.0}:1, nötig $min:1")
    }

    @Test
    fun textIsReadableOnEverySurface() = with(Palette) {
        listOf(BgDeep, Bg, Surface0, Surface1, Surface2).forEach { surface ->
            assertContrast("Text", Text, surface)
            assertContrast("TextSoft", TextSoft, surface)
            assertContrast("Muted", Muted, surface)
        }
    }

    @Test
    fun accentAndErrorsStayReadableAsText() = with(Palette) {
        listOf(Bg, Surface1, Surface2).forEach { surface ->
            assertContrast("Signal", Signal, surface)
            assertContrast("Danger", Danger, surface)
        }
        assertContrast("OnSignal auf Signal", OnSignal, Signal)
    }

    @Test
    fun focusStandsOutFromTheListAndCarriesText() = with(Palette) {
        assertContrast("Fokustext", FocusFg, FocusBg, min = 7.0)
        // Die Fokuskarte muss sich deutlich vom Rest abheben – das stärkste Signal auf dem Bildschirm.
        assertContrast("Fokus zu Karte", FocusBg, Surface1, min = 7.0)
    }
}
