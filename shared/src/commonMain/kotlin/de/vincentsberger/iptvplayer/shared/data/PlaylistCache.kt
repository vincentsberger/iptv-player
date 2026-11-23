package de.vincentsberger.iptvplayer.shared.data

/**
 * Zwischenspeicher für die Listen eines Anbieters (Antworten des Anbieters unverändert, je Abruf ein Eintrag).
 * Schlüssel sind Aktionsnamen („get_vod_streams.json“, „playlist.m3u“) – nie URLs, die enthalten Zugangsdaten.
 * Lesen liefert null, wenn der Eintrag fehlt oder unlesbar ist; der Aufrufer lädt dann aus dem Netz.
 */
interface PlaylistCacheStore {
    fun readText(key: String): String?
    /** Schreibt vollständig oder gar nicht (temporäre Datei + Umbenennen). */
    fun writeText(key: String, text: String)
    fun <T> readLines(key: String, block: (Sequence<String>) -> T): T?
    fun writeLines(key: String, lines: Sequence<String>)
    /**
     * Zeilen beim Lesen mitschreiben (z. B. eine M3U-Liste direkt aus dem Netz): [parse] verarbeitet die Zeilen,
     * gespeichert wird nur, wenn [keep] dem Ergebnis zustimmt – sonst bleibt der alte Eintrag.
     * Speicherfehler (z. B. voller Speicher) verschluckt die Implementierung: Sie dürfen das Parsen nie abbrechen.
     */
    fun <T> teeLines(key: String, lines: Sequence<String>, parse: (Sequence<String>) -> T, keep: (T) -> Boolean): T
    /** Löscht den ganzen Zwischenspeicher dieses Anbieters. */
    fun clear()
}

/** Kein Zwischenspeicher: liest nichts, schreibt nichts (Standard, z. B. für Tests ohne Cache). */
object NoPlaylistCache : PlaylistCacheStore {
    override fun readText(key: String): String? = null
    override fun writeText(key: String, text: String) = Unit
    override fun <T> readLines(key: String, block: (Sequence<String>) -> T): T? = null
    override fun writeLines(key: String, lines: Sequence<String>) = Unit
    override fun <T> teeLines(key: String, lines: Sequence<String>, parse: (Sequence<String>) -> T, keep: (T) -> Boolean): T = parse(lines)
    override fun clear() = Unit
}
