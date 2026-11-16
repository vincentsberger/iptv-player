package de.vincentsberger.iptvplayer.shared.data

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

/** Speicher auf Android: SharedPreferences. */
class AndroidKeyValueStore(context: android.content.Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("iptvplayer", android.content.Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
    override fun clear() = prefs.edit().clear().apply()
}
