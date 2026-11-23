package org.example.project.core.connectivity

import kotlinx.coroutines.flow.StateFlow

/** What the device's network looks like right now. */
sealed interface NetworkStatus {
    /** A validated connection with real internet. */
    data object Online : NetworkStatus

    /** Connected, but the OS reports a constrained or very low-bandwidth network (R6). */
    data object Weak : NetworkStatus

    /** No usable internet: airplane mode, no signal, or Wi-Fi without internet. */
    data object Offline : NetworkStatus

    /** Weak still counts as connected: requests are sent, they may just be slow. */
    val isConnected: Boolean get() = this != Offline
}

/**
 * The only platform-specific part of connectivity.
 * Android: ConnectivityManager default-network callback. iOS: NWPathMonitor.
 * It is a hint: a request can still fail while "Online", which is why ErrorMapper re-checks it.
 */
interface ConnectivityMonitor {
    val status: StateFlow<NetworkStatus>
}
