package de.vincentsberger.iptvplayer.shared.data

import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptingKeyValueStoreTest {

    /** Attrappe: dreht den Text um und markiert ihn; alles ohne Marke gilt als nicht entschlüsselbar. */
    private object Reversing : StringCipher {
        override fun encrypt(plain: String) = "R" + plain.reversed()
        override fun decrypt(cipherText: String) = if (cipherText.startsWith("R")) cipherText.drop(1).reversed() else null
    }

    private val inner = InMemoryKeyValueStore()
    private fun store() = EncryptingKeyValueStore(inner, Reversing, inner.values.keys.toList())

    @Test
    fun valuesSurviveARoundTripAndAreNotStoredInPlainText() {
        store().putString("accounts", """[{"pass":"geheim123"}]""")
        assertEquals("""[{"pass":"geheim123"}]""", store().getString("accounts"))
        assertFalse(inner.values.getValue("accounts").contains("geheim123"))
    }

    @Test
    fun existingPlainValuesAreEncryptedOnStart() {
        inner.values["accounts"] = "geheim123"
        inner.values["volume"] = "80"
        val s = store()
        assertTrue(inner.values.values.none { it == "geheim123" || it == "80" })
        assertEquals("geheim123", s.getString("accounts"))
        assertEquals("80", s.getString("volume"))
    }

    @Test
    fun alreadyEncryptedValuesAreNotEncryptedTwice() {
        store().putString("pass", "geheim")
        val before = inner.values.getValue("pass")
        store()
        assertEquals(before, inner.values.getValue("pass"))
    }

    @Test
    fun unreadableValueCountsAsMissing() {
        inner.values["pass"] = EncryptingKeyValueStore.PREFIX + "kaputt"
        assertNull(store().getString("pass"))
    }

    @Test
    fun nullRemovesAndClearEmpties() {
        val s = store()
        s.putString("a", "1"); s.putString("b", "2")
        s.putString("a", null)
        assertNull(s.getString("a"))
        s.clear()
        assertTrue(inner.values.isEmpty())
    }

    @Test
    fun withoutCipherEncryptedValuesAreDroppedAndPlainOnesKept() {
        inner.values["pass"] = EncryptingKeyValueStore.PREFIX + "Rterceg"
        inner.values["volume"] = "80"
        EncryptingKeyValueStore.dropUnreadable(inner, inner.values.keys.toList())
        assertNull(inner.getString("pass"))
        assertEquals("80", inner.getString("volume"))
    }
}
