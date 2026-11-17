package de.vincentsberger.iptvplayer.shared.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Speichert Zugangsdaten, Favoriten, Verlauf und Lautstärke – nur lokal auf dem Gerät.
 * Wo genau, entscheidet die Plattform über [KeyValueStore].
 */
class Storage(private val kv: KeyValueStore) {

    fun loadAccount(): Account? = when (kv.getString("type")) {
        "xtream" -> Account.Xtream(kv.getString("server").orEmpty(), kv.getString("user").orEmpty(), kv.getString("pass").orEmpty())
        "m3u" -> Account.M3u(kv.getString("url").orEmpty())
        else -> null
    }

    fun saveAccount(account: Account) {
        listOf("type", "server", "user", "pass", "url").forEach { kv.putString(it, null) }
        when (account) {
            is Account.Xtream -> {
                kv.putString("type", "xtream"); kv.putString("server", account.server)
                kv.putString("user", account.username); kv.putString("pass", account.password)
            }
            is Account.M3u -> { kv.putString("type", "m3u"); kv.putString("url", account.url) }
        }
    }

    /** Abmelden: alles löschen. */
    fun clear() = kv.clear()

    fun favorites(): Set<String> =
        (parseJson(kv.getString("favorites") ?: "[]") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet().orEmpty()

    fun saveFavorites(keys: Set<String>) {
        kv.putString("favorites", buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } }.toString())
    }

    fun history(): List<HistoryEntry> =
        parseJson(kv.getString("history") ?: "[]").objects().mapNotNull(::entryFrom)

    fun saveHistory(list: List<HistoryEntry>) {
        val arr = buildJsonArray {
            list.forEach { e ->
                add(buildJsonObject {
                    put("id", e.item.id); put("name", e.item.name); put("logo", e.item.logo ?: "")
                    put("cat", e.item.categoryId); put("url", e.item.url); put("section", e.item.section.name)
                    put("pos", e.position); put("dur", e.duration); put("upd", e.updated)
                })
            }
        }
        kv.putString("history", arr.toString())
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

    // ---------- Wo man aufgehört hat ----------

    var lastTab: String?
        get() = kv.getString("lastTab")
        set(v) = kv.putString("lastTab", v)

    var lastLiveCategory: String?
        get() = kv.getString("lastLiveCat")
        set(v) = kv.putString("lastLiveCat", v)

    fun lastBrowseCategory(section: Section): String? = kv.getString("lastCat:${section.name}")
    fun saveLastBrowseCategory(section: Section, key: String?) = kv.putString("lastCat:${section.name}", key)

    /** Ausgeblendete Kategorien als „SECTION:id“. */
    fun hiddenCategories(): Set<String> =
        (parseJson(kv.getString("hidden") ?: "[]") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet().orEmpty()

    fun saveHiddenCategories(keys: Set<String>) {
        kv.putString("hidden", buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } }.toString())
    }

    private fun entryFrom(o: JsonObject): HistoryEntry? = try {
        HistoryEntry(
            Item(
                o.str("id"), o.str("name"), o.str("logo").ifBlank { null },
                o.str("cat"), o.str("url").ifBlank { return null }, Section.valueOf(o.str("section")),
            ),
            o.str("pos").toLongOrNull() ?: 0, o.str("dur").toLongOrNull() ?: 0, o.str("upd").toLongOrNull() ?: 0,
        )
    } catch (e: IllegalArgumentException) {
        null
    }
}
