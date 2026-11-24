package org.example.project.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.example.project.core.app.AppState
import org.example.project.core.app.AppStatus
import org.example.project.core.loading.LoadEffect
import org.example.project.core.loading.ScreenLoader
import org.example.project.core.loading.ScreenState

/** Swift can't collect a Flow; it gets a callback on the main thread, and this to stop it. */
class FlowSubscription internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

private fun <T> Flow<T>.watch(scope: CoroutineScope, action: (T) -> Unit) = FlowSubscription(onEach { action(it) }.launchIn(scope))

/** What viewModelScope is on Android. */
private fun mainScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** A [ScreenLoader] for a Swift model. Keep [watchState] running only while the screen is visible. */
class IosScreen<T : Any> internal constructor(createLoader: (CoroutineScope) -> ScreenLoader<T>) {
    private val scope = mainScope()
    private val loader = createLoader(scope)

    fun load() = loader.load()

    fun watchState(onChange: (ScreenState<T>) -> Unit): FlowSubscription = loader.state.watch(scope, onChange)

    fun watchEffects(onEffect: (LoadEffect) -> Unit): FlowSubscription = loader.effects.watch(scope, onEffect)

    /** Call from the model's deinit. */
    fun close() = scope.cancel()
}

/** [AppStatus] for the Swift app root, which lives as long as the app. */
class IosApp internal constructor(private val status: AppStatus) {
    private val scope = mainScope()

    val initialState: AppState get() = status.initialState

    fun watchState(onChange: (AppState) -> Unit): FlowSubscription = status.state.watch(scope, onChange)

    fun retryAfterMaintenance() = status.retryAfterMaintenance()

    fun sessionExpiredConfirmed() = status.sessionExpiredConfirmed()
}
