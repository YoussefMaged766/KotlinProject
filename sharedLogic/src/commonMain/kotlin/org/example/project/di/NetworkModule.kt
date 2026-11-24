package org.example.project.di

import org.example.project.core.config.NetworkConfig
import org.example.project.core.connectivity.ConnectivityMonitor
import org.example.project.core.events.GlobalEventBus
import org.example.project.network.ErrorMapper
import org.example.project.network.RequestExecutor
import org.example.project.network.RetryPolicy
import org.example.project.network.createHttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** A function, not a val: NetworkConfig is only known at startup, so the module is built then. */
internal fun networkModule(config: NetworkConfig): Module = module {
    single { config }                                             // any class can ask for NetworkConfig
    single { createHttpClient(engine = get(), config = get()) }   // single: one connection pool
    single {
        val connectivity = get<ConnectivityMonitor>()             // from platformModule
        ErrorMapper(isOnline = { connectivity.status.value.isConnected })
    }
    single { RetryPolicy(maxRetries = get<NetworkConfig>().maxRetries) }
    singleOf(::GlobalEventBus)                                    // single: shared by the executor and the app root
    singleOf(::RequestExecutor)
}
