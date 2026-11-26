package de.vincentsberger.iptvplayer.shared.state

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import de.vincentsberger.iptvplayer.shared.SectionState
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.CountryOption
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.data.splitProviderTag

/** Die Listen des Anbieters, wie die Oberflächen sie zeigen: Rohdaten, ausgeblendete Kategorien, Länderfilter, Sortierung. */
interface Library {
    /** Rohdaten je Bereich, so wie der Anbieter sie liefert. Die Oberflächen lesen [section] (sortiert, gefiltert). */
    val sections: SnapshotStateMap<Section, SectionState>
    fun section(s: Section): SectionState
    val hidden: Set<String>
    fun isHidden(s: Section, categoryId: String): Boolean
    fun setHidden(s: Section, categoryId: String, hide: Boolean)
    fun toggleHidden(s: Section, categoryId: String)
    fun hiddenCategories(s: Section): List<Category>
    fun countries(s: Section): Set<String>
    fun activeCountries(s: Section): Set<String>
    fun availableCountries(s: Section): List<CountryOption>
    fun toggleCountry(s: Section, tag: String)
    fun clearCountries(s: Section)
    val liveCountries: Set<String>
    val activeLiveCountries: Set<String>
    fun availableLiveCountries(): List<CountryOption>
    fun toggleLiveCountry(tag: String)
    fun clearLiveCountries()
}

/** Je Anbieter: ausgeblendete Kategorien und Länder kommen aus dem Speicher des aktiven Anbieters ([reloadForAccount]). */
class LibraryStore(private val store: Storage, private val settings: Settings) : Library {

    override val sections = mutableStateMapOf<Section, SectionState>()

    /** Nach jeder Änderung der Länderauswahl – AppState korrigiert dann ggf. die offene Live-Kategorie. */
    internal var afterCountriesChanged: (Section) -> Unit = {}

    private val sortAz get() = settings.sortAz
    private val sortByYear get() = settings.sortByYear

    override var hidden by mutableStateOf(store.hiddenCategories()); private set

    private fun hiddenKey(s: Section, categoryId: String) = "${s.name}:$categoryId"

    override fun isHidden(s: Section, categoryId: String) = hiddenKey(s, categoryId) in hidden

    /** Kategorie aus- bzw. wieder einblenden. Ausgeblendete Kategorien fehlen auch in „Alle“ und in der Suche. */
    /** Kategorie gezielt aus- oder einblenden (für „Rückgängig“: schaltet nie versehentlich zurück). */
    override fun setHidden(s: Section, categoryId: String, hide: Boolean) {
        if (isHidden(s, categoryId) != hide) toggleHidden(s, categoryId)
    }

    override fun toggleHidden(s: Section, categoryId: String) {
        val k = hiddenKey(s, categoryId)
        hidden = if (k in hidden) hidden - k else hidden + k
        store.saveHiddenCategories(hidden)
    }

    /** Ausgeblendete Kategorien eines Bereichs (für die Einstellungen). */
    override fun hiddenCategories(s: Section): List<Category> =
        sections[s]?.categories.orEmpty().filter { isHidden(s, it.id) }

    // ---------- Länderfilter (Live TV, Filme, Serien) ----------

    /** Gewählte Länderkürzel je Bereich des aktiven Anbieters; leer = alle Länder. */
    private val countriesState = mutableStateMapOf<Section, Set<String>>().apply { Section.entries.forEach { put(it, store.countries(it)) } }

    override fun countries(s: Section): Set<String> = countriesState[s].orEmpty()

    /** Gewählte Kürzel, die der Anbieter auch liefert. Leer = Filter aus – nie eine leere Liste wegen veralteter Auswahl. */
    /**
     * Länderkürzel je Film/Serie (Schlüssel [Item.favKey]): zuerst aus dem Titel („EN - …“, „DE - …“), sonst aus der
     * Kategorie. Viele Anbieter kennzeichnen Filme und Serien nur im Titel – Kategorien wie „NETFLIX MOVIES“ haben
     * keins. Live TV filtert weiter nach Kategorie („DE | Sport“). Gecacht: bei 140 000 Filmen nicht je Zeichnen neu.
     */
    private val itemTags = Section.entries.associateWith { s ->
        derivedStateOf {
            val raw = sections[s]
            if (s == Section.LIVE || raw == null) emptyMap()
            else {
                val categoryTag = raw.categories.associate { it.id to splitProviderTag(it.name).tag }
                raw.items.mapNotNull { item -> (item.tagged.tag ?: categoryTag[item.categoryId])?.let { item.favKey to it } }.toMap()
            }
        }
    }

    private fun tagsIn(s: Section): Set<String> =
        if (s == Section.LIVE) sections[s]?.categories.orEmpty().mapNotNullTo(HashSet()) { splitProviderTag(it.name).tag }
        else itemTags.getValue(s).value.values.toHashSet()

    private val activeCountriesState = Section.entries.associateWith { s ->
        derivedStateOf {
            val chosen = countries(s)
            if (chosen.isEmpty()) emptySet() else chosen intersect tagsIn(s)
        }
    }

    override fun activeCountries(s: Section): Set<String> = activeCountriesState.getValue(s).value

    /**
     * Wählbare Kürzel mit Anzahl (häufigste zuerst); gewählte, aber fehlende Kürzel mit 0 am Ende.
     * Live TV zählt Kategorien, Filme und Serien zählen Titel.
     */
    override fun availableCountries(s: Section): List<CountryOption> {
        val counts = if (s == Section.LIVE) sections[s]?.categories.orEmpty().mapNotNull { splitProviderTag(it.name).tag }.groupingBy { it }.eachCount()
        else itemTags.getValue(s).value.values.groupingBy { it }.eachCount()
        val present = counts.map { (tag, n) -> CountryOption(tag, n) }.sortedWith(compareByDescending<CountryOption> { it.count }.thenBy { it.tag })
        return present + (countries(s) - counts.keys).sorted().map { CountryOption(it, 0) }
    }

    override fun toggleCountry(s: Section, tag: String) = saveCountries(s, countries(s).let { if (tag in it) it - tag else it + tag })

    override fun clearCountries(s: Section) = saveCountries(s, emptySet())

    private fun saveCountries(s: Section, tags: Set<String>) {
        countriesState[s] = tags
        store.saveCountries(s, tags)
        afterCountriesChanged(s)
    }

    // Kurzformen für Live TV (so von allen Apps und Tests genutzt).
    override val liveCountries: Set<String> get() = countries(Section.LIVE)
    override val activeLiveCountries: Set<String> get() = activeCountries(Section.LIVE)
    override fun availableLiveCountries(): List<CountryOption> = availableCountries(Section.LIVE)
    override fun toggleLiveCountry(tag: String) = toggleCountry(Section.LIVE, tag)
    override fun clearLiveCountries() = clearCountries(Section.LIVE)

    /** Nur Live TV: ganze Kategorien nach ihrem Kürzel (Filme/Serien filtern Titel, siehe [applyPrefs]). */
    private fun isFilteredCountry(s: Section, category: Category): Boolean {
        if (s != Section.LIVE) return false
        val active = activeCountries(s)
        if (active.isEmpty()) return false
        val tag = splitProviderTag(category.name).tag ?: return false
        return tag !in active
    }

    // Gecacht je Bereich: rechnet nur neu, wenn sich Rohdaten, Sortierung, Ausblendungen oder Länderfilter ändern.
    private val views = Section.entries.associateWith { s -> derivedStateOf { applyPrefs(s, sections[s] ?: SectionState()) } }

    // Ohne Länderfilter – für Favoriten auf Startseite/Favoriten-Tab (dort steht, was man bewusst gewählt hat).
    private val withoutCountryFilterViews = Section.entries.associateWith { s ->
        derivedStateOf { applyPrefs(s, sections[s] ?: SectionState(), withCountryFilter = false) }
    }

    private fun applyPrefs(s: Section, raw: SectionState, withCountryFilter: Boolean = true): SectionState {
        val countryFilter = withCountryFilter && activeCountries(s).isNotEmpty()
        val byYear = sortByYear && s != Section.LIVE
        if (!sortAz && !byYear && !countryFilter && hidden.none { it.startsWith("${s.name}:") }) return raw
        fun List<Item>.sorted(): List<Item> {
            // Schlüssel je Eintrag einmal berechnen – sortedBy ruft den Selektor bei jedem Vergleich auf, und
            // splitProviderTag ist teuer (mehrere Regex-Läufe); bei 30 000 Sendern sonst Sekunden.
            val az = if (sortAz) map { it to it.tagged.name.lowercase() }.sortedBy { it.second }.map { it.first } else this
            return if (byYear) az.map { it to (it.tagged.year?.toIntOrNull() ?: 0) }.sortedByDescending { it.second }.map { it.first } else az
        }
        val visible = raw.categories.filterNot { isHidden(s, it.id) || (countryFilter && isFilteredCountry(s, it)) }
        val visibleIds = visible.mapTo(HashSet()) { it.id }
        // Filme/Serien: einzelne Titel nach ihrem Kürzel; ohne Kürzel bleiben sie sichtbar (wie Kategorien ohne Kürzel).
        val titleFilter = countryFilter && s != Section.LIVE
        val active = activeCountries(s)
        val tags = if (titleFilter) itemTags.getValue(s).value else emptyMap()
        fun keep(item: Item) = item.categoryId in visibleIds && (!titleFilter || tags[item.favKey].let { it == null || it in active })
        val byCategory = if (titleFilter) raw.items.filter(::keep).groupBy { it.categoryId }
        else raw.byCategory.filterKeys { it in visibleIds }
        val categories = if (titleFilter) visible.filter { byCategory.containsKey(it.id) } else visible
        return raw.copy(
            categories = if (sortAz) categories.map { it to splitProviderTag(it.name).name.lowercase() }.sortedBy { it.second }.map { it.first } else categories,
            items = raw.items.filter(::keep).sorted(),
            byCategory = byCategory.mapValues { it.value.sorted() },
        )
    }

    /** Bereich so, wie die Oberfläche ihn zeigt: ausgeblendete Kategorien entfernt, ggf. A–Z sortiert. */
    override fun section(s: Section): SectionState = views.getValue(s).value

    /** Bereich ohne Länderfilter – für Favoriten auf Startseite/Favoriten-Tab (dort steht, was man bewusst gewählt hat). */
    internal fun sectionWithoutCountryFilter(s: Section): SectionState = withoutCountryFilterViews.getValue(s).value

    /** Ausgeblendete Kategorien und Länder des (neuen) aktiven Anbieters laden. */
    internal fun reloadForAccount() {
        hidden = store.hiddenCategories()
        Section.entries.forEach { countriesState[it] = store.countries(it) }
    }
}
