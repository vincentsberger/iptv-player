package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.CountryOption
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Länderfilter für Live-TV: Auswahl über das Anbieter-Kürzel der Kategorie („DE | Sport“). */
class CountryFilterTest {

    private val tagged = mapOf(
        "get_live_categories" to """[{"category_id":"1","category_name":"DE | Nachrichten"},{"category_id":"2","category_name":"AT | Sport"},
            {"category_id":"3","category_name":"DE | Sport"},{"category_id":"4","category_name":"FOR ADULTS"}]""",
        "get_live_streams" to """[{"stream_id":1,"name":"Das Erste","category_id":"1"},{"stream_id":2,"name":"ORF Sport","category_id":"2"},
            {"stream_id":3,"name":"Sport1","category_id":"3"},{"stream_id":4,"name":"Nacht","category_id":"4"}]""",
    )

    private fun app(kv: InMemoryKeyValueStore = InMemoryKeyValueStore()) = testAppState(http = SampleData.http(tagged), kv = kv)

    private fun AppState.liveCategoryIds() = section(Section.LIVE).categories.map { it.id }

    @Test
    fun noSelectionShowsEverything() {
        val app = app()
        assertEquals(listOf("1", "2", "3", "4"), app.liveCategoryIds())
        assertEquals(emptySet(), app.activeLiveCountries)
    }

    @Test
    fun selectedCountryKeepsItsCategoriesAndUntaggedOnes() {
        val app = app()
        app.toggleLiveCountry("DE")
        assertEquals(listOf("1", "3", "4"), app.liveCategoryIds())
        assertEquals(listOf("Das Erste", "Sport1", "Nacht"), app.section(Section.LIVE).items.map { it.name })
        app.query = "orf"
        assertNull(app.searchResults[Section.LIVE])
    }

    @Test
    fun availableCountriesAreCountedAndSorted() {
        val app = app()
        val expected = listOf(CountryOption("DE", 2), CountryOption("AT", 1))
        assertEquals(expected, app.availableLiveCountries())
        app.toggleLiveCountry("AT")
        assertEquals(expected, app.availableLiveCountries(), "Auswahl hängt nicht vom Filter selbst ab")
    }

    @Test
    fun selectionSurvivesRestartAndIsPerAccount() = runTest {
        val kv = InMemoryKeyValueStore()
        app(kv).toggleLiveCountry("DE")

        val app = app(kv)
        assertEquals(setOf("DE"), app.liveCountries, "Auswahl muss einen Neustart überleben")
        val firstId = app.activeAccountId!!

        app.addAccount()
        app.login(Account.Xtream("other.example", "u", "p"))
        assertEquals(emptySet(), app.liveCountries, "zweiter Anbieter hat seine eigene Auswahl")

        app.switchAccount(firstId)
        assertEquals(setOf("DE"), app.liveCountries)
    }

    @Test
    fun missingCountryDisablesFilterButStaysSelectable() {
        val app = app()
        app.toggleLiveCountry("FR")
        assertEquals(listOf("1", "2", "3", "4"), app.liveCategoryIds())
        assertEquals(emptySet(), app.activeLiveCountries)
        assertEquals(CountryOption("FR", 0), app.availableLiveCountries().last())
    }

    @Test
    fun openCategoryFallsBackToAllWhenFilteredOut() {
        val app = app()
        app.selectTab(Tab.LIVE)
        app.liveCategory = "2"
        app.toggleLiveCountry("DE")
        assertEquals(AppState.LIVE_ALL, app.liveCategory)
    }

    @Test
    fun filterCombinesWithHiddenAndSort() {
        val app = app()
        app.toggleHidden(Section.LIVE, "3")
        app.sortAz = true
        app.toggleLiveCountry("DE")
        assertEquals(listOf("4", "1"), app.liveCategoryIds()) // A–Z nach Name ohne Kürzel: „FOR ADULTS“ vor „Nachrichten“
    }

    @Test
    fun favoritesOnHomeIgnoreTheFilterButLiveFavoritesFollowIt() {
        val app = app()
        val orf = app.section(Section.LIVE).items.first { it.name == "ORF Sport" }
        app.toggleFavorite(orf)
        app.toggleLiveCountry("DE")
        assertEquals(listOf("ORF Sport"), app.favoriteItems(Section.LIVE).map { it.name }, "Startseite/Favoriten-Tab: bewusst gewählt")
        assertEquals(emptyList(), app.liveChannelsOf(AppState.LIVE_FAV), "Kategorie „Favoriten“ in Live-TV folgt dem Filter")
    }

    @Test
    fun clearShowsEverythingAgain() {
        val app = app()
        app.toggleLiveCountry("DE")
        app.clearLiveCountries()
        assertEquals(listOf("1", "2", "3", "4"), app.liveCategoryIds())
        assertEquals(emptySet(), app.liveCountries)
    }

    private val taggedMovies = tagged + mapOf(
        "get_vod_categories" to """[{"category_id":"10","category_name":"DE | Action"},{"category_id":"11","category_name":"EN | Action"}]""",
        "get_vod_streams" to """[{"stream_id":100,"name":"Tatort","category_id":"10","container_extension":"mp4"},
            {"stream_id":101,"name":"Heat","category_id":"11","container_extension":"mp4"}]""",
    )

    @Test
    fun moviesHaveTheirOwnFilterAndFavoritesIgnoreIt() = runTest {
        val kv = InMemoryKeyValueStore()
        val app = testAppState(http = SampleData.http(taggedMovies), kv = kv)
        app.ensureLoaded(Section.MOVIES)
        assertEquals(listOf(CountryOption("DE", 1), CountryOption("EN", 1)), app.availableCountries(Section.MOVIES))
        val heat = app.section(Section.MOVIES).items.single { it.name == "Heat" }
        app.toggleFavorite(heat)

        app.toggleCountry(Section.MOVIES, "DE")
        assertEquals(listOf("Tatort"), app.section(Section.MOVIES).items.map { it.name })
        assertEquals(listOf("1", "2", "3", "4"), app.liveCategoryIds(), "Live bleibt ungefiltert")
        assertEquals(listOf("Heat"), app.favoriteItems(Section.MOVIES).map { it.name }, "Favoriten ohne Länderfilter")
        assertEquals(setOf("DE"), testAppState(http = SampleData.http(taggedMovies), kv = kv).countries(Section.MOVIES), "gespeichert")

        app.clearCountries(Section.MOVIES)
        assertEquals(2, app.section(Section.MOVIES).items.size)
    }

    /** Wie bei vielen Anbietern: Kategorie ohne Kürzel („NETFLIX SERIES“), das Kürzel steht im Titel. */
    @Test
    fun seriesAreFilteredByTheTagInTheirTitle() = runTest {
        val data = tagged + mapOf(
            "get_series_categories" to """[{"category_id":"20","category_name":"NETFLIX SERIES"}]""",
            "get_series" to """[{"series_id":1,"name":"DE - Dark (2017)","category_id":"20"},
                {"series_id":2,"name":"EN - Stranger Things (2016)","category_id":"20"},
                {"series_id":3,"name":"Ohne Kürzel","category_id":"20"}]""",
        )
        val app = testAppState(http = SampleData.http(data))
        app.ensureLoaded(Section.SERIES)
        assertEquals(listOf(CountryOption("DE", 1), CountryOption("EN", 1)), app.availableCountries(Section.SERIES))
        app.toggleCountry(Section.SERIES, "DE")
        assertEquals(listOf("DE - Dark (2017)", "Ohne Kürzel"), app.section(Section.SERIES).items.map { it.name })
        assertEquals(listOf("20"), app.section(Section.SERIES).categories.map { it.id })
        app.query = "stranger"
        assertNull(app.searchResults[Section.SERIES], "gefilterte Titel auch nicht in der Suche")
    }
}
