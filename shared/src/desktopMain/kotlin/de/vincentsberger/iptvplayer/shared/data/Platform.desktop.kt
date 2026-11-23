package de.vincentsberger.iptvplayer.shared.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
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

actual fun localDateTime(millis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date(millis))

/**
 * Schützt die gespeicherten Daten (Zugangsdaten, Verlauf mit Stream-Adressen) auf der Festplatte.
 * Austauschbar, damit Tests ohne Windows laufen.
 */
interface DataProtector {
    /** Dateiname, unter dem die geschützten Daten liegen. */
    val fileName: String
    fun protect(data: ByteArray): ByteArray
    fun unprotect(data: ByteArray): ByteArray

    companion object {
        fun forCurrentOs(): DataProtector =
            if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) WindowsDpapi else OwnerOnlyFile
    }
}

/** Windows: Verschlüsselung mit DPAPI, gebunden an das Windows-Benutzerkonto. */
object WindowsDpapi : DataProtector {
    override val fileName = "data.dat"
    override fun protect(data: ByteArray): ByteArray = com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(data)
    override fun unprotect(data: ByteArray): ByteArray = com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(data)
}

/** Linux/macOS: Klartext, aber nur für den eigenen Benutzer lesbar (Rechte 600, Ordner 700). */
object OwnerOnlyFile : DataProtector {
    override val fileName = "data.json"
    override fun protect(data: ByteArray) = data
    override fun unprotect(data: ByteArray) = data
}

/**
 * Speicher auf dem PC – Windows: %APPDATA%\FreeIPTVPlayer\data.dat (DPAPI-verschlüsselt),
 * sonst ~/.tvplayer/data.json (nur für den Benutzer lesbar).
 * Eine alte, unverschlüsselte data.json wird beim ersten Start übernommen und gelöscht.
 */
class FileKeyValueStore(
    private val dir: File = defaultDir(),
    private val protector: DataProtector = DataProtector.forCurrentOs(),
) : KeyValueStore {
    private val file = File(dir, protector.fileName)
    private val legacyFile = File(dir, "data.json")
    private val map: MutableMap<String, String>

    init {
        dir.mkdirs()
        restrictToOwner(dir, directory = true)
        map = read(file) { protector.unprotect(it) } ?: mutableMapOf()
        if (legacyFile != file && legacyFile.exists()) {
            read(legacyFile) { it }?.let { map.putAll(it); persist() }
            legacyFile.delete()
        } else if (file.exists()) {
            restrictToOwner(file, directory = false) // auch schon vorhandene Dateien absichern
        }
    }

    override fun getString(key: String): String? = synchronized(map) { map[key] }
    override fun putString(key: String, value: String?) = synchronized(map) {
        if (value == null) map.remove(key) else map[key] = value
        persist()
    }
    override fun clear() = synchronized(map) { map.clear(); persist() }

    private fun read(f: File, decode: (ByteArray) -> ByteArray): MutableMap<String, String>? = try {
        (parseJson(decode(f.readBytes()).decodeToString()) as? JsonObject)
            ?.mapValues { (it.value as? JsonPrimitive)?.content ?: "" }
            ?.toMutableMap()
    } catch (e: Exception) {
        null  // fehlt, beschädigt oder von einem anderen Benutzer verschlüsselt: neu beginnen
    }

    private fun persist() {
        val json = JsonObject(map.mapValues { JsonPrimitive(it.value) }).toString()
        val tmp = File(dir, protector.fileName + ".tmp")
        tmp.writeBytes(protector.protect(json.encodeToByteArray()))
        restrictToOwner(tmp, directory = false)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    companion object {
        fun defaultDir(): File = System.getenv("APPDATA")?.let { File(it, "FreeIPTVPlayer") }
            ?: File(System.getProperty("user.home"), ".freeiptvplayer")

        /** Rechte 600 bzw. 700 auf POSIX-Systemen; unter Windows ohne Wirkung (dort schützt DPAPI). */
        internal fun restrictToOwner(f: File, directory: Boolean) {
            try {
                Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString(if (directory) "rwx------" else "rw-------"))
            } catch (e: UnsupportedOperationException) {
            } catch (e: java.io.IOException) {
            }
        }
    }
}

/**
 * Playlist-Zwischenspeicher auf dem PC: je Eintrag eine Datei „<key>.gz“, gzip-komprimiert und danach genauso
 * geschützt wie die übrigen App-Daten (Windows: DPAPI; sonst nur für den Benutzer lesbar). Die Inhalte können
 * Zugangsdaten enthalten (Stream-URLs einer M3U-Liste).
 */
class FilePlaylistCache(
    private val dir: File,
    private val protector: DataProtector = DataProtector.forCurrentOs(),
) : PlaylistCacheStore {

    override fun readText(key: String): String? = open(key) { it.readText() }

    override fun writeText(key: String, text: String) = commit(key, zip { it.write(text) })

    override fun <T> readLines(key: String, block: (Sequence<String>) -> T): T? = open(key) { it.useLines(block) }

    override fun writeLines(key: String, lines: Sequence<String>) = commit(key, zip { w -> lines.forEach { w.write(it); w.write("\n") } })

    override fun <T> teeLines(key: String, lines: Sequence<String>, parse: (Sequence<String>) -> T, keep: (T) -> Boolean): T {
        var result: Result<T>? = null
        // Beim Parsen mitschreiben (komprimiert, also klein) – die Liste liegt nie vollständig als Text im Speicher.
        val zipped = zip { w -> result = runCatching { parse(lines.onEach { w.write(it); w.write("\n") }) } }
        val r = result!!.getOrThrow()
        if (keep(r)) runCatching { commit(key, zipped) } // Speicherfehler: frisch geladen gilt trotzdem
        return r
    }

    override fun clear() {
        dir.deleteRecursively()
    }

    private fun file(key: String) = File(dir, "$key.gz")

    /** Entpackt direkt in einen Reader – ohne die Datei erst als Byte-Array zu verdoppeln. */
    private fun <T> open(key: String, block: (java.io.BufferedReader) -> T): T? = try {
        val f = file(key)
        if (!f.exists()) null
        else java.util.zip.GZIPInputStream(protector.unprotect(f.readBytes()).inputStream()).bufferedReader().use(block)
    } catch (e: Exception) {
        null // beschädigt, abgeschnitten oder von einem anderen Benutzer verschlüsselt: neu laden
    }

    private fun zip(body: (java.io.Writer) -> Unit): ByteArray =
        java.io.ByteArrayOutputStream().also { bytes ->
            java.util.zip.GZIPOutputStream(bytes).bufferedWriter().use(body)
        }.toByteArray()

    private fun commit(key: String, zipped: ByteArray) {
        dir.mkdirs()
        FileKeyValueStore.restrictToOwner(dir, directory = true)
        val tmp = File(dir, "$key.gz.tmp")
        tmp.writeBytes(protector.protect(zipped))
        FileKeyValueStore.restrictToOwner(tmp, directory = false)
        val target = file(key)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    companion object {
        /** Zwischenspeicher eines Anbieters im App-Datenordner. */
        fun forAccount(accountId: String): FilePlaylistCache =
            FilePlaylistCache(File(File(FileKeyValueStore.defaultDir(), "cache"), safePathSegment(accountId).ifEmpty { "_" }))
    }
}
