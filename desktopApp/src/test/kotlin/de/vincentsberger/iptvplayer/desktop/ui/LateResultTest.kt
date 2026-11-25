package de.vincentsberger.iptvplayer.desktop.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** Info-Spalte in Live TV: ein langsames Programm kommt nach dem Ersatz doch noch an (Review-Befund 5). */
class LateResultTest {

    @Test
    fun fastResultIsEmittedOnce() = runBlocking {
        val got = mutableListOf<String>()
        awaitWithFallback(200, "leer", { "Tagesschau" }) { got += it }
        assertEquals(listOf("Tagesschau"), got)
    }

    @Test
    fun slowResultFollowsTheFallback() = runBlocking {
        val got = mutableListOf<String>()
        awaitWithFallback(20, "leer", { delay(100); "Tagesschau" }) { got += it }
        assertEquals(listOf("leer", "Tagesschau"), got)
    }
}
