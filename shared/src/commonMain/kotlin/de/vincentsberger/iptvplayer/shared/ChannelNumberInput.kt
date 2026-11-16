package de.vincentsberger.iptvplayer.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Senderwahl per Zifferntasten wie bei einer TV-Fernbedienung: Ziffern sammeln („1“, „12“, „125“),
 * nach kurzer Pause übernimmt die Oberfläche die Nummer mit [take]. Mehr als [maxDigits] Ziffern beginnen von vorn.
 */
class ChannelNumberInput(private val maxDigits: Int = 4) {
    var digits by mutableStateOf(""); private set

    fun type(digit: Int) {
        require(digit in 0..9)
        digits = if (digits.length >= maxDigits) "$digit" else digits + digit
    }

    /** Gesammelte Nummer (1-basiert) übernehmen und zurücksetzen; null, wenn nichts oder nur „0“ eingegeben wurde. */
    fun take(): Int? {
        val n = digits.toIntOrNull()
        digits = ""
        return n?.takeIf { it > 0 }
    }

    /** Index in einer Liste mit [size] Einträgen für Sendernummer [number], oder null, wenn es sie nicht gibt. */
    companion object {
        fun indexFor(number: Int, size: Int): Int? = (number - 1).takeIf { it in 0 until size }
    }
}
