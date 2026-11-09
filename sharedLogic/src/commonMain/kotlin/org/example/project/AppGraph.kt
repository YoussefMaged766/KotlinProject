package org.example.project

import org.example.project.categories.data.mapper.CategoryDtoListMapper
import org.example.project.categories.data.mapper.CategoryDtoMapper
import org.example.project.categories.data.remote.CategoriesApi
import org.example.project.categories.data.repository.CategoriesRepositoryImpl
import org.example.project.categories.domain.repository.CategoriesRepository
import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.network.ApiConfig
import org.example.project.network.createHttpClient
import org.example.project.network.platformHttpEngine

/** Builds the object graph once (what Hilt's @Binds does in EZeats). Create exactly one per process. */
class AppGraph(config: ApiConfig) {

    private val httpClient = createHttpClient(platformHttpEngine(), config)

    private val categoriesRepository: CategoriesRepository = CategoriesRepositoryImpl(
        api = CategoriesApi(httpClient),
        categoryListMapper = CategoryDtoListMapper(itemMapper = CategoryDtoMapper()),
    )

    /** The only thing the UI layers (Android and iOS) get from this feature. */
    val getCategoriesUseCase = GetCategoriesUseCase(categoriesRepository)
}
