package de.vincentsberger.iptvplayer.shared.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Ein gespeicherter Anbieter. [id] ist stabil: Wer sich erneut anmeldet, bekommt seine Favoriten zurück. */
data class SavedAccount(val id: String, val account: Account)

/** Stabile Kennung eines Zugangs (Server ohne Schema + Benutzer bzw. Playlist-Link). */
fun accountId(account: Account): String {
    val identity = when (account) {
        is Account.Xtream -> "x|${hostOf(account.server).lowercase()}|${account.username}"
        is Account.M3u -> "m|${account.url}"
    }
    return identity.hashCode().toUInt().toString(16)
}

/**
 * Speichert Zugangsdaten, Favoriten, Verlauf und Lautstärke – nur lokal auf dem Gerät.
 * Wo genau, entscheidet die Plattform über [KeyValueStore].
 *
 * Mehrere Anbieter: Favoriten, Verlauf, ausgeblendete Kategorien und „wo man war“ gehören zum
 * gerade aktiven Anbieter (Schlüssel „a:<id>:…“). Einstellungen wie Sortierung oder Lautstärke gelten für alle.
 */
class Storage(private val kv: KeyValueStore) {

    init { migrateSingleAccount() }

    companion object {
        const val SUBTITLES_OFF = "off"

        /** Daten, die zu einem Anbieter gehören (ohne Präfix gespeichert bis Version 1.2). */
        private val PER_ACCOUNT_KEYS = listOf("favorites", "history", "hidden", "liveCountries", "lastTab", "lastLiveCat") +
            Section.entries.map { "lastCat:${it.name}" } + Section.entries.map { "cacheMeta:${it.name}" } + listOf("dashboardCats", "accountStatus")
    }

    // ---------- Anbieter ----------

    /** Alle gespeicherten Anbieter in der Reihenfolge, in der sie hinzugefügt wurden. */
    fun accounts(): List<SavedAccount> =
        parseJson(kv.getString("accounts") ?: "[]").objects().mapNotNull { o ->
            val account = when (o.str("type")) {
                "xtream" -> Account.Xtream(o.str("server"), o.str("user"), o.str("pass"))
                "m3u" -> Account.M3u(o.str("url"))
                else -> null
            } ?: return@mapNotNull null
            SavedAccount(o.str("id").ifEmpty { accountId(account) }, account)
        }

    /** Kennung des aktiven Anbieters (null = keiner angemeldet). */
    var activeAccountId: String?
        get() = kv.getString("active")?.takeIf { id -> accounts().any { it.id == id } }
        set(v) = kv.putString("active", v)

    fun loadAccount(): Account? = activeAccountId?.let { id -> accounts().firstOrNull { it.id == id }?.account }

    /** Anbieter speichern (neu oder mit geänderten Daten) und aktiv schalten. Liefert seine Kennung. */
    fun saveAccount(account: Account): String {
        val id = accountId(account)
        val existing = accounts()
        // Ein bekannter Anbieter behält seinen Platz in der Liste, ein neuer kommt ans Ende.
        writeAccounts(
            if (existing.any { it.id == id }) existing.map { if (it.id == id) SavedAccount(id, account) else it }
            else existing + SavedAccount(id, account)
        )
        activeAccountId = id
        return id
    }

    /** Anbieter samt Favoriten, Verlauf usw. löschen. War er aktiv, wird der erste verbliebene aktiv (oder keiner). */
    fun removeAccount(id: String) {
        PER_ACCOUNT_KEYS.forEach { kv.putString(key(id, it), null) }
        val rest = accounts().filterNot { it.id == id }
        writeAccounts(rest)
        if (kv.getString("active") == id) activeAccountId = rest.firstOrNull()?.id
    }

    private fun writeAccounts(list: List<SavedAccount>) {
        if (list.isEmpty()) { kv.putString("accounts", null); kv.putString("active", null); return }
        kv.putString("accounts", buildJsonArray {
            list.forEach { (id, a) ->
                add(buildJsonObject {
                    put("id", id)
                    when (a) {
                        is Account.Xtream -> { put("type", "xtream"); put("server", a.server); put("user", a.username); put("pass", a.password) }
                        is Account.M3u -> { put("type", "m3u"); put("url", a.url) }
                    }
                })
            }
        }.toString())
    }

    /** Bis Version 1.2 gab es genau einen Zugang mit Daten ohne Präfix – einmalig übernehmen. */
    private fun migrateSingleAccount() {
        val type = kv.getString("type") ?: return
        val legacy = when (type) {
            "xtream" -> Account.Xtream(kv.getString("server").orEmpty(), kv.getString("user").orEmpty(), kv.getString("pass").orEmpty())
            "m3u" -> Account.M3u(kv.getString("url").orEmpty())
            else -> null
        }
        listOf("type", "server", "user", "pass", "url").forEach { kv.putString(it, null) }
        if (legacy == null) return
        val id = saveAccount(legacy)
        PER_ACCOUNT_KEYS.forEach { name ->
            kv.getString(name)?.let { kv.putString(key(id, name), it); kv.putString(name, null) }
        }
    }

    private fun key(id: String, name: String) = "a:$id:$name"

    /** Schlüssel des aktiven Anbieters; ohne Anbieter landet alles unter „a::…“ und wird nie gelesen. */
    private fun k(name: String) = key(kv.getString("active").orEmpty(), name)

    /** Alles löschen (alle Anbieter und Einstellungen). */
    fun clear() = kv.clear()

    fun favorites(): Set<String> =
        (parseJson(kv.getString(k("favorites")) ?: "[]") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet().orEmpty()

    fun saveFavorites(keys: Set<String>) {
        kv.putString(k("favorites"), buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } }.toString())
    }

    fun history(): List<HistoryEntry> =
        parseJson(kv.getString(k("history")) ?: "[]").objects().mapNotNull(::entryFrom)

    fun saveHistory(list: List<HistoryEntry>) {
        val arr = buildJsonArray {
            list.forEach { e ->
                add(buildJsonObject {
                    put("id", e.item.id); put("name", e.item.name); put("logo", e.item.logo ?: "")
                    put("cat", e.item.categoryId); put("url", e.item.url); put("section", e.item.section.name)
                    e.item.number?.let { put("num", it.toString()) }
                    put("pos", e.position); put("dur", e.duration); put("upd", e.updated)
                })
            }
        }
        kv.putString(k("history"), arr.toString())
    }

    var volume: Int
        get() = kv.getString("volume")?.toIntOrNull() ?: 80
        set(v) = kv.putString("volume", v.toString())

    // ---------- Komfort-Einstellungen ----------

    var sortAz: Boolean
        get() = kv.getString("sortAz") == "1"
        set(v) = kv.putString("sortAz", if (v) "1" else null)

    /** Beim Start direkt den zuletzt gesehenen Sender abspielen. */
    var startWithLastChannel: Boolean
        get() = kv.getString("startLast") == "1"
        set(v) = kv.putString("startLast", if (v) "1" else null)

    /** Am Ende einer Folge automatisch die nächste starten (Standard: an). */
    var autoNextEpisode: Boolean
        get() = kv.getString("autoNext") != "0"
        set(v) = kv.putString("autoNext", if (v) null else "0")

    /** Downloads während der Wiedergabe aus dem Netz pausieren (Standard: an). */
    var pauseDownloadsWhilePlaying: Boolean
        get() = kv.getString("pauseDl") != "0"
        set(v) = kv.putString("pauseDl", if (v) null else "0")

    /** Von selbst nach Updates suchen (Standard: an). */
    var updateCheck: Boolean
        get() = kv.getString("updateCheck") != "0"
        set(v) = kv.putString("updateCheck", if (v) null else "0")

    /** Zeitpunkt der letzten Update-Suche (ms). */
    var lastUpdateCheck: Long
        get() = kv.getString("lastUpdateCheck")?.toLongOrNull() ?: 0
        set(v) = kv.putString("lastUpdateCheck", v.toString())

    /** Ton-Verzögerung in ms (Lippensynchronität, z. B. mit Soundbar); nur die Desktop-App (VLC) nutzt sie. */
    var audioDelayMs: Int
        get() = kv.getString("audioDelay")?.toIntOrNull() ?: 0
        set(v) = kv.putString("audioDelay", if (v == 0) null else v.toString())

    /** Bildformat im Player: „fit“ (Standard), „zoom“ oder „fill“. */
    var videoFit: String
        get() = kv.getString("videoFit") ?: "fit"
        set(v) = kv.putString("videoFit", if (v == "fit") null else v)

    // ---------- Ton und Untertitel ----------

    /** Bevorzugte Sprache der Tonspur (z. B. „en“); null = Vorgabe des Anbieters. */
    var audioLanguage: String?
        get() = kv.getString("audioLang")
        set(v) = kv.putString("audioLang", v)

    /** Bevorzugte Untertitel-Sprache, [SUBTITLES_OFF] = bewusst ausgeschaltet, null = Vorgabe des Anbieters. */
    var subtitleLanguage: String?
        get() = kv.getString("subLang")
        set(v) = kv.putString("subLang", v)

    // ---------- Wo man aufgehört hat ----------

    var lastTab: String?
        get() = kv.getString(k("lastTab"))
        set(v) = kv.putString(k("lastTab"), v)

    var lastLiveCategory: String?
        get() = kv.getString(k("lastLiveCat"))
        set(v) = kv.putString(k("lastLiveCat"), v)

    fun lastBrowseCategory(section: Section): String? = kv.getString(k("lastCat:${section.name}"))
    fun saveLastBrowseCategory(section: Section, key: String?) = kv.putString(k("lastCat:${section.name}"), key)

    /** Ausgeblendete Kategorien als „SECTION:id“. */
    fun hiddenCategories(): Set<String> =
        (parseJson(kv.getString(k("hidden")) ?: "[]") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet().orEmpty()

    fun saveHiddenCategories(keys: Set<String>) {
        kv.putString(k("hidden"), buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } }.toString())
    }

    /** Stand des Zwischenspeichers je Bereich (wann zuletzt erfolgreich geladen, wie viele Einträge). */
    fun cacheMeta(section: Section): CacheMeta? {
        val raw = kv.getString(k("cacheMeta:${section.name}")) ?: return null
        return try {
        (parseJson(raw) as? JsonObject)?.let {
            CacheMeta(
                loadedAt = it.str("loadedAt").toLong(),
                count = it.str("count").toInt(),
                previousCount = it.str("previousCount").toIntOrNull(),
                failed = it.str("failed") == "true",
            )
        }
    } catch (e: Exception) {
        null
    }
    }

    fun saveCacheMeta(section: Section, meta: CacheMeta) {
        kv.putString(k("cacheMeta:${section.name}"), buildJsonObject {
            put("loadedAt", meta.loadedAt.toString()); put("count", meta.count.toString())
            put("previousCount", meta.previousCount?.toString() ?: ""); put("failed", meta.failed.toString())
        }.toString())
    }

    /** Für die Startseite gewählte Kategorien als „SECTION:id“. */
    fun dashboardCategories(): Set<String> =
        (parseJson(kv.getString(k("dashboardCats")) ?: "[]") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet().orEmpty()

    fun saveDashboardCategories(keys: Set<String>) {
        kv.putString(k("dashboardCats"), buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } }.toString())
    }

    /** Zuletzt abgerufene Abo-Daten (für die Anzeige ohne Netz). */
    fun accountStatus(): AccountStatus? {
        val raw = kv.getString(k("accountStatus")) ?: return null
        return try {
            val o = parseJson(raw) as? JsonObject ?: return null
            AccountStatus(
                AccountInfo(o.str("expiresAt").toLongOrNull(), o.str("max").toIntOrNull(), o.str("active").toIntOrNull()),
                o.str("fetchedAt").toLong(),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveAccountStatus(status: AccountStatus) {
        kv.putString(k("accountStatus"), buildJsonObject {
            put("expiresAt", status.info.expiresAt?.toString() ?: ""); put("max", status.info.maxConnections?.toString() ?: "")
            put("active", status.info.activeConnections?.toString() ?: ""); put("fetchedAt", status.fetchedAt.toString())
        }.toString())
    }

    /** Gewählte Länderkürzel für Live-TV („DE“, „AT“); leer = alle Länder. */
    fun liveCountries(): Set<String> =
        (parseJson(kv.getString(k("liveCountries")) ?: "[]") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet().orEmpty()

    fun saveLiveCountries(tags: Set<String>) {
        kv.putString(k("liveCountries"), buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } }.toString())
    }

    private fun entryFrom(o: JsonObject): HistoryEntry? = try {
        HistoryEntry(
            Item(
                o.str("id"), o.str("name"), o.str("logo").ifBlank { null },
                o.str("cat"), o.str("url").ifBlank { return null }, Section.valueOf(o.str("section")),
                number = o.str("num").toIntOrNull(),
            ),
            o.str("pos").toLongOrNull() ?: 0, o.str("dur").toLongOrNull() ?: 0, o.str("upd").toLongOrNull() ?: 0,
        )
    } catch (e: IllegalArgumentException) {
        null
    }
}
