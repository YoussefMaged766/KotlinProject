package org.example.project.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.example.project.core.connectivity.AndroidConnectivityMonitor
import org.example.project.core.connectivity.ConnectivityMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single<ConnectivityMonitor> { AndroidConnectivityMonitor(androidContext()) }
}
