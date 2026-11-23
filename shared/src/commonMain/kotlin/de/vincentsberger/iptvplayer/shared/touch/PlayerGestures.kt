package de.vincentsberger.iptvplayer.shared.touch

import kotlin.math.abs

// Touch-Bedienung des Players (iPhone) als reine Regeln – die Oberfläche misst nur und fragt hier nach.

/** Was eine Berührung der Bildfläche auslöst. */
enum class TouchAction { NONE, TOGGLE_CONTROLS, NEXT_CHANNEL, PREVIOUS_CHANNEL, OPEN_CHANNEL_LIST, SEEK_BACK, SEEK_FORWARD }

/** Mindestweg eines Wischers, damit er zählt – kürzere Bewegungen sind Zittern oder Halten. */
const val SWIPE_MIN_DP = 60f
/** Breite des linken Randstreifens, aus dem die Senderliste aufgezogen wird. */
const val EDGE_DP = 24f
/** Längere Bewegungen sind Ziehen, kein Wischen. */
const val SWIPE_MAX_MS = 600L

/**
 * Tippen auf die Bildfläche. Einfachtipp blendet die Bedienelemente ein/aus. Doppeltipp spult bei Filmen und Folgen
 * im linken Drittel zurück, im rechten vor; Live hat nichts zum Spulen.
 */
fun tapAction(x: Float, width: Float, doubleTap: Boolean, isLive: Boolean): TouchAction = when {
    !doubleTap || isLive -> TouchAction.TOGGLE_CONTROLS
    x < width / 3 -> TouchAction.SEEK_BACK
    x > width * 2 / 3 -> TouchAction.SEEK_FORWARD
    else -> TouchAction.TOGGLE_CONTROLS
}

/**
 * Wischen über die Bildfläche (Pixel; [density] = Pixel je dp). Nur Live: vom linken Rand nach rechts öffnet die
 * Senderliste, ein klar senkrechter, zügiger Wischer schaltet um – nach oben zum nächsten Sender (wie eine Liste,
 * die man weiterschiebt), nach unten zum vorigen.
 */
fun swipeAction(startX: Float, dx: Float, dy: Float, durationMs: Long, density: Float, isLive: Boolean): TouchAction {
    if (!isLive) return TouchAction.NONE
    val min = SWIPE_MIN_DP * density
    if (startX <= EDGE_DP * density && dx >= min) return TouchAction.OPEN_CHANNEL_LIST
    val vertical = abs(dy) >= min && abs(dy) >= 2 * abs(dx) && durationMs <= SWIPE_MAX_MS
    return when {
        !vertical -> TouchAction.NONE
        dy < 0 -> TouchAction.NEXT_CHANNEL
        else -> TouchAction.PREVIOUS_CHANNEL
    }
}

/** Was mit der Wiedergabe passiert, wenn die App in den Hintergrund geht. */
enum class BackgroundAction { STOP, PAUSE }

/** Was beim Zurückkommen passiert. */
enum class ForegroundAction { RESTART, STAY_PAUSED }

/** Live wird beendet (ein angehaltener Live-Stream wäre beim Zurückkommen veraltet), Filme/Folgen nur angehalten. */
fun playbackOnBackground(isLive: Boolean): BackgroundAction = if (isLive) BackgroundAction.STOP else BackgroundAction.PAUSE

/** Live startet den Sender neu; Filme/Folgen bleiben an derselben Stelle angehalten, bis man selbst weiterspielt. */
fun playbackOnForeground(isLive: Boolean): ForegroundAction = if (isLive) ForegroundAction.RESTART else ForegroundAction.STAY_PAUSED
