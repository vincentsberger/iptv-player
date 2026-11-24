package de.vincentsberger.iptvplayer.shared

import de.vincentsberger.iptvplayer.shared.testing.InMemoryKeyValueStore
import de.vincentsberger.iptvplayer.shared.testing.SampleData
import de.vincentsberger.iptvplayer.shared.testing.testAppState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Eigener Name je Anbieter („Name in der App“) statt „Xtream Codes · user @ host“. */
class AccountNameTest {

    private val kv = InMemoryKeyValueStore()
    private val account = SampleData.account

    @Test
    fun nameIsShownInsteadOfTechnicalLabel() = runTest {
        val app = testAppState(kv = kv, loggedIn = false)
        app.login(account, name = "Mein Anbieter")
        assertEquals("Mein Anbieter", app.accountLabel)
        assertEquals("Mein Anbieter", app.accounts.single().name)
    }

    @Test
    fun blankNameFallsBackToLabel() = runTest {
        val app = testAppState(kv = kv, loggedIn = false)
        app.login(account, name = "   ")
        assertEquals(labelOf(account), app.accountLabel)
        assertNull(app.accounts.single().name)
    }

    @Test
    fun reLoginWithoutNameKeepsName() = runTest {
        val app = testAppState(kv = kv, loggedIn = false)
        app.login(account, name = "Mein Anbieter")
        app.login(account)
        assertEquals("Mein Anbieter", app.accountLabel)
    }

    @Test
    fun nameSurvivesRestart() = runTest {
        testAppState(kv = kv, loggedIn = false).login(account, name = " Mein Anbieter ")
        assertEquals("Mein Anbieter", testAppState(kv = kv, loggedIn = false).accountLabel)
    }
}
