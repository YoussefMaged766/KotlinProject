package org.example.project.core.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.example.project.core.connectivity.ConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEvents

/** What the app shows whichever screen is open. */
data class AppState(
    val isOffline: Boolean = false,
    val blockedBy: AppBlock? = null,
    val isSessionExpired: Boolean = false,
)

/** Covers the whole app. */
enum class AppBlock { UpgradeRequired, Maintenance }

/** Decides [AppState] for both platforms, and passes the app's reactions on. */
class AppStatus(
    private val connectivity: ConnectivityMonitor,
    private val globalEvents: GlobalEvents,
) {
    /** For the first frame, before [state] is collected. */
    val initialState: AppState get() = toState(connectivity.status.value, emptySet())

    val state: Flow<AppState> = combine(connectivity.status, globalEvents.pending, ::toState)

    fun retryAfterMaintenance() = globalEvents.handled(GlobalEvent.Maintenance)

    fun sessionExpiredConfirmed() = globalEvents.handled(GlobalEvent.SessionExpired)

    private fun toState(status: NetworkStatus, pending: Set<GlobalEvent>) = AppState(
        isOffline = !status.isConnected,
        blockedBy = when {
            GlobalEvent.UpgradeRequired in pending -> AppBlock.UpgradeRequired   // still needed after maintenance
            GlobalEvent.Maintenance in pending -> AppBlock.Maintenance
            else -> null
        },
        isSessionExpired = GlobalEvent.SessionExpired in pending,
    )
}
