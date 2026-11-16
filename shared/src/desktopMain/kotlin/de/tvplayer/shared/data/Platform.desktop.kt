package de.tvplayer.shared.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Gemeinsamer HTTP-Client (auch für den Player nutzbar). */
val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
    .build()

actual suspend fun httpGetText(url: String): String = httpReadLines(url) { it.joinToString("\n") }

actual suspend fun <T> httpReadLines(url: String, block: (Sequence<String>) -> T): T = withContext(Dispatchers.IO) {
    val request = try {
        Request.Builder().url(url).build()
    } catch (e: IllegalArgumentException) {
        throw TvException("Ungültige Adresse: $url")
    }
    val response = try {
        httpClient.newCall(request).execute()
    } catch (e: java.io.IOException) {
        throw TvException("Server nicht erreichbar. Prüfe Adresse und Internetverbindung.")
    }
    response.use { r ->
        if (!r.isSuccessful) throw TvException("Server antwortet mit Fehler ${r.code}")
        val body = r.body ?: throw TvException("Leere Antwort vom Server")
        body.charStream().buffered().use { block(it.lineSequence()) }
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

/**
 * Speicher auf dem PC: eine JSON-Datei
 * (Windows: %APPDATA%\TVPlayer\data.json, sonst ~/.tvplayer/data.json).
 */
class FileKeyValueStore : KeyValueStore {
    private val dir = (System.getenv("APPDATA")?.let { java.io.File(it, "TVPlayer") }
        ?: java.io.File(System.getProperty("user.home"), ".tvplayer")).apply { mkdirs() }
    private val file = java.io.File(dir, "data.json")
    private val map: MutableMap<String, String> = try {
        (parseJson(file.readText()) as? kotlinx.serialization.json.JsonObject)
            ?.mapValues { (it.value as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "" }
            ?.toMutableMap() ?: mutableMapOf()
    } catch (e: Exception) {
        mutableMapOf()
    }

    override fun getString(key: String): String? = synchronized(map) { map[key] }
    override fun putString(key: String, value: String?) = synchronized(map) {
        if (value == null) map.remove(key) else map[key] = value
        persist()
    }
    override fun clear() = synchronized(map) { map.clear(); persist() }

    private fun persist() {
        val json = kotlinx.serialization.json.JsonObject(map.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })
        val tmp = java.io.File(dir, "data.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}
