package de.vincentsberger.iptvplayer.desktop.player

import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class IdleInhibitorTest {
    private class Recorder : IdleInhibitor {
        val calls = mutableListOf<String>()
        override fun inhibit() { calls += "inhibit" }
        override fun release() { calls += "release" }
    }

    @Test
    fun playbackAwakeForwardsOnlyChanges() {
        val rec = Recorder()
        val awake = PlaybackAwake(rec)
        // VLC meldet „playing“ nach jedem Nachpuffern erneut – nur der Wechsel zählt.
        awake.update(true); awake.update(true); awake.update(false); awake.update(false); awake.update(true)
        assertEquals(listOf("inhibit", "release", "inhibit"), rec.calls)
    }

    @Test
    fun sharedInhibitorHoldsWhileAnyPlayerPlays() {
        val rec = Recorder()
        val shared = SharedIdleInhibitor(rec, directExecutor = true)
        shared.inhibit(); shared.inhibit(); shared.release()
        assertEquals(listOf("inhibit"), rec.calls)
        shared.release()
        assertEquals(listOf("inhibit", "release"), rec.calls)
        // Ein überzähliges Freigeben darf den Zähler nicht ins Minus treiben.
        shared.release(); shared.inhibit()
        assertEquals(listOf("inhibit", "release", "inhibit"), rec.calls)
    }

    /** Echter D-Bus-Aufruf – nur in einer GNOME-Sitzung (lokal), nicht in CI. */
    @Test
    fun dbusInhibitIsSeenByGnomeSession() {
        assumeTrue(System.getProperty("os.name").startsWith("Linux"))
        assumeTrue(System.getenv("DBUS_SESSION_BUS_ADDRESS") != null)
        assumeTrue(System.getenv("XDG_CURRENT_DESKTOP").orEmpty().contains("GNOME"))
        val before = inhibitedActions()
        assumeTrue(before != null && before and IDLE_FLAG == 0)
        val dbus = DbusScreenSaverInhibitor()
        dbus.inhibit()
        assertEquals(IDLE_FLAG, inhibitedActions()!! and IDLE_FLAG, "GNOME kennt keine Leerlauf-Sperre")
        dbus.release()
        assertEquals(0, inhibitedActions()!! and IDLE_FLAG, "Leerlauf-Sperre nicht freigegeben")
    }

    private fun inhibitedActions(): Int? = runCatching {
        val p = ProcessBuilder(
            "gdbus", "call", "--session", "--dest", "org.gnome.SessionManager", "--object-path", "/org/gnome/SessionManager",
            "--method", "org.freedesktop.DBus.Properties.Get", "org.gnome.SessionManager", "InhibitedActions",
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        Regex("""uint32 (\d+)""").find(out)?.groupValues?.get(1)?.toInt()
    }.getOrNull()

    private companion object {
        /** GNOME-Flag „Leerlauf“ in `InhibitedActions`. */
        const val IDLE_FLAG = 8
    }
}
