package org.example.project.di

import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.network.ApiConfig
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Swift's door into Koin. Swift can't call Koin's reified get<T>(), so this object does the lookup
 * and exposes plain functions: `IosKoin.shared.start(config:)` and `IosKoin.shared.getCategoriesUseCase()`.
 * It's the only KoinComponent in the project: a service locator at the edge, where one belongs.
 */
object IosKoin : KoinComponent {

    fun start(config: ApiConfig) {
        initKoin(config)
    }

    fun getCategoriesUseCase(): GetCategoriesUseCase = get()
}
