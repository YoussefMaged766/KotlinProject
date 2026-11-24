package org.example.project.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.example.project.core.config.NetworkConfig
import org.example.project.core.config.NetworkLogLevel
import org.example.project.core.connectivity.AndroidConnectivityMonitor
import org.example.project.core.connectivity.ConnectivityMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformModule: Module = module {
    single { ConnectionPool() }
    single<HttpClientEngine> { createOkHttpEngine(get()) }
    single<ConnectivityMonitor> {
        val connectionPool = get<ConnectionPool>()
        AndroidConnectivityMonitor(
            context = androidContext(),
            logChanges = get<NetworkConfig>().logLevel != NetworkLogLevel.None,
            // A connection opened on the old network looks fine but never answers: 15 s lost on the next request.
            onNetworkChanged = connectionPool::evictAll,
        )
    }
}

/**
 * OkHttp has its own silent retry, below RetryPolicy, and it resends POSTs: an order could arrive twice.
 * It must stay off. GETs are still retried, by RetryPolicy.
 */
internal fun createOkHttpEngine(
    connectionPool: ConnectionPool = ConnectionPool(),
    configure: OkHttpClient.Builder.() -> Unit = {},
): HttpClientEngine = OkHttp.create {
    config {
        retryOnConnectionFailure(false)
        connectionPool(connectionPool)
        configure()
    }
}
