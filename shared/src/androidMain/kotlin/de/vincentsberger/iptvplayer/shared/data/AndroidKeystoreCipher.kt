package de.vincentsberger.iptvplayer.shared.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.annotation.TargetApi
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM für die App-Daten (Umschlag-Verfahren): Ein zufälliger Datenschlüssel verschlüsselt die Werte in
 * Software – schnell, ohne Keystore-Aufruf je Lesen. Er selbst liegt nur eingepackt in eigenen Prefs; einpacken und
 * auspacken kann ihn allein ein Schlüssel im Android Keystore, der das Gerät nie verlässt.
 */
@TargetApi(Build.VERSION_CODES.M) // nur über AndroidKeyValueStore.secure, das die Version prüft
class AndroidKeystoreCipher private constructor(private val key: SecretKey) : StringCipher {

    override fun encrypt(plain: String): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return Base64.encodeToString(iv + c.doFinal(plain.encodeToByteArray()), Base64.NO_WRAP)
    }

    override fun decrypt(cipherText: String): String? = try {
        val bytes = Base64.decode(cipherText, Base64.NO_WRAP)
        if (bytes.size <= IV_BYTES) null else {
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            c.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
        }
    } catch (e: GeneralSecurityException) {
        null // falscher Schlüssel oder verändert (GCM prüft das)
    } catch (e: IllegalArgumentException) {
        null // kein gültiges Base64
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEYSTORE = "AndroidKeyStore"
        private const val WRAP_ALIAS = "iptvplayer.data-key-wrap"
        private const val KEY_PREFS = "iptvplayer.key"
        private const val WRAPPED_KEY = "dataKey"
        private val random = SecureRandom()

        /**
         * Datenschlüssel holen oder neu anlegen; null, wenn der Keystore auf dem Gerät nicht mitspielt. Lässt sich ein
         * gespeicherter Schlüssel nicht mehr auspacken (Gerätewechsel, Keystore zurückgesetzt), gibt es einen neuen –
         * die alten Werte sind dann nicht mehr lesbar und gelten als fehlend.
         */
        fun create(context: Context): AndroidKeystoreCipher? = try {
            val prefs = context.getSharedPreferences(KEY_PREFS, Context.MODE_PRIVATE)
            val wrapKey = wrapKey()
            val dataKey = prefs.getString(WRAPPED_KEY, null)?.let { unwrap(wrapKey, it) }
                ?: ByteArray(32).also { random.nextBytes(it) }.also { fresh ->
                    // commit statt apply: Der Schlüssel muss auf dem Speicher liegen, bevor damit etwas verschlüsselt wird.
                    check(prefs.edit().putString(WRAPPED_KEY, wrap(wrapKey, fresh)).commit()) { "Schlüssel nicht gespeichert" }
                }
            AndroidKeystoreCipher(SecretKeySpec(dataKey, "AES"))
        } catch (e: Exception) {
            null
        }

        private fun wrapKey(): SecretKey {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (ks.getKey(WRAP_ALIAS, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(WRAP_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
                generateKey()
            }
        }

        /** Der Keystore wählt den IV selbst (vorgegebene IVs lehnt er ab); er steht vorn im Ergebnis. */
        private fun wrap(wrapKey: SecretKey, dataKey: ByteArray): String {
            val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrapKey) }
            return Base64.encodeToString(c.iv + c.doFinal(dataKey), Base64.NO_WRAP)
        }

        private fun unwrap(wrapKey: SecretKey, wrapped: String): ByteArray? = try {
            val bytes = Base64.decode(wrapped, Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            c.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
