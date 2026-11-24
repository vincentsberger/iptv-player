package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Section
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SortByYearTest {
    private val data = mapOf(
        "get_vod_categories" to """[{"category_id":"10","category_name":"Kino"}]""",
        "get_vod_streams" to """[{"stream_id":1,"name":"EN - Alt (1999)","category_id":"10"},
            {"stream_id":2,"name":"Ohne Jahr","category_id":"10"},
            {"stream_id":3,"name":"EN - Neu (2024)","category_id":"10"},
            {"stream_id":4,"name":"EN - Mitte (2010)","category_id":"10"}]""",
    )

    @Test
    fun newestFirstWithoutYearLastLiveUntouched() = runTest {
        val app = testAppState(http = SampleData.http(data))
        app.ensureLoaded(Section.MOVIES)
        val live = app.section(Section.LIVE).items.map { it.name }
        app.sortByYear = true
        assertEquals(listOf("EN - Neu (2024)", "EN - Mitte (2010)", "EN - Alt (1999)", "Ohne Jahr"), app.section(Section.MOVIES).items.map { it.name })
        assertEquals(live, app.section(Section.LIVE).items.map { it.name })
        app.sortByYear = false
        assertEquals("EN - Alt (1999)", app.section(Section.MOVIES).items.first().name)
    }
}
