package de.vincentsberger.iptvplayer.ios.ui.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.DownloadInfo
import de.vincentsberger.iptvplayer.shared.DownloadRules
import de.vincentsberger.iptvplayer.shared.DownloadStartResult
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.USER_AGENT
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.setValue
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToURL
import platform.darwin.NSObject

/**
 * Downloads auf dem iPhone: Filme und Folgen werden vollständig ins Application-Support-Verzeichnis geladen (vom
 * iCloud-Backup ausgenommen) und von dort abgespielt – auch ohne Internet. Regeln aus `shared` ([DownloadRules]):
 * nur Dateien, einer gleichzeitig, ≥ 1 GB frei.
 *
 * Übertragung über eine NSURLSession im Hintergrundmodus: Der laufende Download lädt weiter, wenn die App im
 * Hintergrund ist oder beendet wird; das Ergebnis holt die Sitzung beim nächsten Start ab. Der nächste Titel der
 * Warteschlange startet, sobald die App wieder läuft. Alle Rückmeldungen kommen auf dem Main-Thread.
 */
object IosDownloads {
    private const val SESSION_ID = "de.vincentsberger.iptvplayer.downloads"

    private class Entry(
        val item: Item,
        val file: String,
        var state: DownloadInfo.State,
        var bytes: Long,
        val addedAt: Long,
        var resumeData: NSData? = null,
    )

    private val entries = mutableListOf<Entry>()
    private val tasks = mutableMapOf<String, NSURLSessionDownloadTask>()
    private var paused = false
    private var started = false
    private var lastPublish = 0.0

    /** Alle Downloads, neueste zuerst (Compose-State). */
    var all by mutableStateOf<List<DownloadInfo>>(emptyList()); private set

    private val fm get() = NSFileManager.defaultManager

    private val dir: NSURL by lazy {
        val support = fm.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).first() as NSURL
        val d = support.URLByAppendingPathComponent("downloads")!!
        fm.createDirectoryAtURL(d, withIntermediateDirectories = true, attributes = null, error = null)
        // Große Videodateien gehören nicht ins iCloud-Backup.
        d.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
        d
    }
    private val indexUrl: NSURL get() = dir.URLByAppendingPathComponent("index.tsv")!!
    private fun fileUrl(e: Entry): NSURL = dir.URLByAppendingPathComponent(e.file)!!

    private val session: NSURLSession by lazy {
        val config = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(SESSION_ID).apply {
            sessionSendsLaunchEvents = true
            discretionary = false
            allowsCellularAccess = true
        }
        NSURLSession.sessionWithConfiguration(config, SessionDelegate(), NSOperationQueue.mainQueue)
    }

    /** Einmal beim App-Start: Liste laden, laufende Übertragungen der Hintergrund-Sitzung wieder zuordnen. */
    fun start() {
        if (started) return
        started = true
        load()
        entries.filter { it.state == DownloadInfo.State.LOADING }.forEach { it.state = DownloadInfo.State.QUEUED }
        session.getTasksWithCompletionHandler { _, _, downloads ->
            downloads.orEmpty().filterIsInstance<NSURLSessionDownloadTask>().forEach { task ->
                val e = entries.firstOrNull { it.item.url == task.taskDescription }
                if (e != null && e.state == DownloadInfo.State.QUEUED && tasks.isEmpty()) {
                    e.state = DownloadInfo.State.LOADING
                    tasks[e.item.url] = task
                } else task.cancel()
            }
            schedule()
            publish(force = true)
        }
        publish(force = true)
    }

    fun infoFor(url: String): DownloadInfo? = all.firstOrNull { it.item.url == url }

    /** Lokale Datei (file://…) eines fertigen Downloads – der Player spielt dann ohne Netz. */
    fun localUrl(url: String): String? =
        entries.firstOrNull { it.item.url == url && it.state == DownloadInfo.State.DONE }?.let { fileUrl(it).absoluteString }

    fun freeBytes(): Long =
        ((fm.attributesOfFileSystemForPath(dir.path ?: return 0L, error = null)?.get(NSFileSystemFreeSize)) as? NSNumber)
            ?.longLongValue ?: 0L

    fun usedBytes(): Long = entries.sumOf { it.bytes }

    fun start(item: Item): DownloadStartResult {
        start()
        val result = DownloadRules.check(item, infoFor(item.url), freeBytes())
        if (result != DownloadStartResult.STARTED) return result
        val ext = item.url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()
            .takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) } ?: "mp4"
        val file = "${item.section.name.lowercase()}-${item.url.hashCode().toUInt().toString(16)}.$ext"
        entries += Entry(item, file, DownloadInfo.State.QUEUED, 0L, nowMillis())
        save(); schedule(); publish(force = true)
        return result
    }

    /** Download abbrechen bzw. löschen (Datei und Eintrag). */
    fun remove(url: String) {
        val e = entries.firstOrNull { it.item.url == url } ?: return
        tasks.remove(url)?.cancel()
        fm.removeItemAtURL(fileUrl(e), error = null)
        entries -= e
        save(); schedule(); publish(force = true)
    }

    fun retry(url: String) {
        val e = entries.firstOrNull { it.item.url == url } ?: return
        e.state = DownloadInfo.State.QUEUED; e.bytes = 0L; e.resumeData = null
        save(); schedule(); publish(force = true)
    }

    /**
     * Während der Wiedergabe aus dem Netz anhalten (Einstellung „Downloads pausieren …“): viele Anbieter erlauben nur
     * eine Verbindung. Der laufende Download merkt sich, wo er war, sofern der Server das Fortsetzen erlaubt.
     */
    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        if (value) {
            tasks.toList().forEach { (url, task) ->
                tasks.remove(url)
                val e = entries.firstOrNull { it.item.url == url }
                e?.state = DownloadInfo.State.QUEUED
                task.cancelByProducingResumeData { data -> e?.resumeData = data }
            }
        } else schedule()
        publish(force = true)
    }

    // ---------- intern ----------

    private fun schedule() {
        if (!started || paused || tasks.isNotEmpty()) return
        val next = entries.filter { it.state == DownloadInfo.State.QUEUED }.minByOrNull { it.addedAt } ?: return
        val resume = next.resumeData
        next.resumeData = null
        val task = if (resume != null) session.downloadTaskWithResumeData(resume)
        else {
            val url = NSURL.URLWithString(next.item.url) ?: run { next.state = DownloadInfo.State.FAILED; save(); return schedule() }
            val request = NSMutableURLRequest.requestWithURL(url).apply { setValue(USER_AGENT, forHTTPHeaderField = "User-Agent") }
            session.downloadTaskWithRequest(request)
        }
        task.taskDescription = next.item.url
        next.state = DownloadInfo.State.LOADING
        tasks[next.item.url] = task
        task.resume()
        save()
    }

    private fun entryOf(task: NSURLSessionTask) = entries.firstOrNull { it.item.url == task.taskDescription }

    internal fun onProgress(task: NSURLSessionDownloadTask, written: Long, expected: Long) {
        val e = entryOf(task) ?: return
        e.bytes = written
        progress[e.item.url] = if (expected > 0) (written.toDouble() / expected).toFloat() else null
        publish()
    }

    internal fun onFinished(task: NSURLSessionDownloadTask, location: NSURL) {
        val e = entryOf(task) ?: return
        // Auch Fehlerseiten (404, 403) kommen hier als „fertige Datei“ an.
        val status = (task.response as? NSHTTPURLResponse)?.statusCode ?: 200L
        if (status >= 400) { e.state = DownloadInfo.State.FAILED; return }
        val target = fileUrl(e)
        fm.removeItemAtURL(target, error = null)
        // Die Datei muss noch in diesem Aufruf verschoben werden – danach löscht iOS sie.
        if (fm.moveItemAtURL(location, toURL = target, error = null)) {
            e.state = DownloadInfo.State.DONE
            e.bytes = (fm.attributesOfItemAtPath(target.path ?: "", error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: e.bytes
        } else e.state = DownloadInfo.State.FAILED
    }

    internal fun onCompleted(task: NSURLSessionTask, error: NSError?) {
        val url = task.taskDescription ?: return
        if (tasks[url] === task) tasks.remove(url)
        val e = entries.firstOrNull { it.item.url == url }
        if (e != null && error != null) {
            // Selbst abgebrochen (Pause, Löschen): kein Fehler.
            if (error.code != NSURLErrorCancelled) e.state = DownloadInfo.State.FAILED
        }
        progress.remove(url)
        save(); schedule(); publish(force = true)
    }

    private val progress = mutableMapOf<String, Float?>()

    private fun publish(force: Boolean = false) {
        val now = NSDate().timeIntervalSince1970
        if (!force && now - lastPublish < 0.5) return // Fortschritt höchstens zweimal pro Sekunde zeichnen
        lastPublish = now
        all = entries.sortedByDescending { it.addedAt }.map { e ->
            val state = if (paused && e.state == DownloadInfo.State.QUEUED) DownloadInfo.State.PAUSED else e.state
            val p = if (e.state == DownloadInfo.State.DONE) 1f else progress[e.item.url]
            DownloadInfo(e.item, state, p, e.bytes)
        }
    }

    // Liste als Textdatei: Zustand, Bytes, hinzugefügt, Dateiname, Titel als JSON (Tabulator getrennt).
    private fun save() {
        val text = entries.joinToString("\n") { e ->
            val state = if (e.state == DownloadInfo.State.LOADING) DownloadInfo.State.QUEUED else e.state
            listOf(state.name, e.bytes.toString(), e.addedAt.toString(), e.file, e.item.toJson()).joinToString("\t")
        }
        NSString.create(string = text).writeToURL(indexUrl, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    private fun load() {
        val text = NSString.stringWithContentsOfURL(indexUrl, encoding = NSUTF8StringEncoding, error = null) ?: return
        entries.clear()
        text.lines().filter { it.isNotBlank() }.forEach { line ->
            val p = line.split('\t', limit = 5)
            if (p.size < 5) return@forEach
            val item = Item.fromJson(p[4]) ?: return@forEach
            val state = DownloadInfo.State.entries.firstOrNull { it.name == p[0] } ?: return@forEach
            entries += Entry(item, p[3], state, p[1].toLongOrNull() ?: 0L, p[2].toLongOrNull() ?: 0L)
        }
    }

    private fun nowMillis() = (NSDate().timeIntervalSince1970 * 1000).toLong()

}

/**
 * Rückmeldungen der Hintergrund-Sitzung (Main-Queue). Eine Klasse statt `object`: Kotlin/Native kann Singletons, die
 * von einer Objective-C-Klasse erben, nicht erzeugen. Die Sitzung hält ihren Delegate selbst fest.
 */
private class SessionDelegate : NSObject(), NSURLSessionDownloadDelegateProtocol {
    override fun URLSession(session: NSURLSession, downloadTask: NSURLSessionDownloadTask, didFinishDownloadingToURL: NSURL) =
        IosDownloads.onFinished(downloadTask, didFinishDownloadingToURL)

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) = IosDownloads.onProgress(downloadTask, totalBytesWritten, totalBytesExpectedToWrite)

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) =
        IosDownloads.onCompleted(task, didCompleteWithError)
}
