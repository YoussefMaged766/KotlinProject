package org.example.project.di

import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.core.config.AppEnvironment
import org.example.project.core.config.AppInfo
import org.example.project.core.config.NetworkConfig
import org.example.project.core.error.ErrorPresenter
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Swift's door into Koin. Swift can't call Koin's reified get<T>(), so this object does the lookup
 * and exposes plain functions. It's the only KoinComponent in the project: a service locator at the edge.
 */
object IosKoin : KoinComponent {

    /**
     * Swift loses Kotlin default arguments, so NetworkConfig's defaults (timeouts, retries) are applied
     * here and Swift only passes what differs per launch.
     */
    fun start(environment: AppEnvironment, appVersion: String, languageTag: String) {
        initKoin(
            NetworkConfig(
                environment = environment,
                appInfo = AppInfo(platform = "ios", appVersion = appVersion, languageTag = languageTag),
            )
        )
    }

    fun getCategoriesUseCase(): GetCategoriesUseCase = get()

    fun errorPresenter(): ErrorPresenter = get()
}
