package de.vincentsberger.iptvplayer.desktop

import de.vincentsberger.iptvplayer.shared.data.KeyValueStore

/**
 * Lage und Größe eines Fensters oder Bildschirmbereichs in dp. Unter Windows und Linux rechnet AWT bei
 * Skalierung (125 %, 150 %) bereits in logischen Punkten – die entsprechen den dp von Compose.
 */
internal data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    /** Ist die Titelleiste (obere [TITLE_BAR] dp) auf [screen] so weit sichtbar, dass man das Fenster greifen kann? */
    fun titleBarVisibleOn(screen: Bounds): Boolean {
        val across = minOf(x + width, screen.x + screen.width) - maxOf(x, screen.x)
        val down = minOf(y + TITLE_BAR, screen.y + screen.height) - maxOf(y, screen.y)
        return across >= MIN_VISIBLE && down >= TITLE_BAR / 2
    }
}

/** Gemerkter Fensterzustand: Lage und Größe im normalen Zustand, dazu, ob das Fenster maximiert war. */
internal data class SavedWindow(val bounds: Bounds, val maximized: Boolean) {
    fun encode(): String = with(bounds) { "$x,$y,$width,$height,${if (maximized) 1 else 0}" }

    companion object {
        fun decode(raw: String?): SavedWindow? {
            val p = raw?.split(',')?.map { it.trim().toIntOrNull() ?: return null } ?: return null
            if (p.size != FIELDS) return null
            val (x, y) = p
            val (w, h) = p.drop(2)
            val b = Bounds(x, y, w, h)
            if (b.width <= 0 || b.height <= 0) return null
            return SavedWindow(b, p.last() == 1)
        }
    }
}

/** Mindestgröße: reicht für das schmale Layout und passt auch auf Full HD mit 150 % (≈ 1280×670 nutzbar). */
internal const val MIN_WINDOW_WIDTH = 1000
internal const val MIN_WINDOW_HEIGHT = 600

private const val MAX_START_WIDTH = 1400
private const val MAX_START_HEIGHT = 860
private const val START_SHARE = 0.9
private const val MIN_VISIBLE = 100
private const val TITLE_BAR = 40
private const val KEY = "desktop.window"
private const val FIELDS = 5

/** Mindestgröße, die auf [work] auch passt (sehr kleine Bildschirme). */
internal fun minimumSize(work: Bounds): Pair<Int, Int> =
    minOf(MIN_WINDOW_WIDTH, work.width) to minOf(MIN_WINDOW_HEIGHT, work.height)

/** Größe beim ersten Start: 90 % des Arbeitsbereichs, höchstens 1400×860, mindestens die Mindestgröße – nie größer als [work]. */
internal fun initialSize(work: Bounds): Pair<Int, Int> {
    val (minW, minH) = minimumSize(work)
    val w = (work.width * START_SHARE).toInt().coerceIn(minW, MAX_START_WIDTH).coerceAtMost(work.width)
    val h = (work.height * START_SHARE).toInt().coerceIn(minH, MAX_START_HEIGHT).coerceAtMost(work.height)
    return w to h
}

/**
 * Lage beim Start: der gemerkte Zustand, solange seine Titelleiste noch auf einem der [screens] liegt (Monitor
 * abgesteckt, Auflösung geändert) – sonst die Startgröße mittig im Arbeitsbereich. Zu große Fenster werden auf den
 * Arbeitsbereich verkleinert. Null-Lage heißt „mittig“.
 */
internal fun startBounds(saved: SavedWindow?, work: Bounds, screens: List<Bounds>): SavedWindow {
    val (minW, minH) = minimumSize(work)
    val b = saved?.bounds
    if (b != null && screens.any { b.titleBarVisibleOn(it) }) {
        val w = b.width.coerceIn(minW, maxOf(minW, work.width))
        val h = b.height.coerceIn(minH, maxOf(minH, work.height))
        return SavedWindow(b.copy(width = w, height = h), saved.maximized)
    }
    val (w, h) = initialSize(work)
    return SavedWindow(Bounds(work.x + (work.width - w) / 2, work.y + (work.height - h) / 2, w, h), saved?.maximized == true)
}

/** Fensterzustand im Einstellungsspeicher der App. */
internal class WindowPrefs(private val kv: KeyValueStore) {
    fun load(): SavedWindow? = SavedWindow.decode(kv.getString(KEY))
    fun save(w: SavedWindow) = kv.putString(KEY, w.encode())
}

/**
 * Startlage aus [prefs], Mitschreiben der Lage im normalen Zustand und Sichern beim Schließen. Maximiert oder im
 * Vollbild zählt die Größe davor – sonst öffnete das Fenster nach dem Entmaximieren bildschirmfüllend.
 */
internal class WindowMemory(private val prefs: WindowPrefs, val work: Bounds, screens: List<Bounds>) {
    val start: SavedWindow = startBounds(prefs.load(), work, screens)
    private var lastFloating = start.bounds

    /** Neue Lage im normalen Zustand; null (maximiert, Vollbild, noch nicht platziert) ändert nichts. */
    fun track(floating: Bounds?) {
        if (floating != null) lastFloating = floating
    }

    fun save(maximized: Boolean) = prefs.save(SavedWindow(lastFloating, maximized))
}
