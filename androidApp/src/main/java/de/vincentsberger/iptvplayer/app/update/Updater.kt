package de.vincentsberger.iptvplayer.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import de.vincentsberger.iptvplayer.shared.LatestRelease
import de.vincentsberger.iptvplayer.shared.data.USER_AGENT
import de.vincentsberger.iptvplayer.shared.data.httpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Update aus der App heraus (nur für selbst installierte Versionen): APK des neuesten Releases laden
 * und die Installationsabfrage von Android öffnen. Bestätigen muss man selbst; Daten bleiben erhalten,
 * weil das Update mit demselben Schlüssel signiert ist.
 */
object Updater {
    /** 0..1 während des Ladens, sonst null. */
    var progress by mutableStateOf<Float?>(null); private set
    var message by mutableStateOf<String?>(null); private set

    private val STORES = setOf("com.android.vending", "com.amazon.venezia", "com.google.android.feedback")

    /** Aus einem Store installiert? Dann aktualisiert der Store – selbst updaten ist dort nicht erlaubt. */
    @Suppress("DEPRECATION")
    fun allowed(context: Context): Boolean =
        runCatching { context.packageManager.getInstallerPackageName(context.packageName) }.getOrNull() !in STORES

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    suspend fun install(context: Context, release: LatestRelease) {
        val url = release.apkUrl ?: run { message = "Im Release fehlt die App-Datei."; return }
        // Android 8+: Die App braucht einmalig die Erlaubnis, Apps zu installieren.
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            message = "Bitte erlaube „Unbekannte Apps installieren“ für TeeVee und starte das Update erneut."
            openUnknownSourcesSettings(context)
            return
        }
        message = null
        val file = try {
            download(context, url, release.version)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "Download fehlgeschlagen – Internetverbindung prüfen."
            null
        } finally {
            progress = null
        } ?: return
        val uri = if (Build.VERSION.SDK_INT >= 24) FileProvider.getUriForFile(context, "${context.packageName}.updates", file) else Uri.fromFile(file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            message = "Dieses Gerät kann Apps nicht direkt installieren. Lade das Update über ${release.pageUrl}."
        }
    }

    private suspend fun download(context: Context, url: String, version: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val target = File(dir, "IPTV-Player-$version.apk")
        progress = 0f
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("leer")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total != null) progress = (done.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
        }
        target
    }

    private fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Manche TV-Oberflächen (z. B. Fire OS) haben diese Seite nicht – dann bleibt der Hinweistext.
        runCatching { context.startActivity(intent) }
    }
}
