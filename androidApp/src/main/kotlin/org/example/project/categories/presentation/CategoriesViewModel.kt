package org.example.project.categories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.categories.presentation.mapper.toUi
import org.example.project.categories.presentation.model.CategoryUi
import org.example.project.core.config.NetworkConfig
import org.example.project.core.error.ErrorDisplay
import org.example.project.core.error.ErrorPresenter
import org.example.project.core.error.RequestKind
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEvents
import org.example.project.core.loading.AutoReloadPolicy
import org.example.project.core.loading.ScreenState
import org.example.project.core.loading.failed
import org.example.project.core.loading.loaded
import org.example.project.core.loading.loading
import org.example.project.core.loading.withSlowSignal
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError

class CategoriesViewModel(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val errorPresenter: ErrorPresenter,
    autoReloadPolicy: AutoReloadPolicy,
    globalEvents: GlobalEvents,
    private val config: NetworkConfig,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesState())
    val state: StateFlow<CategoriesState> = _state.asStateFlow()

    private val _effect = Channel<CategoriesEffect>(Channel.BUFFERED)
    val effect: Flow<CategoriesEffect> = _effect.receiveAsFlow()

    /** Null while a load runs and after a success, which is what AutoReloadPolicy expects. */
    private val lastError = MutableStateFlow<DataError?>(null)
    private var loadJob: Job? = null

    init {
        onIntent(CategoriesIntent.LoadCategories)

        // The UI collects state only while STARTED, so "someone is collecting" means "visible".
        autoReloadPolicy.reloadSignals(lastError, isVisible = _state.subscriptionCount.map { it > 0 })
            .onEach { onIntent(CategoriesIntent.NetworkRestored) }
            .launchIn(viewModelScope)

        globalEvents.pending
            .onEach { pending ->
                val maintenanceIsOver = GlobalEvent.Maintenance !in pending && lastError.value is DataError.Maintenance
                if (maintenanceIsOver) onIntent(CategoriesIntent.Retry)
            }
            .launchIn(viewModelScope)
    }

    /** The only public function. Every user action comes through here. */
    fun onIntent(intent: CategoriesIntent) {
        when (intent) {
            CategoriesIntent.LoadCategories,
            CategoriesIntent.Retry,
            CategoriesIntent.Refresh -> load()
            CategoriesIntent.NetworkRestored -> load(onSuccess = { sendEffect(CategoriesEffect.ShowBackOnline) })
            is CategoriesIntent.CategoryClicked -> sendEffect(CategoriesEffect.OpenCategory(intent.categoryId))
        }
    }

    /** The one path for every load, whoever asked for it. */
    private fun load(onSuccess: () -> Unit = {}) {
        loadJob?.cancel()                      // a second Retry tap replaces the running request
        val kind = _state.value.screen.nextLoadKind
        lastError.value = null
        updateScreen { loading() }

        loadJob = viewModelScope.launch {
            val result = withSlowSignal(
                thresholdMillis = config.slowRequestThresholdMillis,
                onSlow = { isSlow -> updateScreen { copy(isSlow = isSlow) } },
                block = { getCategoriesUseCase() },
            )
            when (result) {
                is ApiResult.Success -> {
                    updateScreen { loaded(result.data.toUi()) }
                    onSuccess()
                }
                is ApiResult.Failure -> showFailure(result.error, kind)
            }
        }
    }

    private fun showFailure(error: DataError, kind: RequestKind) {
        val display = errorPresenter.present(error, kind)
        updateScreen { failed(display) }
        lastError.value = error
        if (display is ErrorDisplay.Toast || display is ErrorDisplay.Dialog) sendEffect(CategoriesEffect.ShowError(display))
    }

    private fun updateScreen(change: ScreenState<ImmutableList<CategoryUi>>.() -> ScreenState<ImmutableList<CategoryUi>>) {
        _state.update { it.copy(screen = it.screen.change()) }
    }

    private fun sendEffect(effect: CategoriesEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }
}
