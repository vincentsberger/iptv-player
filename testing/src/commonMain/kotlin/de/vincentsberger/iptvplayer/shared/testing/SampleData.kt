package de.vincentsberger.iptvplayer.shared.testing

import de.vincentsberger.iptvplayer.shared.AppState
import de.vincentsberger.iptvplayer.shared.data.Account
import de.vincentsberger.iptvplayer.shared.data.NoPlaylistCache
import de.vincentsberger.iptvplayer.shared.data.PlaylistCacheStore
import de.vincentsberger.iptvplayer.shared.data.Repository
import de.vincentsberger.iptvplayer.shared.data.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Feste Testdaten: ein kleiner Xtream-Server mit Sendern, Filmen und einer Serie. */
object SampleData {
    val account = Account.Xtream("tv.example", "user", "pass")

    /** Antworten für [FakeHttp.xtream]; einzelne Einträge lassen sich per `+` überschreiben. */
    val xtreamResponses: Map<String, String> = mapOf(
        "login" to FakeHttp.LOGIN_OK,
        "get_live_categories" to """[{"category_id":"1","category_name":"Nachrichten"},{"category_id":"2","category_name":"Sport"}]""",
        "get_live_streams" to """[
            {"stream_id":1,"name":"Das Erste","category_id":"1"},
            {"stream_id":2,"name":"ZDF","category_id":"1"},
            {"stream_id":3,"name":"Sport1","category_id":"2"}
        ]""",
        "get_vod_categories" to """[{"category_id":"10","category_name":"Science-Fiction"},{"category_id":"11","category_name":"Komödie"}]""",
        "get_vod_streams" to """[
            {"stream_id":100,"name":"Arrival","category_id":"10"},
            {"stream_id":101,"name":"Dune","category_id":"10"},
            {"stream_id":102,"name":"Ziemlich beste Freunde","category_id":"11"}
        ]""",
        "get_series_categories" to """[{"category_id":"20","category_name":"Drama"}]""",
        "get_series" to """[{"series_id":200,"name":"Babylon Berlin","category_id":"20"}]""",
        "get_series_info" to """{"episodes":{
            "1":[{"id":"2001","episode_num":1,"title":"Pilot"},{"id":"2002","episode_num":2,"title":"Der Zug"}],
            "2":[{"id":"2011","episode_num":1,"title":"Neue Spur"}]
        }}""",
        "get_short_epg" to """{"epg_listings":[]}""",
    )

    fun http(overrides: Map<String, String> = emptyMap()) = FakeHttp.xtream(xtreamResponses + overrides)

    /** Antwort des Beispielservers auf eine URL – für eigene [FakeHttp], die z. B. erst scheitern sollen. */
    fun respond(url: String): String =
        xtreamResponses[FakeHttp.actionOf(url)] ?: error("Unerwartete Anfrage: $url")
}

/**
 * [AppState] für Tests: Speicher im Arbeitsspeicher, HTTP-Attrappe, feste Uhr.
 * `Dispatchers.Unconfined` führt das Laden sofort aus, die Oberfläche sieht also ohne Warten das Ergebnis.
 */
fun testAppState(
    http: FakeHttp = SampleData.http(),
    kv: InMemoryKeyValueStore = InMemoryKeyValueStore(),
    loggedIn: Boolean = true,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    now: () -> Long = { 1_000L },
    playlistCache: (accountId: String) -> PlaylistCacheStore = { NoPlaylistCache },
    /** Die Übersicht nach dem Laden gleich mit „Weiter“ schließen – die meisten Tests prüfen, was danach kommt. */
    continueOverview: Boolean = true,
): AppState {
    val storage = Storage(kv)
    if (loggedIn) storage.saveAccount(SampleData.account)
    return AppState(storage, scope = scope, clock = now, newRepository = { a, c -> Repository(a, http, c, Dispatchers.Unconfined) }, playlistCache = playlistCache)
        .also { if (continueOverview && it.overview != null) it.continueFromOverview() }
}
