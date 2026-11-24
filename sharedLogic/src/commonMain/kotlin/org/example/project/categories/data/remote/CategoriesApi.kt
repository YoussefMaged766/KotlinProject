package org.example.project.categories.data.remote

import org.example.project.core.result.ApiResult
import org.example.project.network.RequestExecutor

/** The Ktor version of a Retrofit `interface CategoriesApiService`. */
internal class CategoriesApi(private val executor: RequestExecutor) {

    suspend fun getCategories(): ApiResult<List<CategoryDto>> = executor.get("v1/client/categories")
}
