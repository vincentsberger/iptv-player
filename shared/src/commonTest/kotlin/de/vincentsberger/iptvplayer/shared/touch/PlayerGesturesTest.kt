package de.vincentsberger.iptvplayer.shared.touch

import de.vincentsberger.iptvplayer.shared.touch.TouchAction.NEXT_CHANNEL
import de.vincentsberger.iptvplayer.shared.touch.TouchAction.NONE
import de.vincentsberger.iptvplayer.shared.touch.TouchAction.OPEN_CHANNEL_LIST
import de.vincentsberger.iptvplayer.shared.touch.TouchAction.PREVIOUS_CHANNEL
import de.vincentsberger.iptvplayer.shared.touch.TouchAction.SEEK_BACK
import de.vincentsberger.iptvplayer.shared.touch.TouchAction.SEEK_FORWARD
import de.vincentsberger.iptvplayer.shared.touch.TouchAction.TOGGLE_CONTROLS
import kotlin.test.Test
import kotlin.test.assertEquals

/** Gesten im iPhone-Player: 3 px je dp, Bildfläche 1200 px breit. */
class PlayerGesturesTest {

    @Test
    fun singleTapTogglesControls() {
        assertEquals(TOGGLE_CONTROLS, tapAction(100f, 1200f, doubleTap = false, isLive = false))
        assertEquals(TOGGLE_CONTROLS, tapAction(1100f, 1200f, doubleTap = false, isLive = true))
    }

    @Test
    fun liveDoubleTapNeverSeeks() {
        assertEquals(TOGGLE_CONTROLS, tapAction(100f, 1200f, doubleTap = true, isLive = true))
        assertEquals(TOGGLE_CONTROLS, tapAction(1100f, 1200f, doubleTap = true, isLive = true))
    }

    @Test
    fun vodDoubleTapThirds() {
        assertEquals(SEEK_BACK, tapAction(100f, 1200f, doubleTap = true, isLive = false))
        assertEquals(TOGGLE_CONTROLS, tapAction(600f, 1200f, doubleTap = true, isLive = false))
        assertEquals(SEEK_FORWARD, tapAction(1100f, 1200f, doubleTap = true, isLive = false))
    }

    @Test
    fun swipeUpIsNextChannelDownIsPrevious() {
        assertEquals(NEXT_CHANNEL, swipeAction(600f, 0f, -200f, 200, 3f, isLive = true))
        assertEquals(PREVIOUS_CHANNEL, swipeAction(600f, 0f, 200f, 200, 3f, isLive = true))
    }

    @Test
    fun shortSwipeDoesNothing() = assertEquals(NONE, swipeAction(600f, 0f, -170f, 200, 3f, isLive = true)) // < 60 dp

    @Test
    fun diagonalSwipeDoesNothing() = assertEquals(NONE, swipeAction(600f, 150f, -200f, 200, 3f, isLive = true))

    @Test
    fun slowDragDoesNothing() = assertEquals(NONE, swipeAction(600f, 0f, -300f, 900, 3f, isLive = true))

    @Test
    fun leftEdgeOpensChannelList() {
        assertEquals(OPEN_CHANNEL_LIST, swipeAction(50f, 200f, 10f, 200, 3f, isLive = true))
        // Nicht am Rand begonnen: waagerechtes Wischen bleibt ohne Wirkung.
        assertEquals(NONE, swipeAction(300f, 200f, 10f, 200, 3f, isLive = true))
    }

    @Test
    fun vodSwipeDoesNothing() {
        assertEquals(NONE, swipeAction(600f, 0f, -300f, 200, 3f, isLive = false))
        assertEquals(NONE, swipeAction(50f, 200f, 10f, 200, 3f, isLive = false))
    }

    @Test
    fun backgroundAndForeground() {
        assertEquals(BackgroundAction.STOP, playbackOnBackground(isLive = true))
        assertEquals(BackgroundAction.PAUSE, playbackOnBackground(isLive = false))
        assertEquals(ForegroundAction.RESTART, playbackOnForeground(isLive = true))
        assertEquals(ForegroundAction.STAY_PAUSED, playbackOnForeground(isLive = false))
    }
}
