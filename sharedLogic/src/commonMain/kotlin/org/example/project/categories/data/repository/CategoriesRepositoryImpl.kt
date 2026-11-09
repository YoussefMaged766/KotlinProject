package org.example.project.categories.data.repository

import org.example.project.categories.data.remote.CategoriesApi
import org.example.project.categories.data.remote.CategoryDto
import org.example.project.categories.domain.model.Category
import org.example.project.categories.domain.repository.CategoriesRepository
import org.example.project.core.mapper.ListMapper
import org.example.project.network.ApiResult
import org.example.project.network.map

internal class CategoriesRepositoryImpl(
    private val api: CategoriesApi,
    private val categoryListMapper: ListMapper<CategoryDto, Category>,   // the interface, not the impl
) : CategoriesRepository {

    override suspend fun getCategories(): ApiResult<List<Category>> =
        api.getCategories().map(categoryListMapper::map)
}
