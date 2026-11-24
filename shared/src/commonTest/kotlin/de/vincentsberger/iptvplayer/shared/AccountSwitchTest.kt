package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Jeder Store hält seine Daten je Anbieter – ein Wechsel darf nichts vom anderen zeigen und nichts verlieren. */
class AccountSwitchTest {

    @Test
    fun switchingAccountResetsEveryStore() = runTest {
        val app = testAppState()
        val first = app.activeAccountId!!
        val movie = app.section(Section.MOVIES).items.first()
        val category = app.section(Section.LIVE).categories.first()
        app.toggleFavorite(movie)
        app.toggleHidden(Section.LIVE, category.id)
        app.toggleCountry(Section.LIVE, "DE")
        app.toggleDashboardCategory(Section.MOVIES, movie.categoryId)

        app.addAccount()
        app.login(Account.Xtream("http://anderer.example", "zwei", "geheim"))
        assertTrue(app.favorites.isEmpty() && app.hidden.isEmpty() && app.liveCountries.isEmpty() && app.dashboardCategories.isEmpty())

        app.switchAccount(first)
        assertTrue(movie.favKey in app.favorites)
        assertTrue(app.isHidden(Section.LIVE, category.id))
        assertTrue("DE" in app.liveCountries)
        assertTrue(app.dashboardCategories.isNotEmpty())
    }
}
