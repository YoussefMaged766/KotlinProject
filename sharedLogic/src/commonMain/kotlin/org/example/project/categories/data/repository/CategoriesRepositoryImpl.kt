package org.example.project.categories.data.repository

import org.example.project.categories.data.mapper.toDomain
import org.example.project.categories.data.remote.CategoriesApi
import org.example.project.categories.domain.model.Category
import org.example.project.categories.domain.repository.CategoriesRepository
import org.example.project.network.ApiResult
import org.example.project.network.map

internal class CategoriesRepositoryImpl(
    private val api: CategoriesApi,
) : CategoriesRepository {

    override suspend fun getCategories(): ApiResult<List<Category>> =
        api.getCategories().map { dtos -> dtos.toDomain() }
}
