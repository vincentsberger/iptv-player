package de.vincentsberger.iptvplayer.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nur das Länderkürzel – billig genug für ganze Senderlisten auf dem Fire TV. */
class ProviderTagOfTest {

    @Test
    fun sameTagAsTheFullSplit() {
        for (name in listOf("DE: Das Erste HD", "| UK | SKY MIX ᴿᴬᵂ", "AT - ORF 1", "Das Erste", "4K: Kino")) {
            assertEquals(splitProviderTag(name).tag, providerTagOf(name), name)
        }
        assertNull(providerTagOf("ZDF"))
    }

    @Test
    fun mixedTagsStopsAtTheSecondTag() {
        fun ch(name: String) = Item(name, name, null, "1", "u$name", Section.LIVE)
        assertFalse(hasMixedProviderTags(listOf(ch("DE: A"), ch("DE: B"), ch("C"))))
        assertTrue(hasMixedProviderTags(listOf(ch("DE: A"), ch("AT: B"))))
    }
}
