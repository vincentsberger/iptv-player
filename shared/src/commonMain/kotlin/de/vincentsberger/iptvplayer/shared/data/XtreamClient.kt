package de.vincentsberger.iptvplayer.shared.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Client für die verbreitete "Xtream Codes"-API (player_api.php). */
class XtreamClient(account: Account.Xtream, private val http: HttpSource = PlatformHttp) {
    /** Adressen, die [login] der Reihe nach probiert: ohne Angabe erst https, dann http. */
    private val candidates = candidatesFor(account.server)

    /** Aktuelle Server-Adresse mit Schema. Vor dem Login wie bisher http, damit gespeicherte Konten weiter laufen. */
    var base = normalize(account.server)
        private set
    private val user = account.username.trim()
    private val pass = account.password.trim()
    private val u = urlEncode(user)
    private val p = urlEncode(pass)
    private var liveExt = "ts"

    private fun api(action: String? = null, extra: String = "", server: String = base) = buildString {
        append(server).append("/player_api.php?username=").append(u).append("&password=").append(p)
        if (action != null) append("&action=").append(action)
        append(extra)
    }

    suspend fun login() {
        var unreachable: TvException? = null
        for (candidate in candidates) {
            val text = try {
                http.getText(api(server = candidate))
            } catch (e: TvException) {
                unreachable = e  // z. B. kein https auf diesem Server: nächste Adresse probieren
                continue
            }
            base = candidate
            checkLogin(text)
            return
        }
        throw unreachable ?: TvException("Server nicht erreichbar")
    }

    /** Abo-Daten aus der letzten Anmeldung. */
    var accountInfo: AccountInfo? = null
        private set

    /** Erneute Anmeldung nur für die Abo-Daten (Ablauf, Verbindungen) – wirft bei Netz- oder Anmeldefehlern. */
    suspend fun fetchAccountInfo(): AccountInfo {
        checkLogin(http.getText(api()))
        return accountInfo!!
    }

    private fun checkLogin(text: String) {
        val json = parseJson(text) as? JsonObject
            ?: throw TvException("Unerwartete Antwort. Stimmt die Server-Adresse?")
        val info = json.obj("user_info") ?: throw TvException("Unerwartete Antwort vom Server")
        if (info.str("auth").toIntOrNull() != 1) throw TvException("Benutzername oder Passwort falsch")
        val status = info.str("status")
        if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
            throw TvException("Dein Konto ist nicht aktiv (Status: $status)")
        }
        accountInfo = AccountInfo(
            expiresAt = epochMillis(info.str("exp_date")),
            maxConnections = info.str("max_connections").toIntOrNull(),
            activeConnections = info.str("active_cons").toIntOrNull(),
        )
        val formats = info.arr("allowed_output_formats")?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        liveExt = if ("m3u8" in formats) "m3u8" else "ts"
    }

    /** Aktionen für Kategorien und Einträge eines Bereichs – zugleich die Schlüssel im Zwischenspeicher. */
    fun listActions(section: Section): Pair<String, String> = when (section) {
        Section.LIVE -> "get_live_categories" to "get_live_streams"
        Section.MOVIES -> "get_vod_categories" to "get_vod_streams"
        Section.SERIES -> "get_series_categories" to "get_series"
    }

    suspend fun fetch(action: String): String = http.getText(api(action))

    fun parseCategories(text: String): List<Category> =
        parseJson(text).objects().map { Category(it.str("category_id"), it.str("category_name")) }

    fun parseItems(section: Section, text: String): List<Item> = when (section) {
        Section.LIVE -> parseJson(text).objects().mapNotNull {
            val id = safePathSegment(it.str("stream_id")).ifEmpty { return@mapNotNull null }
            Item(id, it.str("name"), it.str("stream_icon").ifBlank { null }, it.str("category_id"),
                "$base/live/$u/$p/$id.$liveExt", Section.LIVE, number = it.str("num").toIntOrNull()?.takeIf { n -> n > 0 })
        }
        Section.MOVIES -> parseJson(text).objects().mapNotNull {
            val id = safePathSegment(it.str("stream_id")).ifEmpty { return@mapNotNull null }
            val ext = safeExtension(it.str("container_extension"), "mp4")
            Item(id, it.str("name"), it.str("stream_icon").ifBlank { null }, it.str("category_id"),
                "$base/movie/$u/$p/$id.$ext", Section.MOVIES, epochMillis(it.str("added")), it.str("is_adult") == "1")
        }
        Section.SERIES -> parseJson(text).objects().map {
            Item(it.str("series_id"), it.str("name"), it.str("cover").ifBlank { null }, it.str("category_id"), "", Section.SERIES,
                epochMillis(it.str("last_modified")), it.str("is_adult") == "1")
        }
    }

    /** Unix-Sekunden des Anbieters → ms; leer, „0“ oder keine Zahl → null. */
    private fun epochMillis(seconds: String): Long? = seconds.trim().toLongOrNull()?.takeIf { it > 0 }?.times(1000)

    suspend fun categories(section: Section): List<Category> = parseCategories(fetch(listActions(section).first))

    suspend fun items(section: Section): List<Item> = parseItems(section, fetch(listActions(section).second))

    /** Film-Infos für die Pause-Info; null, wenn der Anbieter keine liefert. */
    suspend fun vodInfo(item: Item): MediaInfo? = parseVodInfo(http.getText(api("get_vod_info", "&vod_id=${urlEncode(item.id)}")))

    fun parseVodInfo(text: String): MediaInfo? = (parseJson(text) as? JsonObject)?.let(::mediaInfoOf)

    /** Serien-Infos aus der `get_series_info`-Antwort. */
    fun parseSeriesInfo(json: JsonObject): MediaInfo? = mediaInfoOf(json)

    private fun mediaInfoOf(json: JsonObject): MediaInfo? {
        val info = json["info"] as? JsonObject ?: return null
        val date = listOf("releasedate", "releaseDate", "release_date").map { info.str(it) }.firstOrNull { it.isNotBlank() }
        return MediaInfo(
            year = date?.take(4)?.takeIf { y -> y.length == 4 && y.all(Char::isDigit) },
            rating = info.str("rating").replace(',', '.').toDoubleOrNull(),
            durationSecs = info.str("duration_secs").toIntOrNull(),
            age = Regex("""\d+""").find(info.str("age"))?.value?.toIntOrNull(),
            genres = info.str("genre").split(',', '/').map { it.trim() }.filter { it.isNotEmpty() },
            plot = (info.str("plot").ifBlank { info.str("description") }).trim().ifBlank { null },
            director = info.str("director").trim().ifBlank { null },
            cast = info.str("cast").ifBlank { info.str("actors") }.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            country = info.str("country").trim().ifBlank { null },
            originalTitle = info.str("o_name").trim().takeIf { it.isNotEmpty() && !it.equals(info.str("name").trim(), ignoreCase = true) },
            backdrop = when (val b = info["backdrop_path"]) {
                is JsonArray -> (b.firstOrNull() as? JsonPrimitive)?.content
                is JsonPrimitive -> b.content
                else -> null
            }?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) },
        ).takeIf {
            it.year != null || it.ratingLabel != null || it.durationLabel != null || it.age != null || it.genres.isNotEmpty() ||
                it.plot != null || it.director != null || it.cast.isNotEmpty() || it.backdrop != null
        }
    }

    suspend fun seasons(series: Item): List<Season> = seasonsAndInfo(series).first

    /** Staffeln und – aus derselben Antwort – die Serien-Infos. */
    suspend fun seasonsAndInfo(series: Item): Pair<List<Season>, MediaInfo?> {
        val json = parseJson(http.getText(api("get_series_info", "&series_id=${urlEncode(series.id)}"))) as? JsonObject
            ?: return emptyList<Season>() to null
        return parseSeasons(json, series) to parseSeriesInfo(json)
    }

    private fun parseSeasons(json: JsonObject, series: Item): List<Season> {
        // Die meisten Server liefern {"1":[...],"2":[...]}, manche ein Array von Arrays.
        val raw: List<Pair<String, JsonArray?>> = when (val e = json["episodes"]) {
            is JsonObject -> e.entries.map { it.key to (it.value as? JsonArray) }
            is JsonArray -> e.mapIndexed { i, v -> (i + 1).toString() to (v as? JsonArray) }
            else -> return emptyList()
        }
        return raw
            .sortedBy { it.first.toIntOrNull() ?: Int.MAX_VALUE }
            .map { (seasonNo, arr) ->
                val details = HashMap<String, EpisodeDetail>()
                val list = arr.objects().mapNotNull { ep ->
                    val id = safePathSegment(ep.str("id")).ifEmpty { return@mapNotNull null }
                    val ext = safeExtension(ep.str("container_extension"), "mp4")
                    val num = ep.str("episode_num")
                    val title = ep.str("title").ifBlank { "Folge $num" }
                    val info = ep.obj("info")
                    val img = info?.str("movie_image")?.ifBlank { null } ?: series.logo
                    Item(id, title, img, series.id, "$base/series/$u/$p/$id.$ext", Section.SERIES).also { item ->
                        val plot = info?.str("plot")?.trim()?.ifBlank { null }
                        val secs = info?.let { it.str("duration_secs").toIntOrNull() ?: hmsToSecs(it.str("duration")) }?.takeIf { it > 0 }
                        if (plot != null || secs != null) details[item.url] = EpisodeDetail(plot, secs)
                    }
                }
                Season(seasonNo, list, details)
            }
            .filter { it.episodes.isNotEmpty() }
    }

    suspend fun shortEpg(streamId: String): List<EpgEntry> {
        val json = parseJson(http.getText(api("get_short_epg", "&stream_id=${urlEncode(streamId)}&limit=6"))) as? JsonObject
            ?: return emptyList()
        return json["epg_listings"].objects().map {
            EpgEntry(base64(it.str("title")), time(it.str("start")), time(it.str("end")), dateTime(it.str("start")), dateTime(it.str("end")))
        }
    }

    companion object {
        /** Ohne Schema erst https (verschlüsselt), dann http; mit Schema genau diese Adresse. */
        fun candidatesFor(server: String): List<String> {
            val s = server.trim()
            if (s.startsWith("http://", true) || s.startsWith("https://", true)) return listOf(normalize(s))
            val http = normalize(s)
            return listOf("https://" + http.removePrefix("http://"), http)
        }

        fun normalize(server: String): String {
            var s = server.trim().trimEnd('/')
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            return s.removeSuffix("/player_api.php").removeSuffix("/get.php")
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun base64(s: String): String = try {
            Base64.Mime.decode(s).decodeToString()
        } catch (e: IllegalArgumentException) {
            s
        }

        /** „00:45:12“ bzw. „45:12“ → Sekunden; sonst null. */
        private fun hmsToSecs(s: String): Int? {
            val p = s.trim().split(':').map { it.toIntOrNull() ?: return null }
            return when (p.size) { 3 -> p[0] * 3600 + p[1] * 60 + p[2]; 2 -> p[0] * 60 + p[1]; else -> null }
        }

        private fun time(s: String): String = if (s.length >= 16) s.substring(11, 16) else s

        private val dateTimePattern = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")

        /** „yyyy-MM-dd HH:mm:ss“ oder leer – nur dann ist ein Vergleich mit der Ortszeit sinnvoll. */
        private fun dateTime(s: String): String = s.takeIf { dateTimePattern.matches(it) }.orEmpty()
    }
}
