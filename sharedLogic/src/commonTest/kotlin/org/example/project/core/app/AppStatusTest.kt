package org.example.project.core.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.example.project.core.connectivity.FakeConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEventBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AppStatusTest {

    private val connectivity = FakeConnectivityMonitor()
    private val events = GlobalEventBus()
    private val status = AppStatus(connectivity, events)

    private fun raise(event: GlobalEvent) = events.raise(event, sentAt = events.now())

    // ---- The banner ------------------------------------------------------------------------------

    /** Every banner the user sees, in order. */
    private fun TestScope.recordBanners(): List<NetworkBanner?> {
        val seen = mutableListOf<NetworkBanner?>()
        backgroundScope.launch { status.state.collect { if (seen.isEmpty() || seen.last() != it.banner) seen += it.banner } }
        runCurrent()
        return seen
    }

    private fun TestScope.network(network: NetworkStatus, forMillis: Long) {
        connectivity.set(network)
        runCurrent()
        advanceTimeBy(forMillis.milliseconds)
        runCurrent()
    }

    @Test
    fun startingOnlineShowsNoBanner() = runTest {
        val seen = recordBanners()
        advanceTimeBy(10.seconds)
        assertEquals(listOf<NetworkBanner?>(null), seen)
        assertEquals(null, status.initialState.banner)
    }

    @Test
    fun startingOfflineShowsItFromTheFirstFrame() = runTest {
        connectivity.set(NetworkStatus.Offline)
        assertEquals(NetworkBanner.Offline, status.initialState.banner)
        assertEquals(listOf<NetworkBanner?>(NetworkBanner.Offline), recordBanners())
    }

    @Test
    fun comingBackTurnsItGreenThenHidesIt() = runTest {
        val seen = recordBanners()
        network(NetworkStatus.Offline, 5_000)
        assertEquals(listOf(null, NetworkBanner.Offline), seen)

        network(NetworkStatus.Online, 2_999)
        assertEquals(listOf(null, NetworkBanner.Offline, NetworkBanner.BackOnline), seen)

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(listOf(null, NetworkBanner.Offline, NetworkBanner.BackOnline, null), seen)
    }

    @Test
    fun goingOfflineAgainReplacesTheGreenAtOnce() = runTest {
        val seen = recordBanners()
        network(NetworkStatus.Offline, 1_000)
        network(NetworkStatus.Online, 1_000)
        network(NetworkStatus.Offline, 10_000)
        assertEquals(listOf(null, NetworkBanner.Offline, NetworkBanner.BackOnline, NetworkBanner.Offline), seen)
    }

    @Test
    fun aWeakNetworkIsBackOnlineAndImprovingDoesNotRestartIt() = runTest {
        val seen = recordBanners()
        network(NetworkStatus.Offline, 1_000)
        network(NetworkStatus.Weak, 2_000)
        network(NetworkStatus.Online, 999)
        assertEquals(NetworkBanner.BackOnline, seen.last())     // improving doesn't cut it short

        advanceTimeBy(1.milliseconds)                           // 3 s since it came back, not since it improved
        runCurrent()
        assertEquals(listOf(null, NetworkBanner.Offline, NetworkBanner.BackOnline, null), seen)
    }

    @Test
    fun aNetworkThatWasNeverLostShowsNothing() = runTest {
        val seen = recordBanners()
        network(NetworkStatus.Weak, 5_000)
        network(NetworkStatus.Online, 5_000)
        assertEquals(listOf<NetworkBanner?>(null), seen)
    }

    @Test
    fun anEventDuringTheGreenDoesNotHideIt() = runTest {
        val seen = recordBanners()
        network(NetworkStatus.Offline, 1_000)
        network(NetworkStatus.Online, 1_000)
        raise(GlobalEvent.SessionExpired)
        runCurrent()
        assertEquals(NetworkBanner.BackOnline, seen.last())
    }

    // ---- Global events ---------------------------------------------------------------------------

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
