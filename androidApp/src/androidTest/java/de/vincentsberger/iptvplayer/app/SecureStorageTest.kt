package de.vincentsberger.iptvplayer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.vincentsberger.iptvplayer.shared.data.AndroidKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Echter Android Keystore: Zugangsdaten liegen nie im Klartext in den SharedPreferences. */
@RunWith(AndroidJUnit4::class)
class SecureStorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val raw get() = context.getSharedPreferences("iptvplayer", Context.MODE_PRIVATE)
    private val keyPrefs get() = context.getSharedPreferences("iptvplayer.key", Context.MODE_PRIVATE)

    @Before
    fun reset() {
        raw.edit().clear().commit()
        keyPrefs.edit().clear().commit()
    }

    @Test
    fun valueIsEncryptedAndReadableAfterRestart() {
        AndroidKeyValueStore.secure(context).putString("accounts", """[{"pass":"geheim123"}]""")
        Thread.sleep(200) // apply() schreibt im Hintergrund
        assertFalse(raw.getString("accounts", "")!!.contains("geheim123"))
        assertEquals("""[{"pass":"geheim123"}]""", AndroidKeyValueStore.secure(context).getString("accounts"))
    }

    @Test
    fun plainValueFromOlderVersionIsEncryptedOnStart() {
        raw.edit().putString("accounts", "geheim123").commit()
        val store = AndroidKeyValueStore.secure(context)
        Thread.sleep(200)
        assertFalse(raw.getString("accounts", "")!!.contains("geheim123"))
        assertEquals("geheim123", store.getString("accounts"))
    }

    @Test
    fun lostDataKeyMeansLoginAgainNotCrash() {
        AndroidKeyValueStore.secure(context).putString("accounts", "geheim123")
        Thread.sleep(200)
        keyPrefs.edit().clear().commit() // wie nach einem Gerätewechsel: Werte da, Schlüssel weg
        assertNull(AndroidKeyValueStore.secure(context).getString("accounts"))
    }
}
