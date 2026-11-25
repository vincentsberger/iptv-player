package de.vincentsberger.iptvplayer.desktop.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import de.vincentsberger.iptvplayer.shared.Screen
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Ebenen (Grund + Detail-Fenster) und Ursprung der Einblendung an der angeklickten Kachel. */
class DetailAnchorTest {

    private val movie = Item("1", "Arrival", null, "sf", "http://x/1.mp4", Section.MOVIES)
    private val series = Item("2", "Babylon Berlin", null, "drama", "", Section.SERIES)
    private val modal = Rect(100f, 50f, 1060f, 850f)

    @Test
    fun originIsCenterWithoutTile() {
        assertEquals(TransformOrigin.Center, modalTransformOrigin(null, modal))
    }

    @Test
    fun originFollowsTileRelativeToModal() {
        assertEquals(TransformOrigin(0.25f, 0.5f), modalTransformOrigin(Offset(340f, 450f), modal))
    }

    @Test
    fun originOutsideModalIsClamped() {
        assertEquals(TransformOrigin(0f, 0f), modalTransformOrigin(Offset(0f, 0f), modal))
        assertEquals(TransformOrigin(1f, 1f), modalTransformOrigin(Offset(2000f, 2000f), modal))
    }

    @Test
    fun detailScreensKeepHomeAsBase() {
        assertEquals(Screen.Home, baseScreen(Screen.Movie(movie)))
        assertEquals(Screen.Home, baseScreen(Screen.Series(series)))
        assertIs<Screen.Player>(baseScreen(Screen.Player(listOf(movie), 0)))
    }

    @Test
    fun detailItemOnlyForMovieAndSeries() {
        assertEquals(movie, detailItemOf(Screen.Movie(movie)))
        assertEquals(series, detailItemOf(Screen.Series(series)))
        assertNull(detailItemOf(Screen.Home))
    }
}
