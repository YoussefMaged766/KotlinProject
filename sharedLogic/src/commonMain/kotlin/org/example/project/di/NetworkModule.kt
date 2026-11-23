package org.example.project.di

import org.example.project.network.ApiConfig
import org.example.project.network.createHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/** A function, not a val: ApiConfig is only known at startup, so the module is built then. */
internal fun networkModule(config: ApiConfig): Module = module {
    single { config }
    single { createHttpClient(engine = get(), config = get()) }   // single: one connection pool
}
