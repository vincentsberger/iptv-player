package de.vincentsberger.iptvplayer.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowState
import org.junit.Assume.assumeTrue
import java.awt.GraphicsEnvironment
import java.awt.Insets
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenTest {

    @Test
    fun onlyWindowsNeedsBorderlessFullscreen() {
        assertTrue(needsBorderlessFullscreen("Windows 11"))
        assertTrue(needsBorderlessFullscreen("Windows 10"))
        assertFalse(needsBorderlessFullscreen("Linux"))
        assertFalse(needsBorderlessFullscreen("Mac OS X"))
    }

    /**
     * Öffnet ein echtes Fenster – daher nur in der CI (dort virtueller Bildschirm bzw. Windows-Runner),
     * lokal würde es kurz den Bildschirm füllen. Lokal erzwingen: CI=1 ./gradlew :desktopApp:test
     */
    @Test
    fun borderlessFullscreenCoversScreenAndRestoresWindow() {
        assumeTrue(System.getenv("CI") != null && !GraphicsEnvironment.isHeadless())
        val window = onEdt {
            ComposeWindow().apply {
                setContent { Box(Modifier.fillMaxSize()) }
                setBounds(100, 100, 800, 500)
                isVisible = true
            }
        }
        try {
            val windowed = onEdt { window.bounds }
            val fullscreen = FullscreenController(WindowState(), borderless = true).also { it.window = window }
            val noFrame = Insets(0, 0, 0, 0)

            // Skiko und der Fenstermanager schalten asynchron um – auf den Zustand warten statt fester Pausen.
            onEdt { fullscreen.set(true) }
            awaitOnEdt("Vollbild ohne Titelleiste") { window.bounds == window.graphicsConfiguration.bounds && window.insets == noFrame }
            assertTrue(onEdt { window.isUndecorated })

            onEdt { fullscreen.set(false) }
            awaitOnEdt("altes Fenster mit Rahmen") { window.bounds == windowed && !window.isUndecorated }
        } finally {
            onEdt { window.dispose() }
        }
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun awaitOnEdt(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!onEdt(condition)) {
            check(System.currentTimeMillis() < deadline) { "Zeitüberschreitung: $what" }
            Thread.sleep(20)
        }
    }
}
