package de.vincentsberger.iptvplayer.shared.data

import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Keychain-Ersatz: Simulator-Testprogramme ohne App-Bundle dürfen nicht in die Keychain (auf dem Gerät geprüft). */
private class FakeSecretStore : SecretStore {
    val map = mutableMapOf<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    override fun clear() = map.clear()
}

class IosStorageTest {

    private val dir = NSTemporaryDirectory() + "store-" + NSUUID().UUIDString
    private val fm = NSFileManager.defaultManager

    private fun read(path: String): String? = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
    private fun write(path: String, text: String) {
        fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        NSString.create(string = text).writeToFile(path, atomically = false, encoding = NSUTF8StringEncoding, error = null)
    }

    @Test
    fun secretKeysNeverReachTheFile() {
        val secrets = FakeSecretStore()
        val store = IosKeyValueStore(dir, secrets)
        store.putString("pass", "geheim")
        store.putString("accounts", """[{"user":"demo","pass":"geheim"}]""")
        store.putString("volume", "80")
        assertFalse(read("$dir/data.json").orEmpty().contains("geheim"))
        assertTrue(read("$dir/data.json").orEmpty().contains("80"))
        assertEquals("geheim", store.getString("pass"))
        assertEquals("geheim", secrets.map["pass"])
    }

    @Test
    fun valuesSurviveARestart() {
        val secrets = FakeSecretStore()
        IosKeyValueStore(dir, secrets).apply { putString("volume", "80"); putString("user", "demo") }
        val again = IosKeyValueStore(dir, secrets)
        assertEquals("80", again.getString("volume"))
        assertEquals("demo", again.getString("user"))
    }

    @Test
    fun unreadableFileStartsEmpty() {
        write("$dir/data.json", "{kaputt")
        val store = IosKeyValueStore(dir, FakeSecretStore())
        assertNull(store.getString("volume"))
        store.putString("volume", "10")
        assertEquals("10", IosKeyValueStore(dir, FakeSecretStore()).getString("volume"))
    }

    @Test
    fun leftoverTmpFileDoesNotReplaceData() {
        IosKeyValueStore(dir, FakeSecretStore()).putString("volume", "80")
        write("$dir/data.json.tmp", "{\"volu") // Absturz mitten im Schreiben
        assertEquals("80", IosKeyValueStore(dir, FakeSecretStore()).getString("volume"))
    }

    @Test
    fun removingAValueDeletesIt() {
        val secrets = FakeSecretStore()
        val store = IosKeyValueStore(dir, secrets)
        store.putString("pass", "geheim"); store.putString("volume", "80")
        store.putString("pass", null); store.putString("volume", null)
        assertNull(store.getString("pass")); assertNull(store.getString("volume"))
        assertTrue(secrets.map.isEmpty())
    }

    @Test
    fun clearRemovesFileAndSecrets() {
        val secrets = FakeSecretStore()
        val store = IosKeyValueStore(dir, secrets)
        store.putString("pass", "geheim"); store.putString("volume", "80")
        store.clear()
        assertNull(store.getString("volume"))
        assertTrue(secrets.map.isEmpty())
        assertNull(IosKeyValueStore(dir, secrets).getString("volume"))
    }

    // --- Playlist-Zwischenspeicher (Pendants zu FilePlaylistCacheTest) ---

    @Test
    fun roundTripTextAndLines() {
        val cache = IosPlaylistCache(dir)
        cache.writeText("get_vod_streams.json", "[1]")
        assertEquals("[1]", cache.readText("get_vod_streams.json"))
        cache.writeLines("playlist.m3u", sequenceOf("a", "b"))
        assertEquals(listOf("a", "b"), cache.readLines("playlist.m3u") { it.toList() })
    }

    @Test
    fun corruptOrMissingReadsAsNull() {
        val cache = IosPlaylistCache(dir)
        assertNull(cache.readText("missing"))
        assertNull(cache.readLines("missing") { it.toList() })
        fm.createDirectoryAtPath("$dir/k.txt", withIntermediateDirectories = true, attributes = null, error = null) // unlesbar: Ordner statt Datei
        assertNull(cache.readText("k"))
    }

    @Test
    fun teeLinesCommitsOnlyWhenKept() {
        val cache = IosPlaylistCache(dir)
        cache.writeLines("p", sequenceOf("alt"))
        val parsed = cache.teeLines("p", sequenceOf("a", "b"), parse = { it.toList() }, keep = { false })
        assertEquals(listOf("a", "b"), parsed)
        assertEquals(listOf("alt"), cache.readLines("p") { it.toList() })
        cache.teeLines("p", sequenceOf("a", "b"), parse = { it.toList() }, keep = { true })
        assertEquals(listOf("a", "b"), cache.readLines("p") { it.toList() })
    }

    @Test
    fun clearRemovesDirectory() {
        val cache = IosPlaylistCache(dir)
        cache.writeText("k", "[1]")
        cache.clear()
        assertFalse(fm.fileExistsAtPath(dir))
        assertNull(cache.readText("k"))
    }
}
