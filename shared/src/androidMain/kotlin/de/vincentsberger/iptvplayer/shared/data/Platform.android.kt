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

    override fun readText(key: String): String? = open(key) { it.readText() }

    override fun writeText(key: String, text: String) = write(key, keep = { true }) { it.write(text) }

    override fun <T> readLines(key: String, block: (Sequence<String>) -> T): T? = open(key) { it.useLines(block) }

    override fun writeLines(key: String, lines: Sequence<String>) = write(key, keep = { true }) { w -> lines.forEach { w.write(it); w.write("\n") } }

    override fun <T> teeLines(key: String, lines: Sequence<String>, parse: (Sequence<String>) -> T, keep: (T) -> Boolean): T {
        var result: Result<T>? = null
        var writeFailed = false
        // Speicherfehler (voll, gesperrt) dürfen das Parsen nicht abbrechen: dann einfach nicht speichern.
        fun safe(block: () -> Unit) { if (!writeFailed) try { block() } catch (e: java.io.IOException) { writeFailed = true } }
        runCatching {
            write(key, keep = { !writeFailed && result?.getOrNull()?.let(keep) == true }) { w ->
                result = runCatching { parse(lines.onEach { line -> safe { w.write(line); w.write("\n") } }) }
            }
        }
        return result?.getOrThrow() ?: parse(lines)
    }

    override fun clear() {
        dir.deleteRecursively()
    }

    private fun file(key: String) = java.io.File(dir, "$key.gz")

    /** Entpackt direkt in einen Reader – ohne Byte-Kopie der ganzen Datei (TV-Boxen haben wenig Speicher). */
    private fun <T> open(key: String, block: (java.io.BufferedReader) -> T): T? = try {
        val f = file(key)
        if (!f.exists()) null else java.util.zip.GZIPInputStream(f.inputStream().buffered()).bufferedReader().use(block)
    } catch (e: Exception) {
        null // beschädigt oder abgeschnitten: neu laden
    }

    /** Schreibt in eine temporäre Datei und übernimmt sie nur, wenn [keep] danach zustimmt. */
    private fun write(key: String, keep: () -> Boolean, body: (java.io.Writer) -> Unit) {
        dir.mkdirs()
        val tmp = java.io.File(dir, "$key.gz.tmp")
        try {
            java.util.zip.GZIPOutputStream(tmp.outputStream().buffered()).bufferedWriter().use(body)
            if (keep()) {
                val target = file(key)
                if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
            }
        } finally {
            tmp.delete()
        }
    }

    companion object {
        fun forAccount(context: android.content.Context, accountId: String): AndroidPlaylistCache =
            AndroidPlaylistCache(java.io.File(java.io.File(context.filesDir, "playlist-cache"), safePathSegment(accountId).ifEmpty { "_" }))
    }
}
