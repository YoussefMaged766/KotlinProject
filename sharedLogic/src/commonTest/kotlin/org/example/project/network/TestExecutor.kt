package org.example.project.network

import io.ktor.client.engine.HttpClientEngine
import org.example.project.core.config.NetworkConfig
import org.example.project.core.connectivity.ConnectivityMonitor
import org.example.project.core.connectivity.FakeConnectivityMonitor
import org.example.project.core.events.GlobalEventBus

/** The production executor on a fake engine. No retries unless a test asks for them. */
internal fun testExecutor(
    engine: HttpClientEngine,
    config: NetworkConfig,
    connectivity: ConnectivityMonitor = FakeConnectivityMonitor(),
    retryPolicy: RetryPolicy = RetryPolicy(maxRetries = 0),
    events: GlobalEventBus = GlobalEventBus(),
    isOnline: () -> Boolean = { connectivity.status.value.isConnected },
) = RequestExecutor(createHttpClient(engine, config), ErrorMapper(isOnline), connectivity, retryPolicy, events)
