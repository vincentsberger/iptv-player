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
        throw TvException("Die Server-Adresse ist ungültig. Bitte prüfen, z. B. anbieter.example:8080 (Port höchstens 65535).") // nie die URL zeigen: sie enthält Benutzername und Passwort
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

/**
 * Playlist-Zwischenspeicher auf Android: je Eintrag eine gzip-Datei im App-internen Speicher (`filesDir`, nicht
 * `cacheDir` – das System soll ihn nicht still leeren). Andere Apps können dort nicht lesen.
 */
class AndroidPlaylistCache(private val dir: java.io.File) : PlaylistCacheStore {

    override fun readText(key: String): String? = readBytes(key)?.decodeToString()

    override fun writeText(key: String, text: String) = write(key) { it.write(text.encodeToByteArray()) }

    override fun <T> readLines(key: String, block: (Sequence<String>) -> T): T? =
        readBytes(key)?.inputStream()?.bufferedReader()?.useLines(block)

    override fun writeLines(key: String, lines: Sequence<String>) = write(key) { out ->
        val w = out.bufferedWriter()
        lines.forEach { w.write(it); w.write("\n") }
        w.flush()
    }

    override fun clear() {
        dir.deleteRecursively()
    }

    private fun file(key: String) = java.io.File(dir, "$key.gz")

    private fun readBytes(key: String): ByteArray? = try {
        val f = file(key)
        if (!f.exists()) null else java.util.zip.GZIPInputStream(f.inputStream()).use { it.readBytes() }
    } catch (e: Exception) {
        null // beschädigt oder abgeschnitten: neu laden
    }

    private fun write(key: String, body: (java.io.OutputStream) -> Unit) {
        dir.mkdirs()
        val tmp = java.io.File(dir, "$key.gz.tmp")
        java.util.zip.GZIPOutputStream(tmp.outputStream()).use(body)
        val target = file(key)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    companion object {
        fun forAccount(context: android.content.Context, accountId: String): AndroidPlaylistCache =
            AndroidPlaylistCache(java.io.File(java.io.File(context.filesDir, "playlist-cache"), safePathSegment(accountId).ifEmpty { "_" }))
    }
}
