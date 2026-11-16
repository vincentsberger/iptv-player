package de.tvplayer.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Gibt es (mindestens) einen Knoten mit genau diesem Text? Viele Titel stehen doppelt im Baum
 * (Karte + Beschriftung, Liste + Infobereich), deshalb kein onNodeWithText, das genau einen erwartet.
 */
fun ComposeContentTestRule.hasText(text: String): Boolean =
    onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/**
 * „OK drücken“ ohne Fokus: löst den Klick über die Semantik aus.
 * `performClick()` simuliert einen Finger-Tipp – den ignoriert die TV-Surface (sie reagiert auf Tasten).
 */
fun SemanticsNodeInteraction.select(): SemanticsNodeInteraction = performSemanticsAction(SemanticsActions.OnClick)

/**
 * Wie ein Fernseher: kein Touch-Modus. Im Touch-Modus vergibt Compose keinen Tastaturfokus,
 * Fokus-Anfragen der Oberfläche liefen dann ins Leere.
 */
/** Verzögerte Fokus-Anfragen der Oberfläche (delay 100–350 ms) abwarten: Testuhr vorspulen. */
fun ComposeContentTestRule.settle() {
    mainClock.advanceTimeBy(500)
    waitForIdle()
}

fun leaveTouchMode() = InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
