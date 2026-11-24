package de.vincentsberger.iptvplayer.shared.data

/** Verschlüsselt einzelne Texte. [decrypt] wirft nie: null heißt „nicht lesbar“ (falscher Schlüssel, beschädigt). */
interface StringCipher {
    fun encrypt(plain: String): String
    fun decrypt(cipherText: String): String?
}

/**
 * Legt jeden Wert verschlüsselt in [inner] ab (Android: SharedPreferences, Schlüssel im Keystore). Werte, die noch
 * im Klartext liegen ([existingKeys], z. B. aus einer älteren Version), werden beim Erzeugen einmal verschlüsselt.
 * Ein Wert, der sich nicht mehr entschlüsseln lässt (Schlüssel verloren, Gerätewechsel), gilt als fehlend – die App
 * fragt dann neu nach der Anmeldung, statt abzustürzen.
 */
class EncryptingKeyValueStore(
    private val inner: KeyValueStore,
    private val cipher: StringCipher,
    existingKeys: Collection<String>,
) : KeyValueStore {

    init {
        existingKeys.forEach { key ->
            val value = inner.getString(key)
            if (value != null && !value.startsWith(PREFIX)) inner.putString(key, seal(value))
        }
    }

    override fun getString(key: String): String? {
        val value = inner.getString(key) ?: return null
        return if (value.startsWith(PREFIX)) cipher.decrypt(value.removePrefix(PREFIX)) else value
    }

    override fun putString(key: String, value: String?) = inner.putString(key, value?.let(::seal))

    override fun clear() = inner.clear()

    private fun seal(plain: String) = PREFIX + cipher.encrypt(plain)

    companion object {
        /** Markiert verschlüsselte Werte; die Ziffer erlaubt später ein anderes Verfahren. */
        const val PREFIX = "enc1:"

        /**
         * Ohne Schlüssel (Keystore auf dem Gerät defekt) sind verschlüsselte Werte nur noch Datenmüll: entfernen,
         * damit der Speicher unverschlüsselt weiterläuft und die App nach der Anmeldung fragt.
         */
        fun dropUnreadable(store: KeyValueStore, keys: Collection<String>) {
            keys.forEach { if (store.getString(it)?.startsWith(PREFIX) == true) store.putString(it, null) }
        }
    }
}
