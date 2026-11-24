package de.vincentsberger.iptvplayer.desktop.player

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.ptr.IntByReference
import java.util.concurrent.Executors

/**
 * Hält Bildschirm und Rechner wach, solange ein Video läuft. Ohne Sperre hält das Betriebssystem den Rechner beim
 * Zuschauen für untätig (keine Maus, keine Tastatur) und dimmt bzw. schaltet ab – VLCs eigene Sperre greift nicht,
 * weil die App über eine `CallbackVideoSurface` selbst zeichnet statt in ein VLC-Fenster.
 */
interface IdleInhibitor {
    fun inhibit()
    fun release()
}

/** Pro Player: meldet nur Wechsel weiter – VLC feuert „playing“ z. B. nach jedem Nachpuffern erneut. */
class PlaybackAwake(private val inhibitor: IdleInhibitor) {
    private var active = false

    @Synchronized
    fun update(playing: Boolean) {
        if (playing == active) return
        active = playing
        if (playing) inhibitor.inhibit() else inhibitor.release()
    }
}

/**
 * Für alle Player gemeinsam: gesperrt, solange mindestens einer abspielt. Die Systemaufrufe laufen auf einem eigenen
 * Thread – der D-Bus-Aufruf kann blockieren (nicht auf dem vlcj-Ereignis-Thread), und unter Windows gilt
 * `SetThreadExecutionState` je Thread, Setzen und Zurücknehmen müssen also vom selben Thread kommen.
 */
class SharedIdleInhibitor(private val target: IdleInhibitor, directExecutor: Boolean = false) : IdleInhibitor {
    private val executor = if (directExecutor) null else Executors.newSingleThreadExecutor { r -> Thread(r, "idle-inhibitor").apply { isDaemon = true } }
    private var holders = 0

    @Synchronized
    override fun inhibit() {
        if (holders++ == 0) run { target.inhibit() }
    }

    @Synchronized
    override fun release() {
        if (holders == 0) return
        if (--holders == 0) run { target.release() }
    }

    private fun run(call: () -> Unit) {
        val guarded = { runCatching(call).onFailure { System.err.println("Leerlauf-Sperre: ${it.message}") }; Unit }
        executor?.execute(guarded) ?: guarded()
    }
}

/** Die Sperre der laufenden App, passend zum Betriebssystem. */
object SystemIdleInhibitor : IdleInhibitor by SharedIdleInhibitor(
    System.getProperty("os.name").orEmpty().let { os ->
        when {
            os.startsWith("Windows") -> WindowsIdleInhibitor()
            os.startsWith("Linux") -> DbusScreenSaverInhibitor()
            else -> NoIdleInhibitor
        }
    },
)

private object NoIdleInhibitor : IdleInhibitor {
    override fun inhibit() = Unit
    override fun release() = Unit
}

/**
 * Linux: `org.freedesktop.ScreenSaver.Inhibit` über die Sitzungs-D-Bus (GNOME, KDE, Xfce). Direkt über `libdbus-1`
 * per JNA (kommt mit vlcj), damit keine D-Bus-Bibliothek dazukommt. Die Sperre gilt, solange die Verbindung besteht –
 * die Verbindung bleibt daher bis zum Ende der App offen; ein `gdbus call` aus einem Skript würde sofort wieder freigeben.
 */
internal class DbusScreenSaverInhibitor : IdleInhibitor {
    @Suppress("FunctionNaming", "FunctionName")
    private interface LibDbus : Library {
        fun dbus_error_init(error: Pointer)
        fun dbus_error_is_set(error: Pointer): Int
        fun dbus_error_free(error: Pointer)
        fun dbus_bus_get_private(type: Int, error: Pointer): Pointer?
        fun dbus_connection_set_exit_on_disconnect(connection: Pointer, exitOnDisconnect: Int)
        fun dbus_message_new_method_call(destination: String, path: String, iface: String, method: String): Pointer?
        fun dbus_message_append_args(message: Pointer, firstArgType: Int, vararg args: Any?): Int
        fun dbus_connection_send_with_reply_and_block(connection: Pointer, message: Pointer, timeoutMs: Int, error: Pointer): Pointer?
        fun dbus_message_get_args(message: Pointer, error: Pointer, firstArgType: Int, vararg args: Any?): Int
        fun dbus_message_unref(message: Pointer)
    }

    private val lib by lazy { Native.load("dbus-1", LibDbus::class.java) }
    private var connection: Pointer? = null
    private var cookie: Int? = null

    @Synchronized
    override fun inhibit() {
        if (cookie != null) return
        val reply = IntByReference()
        call("Inhibit", TYPE_STRING, StringArray(arrayOf(APP_NAME)), TYPE_STRING, StringArray(arrayOf(REASON))) { msg, err ->
            check(lib.dbus_message_get_args(msg, err, TYPE_UINT32, reply, TYPE_INVALID) != 0) { errorText(err) }
        }
        cookie = reply.value
    }

    @Synchronized
    override fun release() {
        val c = cookie ?: return
        cookie = null
        call("UnInhibit", TYPE_UINT32, IntByReference(c)) { _, _ -> }
    }

    private fun call(method: String, firstType: Int, vararg rest: Any, readReply: (Pointer, Pointer) -> Unit) {
        val err = Memory(DBUS_ERROR_SIZE).also { lib.dbus_error_init(it) }
        try {
            val conn = connection ?: lib.dbus_bus_get_private(BUS_SESSION, err)
                ?.also { lib.dbus_connection_set_exit_on_disconnect(it, 0) } // Standard wäre: App beenden, wenn der Bus wegfällt
                ?.also { connection = it }
                ?: error(errorText(err))
            val msg = lib.dbus_message_new_method_call(SERVICE, PATH, SERVICE, method) ?: error("$method: keine Nachricht")
            try {
                check(lib.dbus_message_append_args(msg, firstType, *rest, TYPE_INVALID) != 0) { "$method: Argumente" }
                val reply = lib.dbus_connection_send_with_reply_and_block(conn, msg, TIMEOUT_MS, err) ?: error(errorText(err))
                try { readReply(reply, err) } finally { lib.dbus_message_unref(reply) }
            } finally {
                lib.dbus_message_unref(msg)
            }
        } finally {
            if (lib.dbus_error_is_set(err) != 0) lib.dbus_error_free(err)
        }
    }

    /** `DBusError` beginnt mit `name` und `message` (je ein `const char*`). */
    private fun errorText(err: Pointer): String =
        listOfNotNull(err.getPointer(0)?.getString(0), err.getPointer(Native.POINTER_SIZE.toLong())?.getString(0)).joinToString(": ").ifEmpty { "D-Bus" }

    private companion object {
        const val SERVICE = "org.freedesktop.ScreenSaver"
        const val PATH = "/org/freedesktop/ScreenSaver"
        const val APP_NAME = "IPTV Player"
        const val REASON = "Wiedergabe"
        const val BUS_SESSION = 0
        const val TYPE_INVALID = 0
        const val TYPE_STRING = 's'.code
        const val TYPE_UINT32 = 'u'.code
        const val TIMEOUT_MS = 2000
        /** `sizeof(DBusError)` sind 32 Bytes auf 64 Bit – großzügig, die Bibliothek schreibt nur hinein. */
        const val DBUS_ERROR_SIZE = 64L
    }
}

/** Windows: `SetThreadExecutionState` – Bildschirm und System bleiben an, bis derselbe Thread zurücknimmt. */
internal class WindowsIdleInhibitor : IdleInhibitor {
    @Suppress("FunctionNaming", "FunctionName")
    private interface Kernel32 : Library {
        fun SetThreadExecutionState(flags: Int): Int
    }

    private val kernel32 by lazy { Native.load("kernel32", Kernel32::class.java) }

    override fun inhibit() { kernel32.SetThreadExecutionState(ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED) }
    override fun release() { kernel32.SetThreadExecutionState(ES_CONTINUOUS) }

    private companion object {
        const val ES_CONTINUOUS = 0x80000000.toInt()
        const val ES_SYSTEM_REQUIRED = 0x1
        const val ES_DISPLAY_REQUIRED = 0x2
    }
}
