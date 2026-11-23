package org.example.project.core.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * Follows the device's network with Apple's NWPathMonitor. App-lifetime singleton (Koin `single`).
 * Starts as Online until the first update arrives (within milliseconds), so a cold start never
 * shows a false "No internet".
 */
internal class IosConnectivityMonitor : ConnectivityMonitor {

    private val _status = MutableStateFlow<NetworkStatus>(NetworkStatus.Online)
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            _status.value = when {
                path == null || nw_path_get_status(path) != nw_path_status_satisfied -> NetworkStatus.Offline
                nw_path_is_constrained(path) -> NetworkStatus.Weak      // Low Data Mode / constrained link
                else -> NetworkStatus.Online
            }
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("org.example.project.connectivity", null))
        nw_path_monitor_start(monitor)
    }
}
