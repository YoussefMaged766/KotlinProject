package org.example.project.core.loading

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import org.example.project.core.connectivity.ConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.result.DataError
import kotlin.time.Duration.Companion.milliseconds

/** Network failures are the only ones the network itself can fix. A 404 waits for the user. */
val DataError.isFixedByReconnecting: Boolean
    get() = this is DataError.NoInternet || this is DataError.ConnectionLost || this is DataError.Timeout

/**
 * Says when a screen should repeat a failed load by itself (R8). For reads only: never connect it to an action
 * that changes data.
 *
 * One reconnect gives at most one reload, so a reload that fails again waits for the next reconnect instead of
 * looping. Manual retry doesn't go through here and always works.
 */
class AutoReloadPolicy(
    private val connectivity: ConnectivityMonitor,
    private val stableMillis: Long = 1_000,
) {
    /**
     * Emits each time the screen should reload. Collect it for as long as the screen exists, not only while it is
     * visible: a reconnect that happens while hidden is kept until [isVisible] is true again.
     *
     * @param lastError the error of the screen's latest load; null while one is running and after a success.
     */
    fun reloadSignals(
        lastError: Flow<DataError?>,
        isVisible: Flow<Boolean> = flowOf(true),
    ): Flow<Unit> = flow {
        var error: DataError? = null
        var visible = false
        var owesReload = false

        merge(
            networkChanges(),
            lastError.map(Input::Error),
            isVisible.map(Input::Visible),
        ).collect { input ->
            when (input) {
                is Input.Error -> {
                    error = input.error
                    owesReload = false                      // the load it was owed for is history
                }
                is Input.Visible -> visible = input.visible
                Input.NetworkLost -> owesReload = false     // the next reconnect will owe it again
                Input.NetworkImproved -> owesReload = error?.isFixedByReconnecting == true
            }
            if (owesReload && visible) {
                owesReload = false
                emit(Unit)
            }
        }
    }

    /**
     * Improved means: better than the worst seen since the network last settled, for [stableMillis]. Comparing with
     * the worst, not the previous settled value, is what notices a dip too short to settle.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun networkChanges(): Flow<Input> = flow {
        var worst = Int.MAX_VALUE
        emitAll(
            connectivity.status.transformLatest { status ->
                worst = minOf(worst, status.quality)
                if (!status.isConnected) emit(Input.NetworkLost)

                delay(stableMillis.milliseconds)                         // cancelled by the next status change
                val improved = status.quality > worst
                worst = status.quality
                if (improved) emit(Input.NetworkImproved)
            }
        )
    }

    private val NetworkStatus.quality: Int
        get() = when (this) {
            NetworkStatus.Offline -> 0
            NetworkStatus.Weak -> 1
            NetworkStatus.Online -> 2
        }

    private sealed interface Input {
        data class Error(val error: DataError?) : Input
        data class Visible(val visible: Boolean) : Input
        data object NetworkLost : Input
        data object NetworkImproved : Input
    }
}
