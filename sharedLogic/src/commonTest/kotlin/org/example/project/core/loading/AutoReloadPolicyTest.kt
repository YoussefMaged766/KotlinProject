package org.example.project.core.loading

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.example.project.core.connectivity.FakeConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.connectivity.NetworkStatus.Offline
import org.example.project.core.connectivity.NetworkStatus.Online
import org.example.project.core.connectivity.NetworkStatus.Weak
import org.example.project.core.result.DataError
import org.example.project.core.result.TimeoutKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/** R8, on virtual time: when a failed screen may reload by itself. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoReloadPolicyTest {

    private val noInternet = DataError.NoInternet()
    private val timeout = DataError.Timeout(TimeoutKind.Socket)

    /** A screen: holds its last error, and counts the reloads it was told to make. */
    private class Screen(private val scope: TestScope, initial: NetworkStatus, error: DataError?) {
        val connectivity = FakeConnectivityMonitor(initial)
        val lastError = MutableStateFlow(error)
        val isVisible = MutableStateFlow(true)
        var reloads = 0
        var onReload: () -> Unit = {}

        init {
            scope.backgroundScope.launch {
                AutoReloadPolicy(connectivity).reloadSignals(lastError, isVisible).collect {
                    reloads++
                    onReload()
                }
            }
            scope.advanceTimeBy(5_000.milliseconds)                  // whatever the network is now has been so for a while
            scope.runCurrent()
        }

        fun network(status: NetworkStatus, forMillis: Long) {
            connectivity.set(status)
            scope.runCurrent()
            scope.advanceTimeBy(forMillis.milliseconds)
            scope.runCurrent()
        }

        fun error(error: DataError?) {
            lastError.value = error
            scope.runCurrent()
        }

        fun visible(visible: Boolean) {
            isVisible.value = visible
            scope.runCurrent()
        }
    }

    private fun TestScope.screen(initial: NetworkStatus, error: DataError?) = Screen(this, initial, error)

    // ---- Which errors --------------------------------------------------------------------------

    @Test
    fun onlyNetworkErrorsAreFixedByReconnecting() {
        listOf(noInternet, DataError.ConnectionLost(), timeout).forEach { assertEquals(true, it.isFixedByReconnecting, "$it") }
        listOf(
            DataError.NotFound(), DataError.Validation(emptyMap()), DataError.Server(500), DataError.BadResponse(),
            DataError.Unknown(), DataError.RateLimited(1), DataError.Maintenance(), DataError.Unauthorized(hadSession = true),
        ).forEach { assertEquals(false, it.isFixedByReconnecting, "$it") }
    }

    @Test
    fun anErrorTheNetworkCannotFixWaitsForTheUser() = runTest {
        val screen = screen(Offline, DataError.NotFound())
        screen.network(Online, 10_000)
        assertEquals(0, screen.reloads)
    }

    @Test
    fun aScreenWithoutAnErrorIsLeftAlone() = runTest {
        val screen = screen(Offline, error = null)
        screen.network(Online, 10_000)
        assertEquals(0, screen.reloads)
    }

    // ---- Which network changes -----------------------------------------------------------------

    @Test
    fun itReloadsOnceTheNetworkHasBeenBackForASecond() = runTest {
        val screen = screen(Offline, noInternet)

        screen.network(Online, 999)
        assertEquals(0, screen.reloads)

        screen.network(Online, 1)
        assertEquals(1, screen.reloads)
    }

    @Test
    fun flappingReloadsOnceWhenItSettles() = runTest {
        val screen = screen(Offline, noInternet)
        repeat(5) {
            screen.network(Online, 300)
            screen.network(Offline, 300)
        }
        assertEquals(0, screen.reloads)

        screen.network(Online, 1_000)
        assertEquals(1, screen.reloads)
    }

    @Test
    fun aDipTooShortToSettleStillCounts() = runTest {
        val screen = screen(Online, error = null)

        screen.network(Offline, 300)
        screen.error(noInternet)                        // the dip is what failed the load
        screen.network(Online, 1_000)

        assertEquals(1, screen.reloads)
    }

    @Test
    fun aWeakNetworkIsEnough() = runTest {
        // iOS Low Data Mode is always Weak: it would otherwise never reload.
        val screen = screen(Offline, noInternet)
        screen.network(Weak, 1_000)
        assertEquals(1, screen.reloads)
    }

    @Test
    fun aWeakNetworkTurningGoodReloadsWhatTimedOut() = runTest {
        val screen = screen(Weak, timeout)
        screen.network(Online, 1_000)
        assertEquals(1, screen.reloads)
    }

    @Test
    fun aNetworkGettingWorseReloadsNothing() = runTest {
        val screen = screen(Online, timeout)
        screen.network(Weak, 10_000)
        assertEquals(0, screen.reloads)
    }

    @Test
    fun anOldOfflineSpellDoesNotMakeAWorseNetworkLookBetter() = runTest {
        val screen = screen(Offline, error = null)
        screen.network(Online, 10_000)
        screen.error(timeout)

        screen.network(Weak, 10_000)

        assertEquals(0, screen.reloads)
    }

    @Test
    fun aSteadyNetworkReloadsNothing() = runTest {
        val screen = screen(Online, timeout)
        advanceTimeBy(60_000)
        assertEquals(0, screen.reloads)
    }

    // ---- No loops ------------------------------------------------------------------------------

    @Test
    fun aReloadThatFailsAgainWaitsForTheNextReconnect() = runTest {
        val screen = screen(Offline, noInternet)
        screen.onReload = { screen.lastError.value = null }

        screen.network(Online, 1_000)
        screen.error(timeout)
        advanceTimeBy(60_000.milliseconds)
        assertEquals(1, screen.reloads)

        screen.network(Offline, 2_000)
        screen.network(Online, 1_000)
        assertEquals(2, screen.reloads)
    }

    @Test
    fun aRetryAlreadyRunningIsNotReplaced() = runTest {
        val screen = screen(Offline, noInternet)
        screen.network(Online, 500)
        screen.error(null)                              // the user tapped Retry first

        screen.network(Online, 10_000)

        assertEquals(0, screen.reloads)
    }

    // ---- Only visible screens ------------------------------------------------------------------

    @Test
    fun aHiddenScreenReloadsWhenItIsSeenAgain() = runTest {
        val screen = screen(Offline, noInternet)
        screen.visible(false)
        screen.network(Online, 10_000)
        assertEquals(0, screen.reloads)

        screen.visible(true)
        assertEquals(1, screen.reloads)

        screen.visible(false)
        screen.visible(true)
        assertEquals(1, screen.reloads)                 // one reconnect, one reload
    }

    @Test
    fun aHiddenScreenSeenWhileOfflineAgainWaits() = runTest {
        val screen = screen(Offline, noInternet)
        screen.visible(false)
        screen.network(Online, 5_000)
        screen.network(Offline, 100)

        screen.visible(true)
        assertEquals(0, screen.reloads)

        screen.network(Online, 1_000)
        assertEquals(1, screen.reloads)
    }

    @Test
    fun aHiddenScreenThatRecoveredMeanwhileIsLeftAlone() = runTest {
        val screen = screen(Offline, noInternet)
        screen.visible(false)
        screen.network(Online, 5_000)
        screen.error(null)
        screen.error(timeout)                           // a later failure, not the one the reconnect could fix

        screen.visible(true)

        assertEquals(0, screen.reloads)
    }

    @Test
    fun comingBackToAnOldErrorWithoutAReconnectReloadsNothing() = runTest {
        val screen = screen(Online, timeout)
        screen.visible(false)
        screen.visible(true)
        assertEquals(0, screen.reloads)
    }
}
