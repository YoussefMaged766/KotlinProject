package org.example.project.core.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.core.connectivity.FakeConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEventBus
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStatusTest {

    private val connectivity = FakeConnectivityMonitor()
    private val events = GlobalEventBus()
    private val status = AppStatus(connectivity, events)

    private fun raise(event: GlobalEvent) = events.raise(event, sentAt = events.now())

    @Test
    fun onlyOfflineShowsTheBanner() = runTest {
        mapOf(NetworkStatus.Online to false, NetworkStatus.Weak to false, NetworkStatus.Offline to true).forEach { (network, banner) ->
            connectivity.set(network)
            assertEquals(banner, status.state.first().isOffline, "$network")
            assertEquals(banner, status.initialState.isOffline, "$network")
        }
    }

    @Test
    fun maintenanceBlocksUntilTheUserTriesAgain() = runTest {
        raise(GlobalEvent.Maintenance)
        assertEquals(AppBlock.Maintenance, status.state.first().blockedBy)

        status.retryAfterMaintenance()
        assertEquals(AppState(), status.state.first())
    }

    @Test
    fun anUpdateOutranksMaintenanceAndStays() = runTest {
        raise(GlobalEvent.Maintenance)
        raise(GlobalEvent.UpgradeRequired)
        assertEquals(AppBlock.UpgradeRequired, status.state.first().blockedBy)

        status.retryAfterMaintenance()
        assertEquals(AppBlock.UpgradeRequired, status.state.first().blockedBy)
    }

    @Test
    fun anExpiredSessionIsShownUntilConfirmed() = runTest {
        raise(GlobalEvent.SessionExpired)
        assertEquals(AppState(isSessionExpired = true), status.state.first())

        status.sessionExpiredConfirmed()
        assertEquals(AppState(), status.state.first())
    }
}
