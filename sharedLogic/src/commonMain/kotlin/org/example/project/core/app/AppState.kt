package org.example.project.core.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import org.example.project.core.connectivity.ConnectivityMonitor
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEvents
import kotlin.time.Duration.Companion.seconds

/** What the app shows whichever screen is open. */
data class AppState(
    val banner: NetworkBanner? = null,
    val blockedBy: AppBlock? = null,
    val isSessionExpired: Boolean = false,
)

/** The strip above every screen. */
enum class NetworkBanner { Offline, BackOnline }

/** Covers the whole app. */
enum class AppBlock { UpgradeRequired, Maintenance }

/** Decides [AppState] for both platforms, and passes the app's reactions on. */
class AppStatus(
    private val connectivity: ConnectivityMonitor,
    private val globalEvents: GlobalEvents,
) {
    /** For the first frame, before [state] is collected. */
    val initialState: AppState get() = toState(if (connectivity.status.value.isConnected) null else NetworkBanner.Offline, emptySet())

    val state: Flow<AppState> = combine(banner(), globalEvents.pending, ::toState)

    fun retryAfterMaintenance() = globalEvents.handled(GlobalEvent.Maintenance)

    fun sessionExpiredConfirmed() = globalEvents.handled(GlobalEvent.SessionExpired)

    /** Offline at once; after that "back online" for a moment. Never "back online" for a network that wasn't lost. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun banner(): Flow<NetworkBanner?> = flow {
        var wasLost = false
        emitAll(
            connectivity.status.map { it.isConnected }.distinctUntilChanged().transformLatest { isConnected ->
                if (!isConnected) {
                    wasLost = true
                    emit(NetworkBanner.Offline)
                    return@transformLatest
                }
                if (wasLost) {
                    wasLost = false
                    emit(NetworkBanner.BackOnline)
                    delay(BACK_ONLINE_TIME)             // cancelled if the network is lost again
                }
                emit(null)
            }
        )
    }

    private fun toState(banner: NetworkBanner?, pending: Set<GlobalEvent>) = AppState(
        banner = banner,
        blockedBy = when {
            GlobalEvent.UpgradeRequired in pending -> AppBlock.UpgradeRequired   // still needed after maintenance
            GlobalEvent.Maintenance in pending -> AppBlock.Maintenance
            else -> null
        },
        isSessionExpired = GlobalEvent.SessionExpired in pending,
    )

    private companion object {
        val BACK_ONLINE_TIME = 3.seconds
    }
}
