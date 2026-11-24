package org.example.project.core.presentation.root

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.example.project.core.connectivity.ConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEvents

@Immutable
data class AppState(
    val isOffline: Boolean = false,
    /** Covers the whole app: an update outranks maintenance, because it is still needed afterwards. */
    val blockedBy: AppBlock? = null,
    val isSessionExpired: Boolean = false,
)

enum class AppBlock { UpgradeRequired, Maintenance }

@Immutable
sealed interface AppIntent {
    data object RetryAfterMaintenance : AppIntent
    data object SessionExpiredConfirmed : AppIntent
}

/** What the app shows whichever screen is open: the offline banner and the reactions to global events. */
class AppViewModel(
    connectivity: ConnectivityMonitor,
    private val globalEvents: GlobalEvents,
) : ViewModel() {

    val state: StateFlow<AppState> =
        combine(connectivity.status, globalEvents.pending, ::toState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, toState(connectivity.status.value, emptySet()))

    fun onIntent(intent: AppIntent) = when (intent) {
        AppIntent.RetryAfterMaintenance -> globalEvents.handled(GlobalEvent.Maintenance)
        AppIntent.SessionExpiredConfirmed -> globalEvents.handled(GlobalEvent.SessionExpired)
    }

    private fun toState(status: NetworkStatus, pending: Set<GlobalEvent>) = AppState(
        isOffline = !status.isConnected,
        blockedBy = when {
            GlobalEvent.UpgradeRequired in pending -> AppBlock.UpgradeRequired
            GlobalEvent.Maintenance in pending -> AppBlock.Maintenance
            else -> null
        },
        isSessionExpired = GlobalEvent.SessionExpired in pending,
    )
}
