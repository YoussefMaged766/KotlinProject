package org.example.project.core.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Test double: the test decides when the "device" is online, weak or offline. */
class FakeConnectivityMonitor(initial: NetworkStatus = NetworkStatus.Online) : ConnectivityMonitor {
    private val _status = MutableStateFlow(initial)
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    fun set(status: NetworkStatus) {
        _status.value = status
    }
}
