package org.example.project.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Follows the device's default network. App-lifetime singleton (Koin `single`), so the callback is
 * registered once and never unregistered. Needs ACCESS_NETWORK_STATE in the app manifest.
 */
internal class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _status = MutableStateFlow(currentStatus())
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    init {
        Log.d(TAG, "Initial status: ${_status.value}")
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                update(capabilities.toStatus())
            }

            override fun onLost(network: Network) = update(NetworkStatus.Offline)

            override fun onUnavailable() = update(NetworkStatus.Offline)
        })
    }

    private fun update(newStatus: NetworkStatus) {
        if (_status.value != newStatus) Log.d(TAG, "Status: ${_status.value} -> $newStatus")
        _status.value = newStatus
    }

    private fun currentStatus(): NetworkStatus =
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.toStatus()
            ?: NetworkStatus.Offline

    /** "Connected to Wi-Fi" is not "has internet": VALIDATED means Android actually reached the internet. */
    private fun NetworkCapabilities.toStatus(): NetworkStatus = when {
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkStatus.Offline
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkStatus.Offline
        linkDownstreamBandwidthKbps in 1 until WEAK_BANDWIDTH_KBPS -> NetworkStatus.Weak
        else -> NetworkStatus.Online
    }

    private companion object {
        const val TAG = "Connectivity"
        const val WEAK_BANDWIDTH_KBPS = 150      // roughly EDGE / bad 3G
    }
}
