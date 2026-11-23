package de.vincentsberger.iptvplayer.shared.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readLine
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUUID
import platform.Foundation.downloadTaskWithRequest
import platform.Foundation.setValue
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual suspend fun httpGetText(url: String): String = httpReadLines(url) { it.joinToString("\n") }

actual suspend fun <T> httpReadLines(url: String, block: (Sequence<String>) -> T): T =
    fetchLines(url, setOf("http", "https"), block)

/**
 * Lädt [url] erst vollständig in eine temporäre Datei (NSURLSession schreibt direkt auf die Platte) und liest sie
 * dann zeilenweise – so liegt auch eine sehr große M3U-Liste nie komplett im Speicher. Die Datei wird danach gelöscht.
 * [schemes]: erlaubte Protokolle; Tests nutzen zusätzlich `file`.
 */
internal suspend fun <T> fetchLines(url: String, schemes: Set<String>, block: (Sequence<String>) -> T): T {
    val path = downloadToTemp(url, schemes)
    try {
        return withContext(Dispatchers.Default) { readFileLines(path, block) }
    } finally {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
}

/** Zeilen einer Datei; `\n` und `\r\n` werden entfernt, eine letzte Zeile ohne Umbruch zählt mit. */
internal fun <T> readFileLines(path: String, block: (Sequence<String>) -> T): T =
    SystemFileSystem.source(Path(path)).buffered().use { source -> block(generateSequence { source.readLine() }) }

@OptIn(ExperimentalForeignApi::class)
private suspend fun downloadToTemp(url: String, schemes: Set<String>): String {
    val nsUrl = NSURL.URLWithString(url)
    val scheme = nsUrl?.scheme?.lowercase()
    val port = nsUrl?.port?.longValue
    if (nsUrl == null || scheme !in schemes || (scheme != "file" && nsUrl.host.isNullOrEmpty()) || (port != null && port !in 1..65535)) {
        throw TvException(HttpErrors.INVALID_ADDRESS) // nie die URL zeigen: sie enthält Benutzername und Passwort
    }
    val request = NSMutableURLRequest(uRL = nsUrl).apply {
        setValue(USER_AGENT, forHTTPHeaderField = "User-Agent")
        setTimeoutInterval(60.0) // wie Desktop/Android: bis zu 60 s ohne Daten (Anbieter bauen große Listen langsam)
    }
    return suspendCancellableCoroutine { cont ->
        val task = NSURLSession.sharedSession.downloadTaskWithRequest(request) { location, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode
            when {
                error != null -> cont.resumeWithException(TvException(HttpErrors.UNREACHABLE))
                status != null && status >= 400 -> cont.resumeWithException(TvException(HttpErrors.status(status.toInt())))
                location == null -> cont.resumeWithException(TvException(HttpErrors.EMPTY))
                else -> {
                    // Die Datei des Systems verschwindet, sobald dieser Block endet – also sofort wegschieben.
                    val target = NSTemporaryDirectory() + "dl-" + NSUUID().UUIDString
                    if (NSFileManager.defaultManager.moveItemAtURL(location, toURL = NSURL.fileURLWithPath(target), error = null)) {
                        // Wird der Aufrufer genau jetzt abgebrochen, kommt der Pfad nie an – dann hier aufräumen.
                        cont.resume(target) { _, _, _ -> NSFileManager.defaultManager.removeItemAtPath(target, error = null) }
                    } else {
                        cont.resumeWithException(TvException(HttpErrors.UNREACHABLE))
                    }
                }
            }
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}

private val localDateTimeFormat = platform.Foundation.NSDateFormatter().apply {
    dateFormat = "yyyy-MM-dd HH:mm:ss"
    locale = platform.Foundation.NSLocale(localeIdentifier = "en_US_POSIX") // feste Ziffern/Kalender, unabhängig von der Spracheinstellung
}

actual fun localDateTime(millis: Long): String =
    localDateTimeFormat.stringFromDate(NSDate.dateWithTimeIntervalSince1970(millis / 1000.0))
