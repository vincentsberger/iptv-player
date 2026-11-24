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

    /**
     * Antworten für [FakeHttp.xtream]; einzelne Einträge lassen sich per `+` überschreiben. Felder und Datentypen wie
     * bei einem echten Xtream-Anbieter (Stand 11/2026): IDs der Listen als Zahl, Kategorien und Zeitstempel als Text,
     * Folgen-IDs als Text, `episodes` als Objekt mit Staffelnummer (Text) als Schlüssel, `seasons` meist leer.
     */
    val xtreamResponses: Map<String, String> = mapOf(
        "login" to FakeHttp.LOGIN_OK,
        "get_live_categories" to """[{"category_id":"1","category_name":"Nachrichten"},{"category_id":"2","category_name":"Sport"}]""",
        "get_live_streams" to """[
            {"num":1,"name":"Das Erste","stream_type":"live","stream_id":1,"stream_icon":"","epg_channel_id":"daserste.de","added":"1700000000",
             "is_adult":0,"category_id":"1","category_ids":[1],"custom_sid":null,"tv_archive":0,"direct_source":"","tv_archive_duration":0},
            {"num":2,"name":"ZDF","stream_type":"live","stream_id":2,"stream_icon":"","epg_channel_id":"zdf.de","added":"1700000000",
             "is_adult":0,"category_id":"1","category_ids":[1],"custom_sid":null,"tv_archive":0,"direct_source":"","tv_archive_duration":"0"},
            {"num":3,"name":"Sport1","stream_type":"live","stream_id":3,"stream_icon":"","epg_channel_id":"sport1.de","added":"1700000000",
             "is_adult":0,"category_id":"2","category_ids":[2],"custom_sid":null,"tv_archive":0,"direct_source":"","tv_archive_duration":0}
        ]""",
        "get_vod_categories" to """[{"category_id":"10","category_name":"Science-Fiction"},{"category_id":"11","category_name":"Komödie"}]""",
        "get_vod_streams" to """[
            {"num":1,"name":"Arrival","stream_type":"movie","stream_id":100,"stream_icon":"","rating":"","rating_5based":0,"tmdb":"329865",
             "trailer":"","added":"","is_adult":0,"category_id":"10","category_ids":[10],"container_extension":"mkv","custom_sid":null,"direct_source":""},
            {"num":2,"name":"Dune","stream_type":"movie","stream_id":101,"stream_icon":"","rating":"","rating_5based":0,"tmdb":"438631",
             "trailer":"","added":"","is_adult":0,"category_id":"10","category_ids":[10],"container_extension":"mp4","custom_sid":null,"direct_source":""},
            {"num":3,"name":"Ziemlich beste Freunde","stream_type":"movie","stream_id":102,"stream_icon":"","rating":"","rating_5based":0,"tmdb":"77338",
             "trailer":"","added":"","is_adult":0,"category_id":"11","category_ids":[11],"container_extension":"mkv","custom_sid":null,"direct_source":""}
        ]""",
        "get_series_categories" to """[{"category_id":"20","category_name":"Drama"}]""",
        "get_series" to """[{"num":1,"name":"Babylon Berlin","series_id":200,"cover":"","plot":"","cast":"","director":"","genre":"",
            "releaseDate":"2017-10-13","release_date":"2017-10-13","last_modified":"","rating":"","rating_5based":"0","backdrop_path":[],
            "youtube_trailer":"","tmdb":"70593","episode_run_time":"45","category_id":"20","category_ids":[20]}]""",
        "get_series_info" to """{"seasons":[],"info":{"name":"Babylon Berlin","cover":"","plot":"","cast":"","director":"","genre":"",
            "releaseDate":"2017-10-13","release_date":"2017-10-13","last_modified":"","rating":"","rating_5based":"0","backdrop_path":[],
            "tmdb":"70593","youtube_trailer":"","episode_run_time":"45","category_id":"20","category_ids":[20]},
          "episodes":{
            "1":[{"id":"2001","episode_num":1,"title":"Pilot","container_extension":"mkv","info":{"duration_secs":2700,"duration":"00:45:00"},
                  "custom_sid":null,"added":"1700000000","season":1,"direct_source":""},
                 {"id":"2002","episode_num":2,"title":"Der Zug","container_extension":"mkv","info":{"duration_secs":2700,"duration":"00:45:00"},
                  "custom_sid":null,"added":"1700000000","season":1,"direct_source":""}],
            "2":[{"id":"2011","episode_num":1,"title":"Neue Spur","container_extension":"mkv","info":{"duration_secs":2700,"duration":"00:45:00"},
                  "custom_sid":null,"added":"1700000000","season":2,"direct_source":""}]
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
    /** Ortszeit „yyyy-MM-dd HH:mm:ss“ zu [now] – Standard: 1970, also ist keine EPG-Sendung vorbei. */
    localTime: (Long) -> String = { "1970-01-01 00:00:01" },
    playlistCache: (accountId: String) -> PlaylistCacheStore = { NoPlaylistCache },
    /** Die Übersicht nach dem Laden gleich mit „Weiter“ schließen – die meisten Tests prüfen, was danach kommt. */
    continueOverview: Boolean = true,
): AppState {
    val storage = Storage(kv)
    if (loggedIn) storage.saveAccount(SampleData.account)
    return AppState(storage, scope = scope, clock = now, localTime = localTime, newRepository = { a, c -> Repository(a, http, c, Dispatchers.Unconfined) }, playlistCache = playlistCache)
        .also { if (continueOverview && it.overview != null) it.continueFromOverview() }
}
