package de.vincentsberger.iptvplayer.shared.testing

import de.vincentsberger.iptvplayer.shared.data.HttpSource
import de.vincentsberger.iptvplayer.shared.data.KeyValueStore

/** Speicher im Arbeitsspeicher statt SharedPreferences/JSON-Datei. */
class InMemoryKeyValueStore : KeyValueStore {
    val values = HashMap<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
    override fun clear() = values.clear()
}

/** HTTP-Attrappe: beantwortet jede URL über [responder] und merkt sich alle Anfragen. */
class FakeHttp(private val responder: (String) -> String) : HttpSource {
    val requests = mutableListOf<String>()

    override suspend fun getText(url: String): String {
        requests += url
        return responder(url)
    }

    override suspend fun <T> readLines(url: String, block: (Sequence<String>) -> T): T =
        block(getText(url).lineSequence())

    companion object {
        /** Antworten je Xtream-`action`; die Anmeldung (ohne action) steht unter "login". */
        fun xtream(responses: Map<String, String>) = FakeHttp { url ->
            responses[actionOf(url)] ?: error("Unerwartete Anfrage: $url")
        }

        /** Xtream-`action` einer URL; die Anmeldung (ohne action) heißt "login". */
        fun actionOf(url: String): String = url.substringAfter("action=", "").substringBefore('&').ifEmpty { "login" }

        const val LOGIN_OK = """{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["ts"]}}"""
    }
}
