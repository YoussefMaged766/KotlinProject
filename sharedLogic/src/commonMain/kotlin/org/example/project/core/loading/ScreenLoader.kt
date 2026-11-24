package org.example.project.core.loading

import kotlinx.coroutines.CoroutineScope
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
import org.example.project.core.config.NetworkConfig
import org.example.project.core.error.ErrorDisplay
import org.example.project.core.error.ErrorPresenter
import org.example.project.core.error.RequestKind
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEvents
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError

/** One-off things a load asks the screen to show. Everything lasting is in [ScreenState]. */
sealed interface LoadEffect {
    /** A toast or a dialog. */
    data class ShowError(val display: ErrorDisplay) : LoadEffect

    /** Tells the user why the screen changed by itself. */
    data object ShowBackOnline : LoadEffect
}

/**
 * Loads what one screen shows, by the rules both platforms share: first load, refresh, retry, slow signal, auto reload
 * and reloading after maintenance. For reads only. The platform's ViewModel owns it and adds its own UI on top.
 *
 * Collect [state] only while the screen is visible: that is how it knows whether to auto reload.
 */
class ScreenLoader<T : Any> internal constructor(
    private val scope: CoroutineScope,
    private val errorPresenter: ErrorPresenter,
    private val slowThresholdMillis: Long,
    autoReloadPolicy: AutoReloadPolicy,
    globalEvents: GlobalEvents,
    private val request: suspend () -> ApiResult<T>,
) {
    private val _state = MutableStateFlow(ScreenState<T>())
    val state: StateFlow<ScreenState<T>> = _state.asStateFlow()

    private val _effects = Channel<LoadEffect>(Channel.BUFFERED)
    val effects: Flow<LoadEffect> = _effects.receiveAsFlow()

    /** Null while a load runs and after a success, which is what AutoReloadPolicy expects. */
    private val lastError = MutableStateFlow<DataError?>(null)
    private var loadJob: Job? = null

    init {
        autoReloadPolicy.reloadSignals(lastError, isVisible = _state.subscriptionCount.map { it > 0 })
            .onEach { load(isAutomatic = true) }
            .launchIn(scope)

        globalEvents.pending
            .onEach { pending ->
                val maintenanceIsOver = GlobalEvent.Maintenance !in pending && lastError.value is DataError.Maintenance
                if (maintenanceIsOver) load()
            }
            .launchIn(scope)
    }

    /** First load, retry or refresh: what is on screen decides which. Replaces a load that is still running. */
    fun load() = load(isAutomatic = false)

    private fun load(isAutomatic: Boolean) {
        loadJob?.cancel()
        val kind = _state.value.nextLoadKind
        lastError.value = null
        _state.update { it.loading() }

        loadJob = scope.launch {
            val result = withSlowSignal(
                thresholdMillis = slowThresholdMillis,
                onSlow = { isSlow -> _state.update { it.copy(isSlow = isSlow) } },
                block = request,
            )
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.loaded(result.data) }
                    if (isAutomatic) _effects.send(LoadEffect.ShowBackOnline)
                }
                is ApiResult.Failure -> showFailure(result.error, kind)
            }
        }
    }

    private suspend fun showFailure(error: DataError, kind: RequestKind) {
        val display = errorPresenter.present(error, kind)
        _state.update { it.failed(display) }
        lastError.value = error
        if (display is ErrorDisplay.Toast || display is ErrorDisplay.Dialog) _effects.send(LoadEffect.ShowError(display))
    }
}

/** Builds each screen's [ScreenLoader], so a ViewModel only brings its request. */
class ScreenLoaderFactory(
    private val errorPresenter: ErrorPresenter,
    private val autoReloadPolicy: AutoReloadPolicy,
    private val globalEvents: GlobalEvents,
    private val config: NetworkConfig,
) {
    /** @param scope lives as long as the screen: viewModelScope on Android. */
    fun <T : Any> create(scope: CoroutineScope, request: suspend () -> ApiResult<T>): ScreenLoader<T> =
        ScreenLoader(scope, errorPresenter, config.slowRequestThresholdMillis, autoReloadPolicy, globalEvents, request)
}
