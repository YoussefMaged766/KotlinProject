package org.example.project.core.loading

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.example.project.core.config.AppEnvironment
import org.example.project.core.config.AppInfo
import org.example.project.core.config.NetworkConfig
import org.example.project.core.connectivity.FakeConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.error.ErrorDisplay
import org.example.project.core.error.ErrorMessage
import org.example.project.core.error.ErrorPresenter
import org.example.project.core.error.MessageKey
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEventBus
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import org.example.project.core.result.ErrorDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

/** The whole loading behaviour of a screen (R1, R6, R8, M2), for both platforms. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenLoaderTest {

    private val burger = "Burger"
    private val pizza = "Pizza"
    private val events = GlobalEventBus()

    /** The screen as an app runs it: a collector while visible, and every effect recorded. */
    private inner class Screen(
        private val scope: TestScope,
        network: NetworkStatus = NetworkStatus.Online,
        answer: suspend (call: Int) -> ApiResult<List<String>>,
    ) {
        var calls = 0
        val connectivity = FakeConnectivityMonitor(network)
        val effects = mutableListOf<LoadEffect>()
        private val loader = ScreenLoaderFactory(
            errorPresenter = ErrorPresenter(showTechnicalDetails = false),
            autoReloadPolicy = AutoReloadPolicy(connectivity),
            globalEvents = events,
            config = NetworkConfig(AppEnvironment.Prod, AppInfo("android", "1.0", "en")),
        ).create(scope.backgroundScope) { answer(++calls) }
        private var watching: Job? = null

        init {
            scope.backgroundScope.launch { loader.effects.collect { effects += it } }
            loader.load()
            show()
        }

        val state get() = loader.state.value

        fun show() {
            watching = scope.backgroundScope.launch { loader.state.collect {} }
            scope.runCurrent()
        }

        fun hide() {
            watching?.cancel()
            scope.runCurrent()
        }

        fun load() {
            loader.load()
            scope.runCurrent()
        }

        fun network(status: NetworkStatus) {
            connectivity.set(status)
            scope.runCurrent()
        }

        fun after(millis: Long) {
            scope.advanceTimeBy(millis.milliseconds)
            scope.runCurrent()
        }
    }

    private fun success(vararg items: String) = ApiResult.Success(items.toList())
    private fun failure(error: DataError) = ApiResult.Failure(error)

    private fun fullScreen(key: MessageKey) = Content.Failed(ErrorDisplay.FullScreen(ErrorMessage(key), canRetry = true))
    private fun loaded(vararg items: String) = Content.Loaded(items.toList())

    // ---- First load ----------------------------------------------------------------------------

    @Test
    fun showsLoadingThenTheData() = runTest {
        val screen = Screen(this) { delay(1_000); success(burger) }
        assertEquals(Content.Loading, screen.state.content)

        screen.after(1_000)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(1, screen.calls)
    }

    @Test
    fun nothingToListIsEmpty() = runTest {
        assertEquals(Content.Empty, Screen(this) { success() }.state.content)
    }

    @Test
    fun aFailedFirstLoadFillsTheScreen() = runTest {
        val screen = Screen(this, NetworkStatus.Offline) { failure(DataError.NoInternet()) }

        assertEquals(fullScreen(MessageKey.NoInternet), screen.state.content)
        assertEquals(emptyList(), screen.effects)
    }

    @Test
    fun retryShowsLoadingAndLoadsAgain() = runTest {
        val screen = Screen(this) { call -> if (call == 1) failure(DataError.Server(500)) else { delay(100); success(burger) } }

        screen.load()
        assertEquals(Content.Loading, screen.state.content)

        screen.after(100)
        assertEquals(loaded(burger), screen.state.content)
    }

    @Test
    fun onlyTheLastOfSeveralRetriesReachesTheScreen() = runTest {
        val screen = Screen(this) { call ->
            if (call == 1) failure(DataError.Server(500)) else { delay(10_000L / call); success(if (call == 4) pizza else burger) }
        }

        repeat(3) { screen.load() }
        screen.after(20_000)

        assertEquals(loaded(pizza), screen.state.content)
        assertEquals(4, screen.calls)
    }

    // ---- Refresh -------------------------------------------------------------------------------

    @Test
    fun refreshKeepsTheListOnScreen() = runTest {
        val screen = Screen(this) { call -> if (call == 1) success(burger) else { delay(100); success(pizza) } }

        screen.load()
        assertEquals(loaded(burger), screen.state.content)
        assertEquals(true, screen.state.isRefreshing)

        screen.after(100)
        assertEquals(loaded(pizza), screen.state.content)
        assertEquals(false, screen.state.isRefreshing)
    }

    @Test
    fun aFailedRefreshKeepsTheListAndShowsAToast() = runTest {
        val screen = Screen(this) { call -> if (call == 1) success(burger) else failure(DataError.ConnectionLost()) }

        screen.load()

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(false, screen.state.isRefreshing)
        val toast = ErrorDisplay.Toast(ErrorMessage(MessageKey.ConnectionLost), canRetry = true)
        assertEquals(listOf<LoadEffect>(LoadEffect.ShowError(toast)), screen.effects)
    }

    @Test
    fun refreshingWhileOfflineLeavesItToTheBanner() = runTest {
        val screen = Screen(this) { call -> if (call == 1) success(burger) else failure(DataError.NoInternet()) }

        screen.network(NetworkStatus.Offline)
        screen.load()

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(emptyList(), screen.effects)
    }

    @Test
    fun aBusinessErrorOnRefreshIsADialog() = runTest {
        val closed = DataError.Business("CLOSED", ErrorDetails(serverMessage = "Closed today"))
        val screen = Screen(this) { call -> if (call == 1) success(burger) else failure(closed) }

        screen.load()

        assertIs<ErrorDisplay.Dialog>(assertIs<LoadEffect.ShowError>(screen.effects.single()).display)
    }

    // ---- Slow network --------------------------------------------------------------------------

    @Test
    fun aSlowLoadSaysSoUntilItEnds() = runTest {
        val screen = Screen(this) { delay(5_000); success(burger) }

        screen.after(2_999)
        assertEquals(false, screen.state.isSlow)

        screen.after(1)
        assertEquals(true, screen.state.isSlow)

        screen.after(2_000)
        assertEquals(false, screen.state.isSlow)
        assertEquals(loaded(burger), screen.state.content)
    }

    @Test
    fun aRetryStartsItsOwnSlowTimer() = runTest {
        val screen = Screen(this) { delay(10_000); success(burger) }
        screen.after(4_000)

        screen.load()
        assertEquals(false, screen.state.isSlow)

        screen.after(3_000)
        assertEquals(true, screen.state.isSlow)
    }

    // ---- Auto reload ---------------------------------------------------------------------------

    @Test
    fun itReloadsByItselfWhenTheNetworkIsBack() = runTest {
        val screen = Screen(this, NetworkStatus.Offline) { call -> if (call == 1) failure(DataError.NoInternet()) else success(burger) }
        screen.after(5_000)

        screen.network(NetworkStatus.Online)
        screen.after(1_000)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(emptyList(), screen.effects)                 // the banner says it, no toast
    }

    @Test
    fun anAutoReloadThatFailsDoesNotLoop() = runTest {
        val screen = Screen(this, NetworkStatus.Offline) { failure(DataError.NoInternet()) }
        screen.after(5_000)

        screen.network(NetworkStatus.Online)
        screen.after(60_000)

        assertEquals(2, screen.calls)
        assertEquals(fullScreen(MessageKey.NoInternet), screen.state.content)
    }

    @Test
    fun aRefreshThatFailedOfflineIsRepeatedWithTheListStillShown() = runTest {
        val screen = Screen(this) { call ->
            when (call) {
                1 -> success(burger)
                2 -> failure(DataError.NoInternet())
                else -> { delay(100); success(pizza) }
            }
        }
        screen.after(5_000)
        screen.network(NetworkStatus.Offline)
        screen.load()

        screen.network(NetworkStatus.Online)
        screen.after(1_000)
        assertEquals(loaded(burger), screen.state.content)

        screen.after(100)
        assertEquals(loaded(pizza), screen.state.content)
    }

    @Test
    fun aListThatLoadedFineIsNotReloadedWhenTheNetworkReturns() = runTest {
        val screen = Screen(this) { success(burger) }
        screen.after(5_000)

        screen.network(NetworkStatus.Offline)
        screen.after(5_000)
        assertEquals(loaded(burger), screen.state.content)

        screen.network(NetworkStatus.Online)
        screen.after(5_000)
        assertEquals(1, screen.calls)
    }

    @Test
    fun aRetryThatWorkedSettlesTheOldError() = runTest {
        val screen = Screen(this) { call -> if (call == 1) failure(DataError.ConnectionLost()) else success(burger) }
        screen.after(5_000)
        screen.load()

        screen.network(NetworkStatus.Offline)
        screen.after(5_000)
        screen.network(NetworkStatus.Online)
        screen.after(5_000)

        assertEquals(2, screen.calls)
    }

    @Test
    fun aRetryStillRunningIsNotReplacedByAnAutoReload() = runTest {
        val screen = Screen(this, NetworkStatus.Offline) { call -> if (call == 1) failure(DataError.NoInternet()) else { delay(3_000); success(burger) } }
        screen.after(5_000)
        screen.network(NetworkStatus.Online)
        screen.load()

        screen.after(3_000)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(2, screen.calls)
    }

    @Test
    fun aHiddenScreenReloadsWhenItIsSeenAgain() = runTest {
        val screen = Screen(this, NetworkStatus.Offline) { call -> if (call == 1) failure(DataError.NoInternet()) else success(burger) }
        screen.after(5_000)
        screen.hide()

        screen.network(NetworkStatus.Online)
        screen.after(5_000)
        assertEquals(1, screen.calls)

        screen.show()
        assertEquals(loaded(burger), screen.state.content)
    }

    // ---- App-wide events -----------------------------------------------------------------------

    @Test
    fun anAppWideErrorIsNotShownTwice() = runTest {
        val screen = Screen(this) { call -> if (call == 1) success(burger) else failure(DataError.UpgradeRequired()) }
        screen.load()
        assertEquals(emptyList(), screen.effects)
    }

    @Test
    fun afterMaintenanceItLoadsAgainByItself() = runTest {
        events.raise(GlobalEvent.Maintenance, sentAt = events.now())                                    // what the network layer does
        val screen = Screen(this) { call -> if (call == 1) failure(DataError.Maintenance()) else success(burger) }
        assertEquals(fullScreen(MessageKey.Generic), screen.state.content)      // behind the maintenance screen

        events.handled(GlobalEvent.Maintenance)                                  // the user tapped Try again there
        runCurrent()

        assertEquals(loaded(burger), screen.state.content)
    }

    @Test
    fun clearingMaintenanceLeavesOtherScreensAlone() = runTest {
        val screen = Screen(this) { success(burger) }
        events.raise(GlobalEvent.Maintenance, sentAt = events.now())
        runCurrent()
        events.handled(GlobalEvent.Maintenance)
        runCurrent()
        assertEquals(1, screen.calls)
    }
}
