package org.example.project.di

import org.example.project.core.config.NetworkConfig
import org.example.project.network.createHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/** A function, not a val: NetworkConfig is only known at startup, so the module is built then. */
internal fun networkModule(config: NetworkConfig): Module = module {
    single { config }                                             // any class can ask for NetworkConfig
    single { createHttpClient(engine = get(), config = get()) }   // single: one connection pool
}
