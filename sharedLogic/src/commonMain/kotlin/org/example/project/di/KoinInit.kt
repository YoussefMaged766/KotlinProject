package org.example.project.di

import org.example.project.network.ApiConfig
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts the single, app-wide Koin container. Call it exactly once per process:
 * Android from Application.onCreate, iOS from the App struct's init.
 * `appDeclaration` lets each platform add its own setup (Android context, logger, UI modules).
 */
fun initKoin(config: ApiConfig, appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(sharedModules(config))
    }

/** Everything sharedLogic provides. Tests reuse this list with a fake engine on top. */
internal fun sharedModules(config: ApiConfig): List<Module> =
    listOf(platformModule, networkModule(config), categoriesModule)
