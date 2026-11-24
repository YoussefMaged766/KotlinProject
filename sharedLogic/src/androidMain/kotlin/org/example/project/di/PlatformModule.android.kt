package org.example.project.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import org.example.project.core.connectivity.AndroidConnectivityMonitor
import org.example.project.core.connectivity.ConnectivityMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformModule: Module = module {
    single<HttpClientEngine> { createOkHttpEngine() }
    single<ConnectivityMonitor> { AndroidConnectivityMonitor(androidContext()) }
}

/**
 * OkHttp has its own silent retry, below RetryPolicy, and it resends POSTs: an order could arrive twice.
 * It must stay off. GETs are still retried, by RetryPolicy.
 */
internal fun createOkHttpEngine(configure: OkHttpClient.Builder.() -> Unit = {}): HttpClientEngine = OkHttp.create {
    config {
        retryOnConnectionFailure(false)
        configure()
    }
}
