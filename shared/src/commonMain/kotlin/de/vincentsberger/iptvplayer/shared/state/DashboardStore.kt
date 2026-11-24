package de.vincentsberger.iptvplayer.shared.state

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vincentsberger.iptvplayer.shared.AppState.Companion.EXPIRY_WARNING_MS
import de.vincentsberger.iptvplayer.shared.AppState.Companion.NEW_WINDOW_MS
import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.AccountStatus
import de.vincentsberger.iptvplayer.shared.data.Category
import de.vincentsberger.iptvplayer.shared.data.Item
import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.data.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Startseite je Anbieter: Neues aus gewählten Kategorien (letzte 7 Tage) und die Abo-Daten. */
interface Dashboard {
    val supportsDashboard: Boolean
    val dashboardCategories: Set<String>
    val dashboardConfigured: Boolean
    fun toggleDashboardCategory(s: Section, categoryId: String)
    fun dashboardCategoryOptions(s: Section): List<Category>
    val newMovies: List<Item>
    val newEpisodes: List<Item>
    fun newItemsOf(s: Section): List<Item>
    val accountStatus: AccountStatus?
    val accountStatusStale: Boolean
    fun accountExpiresSoon(): Boolean
}

/** Erwachsenen-Kategorie („ADULT“ im Namen) – nie auf Startseite, Titelbild oder in Genre-Reihen. */
internal fun isAdultCategory(c: Category) = c.name.contains("ADULT", ignoreCase = true)

class DashboardStore(
    private val session: Session,
    private val store: Storage,
    private val library: Library,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) : Dashboard {

    /** Neuheiten und Abo-Daten gibt es nur bei Xtream (M3U kennt kein Hinzugefügt-Datum). */
    override val supportsDashboard: Boolean get() = session.repo?.account is Account.Xtream

    override var dashboardCategories by mutableStateOf(store.dashboardCategories()); private set

    override val dashboardConfigured: Boolean get() = dashboardCategories.isNotEmpty()

    override fun toggleDashboardCategory(s: Section, categoryId: String) {
        val key = "${s.name}:$categoryId"
        dashboardCategories = if (key in dashboardCategories) dashboardCategories - key else dashboardCategories + key
        store.saveDashboardCategories(dashboardCategories)
    }

    /** Wählbare Kategorien (Filme/Serien) in Anbieter-Reihenfolge – ohne Erwachsenen-Kategorien. */
    override fun dashboardCategoryOptions(s: Section): List<Category> =
        library.sections[s]?.categories.orEmpty().filterNot { isAdultCategory(it) }

    override val newMovies: List<Item> by derivedStateOf { newItems(Section.MOVIES) }
    override val newEpisodes: List<Item> by derivedStateOf { newItems(Section.SERIES) }

    /** Einträge der gewählten Kategorien, die in den letzten 7 Tagen hinzukamen – neueste zuerst. */
    private fun newItems(s: Section): List<Item> {
        val chosen = dashboardCategories.mapNotNullTo(HashSet()) { it.removePrefix("${s.name}:").takeIf { id -> id != it } }
        if (chosen.isEmpty()) return emptyList()
        val view = library.section(s)
        val adultCats = view.categories.filter(::isAdultCategory).mapTo(HashSet()) { it.id }
        val since = clock() - NEW_WINDOW_MS
        return chosen.flatMap { view.byCategory[it].orEmpty() }
            // Keine Obergrenze: Geht die Uhr des Anbieters oder der TV-Box vor, liegt „gerade hinzugefügt“ in der Zukunft.
            .filter { !it.isAdult && it.categoryId !in adultCats && (it.addedAt ?: 0) >= since }
            .distinctBy { it.favKey }
            .sortedByDescending { it.addedAt }
    }

    /** Neue Einträge für die Kategorie „Neu (7 Tage)“ in Filme/Serien. */
    override fun newItemsOf(s: Section): List<Item> = if (s == Section.SERIES) newEpisodes else newMovies

    /** Abo-Daten (zuletzt erfolgreich abgerufen); [accountStatusStale] = der Abruf bei diesem Start scheiterte. */
    override var accountStatus by mutableStateOf(store.accountStatus()); private set
    override var accountStatusStale by mutableStateOf(false); private set

    override fun accountExpiresSoon(): Boolean =
        accountStatus?.info?.expiresAt?.let { it - clock() <= EXPIRY_WARNING_MS } == true

    internal fun refreshAccountStatus() {
        val r = session.repo ?: return
        if (r.account !is Account.Xtream) return
        accountStatusStale = false
        scope.launch {
            try {
                val info = r.accountInfo() ?: return@launch
                if (session.repo !== r) return@launch
                accountStatus = AccountStatus(info, clock()).also(store::saveAccountStatus)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (session.repo === r) accountStatusStale = true // Start läuft trotzdem weiter
            }
        }
    }

    /** Gewählte Kategorien und Abo-Daten des (neuen) aktiven Anbieters laden. */
    internal fun reloadForAccount() {
        dashboardCategories = store.dashboardCategories()
        accountStatus = store.accountStatus()
        accountStatusStale = false
    }
}
