package de.vincentsberger.iptvplayer.shared.data

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileKeyValueStoreTest {

    private val dir: File = createTempDirectory("tvplayer-test").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** Stellvertreter für DPAPI: kehrt die Bytes um und markiert sie – reicht, um „nicht im Klartext“ zu prüfen. */
    private object ReversingProtector : DataProtector {
        override val fileName = "data.dat"
        override fun protect(data: ByteArray) = byteArrayOf(42) + data.reversedArray()
        override fun unprotect(data: ByteArray): ByteArray {
            require(data.first() == 42.toByte()) { "nicht geschützt" }
            return data.copyOfRange(1, data.size).reversedArray()
        }
    }

    @Test
    fun valuesSurviveARestart() {
        FileKeyValueStore(dir, ReversingProtector).putString("user", "max")
        assertEquals("max", FileKeyValueStore(dir, ReversingProtector).getString("user"))
    }

    @Test
    fun protectedFileDoesNotContainThePasswordInPlainText() {
        FileKeyValueStore(dir, ReversingProtector).putString("pass", "geheim123")
        val raw = File(dir, "data.dat").readBytes().decodeToString()
        assertFalse("geheim123" in raw)
    }

    @Test
    fun legacyPlainJsonIsMigratedAndDeleted() {
        File(dir, "data.json").writeText("""{"type":"xtream","pass":"alt"}""")
        val store = FileKeyValueStore(dir, ReversingProtector)
        assertEquals("alt", store.getString("pass"))
        assertFalse(File(dir, "data.json").exists(), "Klartext-Datei muss weg sein")
        assertEquals("alt", FileKeyValueStore(dir, ReversingProtector).getString("pass"))
    }

    @Test
    fun unreadableFileStartsEmpty() {
        File(dir, "data.dat").writeText("kein gültiger Inhalt")
        assertNull(FileKeyValueStore(dir, ReversingProtector).getString("pass"))
    }

    @Test
    fun clearRemovesEverything() {
        val store = FileKeyValueStore(dir, ReversingProtector)
        store.putString("pass", "x")
        store.clear()
        assertNull(FileKeyValueStore(dir, ReversingProtector).getString("pass"))
    }

    @Test
    fun onPosixOnlyTheOwnerCanReadTheFile() {
        if (!dir.toPath().fileSystem.supportedFileAttributeViews().contains("posix")) return // Windows: dort schützt DPAPI
        FileKeyValueStore(dir, OwnerOnlyFile).putString("pass", "x")
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(File(dir, "data.json").toPath())))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.toPath())))
    }

    @Test
    fun existingFileWithLooseRightsIsTightened() {
        if (!dir.toPath().fileSystem.supportedFileAttributeViews().contains("posix")) return
        val f = File(dir, "data.json").apply { writeText("""{"pass":"x"}""") }
        Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        FileKeyValueStore(dir, OwnerOnlyFile)
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath())))
        assertTrue(f.exists(), "auf Linux ist data.json die richtige Datei und wird nicht gelöscht")
    }
}
