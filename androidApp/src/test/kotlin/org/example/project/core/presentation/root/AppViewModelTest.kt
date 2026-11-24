package org.example.project.core.presentation.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.project.FakeConnectivityMonitor
import org.example.project.FakeGlobalEvents
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.events.GlobalEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val connectivity = FakeConnectivityMonitor()
    private val events = FakeGlobalEvents()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun theBannerFollowsTheNetwork() = runTest(dispatcher) {
        val viewModel = AppViewModel(connectivity, events)
        runCurrent()
        assertEquals(AppState(), viewModel.state.value)

        connectivity.set(NetworkStatus.Offline)
        runCurrent()
        assertEquals(AppState(isOffline = true), viewModel.state.value)

        connectivity.set(NetworkStatus.Weak)                     // connected, so no banner
        runCurrent()
        assertEquals(AppState(), viewModel.state.value)
    }

    @Test
    fun startingOfflineShowsTheBannerFromTheFirstFrame() = runTest(dispatcher) {
        connectivity.set(NetworkStatus.Offline)
        assertEquals(true, AppViewModel(connectivity, events).state.value.isOffline)
    }

    @Test
    fun maintenanceBlocksTheAppUntilTheUserTriesAgain() = runTest(dispatcher) {
        val viewModel = AppViewModel(connectivity, events)
        events.raise(GlobalEvent.Maintenance)
        runCurrent()
        assertEquals(AppBlock.Maintenance, viewModel.state.value.blockedBy)

        viewModel.onIntent(AppIntent.RetryAfterMaintenance)
        runCurrent()
        assertEquals(null, viewModel.state.value.blockedBy)
    }

    @Test
    fun anUpdateOutranksMaintenance() = runTest(dispatcher) {
        val viewModel = AppViewModel(connectivity, events)
        events.raise(GlobalEvent.Maintenance)
        events.raise(GlobalEvent.UpgradeRequired)
        runCurrent()
        assertEquals(AppBlock.UpgradeRequired, viewModel.state.value.blockedBy)
    }

    @Test
    fun anExpiredSessionIsShownUntilConfirmed() = runTest(dispatcher) {
        val viewModel = AppViewModel(connectivity, events)
        events.raise(GlobalEvent.SessionExpired)
        runCurrent()
        assertEquals(true, viewModel.state.value.isSessionExpired)

        viewModel.onIntent(AppIntent.SessionExpiredConfirmed)
        runCurrent()
        assertEquals(AppState(), viewModel.state.value)
    }
}
