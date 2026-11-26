package de.vincentsberger.iptvplayer.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ItemTaggedTest {
    private fun item(name: String) = Item("1", name, null, "c", "u", Section.MOVIES)

    @Test fun taggedIsTheSplitName() {
        val raw = "|DE| Das Boot ᴴᴰ (1981)"
        assertEquals(splitProviderTag(raw), item(raw).tagged)
    }

    @Test fun taggedIsComputedOnce() {
        val i = item("|DE| Das Boot (1981)")
        assertSame(i.tagged, i.tagged)
    }

    @Test fun copyWithNewNameSplitsTheNewName() {
        val renamed = item("|DE| Das Boot (1981)").copy(name = "|AT| Kommissar Rex (1994)")
        assertEquals("AT", renamed.tagged.tag)
        assertEquals("1994", renamed.tagged.year)
    }
}
