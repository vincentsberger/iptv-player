package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** „Rückgängig“ stellt einen festen Zustand her – zweimal ausgelöst schaltet es nicht wieder zurück. */
class UndoStateTest {

    @Test
    fun setFavoriteIsIdempotent() {
        val app = testAppState()
        val item = app.section(Section.LIVE).items.first()
        app.setFavorite(item, true)
        app.setFavorite(item, true)
        assertTrue(item.favKey in app.favorites)
        app.setFavorite(item, false)
        app.setFavorite(item, false)
        assertFalse(item.favKey in app.favorites)
    }

    @Test
    fun setHiddenIsIdempotent() {
        val app = testAppState()
        app.setHidden(Section.LIVE, "1", true)
        app.setHidden(Section.LIVE, "1", true)
        assertTrue(app.isHidden(Section.LIVE, "1"))
        app.setHidden(Section.LIVE, "1", false)
        app.setHidden(Section.LIVE, "1", false)
        assertFalse(app.isHidden(Section.LIVE, "1"))
    }
}
