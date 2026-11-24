package org.example.project

import kotlinx.coroutines.flow.MutableStateFlow
import org.example.project.core.connectivity.ConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus

/** Test double: the test decides when the "device" is online, weak or offline. */
class FakeConnectivityMonitor(initial: NetworkStatus = NetworkStatus.Online) : ConnectivityMonitor {
    override val status = MutableStateFlow(initial)

    fun set(value: NetworkStatus) {
        status.value = value
    }
}
