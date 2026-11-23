package de.vincentsberger.iptvplayer.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.data.Item

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

        /**
         * Sender zur Nummer: Anbieter-Nummer, zuerst in der aktuellen Liste, sonst in allen sichtbaren Sendern.
         * Hat der Anbieter keine Nummern, gilt wie bisher die Position in der aktuellen Liste.
         */
        fun find(number: Int, current: List<Item>, all: List<Item>): ChannelPick? {
            if (all.none { it.number != null } && current.none { it.number != null }) {
                return indexFor(number, current.size)?.let { ChannelPick(current, it) }
            }
            current.indexOfFirst { it.number == number }.takeIf { it >= 0 }?.let { return ChannelPick(current, it) }
            all.indexOfFirst { it.number == number }.takeIf { it >= 0 }?.let { return ChannelPick(all, it) }
            return null
        }
    }
}

/** Ergebnis der Ziffernwahl: in welcher Liste an welcher Stelle der Sender steht. */
data class ChannelPick(val list: List<Item>, val index: Int)

/** Angezeigte Sendernummer: die des Anbieters, sonst die Position in [list] (1-basiert). */
fun channelNumberOf(item: Item, list: List<Item>): Int =
    item.number ?: (list.indexOfFirst { it.url == item.url } + 1)
