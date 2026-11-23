package de.vincentsberger.iptvplayer.shared.data

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilePlaylistCacheTest {

    private val dir: File = createTempDirectory("playlist-cache").toFile()

    /** Stellt Verschlüsselung dar: Bytes umdrehen – Klartext ist danach nicht mehr lesbar. */
    private object Reversing : DataProtector {
        override val fileName = "unused"
        override fun protect(data: ByteArray) = data.reversedArray()
        override fun unprotect(data: ByteArray) = data.reversedArray()
    }

    @Test
    fun roundTripTextAndLines() {
        val cache = FilePlaylistCache(dir, OwnerOnlyFile)
        cache.writeText("get_vod_streams.json", "[1]")
        assertEquals("[1]", cache.readText("get_vod_streams.json"))
        cache.writeLines("playlist.m3u", sequenceOf("a", "b"))
        assertEquals(listOf("a", "b"), cache.readLines("playlist.m3u") { it.toList() })
        assertNull(cache.readText("missing"))
    }

    @Test
    fun filesAreGzippedAndNamedByKey() {
        FilePlaylistCache(dir, OwnerOnlyFile).writeText("get_vod_streams.json", "[1]")
        val f = File(dir, "get_vod_streams.json.gz")
        assertTrue(f.exists())
        val head = f.readBytes().take(2).map { it.toInt() and 0xff }
        assertEquals(listOf(0x1f, 0x8b), head)
    }

    @Test
    fun protectorIsApplied() {
        val cache = FilePlaylistCache(dir, Reversing)
        cache.writeText("k", "geheim")
        assertFalse(File(dir, "k.gz").readBytes().decodeToString().contains("geheim"))
        assertNotEquals(listOf(0x1f, 0x8b), File(dir, "k.gz").readBytes().take(2).map { it.toInt() and 0xff })
        assertEquals("geheim", cache.readText("k"))
    }

    @Test
    fun corruptFileReadsAsNull() {
        val cache = FilePlaylistCache(dir, OwnerOnlyFile)
        cache.writeText("k", "[1]")
        File(dir, "k.gz").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(cache.readText("k"))
        assertNull(cache.readLines("k") { it.toList() })
    }

    @Test
    fun clearRemovesDirectory() {
        val cache = FilePlaylistCache(dir, OwnerOnlyFile)
        cache.writeText("k", "[1]")
        cache.clear()
        assertFalse(dir.exists())
        assertNull(cache.readText("k"))
    }

    @Test
    fun teeLinesCommitsOnlyWhenKept() {
        val cache = FilePlaylistCache(dir, OwnerOnlyFile)
        cache.writeLines("p", sequenceOf("alt"))
        val parsed = cache.teeLines("p", sequenceOf("a", "b"), parse = { it.toList() }, keep = { false })
        assertEquals(listOf("a", "b"), parsed)
        assertEquals(listOf("alt"), cache.readLines("p") { it.toList() })
        cache.teeLines("p", sequenceOf("a", "b"), parse = { it.toList() }, keep = { true })
        assertEquals(listOf("a", "b"), cache.readLines("p") { it.toList() })
    }
}
