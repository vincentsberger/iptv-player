package de.vincentsberger.iptvplayer.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Übersicht der Tastenkürzel in den Einstellungen: nach Ort getrennt, vollständig, ohne Doppelungen. */
class ShortcutHelpTest {

    private fun group(title: String) = shortcutHelp.first { it.title == title }.keys.toMap()

    @Test
    fun groupedByWhereTheyWork() {
        assertEquals(listOf("Überall", "Live-TV-Liste", "Player"), shortcutHelp.map { it.title })
    }

    @Test
    fun noKeyTwiceInOneGroup() {
        shortcutHelp.forEach { g -> assertEquals(g.keys.map { it.first }.distinct().size, g.keys.size, g.title) }
    }

    @Test
    fun listsTheNewAndFormerlyMissingKeys() {
        val everywhere = group("Überall")
        assertTrue("Strg F" in everywhere && "Esc" in everywhere, "Suche und Zurück")
        val player = group("Player")
        assertEquals("Ton & Untertitel", player["T"])
        assertEquals("Statistik", player["I"])
        assertEquals("Lautstärke", player["+ −"])
        assertEquals("Vollbild", player["F"])
        assertEquals("Favorit", group("Live-TV-Liste")["F"])
    }
}
