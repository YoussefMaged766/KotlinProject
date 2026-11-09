package org.example.project.categories.domain.repository

import org.example.project.categories.domain.model.Category
import org.example.project.network.ApiResult

/** Contract only. The data layer implements it (Dependency Inversion). */
interface CategoriesRepository {
    suspend fun getCategories(): ApiResult<List<Category>>
}
