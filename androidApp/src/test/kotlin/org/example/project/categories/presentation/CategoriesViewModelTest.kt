package org.example.project.categories.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.example.project.FakeConnectivityMonitor
import org.example.project.FakeGlobalEvents
import org.example.project.categories.domain.model.Category
import org.example.project.categories.domain.repository.CategoriesRepository
import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.categories.presentation.mapper.toUi
import org.example.project.core.config.AppEnvironment
import org.example.project.core.config.AppInfo
import org.example.project.core.config.NetworkConfig
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.error.ErrorDisplay
import org.example.project.core.error.ErrorMessage
import org.example.project.core.error.ErrorPresenter
import org.example.project.core.error.MessageKey
import org.example.project.core.events.GlobalEvent
import org.example.project.core.loading.AutoReloadPolicy
import org.example.project.core.loading.Content
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import org.example.project.core.result.ErrorDetails
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private val burger = Category(id = "1", name = "Burger", imageUrl = null, isAll = false)
    private val pizza = Category(id = "2", name = "Pizza", imageUrl = null, isAll = false)

    private class FakeRepository : CategoriesRepository {
        var calls = 0
        var answer: suspend (call: Int) -> ApiResult<List<Category>> = { ApiResult.Success(emptyList()) }
        override suspend fun getCategories() = answer(++calls)
    }

    /** The screen as the app runs it: a collector while visible, and every effect recorded. */
    private inner class Screen(private val scope: TestScope, network: NetworkStatus = NetworkStatus.Online, answer: suspend (Int) -> ApiResult<List<Category>>) {
        val repository = FakeRepository().also { it.answer = answer }
        val connectivity = FakeConnectivityMonitor(network)
        val effects = mutableListOf<CategoriesEffect>()
        val viewModel = CategoriesViewModel(
            getCategoriesUseCase = GetCategoriesUseCase(repository),
            errorPresenter = ErrorPresenter(showTechnicalDetails = false),
            autoReloadPolicy = AutoReloadPolicy(connectivity),
            globalEvents = events,
            config = NetworkConfig(AppEnvironment.Prod, AppInfo("android", "1.0", "en")),
        )
        private var watching: Job? = null

        init {
            scope.backgroundScope.launch { viewModel.effect.collect { effects += it } }
            show()
        }

        val state get() = viewModel.state.value.screen

        fun show() {
            watching = scope.backgroundScope.launch { viewModel.state.collect {} }
            scope.runCurrent()
        }

        fun hide() {
            watching?.cancel()
            scope.runCurrent()
        }

        fun send(intent: CategoriesIntent) {
            viewModel.onIntent(intent)
            scope.runCurrent()
        }

        fun network(status: NetworkStatus) {
            connectivity.set(status)
            scope.runCurrent()
        }

        fun after(millis: Long) {
            scope.advanceTimeBy(millis)
            scope.runCurrent()
        }
    }

    private val events = FakeGlobalEvents()

    private fun success(vararg categories: Category) = ApiResult.Success(categories.toList())
    private fun failure(error: DataError) = ApiResult.Failure(error)
    private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

    private fun fullScreen(key: MessageKey) = Content.Failed(ErrorDisplay.FullScreen(ErrorMessage(key), canRetry = true))
    private fun loaded(vararg categories: Category) = Content.Loaded(categories.toList().toUi())

    // ---- First load ----------------------------------------------------------------------------

    @Test
    fun showsLoadingThenTheCategories() = test {
        val screen = Screen(this) { delay(1_000); success(burger) }
        assertEquals(Content.Loading, screen.state.content)

        screen.after(1_000)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(1, screen.repository.calls)
    }

    @Test
    fun noCategoriesIsEmpty() = test {
        assertEquals(Content.Empty, Screen(this) { success() }.state.content)
    }

    @Test
    fun aFailedFirstLoadFillsTheScreen() = test {
        val screen = Screen(this, NetworkStatus.Offline) { failure(DataError.NoInternet()) }

        assertEquals(fullScreen(MessageKey.NoInternet), screen.state.content)
        assertEquals(emptyList(), screen.effects)
    }

    @Test
    fun retryShowsLoadingAndLoadsAgain() = test {
        val screen = Screen(this) { call -> if (call == 1) failure(DataError.Server(500)) else { delay(100); success(burger) } }

        screen.send(CategoriesIntent.Retry)
        assertEquals(Content.Loading, screen.state.content)

        screen.after(100)
        assertEquals(loaded(burger), screen.state.content)
    }

    @Test
    fun onlyTheLastOfSeveralRetriesReachesTheScreen() = test {
        val screen = Screen(this) { call ->
            if (call == 1) failure(DataError.Server(500)) else { delay(10_000L / call); success(if (call == 4) pizza else burger) }
        }

        repeat(3) { screen.send(CategoriesIntent.Retry) }
        screen.after(20_000)

        assertEquals(loaded(pizza), screen.state.content)
        assertEquals(4, screen.repository.calls)
    }

    // ---- Refresh -------------------------------------------------------------------------------

    @Test
    fun refreshKeepsTheListOnScreen() = test {
        val screen = Screen(this) { call -> if (call == 1) success(burger) else { delay(100); success(pizza) } }

        screen.send(CategoriesIntent.Refresh)
        assertEquals(loaded(burger), screen.state.content)
        assertEquals(true, screen.state.isRefreshing)

        screen.after(100)
        assertEquals(loaded(pizza), screen.state.content)
        assertEquals(false, screen.state.isRefreshing)
    }

    @Test
    fun aFailedRefreshKeepsTheListAndShowsAToast() = test {
        val screen = Screen(this) { call -> if (call == 1) success(burger) else failure(DataError.ConnectionLost()) }

        screen.send(CategoriesIntent.Refresh)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(false, screen.state.isRefreshing)
        val toast = ErrorDisplay.Toast(ErrorMessage(MessageKey.ConnectionLost))
        assertEquals(listOf<CategoriesEffect>(CategoriesEffect.ShowError(toast)), screen.effects)
    }

    @Test
    fun refreshingWhileOfflineLeavesItToTheBanner() = test {
        val screen = Screen(this) { call -> if (call == 1) success(burger) else failure(DataError.NoInternet()) }

        screen.network(NetworkStatus.Offline)
        screen.send(CategoriesIntent.Refresh)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(emptyList(), screen.effects)
    }

    @Test
    fun aBusinessErrorOnRefreshIsADialog() = test {
        val closed = DataError.Business("CLOSED", ErrorDetails(serverMessage = "Closed today"))
        val screen = Screen(this) { call -> if (call == 1) success(burger) else failure(closed) }

        screen.send(CategoriesIntent.Refresh)

        assertIs<ErrorDisplay.Dialog>(assertIs<CategoriesEffect.ShowError>(screen.effects.single()).display)
    }

    // ---- Slow network --------------------------------------------------------------------------

    @Test
    fun aSlowLoadSaysSoUntilItEnds() = test {
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
    fun aRetryStartsItsOwnSlowTimer() = test {
        val screen = Screen(this) { delay(10_000); success(burger) }
        screen.after(4_000)

        screen.send(CategoriesIntent.Retry)
        assertEquals(false, screen.state.isSlow)

        screen.after(3_000)
        assertEquals(true, screen.state.isSlow)
    }

    // ---- Auto reload ---------------------------------------------------------------------------

    @Test
    fun itReloadsByItselfWhenTheNetworkIsBackAndSaysWhy() = test {
        val screen = Screen(this, NetworkStatus.Offline) { call -> if (call == 1) failure(DataError.NoInternet()) else success(burger) }
        screen.after(5_000)

        screen.network(NetworkStatus.Online)
        screen.after(1_000)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(listOf<CategoriesEffect>(CategoriesEffect.ShowBackOnline), screen.effects)
    }

    @Test
    fun aManualRetryDoesNotSayBackOnline() = test {
        val screen = Screen(this) { call -> if (call == 1) failure(DataError.ConnectionLost()) else success(burger) }
        screen.send(CategoriesIntent.Retry)
        assertEquals(emptyList(), screen.effects)
    }

    @Test
    fun anAutoReloadThatFailsDoesNotLoop() = test {
        val screen = Screen(this, NetworkStatus.Offline) { failure(DataError.NoInternet()) }
        screen.after(5_000)

        screen.network(NetworkStatus.Online)
        screen.after(60_000)

        assertEquals(2, screen.repository.calls)
        assertEquals(fullScreen(MessageKey.NoInternet), screen.state.content)
    }

    @Test
    fun aRefreshThatFailedOfflineIsRepeatedWithTheListStillShown() = test {
        val screen = Screen(this) { call ->
            when (call) {
                1 -> success(burger)
                2 -> failure(DataError.NoInternet())
                else -> { delay(100); success(pizza) }
            }
        }
        screen.after(5_000)
        screen.network(NetworkStatus.Offline)
        screen.send(CategoriesIntent.Refresh)

        screen.network(NetworkStatus.Online)
        screen.after(1_000)
        assertEquals(loaded(burger), screen.state.content)

        screen.after(100)
        assertEquals(loaded(pizza), screen.state.content)
    }

    @Test
    fun aListThatLoadedFineIsNotReloadedWhenTheNetworkReturns() = test {
        val screen = Screen(this) { success(burger) }
        screen.after(5_000)

        screen.network(NetworkStatus.Offline)
        screen.after(5_000)
        assertEquals(loaded(burger), screen.state.content)

        screen.network(NetworkStatus.Online)
        screen.after(5_000)
        assertEquals(1, screen.repository.calls)
    }

    @Test
    fun aRetryThatWorkedSettlesTheOldError() = test {
        val screen = Screen(this) { call -> if (call == 1) failure(DataError.ConnectionLost()) else success(burger) }
        screen.after(5_000)
        screen.send(CategoriesIntent.Retry)

        screen.network(NetworkStatus.Offline)
        screen.after(5_000)
        screen.network(NetworkStatus.Online)
        screen.after(5_000)

        assertEquals(2, screen.repository.calls)
    }

    @Test
    fun aRetryStillRunningIsNotReplacedByAnAutoReload() = test {
        val screen = Screen(this, NetworkStatus.Offline) { call -> if (call == 1) failure(DataError.NoInternet()) else { delay(3_000); success(burger) } }
        screen.after(5_000)
        screen.network(NetworkStatus.Online)
        screen.send(CategoriesIntent.Retry)

        screen.after(3_000)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(2, screen.repository.calls)
    }

    @Test
    fun aHiddenScreenReloadsWhenItIsSeenAgain() = test {
        val screen = Screen(this, NetworkStatus.Offline) { call -> if (call == 1) failure(DataError.NoInternet()) else success(burger) }
        screen.after(5_000)
        screen.hide()

        screen.network(NetworkStatus.Online)
        screen.after(5_000)
        assertEquals(1, screen.repository.calls)

        screen.show()
        assertEquals(loaded(burger), screen.state.content)
    }

    // ---- App-wide events -----------------------------------------------------------------------

    @Test
    fun anAppWideErrorIsNotShownTwice() = test {
        val screen = Screen(this) { call -> if (call == 1) success(burger) else failure(DataError.UpgradeRequired()) }
        screen.send(CategoriesIntent.Refresh)
        assertEquals(emptyList(), screen.effects)
    }

    @Test
    fun afterMaintenanceItLoadsAgainByItself() = test {
        events.raise(GlobalEvent.Maintenance)                                    // what the network layer does
        val screen = Screen(this) { call -> if (call == 1) failure(DataError.Maintenance()) else success(burger) }
        assertEquals(fullScreen(MessageKey.Generic), screen.state.content)      // behind the maintenance screen

        events.handled(GlobalEvent.Maintenance)                                  // the user tapped Try again there
        runCurrent()

        assertEquals(loaded(burger), screen.state.content)
    }

    @Test
    fun clearingMaintenanceLeavesOtherScreensAlone() = test {
        val screen = Screen(this) { success(burger) }
        events.raise(GlobalEvent.Maintenance)
        runCurrent()
        events.handled(GlobalEvent.Maintenance)
        runCurrent()
        assertEquals(1, screen.repository.calls)
    }

    @Test
    fun tappingACategoryOpensIt() = test {
        val screen = Screen(this) { success(burger) }
        screen.send(CategoriesIntent.CategoryClicked("1"))
        assertEquals(listOf<CategoriesEffect>(CategoriesEffect.OpenCategory("1")), screen.effects)
    }
}
