package de.vincentsberger.iptvplayer.shared.data

import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** Geheime Werte (Zugangsdaten) – auf dem iPhone in der Keychain, in Tests eine Attrappe. */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
    fun clear()
}

/** Keychain: nur auf diesem Gerät, lesbar ab dem ersten Entsperren nach dem Start (Hintergrund-Downloads). */
class KeychainSecretStore(private val service: String = "de.vincentsberger.iptvplayer") : SecretStore {

    override fun get(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = query(key, kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { SecItemCopyMatching(it, result.ptr) }
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override fun put(key: String, value: String?) {
        query(key) { SecItemDelete(it) }
        if (value == null) return
        val data = CFBridgingRetain(value.toNSData())
        try {
            query(key, kSecValueData to data, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) { SecItemAdd(it, null) }
        } finally {
            CFRelease(data)
        }
    }

    override fun clear() {
        query(account = null) { SecItemDelete(it) }
    }

    /** Baut die Keychain-Abfrage (Klasse, Dienst, optional Konto + [extra]) und gibt sie nach [block] wieder frei. */
    private fun <T> query(account: String?, vararg extra: Pair<CFTypeRef?, CFTypeRef?>, block: (platform.CoreFoundation.CFDictionaryRef?) -> T): T = memScoped {
        val owned = listOfNotNull(CFBridgingRetain(service), account?.let { CFBridgingRetain(it) })
        val pairs = buildList<Pair<CFTypeRef?, CFTypeRef?>> {
            add(kSecClass to kSecClassGenericPassword)
            add(kSecAttrService to owned[0])
            if (account != null) add(kSecAttrAccount to owned[1])
            addAll(extra)
        }
        val keys = allocArrayOf(pairs.map { it.first })
        val values = allocArrayOf(pairs.map { it.second })
        val dict = CFDictionaryCreate(null, keys.reinterpret(), values.reinterpret(), pairs.size.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            block(dict)
        } finally {
            CFRelease(dict)
            owned.forEach { CFRelease(it) }
        }
    }
}

/**
 * Speicher auf dem iPhone: `data.json` im Application-Support-Ordner (iOS-Dateischutz, nicht im iCloud-Backup),
 * Zugangsdaten ([SECRET_KEYS]) ausschließlich in [secrets] (Keychain).
 */
class IosKeyValueStore(
    private val dir: String = defaultDir(),
    private val secrets: SecretStore = KeychainSecretStore(),
) : KeyValueStore {
    private val file = "$dir/data.json"
    private val lock = NSRecursiveLock()
    private val map: MutableMap<String, String>

    init {
        ensureDir(dir)
        map = (parseJson(readTextFile(file).orEmpty()) as? JsonObject)
            ?.mapValues { (it.value as? JsonPrimitive)?.content ?: "" }
            ?.toMutableMap()
            ?: mutableMapOf() // fehlt oder beschädigt: neu beginnen
    }

    override fun getString(key: String): String? = locked {
        if (key in SECRET_KEYS) secrets.get(key) else map[key]
    }

    override fun putString(key: String, value: String?) = locked {
        if (key in SECRET_KEYS) {
            secrets.put(key, value)
        } else {
            if (value == null) map.remove(key) else map[key] = value
            persist()
        }
    }

    override fun clear() = locked {
        map.clear()
        secrets.clear()
        persist()
    }

    private fun persist() {
        ensureDir(dir)
        writeTextFile(file, JsonObject(map.mapValues { JsonPrimitive(it.value) }).toString())
    }

    private fun <T> locked(block: () -> T): T {
        lock.lock()
        try { return block() } finally { lock.unlock() }
    }

    companion object {
        val SECRET_KEYS: Set<String> = setOf("accounts", "server", "user", "pass", "url")

        fun defaultDir(): String = systemDir(NSApplicationSupportDirectory) + "/FreeIPTVPlayer"
    }
}

/**
 * Playlist-Zwischenspeicher auf dem iPhone: je Eintrag `<key>.txt`, unkomprimiert im Caches-Ordner
 * (das System darf ihn bei Platzmangel leeren – dann wird neu geladen). Schreiben über temporäre Datei + Umbenennen.
 */
class IosPlaylistCache(private val dir: String) : PlaylistCacheStore {

    override fun readText(key: String): String? = readTextFile(path(key))

    override fun writeText(key: String, text: String) {
        runCatching { ensureDir(dir); writeTextFile(path(key), text) }
    }

    override fun <T> readLines(key: String, block: (Sequence<String>) -> T): T? = try {
        if (!isRegularFile(path(key))) null else readFileLines(path(key), block)
    } catch (e: Exception) {
        null // unlesbar: neu laden
    }

    override fun writeLines(key: String, lines: Sequence<String>) {
        val tmp = tmpPath(key)
        try {
            ensureDir(dir)
            SystemFileSystem.sink(Path(tmp)).buffered().use { s -> lines.forEach { s.writeString(it); s.writeString("\n") } }
            commit(tmp, key)
        } catch (e: Exception) {
            remove(tmp)
        }
    }

    override fun <T> teeLines(key: String, lines: Sequence<String>, parse: (Sequence<String>) -> T, keep: (T) -> Boolean): T {
        val tmp = tmpPath(key)
        var sink: Sink? = runCatching { ensureDir(dir); SystemFileSystem.sink(Path(tmp)).buffered() }.getOrNull()
        var written = sink != null
        fun stopWriting() { runCatching { sink?.close() }; sink = null; written = false; remove(tmp) }
        val result = try {
            // Beim Parsen mitschreiben – Speicherfehler (voller Speicher) brechen nur das Mitschreiben ab, nie das Parsen.
            parse(lines.onEach { line ->
                sink?.let { s -> if (runCatching { s.writeString(line); s.writeString("\n") }.isFailure) stopWriting() }
            })
        } catch (e: Throwable) {
            stopWriting()
            throw e
        }
        runCatching { sink?.close() }.onFailure { written = false }
        if (written && keep(result)) runCatching { commit(tmp, key) }.onFailure { remove(tmp) } else remove(tmp)
        return result
    }

    override fun clear() {
        remove(dir)
    }

    private fun path(key: String) = "$dir/$key.txt"
    private fun tmpPath(key: String) = "$dir/$key.txt.${NSUUID().UUIDString}.tmp"

    private fun commit(tmp: String, key: String) {
        protect(tmp)
        SystemFileSystem.atomicMove(Path(tmp), Path(path(key)))
    }

    companion object {
        /** Zwischenspeicher eines Anbieters unter `<Caches>/playlists/<id>`. */
        fun forAccount(accountId: String): IosPlaylistCache =
            IosPlaylistCache(systemDir(NSCachesDirectory) + "/playlists/" + safePathSegment(accountId).ifEmpty { "_" })
    }
}

// --- Dateihilfen ---

private val fileManager get() = NSFileManager.defaultManager

private fun systemDir(kind: platform.Foundation.NSSearchPathDirectory): String =
    NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).first() as String

/** Legt [dir] an und nimmt es aus dem iCloud-Backup (Daten enthalten Stream-Adressen mit Zugangsdaten). */
private fun ensureDir(dir: String) {
    if (!fileManager.fileExistsAtPath(dir)) {
        fileManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    }
    NSURL.fileURLWithPath(dir).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
}

private fun isRegularFile(path: String): Boolean =
    SystemFileSystem.metadataOrNull(Path(path))?.isRegularFile == true

private fun readTextFile(path: String): String? =
    if (!isRegularFile(path)) null else NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

/** Schreibt vollständig oder gar nicht (NSData schreibt atomar über eine eigene temporäre Datei). */
private fun writeTextFile(path: String, text: String) {
    text.toNSData().writeToFile(path, NSDataWritingAtomic or NSDataWritingFileProtectionCompleteUntilFirstUserAuthentication, null)
}

private fun protect(path: String) {
    fileManager.setAttributes(mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication), ofItemAtPath = path, error = null)
}

private fun remove(path: String) {
    fileManager.removeItemAtPath(path, error = null)
}

@Suppress("CAST_NEVER_SUCCEEDS")
private fun String.toNSData(): NSData = (this as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!
