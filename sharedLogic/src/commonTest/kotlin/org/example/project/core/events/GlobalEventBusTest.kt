package org.example.project.core.events

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.core.events.GlobalEvent.Maintenance
import org.example.project.core.events.GlobalEvent.SessionExpired
import org.example.project.core.events.GlobalEvent.UpgradeRequired
import kotlin.test.Test
import kotlin.test.assertEquals

class GlobalEventBusTest {

    private val bus = GlobalEventBus()

    private suspend fun pending() = bus.pending.first()

    @Test
    fun startsWithNothingPending() = runTest {
        assertEquals(emptySet(), pending())
    }

    @Test
    fun anEventRaisedBeforeAnyoneListensIsNotLost() = runTest {
        bus.raise(UpgradeRequired, sentAt = bus.now())

        assertEquals(setOf<GlobalEvent>(UpgradeRequired), pending())   // a collector that arrives late
    }

    @Test
    fun handlingOneEventLeavesTheOthers() = runTest {
        bus.raise(SessionExpired, sentAt = bus.now())
        bus.raise(Maintenance, sentAt = bus.now())

        bus.handled(SessionExpired)

        assertEquals(setOf<GlobalEvent>(Maintenance), pending())
    }

    @Test
    fun aRequestSentBeforeTheEventWasHandledCannotRaiseItAgain() = runTest {
        val sentInTheOldSession = bus.now()
        bus.raise(SessionExpired, sentAt = bus.now())
        bus.handled(SessionExpired)

        bus.raise(SessionExpired, sentInTheOldSession)

        assertEquals(emptySet(), pending())
    }

    @Test
    fun aRequestSentAfterTheEventWasHandledRaisesItAgain() = runTest {
        bus.raise(SessionExpired, sentAt = bus.now())
        bus.handled(SessionExpired)

        bus.raise(SessionExpired, sentAt = bus.now())

        assertEquals(setOf<GlobalEvent>(SessionExpired), pending())
    }

    @Test
    fun handlingOneEventDoesNotHideADifferentOneFromTheSameTime() = runTest {
        val sentAt = bus.now()
        bus.raise(SessionExpired, sentAt)
        bus.handled(SessionExpired)

        bus.raise(UpgradeRequired, sentAt)

        assertEquals(setOf<GlobalEvent>(UpgradeRequired), pending())
    }

    @Test
    fun aManualLogoutSilencesRequestsStillInFlight() = runTest {
        val sentInTheFirstAccount = bus.now()
        bus.handled(SessionExpired)                 // the user logged out, then into another account

        bus.raise(SessionExpired, sentInTheFirstAccount)

        assertEquals(emptySet(), pending())
    }
}
