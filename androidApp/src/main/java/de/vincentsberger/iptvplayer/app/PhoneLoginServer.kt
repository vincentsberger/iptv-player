package de.vincentsberger.iptvplayer.app

import de.vincentsberger.iptvplayer.shared.PhoneLogin
import de.vincentsberger.iptvplayer.shared.data.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.security.MessageDigest
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/**
 * Kleiner Webserver im Heimnetz für die Anmeldung vom Handy.
 * Läuft nur, solange der Anmeldebildschirm offen ist. Die Adresse enthält einen Zufallsschlüssel,
 * damit nicht jedes Gerät im WLAN Zugangsdaten unterschieben kann.
 */
class PhoneLoginServer(private val onAccount: (Account) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: ServerSocket? = null
    private val token = randomToken()
    @Volatile private var failures = 0

    /** Startet den Server; liefert die Adresse für den QR-Code oder null (kein Netz / kein freier Port). */
    fun start(): String? {
        val ip = lanAddress() ?: return null
        // Nur an der WLAN/LAN-Adresse lauschen, nicht an allen Schnittstellen.
        val bindTo = InetAddress.getByName(ip)
        val server = PORTS.firstNotNullOfOrNull { port -> runCatching { ServerSocket(port, 10, bindTo) }.getOrNull() } ?: return null
        socket = server
        scope.launch {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                launch { client.use { handle(it) } }
            }
        }
        return "http://$ip:${server.localPort}/$token"
    }

    fun stop() {
        runCatching { socket?.close() }
        scope.cancel()
    }

    private suspend fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readLine(input) ?: return
        val (method, path) = requestLine.split(' ').let { it.getOrNull(0).orEmpty() to it.getOrNull(1).orEmpty() }
        var length = 0
        while (true) {
            val header = readLine(input) ?: return
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val validToken = MessageDigest.isEqual(path.substringBefore('?').encodeToByteArray(), "/$token".encodeToByteArray())
        if (!validToken && ++failures >= MAX_FAILURES) stop() // Durchprobieren: Server aus, der nächste Anmeldebildschirm startet neu
        val (status, html) = when {
            !validToken -> 404 to "<h1>Nicht gefunden</h1>"
            method == "GET" -> 200 to PhoneLogin.formPage()
            method == "POST" && length in 1..MAX_BODY -> {
                val body = ByteArray(length).also { readFully(input, it) }.decodeToString()
                when (val r = PhoneLogin.accountFrom(PhoneLogin.parseForm(body))) {
                    is PhoneLogin.Result.Ok -> {
                        withContext(Dispatchers.Main) { onAccount(r.account) }
                        200 to PhoneLogin.sentPage()
                    }
                    is PhoneLogin.Result.Invalid -> 200 to PhoneLogin.formPage(r.message)
                }
            }
            else -> 400 to "<h1>Ungültige Anfrage</h1>"
        }
        val bytes = html.encodeToByteArray()
        val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\n" +
            "Content-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply { write(head.encodeToByteArray()); write(bytes); flush() }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 8192) return null
            sb.append(b.toChar())
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
    }

    companion object {
        private val PORTS = 8790..8799
        private const val MAX_BODY = 16 * 1024
        private const val MAX_FAILURES = 10
        private const val TOKEN_LENGTH = 16 // 16 Zeichen aus 31 ≈ 79 Bit – im QR-Code egal, abtippbar bleibt es trotzdem

        private fun randomToken(): String {
            val chars = "abcdefghjkmnpqrstuvwxyz23456789"
            val rnd = SecureRandom()
            return (1..TOKEN_LENGTH).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        }

        /** IPv4-Adresse im Heimnetz (WLAN oder LAN), sonst null. */
        fun lanAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
