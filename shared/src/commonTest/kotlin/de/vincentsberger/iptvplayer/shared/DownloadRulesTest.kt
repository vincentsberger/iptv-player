package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadRulesTest {
    private fun item(url: String, section: Section = Section.MOVIES) = Item(id = url, name = url, logo = null, categoryId = "1", url = url, section = section)
    private val film = item("http://srv/movie/1.mkv")

    @Test
    fun onlyFilesCanBeDownloaded() {
        assertTrue(DownloadRules.canDownload(film))
        assertFalse(DownloadRules.canDownload(item("http://srv/live/1.ts", Section.LIVE)))
        assertFalse(DownloadRules.canDownload(item("http://srv/movie/1.m3u8?token=x")))
        assertFalse(DownloadRules.canDownload(item("")))
    }

    @Test
    fun startChecksDuplicateAndSpace() {
        val free = DownloadRules.MIN_FREE_BYTES
        assertEquals(DownloadStartResult.STARTED, DownloadRules.check(film, null, free))
        assertEquals(DownloadStartResult.NO_SPACE, DownloadRules.check(film, null, free - 1))
        assertEquals(DownloadStartResult.ALREADY, DownloadRules.check(film, DownloadInfo(film, DownloadInfo.State.DONE, 1f, 5), free))
        assertEquals(DownloadStartResult.NOT_POSSIBLE, DownloadRules.check(item("x.m3u8"), null, free))
    }

    @Test
    fun oneAtATimeOldestFirst() {
        fun d(url: String, s: DownloadInfo.State) = DownloadInfo(item(url), s, null, 0)
        val newestFirst = listOf(d("c", DownloadInfo.State.QUEUED), d("b", DownloadInfo.State.QUEUED), d("a", DownloadInfo.State.DONE))
        assertEquals("b", DownloadRules.nextToLoad(newestFirst, paused = false)?.item?.url)
        assertNull(DownloadRules.nextToLoad(newestFirst, paused = true))
        assertNull(DownloadRules.nextToLoad(listOf(d("x", DownloadInfo.State.LOADING)) + newestFirst, paused = false))
        assertTrue(DownloadRules.hasOpen(newestFirst))
        assertFalse(DownloadRules.hasOpen(listOf(d("a", DownloadInfo.State.DONE), d("f", DownloadInfo.State.FAILED))))
    }

    @Test
    fun bytesAndStatusInGerman() {
        assertEquals("1,4 GB", formatBytes(1_449_000_000))
        assertEquals("1,5 GB", formatBytes(1_450_000_000))
        assertEquals("350 MB", formatBytes(350_400_000))
        assertEquals("12 kB", formatBytes(12_345))
        assertEquals("Wird geladen · 42 % · 350 MB", downloadStatus(DownloadInfo(film, DownloadInfo.State.LOADING, 0.42f, 350_000_000)))
        assertNull(downloadStartMessage(DownloadStartResult.ALREADY, 0))
    }

    @Test
    fun tapOnDownloadButtonStartsRetriesOrCancelsWithConfirmation() {
        assertEquals(DownloadTap.START, downloadTap(null, confirming = false))
        assertEquals(DownloadTap.RETRY, downloadTap(DownloadInfo.State.FAILED, confirming = false))
        assertEquals(DownloadTap.NONE, downloadTap(DownloadInfo.State.DONE, confirming = false))
        for (open in listOf(DownloadInfo.State.QUEUED, DownloadInfo.State.LOADING, DownloadInfo.State.PAUSED)) {
            assertEquals(DownloadTap.ASK_CANCEL, downloadTap(open, confirming = false), "erster Druck fragt nach")
            assertEquals(DownloadTap.CANCEL, downloadTap(open, confirming = true), "zweiter Druck bricht ab")
        }
        // Eine offene Rückfrage gilt nur, solange der Download noch läuft.
        assertEquals(DownloadTap.NONE, downloadTap(DownloadInfo.State.DONE, confirming = true))
        assertEquals(DownloadTap.START, downloadTap(null, confirming = true))
    }

    @Test
    fun openDownloadsAreThoseStillWaitingOrLoading() {
        assertTrue(isOpenDownload(DownloadInfo.State.QUEUED))
        assertTrue(isOpenDownload(DownloadInfo.State.LOADING))
        assertTrue(isOpenDownload(DownloadInfo.State.PAUSED))
        assertFalse(isOpenDownload(DownloadInfo.State.DONE))
        assertFalse(isOpenDownload(DownloadInfo.State.FAILED))
        assertFalse(isOpenDownload(null))
    }
}
