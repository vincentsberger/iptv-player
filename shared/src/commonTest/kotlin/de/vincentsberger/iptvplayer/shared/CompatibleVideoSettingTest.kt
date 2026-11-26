package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.data.Storage
import de.vincentsberger.iptvplayer.shared.state.SettingsStore
import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibleVideoSettingTest {
    @Test fun offByDefaultAndRemembered() {
        val kv = InMemoryKeyValueStore()
        val settings = SettingsStore(Storage(kv), clock = { 0L })
        assertFalse(settings.compatibleVideo)
        settings.compatibleVideo = true
        assertTrue(SettingsStore(Storage(kv), clock = { 0L }).compatibleVideo, "bleibt nach Neustart")
    }
}
