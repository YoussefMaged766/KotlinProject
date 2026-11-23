package org.example.project.di

import org.example.project.categories.data.remote.CategoriesApi
import org.example.project.categories.data.repository.CategoriesRepositoryImpl
import org.example.project.categories.domain.repository.CategoriesRepository
import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Data + domain bindings for the categories feature.
 * `singleOf(::Impl)` calls the constructor and fills every parameter with get().
 * Mapping is done with extension functions, so there are no mapper bindings to qualify.
 */
internal val categoriesModule = module {
    // data
    singleOf(::CategoriesApi)
    singleOf(::CategoriesRepositoryImpl) { bind<CategoriesRepository>() }   // like Hilt's @Binds

    // domain
    factoryOf(::GetCategoriesUseCase)
}
