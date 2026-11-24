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
import org.example.project.core.loading.ScreenLoaderFactory
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import org.example.project.core.result.ErrorDetails
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** How this screen uses ScreenLoader. The loading rules themselves are tested in ScreenLoaderTest. */
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
            loaderFactory = ScreenLoaderFactory(
                errorPresenter = ErrorPresenter(showTechnicalDetails = false),
                autoReloadPolicy = AutoReloadPolicy(connectivity),
                globalEvents = events,
                config = NetworkConfig(AppEnvironment.Prod, AppInfo("android", "1.0", "en")),
            ),
        )
        private var watching: Job? = null

        init {
            scope.backgroundScope.launch { viewModel.effect.collect { effects += it } }
            show()
        }

        val state get() = viewModel.state.value

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

    @Test
    fun showsLoadingThenTheCategories() = test {
        val screen = Screen(this) { delay(1_000); success(burger) }
        assertEquals(Content.Loading, screen.state.content)

        screen.after(1_000)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(1, screen.repository.calls)
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
    fun aFailedRefreshKeepsTheListAndShowsAToast() = test {
        val screen = Screen(this) { call -> if (call == 1) success(burger) else failure(DataError.ConnectionLost()) }

        screen.send(CategoriesIntent.Refresh)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(false, screen.state.isRefreshing)
        val toast = ErrorDisplay.Toast(ErrorMessage(MessageKey.ConnectionLost), canRetry = true)
        assertEquals(listOf<CategoriesEffect>(CategoriesEffect.ShowError(toast)), screen.effects)
    }

    @Test
    fun itReloadsByItselfWhenTheNetworkIsBack() = test {
        val screen = Screen(this, NetworkStatus.Offline) { call -> if (call == 1) failure(DataError.NoInternet()) else success(burger) }
        screen.after(5_000)

        screen.network(NetworkStatus.Online)
        screen.after(1_000)

        assertEquals(loaded(burger), screen.state.content)
        assertEquals(emptyList(), screen.effects)
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

    @Test
    fun tappingACategoryOpensIt() = test {
        val screen = Screen(this) { success(burger) }
        screen.send(CategoriesIntent.CategoryClicked("1"))
        assertEquals(listOf<CategoriesEffect>(CategoriesEffect.OpenCategory("1")), screen.effects)
    }
}
